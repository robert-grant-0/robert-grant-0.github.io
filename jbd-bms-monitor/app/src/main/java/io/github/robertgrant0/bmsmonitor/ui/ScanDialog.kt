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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (victronRecord != null) "Choose your $slotName" else "Choose the BMS for $slotName",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (victronRecord != null) {
                        "Only Victron devices of this type are listed. If yours is missing, turn on Bluetooth " +
                            "and Instant Readout for it in VictronConnect."
                    } else {
                        "A BMS only shows up while nothing else is connected to it – close the Xiaoxiang / " +
                            "JBD app on other phones first. Likely BMS units are listed first."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (locationOff) {
                    Text(
                        "Location is turned off. On this Android version Bluetooth scanning needs Location on.",
                        color = LocalStatusColors.current.warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (list.isEmpty()) {
                        Text(
                            if (scanning) "Searching…" else "Nothing found.",
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    list.forEachIndexed { index, d ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(d.address, d.name) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        d.name ?: "(no name)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (d.likelyBms) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    if (d.likelyBms) {
                                        Spacer(Modifier.width(8.dp))
                                        Pill(if (victronRecord != null) "Victron" else "BMS", MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    d.address + (assigned[d.address]?.let { " · currently $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${d.rssi} dBm",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    Modifier.clickable { showAll = !showAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = showAll, onCheckedChange = { showAll = it })
                    Text("Show devices without a name", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it.uppercase().trim().take(17) },
                        label = { Text("Or type the MAC address") },
                        placeholder = { Text("A4:C1:38:00:00:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onPick(manual, null) },
                        enabled = BluetoothAdapter.checkBluetoothAddress(manual),
                    ) { Text("Use") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            error = null
                            found.clear()
                            round++
                            scanning = true
                        },
                        enabled = !scanning,
                    ) { Text("Scan again") }
                }
            }
        }
    }
}
