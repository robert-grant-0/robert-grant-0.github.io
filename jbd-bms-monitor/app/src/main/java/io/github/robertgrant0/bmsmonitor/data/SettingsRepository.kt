package io.github.robertgrant0.bmsmonitor.data

import android.content.Context
import androidx.core.content.edit
import io.github.robertgrant0.bmsmonitor.bms.AlarmThresholds
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SlotConfig(
    /** User-chosen label, e.g. "House battery". */
    val name: String,
    /** Bluetooth MAC address (upper case), or null if no BMS is assigned. */
    val address: String? = null,
    /** Bluetooth name the BMS advertised when it was picked. */
    val deviceName: String? = null,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val slots: List<SlotConfig> = List(BatteryState.SLOT_COUNT) { SlotConfig(defaultName(it)) },
    /** Whether the background service should keep the batteries connected. */
    val monitoringEnabled: Boolean = true,
    val pollIntervalSec: Int = 2,
    val keepScreenOn: Boolean = true,
    /** Hold a wake lock so polling continues while the screen is off. */
    val keepAwake: Boolean = true,
    val startOnBoot: Boolean = true,
    val alarmNotifications: Boolean = true,
    /** Loud, repeating siren for BMS faults that keeps going until silenced. */
    val sirenEnabled: Boolean = true,
    /** Also sound the siren for warnings (low charge, imbalance, low temperature, disconnected…). */
    val sirenForWarnings: Boolean = false,
    /** Turn the alarm volume up to maximum while the siren sounds. */
    val sirenFullVolume: Boolean = true,
    val thresholds: AlarmThresholds = AlarmThresholds(),
    val theme: ThemeMode = ThemeMode.DARK,
) {
    fun slotName(slot: Int): String = slots[slot].name.ifBlank { defaultName(slot) }

    companion object {
        fun defaultName(slot: Int) = "Battery ${slot + 1}"
    }
}

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

    fun updateSlot(slot: Int, transform: (SlotConfig) -> SlotConfig) = update { s ->
        s.copy(slots = s.slots.mapIndexed { i, c -> if (i == slot) transform(c) else c })
    }

    /** Assigns a BMS to [slot], removing it from any other slot so it is never connected twice. */
    fun assignDevice(slot: Int, address: String, deviceName: String?) = update { s ->
        s.copy(
            slots = s.slots.mapIndexed { i, c ->
                when {
                    i == slot -> c.copy(address = address, deviceName = deviceName)
                    c.address == address -> c.copy(address = null, deviceName = null)
                    else -> c
                }
            },
        )
    }

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
            ),
            theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: d.theme,
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
            putString("theme", s.theme.name)
        }
    }
}
