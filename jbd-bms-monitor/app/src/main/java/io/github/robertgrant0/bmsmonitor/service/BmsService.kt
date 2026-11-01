package io.github.robertgrant0.bmsmonitor.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.robertgrant0.bmsmonitor.Permissions
import io.github.robertgrant0.bmsmonitor.bmsApp
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.Alarms
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the BMS connections alive while the app is in the
 * background or the screen is off, keeps the status notification up to date, and
 * raises alarm notifications.
 */
class BmsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private var lastNotificationText: String? = null

    // Alarm keys currently active per slot, and when each "slot:key" was last notified.
    private val activeAlarms = Array(BatteryState.SLOT_COUNT) { emptySet<String>() }
    private val lastNotified = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = bmsApp
        if (intent?.action == ACTION_STOP) {
            app.settings.update { it.copy(monitoringEnabled = false) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val now = System.currentTimeMillis()
        val batteries = app.manager.batteries.value
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_MONITOR,
                Notifications.monitor(this, batteries, batteries.map { emptyList<Alarm>() }, now),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Missing Bluetooth permission or a background-start restriction.
            stopSelf()
            return START_NOT_STICKY
        }

        if (!app.settings.current.monitoringEnabled || !Permissions.canConnect(this)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            app.manager.start()
            scope.launch { watchLoop() }
        }
        return START_STICKY
    }

    private suspend fun watchLoop() {
        val app = bmsApp
        val nm = getSystemService(NotificationManager::class.java)
        while (scope.isActive) {
            val settings = app.settings.current
            if (!settings.monitoringEnabled) {
                stopSelf()
                return
            }
            updateWakeLock(settings.keepAwake)

            val now = System.currentTimeMillis()
            val batteries = app.manager.batteries.value
            val alarms = batteries.map { Alarms.evaluate(it, settings.thresholds, now) }

            val notification = Notifications.monitor(this, batteries, alarms, now)
            val text = notification.extras.getCharSequence("android.bigText")?.toString()
            if (text != lastNotificationText) {
                lastNotificationText = text
                try {
                    nm?.notify(Notifications.ID_MONITOR, notification)
                } catch (_: SecurityException) {
                }
            }

            if (settings.alarmNotifications) raiseAlarms(batteries, alarms, now)
            delay(2_000)
        }
    }

    private fun raiseAlarms(batteries: List<BatteryState>, alarms: List<List<Alarm>>, now: Long) {
        for (i in batteries.indices) {
            val active = alarms[i]
            val fresh = active.filter { a ->
                a.key !in activeAlarms[i] && now - (lastNotified["$i:${a.key}"] ?: 0L) > RENOTIFY_AFTER_MS
            }
            activeAlarms[i] = active.map { it.key }.toSet()
            if (fresh.isNotEmpty()) {
                fresh.forEach { lastNotified["$i:${it.key}"] = now }
                Notifications.postAlarm(this, batteries[i], active, fresh)
            }
        }
    }

    @SuppressLint("WakelockTimeout") // Deliberately held for as long as monitoring runs.
    private fun updateWakeLock(keepAwake: Boolean) {
        val held = wakeLock?.isHeld == true
        if (keepAwake && !held) {
            val pm = getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BmsMonitor:poll").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!keepAwake && held) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    override fun onDestroy() {
        scope.cancel()
        bmsApp.manager.stop()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.robertgrant0.bmsmonitor.STOP"
        private const val RENOTIFY_AFTER_MS = 10 * 60_000L

        /** Starts monitoring. Safe to call repeatedly. */
        fun start(context: Context) {
            if (!Permissions.canConnect(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, BmsService::class.java))
            } catch (_: Exception) {
                // Android 12+ refuses to start foreground services from the background in some cases.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BmsService::class.java))
        }
    }
}
