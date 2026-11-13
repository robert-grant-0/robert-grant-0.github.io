package io.github.robertgrant0.bmsmonitor.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import io.github.robertgrant0.bmsmonitor.Permissions
import io.github.robertgrant0.bmsmonitor.bms.Alarms
import io.github.robertgrant0.bmsmonitor.bmsApp

/**
 * Dead-man's switch for the monitoring service. An alarm fires every few minutes, even
 * in doze; if the service is gone or its loop has stopped ticking, it is restarted, and if
 * that fails the user is told.
 */
object Watchdog {
    const val INTERVAL_MS = 5 * 60_000L
    private const val REQUEST_CODE = 7

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        val pi = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WatchdogReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.bmsApp
        val log = app.diagnostics
        if (!app.settings.current.monitoringEnabled || !Permissions.canConnect(context)) {
            log.info("watchdog", "Monitoring is off; watchdog idle")
            return
        }
        val now = System.currentTimeMillis()
        val status = app.health.status.value
        val stalled = app.health.isStalled(now, true)
        if (!status.serviceRunning || stalled) {
            val why = if (!status.serviceRunning) {
                "service not running"
            } else {
                "no check for ${Alarms.formatDuration(now - status.lastTick)}"
            }
            log.error("watchdog", "Monitoring stopped ($why); restarting")
            app.health.restarted()
            if (!BmsService.start(context, BmsService.ACTION_WATCHDOG)) {
                log.error("watchdog", "Android refused to restart the service")
                Notifications.postStalled(context)
            }
        } else {
            log.info("watchdog", "OK (last check ${Alarms.formatDuration(now - status.lastTick)} ago)")
        }
        Watchdog.schedule(context)
    }
}
