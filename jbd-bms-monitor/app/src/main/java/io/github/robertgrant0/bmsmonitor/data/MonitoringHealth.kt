package io.github.robertgrant0.bmsmonitor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Heartbeat of the monitoring service. The service ticks it on every pass of its loop;
 * the watchdog and the home screen use it to notice when monitoring has silently stopped.
 */
class MonitoringHealth(private val clock: () -> Long = System::currentTimeMillis) {
    data class Status(
        val serviceRunning: Boolean = false,
        /** Last pass of the service loop, epoch ms (0 = never). */
        val lastTick: Long = 0L,
        /** When this process started, epoch ms. */
        val startedAt: Long = 0L,
        /** Times the watchdog had to restart monitoring. */
        val restarts: Int = 0,
    )

    private val _status = MutableStateFlow(Status(startedAt = clock()))
    val status: StateFlow<Status> = _status.asStateFlow()

    fun serviceStarted() = _status.update { it.copy(serviceRunning = true, lastTick = clock()) }

    fun serviceStopped() = _status.update { it.copy(serviceRunning = false) }

    fun tick() = _status.update { it.copy(lastTick = clock()) }

    fun restarted() = _status.update { it.copy(restarts = it.restarts + 1) }

    /**
     * True when monitoring should be running but the service hasn't checked in: for
     * [STALL_AFTER_MS] since its last pass, or [STARTUP_GRACE_MS] since the process started
     * if it never ran at all.
     */
    fun isStalled(now: Long, monitoringEnabled: Boolean): Boolean {
        if (!monitoringEnabled) return false
        val s = _status.value
        return if (s.lastTick > 0) now - s.lastTick > STALL_AFTER_MS else now - s.startedAt > STARTUP_GRACE_MS
    }

    companion object {
        const val STALL_AFTER_MS = 3 * 60_000L
        const val STARTUP_GRACE_MS = 90_000L
    }
}
