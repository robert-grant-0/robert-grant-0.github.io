package io.github.robertgrant0.bmsmonitor

import android.app.Application
import android.content.Context
import io.github.robertgrant0.bmsmonitor.bms.Alarm
import io.github.robertgrant0.bmsmonitor.bms.BmsManager
import io.github.robertgrant0.bmsmonitor.bms.SirenLatch
import io.github.robertgrant0.bmsmonitor.data.PowerHistory
import io.github.robertgrant0.bmsmonitor.data.SettingsRepository
import io.github.robertgrant0.bmsmonitor.service.Notifications
import io.github.robertgrant0.bmsmonitor.service.SirenPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BmsApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var manager: BmsManager
        private set

    /** Latches the siren on for faults until someone presses Silence. */
    val siren = SirenLatch()

    /** Bank-wide alarms (SmartShunt, solar charger, current sharing), computed by the service. */
    val bankAlarms = MutableStateFlow<List<Alarm>>(emptyList())

    /** Recent bank and solar power for the home-screen chart (memory only). */
    val history = PowerHistory()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        manager = BmsManager(this, settings)
        Notifications.createChannels(this)

        val player = SirenPlayer(this)
        appScope.launch {
            siren.ringing.collect { messages ->
                if (messages.isEmpty()) {
                    player.stop()
                    Notifications.cancelSiren(this@BmsApp)
                } else {
                    player.start(fullVolume = settings.current.sirenFullVolume)
                    Notifications.postSiren(this@BmsApp, messages)
                }
            }
        }
    }
}

val Context.bmsApp: BmsApp get() = applicationContext as BmsApp
