package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.robertgrant0.bmsmonitor.bms.Alarms
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import io.github.robertgrant0.bmsmonitor.data.LogEntry
import io.github.robertgrant0.bmsmonitor.data.LogLevel
import io.github.robertgrant0.bmsmonitor.data.MonitoringHealth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_SHOWN = 400

@Composable
fun DiagnosticsScreen(
    batteries: List<BatteryState>,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    health: MonitoringHealth.Status,
    log: DiagnosticsLog,
    now: Long,
    versionName: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    val revision by log.revision.collectAsStateWithLifecycle()
    val entries = remember(revision) { log.snapshot().asReversed().take(MAX_SHOWN) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    fun ago(t: Long) = if (t > 0) "${Alarms.formatDuration(now - t)} ago" else "never"

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Diagnostics", style = Type.title(28.sp), color = p.t1)
                        Text(
                            "What the app has been doing. Share this when something misbehaves.",
                            style = Type.text(13.sp),
                            color = p.t2,
                        )
                    }
                }

                SettingsGroup("Monitoring") {
                    KeyValueRow(
                        "Service", if (health.serviceRunning) "Running" else "Not running",
                        Modifier.padding(horizontal = 18.dp), valueColor = if (health.serviceRunning) p.green else p.red,
                    )
                    KeyValueRow("Last check", ago(health.lastTick), Modifier.padding(horizontal = 18.dp))
                    KeyValueRow("Watchdog restarts", "${health.restarts}", Modifier.padding(horizontal = 18.dp))
                    KeyValueRow("App version", versionName, Modifier.padding(horizontal = 18.dp), divider = false)
                }

                SettingsGroup("Devices") {
                    for (b in batteries) {
                        val detail = when {
                            !b.isConfigured -> "No BMS chosen"
                            else -> "${linkText(b.link)} · last read ${ago(b.lastUpdate)} · ${b.reconnectCount} reconnects" +
                                (b.rssi?.let { " · $it dBm" } ?: "")
                        }
                        SettingRow(b.name, subtitle = "${b.address ?: ""}${if (b.address != null) " · " else ""}$detail")
                        GroupDivider()
                    }
                    for ((label, d) in listOf("SmartShunt" to shunt, "Solar charger" to solar)) {
                        SettingRow(
                            label,
                            subtitle = if (!d.isConfigured) "Not set up" else "${d.address} · ${d.status.name.lowercase()} · last heard ${ago(d.lastUpdate)}",
                        )
                        if (label == "SmartShunt") GroupDivider()
                    }
                }

                SettingsGroup(
                    "Log",
                    footer = "Newest first. The last ${log.capacity} events are kept in memory and cleared when the app is closed.",
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PillButton("Share", onShare, filled = true)
                        PillButton("Copy", onCopy)
                        Spacer(Modifier.weight(1f))
                        PillButton("Clear", onClear, color = p.red)
                    }
                    GroupDivider()
                    if (entries.isEmpty()) {
                        Text(
                            "Nothing logged yet",
                            style = Type.text(14.sp),
                            color = p.t3,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                    for (e in entries) LogRow(e, timeFormat)
                }
            }
        }
    }
}

private fun linkText(link: LinkState): String = when (link) {
    LinkState.CONNECTED -> "connected"
    LinkState.CONNECTING -> "connecting"
    LinkState.DISCONNECTED -> "reconnecting"
    LinkState.BLUETOOTH_OFF -> "Bluetooth off"
    LinkState.NO_PERMISSION -> "no permission"
    LinkState.STOPPED -> "stopped"
    LinkState.NOT_CONFIGURED -> "not set up"
}

@Composable
private fun LogRow(e: LogEntry, timeFormat: SimpleDateFormat) {
    val p = LocalPalette.current
    val color = when (e.level) {
        LogLevel.INFO -> p.t3
        LogLevel.WARN -> p.orange
        LogLevel.ERROR -> p.red
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            timeFormat.format(Date(e.time)),
            style = Type.figures(12.sp, FontWeight.Normal),
            color = p.t3,
            modifier = Modifier.width(64.dp),
        )
        StatusDot(color, size = 6.dp)
        Spacer(Modifier.width(6.dp))
        Text(e.tag, style = Type.text(12.sp, FontWeight.SemiBold), color = p.t2, modifier = Modifier.width(72.dp))
        Text(
            e.message,
            style = Type.text(13.sp),
            color = if (e.level == LogLevel.ERROR) p.red else p.t1,
            modifier = Modifier.weight(1f),
        )
    }
}
