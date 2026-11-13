package io.github.robertgrant0.bmsmonitor

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.robertgrant0.bmsmonitor.bms.Alarms
import io.github.robertgrant0.bmsmonitor.bms.BankAnalysis
import io.github.robertgrant0.bmsmonitor.bms.VictronProtocol
import io.github.robertgrant0.bmsmonitor.data.AppSettings
import io.github.robertgrant0.bmsmonitor.data.FlowUnit
import io.github.robertgrant0.bmsmonitor.data.VictronKind
import io.github.robertgrant0.bmsmonitor.service.BmsService
import io.github.robertgrant0.bmsmonitor.service.DiagnosticsExport
import io.github.robertgrant0.bmsmonitor.ui.DiagnosticsScreen
import io.github.robertgrant0.bmsmonitor.ui.Banner
import io.github.robertgrant0.bmsmonitor.ui.BmsTheme
import io.github.robertgrant0.bmsmonitor.ui.DetailScreen
import io.github.robertgrant0.bmsmonitor.ui.HomeScreen
import io.github.robertgrant0.bmsmonitor.ui.LocalPalette
import io.github.robertgrant0.bmsmonitor.ui.InterFonts
import io.github.robertgrant0.bmsmonitor.ui.WelcomeScreen
import io.github.robertgrant0.bmsmonitor.ui.ScanDialog
import io.github.robertgrant0.bmsmonitor.ui.SettingsScreen
import io.github.robertgrant0.bmsmonitor.ui.isDarkTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = bmsApp
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.theme)
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            BmsTheme(dark, InterFonts, animations = settings.animations) {
                AppRoot(app, settings)
            }
        }
    }
}

private enum class Screen { HOME, DETAIL, SETTINGS, DIAGNOSTICS }

@Composable
private fun AppRoot(app: BmsApp, settings: AppSettings) {
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()

    var hasPermissions by remember { mutableStateOf(Permissions.hasRequired(context)) }
    LaunchedEffect(resumeTick) { hasPermissions = Permissions.hasRequired(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermissions = Permissions.hasRequired(context) }

    if (!hasPermissions) {
        WelcomeScreen(
            locationWording = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
            onGrant = { permissionLauncher.launch(Permissions.required + Permissions.optional) },
            onOpenAppSettings = { openAppSettings(context) },
        )
        return
    }

    // Keep the background service in step with the "Keep batteries connected" setting.
    LaunchedEffect(settings.monitoringEnabled) {
        if (settings.monitoringEnabled) BmsService.start(context) else BmsService.stop(context)
    }

    val batteries by app.manager.batteries.collectAsStateWithLifecycle()
    val shunt by app.manager.shunt.collectAsStateWithLifecycle()
    val solar by app.manager.solar.collectAsStateWithLifecycle()
    val bankAlarms by app.bankAlarms.collectAsStateWithLifecycle()
    val history by app.history.samples.collectAsStateWithLifecycle()
    val health by app.health.status.collectAsStateWithLifecycle()
    var victronScan by rememberSaveable { mutableStateOf<VictronKind?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val alarms = remember(batteries, settings.thresholds, now) {
        batteries.map { Alarms.evaluate(it, settings.thresholds, now) }
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var detailSlot by rememberSaveable { mutableIntStateOf(0) }
    var scanSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var hideBatteryBanner by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.DIAGNOSTICS) Screen.SETTINGS else Screen.HOME
    }

    val unrestricted = remember(resumeTick) { isIgnoringBatteryOptimizations(context) }
    val fullScreenAllowed = remember(resumeTick) { canUseFullScreenAlarms(context) }

    // While the app is on screen a full-screen notification only shows as a banner,
    // so open the alarm page directly when the siren starts.
    val sirenMessages by app.siren.ringing.collectAsStateWithLifecycle()
    LaunchedEffect(sirenMessages.isNotEmpty()) {
        if (sirenMessages.isNotEmpty()) {
            context.startActivity(Intent(context, AlarmActivity::class.java))
        }
    }
    val bluetoothOn = remember(now) { bluetoothAdapter(context)?.isEnabled == true }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    when (screen) {
        Screen.DETAIL -> DetailScreen(
            state = batteries[detailSlot],
            alarms = alarms[detailSlot],
            deviceName = settings.slots[detailSlot].deviceName,
            share = BankAnalysis.currentShares(batteries, now)?.getOrNull(detailSlot),
            now = now,
            onBack = { screen = Screen.HOME },
        )

        Screen.DIAGNOSTICS -> DiagnosticsScreen(
            batteries = batteries,
            shunt = shunt,
            solar = solar,
            health = health,
            log = app.diagnostics,
            now = now,
            versionName = remember { appVersion(context) },
            onShare = { DiagnosticsExport.share(context) },
            onCopy = { DiagnosticsExport.copy(context) },
            onClear = { app.diagnostics.clear() },
            onBack = { screen = Screen.SETTINGS },
        )

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            batteries = batteries,
            victronStates = mapOf(VictronKind.SHUNT to shunt, VictronKind.SOLAR to solar),
            unrestrictedBackground = unrestricted,
            fullScreenAlarmAllowed = fullScreenAllowed,
            versionName = remember { appVersion(context) },
            onUpdate = { transform -> app.settings.update(transform) },
            onChooseDevice = { scanSlot = it },
            onChooseVictron = { victronScan = it },
            onRequestUnrestricted = { requestUnrestricted(context) },
            onAllowFullScreenAlarm = { requestFullScreenAlarms(context) },
            onTestSiren = { app.siren.test() },
            onSilenceSiren = { app.siren.silence() },
            onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
            onBack = { screen = Screen.HOME },
        )

        Screen.HOME -> HomeScreen(
            batteries = batteries,
            alarms = alarms,
            now = now,
            cellDeltaWarnMv = settings.thresholds.cellDeltaMv.takeIf { settings.thresholds.cellDeltaEnabled },
            unit = settings.flowUnit,
            shunt = shunt,
            solar = solar,
            bankAlarms = bankAlarms,
            history = history,
            banners = {
                val pal = LocalPalette.current
                val spacing = Modifier.padding(bottom = 12.dp)
                if (!bluetoothOn) {
                    Banner(
                        "Bluetooth is off – the batteries can't be reached.",
                        pal.red,
                        spacing,
                        actionLabel = "Turn on",
                        onAction = {
                            try {
                                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } catch (_: Exception) {
                            }
                        },
                    )
                }
                if (!settings.monitoringEnabled) {
                    Banner(
                        "Monitoring is stopped.",
                        pal.orange,
                        spacing,
                        actionLabel = "Start",
                        onAction = { app.settings.update { it.copy(monitoringEnabled = true) } },
                    )
                } else if (app.health.isStalled(now, true)) {
                    Banner(
                        "Monitoring has stalled – readings and alarms may be out of date.",
                        pal.red,
                        spacing,
                        actionLabel = "Restart",
                        onAction = {
                            app.diagnostics.warn("ui", "Restart requested from the stall banner")
                            BmsService.start(context)
                        },
                    )
                }
                if (!unrestricted && !hideBatteryBanner) {
                    Banner(
                        "For a constant connection, allow the app to run in the background without restrictions.",
                        pal.orange,
                        spacing,
                        actionLabel = "Allow",
                        onAction = { requestUnrestricted(context) },
                        dismissLabel = "Not now",
                        onDismiss = { hideBatteryBanner = true },
                    )
                }
            },
            onOpenDetail = {
                detailSlot = it
                screen = Screen.DETAIL
            },
            onSetup = { scanSlot = it },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
    }

    victronScan?.let { kind ->
        ScanDialog(
            slotName = kind.label,
            assigned = emptyMap(),
            onDismiss = { victronScan = null },
            onPick = { address, name ->
                app.settings.updateVictron(kind) { it.copy(address = address, deviceName = name) }
                victronScan = null
            },
            victronRecord = if (kind == VictronKind.SHUNT) {
                VictronProtocol.RECORD_BATTERY_MONITOR
            } else {
                VictronProtocol.RECORD_SOLAR_CHARGER
            },
        )
    }

    scanSlot?.let { slot ->
        ScanDialog(
            slotName = settings.slotName(slot),
            assigned = settings.slots.withIndex()
                .filter { it.value.address != null }
                .associate { it.value.address!! to settings.slotName(it.index) },
            onDismiss = { scanSlot = null },
            onPick = { address, name ->
                app.settings.assignDevice(slot, address, name)
                scanSlot = null
            },
        )
    }
}

/** Increments every time the activity resumes, e.g. after returning from a system settings screen. */
@Composable
private fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    context.getSystemService(BluetoothManager::class.java)?.adapter

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

@SuppressLint("BatteryLife") // Sideloaded monitoring app that must stay connected.
private fun requestUnrestricted(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(direct)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

private fun canUseFullScreenAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false

private fun requestFullScreenAlarms(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
        )
    } catch (_: ActivityNotFoundException) {
        openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    } catch (_: ActivityNotFoundException) {
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (_: Exception) {
    ""
}
