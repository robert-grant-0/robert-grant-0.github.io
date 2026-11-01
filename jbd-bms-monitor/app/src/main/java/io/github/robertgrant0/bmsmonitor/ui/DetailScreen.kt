@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.Protection

@Composable
fun DetailScreen(
    state: BatteryState,
    alarms: List<Alarm>,
    deviceName: String?,
    now: Long,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, fontWeight = FontWeight.SemiBold)
                        ConnectionBadge(state)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val b = state.basic
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (b == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No data yet – ${state.statusText.ifBlank { "waiting for the BMS" }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (maxWidth >= 840.dp) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewPanel(state, b, alarms, now)
                        ProtectionPanel(b)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CellsPanel(state, b)
                        InfoPanel(state, b, deviceName, now)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OverviewPanel(state, b, alarms, now)
                    CellsPanel(state, b)
                    ProtectionPanel(b)
                    InfoPanel(state, b, deviceName, now)
                }
            }
        }
    }
}

@Composable
private fun OverviewPanel(state: BatteryState, b: BasicInfo, alarms: List<Alarm>, now: Long) {
    val live = state.isLive(now)
    Panel(if (live) "Now" else "Last reading (${Fmt.ago(now - state.lastUpdate)})") {
        Row(
            Modifier.alpha(if (live) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SocGauge(b.stateOfCharge, 170.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowBanner(b)
                MetricGrid(
                    listOf(
                        MetricItem("Voltage", Fmt.volts(b.totalVoltage)),
                        MetricItem("Power", Fmt.watts(b.power)),
                        MetricItem("Remaining", Fmt.ah(b.remainingCapacity)),
                        MetricItem("Capacity", Fmt.ah(b.nominalCapacity)),
                    ),
                )
            }
        }
        MetricGrid(
            listOf(
                MetricItem("Estimate", timeEstimate(b) ?: "Idle"),
                MetricItem("Cycles", "${b.cycles}"),
            ) + b.temperatures.mapIndexed { i, t -> MetricItem("Temp ${i + 1}", Fmt.temp(t)) },
            columns = 3,
        )
        FetRow(b)
        SectionLabel("Alarms")
        AlarmList(alarms)
    }
}

@Composable
private fun CellsPanel(state: BatteryState, b: BasicInfo) {
    val cells = state.cells
    Panel("Cells (${cells.size})") {
        if (cells.isEmpty()) {
            Text("No cell data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CellBars(cells, b)
        }
    }
}

@Composable
private fun CellBars(cells: List<Double>, b: BasicInfo) {
    val status = LocalStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val min = cells.min()
        val max = cells.max()
        val spread = max - min
        MetricGrid(
            listOf(
                MetricItem("Lowest", "${Fmt.cell(min)} V", status.cellLow),
                MetricItem("Highest", "${Fmt.cell(max)} V", status.cellHigh),
                MetricItem("Average", "${Fmt.cell(cells.average())} V"),
                MetricItem("Difference", Fmt.mv(spread)),
            ),
            columns = 4,
        )
        Spacer(Modifier.height(4.dp))
        cells.forEachIndexed { i, v ->
            val color = when {
                spread >= 0.002 && v == max -> status.cellHigh
                spread >= 0.002 && v == min -> status.cellLow
                else -> MaterialTheme.colorScheme.primary
            }
            // Lowest cell shows a quarter bar, highest a full bar, so small differences stand out.
            val fraction = if (spread < 0.001) 1f else (0.25 + 0.75 * (v - min) / spread).toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${i + 1}",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Fmt.cell(v)} V",
                    modifier = Modifier.width(76.dp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(color),
                    )
                }
                Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) {
                    if (b.isBalancing(i)) Pill("BAL", status.warning)
                }
            }
        }
    }
}

@Composable
private fun ProtectionPanel(b: BasicInfo) {
    val status = LocalStatusColors.current
    val active = b.protections.toSet()
    Panel("BMS protection status") {
        for (row in Protection.entries.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (p in row) {
                    val on = p in active
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (on) status.alarm else status.ok)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            p.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) status.alarm else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoPanel(state: BatteryState, b: BasicInfo, deviceName: String?, now: Long) {
    val sw = b.softwareVersion
    Panel("BMS information") {
        MetricGrid(
            listOf(
                MetricItem("Bluetooth name", deviceName ?: "--"),
                MetricItem("Address", state.address ?: "--"),
                MetricItem("Signal", state.rssi?.let { "$it dBm" } ?: "--"),
                MetricItem("Hardware", state.hardwareVersion ?: "--"),
                MetricItem("Software", "${sw shr 4}.${sw and 0x0F}"),
                MetricItem("Manufactured", b.productionDate ?: "--"),
                MetricItem("Cells / sensors", "${b.cellCount}S / ${b.temperatures.size}"),
                MetricItem("Reconnects", "${state.reconnectCount}"),
                MetricItem("Last update", if (state.lastUpdate > 0) Fmt.ago(now - state.lastUpdate) else "--"),
            ),
        )
    }
}
