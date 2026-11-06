package io.github.robertgrant0.bmsmonitor.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a looping wailing siren on the ALARM audio stream (so it sounds even when the
 * device is on silent), optionally at full alarm volume, and vibrates continuously.
 */
class SirenPlayer(private val context: Context) {
    private var track: AudioTrack? = null
    private var savedAlarmVolume: Int? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val isPlaying: Boolean @Synchronized get() = track != null

    @Synchronized
    fun start(fullVolume: Boolean) {
        if (track != null) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (fullVolume && audio != null) {
            try {
                savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                audio.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0,
                )
            } catch (_: Exception) {
                savedAlarmVolume = null
            }
        }
        try {
            val pcm = wail()
            val t = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            t.play()
            track = t
        } catch (_: Exception) {
            // Audio unavailable; vibration and the full-screen alarm still work.
        }
        vibrator()?.let { v ->
            try {
                @Suppress("DEPRECATION")
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0), attributes)
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    fun stop() {
        track?.let { t ->
            try {
                t.stop()
            } catch (_: Exception) {
            }
            t.release()
        }
        track = null
        savedAlarmVolume?.let { v ->
            try {
                context.getSystemService(AudioManager::class.java)
                    ?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0)
            } catch (_: Exception) {
            }
        }
        savedAlarmVolume = null
        try {
            vibrator()?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    /** One 2-second siren cycle: the pitch sweeps 600 → 1500 → 600 Hz. */
    private fun wail(): ShortArray {
        val n = SAMPLE_RATE * 2
        var phase = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / n
            val sweep = if (t < 0.5) t * 2 else (1 - t) * 2
            val freq = 600.0 + 900.0 * sweep
            phase += 2 * PI * freq / SAMPLE_RATE
            // A touch of the third harmonic makes it harsher and easier to hear.
            val s = (sin(phase) + 0.35 * sin(3 * phase)) / 1.35
            (s * 0.95 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
