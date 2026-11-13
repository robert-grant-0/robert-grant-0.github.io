package io.github.robertgrant0.bmsmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun logKeepsOnlyTheNewestEntries() {
        var t = 0L
        val log = DiagnosticsLog(capacity = 3, clock = { t })
        for (i in 1..5) {
            t = i * 1000L
            log.info("t", "event $i")
        }
        assertEquals(listOf("event 3", "event 4", "event 5"), log.snapshot().map { it.message })
        assertEquals(5, log.revision.value)
        log.clear()
        assertTrue(log.snapshot().isEmpty())
        assertEquals(6, log.revision.value)
    }

    @Test
    fun exportIsReadable() {
        val log = DiagnosticsLog(clock = { 0L })
        log.warn("battery1", "Link lost: Timed out")
        val text = log.export(listOf("BMS Monitor 1.0", "Batteries:"))
        val lines = text.lines()
        assertEquals("BMS Monitor 1.0", lines[0])
        assertEquals("Batteries:", lines[1])
        assertEquals("", lines[2])
        assertTrue(lines[3], lines[3].endsWith(" WARN  battery1   Link lost: Timed out"))
    }

    @Test
    fun healthFlagsAStall() {
        var t = 1_000_000L
        val health = MonitoringHealth(clock = { t })
        assertFalse(health.isStalled(t + 10 * 60_000, monitoringEnabled = false))
        assertFalse("still inside the start-up grace period", health.isStalled(t + 60_000, true))
        assertTrue("never started", health.isStalled(t + 91_000, true))
        t += 5_000
        health.serviceStarted()
        assertFalse(health.isStalled(t + 60_000, true))
        assertTrue(health.isStalled(t + 3 * 60_000 + 1, true))
        t += 3 * 60_000
        health.tick()
        assertFalse(health.isStalled(t + 1, true))
        assertEquals(t, health.status.value.lastTick)
        health.restarted()
        assertEquals(1, health.status.value.restarts)
    }
}
