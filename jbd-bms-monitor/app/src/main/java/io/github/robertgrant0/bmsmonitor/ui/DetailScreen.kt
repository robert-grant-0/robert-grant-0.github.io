package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.Protection
import io.github.robertgrant0.bmsmonitor.bms.Severity
import java.util.Locale

@Composable
fun DetailScreen(
    state: BatteryState,
    alarms: List<Alarm>,
    deviceName: String?,
    share: Double?,
    now: Long,
    onBack: () -> Unit,
) {
    AuroraBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            val compact = maxWidth < 600.dp
            val side = if (compact) 16.dp else 24.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = side, end = side, top = 8.dp, bottom = 24.dp),
            ) {
                DetailHeader(state, deviceName, now, onBack, compact = compact)
                Spacer(Modifier.height(16.dp))
                val b = state.basic
                when {
                    b == null -> GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(28.dp)) {
                        Text("Waiting for data", style = Type.text(18.sp, FontWeight.SemiBold), color = LocalPalette.current.t1)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.statusText.ifBlank { "The BMS hasn't sent any readings yet." },
                            style = Type.text(14.sp),
                            color = LocalPalette.current.t2,
                        )
                    }
                    wide -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OverviewCard(state, b, alarms, share, now, Modifier.width(420.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CellsCard(state, b)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                ProtectionCard(b, Modifier.weight(1.25f).fillMaxHeight())
                                InfoCard(state, b, deviceName, now, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OverviewCard(state, b, alarms, share, now, Modifier.fillMaxWidth())
                        CellsCard(state, b)
                        ProtectionCard(b, Modifier.fillMaxWidth())
                        InfoCard(state, b, deviceName, now, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(state: BatteryState, deviceName: String?, now: Long, onBack: () -> Unit, compact: Boolean) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(state.name, style = Type.title(26.sp), color = p.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(deviceName, state.address, state.hardwareVersion?.let { "Hardware $it" })
            Text(
                details.joinToString(" · "),
                style = Type.text(13.sp),
                color = p.t2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        LinkPill(state, state.isLive(now))
        if (!compact && state.rssi != null) {
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .height(36.dp)
                    .well(p, 18.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalBars(state.rssi)
                Spacer(Modifier.width(8.dp))
                Text("${state.rssi} dBm", style = Type.figures(13.sp, FontWeight.Medium), color = p.t2)
            }
        }
    }
}

@Composable
private fun OverviewCard(
    state: BatteryState,
    b: BasicInfo,
    alarms: List<Alarm>,
    share: Double?,
    now: Long,
    modifier: Modifier,
) {
    val p = LocalPalette.current
    val live = state.isLive(now)
    val tone = when (alarms.maxOfOrNull { it.severity }) {
        Severity.ALARM -> Tone.ALARM
        Severity.WARNING -> Tone.WARNING
        null -> Tone.NORMAL
    }
    val flowColor = when (powerFlow(b.current)) {
        PowerFlow.CHARGING -> p.green
        PowerFlow.DISCHARGING -> p.sky
        PowerFlow.IDLE -> p.t2
    }
    GlassCard(modifier, tone = tone, padding = PaddingValues(20.dp)) {
        Row(Modifier.alpha(if (live) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
            BatteryGlyph(b.stateOfCharge, live && b.current > IDLE_CURRENT_A, width = 76.dp, height = 136.dp)
            Spacer(Modifier.width(24.dp))
            Column {
                ValueUnit(b.stateOfCharge.toString(), "%", Type.number(64.sp), 24.sp)
                Spacer(Modifier.height(6.dp))
                ValueUnit(Fmt.signed(b.current, 2), "A", Type.figures(24.sp), 15.sp, color = flowColor, unitColor = flowColor.copy(alpha = 0.8f))
                Text(
                    listOfNotNull(Fmt.watts(b.power), Fmt.volts(b.totalVoltage), timeEstimate(b)).joinToString(" · "),
                    style = Type.figures(14.sp, FontWeight.Normal),
                    color = p.t2,
                )
            }
        }
        if (!live && state.lastUpdate > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Last reading ${Fmt.ago(now - state.lastUpdate)} · ${state.statusText}", style = Type.text(13.sp), color = p.t2)
        }
        if (alarms.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in alarms.sortedByDescending { it.severity }) AlarmStrip(a.message, a.severity)
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyValueRow("Remaining", "${Fmt.ah(b.remainingCapacity)} of ${Fmt.ah(b.nominalCapacity)}")
        KeyValueRow(
            "Temperature${if (b.temperatures.size > 1) "s" else ""}",
            b.temperatures.joinToString(" · ") { String.format(Locale.US, "%.1f°", it) }.ifEmpty { "--" },
        )
        KeyValueRow("Cycles", "${b.cycles}")
        KeyValueRow("Share of bank current", share?.let { "${Math.round(it * 100)}%" } ?: "--")
        val balancing = (0 until state.cells.size).filter { b.isBalancing(it) }
        KeyValueRow("Balancing", if (balancing.isEmpty()) "Off" else balancing.joinToString(", ") { "Cell ${it + 1}" })
        KeyValueRow("MOSFETs", "", divider = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FetChip("CHARGE", b.chargeFetOn)
                FetChip("DISCHARGE", b.dischargeFetOn)
            }
        }
    }
}

@Composable
private fun CellsCard(state: BatteryState, b: BasicInfo) {
    val p = LocalPalette.current
    val cells = state.cells
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Cells · ${cells.size.takeIf { it > 0 } ?: b.cellCount}S", Modifier.weight(1f))
            if (cells.size >= 2) {
                val mn = cells.min()
                val mx = cells.max()
                val spread = mx - mn
                val spreadColor = if (spread >= 0.015) p.orange else p.t1
                Text(
                    buildAnnotatedString {
                        append("Low ")
                        withStyle(SpanStyle(color = p.sky, fontWeight = FontWeight.SemiBold)) { append(Fmt.cell(mn)) }
                        append(" · High ")
                        withStyle(SpanStyle(color = p.orange, fontWeight = FontWeight.SemiBold)) { append(Fmt.cell(mx)) }
                        append(" · Avg ")
                        withStyle(SpanStyle(color = p.t1, fontWeight = FontWeight.SemiBold)) { append("${Fmt.cell(cells.average())} V") }
                        append(" · Spread ")
                        withStyle(SpanStyle(color = spreadColor, fontWeight = FontWeight.SemiBold)) { append(Fmt.mv(spread)) }
                    },
                    style = Type.figures(13.sp, FontWeight.Normal),
                    color = p.t2,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        if (cells.isEmpty()) {
            Text("No cell readings yet", style = Type.text(14.sp), color = p.t2)
        } else {
            CellBars(
                cells, b::isBalancing,
                barHeight = 150.dp,
                gap = if (cells.size > 8) 6.dp else 18.dp,
                big = true,
            )
        }
    }
}

@Composable
private fun ProtectionCard(b: BasicInfo, modifier: Modifier) {
    val p = LocalPalette.current
    val active = b.protections.toSet()
    GlassCard(modifier, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Protection status", Modifier.weight(1f))
            Text(
                if (active.isEmpty()) "All clear" else "${active.size} active",
                style = Type.text(12.sp, FontWeight.SemiBold),
                color = if (active.isEmpty()) p.green else p.red,
            )
        }
        Spacer(Modifier.height(12.dp))
        for (row in Protection.entries.chunked(2)) {
            Row(Modifier.fillMaxWidth()) {
                for (item in row) {
                    val on = item in active
                    Row(
                        Modifier
                            .weight(1f)
                            .height(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (on) p.red else p.green.copy(alpha = 0.7f)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            item.label,
                            style = Type.text(13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (on) p.red else p.t2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoCard(state: BatteryState, b: BasicInfo, deviceName: String?, now: Long, modifier: Modifier) {
    val sw = b.softwareVersion
    GlassCard(modifier, padding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Label("BMS")
        Spacer(Modifier.height(4.dp))
        KeyValueRow("Bluetooth name", deviceName ?: "--")
        KeyValueRow("Software", "${sw shr 4}.${sw and 0x0F}")
        KeyValueRow("Manufactured", b.productionDate ?: "--")
        KeyValueRow("Cells / sensors", "${b.cellCount}S / ${b.temperatures.size}")
        KeyValueRow("Reconnects", "${state.reconnectCount}")
        KeyValueRow("Last update", if (state.lastUpdate > 0) Fmt.ago(now - state.lastUpdate) else "--", divider = false)
    }
}
