package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
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
    private const val KEY_UPLOAD_FOLDER = "upload_folder"
    private const val KEY_MANUAL_OVERRIDE = "manual_override"
    private const val DEFAULT_CLOUD_NAME = "demo"
    private const val DEFAULT_UPLOAD_PRESET = "unsigned_preset"
    const val DEFAULT_UPLOAD_FOLDER = "raven_media"
    private const val MAX_MEDIA_BYTES = 15L * 1024L * 1024L // 15 MB hard cap
    private val CONFIG_IDENTIFIER_REGEX = Regex("^[a-zA-Z0-9_-]{2,64}$")
    private val SAFE_SUBDIR_REGEX = Regex("^[a-zA-Z0-9_-]{1,32}$")
    private val SAFE_FOLDER_REGEX = Regex("^[a-zA-Z0-9_/-]{1,64}$")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    // Active Cloudinary runtime parameters (Secrets/BuildConfig by default or manual override)
    var cloudName: String = DEFAULT_CLOUD_NAME
    var uploadPreset: String = DEFAULT_UPLOAD_PRESET
    var uploadFolder: String = DEFAULT_UPLOAD_FOLDER

    fun isValidConfigToken(value: String): Boolean {
        val clean = value.trim()
        return CONFIG_IDENTIFIER_REGEX.matches(clean) && !FirebaseConfigManager.isPlaceholderOrBlank(clean)
    }

    fun isValidFolderToken(value: String): Boolean {
        val clean = value.trim()
        if (clean.isEmpty()) return true
        return SAFE_FOLDER_REGEX.matches(clean) &&
            !clean.contains("..") &&
            !clean.startsWith("/") &&
            !clean.endsWith("/") &&
            !FirebaseConfigManager.isPlaceholderOrBlank(clean)
    }

    internal fun resolveBuildConfigCloudName(): String? =
        BuildConfig.CLOUDINARY_CLOUD_NAME.trim().takeIf { isValidConfigToken(it) }

    internal fun resolveBuildConfigPreset(): String? =
        BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim().takeIf { isValidConfigToken(it) }

    internal fun resolveBuildConfigFolder(): String =
        BuildConfig.CLOUDINARY_UPLOAD_FOLDER.trim()
            .takeIf { it.isNotBlank() && isValidFolderToken(it) }
            ?: DEFAULT_UPLOAD_FOLDER

    fun isBuildConfigConfigured(): Boolean =
        resolveBuildConfigCloudName() != null && resolveBuildConfigPreset() != null

    fun isManualOverride(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MANUAL_OVERRIDE, false)
        } catch (_: Exception) {
            false
        }
    }

    fun configSourceLabel(context: Context): String {
        return when {
            !isConfigured() -> "Mode Local (non configuré)"
            isManualOverride(context) -> "Configuration manuelle"
            isBuildConfigConfigured() -> "Secrets (.env / BuildConfig)"
            else -> "Configuration active"
        }
    }

    fun init(context: Context) {
        val envName = resolveBuildConfigCloudName()
        val envPreset = resolveBuildConfigPreset()
        val envFolder = resolveBuildConfigFolder()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val manualOverride = prefs.getBoolean(KEY_MANUAL_OVERRIDE, false)
            if (manualOverride) {
                val savedName = prefs.getString(KEY_CLOUD_NAME, DEFAULT_CLOUD_NAME) ?: DEFAULT_CLOUD_NAME
                val savedPreset = prefs.getString(KEY_UPLOAD_PRESET, DEFAULT_UPLOAD_PRESET) ?: DEFAULT_UPLOAD_PRESET
                val savedFolder = prefs.getString(KEY_UPLOAD_FOLDER, envFolder) ?: envFolder
                cloudName = if (isValidConfigToken(savedName)) savedName.trim() else (envName ?: DEFAULT_CLOUD_NAME)
                uploadPreset = if (isValidConfigToken(savedPreset)) savedPreset.trim() else (envPreset ?: DEFAULT_UPLOAD_PRESET)
                uploadFolder = if (isValidFolderToken(savedFolder)) savedFolder.trim().ifBlank { envFolder } else envFolder
            } else {
                cloudName = envName ?: DEFAULT_CLOUD_NAME
                uploadPreset = envPreset ?: DEFAULT_UPLOAD_PRESET
                uploadFolder = envFolder
            }
        } catch (_: Exception) {
            cloudName = envName ?: DEFAULT_CLOUD_NAME
            uploadPreset = envPreset ?: DEFAULT_UPLOAD_PRESET
            uploadFolder = envFolder
        }
    }

    fun saveConfig(
        context: Context,
        name: String,
        preset: String,
        folder: String = uploadFolder
    ): Boolean {
        val cleanName = name.trim()
        val cleanPreset = preset.trim()
        val cleanFolder = folder.trim()
        if (cleanName.isNotEmpty() && !isValidConfigToken(cleanName)) return false
        if (cleanPreset.isNotEmpty() && !isValidConfigToken(cleanPreset)) return false
        if (!isValidFolderToken(cleanFolder)) return false

        val hasCustomValues = cleanName.isNotBlank() && cleanPreset.isNotBlank()
        val envName = resolveBuildConfigCloudName()
        val envPreset = resolveBuildConfigPreset()
        val envFolder = resolveBuildConfigFolder()

        cloudName = cleanName.ifBlank { envName ?: DEFAULT_CLOUD_NAME }
        uploadPreset = cleanPreset.ifBlank { envPreset ?: DEFAULT_UPLOAD_PRESET }
        uploadFolder = cleanFolder.ifBlank { envFolder }

        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CLOUD_NAME, cloudName)
                .putString(KEY_UPLOAD_PRESET, uploadPreset)
                .putString(KEY_UPLOAD_FOLDER, uploadFolder)
                .putBoolean(KEY_MANUAL_OVERRIDE, hasCustomValues)
                .apply()
        } catch (_: Exception) {
        }
        return true
    }

    fun resetToBuildConfig(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CLOUD_NAME)
                .remove(KEY_UPLOAD_PRESET)
                .remove(KEY_UPLOAD_FOLDER)
                .putBoolean(KEY_MANUAL_OVERRIDE, false)
                .apply()
        } catch (_: Exception) {
        }
        init(context)
    }

    fun isConfigured(): Boolean =
        cloudName != DEFAULT_CLOUD_NAME &&
            uploadPreset != DEFAULT_UPLOAD_PRESET &&
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

            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
            if (uploadFolder.isNotBlank() && isValidFolderToken(uploadFolder)) {
                bodyBuilder.addFormDataPart("folder", uploadFolder)
            }

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(bodyBuilder.build())
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

    suspend fun uploadAudioFile(localAudioPath: String): String = withContext(Dispatchers.IO) {
        val cleanPath = localAudioPath.trim()
        if (cleanPath.isBlank()) return@withContext ""
        if (cleanPath.startsWith("https://")) return@withContext cleanPath
        if (!isConfigured()) return@withContext cleanPath
        val audioFile = File(cleanPath)
        if (!audioFile.exists() || audioFile.length() <= 0L || audioFile.length() > MAX_MEDIA_BYTES) {
            return@withContext cleanPath
        }
        try {
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
            if (uploadFolder.isNotBlank() && isValidFolderToken(uploadFolder)) {
                bodyBuilder.addFormDataPart("folder", "$uploadFolder/voice")
            }

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/auto/upload")
                .post(bodyBuilder.build())
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    val secureUrl = json.optString("secure_url", "").trim()
                    if (secureUrl.startsWith("https://")) secureUrl else cleanPath
                } else {
                    Log.w("Cloudinary", "Audio upload failed: ${response.code}, keeping local audio file")
                    cleanPath
                }
            }
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error uploading audio to Cloudinary", e)
            cleanPath
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
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(sanitizedMime.toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
            if (uploadFolder.isNotBlank() && isValidFolderToken(uploadFolder)) {
                bodyBuilder.addFormDataPart("folder", uploadFolder)
            }

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/auto/upload")
                .post(bodyBuilder.build())
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
