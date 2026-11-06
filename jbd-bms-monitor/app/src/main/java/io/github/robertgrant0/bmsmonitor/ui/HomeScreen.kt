package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BankAnalysis
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.Severity
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bms.VictronStatus
import io.github.robertgrant0.bmsmonitor.data.FlowUnit
import io.github.robertgrant0.bmsmonitor.data.PowerSample
import java.util.Locale

private const val CHART_WINDOW_MS = 3 * 3_600_000L

@Composable
fun HomeScreen(
    batteries: List<BatteryState>,
    alarms: List<List<Alarm>>,
    bankAlarms: List<Alarm>,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    history: List<PowerSample>,
    now: Long,
    cellDeltaWarnMv: Int?,
    banners: @Composable ColumnScope.() -> Unit,
    unit: FlowUnit = FlowUnit.WATTS,
    onOpenDetail: (Int) -> Unit,
    onSetup: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val view = bankView(batteries, shunt, solar, now)
    val shares = BankAnalysis.currentShares(batteries, now)
    AuroraBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val phone = maxWidth < 600.dp
            val side = if (phone) 16.dp else 24.dp
            val contentWidth = maxWidth - side * 2
            val cardWidth = if (phone) maxWidth - side * 2 else (maxWidth - side * 2 - 32.dp) / 3
            val statColumns = if (cardWidth < 330.dp && !phone) 2 else 4
            val statIcons = cardWidth >= 400.dp || statColumns == 2
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = side, end = side, top = 8.dp, bottom = 24.dp),
            ) {
                Header(batteries, shunt, solar, now, compact = phone, onOpenSettings = onOpenSettings)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { banners() }
                BankHero(view, unit, shunt, solar, history, now, contentWidth, phone)
                if (bankAlarms.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in bankAlarms.sortedByDescending { it.severity }) AlarmStrip(a.message, a.severity)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (phone) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        batteries.forEachIndexed { i, b ->
                            BatteryCard(
                                b, alarms[i], now, cellDeltaWarnMv, shares?.getOrNull(i), statColumns, statIcons,
                                onClick = { if (b.isConfigured) onOpenDetail(i) else onSetup(i) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        batteries.forEachIndexed { i, b ->
                            BatteryCard(
                                b, alarms[i], now, cellDeltaWarnMv, shares?.getOrNull(i), statColumns, statIcons,
                                onClick = { if (b.isConfigured) onOpenDetail(i) else onSetup(i) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    batteries: List<BatteryState>,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    now: Long,
    compact: Boolean,
    onOpenSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val configured = batteries.filter { it.isConfigured }
    val live = configured.count { it.isLive(now) }
    val latest = (configured.map { it.lastUpdate } + shunt.lastUpdate).maxOrNull() ?: 0L
    val subtitle = when {
        configured.isEmpty() -> "No batteries set up yet"
        latest == 0L -> "Connecting to ${configured.size} batter${if (configured.size == 1) "y" else "ies"}…"
        else -> "$live of ${configured.size} connected · Updated ${Fmt.ago(now - latest)}"
    }
    val dot = when {
        configured.isEmpty() -> p.t3
        live == configured.size -> p.green
        live == 0 -> p.red
        else -> p.orange
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Batteries", style = Type.title(if (compact) 26.sp else 30.sp), color = p.t1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dot, pulse = live > 0)
                Spacer(Modifier.width(4.dp))
                Text(subtitle, style = Type.figures(13.sp, FontWeight.Normal), color = p.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!compact) {
            for (d in listOf(shunt to "SmartShunt", solar to "Solar")) {
                if (d.first.isConfigured) {
                    HeaderChip(d.second, victronDot(p, d.first, now))
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
        CircleButton(Icons.Filled.Settings, "Settings", onOpenSettings)
    }
}

private fun victronDot(p: Palette, d: VictronDeviceState, now: Long): Color = when {
    d.isLive(now) -> p.green
    d.status == VictronStatus.WRONG_KEY || d.status == VictronStatus.NO_KEY -> p.red
    d.status == VictronStatus.STOPPED -> p.t3
    else -> p.orange
}

@Composable
private fun BankHero(
    view: BankView,
    unit: FlowUnit,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    history: List<PowerSample>,
    now: Long,
    width: Dp,
    phone: Boolean,
) {
    val showFlow = solar.isConfigured
    GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(if (phone) 16.dp else 18.dp)) {
        if (phone) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocRing(view.soc, 124.dp, caption = "Bank")
                Spacer(Modifier.width(14.dp))
                HeroMetrics(view, unit, compact = true)
            }
            if (showFlow) {
                Spacer(Modifier.height(14.dp))
                FlowWell(view, unit, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            ChartWell(history, now, unit, Modifier.fillMaxWidth())
        } else {
            val wide = width >= 1100.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SocRing(view.soc, if (wide) 160.dp else 140.dp, caption = if (view.fromShunt) "Bank · Shunt" else "Bank")
                HeroMetrics(view, unit, compact = false, modifier = Modifier.widthIn(min = 230.dp))
                when {
                    wide && showFlow -> {
                        FlowWell(view, unit, Modifier.width(330.dp).fillMaxHeight())
                        ChartWell(history, now, unit, Modifier.weight(1f).fillMaxHeight())
                    }
                    showFlow -> FlowWell(view, unit, Modifier.weight(1f).fillMaxHeight())
                    else -> ChartWell(history, now, unit, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun HeroMetrics(view: BankView, unit: FlowUnit, compact: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val (state, color) = flowLabel(p, view.amps)
    val amps = view.amps?.let { Fmt.amps(it) }
    val watts = view.watts?.let { Fmt.signedWatts(it) }
    val volts = view.volts?.let { Fmt.volts(it) }
    val estimate = view.estimate ?: if (view.amps != null) "Idle" else null
    val bigStyle = Type.number(if (compact) 36.sp else 44.sp)
    val unitSize = if (compact) 17.sp else 20.sp
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Pill(state, color, pulse = view.amps != null && kotlin.math.abs(view.amps) > IDLE_CURRENT_A)
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        if (unit == FlowUnit.AMPS) {
            ValueUnit(view.amps?.let { Fmt.signed(it, 1) } ?: "--", "A", bigStyle, unitSize)
        } else {
            ValueUnit(view.watts?.let { Fmt.signed(it, 0) } ?: "--", "W", bigStyle, unitSize)
        }
        Text(
            listOfNotNull(if (unit == FlowUnit.AMPS) watts else amps, volts).joinToString(" · ").ifEmpty { "Waiting for data" },
            style = Type.figures(if (compact) 14.sp else 15.sp, FontWeight.Normal),
            color = p.t2,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        val rem = view.remainingAh
        val cap = view.capacityAh
        if (compact) {
            // One line on phones, where two side-by-side columns ran out of room.
            val remaining = when {
                rem != null && cap != null -> "${Math.round(rem)} of ${Math.round(cap)} Ah"
                rem != null -> Fmt.ah(rem)
                else -> null
            }
            Text(
                listOfNotNull(remaining, estimate).joinToString(" · ").ifEmpty { "--" },
                style = Type.figures(13.5.sp, FontWeight.Medium),
                color = p.t1,
                maxLines = 2,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Column {
                    Label("Remaining")
                    Spacer(Modifier.height(4.dp))
                    if (rem != null && cap != null) {
                        ValueUnit(Math.round(rem).toString(), "of ${Math.round(cap)} Ah", Type.figures(15.sp), 15.sp, unitGap = " ")
                    } else {
                        Text(rem?.let { Fmt.ah(it) } ?: "--", style = Type.figures(15.sp), color = p.t1)
                    }
                }
                Column {
                    Label("Estimate")
                    Spacer(Modifier.height(4.dp))
                    Text(estimate ?: "--", style = Type.figures(15.sp), color = p.t1, maxLines = 1)
                }
            }
        }
    }
}

private fun flowLabel(p: Palette, amps: Double?): Pair<String, Color> = when {
    amps == null -> "No data" to p.t3
    amps > IDLE_CURRENT_A -> "Charging" to p.green
    amps < -IDLE_CURRENT_A -> "Discharging" to p.sky
    else -> "Idle" to p.t2
}

@Composable
private fun FlowWell(view: BankView, unit: FlowUnit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .well(p)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Energy flow", Modifier.weight(1f))
            view.solarTodayKwh?.let {
                Text(String.format(Locale.US, "Solar today %.2f kWh", it), style = Type.figures(12.sp, FontWeight.Medium), color = p.t3)
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        EnergyFlow(
            solar = if (unit == FlowUnit.AMPS) view.solarAmps else view.solarWatts,
            bank = if (unit == FlowUnit.AMPS) view.amps else view.watts,
            other = if (unit == FlowUnit.AMPS) view.otherAmps else view.otherWatts,
            unit = unit,
            solarCaption = view.solarState?.let { "Solar · $it" } ?: "Solar",
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ChartWell(history: List<PowerSample>, now: Long, unit: FlowUnit, modifier: Modifier) {
    val p = LocalPalette.current
    val amps = unit == FlowUnit.AMPS
    val last = history.lastOrNull()?.let { if (amps) it.bankAmps else it.bankWatts }
    Column(
        modifier
            .well(p)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(if (amps) "Bank current · last 3 h" else "Bank power · last 3 h", Modifier.weight(1f))
            if (last != null) {
                val v = last.toDouble()
                Text(
                    "${if (amps) Fmt.signedAmps(v) else Fmt.signedWatts(v)} now",
                    style = Type.figures(12.sp),
                    color = if (last >= 0) p.green else p.sky,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        PowerChart(history, now, CHART_WINDOW_MS, unit = unit)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (t in listOf("3 h ago", "2 h", "1 h", "Now")) {
                Text(t, style = Type.text(11.sp, FontWeight.Medium), color = p.t3)
            }
        }
    }
}

@Composable
fun AlarmStrip(message: String, severity: Severity, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val c = if (severity == Severity.ALARM) p.red else p.orange
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.20f), c.copy(alpha = 0.06f))))
            .border(1.dp, c.copy(alpha = 0.32f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = c, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = Type.text(13.5.sp, FontWeight.SemiBold), color = c)
    }
}

@Composable
private fun BatteryCard(
    state: BatteryState,
    alarms: List<Alarm>,
    now: Long,
    cellDeltaWarnMv: Int?,
    share: Double?,
    statColumns: Int,
    statIcons: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isConfigured) {
        EmptyCard(state.name, onClick, modifier)
        return
    }
    val p = LocalPalette.current
    val tone = when (alarms.maxOfOrNull { it.severity }) {
        Severity.ALARM -> Tone.ALARM
        Severity.WARNING -> Tone.WARNING
        null -> Tone.NORMAL
    }
    val b = state.basic
    val live = state.isLive(now)
    GlassCard(modifier, tone = tone, onClick = onClick, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.name,
                style = Type.text(19.sp, FontWeight.SemiBold),
                color = p.t1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LinkPill(state, live)
        }

        Row(
            Modifier.alpha(if (live || b == null) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val charging = live && b != null && b.current > IDLE_CURRENT_A
            BatteryGlyph(b?.stateOfCharge, charging)
            Spacer(Modifier.width(18.dp))
            if (b == null) {
                Column {
                    Text("Waiting for data", style = Type.text(16.sp, FontWeight.SemiBold), color = p.t1)
                    Spacer(Modifier.height(4.dp))
                    Text(state.statusText.ifBlank { "Connecting…" }, style = Type.text(13.sp), color = p.t2)
                }
            } else {
                val flowColor = when (powerFlow(b.current)) {
                    PowerFlow.CHARGING -> p.green
                    PowerFlow.DISCHARGING -> p.sky
                    PowerFlow.IDLE -> p.t2
                }
                Column {
                    ValueUnit(b.stateOfCharge.toString(), "%", Type.number(46.sp), 20.sp)
                    Spacer(Modifier.height(4.dp))
                    ValueUnit(Fmt.signed(b.current, 2), "A", Type.figures(19.sp), 13.sp, color = flowColor, unitColor = flowColor.copy(alpha = 0.8f))
                    Text(
                        "${Fmt.watts(b.power)} · ${Fmt.volts(b.totalVoltage)}",
                        style = Type.figures(13.sp, FontWeight.Normal),
                        color = p.t2,
                        maxLines = 1,
                    )
                    Text(timeEstimate(b) ?: "Idle", style = Type.figures(13.sp, FontWeight.Normal), color = p.t2, maxLines = 1)
                }
            }
        }

        if (b != null) {
            Column(Modifier.alpha(if (live) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StatsGrid(state, cellDeltaWarnMv, share, statColumns, statIcons)
                if (state.cells.isNotEmpty()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("Cells", Modifier.weight(1f))
                            Text(
                                "${Fmt.cell(state.cellMin ?: 0.0)} – ${Fmt.cell(state.cellMax ?: 0.0)} V",
                                style = Type.figures(12.sp, FontWeight.Medium),
                                color = p.t3,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        CellBars(state.cells, b::isBalancing)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        CardFooter(state, alarms, live, now)
    }
}

@Composable
fun LinkPill(state: BatteryState, live: Boolean) {
    val p = LocalPalette.current
    val b = state.basic
    when (state.link) {
        LinkState.CONNECTED -> if (b != null && live) {
            val (text, color) = flowLabel(p, b.current)
            Pill(text, color, pulse = color != p.t2)
        } else {
            Pill("Connected", p.green)
        }
        LinkState.CONNECTING -> Pill("Connecting", p.sky, pulse = true)
        LinkState.DISCONNECTED -> Pill("Reconnecting", p.orange, pulse = true)
        LinkState.BLUETOOTH_OFF -> Pill("Bluetooth off", p.red)
        LinkState.NO_PERMISSION -> Pill("No permission", p.red)
        LinkState.STOPPED -> Pill("Stopped", p.t2)
        LinkState.NOT_CONFIGURED -> Pill("Not set up", p.t3)
    }
}

@Composable
private fun StatsGrid(state: BatteryState, cellDeltaWarnMv: Int?, share: Double?, columns: Int, icons: Boolean) {
    val p = LocalPalette.current
    val b = state.basic ?: return
    val delta = state.cellDelta
    val deltaWarn = delta != null && cellDeltaWarnMv != null && delta * 1000 >= cellDeltaWarnMv
    val maxTemp = b.temperatures.maxOrNull()
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { m -> StatTile(AppIcons.Thermometer, "Temp", maxTemp?.let { String.format(Locale.US, "%.1f°", it) } ?: "--", m, showIcon = icons) },
        { m ->
            StatTile(
                AppIcons.Delta, "Cell Δ", delta?.let { Fmt.mv(it) } ?: "--", m,
                valueColor = if (deltaWarn) p.orange else p.t1,
                iconColor = if (deltaWarn) p.orange else p.t3,
                showIcon = icons,
            )
        },
        { m -> StatTile(AppIcons.Cycles, "Cycles", "${b.cycles}", m, showIcon = icons) },
        { m -> StatTile(AppIcons.Share, "Share", share?.let { "${Math.round(it * 100)}%" } ?: "--", m, showIcon = icons) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in tiles.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tile in row) tile(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CardFooter(state: BatteryState, alarms: List<Alarm>, live: Boolean, now: Long) {
    val p = LocalPalette.current
    val worst = alarms.maxByOrNull { it.severity }
    val accent = when (worst?.severity) {
        Severity.ALARM -> p.red
        Severity.WARNING -> p.orange
        null -> null
    }
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 40.dp)
        .clip(shape)
    val styled = if (accent != null) {
        base
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.06f))))
            .border(1.dp, accent.copy(alpha = 0.32f), shape)
    } else {
        base.well(p, 14.dp)
    }
    Row(styled.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            worst != null -> {
                Icon(Icons.Filled.Warning, null, tint = accent!!, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                val more = if (alarms.size > 1) "  +${alarms.size - 1}" else ""
                Text(
                    worst.message + more,
                    style = Type.text(13.sp, FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            !state.isConnected -> {
                if (state.link == LinkState.CONNECTING || state.link == LinkState.DISCONNECTED) {
                    StatusDot(if (state.link == LinkState.DISCONNECTED) p.orange else p.sky, pulse = true)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    state.statusText.ifBlank { "Not connected" } +
                        if (state.lastUpdate > 0) " · ${Fmt.ago(now - state.lastUpdate)}" else "",
                    style = Type.text(13.sp, FontWeight.Medium),
                    color = p.t2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                Icon(Icons.Filled.CheckCircle, null, tint = p.green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("All good", style = Type.text(13.sp, FontWeight.SemiBold), color = p.green, modifier = Modifier.weight(1f))
            }
        }
        if (live || state.rssi != null) {
            SignalBars(state.rssi)
            Spacer(Modifier.width(8.dp))
        }
        val b = state.basic
        if (b != null && live) {
            FetChip("CHG", b.chargeFetOn)
            Spacer(Modifier.width(6.dp))
            FetChip("DSG", b.dischargeFetOn)
        }
    }
}

@Composable
private fun EmptyCard(name: String, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .heightIn(min = 360.dp)
            .clip(shape)
            .dashedOutline(p.t1.copy(alpha = 0.14f), 28.dp)
            .clickable(onClick = onClick)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(p.green.copy(alpha = 0.10f))
                .border(1.dp, p.green.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = p.green, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Add $name", style = Type.text(18.sp, FontWeight.SemiBold), color = p.t1)
        Spacer(Modifier.height(4.dp))
        Text("Tap to connect a JBD BMS", style = Type.text(13.sp), color = p.t2)
    }
}
