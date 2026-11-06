@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BankAnalysis
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bms.VictronProtocol
import io.github.robertgrant0.bmsmonitor.bms.VictronStatus
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.Severity

@Composable
fun HomeScreen(
    batteries: List<BatteryState>,
    alarms: List<List<Alarm>>,
    now: Long,
    cellDeltaWarnMv: Int?,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    bankAlarms: List<Alarm>,
    banners: @Composable ColumnScope.() -> Unit,
    onOpenDetail: (Int) -> Unit,
    onSetup: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Monitor", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            banners()
            val shares = BankAnalysis.currentShares(batteries, now)
            SummaryBar(bankSummary(batteries, now), alarms, bankAlarms, shunt, solar, now)
            if (bankAlarms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AlarmList(bankAlarms)
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (maxWidth >= 600.dp) {
                    // Tablet: the three batteries side by side.
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        batteries.forEachIndexed { i, b ->
                            BatteryCard(
                                state = b,
                                alarms = alarms[i],
                                now = now,
                                cellDeltaWarnMv = cellDeltaWarnMv,
                                share = shares?.getOrNull(i),
                                scrollable = true,
                                onClick = { if (b.isConfigured) onOpenDetail(i) else onSetup(i) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    // Phone: stacked.
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        batteries.forEachIndexed { i, b ->
                            BatteryCard(
                                state = b,
                                alarms = alarms[i],
                                now = now,
                                cellDeltaWarnMv = cellDeltaWarnMv,
                                share = shares?.getOrNull(i),
                                scrollable = false,
                                onClick = { if (b.isConfigured) onOpenDetail(i) else onSetup(i) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(
    s: BankSummary,
    alarms: List<List<Alarm>>,
    bankAlarms: List<Alarm>,
    shunt: VictronDeviceState,
    solar: VictronDeviceState,
    now: Long,
) {
    val status = LocalStatusColors.current
    val all = alarms.flatten() + bankAlarms
    val alarmColor = when {
        all.any { it.severity == Severity.ALARM } -> status.alarm
        all.isNotEmpty() -> status.warning
        else -> status.ok
    }
    fun flowColor(amps: Double?): Color = when {
        amps == null -> Color.Unspecified
        powerFlow(amps) == PowerFlow.CHARGING -> status.charging
        powerFlow(amps) == PowerFlow.DISCHARGING -> status.discharging
        else -> Color.Unspecified
    }

    val sh = shunt.shunt?.takeIf { shunt.isLive(now) }
    val pv = solar.solar?.takeIf { solar.isLive(now) }
    val hasLive = s.liveCount > 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sh != null) {
                    // The SmartShunt measures the whole bank directly, so it leads.
                    val soc = sh.stateOfCharge?.let { Math.round(it).toInt() }
                    val watts = if (sh.voltage != null && sh.current != null) sh.voltage * sh.current else null
                    Metric(MetricItem("Bank charge", soc?.let { "$it%" } ?: "--", socColor(soc)), large = true)
                    Metric(MetricItem("Bank current", sh.current?.let { Fmt.amps(it) } ?: "--", flowColor(sh.current)), large = true)
                    Metric(MetricItem("Bank power", watts?.let { Fmt.watts(it) } ?: "--", flowColor(sh.current)), large = true)
                    Metric(MetricItem("Bank voltage", sh.voltage?.let { Fmt.volts(it) } ?: "--"), large = true)
                    Metric(MetricItem("Used", sh.consumedAh?.let { Fmt.ah(-it) } ?: "--"), large = true)
                    Metric(
                        MetricItem(
                            "Time to go",
                            sh.remainingMinutes?.let { Fmt.hours(it / 60.0) } ?: if (hasLive) s.estimate ?: "Idle" else "--",
                        ),
                        large = true,
                    )
                } else {
                    val amps = if (hasLive) s.current else null
                    Metric(MetricItem("Bank charge", s.socPercent?.let { "$it%" } ?: "--", socColor(s.socPercent)), large = true)
                    Metric(MetricItem("Total current", amps?.let { Fmt.amps(it) } ?: "--", flowColor(amps)), large = true)
                    Metric(MetricItem("Total power", if (hasLive) Fmt.watts(s.power) else "--", flowColor(amps)), large = true)
                    Metric(
                        MetricItem("Remaining", if (hasLive) "${Fmt.ah(s.remainingAh)} of ${Fmt.ah(s.nominalAh)}" else "--"),
                        large = true,
                    )
                    Metric(MetricItem("Estimate", if (hasLive) s.estimate ?: "Idle" else "--"), large = true)
                }
                Metric(
                    MetricItem(
                        "Batteries",
                        "${s.liveCount} / ${s.configuredCount}",
                        if (s.liveCount == s.configuredCount && s.configuredCount > 0) status.ok else status.warning,
                    ),
                    large = true,
                )
                Metric(MetricItem("Alarms", if (all.isEmpty()) "None" else "${all.size}", alarmColor), large = true)
            }
            if (shunt.isConfigured || solar.isConfigured) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (solar.isConfigured) {
                        Metric(MetricItem("Solar", pv?.solarPower?.let { "$it W" } ?: victronStatusText(solar, now), status.charging))
                        if (pv != null) {
                            Metric(MetricItem("Solar charging", pv.batteryCurrent?.let { Fmt.amps(it) } ?: "--"))
                            Metric(MetricItem("Charger state", VictronProtocol.chargeStateName(pv.chargeState)))
                            Metric(
                                MetricItem(
                                    "Solar today",
                                    pv.yieldTodayWh?.let { String.format(java.util.Locale.US, "%.2f kWh", it / 1000.0) } ?: "--",
                                ),
                            )
                        }
                    }
                    val other = BankAnalysis.otherCurrent(shunt, solar, now)
                    if (other != null) {
                        Metric(MetricItem("Other (DC-DC − loads)", Fmt.amps(other), flowColor(other)))
                    }
                    if (shunt.isConfigured && sh == null) {
                        Metric(MetricItem("SmartShunt", victronStatusText(shunt, now), status.warning))
                    }
                }
            }
        }
    }
}

private fun victronStatusText(d: VictronDeviceState, now: Long): String = when (d.status) {
    VictronStatus.NOT_CONFIGURED -> "Not set up"
    VictronStatus.NO_KEY -> "Key needed"
    VictronStatus.STOPPED -> "Stopped"
    VictronStatus.WAITING -> "Listening…"
    VictronStatus.WRONG_KEY -> "Wrong key"
    VictronStatus.WRONG_TYPE -> "Wrong device type"
    VictronStatus.RECEIVING -> if (d.isLive(now)) "Receiving" else "Not heard ${Fmt.ago(now - d.lastUpdate)}"
}

@Composable
private fun BatteryCard(
    state: BatteryState,
    alarms: List<Alarm>,
    now: Long,
    cellDeltaWarnMv: Int?,
    share: Double?,
    scrollable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val border = when (alarms.maxOfOrNull { it.severity }) {
        Severity.ALARM -> BorderStroke(2.dp, status.alarm)
        Severity.WARNING -> BorderStroke(2.dp, status.warning)
        null -> null
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = border,
    ) {
        if (!state.isConfigured) {
            EmptySlot(state.name)
        } else {
            val scroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CardHeader(state)
                BatteryReadings(state, now, cellDeltaWarnMv, share)
                SectionLabel("Alarms")
                AlarmList(alarms)
            }
        }
    }
}

@Composable
private fun CardHeader(state: BatteryState) {
    Column {
        Text(
            state.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        ConnectionBadge(state)
        if (!state.isConnected && state.statusText.isNotBlank()) {
            Text(
                state.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun BatteryReadings(state: BatteryState, now: Long, cellDeltaWarnMv: Int?, share: Double?) {
    val status = LocalStatusColors.current
    val b = state.basic
    if (b == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.link == LinkState.CONNECTING || state.link == LinkState.CONNECTED) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "Waiting for data…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    val live = state.isLive(now)
    val delta = state.cellDelta
    val deltaColor = if (delta != null && cellDeltaWarnMv != null && delta * 1000 >= cellDeltaWarnMv) {
        status.warning
    } else {
        Color.Unspecified
    }
    val temps = when (b.temperatures.size) {
        0 -> "--"
        1 -> Fmt.temp(b.temperatures[0])
        else -> "${Fmt.temp(b.temperatures.max())} max"
    }

    Column(
        Modifier.alpha(if (live) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SocGauge(
                soc = b.stateOfCharge,
                diameter = 150.dp,
                subtitle = "${Fmt.ah(b.remainingCapacity)} / ${Fmt.ah(b.nominalCapacity)}",
            )
        }
        FlowBanner(b, share = share)
        MetricGrid(
            listOf(
                MetricItem("Voltage", Fmt.volts(b.totalVoltage)),
                MetricItem("Power", Fmt.watts(b.power)),
                MetricItem("Time", timeEstimate(b) ?: "Idle"),
                MetricItem("Temp", temps),
                MetricItem("Cell Δ", delta?.let { Fmt.mv(it) } ?: "--", deltaColor),
                MetricItem("Cycles", "${b.cycles}"),
            ),
        )
        FetRow(b)
        if (state.cells.isNotEmpty()) {
            SectionLabel("Cells (${state.cells.size})")
            CellGrid(state.cells, b::isBalancing, columns = 4)
        }
    }
    if (!live) {
        Text(
            "Showing last data from ${Fmt.ago(now - state.lastUpdate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySlot(name: String) {
    Column(
        Modifier
            .fillMaxSize()
            .heightIn(min = 220.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap to connect a JBD BMS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Slim banner with an optional action, used for "Bluetooth is off" and similar. */
@Composable
fun Banner(
    text: String,
    color: Color,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
            if (actionLabel != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onAction) { Text(actionLabel, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
