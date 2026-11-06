package io.github.robertgrant0.bmsmonitor.ui

import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bms.VictronProtocol
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Below this current (A) a battery is shown as idle. */
const val IDLE_CURRENT_A = 0.1

/** Nominal voltage of a 12 V LiFePO4 bank, used only when no measured voltage is available. */
const val NOMINAL_VOLTS = 12.8

enum class PowerFlow { CHARGING, DISCHARGING, IDLE }

fun powerFlow(current: Double): PowerFlow = when {
    current > IDLE_CURRENT_A -> PowerFlow.CHARGING
    current < -IDLE_CURRENT_A -> PowerFlow.DISCHARGING
    else -> PowerFlow.IDLE
}

object Fmt {
    private fun f(pattern: String, vararg args: Any) = String.format(Locale.US, pattern, *args)

    fun volts(v: Double) = f("%.2f V", v)

    /** "+12.35" / "−8.20" with a true minus sign. */
    fun signed(v: Double, decimals: Int): String {
        val s = f("%.${decimals}f", abs(v))
        val zero = s.all { it == '0' || it == '.' }
        return when {
            zero -> s
            v > 0 -> "+$s"
            else -> "\u2212$s"
        }
    }

    fun amps(a: Double) = "${signed(a, 2)} A"

    fun signedAmps(a: Double, decimals: Int = 1) = "${signed(a, decimals)} A"

    fun signedWatts(w: Double): String = when {
        abs(w) >= 10_000 -> "${signed(w / 1000, 1)} kW"
        abs(w) >= 1_000 -> "${signed(w / 1000, 2)} kW"
        else -> "${signed(w, 0)} W"
    }

    fun watts(w: Double) = when {
        abs(w) >= 10_000 -> f("%.1f kW", w / 1000)
        abs(w) >= 1_000 -> f("%.2f kW", w / 1000)
        abs(w) < 0.5 -> "0 W"
        else -> f("%.0f W", w)
    }

    fun ah(v: Double) = if (v >= 100) f("%.0f Ah", v) else f("%.1f Ah", v)

    fun temp(c: Double) = f("%.1f °C", c)

    fun cell(v: Double) = f("%.3f", v)

    fun mv(v: Double) = "${(v * 1000).roundToLong()} mV"

    fun hours(h: Double): String {
        if (h.isNaN() || h.isInfinite() || h < 0) return "—"
        val totalMin = (h * 60).roundToLong()
        if (totalMin >= 100 * 60) return "> 99 h"
        val hh = totalMin / 60
        val mm = totalMin % 60
        return if (hh > 0) "${hh}h ${mm}m" else "${mm}m"
    }

    fun ago(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 3 -> "just now"
            s < 60 -> "$s s ago"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }
}

/** "Full in 3h 20m" / "Empty in 5h 2m" / null when idle or unknown. */
fun timeEstimate(b: BasicInfo): String? = when (powerFlow(b.current)) {
    PowerFlow.CHARGING -> {
        val missing = (b.nominalCapacity - b.remainingCapacity).coerceAtLeast(0.0)
        "Full in ${Fmt.hours(missing / b.current)}"
    }
    PowerFlow.DISCHARGING -> "Empty in ${Fmt.hours(b.remainingCapacity / -b.current)}"
    PowerFlow.IDLE -> null
}

/** Totals across all batteries with live data. Energy-based so it works for parallel and series banks. */
data class BankSummary(
    val liveCount: Int,
    val configuredCount: Int,
    val current: Double,
    val power: Double,
    val remainingAh: Double,
    val nominalAh: Double,
    val socPercent: Int?,
    val estimate: String?,
)

fun bankSummary(batteries: List<BatteryState>, now: Long): BankSummary {
    val configured = batteries.filter { it.isConfigured }
    val live = configured.filter { it.isLive(now) }.map { it.basic!! }
    val remainingAh = live.sumOf { it.remainingCapacity }
    val nominalAh = live.sumOf { it.nominalCapacity }
    val remainingWh = live.sumOf { it.remainingCapacity * it.totalVoltage }
    val nominalWh = live.sumOf { it.nominalCapacity * it.totalVoltage }
    val power = live.sumOf { it.power }
    val soc = if (nominalWh > 0) ((remainingWh / nominalWh) * 100).roundToLong().toInt().coerceIn(0, 100) else null
    val estimate = when {
        power > 5 -> "Full in ${Fmt.hours((nominalWh - remainingWh).coerceAtLeast(0.0) / power)}"
        power < -5 -> "Empty in ${Fmt.hours(remainingWh / -power)}"
        else -> null
    }
    return BankSummary(
        liveCount = live.size,
        configuredCount = configured.size,
        current = live.sumOf { it.current },
        power = power,
        remainingAh = remainingAh,
        nominalAh = nominalAh,
        socPercent = soc,
        estimate = estimate,
    )
}

/** Everything the bank summary at the top of the home screen shows. */
data class BankView(
    val soc: Int?,
    val watts: Double?,
    val amps: Double?,
    val volts: Double?,
    val remainingAh: Double?,
    val capacityAh: Double?,
    val estimate: String?,
    val fromShunt: Boolean,
    val solarWatts: Double?,
    val solarState: String?,
    val solarTodayKwh: Double?,
    val otherWatts: Double?,
    /** Solar charger output into the bank, A. */
    val solarAmps: Double?,
    val otherAmps: Double?,
    val liveBatteries: Int,
    val configuredBatteries: Int,
)

fun bankView(
    batteries: List<BatteryState>,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    now: Long,
): BankView {
    val s = bankSummary(batteries, now)
    val configured = batteries.filter { it.isConfigured }
    val allLive = configured.isNotEmpty() && configured.all { it.isLive(now) }
    val liveInfo = configured.filter { it.isLive(now) }.map { it.basic!! }
    val bmsVolts = if (liveInfo.isEmpty()) null else liveInfo.map { it.totalVoltage }.average()
    // Capacity is known from the BMS units even when a battery is momentarily offline.
    val capacity = configured.mapNotNull { it.basic?.nominalCapacity }.takeIf { it.isNotEmpty() }?.sum()

    val sh = shunt.shunt?.takeIf { shunt.isLive(now) }
    val pv = solar.solar?.takeIf { solar.isLive(now) }

    val soc: Int?
    val amps: Double?
    val volts: Double?
    val remaining: Double?
    var estimate: String?
    if (sh != null && sh.current != null && sh.voltage != null) {
        soc = sh.stateOfCharge?.let { Math.round(it).toInt() } ?: s.socPercent
        amps = sh.current
        volts = sh.voltage
        val consumed = sh.consumedAh
        remaining = if (capacity != null && consumed != null) (capacity + consumed).coerceAtLeast(0.0) else null
        val i: Double = sh.current
        val minutes = sh.remainingMinutes
        estimate = when {
            i < -IDLE_CURRENT_A && minutes != null -> "Empty in ${Fmt.hours(minutes / 60.0)}"
            i > IDLE_CURRENT_A && capacity != null && remaining != null ->
                "Full in ${Fmt.hours((capacity - remaining).coerceAtLeast(0.0) / i)}"
            else -> null
        }
        if (estimate == null && i < -IDLE_CURRENT_A) estimate = s.estimate
    } else {
        soc = s.socPercent
        amps = if (s.liveCount > 0) s.current else null
        volts = bmsVolts
        remaining = if (s.liveCount > 0) s.remainingAh else null
        estimate = s.estimate
    }
    val watts = if (amps != null && volts != null) amps * volts else null

    // The MPPT reports the current it pushes into the bank. If that is ever missing, derive it
    // from the panel power at the battery voltage (nominal 12.8 V as a last resort).
    val solarAmps: Double? = pv?.let { charger ->
        charger.batteryCurrent
            ?: charger.solarPower?.let { w -> w / (charger.batteryVoltage ?: volts ?: NOMINAL_VOLTS) }
    }
    // Whatever isn't solar: mostly the DC-DC chargers (positive) or loads (negative).
    val bankAmps = sh?.current ?: if (allLive) s.current else null
    val otherAmps = if (bankAmps != null && solarAmps != null) bankAmps - solarAmps else null
    val refVolts = volts ?: pv?.batteryVoltage

    return BankView(
        soc = soc,
        watts = watts,
        amps = amps,
        volts = volts,
        remainingAh = remaining,
        capacityAh = capacity,
        estimate = estimate,
        fromShunt = sh != null,
        solarWatts = pv?.solarPower?.toDouble(),
        solarState = pv?.let { VictronProtocol.chargeStateName(it.chargeState) },
        solarTodayKwh = pv?.yieldTodayWh?.let { it / 1000.0 },
        otherWatts = if (otherAmps != null && refVolts != null) otherAmps * refVolts else null,
        solarAmps = solarAmps,
        otherAmps = otherAmps,
        liveBatteries = s.liveCount,
        configuredBatteries = s.configuredCount,
    )
}
