package io.github.robertgrant0.bmsmonitor.bms

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Victron "Instant Readout": Victron devices broadcast their live data in encrypted
 * Bluetooth advertisements. Nothing connects to the device, so this is read-only and
 * leaves the device free for the VictronConnect app.
 *
 * Manufacturer data (after Victron's company id 0x02E1):
 *   [0..1] record prefix (0x10 …)  [2..3] model id (LE)  [4] record type
 *   [5..6] AES-CTR nonce (LE)      [7] first byte of the key (key check)
 *   [8..]  encrypted record, bit-packed LSB first.
 */
object VictronProtocol {
    const val COMPANY_ID = 0x02E1
    const val RECORD_SOLAR_CHARGER = 0x01
    const val RECORD_BATTERY_MONITOR = 0x02

    sealed interface Result {
        data class Ok(val reading: VictronReading) : Result
        data object WrongKey : Result
        data object UnsupportedRecord : Result
        data object Invalid : Result
    }

    fun recordType(data: ByteArray): Int? =
        if (data.size >= 8 && u8(data, 0) == 0x10) u8(data, 4) else null

    fun decode(data: ByteArray, key: ByteArray): Result {
        val type = recordType(data) ?: return Result.Invalid
        if (type != RECORD_BATTERY_MONITOR && type != RECORD_SOLAR_CHARGER) return Result.UnsupportedRecord
        if (key.size != 16) return Result.WrongKey
        if (data[7] != key[0]) return Result.WrongKey
        val nonce = u8(data, 5) or (u8(data, 6) shl 8)
        val plain = ctr(key, nonce, data.copyOfRange(8, data.size))
        return when (type) {
            RECORD_BATTERY_MONITOR ->
                if (plain.size < 15) Result.Invalid else Result.Ok(parseBatteryMonitor(plain))
            else ->
                if (plain.size < 12) Result.Invalid else Result.Ok(parseSolarCharger(plain))
        }
    }

    /** Parses the 32-hex-digit key shown in VictronConnect; null if malformed. */
    fun parseKey(hex: String): ByteArray? {
        val h = hex.filter { !it.isWhitespace() }
        if (h.length != 32 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** AES-CTR with a 128-bit little-endian counter starting at [nonce]. */
    private fun ctr(key: ByteArray, nonce: Int, data: ByteArray): ByteArray {
        val aes = Cipher.getInstance("AES/ECB/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val out = ByteArray(data.size)
        var block = 0L
        var offset = 0
        while (offset < data.size) {
            val counter = ByteArray(16)
            val value = nonce + block
            for (b in 0 until 8) counter[b] = (value shr (8 * b)).toByte()
            val stream = aes.doFinal(counter)
            for (j in 0 until 16) {
                if (offset + j >= data.size) break
                out[offset + j] = (data[offset + j].toInt() xor stream[j].toInt()).toByte()
            }
            offset += 16
            block++
        }
        return out
    }

    fun parseBatteryMonitor(p: ByteArray): ShuntReading {
        val r = BitReader(p)
        val remaining = r.unsigned(16)
        val voltage = r.signed(16)
        val alarm = r.unsigned(16)
        r.unsigned(16) // aux value (unused: no aux input)
        val auxMode = r.unsigned(2)
        val current = r.unsigned(22)
        val consumed = r.unsigned(20)
        val soc = r.unsigned(10)
        return ShuntReading(
            remainingMinutes = remaining.takeIf { it != 0xFFFF },
            voltage = voltage.takeIf { it != 0x7FFF }?.let { it / 100.0 },
            alarmBits = alarm,
            auxMode = auxMode,
            current = current.takeIf { it != 0x3FFFFF }?.let { BitReader.toSigned(it, 22) / 1000.0 },
            consumedAh = consumed.takeIf { it != 0xFFFFF }?.let { -it / 10.0 },
            stateOfCharge = soc.takeIf { it != 0x3FF }?.let { it / 10.0 },
        )
    }

    fun parseSolarCharger(p: ByteArray): SolarReading {
        val r = BitReader(p)
        val state = r.unsigned(8)
        val error = r.unsigned(8)
        val voltage = r.signed(16)
        val current = r.signed(16)
        val yieldToday = r.unsigned(16)
        val power = r.unsigned(16)
        val load = r.unsigned(9)
        return SolarReading(
            chargeState = state.takeIf { it != 0xFF },
            error = error.takeIf { it != 0xFF },
            batteryVoltage = voltage.takeIf { it != 0x7FFF }?.let { it / 100.0 },
            batteryCurrent = current.takeIf { it != 0x7FFF }?.let { it / 10.0 },
            yieldTodayWh = yieldToday.takeIf { it != 0xFFFF }?.let { it * 10 },
            solarPower = power.takeIf { it != 0xFFFF },
            loadCurrent = load.takeIf { it != 0x1FF }?.let { it / 10.0 },
        )
    }

    fun chargeStateName(state: Int?): String = when (state) {
        null -> "--"
        0 -> "Off"
        1 -> "Low power"
        2 -> "Fault"
        3 -> "Bulk"
        4 -> "Absorption"
        5 -> "Float"
        6 -> "Storage"
        7 -> "Equalize"
        245 -> "Starting up"
        246 -> "Repeated absorption"
        247 -> "Recondition"
        248 -> "Battery safe"
        252 -> "External control"
        else -> "State $state"
    }

    fun chargerErrorName(error: Int): String = when (error) {
        1 -> "Battery temperature too high"
        2 -> "Battery voltage too high"
        3, 4, 5 -> "Remote temperature sensor failure"
        6, 7, 8 -> "Remote battery voltage sense failure"
        11 -> "High ripple voltage"
        14 -> "Battery temperature too low"
        17 -> "Charger temperature too high"
        18 -> "Charger over-current"
        20 -> "Bulk time limit exceeded"
        21 -> "Current sensor issue"
        22, 23 -> "Internal temperature sensor failure"
        24 -> "Fan failure"
        26 -> "Terminals overheated"
        27 -> "Short circuit"
        28 -> "Converter issue"
        29 -> "Over-charge protection"
        33 -> "PV input voltage too high"
        34 -> "PV input current too high"
        35 -> "PV input power too high"
        38, 39, 40 -> "PV input shutdown"
        65, 66 -> "Communication issue"
        67 -> "BMS connection lost"
        68, 69, 70, 71 -> "Network misconfigured"
        in 80..87 -> "PV input shutdown"
        114 -> "CPU temperature too high"
        116 -> "Calibration data lost"
        117 -> "Invalid firmware"
        119 -> "Settings data lost"
        else -> "Charger error $error"
    }

    /** SmartShunt alarm bits → (label, is it a serious alarm). */
    val shuntAlarms: List<Triple<Int, String, Severity>> = listOf(
        Triple(1, "Low voltage", Severity.ALARM),
        Triple(2, "High voltage", Severity.ALARM),
        Triple(4, "Low state of charge", Severity.WARNING),
        Triple(8, "Low starter voltage", Severity.WARNING),
        Triple(16, "High starter voltage", Severity.WARNING),
        Triple(32, "Low temperature", Severity.WARNING),
        Triple(64, "High temperature", Severity.ALARM),
        Triple(128, "Midpoint voltage deviation", Severity.WARNING),
        Triple(256, "Overload", Severity.ALARM),
        Triple(512, "DC ripple", Severity.WARNING),
        Triple(4096, "Short circuit", Severity.ALARM),
        Triple(8192, "BMS lockout", Severity.ALARM),
    )

    private fun u8(p: ByteArray, i: Int) = p[i].toInt() and 0xFF

    /** Reads bit fields packed LSB-first, as Victron does. */
    private class BitReader(private val data: ByteArray) {
        private var index = 0

        fun unsigned(bits: Int): Int {
            var value = 0
            for (pos in 0 until bits) {
                val byte = index ushr 3
                val bit = if (byte < data.size) (data[byte].toInt() ushr (index and 7)) and 1 else 0
                value = value or (bit shl pos)
                index++
            }
            return value
        }

        fun signed(bits: Int): Int = toSigned(unsigned(bits), bits)

        companion object {
            fun toSigned(value: Int, bits: Int): Int =
                if (value and (1 shl (bits - 1)) != 0) value - (1 shl bits) else value
        }
    }
}

sealed interface VictronReading

data class ShuntReading(
    val remainingMinutes: Int?,
    val voltage: Double?,
    val alarmBits: Int,
    val auxMode: Int,
    /** A; positive = charging. */
    val current: Double?,
    /** Ah, zero or negative. */
    val consumedAh: Double?,
    /** %. */
    val stateOfCharge: Double?,
) : VictronReading

data class SolarReading(
    val chargeState: Int?,
    val error: Int?,
    val batteryVoltage: Double?,
    /** A into the battery. */
    val batteryCurrent: Double?,
    val yieldTodayWh: Int?,
    /** PV power, W. */
    val solarPower: Int?,
    val loadCurrent: Double?,
) : VictronReading

enum class VictronStatus { NOT_CONFIGURED, NO_KEY, STOPPED, WAITING, RECEIVING, WRONG_KEY, WRONG_TYPE }

data class VictronDeviceState(
    val address: String? = null,
    val name: String = "",
    val status: VictronStatus = VictronStatus.NOT_CONFIGURED,
    val reading: VictronReading? = null,
    val lastUpdate: Long = 0L,
    /** When listening started, for the "not heard" alarm. */
    val since: Long = 0L,
) {
    val isConfigured: Boolean get() = address != null
    fun isLive(now: Long): Boolean = reading != null && now - lastUpdate < STALE_AFTER_MS
    val shunt: ShuntReading? get() = reading as? ShuntReading
    val solar: SolarReading? get() = reading as? SolarReading

    companion object {
        const val STALE_AFTER_MS = 30_000L
    }
}
