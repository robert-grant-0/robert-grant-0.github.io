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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VictronConfig(val address: String?, val name: String, val keyHex: String)

/**
 * Listens for Victron Instant Readout broadcasts from the SmartShunt and the solar charger.
 * Never connects to them. The scan filters on Victron's manufacturer data so Android keeps
 * delivering results while the screen is off.
 */
@SuppressLint("MissingPermission")
class VictronMonitor(private val context: Context, private val log: DiagnosticsLog) {
    private class Slot(val expectedRecord: Int, val label: String) {
        val state = MutableStateFlow(VictronDeviceState())

        @Volatile var address: String? = null

        @Volatile var key: ByteArray? = null
    }

    private val shuntSlot = Slot(VictronProtocol.RECORD_BATTERY_MONITOR, "SmartShunt")
    private val solarSlot = Slot(VictronProtocol.RECORD_SOLAR_CHARGER, "Solar charger")
    val shunt: StateFlow<VictronDeviceState> = shuntSlot.state.asStateFlow()
    val solar: StateFlow<VictronDeviceState> = solarSlot.state.asStateFlow()

    @Volatile private var running = false
    @Volatile private var scanning = false
    private var scanStartedAt = 0L
    private var lastStartAt = -SCAN_START_GAP_MS
    private var job: Job? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handle(r)
        }

        override fun onScanFailed(errorCode: Int) {
            log.error("victron", "Victron scan failed (error $errorCode)")
            scanning = false
        }
    }

    @Synchronized
    fun configure(shuntConfig: VictronConfig, solarConfig: VictronConfig) {
        apply(shuntSlot, shuntConfig)
        apply(solarSlot, solarConfig)
    }

    private fun apply(slot: Slot, cfg: VictronConfig) {
        val key = VictronProtocol.parseKey(cfg.keyHex)
        val changed = slot.address != cfg.address || !(slot.key contentEquals key)
        slot.address = cfg.address
        slot.key = key
        slot.state.update { s ->
            when {
                cfg.address == null -> VictronDeviceState()
                changed || s.address != cfg.address -> VictronDeviceState(
                    address = cfg.address,
                    name = cfg.name,
                    status = initialStatus(key),
                    since = System.currentTimeMillis(),
                )
                else -> s.copy(name = cfg.name)
            }
        }
    }

    private fun initialStatus(key: ByteArray?) = when {
        !running -> VictronStatus.STOPPED
        key == null -> VictronStatus.NO_KEY
        else -> VictronStatus.WAITING
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        running = true
        for (slot in listOf(shuntSlot, solarSlot)) {
            slot.state.update {
                if (it.isConfigured) it.copy(status = initialStatus(slot.key), since = System.currentTimeMillis()) else it
            }
        }
        job = scope.launch {
            try {
                while (isActive) {
                    reconcile()
                    delay(3_000)
                }
            } finally {
                stopScan()
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        for (slot in listOf(shuntSlot, solarSlot)) {
            slot.state.update { if (it.isConfigured) it.copy(status = VictronStatus.STOPPED) else it }
        }
    }

    private fun handle(r: ScanResult) {
        val address = r.device.address.uppercase()
        val slot = when (address) {
            shuntSlot.address -> shuntSlot
            solarSlot.address -> solarSlot
            else -> return
        }
        val data = r.scanRecord?.getManufacturerSpecificData(VictronProtocol.COMPANY_ID) ?: return
        val type = VictronProtocol.recordType(data) ?: return
        val before = slot.state.value.status
        if (type != slot.expectedRecord) {
            // Some devices send several record kinds; only complain if this is all we ever get.
            if (slot.state.value.reading == null && before != VictronStatus.WRONG_TYPE) {
                log.warn("victron", "${slot.label}: device sends record type 0x${type.toString(16)}, not the expected kind")
            }
            slot.state.update { if (it.reading == null) it.copy(status = VictronStatus.WRONG_TYPE) else it }
            return
        }
        val key = slot.key
        if (key == null) {
            if (before != VictronStatus.NO_KEY) log.warn("victron", "${slot.label}: heard, but no encryption key entered")
            slot.state.update { it.copy(status = VictronStatus.NO_KEY) }
            return
        }
        when (val res = VictronProtocol.decode(data, key)) {
            is VictronProtocol.Result.Ok -> {
                if (before != VictronStatus.RECEIVING) log.info("victron", "${slot.label}: receiving")
                slot.state.update {
                    it.copy(status = VictronStatus.RECEIVING, reading = res.reading, lastUpdate = System.currentTimeMillis())
                }
            }
            VictronProtocol.Result.WrongKey -> {
                if (before != VictronStatus.WRONG_KEY) log.warn("victron", "${slot.label}: wrong encryption key")
                slot.state.update { it.copy(status = VictronStatus.WRONG_KEY) }
            }
            else -> Unit
        }
    }

    private fun reconcile() {
        val wanted = running && (shuntSlot.address != null || solarSlot.address != null)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (!wanted || adapter == null || !adapter.isEnabled || !Permissions.canScan(context)) {
            stopScan()
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        val now = SystemClock.elapsedRealtime()
        // Android quietly downgrades scans that run for 30+ minutes, so restart them.
        val needsStart = !scanning || now - scanStartedAt > SCAN_MAX_AGE_MS
        if (!needsStart || now - lastStartAt < SCAN_START_GAP_MS) return
        stopScan()
        lastStartAt = now
        try {
            val filter = ScanFilter.Builder()
                .setManufacturerData(VictronProtocol.COMPANY_ID, byteArrayOf(0x10), byteArrayOf(0xFF.toByte()))
                .build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            scanner.startScan(listOf(filter), settings, callback)
            scanning = true
            scanStartedAt = now
            log.info("victron", "Listening for Victron broadcasts")
        } catch (_: Exception) {
            scanning = false
        }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val SCAN_START_GAP_MS = 13_000L
        const val SCAN_MAX_AGE_MS = 25 * 60_000L
    }
}
