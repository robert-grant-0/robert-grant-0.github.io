package io.github.robertgrant0.bmsmonitor.bms

import java.util.Locale
import java.util.UUID

/**
 * JBD (Jiabaida / "Xiaoxiang") BMS Bluetooth protocol.
 *
 * Request:  DD A5 <cmd> <len> <data...> <chk_hi> <chk_lo> 77
 * Response: DD <cmd> <status> <len> <data...> <chk_hi> <chk_lo> 77
 *
 * The checksum is 0x10000 minus the byte sum of everything between the
 * command/status byte and the checksum (exclusive of the leading DD/A5/cmd).
 * All multi-byte numbers are big-endian.
 */
object JbdProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val START = 0xDD
    const val END = 0x77
    const val READ = 0xA5

    const val CMD_BASIC_INFO = 0x03
    const val CMD_CELL_VOLTAGES = 0x04
    const val CMD_HARDWARE_VERSION = 0x05

    /** Builds a read request for [cmd] (e.g. DD A5 03 00 FF FD 77). */
    fun readCommand(cmd: Int): ByteArray {
        val chk = (0x10000 - (cmd and 0xFF)) and 0xFFFF
        return byteArrayOf(
            START.toByte(), READ.toByte(), cmd.toByte(), 0x00,
            (chk shr 8).toByte(), (chk and 0xFF).toByte(), END.toByte(),
        )
    }

    /** JBD checksum over bytes[from, toExclusive). */
    fun checksum(bytes: ByteArray, from: Int, toExclusive: Int): Int {
        var sum = 0
        for (i in from until toExclusive) sum += bytes[i].toInt() and 0xFF
        return (0x10000 - sum) and 0xFFFF
    }

    fun parseBasicInfo(p: ByteArray): BasicInfo? {
        if (p.size < 23) return null
        val ntcCount = u8(p, 22)
        val temps = ArrayList<Double>(ntcCount)
        for (i in 0 until ntcCount) {
            val off = 23 + 2 * i
            if (off + 1 >= p.size) break
            temps += (u16(p, off) - 2731) / 10.0
        }
        val balanceLow = u16(p, 12).toLong()
        val balanceHigh = u16(p, 14).toLong()
        val fet = u8(p, 20)
        return BasicInfo(
            totalVoltage = u16(p, 0) / 100.0,
            current = s16(p, 2) / 100.0,
            remainingCapacity = u16(p, 4) / 100.0,
            nominalCapacity = u16(p, 6) / 100.0,
            cycles = u16(p, 8),
            productionDate = decodeDate(u16(p, 10)),
            balanceBits = balanceLow or (balanceHigh shl 16),
            protectionBits = u16(p, 16),
            softwareVersion = u8(p, 18),
            stateOfCharge = u8(p, 19).coerceIn(0, 100),
            chargeFetOn = fet and 0x01 != 0,
            dischargeFetOn = fet and 0x02 != 0,
            cellCount = u8(p, 21),
            temperatures = temps,
        )
    }

    /** Cell voltages in volts. */
    fun parseCellVoltages(p: ByteArray): List<Double> =
        List(p.size / 2) { u16(p, it * 2) / 1000.0 }

    fun parseHardwareVersion(p: ByteArray): String =
        String(p, Charsets.US_ASCII).filter { it in ' '..'~' }.trim()

    private fun decodeDate(d: Int): String? {
        if (d == 0) return null
        val year = (d shr 9) + 2000
        val month = (d shr 5) and 0x0F
        val day = d and 0x1F
        if (month !in 1..12 || day !in 1..31) return null
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    private fun u8(p: ByteArray, i: Int) = p[i].toInt() and 0xFF
    private fun u16(p: ByteArray, i: Int) = (u8(p, i) shl 8) or u8(p, i + 1)
    private fun s16(p: ByteArray, i: Int) = u16(p, i).toShort().toInt()
}

/** One complete, checksum-verified response frame. */
class JbdFrame(val cmd: Int, val status: Int, val payload: ByteArray) {
    val ok: Boolean get() = status == 0
}

/**
 * Reassembles response frames from BLE notifications. The default BLE MTU only
 * carries 20 bytes per notification, so a single response arrives in pieces.
 */
class JbdFrameAssembler(private val maxBuffer: Int = 1024) {
    private var buf = ByteArray(256)
    private var size = 0

    fun reset() {
        size = 0
    }

    fun feed(chunk: ByteArray): List<JbdFrame> {
        append(chunk)
        val frames = ArrayList<JbdFrame>(1)
        while (true) {
            val start = indexOfStart()
            if (start < 0) {
                size = 0
                break
            }
            if (start > 0) drop(start)
            if (size < 4) break
            val len = buf[3].toInt() and 0xFF
            val total = len + 7
            if (size < total) break
            val endOk = (buf[total - 1].toInt() and 0xFF) == JbdProtocol.END
            val expected = ((buf[4 + len].toInt() and 0xFF) shl 8) or (buf[5 + len].toInt() and 0xFF)
            if (!endOk || expected != JbdProtocol.checksum(buf, 2, 4 + len)) {
                // Corrupt or misaligned: skip this start byte and look for the next one.
                drop(1)
                continue
            }
            frames += JbdFrame(
                cmd = buf[1].toInt() and 0xFF,
                status = buf[2].toInt() and 0xFF,
                payload = buf.copyOfRange(4, 4 + len),
            )
            drop(total)
        }
        if (size > maxBuffer) size = 0
        return frames
    }

    private fun append(chunk: ByteArray) {
        if (size + chunk.size > buf.size) {
            buf = buf.copyOf(maxOf(buf.size * 2, size + chunk.size))
        }
        System.arraycopy(chunk, 0, buf, size, chunk.size)
        size += chunk.size
    }

    private fun indexOfStart(): Int {
        for (i in 0 until size) if ((buf[i].toInt() and 0xFF) == JbdProtocol.START) return i
        return -1
    }

    private fun drop(n: Int) {
        System.arraycopy(buf, n, buf, 0, size - n)
        size -= n
    }
}

data class BasicInfo(
    /** Pack voltage, V. */
    val totalVoltage: Double,
    /** Pack current, A. Positive = charging, negative = discharging. */
    val current: Double,
    /** Remaining capacity, Ah. */
    val remainingCapacity: Double,
    /** Nominal (design) capacity, Ah. */
    val nominalCapacity: Double,
    val cycles: Int,
    val productionDate: String?,
    /** Bit n set = cell n+1 is balancing. */
    val balanceBits: Long,
    val protectionBits: Int,
    val softwareVersion: Int,
    /** State of charge, 0–100 %. */
    val stateOfCharge: Int,
    val chargeFetOn: Boolean,
    val dischargeFetOn: Boolean,
    val cellCount: Int,
    /** Temperature probes, °C. */
    val temperatures: List<Double>,
) {
    val power: Double get() = totalVoltage * current
    val protections: List<Protection> get() = Protection.fromBits(protectionBits)
    fun isBalancing(cellIndex: Int): Boolean = (balanceBits shr cellIndex) and 1L == 1L
}

/** Protection flags reported by the BMS (basic info bytes 16–17). */
enum class Protection(val bit: Int, val label: String) {
    CELL_OVERVOLTAGE(0, "Cell over-voltage"),
    CELL_UNDERVOLTAGE(1, "Cell under-voltage"),
    PACK_OVERVOLTAGE(2, "Pack over-voltage"),
    PACK_UNDERVOLTAGE(3, "Pack under-voltage"),
    CHARGE_OVERTEMP(4, "Charge over-temperature"),
    CHARGE_UNDERTEMP(5, "Charge under-temperature"),
    DISCHARGE_OVERTEMP(6, "Discharge over-temperature"),
    DISCHARGE_UNDERTEMP(7, "Discharge under-temperature"),
    CHARGE_OVERCURRENT(8, "Charge over-current"),
    DISCHARGE_OVERCURRENT(9, "Discharge over-current"),
    SHORT_CIRCUIT(10, "Short circuit"),
    FRONTEND_IC_ERROR(11, "Front-end IC error"),
    MOSFET_SOFTWARE_LOCK(12, "MOSFET software lock"),
    ;

    companion object {
        fun fromBits(bits: Int): List<Protection> = entries.filter { bits and (1 shl it.bit) != 0 }
    }
}
