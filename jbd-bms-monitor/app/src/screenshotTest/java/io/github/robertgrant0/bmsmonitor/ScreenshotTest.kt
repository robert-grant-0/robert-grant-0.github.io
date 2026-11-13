package io.github.robertgrant0.bmsmonitor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.robertgrant0.bmsmonitor.bms.AlarmThresholds
import io.github.robertgrant0.bmsmonitor.bms.Alarms
import io.github.robertgrant0.bmsmonitor.bms.BankAnalysis
import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.ShuntReading
import io.github.robertgrant0.bmsmonitor.bms.SolarReading
import io.github.robertgrant0.bmsmonitor.bms.SustainTracker
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bms.VictronStatus
import io.github.robertgrant0.bmsmonitor.data.AppSettings
import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import io.github.robertgrant0.bmsmonitor.data.MonitoringHealth
import io.github.robertgrant0.bmsmonitor.data.FlowUnit
import io.github.robertgrant0.bmsmonitor.data.PowerSample
import io.github.robertgrant0.bmsmonitor.data.SlotConfig
import io.github.robertgrant0.bmsmonitor.data.VictronKind
import io.github.robertgrant0.bmsmonitor.data.VictronSlot
import io.github.robertgrant0.bmsmonitor.ui.AlarmScreen
import io.github.robertgrant0.bmsmonitor.ui.BmsTheme
import io.github.robertgrant0.bmsmonitor.ui.DetailScreen
import io.github.robertgrant0.bmsmonitor.ui.DiagnosticsScreen
import io.github.robertgrant0.bmsmonitor.ui.HomeScreen
import io.github.robertgrant0.bmsmonitor.ui.InterFonts
import io.github.robertgrant0.bmsmonitor.ui.SettingsScreen
import io.github.robertgrant0.bmsmonitor.ui.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.sin

/**
 * Renders the real screens with sample readings to app/build/screenshots/ (run with
 * `./gradlew -Pscreenshots :app:testDebugUnitTest --tests '*ScreenshotTest*'`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_762_400_000_000L

    private fun info(soc: Int, amps: Double, temp: Double, cycles: Int, balance: Long = 0) = BasicInfo(
        totalVoltage = 13.38, current = amps, remainingCapacity = soc.toDouble(), nominalCapacity = 100.0,
        cycles = cycles, productionDate = "2023-06-14", balanceBits = balance, protectionBits = 0,
        softwareVersion = 0x10, stateOfCharge = soc, chargeFetOn = true, dischargeFetOn = true, cellCount = 4,
        temperatures = listOf(temp, temp - 0.2),
    )

    private val batteries = listOf(
        BatteryState(
            0, "House 1", "A4:C1:38:5E:10:01", LinkState.CONNECTED, "Connected", info(84, 6.4, 21.4, 42),
            listOf(3.352, 3.355, 3.351, 3.354), "4.1", -58, now, 0L, 0,
        ),
        BatteryState(
            1, "House 2", "A4:C1:38:5E:10:02", LinkState.CONNECTED, "Connected", info(81, 6.1, 21.9, 44),
            listOf(3.348, 3.351, 3.346, 3.350), "4.1", -66, now, 0L, 1,
        ),
        BatteryState(
            2, "House 3", "A4:C1:38:5E:10:03", LinkState.CONNECTED, "Connected", info(80, 6.1, 22.3, 39, balance = 1L shl 1),
            listOf(3.338, 3.402, 3.351, 3.290), "4.1", -71, now, 0L, 2,
        ),
    )

    private val shunt = VictronDeviceState(
        address = "C0:3B:51:22:01:AA", name = "SmartShunt", status = VictronStatus.RECEIVING, lastUpdate = now,
        reading = ShuntReading(null, 13.38, 0, 3, 18.6, -54.0, 82.0),
    )
    private val solar = VictronDeviceState(
        address = "C0:3B:51:22:01:BB", name = "SmartSolar", status = VictronStatus.RECEIVING, lastUpdate = now,
        reading = SolarReading(3, 0, 13.40, 30.1, 1860, 412, null),
    )

    private val history: List<PowerSample> = (0 until 90).map { i ->
        val t = i / 89.0
        var w = -160 + max(0.0, t - 0.25) * 520 + sin(i / 3.2) * 18 + sin(i / 1.3) * 9
        if (t > 0.82) w -= (t - 0.82) * 180
        val solarW = max(0.0, (t - 0.18) * 560 + sin(i / 2.5) * 14)
        PowerSample(now - ((89 - i) * 2 * 60_000L), w.toFloat(), solarW.toFloat(), (w / 13.38).toFloat(), (solarW / 13.4).toFloat())
    }.dropLast(1) + PowerSample(now, 249f, 412f, 18.6f, 30.1f)

    private val settings = AppSettings(
        slots = batteries.map { SlotConfig(it.name, it.address, "SP04S020") },
        shunt = VictronSlot("C0:3B:51:22:01:AA", "SmartShunt 500A", "0123456789abcdef0123456789abcdef"),
        solar = VictronSlot("C0:3B:51:22:01:BB", "SmartSolar MPPT VE.Can 150/100", "fedcba9876543210fedcba9876543210"),
    )

    private val alarms = batteries.map { Alarms.evaluate(it, AlarmThresholds(), now) }

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            BmsTheme(dark = dark, fonts = InterFonts, animations = false) { content() }
        }
        compose.waitForIdle()
        // Compose's captureToImage() waits for a frame-commit callback that Robolectric never
        // sends, so copy the window ourselves and run the test looper to deliver the result.
        val activity = compose.activity
        val window = activity.window
        val view = window.decorView
        val metrics = activity.resources.displayMetrics
        val width = view.width.takeIf { it > 0 } ?: metrics.widthPixels
        val height = view.height.takeIf { it > 0 } ?: metrics.heightPixels
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var result = Int.MIN_VALUE
        try {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
            shadowOf(Looper.getMainLooper()).idle()
        } catch (e: Throwable) {
            println("PixelCopy failed (${e.message}); drawing the view directly")
        }
        if (result != PixelCopy.SUCCESS) {
            println("PixelCopy result $result for $name; drawing the view directly")
            view.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Home(list: List<BatteryState> = batteries, unit: FlowUnit = FlowUnit.WATTS) {
        HomeScreen(
            batteries = list,
            alarms = list.map { Alarms.evaluate(it, AlarmThresholds(), now) },
            bankAlarms = BankAnalysis.evaluate(list, shunt, solar, AlarmThresholds(), now, SustainTracker()),
            shunt = shunt,
            solar = solar,
            history = history,
            now = now,
            cellDeltaWarnMv = 100,
            banners = {},
            unit = unit,
            onOpenDetail = {},
            onSetup = {},
            onOpenSettings = {},
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun homeTablet() = capture("1-home-tablet") { Home() }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun homeTabletAmps() = capture("10-home-tablet-amps") { Home(unit = FlowUnit.AMPS) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun homeTabletDark() = capture("2-home-tablet-dark", dark = true) { Home() }

    @Test
    @Config(qualifiers = "w962dp-h600dp-land-xhdpi")
    fun homeSmallTablet() = capture("3-home-8inch-tablet") { Home() }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-port-xhdpi")
    fun homePhone() = capture("4-home-phone") { Home() }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun detailTablet() = capture("5-battery-detail") {
        DetailScreen(
            state = batteries[2],
            alarms = alarms[2],
            deviceName = "SP04S020",
            share = BankAnalysis.currentShares(batteries, now)?.getOrNull(2),
            now = now,
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun settingsTablet() = capture("6-settings") {
        SettingsScreen(
            settings = settings,
            batteries = batteries,
            victronStates = mapOf(VictronKind.SHUNT to shunt, VictronKind.SOLAR to solar),
            unrestrictedBackground = true,
            fullScreenAlarmAllowed = true,
            versionName = "1.0",
            onUpdate = {},
            onChooseDevice = {},
            onChooseVictron = {},
            onRequestUnrestricted = {},
            onAllowFullScreenAlarm = {},
            onTestSiren = {},
            onSilenceSiren = {},
            onOpenDiagnostics = {},
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun diagnosticsTablet() = capture("11-diagnostics") {
        val log = DiagnosticsLog(clock = { now - 95_000 })
        log.info("app", "App started")
        log.info("service", "Monitoring started")
        log.info("scan", "Background scan for 3 BMS address(es)")
        log.info("battery1", "Connecting to A4:C1:38:5E:10:01 (attempt 1)")
        log.info("battery1", "Connected")
        log.info("battery1", "Hardware 4.1")
        log.warn("battery2", "Link lost: Signal lost (out of range?)")
        log.info("battery2", "Seen advertising")
        log.info("battery2", "Connected")
        log.info("victron", "SmartShunt: receiving")
        log.warn("alarm", "House 3: Cell imbalance: 112 mV")
        log.info("watchdog", "OK (last check 4s ago)")
        DiagnosticsScreen(
            batteries = batteries,
            shunt = shunt,
            solar = solar,
            health = MonitoringHealth.Status(serviceRunning = true, lastTick = now - 4_000, startedAt = now - 100_000),
            log = log,
            now = now,
            versionName = "1.0",
            onShare = {},
            onCopy = {},
            onClear = {},
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun alarmTablet() = capture("7-siren-alarm") {
        AlarmScreen(listOf("House 2: Discharge over-current", "SmartShunt alarm: Low voltage"), onSilence = {}, onOpenApp = {})
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun welcomeTablet() = capture("8-welcome") {
        WelcomeScreen(locationWording = false, onGrant = {}, onOpenAppSettings = {})
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun homeWithEmptySlot() = capture("9-home-setup") {
        Home(
            listOf(
                batteries[0],
                batteries[1].copy(link = LinkState.DISCONNECTED, statusText = "Signal lost – searching…", lastUpdate = now - 95_000),
                BatteryState(2, "House 3"),
            ),
        )
    }
}
