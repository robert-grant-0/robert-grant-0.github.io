package io.github.robertgrant0.bmsmonitor.bms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.robertgrant0.bmsmonitor.Permissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps one battery connected for as long as it's running: connects, polls the BMS,
 * and reconnects with back-off whenever the link drops.
 */
@SuppressLint("MissingPermission") // Checked via Permissions.canConnect before every attempt.
class BmsConnection(
    private val context: Context,
    val slot: Int,
    val address: String,
    private val finder: DeviceFinder,
    /** Shared by all batteries: Android's BLE stack misbehaves when several connections are set up at once. */
    private val setupLock: Mutex,
    private val pollIntervalMs: () -> Long,
    private val sink: (slot: Int, address: String, transform: BatteryState.() -> BatteryState) -> Unit,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { runForever() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun publish(transform: BatteryState.() -> BatteryState) {
        // A cancelled connection must not overwrite the state of its replacement.
        if (currentCoroutineContext().isActive) sink(slot, address, transform)
    }

    private suspend fun runForever() {
        var attempt = 0
        var lastReason = ""
        while (true) {
            if (!Permissions.canConnect(context)) {
                publish { copy(link = LinkState.NO_PERMISSION, statusText = "Bluetooth permission needed", rssi = null) }
                delay(3_000)
                continue
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                publish { copy(link = LinkState.BLUETOOTH_OFF, statusText = "Bluetooth is off", rssi = null) }
                delay(3_000)
                continue
            }

            if (attempt > 0) {
                // Wait for the BMS to show up in the background scan, but not forever in
                // case scanning isn't possible (e.g. location services off on old Android).
                val waitMs = (10_000L * attempt).coerceAtMost(60_000L)
                publish { copy(link = LinkState.DISCONNECTED, statusText = "$lastReason – searching…") }
                finder.setWaiting(address, true)
                try {
                    withTimeoutOrNull(waitMs) { finder.sightings.first { it.address == address } }
                } finally {
                    finder.setWaiting(address, false)
                }
            }

            publish {
                copy(
                    link = LinkState.CONNECTING,
                    statusText = if (attempt == 0) "Connecting…" else "Reconnecting…",
                )
            }
            var gotData = false
            lastReason = try {
                runSession(adapter.getRemoteDevice(address)) { gotData = true }
                "Disconnected"
            } catch (e: TimeoutCancellationException) {
                "Timed out"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            attempt = if (gotData) 1 else attempt + 1
            val now = System.currentTimeMillis()
            publish {
                copy(
                    link = LinkState.DISCONNECTED,
                    statusText = lastReason,
                    rssi = null,
                    disconnectedSince = if (disconnectedSince == 0L) now else disconnectedSince,
                    reconnectCount = if (gotData) reconnectCount + 1 else reconnectCount,
                )
            }
            delay(if (gotData) 1_000L else 3_000L)
        }
    }

    private suspend fun runSession(device: BluetoothDevice, onData: () -> Unit) {
        val session = GattSession(context, device)
        try {
            setupLock.withLock {
                session.connect(CONNECT_TIMEOUT_MS)
                delay(500) // Some BLE modules need a moment before service discovery.
                session.discoverServices()
                session.enableNotifications()
            }
            publish { copy(link = LinkState.CONNECTED, statusText = "Connected", disconnectedSince = 0L) }

            session.request(JbdProtocol.CMD_HARDWARE_VERSION)
                ?.takeIf { it.ok }
                ?.let { JbdProtocol.parseHardwareVersion(it.payload) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { hw -> publish { copy(hardwareVersion = hw) } }

            var misses = 0
            var polls = 0
            while (true) {
                val basic = session.request(JbdProtocol.CMD_BASIC_INFO)
                    ?.takeIf { it.ok }
                    ?.let { JbdProtocol.parseBasicInfo(it.payload) }
                val cells = session.request(JbdProtocol.CMD_CELL_VOLTAGES)
                    ?.takeIf { it.ok }
                    ?.let { JbdProtocol.parseCellVoltages(it.payload) }
                val rssi = if (polls % RSSI_EVERY_N_POLLS == 0) session.readRssi() else null
                polls++

                if (basic == null && cells == null) {
                    misses++
                    if (misses >= MAX_MISSED_POLLS) throw GattException("BMS stopped responding")
                    publish { copy(statusText = "Waiting for BMS…") }
                } else {
                    misses = 0
                    onData()
                    val now = System.currentTimeMillis()
                    publish {
                        copy(
                            link = LinkState.CONNECTED,
                            statusText = "Connected",
                            basic = basic ?: this.basic,
                            cells = cells ?: this.cells,
                            rssi = rssi ?: this.rssi,
                            lastUpdate = now,
                            disconnectedSince = 0L,
                        )
                    }
                }
                delay(pollIntervalMs())
            }
        } finally {
            session.close()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val MAX_MISSED_POLLS = 5
        const val RSSI_EVERY_N_POLLS = 5
    }
}
