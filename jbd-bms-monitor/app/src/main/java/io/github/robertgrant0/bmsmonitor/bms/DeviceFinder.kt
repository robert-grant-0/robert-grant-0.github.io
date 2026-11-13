package io.github.robertgrant0.bmsmonitor.bms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import io.github.robertgrant0.bmsmonitor.Permissions
import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background scanner shared by all battery connections. A JBD BMS only advertises
 * while nothing is connected to it, so seeing an advertisement means "it's in range
 * and free – connect now". Scanning only runs while at least one battery is waiting
 * to reconnect. Scans use address filters so Android keeps delivering results while
 * the screen is off.
 */
@SuppressLint("MissingPermission")
class DeviceFinder(private val context: Context, private val log: DiagnosticsLog) {

    data class Sighting(val address: String, val rssi: Int)

    private val _sightings = MutableSharedFlow<Sighting>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sightings: SharedFlow<Sighting> = _sightings

    private val lock = Any()
    private var targets: Set<String> = emptySet()
    private val waiting = HashSet<String>()

    @Volatile private var activeFilter: Set<String>? = null
    private var scanStartedAt = 0L
    private var lastStartAt = -SCAN_START_GAP_MS
    private var job: Job? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = emit(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) emit(r)
        }

        override fun onScanFailed(errorCode: Int) {
            log.error("scan", "Background scan failed (error $errorCode)")
            activeFilter = null
        }
    }

    private fun emit(r: ScanResult) {
        _sightings.tryEmit(Sighting(r.device.address.uppercase(), r.rssi))
    }

    fun setTargets(addresses: Set<String>) {
        synchronized(lock) { targets = addresses }
    }

    fun setWaiting(address: String, isWaiting: Boolean) {
        synchronized(lock) { if (isWaiting) waiting += address else waiting -= address }
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                while (isActive) {
                    reconcile()
                    delay(1_000)
                }
            } finally {
                stopScan()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun reconcile() {
        val want = synchronized(lock) { if (waiting.isEmpty()) emptySet() else targets.toSet() }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (want.isEmpty() || adapter == null || !adapter.isEnabled || !Permissions.canScan(context)) {
            stopScan()
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        val now = SystemClock.elapsedRealtime()
        // Android silently downgrades scans that run for 30+ minutes, so restart them periodically.
        val needsRestart = activeFilter != want || now - scanStartedAt > SCAN_MAX_AGE_MS
        // Android blocks apps that start more than 5 scans in 30 seconds.
        if (!needsRestart || now - lastStartAt < SCAN_START_GAP_MS) return
        stopScan()
        lastStartAt = now
        try {
            val filters = want.map { ScanFilter.Builder().setDeviceAddress(it).build() }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            scanner.startScan(filters, settings, callback)
            activeFilter = want
            scanStartedAt = now
            log.info("scan", "Background scan for ${want.size} BMS address(es)")
        } catch (_: Exception) {
            activeFilter = null
        }
    }

    private fun stopScan() {
        if (activeFilter == null) return
        activeFilter = null
        log.info("scan", "Background scan stopped")
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val SCAN_START_GAP_MS = 7_000L
        const val SCAN_MAX_AGE_MS = 25 * 60_000L
    }
}
