package io.github.robertgrant0.bmsmonitor.data

import io.github.robertgrant0.bmsmonitor.bms.AlarmThresholds
import io.github.robertgrant0.bmsmonitor.bms.BatteryState

data class SlotConfig(
    /** User-chosen label, e.g. "House battery". */
    val name: String,
    /** Bluetooth MAC address (upper case), or null if no BMS is assigned. */
    val address: String? = null,
    /** Bluetooth name the BMS advertised when it was picked. */
    val deviceName: String? = null,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class VictronKind(val prefKey: String, val label: String) {
    SHUNT("shunt", "SmartShunt"),
    SOLAR("solar", "Solar charger"),
}

data class VictronSlot(
    val address: String? = null,
    val deviceName: String? = null,
    /** Instant Readout encryption key from VictronConnect (32 hex digits). Stays on this device. */
    val key: String = "",
)

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
    /** Moving charge waves and energy-flow particles. Off saves power on slower tablets. */
    val animations: Boolean = true,
    val shunt: VictronSlot = VictronSlot(),
    val solar: VictronSlot = VictronSlot(),
) {
    fun victron(kind: VictronKind): VictronSlot = if (kind == VictronKind.SHUNT) shunt else solar

    fun slotName(slot: Int): String = slots[slot].name.ifBlank { defaultName(slot) }

    fun withSlot(slot: Int, transform: (SlotConfig) -> SlotConfig): AppSettings =
        copy(slots = slots.mapIndexed { i, c -> if (i == slot) transform(c) else c })

    /** Assigns a BMS to [slot], removing it from any other slot so it is never connected twice. */
    fun withDevice(slot: Int, address: String, deviceName: String?): AppSettings = copy(
        slots = slots.mapIndexed { i, c ->
            when {
                i == slot -> c.copy(address = address, deviceName = deviceName)
                c.address == address -> c.copy(address = null, deviceName = null)
                else -> c
            }
        },
    )

    fun withVictron(kind: VictronKind, transform: (VictronSlot) -> VictronSlot): AppSettings = when (kind) {
        VictronKind.SHUNT -> copy(shunt = transform(shunt))
        VictronKind.SOLAR -> copy(solar = transform(solar))
    }

    fun withThresholds(transform: (AlarmThresholds) -> AlarmThresholds): AppSettings =
        copy(thresholds = transform(thresholds))

    companion object {
        fun defaultName(slot: Int) = "Battery ${slot + 1}"
    }
}
