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
        val app = context.bmsApp
        val settings = app.settings.current
        val anyConfigured = settings.slots.any { it.address != null }
        val reason = if (intent.action == Intent.ACTION_BOOT_COMPLETED) "boot" else "app update"
        if (settings.monitoringEnabled && settings.startOnBoot && anyConfigured && Permissions.hasRequired(context)) {
            app.diagnostics.info("boot", "Starting monitoring after $reason")
            BmsService.start(context)
        } else {
            app.diagnostics.info("boot", "Not starting after $reason (monitoring off, start-on-boot off, nothing set up or no permission)")
        }
    }
}
