@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.robertgrant0.bmsmonitor.bms.AlarmThresholds
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.data.AppSettings
import io.github.robertgrant0.bmsmonitor.data.SettingsRepository
import io.github.robertgrant0.bmsmonitor.data.ThemeMode
import io.github.robertgrant0.bmsmonitor.service.Notifications

@Composable
fun SettingsScreen(
    settings: AppSettings,
    batteries: List<BatteryState>,
    repo: SettingsRepository,
    unrestrictedBackground: Boolean,
    versionName: String,
    onRequestUnrestricted: () -> Unit,
    onChooseDevice: (Int) -> Unit,
    onBack: () -> Unit,
) {
    fun thresholds(transform: (AlarmThresholds) -> AlarmThresholds) =
        repo.update { it.copy(thresholds = transform(it.thresholds)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Panel("Batteries") {
                    settings.slots.forEachIndexed { i, slot ->
                        if (i > 0) HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = slot.name,
                                onValueChange = { name -> repo.updateSlot(i) { it.copy(name = name.take(30)) } },
                                label = { Text("Battery ${i + 1} name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        when {
                                            slot.address == null -> "No BMS assigned"
                                            slot.deviceName != null -> slot.deviceName
                                            else -> "BMS"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (slot.address != null) {
                                        Text(
                                            "${slot.address} · ${Notifications.linkLabel(batteries[i].link)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (slot.address != null) {
                                    TextButton(onClick = {
                                        repo.updateSlot(i) { it.copy(address = null, deviceName = null) }
                                    }) { Text("Remove") }
                                    Spacer(Modifier.width(8.dp))
                                }
                                OutlinedButton(onClick = { onChooseDevice(i) }) {
                                    Text(if (slot.address == null) "Choose BMS" else "Change")
                                }
                            }
                        }
                    }
                }

                Panel("Connection") {
                    SwitchRow(
                        "Keep batteries connected",
                        "Stays connected in the background and reconnects automatically",
                        settings.monitoringEnabled,
                    ) { on -> repo.update { it.copy(monitoringEnabled = on) } }
                    StepperRow(
                        title = "Refresh every",
                        value = "${settings.pollIntervalSec} s",
                        canDecrease = settings.pollIntervalSec > 1,
                        canIncrease = settings.pollIntervalSec < 30,
                        onDecrease = { repo.update { it.copy(pollIntervalSec = it.pollIntervalSec - 1) } },
                        onIncrease = { repo.update { it.copy(pollIntervalSec = it.pollIntervalSec + 1) } },
                    )
                    SwitchRow(
                        "Keep screen on",
                        "While the app is open – useful for a wall-mounted tablet",
                        settings.keepScreenOn,
                    ) { on -> repo.update { it.copy(keepScreenOn = on) } }
                    SwitchRow(
                        "Keep updating with the screen off",
                        "Keeps the tablet's processor awake so readings and alarms continue. Uses more tablet battery.",
                        settings.keepAwake,
                    ) { on -> repo.update { it.copy(keepAwake = on) } }
                    SwitchRow(
                        "Start when the tablet boots",
                        null,
                        settings.startOnBoot,
                    ) { on -> repo.update { it.copy(startOnBoot = on) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Unrestricted background use", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (unrestrictedBackground) {
                                    "Allowed – Android won't put the app to sleep"
                                } else {
                                    "Android may pause the app to save power and drop the connections"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (unrestrictedBackground) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Allowed", tint = LocalStatusColors.current.ok)
                        } else {
                            Button(onClick = onRequestUnrestricted) { Text("Allow") }
                        }
                    }
                }

                Panel("Alarms") {
                    val t = settings.thresholds
                    SwitchRow(
                        "Alarm notifications",
                        "Notify with sound when a new alarm starts",
                        settings.alarmNotifications,
                    ) { on -> repo.update { it.copy(alarmNotifications = on) } }
                    Text(
                        "BMS protection events (over/under-voltage, over-current, short circuit, " +
                            "temperature protection) and a switched-off discharge MOSFET always count as alarms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ThresholdRow(
                        "Low charge at or below", t.lowSocEnabled, t.lowSocPercent, "%", 5..90, 5,
                        onEnabled = { on -> thresholds { it.copy(lowSocEnabled = on) } },
                        onValue = { v -> thresholds { it.copy(lowSocPercent = v) } },
                    )
                    ThresholdRow(
                        "High temperature at or above", t.highTempEnabled, t.highTempC, "°C", 30..80, 1,
                        onEnabled = { on -> thresholds { it.copy(highTempEnabled = on) } },
                        onValue = { v -> thresholds { it.copy(highTempC = v) } },
                    )
                    ThresholdRow(
                        "Low temperature at or below", t.lowTempEnabled, t.lowTempC, "°C", -20..15, 1,
                        onEnabled = { on -> thresholds { it.copy(lowTempEnabled = on) } },
                        onValue = { v -> thresholds { it.copy(lowTempC = v) } },
                    )
                    ThresholdRow(
                        "Cell difference at or above", t.cellDeltaEnabled, t.cellDeltaMv, "mV", 20..500, 10,
                        onEnabled = { on -> thresholds { it.copy(cellDeltaEnabled = on) } },
                        onValue = { v -> thresholds { it.copy(cellDeltaMv = v) } },
                    )
                    ThresholdRow(
                        "Not connected for", t.connectionLostEnabled, t.connectionLostSeconds, "s", 15..900, 15,
                        onEnabled = { on -> thresholds { it.copy(connectionLostEnabled = on) } },
                        onValue = { v -> thresholds { it.copy(connectionLostSeconds = v) } },
                    )
                }

                Panel("Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (mode in ThemeMode.entries) {
                            Row(
                                Modifier.clickable { repo.update { it.copy(theme = mode) } },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = settings.theme == mode,
                                    onClick = { repo.update { it.copy(theme = mode) } },
                                )
                                Text(
                                    when (mode) {
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.SYSTEM -> "Follow system"
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    }
                }

                Text(
                    "BMS Monitor $versionName · reads JBD / Xiaoxiang Bluetooth BMS units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDecrease, enabled = canDecrease) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 80.dp),
        )
        FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Stepper(value, canDecrease, canIncrease, onDecrease, onIncrease)
    }
}

@Composable
private fun ThresholdRow(
    title: String,
    enabled: Boolean,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int,
    onEnabled: (Boolean) -> Unit,
    onValue: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, onCheckedChange = onEnabled)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Stepper(
            value = "$value $unit",
            canDecrease = enabled && value > range.first,
            canIncrease = enabled && value < range.last,
            onDecrease = { onValue((value - step).coerceIn(range)) },
            onIncrease = { onValue((value + step).coerceIn(range)) },
        )
    }
}
