package io.github.robertgrant0.bmsmonitor.bms

import android.content.Context
import io.github.robertgrant0.bmsmonitor.data.AppSettings
import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import io.github.robertgrant0.bmsmonitor.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the connections to all three batteries and exposes their live state.
 * Lives for the whole process; the foreground service calls [start]/[stop].
 */
class BmsManager(
    context: Context,
    private val settings: SettingsRepository,
    private val log: DiagnosticsLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val finder = DeviceFinder(context, log)
    private val env: BmsEnvironment = AndroidBmsEnvironment(context, finder, log)
    private val connections = arrayOfNulls<BmsConnection>(BatteryState.SLOT_COUNT)
    private val victron = VictronMonitor(context, log)

    /** Victron SmartShunt and solar charger readings. */
    val shunt: StateFlow<VictronDeviceState> = victron.shunt
    val solar: StateFlow<VictronDeviceState> = victron.solar

    private val _batteries = MutableStateFlow(
        List(BatteryState.SLOT_COUNT) { idleState(it, settings.current, null) },
    )
    val batteries: StateFlow<List<BatteryState>> = _batteries.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    init {
        scope.launch { settings.state.collect { apply(it) } }
    }

    @Synchronized
    fun start() {
        if (_running.value) return
        _running.value = true
        log.info("manager", "Monitoring started")
        finder.start(scope)
        victron.start(scope)
        apply(settings.current)
    }

    @Synchronized
    fun stop() {
        if (!_running.value) return
        _running.value = false
        log.info("manager", "Monitoring stopped")
        finder.stop()
        victron.stop()
        for (i in connections.indices) {
            connections[i]?.stop()
            connections[i] = null
        }
        val s = settings.current
        _batteries.update { list -> list.mapIndexed { i, st -> idleState(i, s, st) } }
    }

    @Synchronized
    private fun apply(s: AppSettings) {
        finder.setTargets(s.slots.mapNotNull { it.address }.toSet())
        victron.configure(
            VictronConfig(s.shunt.address, s.shunt.deviceName ?: "SmartShunt", s.shunt.key),
            VictronConfig(s.solar.address, s.solar.deviceName ?: "Solar charger", s.solar.key),
        )
        for (i in 0 until BatteryState.SLOT_COUNT) {
            val address = s.slots[i].address
            val name = s.slotName(i)
            val existing = connections[i]
            if (existing != null && existing.address != address) {
                existing.stop()
                connections[i] = null
            }
            val current = _batteries.value[i]
            when {
                current.address != address -> {
                    log.info("manager", "Battery ${i + 1} ($name): BMS ${address ?: "removed"}")
                    setState(i) { idleState(i, s, null) }
                }
                current.name != name -> setState(i) { it.copy(name = name) }
            }
            if (_running.value && address != null && connections[i] == null) {
                setState(i) {
                    it.copy(
                        link = LinkState.CONNECTING,
                        statusText = "Starting…",
                        disconnectedSince = System.currentTimeMillis(),
                    )
                }
                connections[i] = BmsConnection(
                    slot = i,
                    address = address,
                    env = env,
                    pollIntervalMs = { settings.current.pollIntervalSec.coerceIn(1, 60) * 1000L },
                    sink = ::sink,
                    log = log,
                ).also { it.start(scope) }
            }
        }
    }

    private fun idleState(slot: Int, s: AppSettings, previous: BatteryState?): BatteryState {
        val cfg = s.slots[slot]
        val name = s.slotName(slot)
        if (cfg.address == null) return BatteryState(slot = slot, name = name)
        val base = previous?.takeIf { it.address == cfg.address }
            ?: BatteryState(slot = slot, name = name, address = cfg.address)
        return base.copy(name = name, link = LinkState.STOPPED, statusText = "Monitoring stopped", rssi = null)
    }

    private fun setState(slot: Int, transform: (BatteryState) -> BatteryState) {
        _batteries.update { list -> list.toMutableList().also { it[slot] = transform(it[slot]) } }
    }

    private fun sink(slot: Int, address: String, transform: BatteryState.() -> BatteryState) {
        _batteries.update { list ->
            val cur = list[slot]
            if (cur.address != address) list else list.toMutableList().also { it[slot] = cur.transform() }
        }
    }
}
