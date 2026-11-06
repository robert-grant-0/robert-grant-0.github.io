package io.github.robertgrant0.bmsmonitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.robertgrant0.bmsmonitor.bmsApp

/** Handles the "Silence" button on the siren notification. */
class SilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.bmsApp.siren.silence()
    }
}
