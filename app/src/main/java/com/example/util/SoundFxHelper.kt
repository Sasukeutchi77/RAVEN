package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundFxHelper {
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isSoundEnabled: Boolean = true

    /**
     * Plays a pleasant synthesized chime when claiming Orbs or making a purchase.
     */
    fun playChime() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(523.25, 659.25, 783.99, 1046.50), 120)
            } catch (_: Exception) {}
        }
    }

    /**
     * Plays a fast wheel spin tick.
     */
    fun playTick() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(880.0), 25)
            } catch (_: Exception) {}
        }
    }

    /**
     * Plays a jackpot / victory fanfare.
     */
    fun playJackpot() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(523.25, 659.25, 783.99, 1046.50, 1318.51, 1567.98), 100)
            } catch (_: Exception) {}
        }
    }

    fun playAirhorn() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(440.0, 466.16, 440.0, 440.0), 90)
            } catch (_: Exception) {}
        }
    }

    fun playDrumroll() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(180.0, 210.0, 195.0, 240.0, 220.0, 280.0, 400.0), 65)
            } catch (_: Exception) {}
        }
    }

    fun playRimshot() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(330.0, 220.0, 660.0), 110)
            } catch (_: Exception) {}
        }
    }

    fun playApplause() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(400.0, 600.0, 520.0, 720.0, 640.0, 880.0), 75)
            } catch (_: Exception) {}
        }
    }

    fun playLevelUp() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(440.0, 554.37, 659.25, 880.0, 1108.73), 95)
            } catch (_: Exception) {}
        }
    }

    fun playNotification() {
        if (!isSoundEnabled) return
        scope.launch {
            try {
                playToneFrequencies(listOf(783.99, 1046.50), 80)
            } catch (_: Exception) {}
        }
    }

    private fun playToneFrequencies(frequencies: List<Double>, durationMsPerTone: Int) {
        val sampleRate = 22050
        val totalSamples = (sampleRate * (durationMsPerTone * frequencies.size) / 1000)
        val generatedSnd = ShortArray(totalSamples)

        val samplesPerTone = (sampleRate * durationMsPerTone) / 1000
        for (i in frequencies.indices) {
            val freq = frequencies[i]
            val offset = i * samplesPerTone
            for (j in 0 until samplesPerTone) {
                val idx = offset + j
                if (idx < generatedSnd.size) {
                    val time = j.toDouble() / sampleRate
                    // Apply fade-out envelope to avoid audio clicks
                    val envelope = (1.0 - (j.toDouble() / samplesPerTone)).coerceIn(0.0, 1.0)
                    val sample = (sin(2.0 * Math.PI * freq * time) * 32767 * 0.45 * envelope).toInt()
                    generatedSnd[idx] = sample.toShort()
                }
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
            .setBufferSizeInBytes(generatedSnd.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(generatedSnd, 0, generatedSnd.size)
        track.play()
    }
}
