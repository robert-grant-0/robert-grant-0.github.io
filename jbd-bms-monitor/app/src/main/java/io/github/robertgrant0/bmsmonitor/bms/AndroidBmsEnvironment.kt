package io.github.robertgrant0.bmsmonitor.bms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.robertgrant0.bmsmonitor.Permissions
import io.github.robertgrant0.bmsmonitor.data.DiagnosticsLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** The real thing: Android Bluetooth behind [BmsEnvironment]. */
@SuppressLint("MissingPermission") // BmsConnection checks canConnect() before calling open().
class AndroidBmsEnvironment(
    private val context: Context,
    private val finder: DeviceFinder,
    private val log: DiagnosticsLog,
) : BmsEnvironment {
    /** Android's BLE stack misbehaves when several connections are set up at once. */
    private val setupLock = Mutex()

    override fun canConnect(): Boolean = Permissions.canConnect(context)

    override fun bluetoothEnabled(): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    override suspend fun awaitAdvertisement(address: String, timeoutMs: Long): Boolean {
        finder.setWaiting(address, true)
        try {
            return withTimeoutOrNull(timeoutMs) { finder.sightings.first { it.address == address } } != null
        } finally {
            finder.setWaiting(address, false)
        }
    }

    override suspend fun open(address: String): BmsLink {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw GattException("Bluetooth is off")
        val session = GattSession(context, adapter.getRemoteDevice(address), log)
        try {
            setupLock.withLock {
                session.connect(CONNECT_TIMEOUT_MS)
                delay(500) // Some BLE modules need a moment before service discovery.
                session.discoverServices()
                session.enableNotifications()
            }
        } catch (e: Throwable) {
            session.close()
            throw e
        }
        return session
    }

    override fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
