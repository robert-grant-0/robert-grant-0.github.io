package io.github.robertgrant0.bmsmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    /** Runtime permissions the app cannot work without. */
    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 11 and older need location permission to scan for Bluetooth devices.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Requested alongside [required] but not essential. */
    val optional: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    fun hasRequired(ctx: Context): Boolean = required.all { granted(ctx, it) }

    fun canConnect(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)

    fun canScan(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(ctx, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(ctx: Context, permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
}
