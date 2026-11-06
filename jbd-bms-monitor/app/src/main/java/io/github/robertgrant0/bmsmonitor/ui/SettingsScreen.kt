package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robertgrant0.bmsmonitor.bms.AlarmThresholds
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bms.VictronProtocol
import io.github.robertgrant0.bmsmonitor.bms.VictronStatus
import io.github.robertgrant0.bmsmonitor.data.AppSettings
import io.github.robertgrant0.bmsmonitor.data.FlowUnit
import io.github.robertgrant0.bmsmonitor.data.ThemeMode
import io.github.robertgrant0.bmsmonitor.data.VictronKind
import io.github.robertgrant0.bmsmonitor.data.VictronSlot

@Composable
fun SettingsScreen(
    settings: AppSettings,
    batteries: List<BatteryState>,
    victronStates: Map<VictronKind, VictronDeviceState>,
    unrestrictedBackground: Boolean,
    fullScreenAlarmAllowed: Boolean,
    versionName: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onChooseDevice: (Int) -> Unit,
    onChooseVictron: (VictronKind) -> Unit,
    onRequestUnrestricted: () -> Unit,
    onAllowFullScreenAlarm: () -> Unit,
    onTestSiren: () -> Unit,
    onSilenceSiren: () -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    fun thresholds(transform: (AlarmThresholds) -> AlarmThresholds) = onUpdate { it.withThresholds(transform) }

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
                    .widthIn(max = 780.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(14.dp))
                    Text("Settings", style = Type.title(28.sp), color = p.t1)
                }

                SettingsGroup("Batteries", footer = "Tap Choose to scan for a BMS. A BMS only appears while no other phone is connected to it.") {
                    settings.slots.forEachIndexed { i, slot ->
                        if (i > 0) GroupDivider()
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(settings.slotName(i), style = Type.text(15.sp, FontWeight.SemiBold), color = p.t1)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (slot.address == null) {
                                            "No BMS chosen"
                                        } else {
                                            listOfNotNull(slot.deviceName, slot.address, linkText(batteries[i].link)).joinToString(" · ")
                                        },
                                        style = Type.text(12.5.sp),
                                        color = p.t2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (slot.address != null) {
                                    TextAction("Remove", p.t2) { onUpdate { it.withSlot(i) { c -> c.copy(address = null, deviceName = null) } } }
                                    Spacer(Modifier.width(6.dp))
                                }
                                PillButton(if (slot.address == null) "Choose" else "Change", { onChooseDevice(i) }, filled = true)
                            }
                            Spacer(Modifier.height(12.dp))
                            StyledField(
                                value = slot.name,
                                onValueChange = { name -> onUpdate { it.withSlot(i) { c -> c.copy(name = name.take(30)) } } },
                                label = "Name shown on the dashboard",
                            )
                        }
                    }
                }

                SettingsGroup(
                    "Victron devices",
                    footer = "Read from Victron Instant Readout broadcasts, so VictronConnect keeps working. Find each key in " +
                        "VictronConnect: open the device → ⚙ → ⋮ → Product info → Instant readout details → Show. " +
                        "Keys never leave this tablet.",
                ) {
                    VictronKind.entries.forEachIndexed { index, kind ->
                        if (index > 0) GroupDivider()
                        VictronEditor(
                            kind = kind,
                            slot = settings.victron(kind),
                            state = victronStates[kind] ?: VictronDeviceState(),
                            onChoose = { onChooseVictron(kind) },
                            onRemove = { onUpdate { it.withVictron(kind) { VictronSlot() } } },
                            onKey = { key -> onUpdate { it.withVictron(kind) { v -> v.copy(key = key.trim()) } } },
                        )
                    }
                }

                SettingsGroup("Connection") {
                    SwitchSetting(
                        "Keep batteries connected",
                        "Stays connected in the background and reconnects automatically",
                        settings.monitoringEnabled,
                    ) { on -> onUpdate { it.copy(monitoringEnabled = on) } }
                    GroupDivider()
                    SettingRow("Refresh every") {
                        Stepper(
                            "${settings.pollIntervalSec} s",
                            canDecrease = settings.pollIntervalSec > 1,
                            canIncrease = settings.pollIntervalSec < 30,
                            onDecrease = { onUpdate { it.copy(pollIntervalSec = it.pollIntervalSec - 1) } },
                            onIncrease = { onUpdate { it.copy(pollIntervalSec = it.pollIntervalSec + 1) } },
                        )
                    }
                    GroupDivider()
                    SwitchSetting("Keep screen on", "While the app is open – ideal for a wall-mounted tablet", settings.keepScreenOn) { on ->
                        onUpdate { it.copy(keepScreenOn = on) }
                    }
                    GroupDivider()
                    SwitchSetting(
                        "Keep updating with the screen off",
                        "Keeps the processor awake so readings and alarms continue. Uses more tablet battery.",
                        settings.keepAwake,
                    ) { on -> onUpdate { it.copy(keepAwake = on) } }
                    GroupDivider()
                    SwitchSetting("Start when the tablet boots", null, settings.startOnBoot) { on -> onUpdate { it.copy(startOnBoot = on) } }
                    GroupDivider()
                    SettingRow(
                        "Unrestricted background use",
                        subtitle = if (unrestrictedBackground) {
                            "Allowed – Android won't put the app to sleep"
                        } else {
                            "Android may pause the app to save power and drop the connections"
                        },
                    ) {
                        if (unrestrictedBackground) {
                            Icon(Icons.Filled.CheckCircle, "Allowed", tint = p.green, modifier = Modifier.size(22.dp))
                        } else {
                            PillButton("Allow", onRequestUnrestricted, filled = true)
                        }
                    }
                }

                SettingsGroup(
                    "Siren",
                    footer = "The siren plays through the alarm volume – it sounds even in silent mode – and keeps going until " +
                        "you press Silence. It's used for BMS protections, a switched-off discharge MOSFET, high temperature, " +
                        "SmartShunt alarms and solar charger errors.",
                ) {
                    SwitchSetting("Loud siren for faults", null, settings.sirenEnabled) { on ->
                        onUpdate { it.copy(sirenEnabled = on) }
                        if (!on) onSilenceSiren()
                    }
                    GroupDivider()
                    SwitchSetting(
                        "Siren for warnings too",
                        "Low charge, low temperature, cell imbalance, uneven sharing, disconnected devices",
                        settings.sirenForWarnings,
                    ) { on -> onUpdate { it.copy(sirenForWarnings = on) } }
                    GroupDivider()
                    SwitchSetting(
                        "Full alarm volume",
                        "Turns the alarm volume to maximum while the siren sounds, then puts it back",
                        settings.sirenFullVolume,
                    ) { on -> onUpdate { it.copy(sirenFullVolume = on) } }
                    if (!fullScreenAlarmAllowed) {
                        GroupDivider()
                        SettingRow(
                            "Full-screen alarm blocked",
                            subtitle = "Allow it so the alarm can wake the screen. The siren sounds either way.",
                        ) { PillButton("Allow", onAllowFullScreenAlarm, color = p.orange, filled = true) }
                    }
                    GroupDivider()
                    SettingRow("Test the siren", subtitle = "Sounds it now – press Silence to stop") {
                        PillButton("Test", onTestSiren, color = p.red)
                    }
                }

                val t = settings.thresholds
                SettingsGroup(
                    "Alarms",
                    footer = "BMS protection events always raise an alarm. These extra checks can be tuned or switched off.",
                ) {
                    SwitchSetting("Alarm notifications", "A notification with sound when a new alarm starts", settings.alarmNotifications) { on ->
                        onUpdate { it.copy(alarmNotifications = on) }
                    }
                    GroupDivider()
                    ThresholdSetting("Low charge", "At or below", t.lowSocEnabled, t.lowSocPercent, "%", 5..90, 5,
                        { on -> thresholds { it.copy(lowSocEnabled = on) } }, { v -> thresholds { it.copy(lowSocPercent = v) } })
                    GroupDivider()
                    ThresholdSetting("High temperature", "At or above", t.highTempEnabled, t.highTempC, "°C", 30..80, 1,
                        { on -> thresholds { it.copy(highTempEnabled = on) } }, { v -> thresholds { it.copy(highTempC = v) } })
                    GroupDivider()
                    ThresholdSetting("Low temperature", "At or below", t.lowTempEnabled, t.lowTempC, "°C", -20..15, 1,
                        { on -> thresholds { it.copy(lowTempEnabled = on) } }, { v -> thresholds { it.copy(lowTempC = v) } })
                    GroupDivider()
                    ThresholdSetting("Cell imbalance", "Spread at or above", t.cellDeltaEnabled, t.cellDeltaMv, "mV", 20..500, 10,
                        { on -> thresholds { it.copy(cellDeltaEnabled = on) } }, { v -> thresholds { it.copy(cellDeltaMv = v) } })
                    GroupDivider()
                    ThresholdSetting(
                        "Uneven current sharing", "Points away from an even share, for 2 min", t.shareImbalanceEnabled,
                        t.shareImbalancePoints, "pts", 5..50, 5,
                        { on -> thresholds { it.copy(shareImbalanceEnabled = on) } }, { v -> thresholds { it.copy(shareImbalancePoints = v) } },
                    )
                    GroupDivider()
                    SwitchSetting(
                        "Shunt and batteries disagree",
                        "When the SmartShunt and the three batteries' total differ for a minute",
                        t.shuntMismatchEnabled,
                    ) { on -> thresholds { it.copy(shuntMismatchEnabled = on) } }
                    GroupDivider()
                    ThresholdSetting("Not connected", "For at least", t.connectionLostEnabled, t.connectionLostSeconds, "s", 15..900, 15,
                        { on -> thresholds { it.copy(connectionLostEnabled = on) } }, { v -> thresholds { it.copy(connectionLostSeconds = v) } })
                }

                SettingsGroup("Appearance") {
                    SettingRow("Theme") {
                        Segmented(
                            options = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "Auto"),
                            selected = settings.theme,
                            onSelect = { mode -> onUpdate { it.copy(theme = mode) } },
                        )
                    }
                    GroupDivider()
                    SettingRow("Energy flow in", subtitle = "The bank headline, flow diagram and chart") {
                        Segmented(
                            options = listOf(FlowUnit.WATTS to "Watts", FlowUnit.AMPS to "Amps"),
                            selected = settings.flowUnit,
                            onSelect = { u -> onUpdate { it.copy(flowUnit = u) } },
                        )
                    }
                    GroupDivider()
                    SwitchSetting(
                        "Animations",
                        "Charging waves and moving energy flow. Turn off to save power on slower tablets.",
                        settings.animations,
                    ) { on -> onUpdate { it.copy(animations = on) } }
                }

                Text(
                    "BMS Monitor $versionName · reads JBD / Xiaoxiang BMS and Victron devices · read-only",
                    style = Type.text(12.sp),
                    color = p.t3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

private fun linkText(link: LinkState): String = when (link) {
    LinkState.CONNECTED -> "connected"
    LinkState.CONNECTING -> "connecting…"
    LinkState.DISCONNECTED -> "reconnecting…"
    LinkState.BLUETOOTH_OFF -> "Bluetooth off"
    LinkState.NO_PERMISSION -> "no permission"
    LinkState.STOPPED -> "monitoring stopped"
    LinkState.NOT_CONFIGURED -> ""
}

@Composable
private fun VictronEditor(
    kind: VictronKind,
    slot: VictronSlot,
    state: VictronDeviceState,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onKey: (String) -> Unit,
) {
    val p = LocalPalette.current
    var showKey by remember { mutableStateOf(false) }
    val keyOk = VictronProtocol.parseKey(slot.key) != null
    val (statusText, statusColor) = when (state.status) {
        VictronStatus.RECEIVING -> "receiving" to p.green
        VictronStatus.WAITING -> "listening…" to p.t2
        VictronStatus.NO_KEY -> "key needed" to p.orange
        VictronStatus.WRONG_KEY -> "wrong key" to p.red
        VictronStatus.WRONG_TYPE -> "wrong device type" to p.red
        VictronStatus.STOPPED -> "monitoring stopped" to p.t2
        VictronStatus.NOT_CONFIGURED -> "" to p.t2
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = Type.text(15.sp, FontWeight.SemiBold), color = p.t1)
                Spacer(Modifier.height(2.dp))
                if (slot.address == null) {
                    Text("Not set up", style = Type.text(12.5.sp), color = p.t2)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${slot.deviceName ?: kind.label} · ${slot.address} · ",
                            style = Type.text(12.5.sp),
                            color = p.t2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(statusText, style = Type.text(12.5.sp, FontWeight.SemiBold), color = statusColor, maxLines = 1)
                    }
                }
            }
            if (slot.address != null) {
                TextAction("Remove", p.t2, onRemove)
                Spacer(Modifier.width(6.dp))
            }
            PillButton(if (slot.address == null) "Choose" else "Change", onChoose, filled = true)
        }
        if (slot.address != null) {
            Spacer(Modifier.height(12.dp))
            StyledField(
                value = slot.key,
                onValueChange = { onKey(it.take(40)) },
                label = "Encryption key (32 characters)",
                isError = slot.key.isNotBlank() && !keyOk,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = { TextAction(if (showKey) "Hide" else "Show", p.accent) { showKey = !showKey } },
            )
        }
    }
}

@Composable
private fun StyledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        textStyle = Type.text(15.sp, FontWeight.Medium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.t1,
            unfocusedTextColor = p.t1,
            focusedContainerColor = p.well,
            unfocusedContainerColor = p.well,
            focusedBorderColor = p.accent,
            unfocusedBorderColor = p.line,
            focusedLabelColor = p.accent,
            unfocusedLabelColor = p.t3,
            cursorColor = p.accent,
            errorBorderColor = p.red,
            errorLabelColor = p.red,
            errorContainerColor = p.well,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = Type.text(14.sp, FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchSetting(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle = subtitle, onClick = { onChange(!checked) }) {
        StyledSwitch(checked, onChange)
    }
}

@Composable
private fun StyledSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = p.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = p.t2,
            uncheckedTrackColor = p.t1.copy(alpha = 0.08f),
            uncheckedBorderColor = p.line,
        ),
    )
}

@Composable
private fun ThresholdSetting(
    title: String,
    caption: String,
    enabled: Boolean,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int,
    onEnabled: (Boolean) -> Unit,
    onValue: (Int) -> Unit,
) {
    SettingRow(title, subtitle = if (enabled) caption else "Off", enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(
                "$value $unit",
                canDecrease = enabled && value > range.first,
                canIncrease = enabled && value < range.last,
                onDecrease = { onValue((value - step).coerceIn(range)) },
                onIncrease = { onValue((value + step).coerceIn(range)) },
            )
            Spacer(Modifier.width(12.dp))
            StyledSwitch(enabled, onEnabled)
        }
    }
}

/** − value + in one pill. */
@Composable
private fun Stepper(value: String, canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .height(38.dp)
            .well(p, 19.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", canDecrease, onDecrease)
        Text(
            value,
            style = Type.figures(14.sp),
            color = if (canDecrease || canIncrease) p.t1 else p.t3,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
            maxLines = 1,
        )
        StepButton("+", canIncrease, onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = Type.text(20.sp, FontWeight.Medium), color = if (enabled) p.accent else p.t3)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .height(38.dp)
            .well(p, 19.dp)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((value, label) in options) {
            val on = value == selected
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .height(32.dp)
                    .clip(shape)
                    .then(
                        when {
                            on && p.flat -> Modifier.background(p.accent)
                            on -> Modifier
                                .background(p.card1)
                                .border(1.dp, p.line, shape)
                            else -> Modifier
                        },
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Type.text(13.5.sp, FontWeight.SemiBold),
                    color = if (on && p.flat) p.onAccent else if (on) p.t1 else p.t2,
                )
            }
        }
    }
}
