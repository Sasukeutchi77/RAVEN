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

object CloudinaryHelper {
    private const val PREFS_NAME = "raven_cloudinary_prefs"
    private const val KEY_CLOUD_NAME = "cloud_name"
    private const val KEY_UPLOAD_PRESET = "upload_preset"

    // Cloudinary credentials can be set by the user or dynamically configured
    var cloudName: String = "demo"
    var uploadPreset: String = "unsigned_preset"

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cloudName = prefs.getString(KEY_CLOUD_NAME, "demo") ?: "demo"
            uploadPreset = prefs.getString(KEY_UPLOAD_PRESET, "unsigned_preset") ?: "unsigned_preset"
        } catch (_: Exception) {
        }
    }

    fun saveConfig(context: Context, name: String, preset: String) {
        cloudName = name.trim().ifBlank { "demo" }
        uploadPreset = preset.trim().ifBlank { "unsigned_preset" }
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CLOUD_NAME, cloudName)
                .putString(KEY_UPLOAD_PRESET, uploadPreset)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun isConfigured(): Boolean = cloudName != "demo" && uploadPreset != "unsigned_preset" && cloudName.isNotBlank() && uploadPreset.isNotBlank()

    suspend fun uploadImage(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext imageUri.toString()
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            if (!isConfigured()) {
                return@withContext tempFile.absolutePath
            }

            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", tempFile.name, tempFile.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                json.optString("secure_url", tempFile.absolutePath)
            } else {
                Log.w("Cloudinary", "Upload failed: ${response.code}, falling back to local file")
                tempFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error uploading to Cloudinary", e)
            imageUri.toString()
        }
    }

    suspend fun uploadFile(context: Context, fileUri: Uri, mimeType: String = "application/octet-stream"): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@withContext fileUri.toString()
            val tempFile = File(context.cacheDir, "file_${System.currentTimeMillis()}")
            FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
            inputStream.close()

            if (!isConfigured()) {
                return@withContext tempFile.absolutePath
            }

            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", tempFile.name, tempFile.asRequestBody(mimeType.toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/auto/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                json.optString("secure_url", tempFile.absolutePath)
            } else {
                tempFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error uploading file", e)
            fileUri.toString()
        }
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

    fun play(path: String, onCompletion: () -> Unit) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    this@AudioPlayerHelper.isPlaying = false
                    this@AudioPlayerHelper.currentPlayingPath = null
                    onCompletion()
                }
            }
            isPlaying = true
            currentPlayingPath = path
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio", e)
            isPlaying = false
            currentPlayingPath = null
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
