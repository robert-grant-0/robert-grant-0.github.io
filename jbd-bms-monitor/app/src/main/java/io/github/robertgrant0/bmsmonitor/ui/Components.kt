package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.Severity

@Composable
fun socColor(soc: Int?): Color {
    val s = LocalStatusColors.current
    return when {
        soc == null -> s.idle
        soc <= 20 -> s.alarm
        soc <= 40 -> s.warning
        else -> s.ok
    }
}

/** Ring gauge showing state of charge. */
@Composable
fun SocGauge(soc: Int?, diameter: Dp, modifier: Modifier = Modifier, subtitle: String? = null) {
    val color = socColor(soc)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sweep by animateFloatAsState(targetValue = (soc ?: 0) / 100f, label = "soc")
    val density = LocalDensity.current
    val bigText = with(density) { (diameter * 0.27f).toSp() }
    val unitText = with(density) { (diameter * 0.11f).toSp() }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (soc != null && sweep > 0f) {
                drawArc(
                    color = color, startAngle = 135f, sweepAngle = 270f * sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = soc?.toString() ?: "--",
                    fontSize = bigText,
                    lineHeight = bigText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "%",
                    fontSize = unitText,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = diameter * 0.035f, start = 2.dp),
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

data class MetricItem(val label: String, val value: String, val color: Color = Color.Unspecified)

@Composable
fun Metric(item: MetricItem, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(
            text = item.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
        Text(
            text = item.value,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = item.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MetricGrid(items: List<MetricItem>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in items.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (item in row) Metric(item, Modifier.weight(1f))
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Big "CHARGING +12.34 A" banner. */
@Composable
fun FlowBanner(b: BasicInfo, modifier: Modifier = Modifier, share: Double? = null) {
    val status = LocalStatusColors.current
    val (label, color) = when (powerFlow(b.current)) {
        PowerFlow.CHARGING -> "CHARGING" to status.charging
        PowerFlow.DISCHARGING -> "DISCHARGING" to status.discharging
        PowerFlow.IDLE -> "IDLE" to status.idle
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = Fmt.amps(b.current),
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (share != null) {
                Text(
                    text = "${Math.round(share * 100)}% of bank current",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun FetRow(b: BasicInfo, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Charge ${if (b.chargeFetOn) "ON" else "OFF"}", if (b.chargeFetOn) status.ok else status.alarm)
        Pill("Discharge ${if (b.dischargeFetOn) "ON" else "OFF"}", if (b.dischargeFetOn) status.ok else status.alarm)
    }
}

@Composable
fun ConnectionBadge(state: BatteryState, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val (color, label) = when (state.link) {
        LinkState.CONNECTED -> status.ok to (state.rssi?.let { "Connected · $it dBm" } ?: "Connected")
        LinkState.CONNECTING -> status.warning to "Connecting…"
        LinkState.DISCONNECTED -> status.alarm to "Reconnecting…"
        LinkState.BLUETOOTH_OFF -> status.alarm to "Bluetooth off"
        LinkState.NO_PERMISSION -> status.alarm to "No permission"
        LinkState.STOPPED -> status.idle to "Stopped"
        LinkState.NOT_CONFIGURED -> status.idle to "Not set up"
    }
    val pulsing = state.link == LinkState.CONNECTING || state.link == LinkState.DISCONNECTED
    val dotAlpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val a by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        a
    } else {
        1f
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(color, Modifier.alpha(dotAlpha))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/** Grid of cell voltages; lowest cell in blue, highest in orange, balancing cells marked with a dot. */
@Composable
fun CellGrid(cells: List<Double>, isBalancing: (Int) -> Boolean, modifier: Modifier = Modifier, columns: Int = 4) {
    val status = LocalStatusColors.current
    val min = cells.minOrNull() ?: 0.0
    val max = cells.maxOrNull() ?: 0.0
    val spread = cells.size >= 2 && max - min >= 0.002
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in cells.withIndex().chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((i, v) in row) {
                    val color = when {
                        spread && v == max -> status.cellHigh
                        spread && v == min -> status.cellLow
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isBalancing(i)) {
                                    Spacer(Modifier.width(4.dp))
                                    Dot(status.warning, size = 6.dp)
                                }
                            }
                            Text(
                                Fmt.cell(v),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                                maxLines = 1,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun AlarmList(alarms: List<Alarm>, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (alarms.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = status.ok, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("No alarms", color = status.ok, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            for (a in alarms.sortedByDescending { it.severity }) {
                val c = if (a.severity == Severity.ALARM) status.alarm else status.warning
                Surface(
                    color = c.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(a.message, color = c, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
