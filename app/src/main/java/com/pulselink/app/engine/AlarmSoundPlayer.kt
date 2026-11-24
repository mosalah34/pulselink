package com.pulselink.app.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pulselink.app.data.model.AlarmToneType
import com.pulselink.app.data.model.AlertType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class AlarmSoundPlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var alarmJob: Job? = null
    private var isPlaying = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Synthesizes and plays a pure tone using native AudioTrack.
     */
    private fun playTone(frequencyHz: Double, durationMs: Int) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val generatedSnd = ByteArray(2 * numSamples)

            for (i in 0 until numSamples) {
                val dVal = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / frequencyHz))
                val valShort = (dVal * 32767).toInt().toShort()
                generatedSnd[2 * i] = (valShort.toInt() and 0x00ff).toByte()
                generatedSnd[2 * i + 1] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(generatedSnd.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(generatedSnd, 0, generatedSnd.size)
            audioTrack.play()
            Thread.sleep(durationMs.toLong())
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            // AudioTrack failure handled safely
        }
    }

    private fun triggerVibration(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun startAlert(
        alertType: AlertType,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        toneType: AlarmToneType = AlarmToneType.MEDICAL_BUZZER
    ) {
        stop()
        isPlaying = true

        val frequency = when (toneType) {
            AlarmToneType.MEDICAL_BUZZER -> 2800.0 // Piercing hospital piezo frequency
            AlarmToneType.PULSE_CHIME -> 1800.0
            AlarmToneType.RAPID_SIREN -> 3200.0
        }

        alarmJob = scope.launch {
            while (isActive && isPlaying) {
                when (alertType) {
                    AlertType.LOW_HEART_RATE -> {
                        // Pattern: Two short beeps (150ms on, 150ms off)
                        if (vibrationEnabled) {
                            triggerVibration(longArrayOf(0, 150, 150, 150))
                        }
                        if (soundEnabled) {
                            repeat(2) {
                                if (!isActive) return@launch
                                playTone(frequency, 150)
                                delay(150)
                            }
                        }
                        delay(1200)
                    }
                    AlertType.HIGH_HEART_RATE -> {
                        // Pattern: Three short beeps (120ms on, 120ms off)
                        if (vibrationEnabled) {
                            triggerVibration(longArrayOf(0, 120, 120, 120, 120, 120))
                        }
                        if (soundEnabled) {
                            repeat(3) {
                                if (!isActive) return@launch
                                playTone(frequency, 120)
                                delay(120)
                            }
                        }
                        delay(1000)
                    }
                    AlertType.HIGH_TEMPERATURE -> {
                        // Pattern: Three long beeps (400ms on, 200ms off) - Highest priority
                        if (vibrationEnabled) {
                            triggerVibration(longArrayOf(0, 400, 200, 400, 200, 400))
                        }
                        if (soundEnabled) {
                            repeat(3) {
                                if (!isActive) return@launch
                                playTone(frequency + 400.0, 400)
                                delay(200)
                            }
                        }
                        delay(800)
                    }
                    AlertType.SENSOR_DISCONNECTED -> {
                        // Warning pulse
                        if (vibrationEnabled) {
                            triggerVibration(longArrayOf(0, 300))
                        }
                        if (soundEnabled) {
                            playTone(1500.0, 300)
                        }
                        delay(2500)
                    }
                }
            }
        }
    }

    /**
     * Plays a brief 1-second sample to test the alarm sound in settings.
     */
    fun testAlarmSound(toneType: AlarmToneType) {
        stop()
        scope.launch {
            val freq = when (toneType) {
                AlarmToneType.MEDICAL_BUZZER -> 2800.0
                AlarmToneType.PULSE_CHIME -> 1800.0
                AlarmToneType.RAPID_SIREN -> 3200.0
            }
            triggerVibration(longArrayOf(0, 150, 100, 150))
            repeat(3) {
                playTone(freq, 150)
                delay(120)
            }
        }
    }

    fun stop() {
        isPlaying = false
        alarmJob?.cancel()
        alarmJob = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) { /* ignore */ }
    }
}

