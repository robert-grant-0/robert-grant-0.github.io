package io.github.robertgrant0.bmsmonitor.bms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SirenLatchTest {
    private val fault = mapOf("0:prot_SHORT_CIRCUIT" to "House 1: Short circuit")

    @Test
    fun quietWithoutAlarms() {
        val latch = SirenLatch()
        latch.update(emptyMap())
        assertTrue(latch.ringing.value.isEmpty())
    }

    @Test
    fun keepsRingingAfterAlarmClearsUntilSilenced() {
        val latch = SirenLatch()
        latch.update(fault)
        assertEquals(listOf("House 1: Short circuit"), latch.ringing.value)
        latch.update(emptyMap())
        assertEquals(listOf("House 1: Short circuit"), latch.ringing.value)
        latch.silence()
        assertTrue(latch.ringing.value.isEmpty())
    }

    @Test
    fun silencedAlarmStaysQuietWhileStillActive() {
        val latch = SirenLatch()
        latch.update(fault)
        latch.silence()
        latch.update(fault)
        latch.update(fault)
        assertTrue(latch.ringing.value.isEmpty())
    }

    @Test
    fun newAlarmRingsAgainAfterSilence() {
        val latch = SirenLatch()
        latch.update(fault)
        latch.silence()
        latch.update(fault + ("1:high_temp" to "House 2: High temperature"))
        assertEquals(listOf("House 2: High temperature"), latch.ringing.value)
    }

    @Test
    fun alarmThatClearsAndReturnsRingsAgain() {
        val latch = SirenLatch()
        latch.update(fault)
        latch.silence()
        latch.update(emptyMap())
        latch.update(fault)
        assertFalse(latch.ringing.value.isEmpty())
    }

    @Test
    fun testSirenRingsUntilSilenced() {
        val latch = SirenLatch()
        latch.test()
        latch.update(emptyMap())
        assertEquals(1, latch.ringing.value.size)
        latch.silence()
        assertTrue(latch.ringing.value.isEmpty())
    }

    @Test
    fun warningsOnlyWhenEnabled() {
        val warning = Alarm("low_soc", "Low charge: 10%", Severity.WARNING)
        val alarm = Alarm("prot_SHORT_CIRCUIT", "Short circuit", Severity.ALARM)
        assertTrue(SirenLatch.isSirenWorthy(alarm, includeWarnings = false))
        assertFalse(SirenLatch.isSirenWorthy(warning, includeWarnings = false))
        assertTrue(SirenLatch.isSirenWorthy(warning, includeWarnings = true))
    }
}
