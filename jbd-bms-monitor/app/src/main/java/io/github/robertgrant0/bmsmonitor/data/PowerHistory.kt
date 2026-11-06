package io.github.robertgrant0.bmsmonitor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One point on the power chart. Watts; positive = into the batteries. */
data class PowerSample(val time: Long, val bankWatts: Float, val solarWatts: Float?)

/**
 * Recent bank (and solar) power for the home-screen chart. Kept in memory only:
 * a sample every [intervalMs] for the last [keepMs].
 */
class PowerHistory(
    private val intervalMs: Long = 15_000L,
    private val keepMs: Long = 6 * 3_600_000L,
) {
    private val buffer = ArrayDeque<PowerSample>()
    private val _samples = MutableStateFlow<List<PowerSample>>(emptyList())
    val samples: StateFlow<List<PowerSample>> = _samples.asStateFlow()

    /** Records a sample if at least [intervalMs] has passed since the last one. */
    @Synchronized
    fun offer(time: Long, bankWatts: Double?, solarWatts: Double?) {
        if (bankWatts == null) return
        val last = buffer.lastOrNull()
        if (last != null && time - last.time < intervalMs) return
        buffer.addLast(PowerSample(time, bankWatts.toFloat(), solarWatts?.toFloat()))
        while (buffer.isNotEmpty() && time - buffer.first().time > keepMs) buffer.removeFirst()
        _samples.value = buffer.toList()
    }

    companion object {
        fun window(samples: List<PowerSample>, now: Long, windowMs: Long): List<PowerSample> =
            samples.filter { now - it.time <= windowMs }
    }
}
