package io.github.robertgrant0.bmsmonitor.bms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class GattException(message: String) : IOException(message)

/**
 * One GATT connection to a JBD BMS. Turns Android's callback API into suspend
 * functions. Android only allows one outstanding GATT operation per connection,
 * so callers must use this from a single coroutine.
 */
@SuppressLint("MissingPermission") // Callers check BLUETOOTH_CONNECT before creating a session.
class GattSession(private val context: Context, private val device: BluetoothDevice) {

    private enum class OpType { DISCOVER, DESCRIPTOR_WRITE, WRITE, RSSI }

    private class PendingOp(val type: OpType) {
        val result = CompletableDeferred<Int>()
    }

    private var gatt: BluetoothGatt? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val connected = CompletableDeferred<Unit>()
    private val frames = Channel<JbdFrame>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val assembler = JbdFrameAssembler()

    @Volatile private var pending: PendingOp? = null
    @Volatile private var closedCause: GattException? = null
    @Volatile private var lastRssi: Int? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val ex = GattException(describeStatus(status))
                closedCause = ex
                connected.completeExceptionally(ex)
                pending?.result?.completeExceptionally(ex)
                frames.close(ex)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = finish(OpType.DISCOVER, status)

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
            finish(OpType.DESCRIPTOR_WRITE, status)

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) =
            finish(OpType.WRITE, status)

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) lastRssi = rssi
            finish(OpType.RSSI, status)
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = onNotification(c, value)

        // Android 12 and older
        @Deprecated("Deprecated in Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onNotification(c, c.value)
        }
    }

    private fun finish(type: OpType, status: Int) {
        val op = pending
        if (op != null && op.type == type) op.result.complete(status)
    }

    private fun onNotification(c: BluetoothGattCharacteristic, value: ByteArray?) {
        if (value == null || c.uuid != JbdProtocol.NOTIFY_UUID) return
        val complete = synchronized(assembler) { assembler.feed(value) }
        for (f in complete) frames.trySend(f)
    }

    suspend fun connect(timeoutMs: Long) {
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw GattException("Bluetooth stack refused the connection")
        gatt = g
        withTimeoutOrNull(timeoutMs) { connected.await() }
            ?: throw GattException("No response (out of range or in use by another phone?)")
    }

    suspend fun discoverServices() {
        val status = runOp(OpType.DISCOVER, 15_000) { it.discoverServices() }
        if (status != BluetoothGatt.GATT_SUCCESS) throw GattException("Service discovery failed ($status)")
        val service = gatt?.getService(JbdProtocol.SERVICE_UUID)
            ?: throw GattException("Not a JBD BMS (service FF00 missing)")
        notifyChar = service.getCharacteristic(JbdProtocol.NOTIFY_UUID)
            ?: throw GattException("JBD notify characteristic FF01 missing")
        writeChar = service.getCharacteristic(JbdProtocol.WRITE_UUID)
            ?: throw GattException("JBD write characteristic FF02 missing")
    }

    suspend fun enableNotifications() {
        val g = gatt ?: throw GattException("Not connected")
        val c = notifyChar ?: throw GattException("Services not discovered")
        if (!g.setCharacteristicNotification(c, true)) throw GattException("Could not enable notifications")
        val cccd = c.getDescriptor(JbdProtocol.CCCD_UUID) ?: return
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val status = runOp(OpType.DESCRIPTOR_WRITE, 5_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            } else {
                legacyWriteDescriptor(it, cccd, value)
            }
        }
        if (status != BluetoothGatt.GATT_SUCCESS) throw GattException("Could not subscribe to BMS ($status)")
    }

    /** Sends a read request and waits for the matching response, or null on timeout. */
    suspend fun request(cmd: Int, timeoutMs: Long = 3_000): JbdFrame? {
        while (frames.tryReceive().isSuccess) { /* drop stale frames */ }
        synchronized(assembler) { assembler.reset() }
        write(JbdProtocol.readCommand(cmd))
        return withTimeoutOrNull(timeoutMs) { awaitFrame(cmd) }
    }

    private suspend fun awaitFrame(cmd: Int): JbdFrame {
        while (true) {
            val f = frames.receive()
            if (f.cmd == cmd) return f
        }
    }

    private suspend fun write(bytes: ByteArray) {
        val c = writeChar ?: throw GattException("Services not discovered")
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        runOp(OpType.WRITE, 3_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS
            } else {
                legacyWriteCharacteristic(it, c, bytes, type)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyWriteDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean {
        d.value = value
        return g.writeDescriptor(d)
    }

    @Suppress("DEPRECATION")
    private fun legacyWriteCharacteristic(
        g: BluetoothGatt,
        c: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        c.writeType = writeType
        c.value = value
        return g.writeCharacteristic(c)
    }

    /** Signal strength in dBm, or null if it couldn't be read. Never throws for a live link. */
    suspend fun readRssi(): Int? {
        closedCause?.let { throw it }
        return try {
            val status = runOp(OpType.RSSI, 2_000) { it.readRemoteRssi() }
            if (status == BluetoothGatt.GATT_SUCCESS) lastRssi else null
        } catch (e: GattException) {
            closedCause?.let { throw it }
            null
        }
    }

    private suspend fun runOp(type: OpType, timeoutMs: Long, start: (BluetoothGatt) -> Boolean): Int {
        closedCause?.let { throw it }
        val g = gatt ?: throw GattException("Not connected")
        val op = PendingOp(type)
        pending = op
        try {
            if (!start(g)) {
                closedCause?.let { throw it }
                throw GattException("Bluetooth busy (${type.name.lowercase()})")
            }
            return withTimeoutOrNull(timeoutMs) { op.result.await() }
                ?: throw GattException("BMS did not respond (${type.name.lowercase()})")
        } finally {
            if (pending === op) pending = null
        }
    }

    fun close() {
        val g = gatt ?: return
        gatt = null
        frames.close()
        try {
            g.disconnect()
        } catch (_: Exception) {
        }
        try {
            g.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun describeStatus(status: Int): String = when (status) {
            0 -> "Disconnected"
            8 -> "Signal lost (out of range?)"
            19 -> "BMS closed the connection"
            22 -> "Connection closed by Android"
            62 -> "Could not establish connection"
            133 -> "Connection failed (error 133)"
            147 -> "Connection timed out"
            else -> "Connection error $status"
        }
    }
}
