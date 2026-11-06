package io.github.robertgrant0.bmsmonitor.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.location.LocationManagerCompat
import io.github.robertgrant0.bmsmonitor.bms.JbdProtocol
import io.github.robertgrant0.bmsmonitor.bms.VictronProtocol
import kotlinx.coroutines.delay

private data class FoundDevice(val address: String, val name: String?, val rssi: Int, val likelyBms: Boolean)

// Default JBD names look like "SP04S020", "DP04S007L4S100A" or "xiaoxiang BMS".
private val BMS_NAME = Regex("(?i)(xiaoxiang|jbd|bms|overkill|^[a-z]{2}\\d{2}s\\d{3})")

@SuppressLint("MissingPermission") // The dialog is only reachable once Bluetooth permissions are granted.
@Composable
fun ScanDialog(
    slotName: String,
    /** Address -> name of the battery slot it's already assigned to. */
    assigned: Map<String, String>,
    onDismiss: () -> Unit,
    onPick: (address: String, deviceName: String?) -> Unit,
    /** When set, list only Victron devices broadcasting this Instant Readout record type. */
    victronRecord: Int? = null,
) {
    val context = LocalContext.current
    val found = remember { mutableStateMapOf<String, FoundDevice>() }
    var round by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }

    val locationOff = Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
        context.getSystemService(LocationManager::class.java)?.let { !LocationManagerCompat.isLocationEnabled(it) } == true

    if (scanning) {
        DisposableEffect(round) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            val scanner = adapter?.bluetoothLeScanner
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val address = result.device.address.uppercase()
                    val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
                    if (victronRecord != null) {
                        val data = result.scanRecord?.getManufacturerSpecificData(VictronProtocol.COMPANY_ID)
                        if (data == null || VictronProtocol.recordType(data) != victronRecord) return
                        found[address] = FoundDevice(address, name ?: found[address]?.name ?: "Victron device", result.rssi, true)
                        return
                    }
                    val advertisesJbd = result.scanRecord?.serviceUuids?.any { it.uuid == JbdProtocol.SERVICE_UUID } == true
                    val prev = found[address]
                    val finalName = name ?: prev?.name
                    found[address] = FoundDevice(
                        address = address,
                        name = finalName,
                        rssi = result.rssi,
                        likelyBms = advertisesJbd || prev?.likelyBms == true ||
                            (finalName != null && BMS_NAME.containsMatchIn(finalName)),
                    )
                }

                override fun onScanFailed(errorCode: Int) {
                    error = "Scan failed (error $errorCode). Wait a few seconds and try again."
                    scanning = false
                }
            }
            if (adapter == null || !adapter.isEnabled || scanner == null) {
                error = "Bluetooth is off. Turn it on and scan again."
                scanning = false
            } else {
                try {
                    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                    scanner.startScan(emptyList<ScanFilter>(), settings, callback)
                } catch (e: Exception) {
                    error = "Could not scan: ${e.message}"
                    scanning = false
                }
            }
            onDispose {
                try {
                    scanner?.stopScan(callback)
                } catch (_: Exception) {
                }
            }
        }
        LaunchedEffect(round) {
            delay(20_000)
            scanning = false
        }
    }

    val list = found.values
        .filter { showAll || it.name != null }
        .sortedWith(compareByDescending<FoundDevice> { it.likelyBms }.thenByDescending { it.rssi })
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.94f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(pal.card1, pal.card2)))
                .border(1.dp, pal.line, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanning) {
                    RadarPulse(pal.accent)
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(pal.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Bluetooth, null, tint = pal.accent, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (victronRecord != null) "Choose your $slotName" else "Choose the BMS for $slotName",
                        style = Type.title(22.sp),
                        color = pal.t1,
                    )
                    Text(
                        if (scanning) "Searching nearby…" else "${found.size} device${if (found.size == 1) "" else "s"} found",
                        style = Type.text(13.sp),
                        color = pal.t2,
                    )
                }
            }
            Text(
                if (victronRecord != null) {
                    "Only Victron devices of this type are listed. If yours is missing, turn on Bluetooth " +
                        "and Instant Readout for it in VictronConnect."
                } else {
                    "A BMS only shows up while nothing else is connected to it – close the Xiaoxiang / " +
                        "JBD app on other phones first. Likely BMS units are listed first."
                },
                style = Type.text(13.5.sp),
                color = pal.t2,
            )
            if (locationOff) {
                Banner("Location is off. On this Android version Bluetooth scanning needs Location on.", pal.orange)
            }
            error?.let { Banner(it, pal.red) }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .well(pal, 18.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (list.isEmpty()) {
                    Text(
                        if (scanning) "Looking for devices…" else "Nothing found. Make sure the device is powered and in range.",
                        style = Type.text(14.sp),
                        color = pal.t3,
                        modifier = Modifier.padding(18.dp),
                    )
                }
                list.forEachIndexed { index, d ->
                    if (index > 0) GroupDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(d.address, d.name) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    d.name ?: "(no name)",
                                    style = Type.text(15.sp, if (d.likelyBms) FontWeight.SemiBold else FontWeight.Normal),
                                    color = pal.t1,
                                    maxLines = 1,
                                )
                                if (d.likelyBms) {
                                    Spacer(Modifier.width(8.dp))
                                    Pill(if (victronRecord != null) "Victron" else "BMS", pal.accent)
                                }
                            }
                            Text(
                                d.address + (assigned[d.address]?.let { " · currently $it" } ?: ""),
                                style = Type.figures(12.sp, FontWeight.Normal),
                                color = pal.t3,
                            )
                        }
                        SignalBars(d.rssi)
                        Spacer(Modifier.width(8.dp))
                        Text("${d.rssi} dBm", style = Type.figures(12.sp, FontWeight.Medium), color = pal.t2)
                    }
                }
            }

            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAll = !showAll }
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = showAll,
                    onCheckedChange = { showAll = it },
                    colors = CheckboxDefaults.colors(checkedColor = pal.accent, uncheckedColor = pal.t3, checkmarkColor = pal.onAccent),
                )
                Text("Show devices without a name", style = Type.text(14.sp), color = pal.t2)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it.uppercase().trim().take(17) },
                    label = { Text("Or type the MAC address") },
                    placeholder = { Text("A4:C1:38:00:00:00") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = Type.figures(15.sp, FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = pal.t1,
                        unfocusedTextColor = pal.t1,
                        focusedContainerColor = pal.well,
                        unfocusedContainerColor = pal.well,
                        focusedBorderColor = pal.accent,
                        unfocusedBorderColor = pal.line,
                        focusedLabelColor = pal.accent,
                        unfocusedLabelColor = pal.t3,
                        cursorColor = pal.accent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PillButton("Use", { onPick(manual, null) }, enabled = BluetoothAdapter.checkBluetoothAddress(manual))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                PillButton("Cancel", onDismiss, color = pal.t2)
                Spacer(Modifier.width(10.dp))
                PillButton(
                    "Scan again",
                    {
                        error = null
                        found.clear()
                        round++
                        scanning = true
                    },
                    filled = true,
                    enabled = !scanning,
                )
            }
        }
    }
}
