package io.github.robertgrant0.bmsmonitor.bms

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Only flags conditions that have lasted at least their hold time, so brief surges don't alarm. */
class SustainTracker {
    private val since = HashMap<String, Long>()

    fun filter(candidates: List<Pair<Alarm, Long>>, now: Long): List<Alarm> {
        since.keys.retainAll(candidates.map { it.first.key }.toSet())
        return candidates.filter { (alarm, holdMs) ->
            now - since.getOrPut(alarm.key) { now } >= holdMs
        }.map { it.first }
    }
}

/** Bank-wide checks for batteries in parallel, using the BMS readings plus the Victron devices. */
object BankAnalysis {
    /** Below this total current the split between batteries is too noisy to judge. */
    const val MIN_SHARE_CURRENT_A = 10.0
    private const val SHARE_HOLD_MS = 120_000L
    private const val MISMATCH_HOLD_MS = 60_000L

    /**
     * Fraction of the bank's current each battery carries (null for slots without a BMS),
     * or null when it can't be judged: a battery offline, too little current, or batteries
     * flowing in opposite directions (e.g. one charging another after a load stops).
     */
    fun currentShares(batteries: List<BatteryState>, now: Long): List<Double?>? {
        val configured = batteries.filter { it.isConfigured }
        if (configured.size < 2 || configured.any { !it.isLive(now) }) return null
        val total = configured.sumOf { it.basic!!.current }
        if (abs(total) < MIN_SHARE_CURRENT_A) return null
        if (configured.any { val i = it.basic!!.current; abs(i) > 0.5 && (i > 0) != (total > 0) }) return null
        return batteries.map { b -> if (b.isConfigured) b.basic!!.current / total else null }
    }

    fun evaluate(
        batteries: List<BatteryState>,
        shunt: VictronDeviceState,
        solar: VictronDeviceState,
        t: AlarmThresholds,
        now: Long,
        tracker: SustainTracker,
    ): List<Alarm> {
        val immediate = ArrayList<Alarm>()
        val sustained = ArrayList<Pair<Alarm, Long>>()

        victronLinkAlarms("shunt", "SmartShunt", shunt, t, now, immediate)
        victronLinkAlarms("solar", "Solar charger", solar, t, now, immediate)

        val s = shunt.shunt?.takeIf { shunt.isLive(now) }
        if (s != null) {
            for ((bit, label, severity) in VictronProtocol.shuntAlarms) {
                if (s.alarmBits and bit != 0) immediate += Alarm("shunt_alarm_$bit", "SmartShunt alarm: $label", severity)
            }
            val configured = batteries.filter { it.isConfigured }
            val shuntAmps = s.current
            if (t.shuntMismatchEnabled && shuntAmps != null && configured.isNotEmpty() && configured.all { it.isLive(now) }) {
                val bmsTotal = configured.sumOf { it.basic!!.current }
                if (abs(shuntAmps - bmsTotal) > max(5.0, 0.2 * abs(shuntAmps))) {
                    sustained += Alarm(
                        "shunt_mismatch",
                        String.format(
                            Locale.US, "Shunt reads %+.1f A but the batteries total %+.1f A", shuntAmps, bmsTotal,
                        ),
                        Severity.WARNING,
                    ) to MISMATCH_HOLD_MS
                }
            }
        }

        val pv = solar.solar?.takeIf { solar.isLive(now) }
        val err = pv?.error
        if (err != null && err != 0) {
            immediate += Alarm("solar_error_$err", "Solar charger: ${VictronProtocol.chargerErrorName(err)}", Severity.ALARM)
        }

        if (t.shareImbalanceEnabled) {
            val shares = currentShares(batteries, now)
            if (shares != null) {
                val present = shares.filterNotNull()
                val even = 1.0 / present.size
                if (present.any { abs(it - even) * 100 > t.shareImbalancePoints }) {
                    val split = present.joinToString(" / ") { "${(it * 100).roundToInt()}%" }
                    sustained += Alarm("share", "Uneven current sharing between batteries: $split", Severity.WARNING) to
                        SHARE_HOLD_MS
                }
            }
        }

        return immediate + tracker.filter(sustained, now)
    }

    private fun victronLinkAlarms(
        id: String,
        label: String,
        d: VictronDeviceState,
        t: AlarmThresholds,
        now: Long,
        out: MutableList<Alarm>,
    ) {
        if (!d.isConfigured || d.status == VictronStatus.STOPPED) return
        when (d.status) {
            VictronStatus.NO_KEY -> out += Alarm("${id}_key", "$label: enter its encryption key in Settings", Severity.WARNING)
            VictronStatus.WRONG_KEY -> out += Alarm("${id}_key", "$label: wrong encryption key", Severity.WARNING)
            VictronStatus.WRONG_TYPE -> out += Alarm("${id}_type", "$label: this device sends a different kind of data", Severity.WARNING)
            else -> {
                val last = max(d.lastUpdate, d.since)
                if (!d.isLive(now) && t.connectionLostEnabled && last > 0 &&
                    now - last >= t.connectionLostSeconds * 1000L
                ) {
                    out += Alarm("${id}_link", "$label not heard for ${Alarms.formatDuration(now - last)}", Severity.WARNING)
                }
            }
        }
    }
}
