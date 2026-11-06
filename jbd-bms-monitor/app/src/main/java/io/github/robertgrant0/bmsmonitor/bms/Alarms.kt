package io.github.robertgrant0.bmsmonitor.bms

import java.util.Locale

enum class Severity { WARNING, ALARM }

/** [key] is stable for a given condition (no values in it) so it can be used to de-duplicate notifications. */
data class Alarm(val key: String, val message: String, val severity: Severity)

data class AlarmThresholds(
    val lowSocEnabled: Boolean = true,
    val lowSocPercent: Int = 20,
    val highTempEnabled: Boolean = true,
    val highTempC: Int = 50,
    val lowTempEnabled: Boolean = true,
    val lowTempC: Int = 0,
    val cellDeltaEnabled: Boolean = true,
    val cellDeltaMv: Int = 100,
    val connectionLostEnabled: Boolean = true,
    val connectionLostSeconds: Int = 60,
    /** Warn when one battery carries this many percentage points more or less than an even share. */
    val shareImbalanceEnabled: Boolean = true,
    val shareImbalancePoints: Int = 15,
    /** Warn when the SmartShunt and the sum of the BMS currents disagree. */
    val shuntMismatchEnabled: Boolean = true,
)

object Alarms {
    fun evaluate(s: BatteryState, t: AlarmThresholds, now: Long): List<Alarm> {
        if (!s.isConfigured || s.link == LinkState.STOPPED) return emptyList()
        val out = ArrayList<Alarm>()

        if (!s.isConnected) {
            if (t.connectionLostEnabled && s.disconnectedSince > 0 &&
                now - s.disconnectedSince >= t.connectionLostSeconds * 1000L
            ) {
                out += Alarm("link", "Not connected for ${formatDuration(now - s.disconnectedSince)}", Severity.WARNING)
            }
            // Don't raise alarms from stale data captured before the link dropped.
            return out
        }

        val b = s.basic ?: return out
        for (p in b.protections) out += Alarm("prot_${p.name}", p.label, Severity.ALARM)
        if (!b.dischargeFetOn) out += Alarm("dsg_fet", "Discharge MOSFET off (no output)", Severity.ALARM)
        if (!b.chargeFetOn) out += Alarm("chg_fet", "Charge MOSFET off (not charging)", Severity.WARNING)

        if (t.lowSocEnabled && b.stateOfCharge <= t.lowSocPercent) {
            out += Alarm("low_soc", "Low charge: ${b.stateOfCharge}%", Severity.WARNING)
        }
        val maxTemp = b.temperatures.maxOrNull()
        if (t.highTempEnabled && maxTemp != null && maxTemp >= t.highTempC) {
            out += Alarm("high_temp", "High temperature: ${fmt1(maxTemp)} °C", Severity.ALARM)
        }
        val minTemp = b.temperatures.minOrNull()
        if (t.lowTempEnabled && minTemp != null && minTemp <= t.lowTempC) {
            out += Alarm("low_temp", "Low temperature: ${fmt1(minTemp)} °C", Severity.WARNING)
        }
        val delta = s.cellDelta
        if (t.cellDeltaEnabled && delta != null && delta * 1000 >= t.cellDeltaMv) {
            out += Alarm("cell_delta", "Cell imbalance: ${Math.round(delta * 1000)} mV", Severity.WARNING)
        }
        return out
    }

    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
