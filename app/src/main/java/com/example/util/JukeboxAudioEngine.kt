package com.example.util

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioEqPreset
import com.example.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real Media3 ExoPlayer engine for the collaborative Voice Jukebox.
 * Supports streaming any HTTP(S) audio URL AND generates clean polyphonic WAV
 * loops locally for Raven's built-in catalog tracks so playback is always real and audible.
 */
class JukeboxAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onProgressTick: (elapsedSec: Int) -> Unit,
    private val onTrackEnded: () -> Unit
) {
    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var progressJob: Job? = null
    private var currentTrackId: String? = null
    private var currentPreset: AudioEqPreset = AudioEqPreset.STUDIO
    private var masterVolume: Float = 0.85f

    private fun ensurePlayer(): ExoPlayer? {
        if (exoPlayer != null) return exoPlayer
        return try {
            val player = ExoPlayer.Builder(context.applicationContext).build()
            player.volume = masterVolume
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onTrackEnded()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachAudioEffects(audioSessionId)
                }
            })
            exoPlayer = player
            attachAudioEffects(player.audioSessionId)
            player
        } catch (e: Throwable) {
            Log.w("JukeboxAudio", "ExoPlayer unavailable in this runtime", e)
            null
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            equalizer?.release()
            bassBoost?.release()
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            applyEqPreset(currentPreset)
        } catch (e: Throwable) {
            Log.w("JukeboxAudio", "AudioEffect setup skipped", e)
        }
    }

    fun applyEqPreset(preset: AudioEqPreset) {
        currentPreset = preset
        val eq = equalizer ?: return
        try {
            val bands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            for (b in 0 until bands) {
                val bandShort = b.toShort()
                val ratio = when (preset) {
                    AudioEqPreset.NORMAL -> 0.0f
                    AudioEqPreset.STUDIO -> if (b == 0 || b == bands - 1) 0.35f else 0.15f
                    AudioEqPreset.RADIO -> if (b in 1 until (bands - 1)) 0.45f else -0.15f
                    AudioEqPreset.BASS_BOOST -> if (b <= 1) 0.85f else 0.10f
                }
                val target = (ratio * (maxLevel - minLevel) * 0.5f).toInt()
                    .coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                eq.setBandLevel(bandShort, target)
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(
                        when (preset) {
                            AudioEqPreset.BASS_BOOST -> 800
                            AudioEqPreset.STUDIO -> 300
                            else -> 0
                        }
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w("JukeboxAudio", "Failed to apply EQ preset", e)
        }
    }

    fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        try {
            exoPlayer?.volume = masterVolume
        } catch (_: Throwable) {
        }
    }

    fun syncWithState(track: MusicTrack?, isPlaying: Boolean, targetElapsedSec: Int = 0) {
        if (track == null) {
            pause()
            currentTrackId = null
            return
        }
        val player = ensurePlayer() ?: return
        scope.launch(Dispatchers.Main) {
            try {
                if (currentTrackId != track.id) {
                    currentTrackId = track.id
                    val mediaUri = resolveMediaUri(track)
                    player.setMediaItem(MediaItem.fromUri(mediaUri))
                    player.repeatMode = if (track.streamUrl.isNullOrBlank()) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                    player.prepare()
                    if (targetElapsedSec > 0) {
                        player.seekTo(targetElapsedSec * 1000L)
                    }
                }
                if (isPlaying && !player.isPlaying) {
                    player.play()
                    startProgressLoop()
                } else if (!isPlaying && player.isPlaying) {
                    player.pause()
                    stopProgressLoop()
                }
            } catch (e: Throwable) {
                Log.w("JukeboxAudio", "syncWithState error", e)
            }
        }
    }

    fun pause() {
        stopProgressLoop()
        try {
            exoPlayer?.pause()
        } catch (_: Throwable) {
        }
    }

    fun stopAndRelease() {
        stopProgressLoop()
        try {
            equalizer?.release()
            bassBoost?.release()
            exoPlayer?.release()
        } catch (_: Throwable) {
        } finally {
            equalizer = null
            bassBoost = null
            exoPlayer = null
            currentTrackId = null
        }
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1000L)
                val player = exoPlayer ?: break
                if (player.isPlaying) {
                    onProgressTick((player.currentPosition / 1000L).toInt())
                }
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun resolveMediaUri(track: MusicTrack): Uri {
        val customUrl = track.streamUrl?.trim()
        if (!customUrl.isNullOrBlank() && (customUrl.startsWith("http://") || customUrl.startsWith("https://"))) {
            return Uri.parse(customUrl)
        }
        val wavFile = generateLocalSynthWav(track)
        return Uri.fromFile(wavFile)
    }

    private fun generateLocalSynthWav(track: MusicTrack): File {
        val safeKey = track.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(context.cacheDir, "jukebox_$safeKey.wav")
        if (file.exists() && file.length() > 44) return file

        val sampleRate = 22050
        val durationSec = 12
        val totalSamples = sampleRate * durationSec
        val chordFreqs = when {
            track.id.contains("trk_1") -> floatArrayOf(220f, 277.18f, 329.63f, 440f) // A major Synthwave
            track.id.contains("trk_2") -> floatArrayOf(174.61f, 220f, 261.63f, 329.63f) // Fmaj7 Lo-Fi
            track.id.contains("trk_3") -> floatArrayOf(146.83f, 220f, 293.66f, 349.23f) // Dm Cyberpunk
            track.id.contains("trk_4") -> floatArrayOf(261.63f, 329.63f, 392f, 493.88f) // Cmaj7 Ambient
            else -> floatArrayOf(293.66f, 369.99f, 440f, 587.33f) // Chiptune D
        }

        val pcmBytes = ByteArray(totalSamples * 2)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val beatIndex = ((t * 2.0).toInt()) % chordFreqs.size
            val root = chordFreqs[0]
            val arp = chordFreqs[beatIndex]
            val env = 0.65 + 0.35 * sin(2.0 * PI * 2.0 * t)
            val sampleVal = (0.35 * sin(2.0 * PI * root * 0.5 * t) +
                0.40 * sin(2.0 * PI * arp * t) * env) * 0.35
            val pcm = (sampleVal * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer.putShort(pcm)
        }

        FileOutputStream(file).use { out ->
            val dataSize = pcmBytes.size
            val chunkSize = 36 + dataSize
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM subchunk size
            header.putShort(1) // PCM format
            header.putShort(1) // Mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2) // Block align
            header.putShort(16) // Bits per sample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())
            out.write(pcmBytes)
        }
        return file
    }
}
