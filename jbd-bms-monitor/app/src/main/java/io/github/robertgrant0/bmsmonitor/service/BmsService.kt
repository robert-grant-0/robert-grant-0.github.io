package io.github.robertgrant0.bmsmonitor.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import io.github.robertgrant0.bmsmonitor.bms.BankAnalysis
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.SirenLatch
import io.github.robertgrant0.bmsmonitor.bms.SustainTracker
import io.github.robertgrant0.bmsmonitor.ui.bankView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the BMS connections alive while the app is in the
 * background or the screen is off, keeps the status notification up to date, evaluates
 * alarms, and feeds the siren. It ticks [io.github.robertgrant0.bmsmonitor.data.MonitoringHealth]
 * so the watchdog can tell when it has died or stalled.
 */
class BmsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationText: String? = null

    // Alarm keys currently active per slot (index SLOT_COUNT = bank-wide), when each was
    // last notified, and which have been written to the diagnostics log.
    private val activeAlarms = Array(BatteryState.SLOT_COUNT + 1) { emptySet<String>() }
    private val bankTracker = SustainTracker()
    private val lastNotified = HashMap<String, Long>()
    private var loggedAlarms = emptySet<String>()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val name = when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> "on"
                BluetoothAdapter.STATE_OFF -> "off"
                BluetoothAdapter.STATE_TURNING_ON -> "turning on"
                BluetoothAdapter.STATE_TURNING_OFF -> "turning off"
                else -> "changed"
            }
            bmsApp.diagnostics.warn("bluetooth", "Adapter $name")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        bmsApp.diagnostics.info("service", "Service created")
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = bmsApp
        val log = app.diagnostics
        if (intent?.action == ACTION_STOP) {
            log.info("service", "Stopped from the notification")
            app.settings.update { it.copy(monitoringEnabled = false) }
            Watchdog.cancel(this)
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
            log.error("service", "Could not start in the foreground: ${e.message ?: e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!app.settings.current.monitoringEnabled || !Permissions.canConnect(this)) {
            log.info("service", "Not starting: monitoring off or no Bluetooth permission")
            Watchdog.cancel(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when {
            loopJob?.isActive != true -> {
                log.info(
                    "service",
                    when {
                        intent == null -> "Restarted by Android after being killed"
                        intent.action == ACTION_WATCHDOG -> "Started by the watchdog"
                        else -> "Monitoring started"
                    },
                )
                startLoop()
            }
            intent?.action == ACTION_WATCHDOG && app.health.isStalled(now, true) -> {
                log.error("service", "Loop had stalled; restarting it")
                app.health.restarted()
                loopJob?.cancel()
                startLoop()
            }
        }
        Notifications.cancelStalled(this)
        Watchdog.schedule(this)
        return START_STICKY
    }

    private fun startLoop() {
        val app = bmsApp
        app.manager.start()
        app.health.serviceStarted()
        loopJob = scope.launch { watchLoop() }
    }

    private suspend fun watchLoop() {
        val app = bmsApp
        val nm = getSystemService(NotificationManager::class.java)
        while (scope.isActive) {
            val settings = app.settings.current
            if (!settings.monitoringEnabled) {
                app.diagnostics.info("service", "Monitoring switched off")
                Watchdog.cancel(this)
                stopSelf()
                return
            }
            updateWakeLock(settings.keepAwake)

            val now = System.currentTimeMillis()
            val batteries = app.manager.batteries.value
            val alarms = batteries.map { Alarms.evaluate(it, settings.thresholds, now) }
            val bankAlarms = BankAnalysis.evaluate(
                batteries, app.manager.shunt.value, app.manager.solar.value, settings.thresholds, now, bankTracker,
            )
            app.bankAlarms.value = bankAlarms
            val bank = bankView(batteries, app.manager.shunt.value, app.manager.solar.value, now)
            app.history.offer(now, bank.watts, bank.solarWatts, bank.amps, bank.solarAmps)
            logAlarmChanges(batteries, alarms, bankAlarms)

            val notification = Notifications.monitor(this, batteries, alarms, now)
            val text = notification.extras.getCharSequence("android.bigText")?.toString()
            if (text != lastNotificationText) {
                lastNotificationText = text
                try {
                    nm?.notify(Notifications.ID_MONITOR, notification)
                } catch (_: SecurityException) {
                }
            }

            val sirenFor = { a: Alarm -> settings.sirenEnabled && SirenLatch.isSirenWorthy(a, settings.sirenForWarnings) }
            app.siren.update(
                buildMap {
                    batteries.forEachIndexed { i, b ->
                        for (a in alarms[i]) if (sirenFor(a)) put("$i:${a.key}", "${b.name}: ${a.message}")
                    }
                    for (a in bankAlarms) if (sirenFor(a)) put("bank:${a.key}", a.message)
                },
            )
            if (settings.alarmNotifications) raiseAlarms(batteries, alarms, bankAlarms, now, skip = sirenFor)
            app.health.tick()
            delay(LOOP_MS)
        }
    }

    private fun logAlarmChanges(batteries: List<BatteryState>, alarms: List<List<Alarm>>, bankAlarms: List<Alarm>) {
        val current = HashMap<String, String>()
        batteries.forEachIndexed { i, b -> for (a in alarms[i]) current["$i:${a.key}"] = "${b.name}: ${a.message}" }
        for (a in bankAlarms) current["bank:${a.key}"] = a.message
        val log = bmsApp.diagnostics
        for ((key, message) in current) if (key !in loggedAlarms) log.warn("alarm", message)
        for (key in loggedAlarms) if (key !in current) log.info("alarm", "Cleared: ${key.substringAfter(':')}")
        loggedAlarms = current.keys.toSet()
    }

    private fun raiseAlarms(
        batteries: List<BatteryState>,
        alarms: List<List<Alarm>>,
        bankAlarms: List<Alarm>,
        now: Long,
        skip: (Alarm) -> Boolean,
    ) {
        for (i in 0..batteries.size) {
            val active = if (i < batteries.size) alarms[i] else bankAlarms
            // Alarms that sound the siren get the full-screen siren notification instead.
            val fresh = active.filter { a ->
                !skip(a) && a.key !in activeAlarms[i] && now - (lastNotified["$i:${a.key}"] ?: 0L) > RENOTIFY_AFTER_MS
            }
            activeAlarms[i] = active.map { it.key }.toSet()
            if (fresh.isNotEmpty()) {
                fresh.forEach { lastNotified["$i:${it.key}"] = now }
                if (i < batteries.size) {
                    Notifications.postAlarm(this, batteries[i].slot, batteries[i].name, active, fresh)
                } else {
                    Notifications.postAlarm(this, BatteryState.SLOT_COUNT, "Battery bank", active, fresh)
                }
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
            bmsApp.diagnostics.info("service", "Wake lock acquired")
        } else if (!keepAwake && held) {
            wakeLock?.release()
            wakeLock = null
            bmsApp.diagnostics.info("service", "Wake lock released")
        }
    }

    override fun onDestroy() {
        val app = bmsApp
        app.diagnostics.info("service", "Service destroyed")
        scope.cancel()
        app.health.serviceStopped()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {
        }
        app.manager.stop()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.robertgrant0.bmsmonitor.STOP"
        const val ACTION_WATCHDOG = "io.github.robertgrant0.bmsmonitor.WATCHDOG"
        private const val LOOP_MS = 2_000L
        private const val RENOTIFY_AFTER_MS = 10 * 60_000L

        /** Starts monitoring. Safe to call repeatedly. Returns false if Android refused. */
        fun start(context: Context, action: String? = null): Boolean {
            if (!Permissions.canConnect(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, BmsService::class.java).setAction(action))
                true
            } catch (e: Exception) {
                // Android 12+ refuses to start foreground services from the background in some cases.
                context.bmsApp.diagnostics.error("service", "Start refused: ${e.message ?: e.javaClass.simpleName}")
                false
            }
        }

        fun stop(context: Context) {
            Watchdog.cancel(context)
            context.stopService(Intent(context, BmsService::class.java))
        }
    }
}
