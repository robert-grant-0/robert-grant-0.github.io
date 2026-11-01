package io.github.robertgrant0.bmsmonitor.ui

import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Below this current (A) a battery is shown as idle. */
const val IDLE_CURRENT_A = 0.1

enum class PowerFlow { CHARGING, DISCHARGING, IDLE }

fun powerFlow(current: Double): PowerFlow = when {
    current > IDLE_CURRENT_A -> PowerFlow.CHARGING
    current < -IDLE_CURRENT_A -> PowerFlow.DISCHARGING
    else -> PowerFlow.IDLE
}

object Fmt {
    private fun f(pattern: String, vararg args: Any) = String.format(Locale.US, pattern, *args)

    fun volts(v: Double) = f("%.2f V", v)

    fun amps(a: Double) = if (abs(a) < 0.005) "0.00 A" else f("%+.2f A", a)

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

/** "3h 20m to full" / "5h 2m to empty" / null when idle or unknown. */
fun timeEstimate(b: BasicInfo): String? = when (powerFlow(b.current)) {
    PowerFlow.CHARGING -> {
        val missing = (b.nominalCapacity - b.remainingCapacity).coerceAtLeast(0.0)
        "${Fmt.hours(missing / b.current)} to full"
    }
    PowerFlow.DISCHARGING -> "${Fmt.hours(b.remainingCapacity / -b.current)} to empty"
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
        power > 5 -> "${Fmt.hours((nominalWh - remainingWh).coerceAtLeast(0.0) / power)} to full"
        power < -5 -> "${Fmt.hours(remainingWh / -power)} to empty"
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
