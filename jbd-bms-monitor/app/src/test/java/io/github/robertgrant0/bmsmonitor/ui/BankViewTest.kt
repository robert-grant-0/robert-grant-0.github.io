package io.github.robertgrant0.bmsmonitor.ui

import io.github.robertgrant0.bmsmonitor.bms.BasicInfo
import io.github.robertgrant0.bmsmonitor.bms.BatteryState
import io.github.robertgrant0.bmsmonitor.bms.LinkState
import io.github.robertgrant0.bmsmonitor.bms.ShuntReading
import io.github.robertgrant0.bmsmonitor.bms.SolarReading
import io.github.robertgrant0.bmsmonitor.bms.VictronDeviceState
import io.github.robertgrant0.bmsmonitor.bms.VictronStatus
import io.github.robertgrant0.bmsmonitor.data.PowerHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankViewTest {
    private val now = 10_000_000L

    private fun battery(slot: Int, soc: Int, amps: Double) = BatteryState(
        slot = slot, name = "House ${slot + 1}", address = "AA:BB:CC:DD:EE:0$slot", link = LinkState.CONNECTED,
        lastUpdate = now,
        basic = BasicInfo(
            totalVoltage = 13.38, current = amps, remainingCapacity = soc.toDouble(), nominalCapacity = 100.0,
            cycles = 40, productionDate = null, balanceBits = 0, protectionBits = 0, softwareVersion = 0x10,
            stateOfCharge = soc, chargeFetOn = true, dischargeFetOn = true, cellCount = 4, temperatures = listOf(21.0),
        ),
    )

    private val bank = listOf(battery(0, 84, 6.4), battery(1, 81, 6.1), battery(2, 80, 6.1))

    private val shunt = VictronDeviceState(
        address = "C0:00:00:00:00:01", status = VictronStatus.RECEIVING, lastUpdate = now,
        reading = ShuntReading(null, 13.38, 0, 3, 18.6, -54.0, 82.0),
    )
    private val solar = VictronDeviceState(
        address = "C0:00:00:00:00:02", status = VictronStatus.RECEIVING, lastUpdate = now,
        reading = SolarReading(3, 0, 13.40, 30.1, 1860, 412, null),
    )

    @Test
    fun shuntLeadsWhenAvailable() {
        val v = bankView(bank, shunt, solar, now)
        assertTrue(v.fromShunt)
        assertEquals(82, v.soc)
        assertEquals(18.6 * 13.38, v.watts!!, 1e-9)
        assertEquals(246.0, v.remainingAh!!, 1e-9)
        assertEquals(300.0, v.capacityAh!!, 1e-9)
        assertEquals("Full in 2h 54m", v.estimate)
        assertEquals(412.0, v.solarWatts!!, 1e-9)
        assertEquals("Bulk", v.solarState)
        assertEquals(1.86, v.solarTodayKwh!!, 1e-9)
        assertEquals((18.6 - 30.1) * 13.38, v.otherWatts!!, 1e-9)
        assertEquals(30.1, v.solarAmps!!, 1e-9)
        assertEquals(18.6 - 30.1, v.otherAmps!!, 1e-9)
    }

    @Test
    fun solarAmpsFallBackToPowerOverVoltage() {
        val noCurrent = solar.copy(reading = SolarReading(3, 0, 13.40, null, 1860, 412, null))
        val v = bankView(bank, shunt, noCurrent, now)
        assertEquals(412 / 13.40, v.solarAmps!!, 1e-9)
        assertEquals(18.6 - 412 / 13.40, v.otherAmps!!, 1e-9)
        assertEquals((18.6 - 412 / 13.40) * 13.38, v.otherWatts!!, 1e-9)
    }

    @Test
    fun fallsBackToBatteriesWithoutShunt() {
        val v = bankView(bank, VictronDeviceState(), solar, now)
        assertFalse(v.fromShunt)
        assertEquals(82, v.soc) // (84 + 81 + 80) / 300 → 81.7 %
        assertEquals(18.6, v.amps!!, 1e-9)
        assertEquals(245.0, v.remainingAh!!, 1e-9)
        // Without a shunt the batteries' total stands in for the bank current.
        assertEquals((18.6 - 30.1) * 13.38, v.otherWatts!!, 1e-6)
    }

    @Test
    fun nothingLiveMeansNoNumbers() {
        val stale = bank.map { it.copy(lastUpdate = now - 60_000) }
        val v = bankView(stale, VictronDeviceState(), VictronDeviceState(), now)
        assertNull(v.watts)
        assertNull(v.amps)
        assertNull(v.otherWatts)
        assertEquals(0, v.liveBatteries)
        assertEquals(3, v.configuredBatteries)
    }

    @Test
    fun signedNumbersUseATrueMinus() {
        assertEquals("+6.40", Fmt.signed(6.4, 2))
        assertEquals("−8.20", Fmt.signed(-8.2, 2))
        assertEquals("0.00", Fmt.signed(0.001, 2))
        assertEquals("+249 W", Fmt.signedWatts(249.0))
        assertEquals("−1.50 kW", Fmt.signedWatts(-1500.0))
        assertEquals("−8.20 A", Fmt.amps(-8.2))
        assertEquals("+18.6 A", Fmt.signedAmps(18.6))
        assertEquals("−0.4 A", Fmt.signedAmps(-0.44))
    }

    @Test
    fun historySamplesAtAnIntervalAndForgetsOldData() {
        val h = PowerHistory(intervalMs = 15_000, keepMs = 60_000)
        h.offer(0, 100.0, null)
        h.offer(5_000, 120.0, null) // too soon, ignored
        h.offer(15_000, 130.0, 50.0, 9.7, 3.7)
        h.offer(30_000, null, null) // nothing to record
        assertEquals(listOf(0L, 15_000L), h.samples.value.map { it.time })
        assertEquals(9.7f, h.samples.value[1].bankAmps)
        assertEquals(null, h.samples.value[0].bankAmps)
        h.offer(80_000, 90.0, null)
        assertEquals(listOf(80_000L), h.samples.value.map { it.time }.filter { it >= 20_000 })
        assertTrue(h.samples.value.none { it.time == 0L })
    }
}
