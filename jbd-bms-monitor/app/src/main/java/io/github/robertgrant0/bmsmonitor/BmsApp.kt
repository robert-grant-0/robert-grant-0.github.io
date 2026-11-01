package io.github.robertgrant0.bmsmonitor

import android.app.Application
import android.content.Context
import io.github.robertgrant0.bmsmonitor.bms.BmsManager
import io.github.robertgrant0.bmsmonitor.data.SettingsRepository
import io.github.robertgrant0.bmsmonitor.service.Notifications

class BmsApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var manager: BmsManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        manager = BmsManager(this, settings)
        Notifications.createChannels(this)
    }
}

val Context.bmsApp: BmsApp get() = applicationContext as BmsApp
