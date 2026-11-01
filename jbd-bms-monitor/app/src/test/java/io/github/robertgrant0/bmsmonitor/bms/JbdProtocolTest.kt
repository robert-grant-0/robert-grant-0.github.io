package io.github.robertgrant0.bmsmonitor.bms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JbdProtocolTest {

    private fun hex(s: String): ByteArray =
        s.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    // Example frames from the JBD BMS communication protocol document.
    private val basicInfoFrame = hex(
        "DD 03 00 1B 17 00 00 00 02 D0 03 E8 00 00 20 78 00 00 00 00 00 00 10 48 03 0F 02 0B 76 0B 82 FB FF 77",
    )
    private val cellFrame = hex(
        "DD 04 00 1E 0F 66 0F 63 0F 63 0F 64 0F 3E 0F 63 0F 37 0F 5B 0F 65 0F 3B 0F 63 0F 63 0F 3C 0F 66 0F 3D F9 F9 77",
    )

    @Test
    fun readCommandsMatchSpec() {
        assertArrayEquals(hex("DD A5 03 00 FF FD 77"), JbdProtocol.readCommand(JbdProtocol.CMD_BASIC_INFO))
        assertArrayEquals(hex("DD A5 04 00 FF FC 77"), JbdProtocol.readCommand(JbdProtocol.CMD_CELL_VOLTAGES))
        assertArrayEquals(hex("DD A5 05 00 FF FB 77"), JbdProtocol.readCommand(JbdProtocol.CMD_HARDWARE_VERSION))
    }

    @Test
    fun parsesBasicInfo() {
        val frame = JbdFrameAssembler().feed(basicInfoFrame).single()
        assertEquals(0x03, frame.cmd)
        assertTrue(frame.ok)
        val info = JbdProtocol.parseBasicInfo(frame.payload)!!
        assertEquals(58.88, info.totalVoltage, 1e-9)
        assertEquals(0.0, info.current, 1e-9)
        assertEquals(7.20, info.remainingCapacity, 1e-9)
        assertEquals(10.00, info.nominalCapacity, 1e-9)
        assertEquals(0, info.cycles)
        assertEquals("2016-03-24", info.productionDate)
        assertEquals(0, info.protectionBits)
        assertEquals(72, info.stateOfCharge)
        assertTrue(info.chargeFetOn)
        assertTrue(info.dischargeFetOn)
        assertEquals(15, info.cellCount)
        assertEquals(2, info.temperatures.size)
        assertEquals(20.3, info.temperatures[0], 1e-9)
        assertEquals(21.5, info.temperatures[1], 1e-9)
    }

    @Test
    fun negativeCurrentMeansDischarging() {
        // Same payload with current = -12.34 A (0xFB2E) and the checksum recomputed.
        val payload = JbdFrameAssembler().feed(basicInfoFrame).single().payload.copyOf()
        payload[2] = 0xFB.toByte()
        payload[3] = 0x2E.toByte()
        val info = JbdProtocol.parseBasicInfo(payload)!!
        assertEquals(-12.34, info.current, 1e-9)
        assertEquals(58.88 * -12.34, info.power, 1e-9)
    }

    @Test
    fun parsesCellVoltages() {
        val frame = JbdFrameAssembler().feed(cellFrame).single()
        assertEquals(0x04, frame.cmd)
        val cells = JbdProtocol.parseCellVoltages(frame.payload)
        assertEquals(15, cells.size)
        assertEquals(3.942, cells[0], 1e-9)
        assertEquals(3.901, cells[14], 1e-9)
    }

    @Test
    fun reassemblesFramesSplitAcrossNotifications() {
        val assembler = JbdFrameAssembler()
        val chunks = cellFrame.toList().chunked(20).map { it.toByteArray() }
        val frames = chunks.flatMap { assembler.feed(it) }
        assertEquals(1, frames.size)
        assertEquals(15, JbdProtocol.parseCellVoltages(frames[0].payload).size)
    }

    @Test
    fun skipsGarbageAndBadChecksums() {
        val assembler = JbdFrameAssembler()
        val corrupt = basicInfoFrame.copyOf().also { it[10] = 0x55 }
        val input = hex("00 12 77") + corrupt + cellFrame
        val frames = assembler.feed(input)
        assertEquals(1, frames.size)
        assertEquals(0x04, frames[0].cmd)
    }

    @Test
    fun handlesTwoFramesInOneChunk() {
        val frames = JbdFrameAssembler().feed(basicInfoFrame + cellFrame)
        assertEquals(listOf(0x03, 0x04), frames.map { it.cmd })
    }

    @Test
    fun decodesProtectionFlags() {
        val flags = Protection.fromBits((1 shl 0) or (1 shl 10))
        assertEquals(listOf(Protection.CELL_OVERVOLTAGE, Protection.SHORT_CIRCUIT), flags)
        assertTrue(Protection.fromBits(0).isEmpty())
    }

    @Test
    fun shortPayloadIsRejected() {
        assertNull(JbdProtocol.parseBasicInfo(ByteArray(10)))
    }

    @Test
    fun alarmsFromLiveData() {
        val now = 1_000_000L
        val info = JbdProtocol.parseBasicInfo(JbdFrameAssembler().feed(basicInfoFrame).single().payload)!!
        val base = BatteryState(
            slot = 0, name = "B1", address = "AA:BB:CC:DD:EE:FF", link = LinkState.CONNECTED,
            basic = info, cells = listOf(3.300, 3.305), lastUpdate = now,
        )
        assertTrue(Alarms.evaluate(base, AlarmThresholds(), now).isEmpty())

        val bad = base.copy(
            basic = info.copy(protectionBits = 1 shl 9, dischargeFetOn = false, stateOfCharge = 10),
            cells = listOf(3.30, 3.45),
        )
        val keys = Alarms.evaluate(bad, AlarmThresholds(), now).map { it.key }.toSet()
        assertEquals(setOf("prot_DISCHARGE_OVERCURRENT", "dsg_fet", "low_soc", "cell_delta"), keys)
    }

    @Test
    fun connectionLostAlarmWaitsForThreshold() {
        val s = BatteryState(
            slot = 0, name = "B1", address = "AA:BB:CC:DD:EE:FF",
            link = LinkState.DISCONNECTED, disconnectedSince = 0L + 1,
        )
        val t = AlarmThresholds(connectionLostSeconds = 60)
        assertTrue(Alarms.evaluate(s, t, 30_000).isEmpty())
        val alarms = Alarms.evaluate(s, t, 61_001)
        assertEquals(listOf("link"), alarms.map { it.key })
        assertFalse(Alarms.evaluate(s.copy(link = LinkState.STOPPED), t, 61_001).isNotEmpty())
        assertNotNull(alarms.first().message)
    }
}
