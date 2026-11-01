package io.github.robertgrant0.bmsmonitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.robertgrant0.bmsmonitor.Permissions
import io.github.robertgrant0.bmsmonitor.bmsApp

/** Restarts monitoring after the tablet reboots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = context.bmsApp.settings.current
        val anyConfigured = settings.slots.any { it.address != null }
        if (settings.monitoringEnabled && settings.startOnBoot && anyConfigured && Permissions.hasRequired(context)) {
            BmsService.start(context)
        }
    }
}
