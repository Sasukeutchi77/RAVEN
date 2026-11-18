package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class VoiceNotificationAction {
    TOGGLE_MUTE,
    LEAVE_VOICE
}

object VoiceServiceCallbacks {
    private val _actions = MutableSharedFlow<VoiceNotificationAction>(extraBufferCapacity = 8)
    val actions: SharedFlow<VoiceNotificationAction> = _actions.asSharedFlow()

    fun emit(action: VoiceNotificationAction) {
        _actions.tryEmit(action)
    }
}

/**
 * Foreground Service keeping active WebRTC voice audio (microphone FGS) and MediaProjection
 * screen sharing alive cleanly when RAVEN moves to background, with quick notification controls.
 */
class VoiceCallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                VoiceServiceCallbacks.emit(VoiceNotificationAction.TOGGLE_MUTE)
                return START_STICKY
            }
            ACTION_LEAVE_VOICE -> {
                VoiceServiceCallbacks.emit(VoiceNotificationAction.LEAVE_VOICE)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty().ifBlank { "Salon vocal" }
                val isMuted = intent?.getBooleanExtra(EXTRA_IS_MUTED, false) ?: false
                val isScreenSharing = intent?.getBooleanExtra(EXTRA_IS_SCREEN_SHARING, false) ?: false
                promoteToForeground(channelName, isMuted, isScreenSharing)
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun promoteToForeground(
        channelName: String,
        isMuted: Boolean,
        isScreenSharing: Boolean
    ) {
        ensureVoiceChannel(this)
        val notification = buildNotification(channelName, isMuted, isScreenSharing)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var mask = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (isScreenSharing) {
                mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            mask
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } catch (_: Throwable) {
            // Fallback without strict type flag on test / restricted environments
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Throwable) {
            }
        }
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
    }

    private fun buildNotification(
        channelName: String,
        isMuted: Boolean,
        isScreenSharing: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, VoiceCallForegroundService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePending = PendingIntent.getService(
            this,
            102,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val leaveIntent = Intent(this, VoiceCallForegroundService::class.java).apply {
            action = ACTION_LEAVE_VOICE
        }
        val leavePending = PendingIntent.getService(
            this,
            103,
            leaveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusSummary = buildString {
            append(if (isScreenSharing) "Partage d'écran en cours" else "Audio WebRTC actif")
            if (isMuted) append(" • Micro coupé")
        }

        return NotificationCompat.Builder(this, VOICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Salon vocal • $channelName")
            .setContentText(statusSummary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, if (isMuted) "Activer micro" else "Couper micro", mutePending)
            .addAction(0, "Quitter", leavePending)
            .build()
    }

    companion object {
        const val VOICE_CHANNEL_ID = "raven_voice_call_channel"
        private const val VOICE_CHANNEL_NAME = "Salons vocaux actifs"
        private const val NOTIFICATION_ID = 4042

        const val ACTION_START_OR_UPDATE = "com.example.action.VOICE_START_OR_UPDATE"
        const val ACTION_TOGGLE_MUTE = "com.example.action.VOICE_TOGGLE_MUTE"
        const val ACTION_LEAVE_VOICE = "com.example.action.VOICE_LEAVE"
        const val ACTION_STOP = "com.example.action.VOICE_STOP"

        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_IS_MUTED = "extra_is_muted"
        const val EXTRA_IS_SCREEN_SHARING = "extra_is_screen_sharing"

        fun ensureVoiceChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                    if (nm.getNotificationChannel(VOICE_CHANNEL_ID) == null) {
                        val channel = NotificationChannel(
                            VOICE_CHANNEL_ID,
                            VOICE_CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = "Maintient les appels vocaux WebRTC et le partage d'écran actifs"
                            setShowBadge(false)
                        }
                        nm.createNotificationChannel(channel)
                    }
                } catch (_: Throwable) {
                }
            }
        }

        fun startOrUpdate(
            context: Context,
            channelName: String,
            isMuted: Boolean,
            isScreenSharing: Boolean
        ) {
            ensureVoiceChannel(context)
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_IS_MUTED, isMuted)
                putExtra(EXTRA_IS_SCREEN_SHARING, isScreenSharing)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (_: Throwable) {
            }
        }
    }
}
