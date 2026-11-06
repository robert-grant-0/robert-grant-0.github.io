package io.github.robertgrant0.bmsmonitor.data

import android.content.Context
import androidx.core.content.edit
import io.github.robertgrant0.bmsmonitor.bms.AlarmThresholds
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bms_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        save(next)
        _state.value = next
    }

    fun updateSlot(slot: Int, transform: (SlotConfig) -> SlotConfig) = update { it.withSlot(slot, transform) }

    /** Assigns a BMS to [slot], removing it from any other slot so it is never connected twice. */
    fun assignDevice(slot: Int, address: String, deviceName: String?) =
        update { it.withDevice(slot, address, deviceName) }

    fun updateVictron(kind: VictronKind, transform: (VictronSlot) -> VictronSlot) =
        update { it.withVictron(kind, transform) }

    private fun loadVictron(kind: VictronKind) = VictronSlot(
        address = prefs.getString("${kind.prefKey}_address", null),
        deviceName = prefs.getString("${kind.prefKey}_device", null),
        key = prefs.getString("${kind.prefKey}_key", null) ?: "",
    )

    private fun load(): AppSettings {
        val d = AppSettings()
        val t = d.thresholds
        return AppSettings(
            slots = List(BatteryState.SLOT_COUNT) { i ->
                SlotConfig(
                    name = prefs.getString("slot${i}_name", null) ?: AppSettings.defaultName(i),
                    address = prefs.getString("slot${i}_address", null),
                    deviceName = prefs.getString("slot${i}_device", null),
                )
            },
            monitoringEnabled = prefs.getBoolean("monitoring", d.monitoringEnabled),
            pollIntervalSec = prefs.getInt("poll_sec", d.pollIntervalSec),
            keepScreenOn = prefs.getBoolean("keep_screen_on", d.keepScreenOn),
            keepAwake = prefs.getBoolean("keep_awake", d.keepAwake),
            startOnBoot = prefs.getBoolean("start_on_boot", d.startOnBoot),
            alarmNotifications = prefs.getBoolean("alarm_notifications", d.alarmNotifications),
            sirenEnabled = prefs.getBoolean("siren", d.sirenEnabled),
            sirenForWarnings = prefs.getBoolean("siren_warnings", d.sirenForWarnings),
            sirenFullVolume = prefs.getBoolean("siren_full_volume", d.sirenFullVolume),
            thresholds = AlarmThresholds(
                lowSocEnabled = prefs.getBoolean("low_soc_on", t.lowSocEnabled),
                lowSocPercent = prefs.getInt("low_soc", t.lowSocPercent),
                highTempEnabled = prefs.getBoolean("high_temp_on", t.highTempEnabled),
                highTempC = prefs.getInt("high_temp", t.highTempC),
                lowTempEnabled = prefs.getBoolean("low_temp_on", t.lowTempEnabled),
                lowTempC = prefs.getInt("low_temp", t.lowTempC),
                cellDeltaEnabled = prefs.getBoolean("cell_delta_on", t.cellDeltaEnabled),
                cellDeltaMv = prefs.getInt("cell_delta", t.cellDeltaMv),
                connectionLostEnabled = prefs.getBoolean("link_lost_on", t.connectionLostEnabled),
                connectionLostSeconds = prefs.getInt("link_lost_sec", t.connectionLostSeconds),
                shareImbalanceEnabled = prefs.getBoolean("share_on", t.shareImbalanceEnabled),
                shareImbalancePoints = prefs.getInt("share_points", t.shareImbalancePoints),
                shuntMismatchEnabled = prefs.getBoolean("shunt_mismatch_on", t.shuntMismatchEnabled),
            ),
            shunt = loadVictron(VictronKind.SHUNT),
            solar = loadVictron(VictronKind.SOLAR),
            theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: d.theme,
            animations = prefs.getBoolean("animations", d.animations),
            flowUnit = FlowUnit.entries.firstOrNull { it.name == prefs.getString("flow_unit", null) } ?: d.flowUnit,
        )
    }

    private fun save(s: AppSettings) {
        val t = s.thresholds
        prefs.edit {
            s.slots.forEachIndexed { i, c ->
                putString("slot${i}_name", c.name)
                putString("slot${i}_address", c.address)
                putString("slot${i}_device", c.deviceName)
            }
            putBoolean("monitoring", s.monitoringEnabled)
            putInt("poll_sec", s.pollIntervalSec)
            putBoolean("keep_screen_on", s.keepScreenOn)
            putBoolean("keep_awake", s.keepAwake)
            putBoolean("start_on_boot", s.startOnBoot)
            putBoolean("alarm_notifications", s.alarmNotifications)
            putBoolean("siren", s.sirenEnabled)
            putBoolean("siren_warnings", s.sirenForWarnings)
            putBoolean("siren_full_volume", s.sirenFullVolume)
            putBoolean("low_soc_on", t.lowSocEnabled)
            putInt("low_soc", t.lowSocPercent)
            putBoolean("high_temp_on", t.highTempEnabled)
            putInt("high_temp", t.highTempC)
            putBoolean("low_temp_on", t.lowTempEnabled)
            putInt("low_temp", t.lowTempC)
            putBoolean("cell_delta_on", t.cellDeltaEnabled)
            putInt("cell_delta", t.cellDeltaMv)
            putBoolean("link_lost_on", t.connectionLostEnabled)
            putInt("link_lost_sec", t.connectionLostSeconds)
            putBoolean("share_on", t.shareImbalanceEnabled)
            putInt("share_points", t.shareImbalancePoints)
            putBoolean("shunt_mismatch_on", t.shuntMismatchEnabled)
            for (kind in VictronKind.entries) {
                val v = s.victron(kind)
                putString("${kind.prefKey}_address", v.address)
                putString("${kind.prefKey}_device", v.deviceName)
                putString("${kind.prefKey}_key", v.key)
            }
            putString("theme", s.theme.name)
            putBoolean("animations", s.animations)
            putString("flow_unit", s.flowUnit.name)
        }
    }
}
