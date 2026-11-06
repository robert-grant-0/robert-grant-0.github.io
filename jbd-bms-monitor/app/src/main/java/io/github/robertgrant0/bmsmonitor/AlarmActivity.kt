package io.github.robertgrant0.bmsmonitor

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.robertgrant0.bmsmonitor.ui.AlarmScreen
import io.github.robertgrant0.bmsmonitor.ui.BmsTheme
import io.github.robertgrant0.bmsmonitor.ui.InterFonts

/** Full-screen alarm shown over the lock screen while the siren sounds. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = bmsApp
        setContent {
            BmsTheme(dark = true, fonts = InterFonts) {
                val messages by app.siren.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(messages.isEmpty()) {
                    if (messages.isEmpty()) finish()
                }
                AlarmScreen(
                    messages = messages,
                    onSilence = { app.siren.silence() },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }
}
