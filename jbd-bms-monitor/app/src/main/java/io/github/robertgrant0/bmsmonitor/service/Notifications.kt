package io.github.robertgrant0.bmsmonitor.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.robertgrant0.bmsmonitor.AlarmActivity
import io.github.robertgrant0.bmsmonitor.MainActivity
import io.github.robertgrant0.bmsmonitor.Permissions
import io.github.robertgrant0.bmsmonitor.R
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.label
import io.github.robertgrant0.bmsmonitor.bms.Severity
import java.util.Locale

object Notifications {
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALARMS = "alarms"
    const val CHANNEL_SIREN = "siren"
    const val ID_MONITOR = 1
    private const val ID_SIREN = 50
    private const val ID_STALLED = 60
    private const val ID_ALARM_BASE = 100

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Battery monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that the batteries are being monitored"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Battery alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "BMS protection events, low charge, temperature and connection alarms"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SIREN, "Battery siren", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen alarm while the siren sounds (the siren plays its own sound)"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    @SuppressLint("MissingPermission") // Checked with Permissions.canNotify.
    fun postSiren(ctx: Context, messages: List<String>) {
        if (!Permissions.canNotify(ctx)) return
        val fullScreen = PendingIntent.getActivity(
            ctx,
            2,
            Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val silence = PendingIntent.getBroadcast(
            ctx,
            3,
            Intent(ctx, SilenceReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_SIREN)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("⚠ BATTERY ALARM")
            .setContentText(messages.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(messages.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFD50000.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "SILENCE", silence)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_SIREN, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelSiren(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_SIREN)
    }

    /** Posted by the watchdog when the monitor died and could not be restarted in the background. */
    @SuppressLint("MissingPermission") // Checked with Permissions.canNotify.
    fun postStalled(ctx: Context) {
        if (!Permissions.canNotify(ctx)) return
        val text = "Android stopped the battery monitor and it couldn't be restarted in the background. " +
            "Tap to open the app, which restarts it."
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Battery monitoring has stopped")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setColor(0xFFE53935.toInt())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_STALLED, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelStalled(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_STALLED)
    }

    private fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun monitor(ctx: Context, batteries: List<BatteryState>, alarms: List<List<Alarm>>, now: Long): Notification {
        val configured = batteries.filter { it.isConfigured }
        val live = configured.filter { it.isLive(now) }
        val alarmCount = alarms.sumOf { it.size }

        val title = if (configured.isEmpty()) {
            "No batteries set up"
        } else {
            configured.joinToString("  ·  ") { b ->
                b.basic?.takeIf { b.isLive(now) }?.let { "${it.stateOfCharge}%" } ?: "--"
            }
        }
        val totalAmps = live.sumOf { it.basic!!.current }
        val totalWatts = live.sumOf { it.basic!!.power }
        val summary = buildString {
            append("${live.size}/${configured.size} connected")
            if (live.isNotEmpty()) {
                append(String.format(Locale.US, "  ·  %+.1f A  ·  %+.0f W", totalAmps, totalWatts))
            }
            if (alarmCount > 0) append("  ·  ⚠ $alarmCount alarm${if (alarmCount == 1) "" else "s"}")
        }
        val details = configured.joinToString("\n") { b ->
            val d = b.basic
            if (d != null && b.isLive(now)) {
                String.format(
                    Locale.US, "%s: %d%%  %.2f V  %+.2f A", b.name, d.stateOfCharge, d.totalVoltage, d.current,
                )
            } else {
                "${b.name}: ${b.statusText.ifBlank { b.link.label }}"
            }
        }

        val stop = PendingIntent.getService(
            ctx,
            1,
            Intent(ctx, BmsService::class.java).setAction(BmsService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(ctx, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$summary\n$details"))
            .setContentIntent(openAppIntent(ctx))
            .addAction(0, "Stop monitoring", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("MissingPermission") // Checked with Permissions.canNotify.
    fun postAlarm(ctx: Context, id: Int, name: String, active: List<Alarm>, fresh: List<Alarm>) {
        if (!Permissions.canNotify(ctx)) return
        val critical = fresh.any { it.severity == Severity.ALARM }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("${if (critical) "⚠ " else ""}$name: ${fresh.first().message}")
            .setContentText(fresh.joinToString(", ") { it.message })
            .setStyle(NotificationCompat.BigTextStyle().bigText(active.joinToString("\n") { "• ${it.message}" }))
            .setContentIntent(openAppIntent(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(if (critical) 0xFFE53935.toInt() else 0xFFFFA000.toInt())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_ALARM_BASE + id, notification)
        } catch (_: SecurityException) {
        }
    }
}
