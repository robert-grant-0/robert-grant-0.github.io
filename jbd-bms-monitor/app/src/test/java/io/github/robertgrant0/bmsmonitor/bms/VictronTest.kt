package io.github.robertgrant0.bmsmonitor.bms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test packets are real advertisements from the open-source victron-ble project's test suite. */
class VictronTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun decodesSmartShuntAdvertisement() {
        val data = hex("100289a302b040af925d09a4d89aa0128bdef48c6298a9")
        val key = VictronProtocol.parseKey("aff4d0995b7d1e176c0c33ecb9e70dcd")!!
        val result = VictronProtocol.decode(data, key)
        val r = (result as VictronProtocol.Result.Ok).reading as ShuntReading
        assertEquals(12.53, r.voltage!!, 1e-9)
        assertEquals(0.0, r.current!!, 1e-9)
        assertEquals(-50.0, r.consumedAh!!, 1e-9)
        assertEquals(50.0, r.stateOfCharge!!, 1e-9)
        assertNull(r.remainingMinutes)
        assertEquals(0, r.alarmBits)
        assertEquals(3, r.auxMode)
    }

    @Test
    fun wrongKeyIsDetected() {
        val data = hex("100289a302bb01af129087600b9b97bc2c32867c8238da")
        val key = VictronProtocol.parseKey("ffffffffffffffffffffffffffffffff")!!
        assertEquals(VictronProtocol.Result.WrongKey, VictronProtocol.decode(data, key))
    }

    @Test
    fun decodesSolarChargerAdvertisement() {
        val data = hex("100242a0016207adceb37b605d7e0ee21b24df5c")
        val key = VictronProtocol.parseKey("adeccb947395801a4dd45a2eaa44bf17")!!
        val r = (VictronProtocol.decode(data, key) as VictronProtocol.Result.Ok).reading as SolarReading
        assertEquals(4, r.chargeState)
        assertEquals("Absorption", VictronProtocol.chargeStateName(r.chargeState))
        assertEquals(13.88, r.batteryVoltage!!, 1e-9)
        assertEquals(1.4, r.batteryCurrent!!, 1e-9)
        assertEquals(30, r.yieldTodayWh)
        assertEquals(19, r.solarPower)
        assertEquals(0.0, r.loadCurrent!!, 1e-9)
    }

    @Test
    fun parsesLargerSolarChargerRecord() {
        val r = VictronProtocol.parseSolarCharger(hex("0300fb09650032000901ffff31bc45ad"))
        assertEquals(3, r.chargeState)
        assertEquals(25.55, r.batteryVoltage!!, 1e-9)
        assertEquals(10.1, r.batteryCurrent!!, 1e-9)
        assertEquals(500, r.yieldTodayWh)
        assertEquals(265, r.solarPower)
        assertNull(r.loadCurrent)
    }

    @Test
    fun keyParsing() {
        assertNotNull(VictronProtocol.parseKey("AFF4D0995B7D1E176C0C33ECB9E70DCD"))
        assertNotNull(VictronProtocol.parseKey(" aff4d0995b7d1e17 6c0c33ecb9e70dcd "))
        assertNull(VictronProtocol.parseKey("aff4"))
        assertNull(VictronProtocol.parseKey("zzf4d0995b7d1e176c0c33ecb9e70dcd"))
    }

    private fun battery(slot: Int, amps: Double, now: Long): BatteryState {
        val info = BasicInfo(
            totalVoltage = 13.3, current = amps, remainingCapacity = 50.0, nominalCapacity = 100.0,
            cycles = 1, productionDate = null, balanceBits = 0, protectionBits = 0, softwareVersion = 0,
            stateOfCharge = 50, chargeFetOn = true, dischargeFetOn = true, cellCount = 4, temperatures = listOf(20.0),
        )
        return BatteryState(
            slot = slot, name = "B${slot + 1}", address = "AA:BB:CC:DD:EE:0$slot",
            link = LinkState.CONNECTED, basic = info, lastUpdate = now,
        )
    }

    @Test
    fun currentSharesForParallelBank() {
        val now = 1_000_000L
        val shares = BankAnalysis.currentShares(listOf(battery(0, -20.0, now), battery(1, -20.0, now), battery(2, -10.0, now)), now)!!
        assertEquals(0.4, shares[0]!!, 1e-9)
        assertEquals(0.2, shares[2]!!, 1e-9)
        // Too little current to judge
        assertNull(BankAnalysis.currentShares(listOf(battery(0, -2.0, now), battery(1, -2.0, now)), now))
        // One battery charging another: shares meaningless
        assertNull(BankAnalysis.currentShares(listOf(battery(0, -30.0, now), battery(1, 15.0, now)), now))
    }

    @Test
    fun unevenSharingNeedsToPersist() {
        val start = 1_000_000L
        val tracker = SustainTracker()
        val none = VictronDeviceState()
        fun bank(now: Long) = listOf(battery(0, -40.0, now), battery(1, -10.0, now), battery(2, -10.0, now))
        assertTrue(BankAnalysis.evaluate(bank(start), none, none, AlarmThresholds(), start, tracker).isEmpty())
        val later = start + 121_000
        val alarms = BankAnalysis.evaluate(bank(later), none, none, AlarmThresholds(), later, tracker)
        assertEquals(listOf("share"), alarms.map { it.key })
    }

    @Test
    fun shuntDisagreeingWithBatteriesIsFlagged() {
        val start = 1_000_000L
        val tracker = SustainTracker()
        fun shunt(now: Long) = VictronDeviceState(
            address = "C0:00:00:00:00:01", status = VictronStatus.RECEIVING, lastUpdate = now,
            reading = ShuntReading(null, 13.3, 0, 3, -45.0, -10.0, 90.0),
        )
        fun bank(now: Long) = listOf(battery(0, -10.0, now), battery(1, -10.0, now), battery(2, -10.0, now))
        val t = AlarmThresholds(shareImbalanceEnabled = false)
        assertTrue(BankAnalysis.evaluate(bank(start), shunt(start), VictronDeviceState(), t, start, tracker).isEmpty())
        val later = start + 61_000
        val alarms = BankAnalysis.evaluate(bank(later), shunt(later), VictronDeviceState(), t, later, tracker)
        assertEquals(listOf("shunt_mismatch"), alarms.map { it.key })
    }
}
