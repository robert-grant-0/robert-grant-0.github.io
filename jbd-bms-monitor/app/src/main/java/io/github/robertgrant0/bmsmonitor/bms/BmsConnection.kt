package io.github.robertgrant0.bmsmonitor.bms

import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps one battery connected for as long as it's running: connects, polls the BMS, and
 * reconnects with back-off whenever the link drops. Pure Kotlin: Android comes in through
 * [BmsEnvironment], so this logic is covered by unit tests.
 */
class BmsConnection(
    val slot: Int,
    val address: String,
    private val env: BmsEnvironment,
    private val pollIntervalMs: () -> Long,
    private val sink: (slot: Int, address: String, transform: BatteryState.() -> BatteryState) -> Unit,
    private val log: DiagnosticsLog,
) {
    private var job: Job? = null
    private val tag = "battery${slot + 1}"

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
        var blockedBy: LinkState? = null
        while (true) {
            val block = when {
                !env.canConnect() -> LinkState.NO_PERMISSION
                !env.bluetoothEnabled() -> LinkState.BLUETOOTH_OFF
                else -> null
            }
            if (block != null) {
                val text = if (block == LinkState.NO_PERMISSION) "Bluetooth permission needed" else "Bluetooth is off"
                if (blockedBy != block) {
                    log.warn(tag, text)
                    blockedBy = block
                }
                publish { copy(link = block, statusText = text, rssi = null) }
                delay(BLOCKED_RETRY_MS)
                continue
            }
            blockedBy = null

            if (attempt > 0) {
                // Wait for the BMS to show up in the background scan, but not forever in
                // case scanning isn't possible (e.g. location services off on old Android).
                val waitMs = (ADVERT_WAIT_STEP_MS * attempt).coerceAtMost(ADVERT_WAIT_MAX_MS)
                publish { copy(link = LinkState.DISCONNECTED, statusText = "$lastReason – searching…") }
                val seen = env.awaitAdvertisement(address, waitMs)
                log.info(tag, if (seen) "Seen advertising" else "Not seen for ${waitMs / 1000} s, trying anyway")
            }

            publish {
                copy(link = LinkState.CONNECTING, statusText = if (attempt == 0) "Connecting…" else "Reconnecting…")
            }
            log.info(tag, "Connecting to $address (attempt ${attempt + 1})")
            var gotData = false
            lastReason = try {
                runSession { gotData = true }
                "Disconnected"
            } catch (e: TimeoutCancellationException) {
                "Timed out"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            log.warn(tag, "Link lost: $lastReason" + if (gotData) "" else " (no data this session)")
            attempt = if (gotData) 1 else attempt + 1
            val now = env.now()
            publish {
                copy(
                    link = LinkState.DISCONNECTED,
                    statusText = lastReason,
                    rssi = null,
                    disconnectedSince = if (disconnectedSince == 0L) now else disconnectedSince,
                    reconnectCount = if (gotData) reconnectCount + 1 else reconnectCount,
                )
            }
            delay(if (gotData) RETRY_AFTER_DATA_MS else RETRY_AFTER_FAILURE_MS)
        }
    }

    private suspend fun runSession(onData: () -> Unit) {
        val link = env.open(address)
        try {
            log.info(tag, "Connected")
            publish { copy(link = LinkState.CONNECTED, statusText = "Connected", disconnectedSince = 0L) }

            link.request(JbdProtocol.CMD_HARDWARE_VERSION)
                ?.takeIf { it.ok }
                ?.let { JbdProtocol.parseHardwareVersion(it.payload) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { hw ->
                    log.info(tag, "Hardware $hw")
                    publish { copy(hardwareVersion = hw) }
                }

            var misses = 0
            var polls = 0
            while (true) {
                val basic = link.request(JbdProtocol.CMD_BASIC_INFO)
                    ?.takeIf { it.ok }
                    ?.let { JbdProtocol.parseBasicInfo(it.payload) }
                val cells = link.request(JbdProtocol.CMD_CELL_VOLTAGES)
                    ?.takeIf { it.ok }
                    ?.let { JbdProtocol.parseCellVoltages(it.payload) }
                val rssi = if (polls % RSSI_EVERY_N_POLLS == 0) link.readRssi() else null
                polls++

                if (basic == null && cells == null) {
                    misses++
                    log.warn(tag, "No reply from BMS ($misses/$MAX_MISSED_POLLS)")
                    if (misses >= MAX_MISSED_POLLS) throw BmsException("BMS stopped responding")
                    publish { copy(statusText = "Waiting for BMS…") }
                } else {
                    if (misses > 0) log.info(tag, "BMS replying again")
                    misses = 0
                    onData()
                    val now = env.now()
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
            link.close()
        }
    }

    companion object {
        const val MAX_MISSED_POLLS = 5
        const val RSSI_EVERY_N_POLLS = 5
        const val BLOCKED_RETRY_MS = 3_000L
        const val ADVERT_WAIT_STEP_MS = 10_000L
        const val ADVERT_WAIT_MAX_MS = 60_000L
        const val RETRY_AFTER_DATA_MS = 1_000L
        const val RETRY_AFTER_FAILURE_MS = 3_000L
    }
}
