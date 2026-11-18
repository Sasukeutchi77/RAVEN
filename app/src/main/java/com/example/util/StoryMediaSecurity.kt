package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.StoryMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class StoryMediaValidationResult {
    data class Valid(
        val mediaUrl: String,
        val mimeType: String,
        val sizeBytes: Long,
        val isRemoteHttps: Boolean
    ) : StoryMediaValidationResult()

    data class Invalid(val reason: String) : StoryMediaValidationResult()
}

object StoryMediaSecurity {
    const val STORY_MEDIA_DIR = "raven_story_media"
    const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L // 15 Mo
    const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L // 50 Mo
    const val MAX_ORPHAN_MEDIA_AGE_MS = 72L * 3600_000L

    val allowedImageMimes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/gif"
    )

    val allowedVideoMimes = setOf(
        "video/mp4",
        "video/webm",
        "video/3gpp",
        "video/quicktime"
    )

    private val safeLocalFileRegex = Regex("^[a-zA-Z0-9_\\-]+\\.(jpg|jpeg|png|webp|gif|mp4|webm|mov|3gp)$")

    fun storyMediaDir(context: Context): File =
        File(context.cacheDir, STORY_MEDIA_DIR).apply { mkdirs() }

    fun extensionForMime(mimeType: String, expectedType: StoryMediaType): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        else -> if (expectedType == StoryMediaType.VIDEO) "mp4" else "jpg"
    }

    fun isValidStoryMediaReference(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (trimmed.length > 900) return false
        if (trimmed.contains("..")) return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return false
        }
        if (lower.startsWith("https://")) {
            return true
        }
        // App-private validated story media cache path
        if (trimmed.contains("/$STORY_MEDIA_DIR/")) {
            val fileName = trimmed.substringAfterLast('/')
            return safeLocalFileRegex.matches(fileName)
        }
        return false
    }

    fun validateMagicBytes(header: ByteArray, bytesRead: Int, expectedType: StoryMediaType): Boolean {
        if (bytesRead < 4) return false
        return when (expectedType) {
            StoryMediaType.TEXT -> true
            StoryMediaType.IMAGE -> {
                val isJpeg = (header[0].toInt() and 0xFF) == 0xFF &&
                    (header[1].toInt() and 0xFF) == 0xD8 &&
                    (header[2].toInt() and 0xFF) == 0xFF
                val isPng = (header[0].toInt() and 0xFF) == 0x89 &&
                    header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() &&
                    header[3] == 0x47.toByte()
                val isGif = bytesRead >= 6 &&
                    header[0] == 'G'.code.toByte() &&
                    header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte()
                val isWebp = bytesRead >= 12 &&
                    header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                    header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
                isJpeg || isPng || isGif || isWebp
            }
            StoryMediaType.VIDEO -> {
                val isMp4OrMov = bytesRead >= 8 &&
                    header[4] == 'f'.code.toByte() &&
                    header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() &&
                    header[7] == 'p'.code.toByte()
                val isWebm = (header[0].toInt() and 0xFF) == 0x1A &&
                    (header[1].toInt() and 0xFF) == 0x45 &&
                    (header[2].toInt() and 0xFF) == 0xDF &&
                    (header[3].toInt() and 0xFF) == 0xA3
                isMp4OrMov || isWebm
            }
        }
    }

    fun validateRemoteMediaUrl(url: String?, expectedType: StoryMediaType): StoryMediaValidationResult {
        if (url.isNullOrBlank()) {
            return StoryMediaValidationResult.Invalid("URL média manquante.")
        }
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://") || trimmed.length > 900 || trimmed.contains("..")) {
            return StoryMediaValidationResult.Invalid("Seules les URL HTTPS sécurisées sont acceptées.")
        }
        val mime = if (expectedType == StoryMediaType.VIDEO) "video/mp4" else "image/jpeg"
        return StoryMediaValidationResult.Valid(
            mediaUrl = trimmed,
            mimeType = mime,
            sizeBytes = 0L,
            isRemoteHttps = true
        )
    }

    suspend fun secureCopyAndUploadStoryMedia(
        context: Context,
        uri: Uri,
        expectedType: StoryMediaType
    ): StoryMediaValidationResult = withContext(Dispatchers.IO) {
        if (expectedType == StoryMediaType.TEXT) {
            return@withContext StoryMediaValidationResult.Invalid("Le type TEXT n'accepte pas de fichier média.")
        }
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "https") {
            return@withContext validateRemoteMediaUrl(uri.toString(), expectedType)
        }
        if (scheme != "content" && scheme != "file") {
            return@withContext StoryMediaValidationResult.Invalid("Source média non autorisée.")
        }

        val mimeType = context.contentResolver.getType(uri)?.lowercase()
            ?: if (expectedType == StoryMediaType.VIDEO) "video/mp4" else "image/jpeg"

        val maxAllowedBytes = if (expectedType == StoryMediaType.VIDEO) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES
        val allowedMimes = if (expectedType == StoryMediaType.VIDEO) allowedVideoMimes else allowedImageMimes
        if (mimeType !in allowedMimes) {
            return@withContext StoryMediaValidationResult.Invalid("Format média non pris en charge ($mimeType).")
        }

        // Check declared OpenableColumns.SIZE up front if available
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        val declaredSize = cursor.getLong(idx)
                        if (declaredSize > maxAllowedBytes) {
                            return@withContext StoryMediaValidationResult.Invalid(
                                "Fichier trop volumineux (max ${maxAllowedBytes / (1024 * 1024)} Mo)."
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val ext = extensionForMime(mimeType, expectedType)
        val safeFileName = "story_${UUID.randomUUID().toString().replace("-", "").take(16)}.$ext"
        val destFile = File(storyMediaDir(context), safeFileName)

        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext StoryMediaValidationResult.Invalid("Impossible de lire le fichier sélectionné.")

            var totalBytes = 0L
            val headerBuffer = ByteArray(16)
            var headerBytesRead = 0
            val copyBuffer = ByteArray(8192)

            stream.use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        val read = input.read(copyBuffer)
                        if (read <= 0) break
                        if (headerBytesRead < headerBuffer.size) {
                            val toCopy = minOf(read, headerBuffer.size - headerBytesRead)
                            System.arraycopy(copyBuffer, 0, headerBuffer, headerBytesRead, toCopy)
                            headerBytesRead += toCopy
                        }
                        totalBytes += read
                        if (totalBytes > maxAllowedBytes) {
                            destFile.delete()
                            return@withContext StoryMediaValidationResult.Invalid(
                                "Fichier trop volumineux (max ${maxAllowedBytes / (1024 * 1024)} Mo)."
                            )
                        }
                        output.write(copyBuffer, 0, read)
                    }
                }
            }

            if (totalBytes <= 0L) {
                destFile.delete()
                return@withContext StoryMediaValidationResult.Invalid("Le fichier sélectionné est vide.")
            }

            if (!validateMagicBytes(headerBuffer, headerBytesRead, expectedType)) {
                destFile.delete()
                return@withContext StoryMediaValidationResult.Invalid(
                    "Signature du fichier invalide : contenu potentiellement dangereux ou corrompu."
                )
            }

            // Upload via existing RAVEN Cloudinary infrastructure if configured
            val resolvedUrl = if (CloudinaryHelper.isConfigured()) {
                val uploaded = if (expectedType == StoryMediaType.VIDEO) {
                    CloudinaryHelper.uploadFile(context, Uri.fromFile(destFile), mimeType)
                } else {
                    CloudinaryHelper.uploadImage(context, Uri.fromFile(destFile))
                }
                if (uploaded.startsWith("https://")) {
                    // Clean up local copy once stored remotely over HTTPS
                    destFile.delete()
                    uploaded
                } else {
                    destFile.absolutePath
                }
            } else {
                destFile.absolutePath
            }

            StoryMediaValidationResult.Valid(
                mediaUrl = resolvedUrl,
                mimeType = mimeType,
                sizeBytes = totalBytes,
                isRemoteHttps = resolvedUrl.startsWith("https://")
            )
        } catch (e: Exception) {
            destFile.delete()
            StoryMediaValidationResult.Invalid(e.message ?: "Erreur lors de l'analyse du média.")
        }
    }

    fun deleteLocalMediaIfOwned(mediaUrl: String?) {
        if (mediaUrl.isNullOrBlank()) return
        if (!mediaUrl.contains("/$STORY_MEDIA_DIR/")) return
        val fileName = mediaUrl.substringAfterLast('/')
        if (!safeLocalFileRegex.matches(fileName)) return
        try {
            val file = File(mediaUrl)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun cleanupExpiredLocalMedia(
        mediaDir: File,
        activeMediaPaths: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
        maxOrphanAgeMs: Long = MAX_ORPHAN_MEDIA_AGE_MS
    ): Int {
        if (!mediaDir.exists() || !mediaDir.isDirectory) return 0
        var removed = 0
        val files = mediaDir.listFiles() ?: return 0
        files.forEach { file ->
            if (file.isFile) {
                val path = file.absolutePath
                val isReferenced = path in activeMediaPaths
                val isExpiredOrphan = (nowMs - file.lastModified()) > maxOrphanAgeMs
                if (!isReferenced || isExpiredOrphan) {
                    if (file.delete()) removed++
                }
            }
        }
        return removed
    }
}
