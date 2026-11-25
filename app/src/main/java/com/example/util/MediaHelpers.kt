package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object CloudinaryHelper {
    private const val PREFS_NAME = "raven_cloudinary_prefs"
    private const val KEY_CLOUD_NAME = "cloud_name"
    private const val KEY_UPLOAD_PRESET = "upload_preset"
    private const val MAX_MEDIA_BYTES = 15L * 1024L * 1024L // 15 MB hard cap
    private val CONFIG_IDENTIFIER_REGEX = Regex("^[a-zA-Z0-9_-]{2,64}$")
    private val SAFE_SUBDIR_REGEX = Regex("^[a-zA-Z0-9_-]{1,32}$")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    // Cloudinary credentials can be set by the user or dynamically configured
    var cloudName: String = "demo"
    var uploadPreset: String = "unsigned_preset"

    fun isValidConfigToken(value: String): Boolean =
        CONFIG_IDENTIFIER_REGEX.matches(value.trim())

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedName = prefs.getString(KEY_CLOUD_NAME, "demo") ?: "demo"
            val savedPreset = prefs.getString(KEY_UPLOAD_PRESET, "unsigned_preset") ?: "unsigned_preset"
            cloudName = if (isValidConfigToken(savedName)) savedName.trim() else "demo"
            uploadPreset = if (isValidConfigToken(savedPreset)) savedPreset.trim() else "unsigned_preset"
        } catch (_: Exception) {
        }
    }

    fun saveConfig(context: Context, name: String, preset: String): Boolean {
        val cleanName = name.trim()
        val cleanPreset = preset.trim()
        if (cleanName.isNotEmpty() && !isValidConfigToken(cleanName)) return false
        if (cleanPreset.isNotEmpty() && !isValidConfigToken(cleanPreset)) return false

        cloudName = cleanName.ifBlank { "demo" }
        uploadPreset = cleanPreset.ifBlank { "unsigned_preset" }
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CLOUD_NAME, cloudName)
                .putString(KEY_UPLOAD_PRESET, uploadPreset)
                .apply()
        } catch (_: Exception) {
        }
        return true
    }

    fun isConfigured(): Boolean =
        cloudName != "demo" &&
            uploadPreset != "unsigned_preset" &&
            isValidConfigToken(cloudName) &&
            isValidConfigToken(uploadPreset)

    suspend fun uploadImage(context: Context, imageUri: Uri): String =
        persistImage(context, imageUri, subDir = null)

    suspend fun persistImage(
        context: Context,
        imageUri: Uri,
        subDir: String? = "backgrounds"
    ): String = withContext(Dispatchers.IO) {
        var destFile: File? = null
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext ""
            val safeSubDir = subDir?.trim()?.takeIf { SAFE_SUBDIR_REGEX.matches(it) }
            val targetDir = if (safeSubDir != null) {
                File(context.filesDir, safeSubDir).apply { if (!exists()) mkdirs() }
            } else {
                context.cacheDir
            }
            val file = File(targetDir, "img_${System.currentTimeMillis()}.jpg")
            destFile = file
            val copiedBytes = copyWithSizeCap(inputStream, file)
            if (copiedBytes <= 0L) {
                file.delete()
                return@withContext ""
            }

            if (!isConfigured()) {
                return@withContext file.absolutePath
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    val secureUrl = json.optString("secure_url", "").trim()
                    if (secureUrl.startsWith("https://")) secureUrl else file.absolutePath
                } else {
                    Log.w("Cloudinary", "Upload failed: ${response.code}, falling back to local file")
                    file.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error uploading to Cloudinary", e)
            destFile?.takeIf { it.exists() && it.length() > 0L }?.absolutePath.orEmpty()
        }
    }

    suspend fun uploadFile(
        context: Context,
        fileUri: Uri,
        mimeType: String = "application/octet-stream"
    ): String = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@withContext ""
            val file = File(context.cacheDir, "file_${System.currentTimeMillis()}")
            tempFile = file
            val copiedBytes = copyWithSizeCap(inputStream, file)
            if (copiedBytes <= 0L) {
                file.delete()
                return@withContext ""
            }

            if (!isConfigured()) {
                return@withContext file.absolutePath
            }

            val sanitizedMime = mimeType.trim().takeIf { it.contains("/") && it.length <= 64 } ?: "application/octet-stream"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(sanitizedMime.toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/auto/upload")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    val secureUrl = json.optString("secure_url", "").trim()
                    if (secureUrl.startsWith("https://")) secureUrl else file.absolutePath
                } else {
                    file.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error uploading file", e)
            tempFile?.takeIf { it.exists() && it.length() > 0L }?.absolutePath.orEmpty()
        }
    }

    private fun copyWithSizeCap(inputStream: java.io.InputStream, destFile: File): Long {
        var total = 0L
        val buffer = ByteArray(8192)
        inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_MEDIA_BYTES) {
                        throw IllegalStateException("Fichier trop volumineux (max 15 Mo).")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return total
    }
}

class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    var isRecording = false
        private set

    fun startRecording(): Boolean {
        try {
            val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            isRecording = false
            return false
        }
    }

    fun sampleNormalizedAmplitude(): Int {
        return try {
            val raw = mediaRecorder?.maxAmplitude ?: 0
            ((raw / 32767f) * 100f).toInt().coerceIn(8, 100)
        } catch (_: Exception) {
            24
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            currentOutputFile?.absolutePath
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording", e)
            mediaRecorder = null
            isRecording = false
            null
        }
    }
}

class AudioPlayerHelper {
    private var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
        private set
    var currentPlayingPath: String? = null
        private set
    var playbackSpeed: Float = 1.0f
        private set

    fun isAllowedAudioSource(source: String): Boolean {
        val clean = source.trim()
        if (clean.isBlank() || clean.length > 900 || clean.contains("..")) return false
        val lower = clean.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") ||
            lower.startsWith("data:") || lower.startsWith("content://")
        ) {
            return false
        }
        return lower.startsWith("https://") || File(clean).isAbsolute
    }

    fun play(path: String, speed: Float = playbackSpeed, onCompletion: () -> Unit) {
        stop()
        if (!isAllowedAudioSource(path)) {
            onCompletion()
            return
        }
        playbackSpeed = speed.coerceIn(0.75f, 2.0f)
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path.trim())
                prepare()
                applySpeed(this, playbackSpeed)
                start()
                setOnCompletionListener {
                    this@AudioPlayerHelper.isPlaying = false
                    this@AudioPlayerHelper.currentPlayingPath = null
                    onCompletion()
                }
            }
            isPlaying = true
            currentPlayingPath = path.trim()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio", e)
            isPlaying = false
            currentPlayingPath = null
            onCompletion()
        }
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.75f, 2.0f)
        mediaPlayer?.let { player ->
            applySpeed(player, playbackSpeed)
        }
    }

    fun cycleSpeed(): Float {
        val next = when {
            playbackSpeed < 1.25f -> 1.5f
            playbackSpeed < 1.85f -> 2.0f
            else -> 1.0f
        }
        setSpeed(next)
        return next
    }

    fun currentPositionMs(): Int = runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)

    fun durationMs(): Int = runCatching { mediaPlayer?.duration ?: 0 }.getOrDefault(0)

    private fun applySpeed(player: MediaPlayer, speed: Float) {
        try {
            val params = player.playbackParams
            params.speed = speed
            player.playbackParams = params
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            isPlaying = false
            currentPlayingPath = null
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping audio", e)
        }
    }
}
