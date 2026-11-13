package io.github.robertgrant0.bmsmonitor.bms

import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives [BmsConnection] against a fake radio with virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class BmsConnectionTest {
    private val address = "AA:BB:CC:DD:EE:01"

    // Payloads from the JBD protocol document (also used in JbdProtocolTest).
    private val basicPayload = hex("17 00 00 00 02 D0 03 E8 00 00 20 78 00 00 00 00 00 00 10 48 03 0F 02 0B 76 0B 82")
    private val cellPayload = hex("0F 66 0F 63 0F 63 0F 64 0F 3E 0F 63 0F 37 0F 5B 0F 65 0F 3B 0F 63 0F 63 0F 3C 0F 66 0F 3D")

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun answer(cmd: Int): JbdFrame? = when (cmd) {
        JbdProtocol.CMD_BASIC_INFO -> JbdFrame(cmd, 0, basicPayload)
        JbdProtocol.CMD_CELL_VOLTAGES -> JbdFrame(cmd, 0, cellPayload)
        JbdProtocol.CMD_HARDWARE_VERSION -> JbdFrame(cmd, 0, "SP04S020".toByteArray())
        else -> null
    }

    private class FakeLink(private val answer: (Int) -> JbdFrame?) : BmsLink {
        var failWith: String? = null
        var closed = false
        var requests = 0

        override suspend fun request(cmd: Int): JbdFrame? {
            failWith?.let { throw BmsException(it) }
            requests++
            return answer(cmd)
        }

        override suspend fun readRssi(): Int = -60

        override fun close() {
            closed = true
        }
    }

    private inner class FakeEnv(private val scheduler: TestCoroutineScheduler) : BmsEnvironment {
        var permission = true
        var bluetooth = true
        var advertising = true
        var openError: String? = null
        var openCalls = 0
        val links = ArrayList<FakeLink>()
        var linkFactory: () -> FakeLink = { FakeLink(::answer) }

        override fun canConnect() = permission
        override fun bluetoothEnabled() = bluetooth

        override suspend fun awaitAdvertisement(address: String, timeoutMs: Long): Boolean {
            if (advertising) return true
            delay(timeoutMs)
            return false
        }

        override suspend fun open(address: String): BmsLink {
            openCalls++
            openError?.let { throw BmsException(it) }
            return linkFactory().also { links += it }
        }

        override fun now(): Long = scheduler.currentTime
    }

    private class Harness(var state: BatteryState) {
        var updates = 0
    }

    private fun TestScope.start(env: FakeEnv): Pair<BmsConnection, Harness> {
        val h = Harness(BatteryState(0, "House 1", address, LinkState.CONNECTING))
        val log = DiagnosticsLog(clock = { env.now() })
        val conn = BmsConnection(0, address, env, { 2_000L }, { _, _, t ->
            h.state = h.state.t()
            h.updates++
        }, log)
        conn.start(backgroundScope)
        return conn to h
    }

    @Test
    fun connectsAndPublishesReadings() = runTest {
        val env = FakeEnv(testScheduler)
        val (_, h) = start(env)
        runCurrent()
        assertEquals(LinkState.CONNECTED, h.state.link)
        assertEquals("Connected", h.state.statusText)
        assertEquals(72, h.state.basic!!.stateOfCharge)
        assertEquals(15, h.state.cells.size)
        assertEquals("SP04S020", h.state.hardwareVersion)
        assertEquals(-60, h.state.rssi)
        assertEquals(0L, h.state.disconnectedSince)
        assertEquals(1, env.openCalls)
        advanceTimeBy(6_001)
        runCurrent()
        assertEquals("polls every 2 s; the last one ran at 6 s", 6_000L, h.state.lastUpdate)
    }

    @Test
    fun reconnectsAfterTheLinkDrops() = runTest {
        val env = FakeEnv(testScheduler)
        val (_, h) = start(env)
        runCurrent()
        env.links[0].failWith = "Signal lost (out of range?)"
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, h.state.link)
        assertEquals("Signal lost (out of range?)", h.state.statusText)
        assertEquals(1, h.state.reconnectCount)
        assertEquals(2_000L, h.state.disconnectedSince)
        assertTrue(env.links[0].closed)
        // Reconnects 1 s later: the BMS is advertising, so no waiting.
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(LinkState.CONNECTED, h.state.link)
        assertEquals(2, env.openCalls)
        assertEquals(0L, h.state.disconnectedSince)
        assertEquals(72, h.state.basic!!.stateOfCharge)
    }

    @Test
    fun backsOffWhileConnectsKeepFailing() = runTest {
        val env = FakeEnv(testScheduler).apply {
            openError = "Connection failed (error 133)"
            advertising = false
        }
        val (_, h) = start(env)
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, h.state.link)
        assertEquals("Connection failed (error 133)", h.state.statusText)
        assertEquals(0, h.state.reconnectCount)
        // 3 s pause, then a search that grows by 10 s per failure: attempts at 13, 36, 69 and 112 s.
        for ((i, t) in listOf(13_000L, 36_000L, 69_000L, 112_000L).withIndex()) {
            advanceTimeBy(t - 1 - currentTime)
            runCurrent()
            assertEquals("attempts just before ${t / 1000} s", i + 1, env.openCalls)
            assertTrue(h.state.statusText.endsWith("searching…"))
            advanceTimeBy(1)
            runCurrent()
            assertEquals("attempts at ${t / 1000} s", i + 2, env.openCalls)
        }
    }

    @Test
    fun givesUpOnASilentBmsAndReconnects() = runTest {
        val env = FakeEnv(testScheduler)
        env.linkFactory = { FakeLink { null } }
        val (_, h) = start(env)
        runCurrent()
        assertEquals(LinkState.CONNECTED, h.state.link)
        assertEquals("Waiting for BMS…", h.state.statusText)
        // Five unanswered polls 2 s apart: the fifth, at 8 s, ends the session.
        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(LinkState.CONNECTED, h.state.link)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(LinkState.DISCONNECTED, h.state.link)
        assertEquals("BMS stopped responding", h.state.statusText)
        assertEquals(0, h.state.reconnectCount)
        assertTrue(env.links[0].closed)
        env.linkFactory = { FakeLink(::answer) }
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(LinkState.CONNECTED, h.state.link)
        assertNotNull(h.state.basic)
        assertEquals(2, env.openCalls)
    }

    @Test
    fun waitsWhileBluetoothIsOffOrPermissionIsMissing() = runTest {
        val env = FakeEnv(testScheduler).apply { bluetooth = false }
        val (_, h) = start(env)
        runCurrent()
        assertEquals(LinkState.BLUETOOTH_OFF, h.state.link)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, env.openCalls)
        env.permission = false
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(LinkState.NO_PERMISSION, h.state.link)
        env.permission = true
        env.bluetooth = true
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(LinkState.CONNECTED, h.state.link)
        assertEquals(1, env.openCalls)
    }

    @Test
    fun stopClosesTheLinkAndPublishesNothingMore() = runTest {
        val env = FakeEnv(testScheduler)
        val (conn, h) = start(env)
        runCurrent()
        val updates = h.updates
        conn.stop()
        runCurrent()
        assertTrue(env.links[0].closed)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(updates, h.updates)
        assertEquals(1, env.openCalls)
    }
}
