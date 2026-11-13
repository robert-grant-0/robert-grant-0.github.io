package io.github.robertgrant0.bmsmonitor.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.FileProvider
import io.github.robertgrant0.bmsmonitor.bms.Alarms
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bmsApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the shareable diagnostics text: device and settings summary, then the log. */
object DiagnosticsExport {
    fun text(context: Context): String {
        val app = context.bmsApp
        val s = app.settings.current
        val now = System.currentTimeMillis()
        val health = app.health.status.value
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val unrestricted = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        fun ago(t: Long) = if (t > 0) "${Alarms.formatDuration(now - t)} ago" else "never"

        val header = ArrayList<String>()
        header += "BMS Monitor $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL}"
        header += "Exported ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))}"
        header += "Monitoring ${if (s.monitoringEnabled) "on" else "off"} · poll ${s.pollIntervalSec} s · " +
            "keep awake ${s.keepAwake} · unrestricted background ${unrestricted} · siren ${s.sirenEnabled}"
        header += "Service running ${health.serviceRunning} · last check ${ago(health.lastTick)} · " +
            "watchdog restarts ${health.restarts}"
        header += ""
        header += "Batteries:"
        for (b in app.manager.batteries.value) {
            val d = b.basic
            header += "  ${b.slot + 1}. ${b.name}: ${b.address ?: "no BMS"} · ${Notifications.linkLabel(b.link)} · " +
                "last read ${ago(b.lastUpdate)} · reconnects ${b.reconnectCount} · rssi ${b.rssi ?: "-"} · " +
                "hw ${b.hardwareVersion ?: "-"}" +
                if (d != null) " · ${d.stateOfCharge}% ${d.totalVoltage} V ${d.current} A cells ${b.cells.size}" else ""
        }
        header += "Victron:"
        header += "  SmartShunt: ${victronLine(app.manager.shunt.value, ::ago)}"
        header += "  Solar charger: ${victronLine(app.manager.solar.value, ::ago)}"
        return app.diagnostics.export(header)
    }

    private fun victronLine(d: VictronDeviceState, ago: (Long) -> String): String =
        if (!d.isConfigured) "not set up" else "${d.address} · ${d.status.name.lowercase()} · last heard ${ago(d.lastUpdate)}"

    /** Opens the system share sheet with the diagnostics as a text file. */
    fun share(context: Context) {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, "bms-monitor-diagnostics.txt")
        file.writeText(text(context))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "BMS Monitor diagnostics")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, "Share diagnostics"))
        } catch (_: Exception) {
        }
    }

    fun copy(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("BMS Monitor diagnostics", text(context).takeLast(MAX_CLIPBOARD_CHARS)))
    }

    private const val MAX_CLIPBOARD_CHARS = 100_000
}
