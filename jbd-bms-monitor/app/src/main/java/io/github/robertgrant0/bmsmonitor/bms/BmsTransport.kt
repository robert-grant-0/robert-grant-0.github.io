package io.github.robertgrant0.bmsmonitor.bms

import java.io.IOException

/** An open, ready-to-poll link to one BMS. */
interface BmsLink {
    /** Sends a read request and returns the matching response, or null if none arrived in time. */
    suspend fun request(cmd: Int): JbdFrame?

    /** Signal strength in dBm, or null if it couldn't be read. Throws if the link has died. */
    suspend fun readRssi(): Int?

    fun close()
}

/**
 * Everything [BmsConnection] needs from the outside world, so the connect/poll/reconnect
 * logic can be tested without Android or a radio.
 */
interface BmsEnvironment {
    fun canConnect(): Boolean

    fun bluetoothEnabled(): Boolean

    /** Waits up to [timeoutMs] for the BMS at [address] to be seen advertising. */
    suspend fun awaitAdvertisement(address: String, timeoutMs: Long): Boolean

    /** Connects to [address] and prepares the link for polling. Throws, with a readable message, on failure. */
    suspend fun open(address: String): BmsLink

    fun now(): Long
}

/** A readable failure in talking to a BMS. */
open class BmsException(message: String) : IOException(message)
