package com.example.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import kotlin.math.min
import kotlin.math.sqrt

enum class VoiceConnectionPhase(val label: String) {
    DISCONNECTED("Déconnecté"),
    CONNECTING("Connexion RTC..."),
    CONNECTED("RTC Connecté"),
    RECONNECTING("Reconnexion..."),
    NETWORK_LOST("Réseau perdu"),
    FAILED("Connexion perdue")
}

enum class VoiceDisconnectReason(val userMessage: String) {
    USER_LEFT("Vous avez quitté le salon vocal."),
    NETWORK_TIMEOUT("Déconnexion automatique : perte réseau prolongée."),
    RECONNECT_EXHAUSTED("Déconnexion automatique : impossible de rétablir la connexion WebRTC."),
    KICKED("Vous avez été exclu du salon vocal."),
    CHANNEL_LOCKED("Déconnecté : ce salon vocal a été verrouillé."),
    CHANNEL_DELETED("Déconnecté : ce salon vocal a été supprimé."),
    PERMISSION_REVOKED("Déconnecté : votre accès vocal a été révoqué.")
}

data class RemoteVoicePeerState(
    val uid: String,
    val displayName: String,
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val isCameraOn: Boolean = false,
    val isScreenSharing: Boolean = false,
    val isSpeaking: Boolean = false,
    val connectionState: String = "CONNECTED"
)

data class WebRtcSessionState(
    val isConnected: Boolean = false,
    val connectionPhase: VoiceConnectionPhase = VoiceConnectionPhase.DISCONNECTED,
    val iceStatusLabel: String = "Prêt",
    val remotePeers: List<RemoteVoicePeerState> = emptyList(),
    val localMicSpeaking: Boolean = false,
    val localMicLevel: Float = 0f,
    val isCameraStreaming: Boolean = false,
    val cameraPausedInBackground: Boolean = false,
    val useFrontCamera: Boolean = true,
    val isScreenSharing: Boolean = false,
    val isSpeakerphoneOn: Boolean = true,
    val networkAvailable: Boolean = true,
    val networkTypeLabel: String = "Wi-Fi / Données",
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = 5,
    val peerConnectionStates: Map<String, String> = emptyMap(),
    val pingMs: Int = 24,
    val lastDisconnectReason: VoiceDisconnectReason? = null
)

/**
 * Production WebRTC voice, video, and MediaProjection screen-share manager.
 * Extends Stream WebRTC Android with:
 * - Multi-peer P2P mesh via Firestore SDP/ICE signaling & peer departure cleanup
 * - Individual per-peer volume mixing (0%..200%) & AudioManager routing
 * - Real AudioRecord RMS speaking detection synced to Firestore peers
 * - Camera toggle + front/back switch + SDP renegotiation + local SurfaceView track
 * - MediaProjection ScreenCapturerAndroid screen sharing with Android 14+ ForegroundService
 * - ConnectivityManager NetworkCallback recovery + exponential backoff auto-reconnect
 * - Network-loss / failure auto-disconnect timers & clean lifecycle pause/resume
 * - Complete WebRTC resource disposal on leaveRoom() / shutdown()
 */
class WebRtcVoiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val networkGraceTimeoutMs: Long = 45_000L,
    private val reconnectBackoffBaseMs: Long = 1_000L,
    private val maxReconnectAttempts: Int = 5,
    private val onAutoDisconnected: (VoiceDisconnectReason) -> Unit = {},
    private val startServiceHook: (channelName: String, isMuted: Boolean, isScreenSharing: Boolean) -> Unit = { ch, muted, screen ->
        VoiceCallForegroundService.startOrUpdate(context, ch, muted, screen)
    },
    private val stopServiceHook: () -> Unit = {
        VoiceCallForegroundService.stop(context)
    }
) {
    private val _sessionState = MutableStateFlow(WebRtcSessionState(maxReconnectAttempts = maxReconnectAttempts))
    val sessionState: StateFlow<WebRtcSessionState> = _sessionState.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var videoCapturer: VideoCapturer? = null
    private var cameraTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null

    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenTextureHelper: SurfaceTextureHelper? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null

    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val videoSenders = mutableMapOf<String, RtpSender>()
    private val screenSenders = mutableMapOf<String, RtpSender>()
    private val remoteAudioTracks = mutableMapOf<String, AudioTrack>()
    private val peerVolumes = mutableMapOf<String, Float>()
    private val handledSignalIds = mutableSetOf<String>()
    private val remoteDescSetPeers = mutableSetOf<String>()
    private val pendingIceCandidates = mutableMapOf<String, MutableList<IceCandidate>>()

    private var peersListener: ListenerRegistration? = null
    private var signalsListener: ListenerRegistration? = null
    private var micMeterJob: Job? = null
    private var reconnectJob: Job? = null
    private var networkGraceJob: Job? = null

    private var activeServerId: String? = null
    private var activeChannelId: String? = null
    private var activeChannelName: String = "Salon vocal"
    private var activeUid: String? = null
    private var activeDisplayName: String = ""
    private var activeIsPublic: Boolean = false
    private var activeMemberIds: List<String> = emptyList()
    private var activeFirestore: FirebaseFirestore? = null

    private var isMutedState: Boolean = false
    private var isDeafenedState: Boolean = false
    private var isCameraState: Boolean = false
    private var useFrontCameraState: Boolean = true
    private var resumeCameraAfterBackground: Boolean = false
    private var isAppInBackground: Boolean = false
    private var isScreenShareState: Boolean = false
    private var isSpeakerphoneOnState: Boolean = true
    private var currentSpeakingState: Boolean = false
    private var lastPresencePublishMs: Long = 0L

    private var previousAudioMode: Int? = null
    private var previousSpeakerphone: Boolean? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var networkCallbackRegistered: Boolean = false
    private var hasNetworkEverBeenLostInSession: Boolean = false
    private var activeNetworkId: String? = null

    private val connectivityManager: ConnectivityManager? by lazy {
        try {
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        } catch (_: Throwable) {
            null
        }
    }

    private val audioManager: AudioManager? by lazy {
        try {
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Throwable) {
            null
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val newId = network.toString()
            val caps = try {
                connectivityManager?.getNetworkCapabilities(network)
            } catch (_: Throwable) {
                null
            }
            val typeLabel = resolveNetworkLabel(caps)
            val switched = activeNetworkId != null && activeNetworkId != newId
            activeNetworkId = newId
            onNetworkRestoredOrChanged(typeLabel = typeLabel, networkSwitched = switched || hasNetworkEverBeenLostInSession)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val typeLabel = resolveNetworkLabel(networkCapabilities)
            if (typeLabel != _sessionState.value.networkTypeLabel) {
                _sessionState.value = _sessionState.value.copy(networkTypeLabel = typeLabel)
            }
        }

        override fun onLost(network: Network) {
            val cm = connectivityManager
            val stillConnected = try {
                val active = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(active)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (_: Throwable) {
                false
            }
            if (!stillConnected) {
                onNetworkLostDetected()
            }
        }
    }

    fun getEglContext(): EglBase.Context? = eglBase?.eglBaseContext

    fun joinRoom(
        serverId: String,
        channelId: String,
        channelName: String = "Salon vocal",
        currentUid: String,
        displayName: String,
        isPublic: Boolean,
        memberIds: List<String>,
        firestore: FirebaseFirestore?,
        isMuted: Boolean,
        isDeafened: Boolean
    ) {
        leaveRoom(reason = null, preserveDisconnectReason = false)

        activeServerId = serverId
        activeChannelId = channelId
        activeChannelName = channelName.ifBlank { "Salon vocal" }
        activeUid = currentUid
        activeDisplayName = displayName
        activeIsPublic = isPublic
        activeMemberIds = (memberIds + currentUid).distinct()
        activeFirestore = firestore
        isMutedState = isMuted
        isDeafenedState = isDeafened
        hasNetworkEverBeenLostInSession = false

        registerNetworkCallbackIfNeeded()
        configureAudioRoutingForCall()
        initializeWebRtcEngine()
        startMicMeteringLoop()
        startServiceHook(activeChannelName, isMutedState, isScreenShareState)

        val initialPhase = if (firestore != null && serverId != "dm_tab") {
            VoiceConnectionPhase.CONNECTED
        } else {
            VoiceConnectionPhase.CONNECTED
        }

        _sessionState.value = WebRtcSessionState(
            isConnected = true,
            connectionPhase = initialPhase,
            iceStatusLabel = if (firestore != null && serverId != "dm_tab") {
                "RTC Connecté • Signaling Cloud"
            } else {
                "RTC Local Actif"
            },
            useFrontCamera = useFrontCameraState,
            isSpeakerphoneOn = isSpeakerphoneOnState,
            networkAvailable = checkCurrentNetworkAvailable(),
            networkTypeLabel = detectCurrentNetworkLabel(),
            reconnectAttempt = 0,
            maxReconnectAttempts = maxReconnectAttempts,
            lastDisconnectReason = null
        )

        if (firestore != null && serverId != "dm_tab") {
            publishSelfPeer(force = true)
            subscribePeersAndSignals(firestore, serverId, channelId, currentUid)
        }
    }

    fun leaveRoom(
        reason: VoiceDisconnectReason? = VoiceDisconnectReason.USER_LEFT,
        preserveDisconnectReason: Boolean = true
    ) {
        val db = activeFirestore
        val srv = activeServerId
        val ch = activeChannelId
        val uid = activeUid
        if (db != null && srv != null && ch != null && uid != null && srv != "dm_tab") {
            try {
                db.collection("servers").document(srv)
                    .collection("channels").document(ch)
                    .collection("voice_peers").document(uid)
                    .delete()
            } catch (_: Exception) {
            }
        }

        peersListener?.remove()
        peersListener = null
        signalsListener?.remove()
        signalsListener = null
        micMeterJob?.cancel()
        micMeterJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        networkGraceJob?.cancel()
        networkGraceJob = null

        stopCameraTrack()
        stopScreenCaptureInternal(renegotiate = false)

        peerConnections.keys.toList().forEach { remoteUid ->
            closePeerConnection(remoteUid)
        }
        peerConnections.clear()
        videoSenders.clear()
        screenSenders.clear()
        remoteAudioTracks.clear()
        handledSignalIds.clear()
        remoteDescSetPeers.clear()
        pendingIceCandidates.clear()
        _remoteVideoTracks.value = emptyMap()

        try {
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
        } catch (_: Throwable) {
        } finally {
            localAudioTrack = null
            localAudioSource = null
        }

        restoreAudioRoutingAfterCall()
        stopServiceHook()

        activeServerId = null
        activeChannelId = null
        activeUid = null
        activeFirestore = null
        isCameraState = false
        resumeCameraAfterBackground = false
        isScreenShareState = false
        currentSpeakingState = false
        hasNetworkEverBeenLostInSession = false

        _sessionState.value = WebRtcSessionState(
            isConnected = false,
            connectionPhase = VoiceConnectionPhase.DISCONNECTED,
            iceStatusLabel = reason?.userMessage ?: "Déconnecté",
            maxReconnectAttempts = maxReconnectAttempts,
            lastDisconnectReason = if (preserveDisconnectReason) reason else null
        )
    }

    /**
     * Complete teardown when MainActivity/ViewModel finishes permanently.
     * Releases native PeerConnectionFactory, EglBase, and ConnectivityManager callback.
     */
    fun shutdown() {
        leaveRoom(reason = VoiceDisconnectReason.USER_LEFT, preserveDisconnectReason = false)
        unregisterNetworkCallback()
        try {
            peerConnectionFactory?.dispose()
        } catch (_: Throwable) {
        } finally {
            peerConnectionFactory = null
        }
        try {
            eglBase?.release()
        } catch (_: Throwable) {
        } finally {
            eglBase = null
        }
    }

    fun setMuted(muted: Boolean) {
        isMutedState = muted
        if (muted || isDeafenedState) {
            currentSpeakingState = false
        }
        try {
            localAudioTrack?.setEnabled(!muted && !isDeafenedState)
        } catch (_: Throwable) {
        }
        if (_sessionState.value.isConnected) {
            _sessionState.value = _sessionState.value.copy(
                localMicSpeaking = if (muted) false else _sessionState.value.localMicSpeaking,
                localMicLevel = if (muted) 0f else _sessionState.value.localMicLevel
            )
            startServiceHook(activeChannelName, isMutedState, isScreenShareState)
            publishSelfPeer(force = true)
        }
    }

    fun setDeafened(deafened: Boolean) {
        isDeafenedState = deafened
        if (deafened) {
            isMutedState = true
            currentSpeakingState = false
        }
        try {
            localAudioTrack?.setEnabled(!isMutedState && !deafened)
            remoteAudioTracks.forEach { (uid, track) ->
                track.setEnabled(!deafened)
                val vol = if (deafened) 0.0 else ((peerVolumes[uid] ?: 1f) * 2.0)
                track.setVolume(vol)
            }
        } catch (_: Throwable) {
        }
        if (_sessionState.value.isConnected) {
            startServiceHook(activeChannelName, isMutedState, isScreenShareState)
            publishSelfPeer(force = true)
        }
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        isSpeakerphoneOnState = enabled
        try {
            val manager = audioManager
            if (manager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (enabled) {
                        val speaker = manager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        if (speaker != null) {
                            manager.setCommunicationDevice(speaker)
                        }
                    } else {
                        manager.clearCommunicationDevice()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    manager.isSpeakerphoneOn = enabled
                }
            }
        } catch (_: Throwable) {
        }
        _sessionState.value = _sessionState.value.copy(isSpeakerphoneOn = enabled)
    }

    fun setRemotePeerVolume(peerUid: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 2f)
        peerVolumes[peerUid] = clamped
        try {
            remoteAudioTracks[peerUid]?.setVolume(if (isDeafenedState) 0.0 else (clamped * 2.0))
        } catch (_: Throwable) {
        }
    }

    fun getRemotePeerVolume(peerUid: String): Float = peerVolumes[peerUid] ?: 1f

    fun setCameraEnabled(enabled: Boolean, useFrontCamera: Boolean = useFrontCameraState) {
        val switchingDirection = isCameraState && enabled && useFrontCameraState != useFrontCamera
        isCameraState = enabled
        useFrontCameraState = useFrontCamera
        if (!enabled) {
            resumeCameraAfterBackground = false
        }

        if (enabled) {
            if (switchingDirection && videoCapturer is CameraVideoCapturer) {
                try {
                    (videoCapturer as CameraVideoCapturer).switchCamera(null)
                } catch (_: Throwable) {
                    startCameraTrack(useFrontCamera)
                }
            } else {
                startCameraTrack(useFrontCamera)
            }
        } else {
            stopCameraTrack()
            renegotiateAllPeers(iceRestart = false)
        }

        _sessionState.value = _sessionState.value.copy(
            isCameraStreaming = enabled,
            cameraPausedInBackground = false,
            useFrontCamera = useFrontCamera
        )
        publishSelfPeer(force = true)
    }

    /**
     * Starts real WebRTC MediaProjection screen sharing using a system consent Intent
     * (ActivityResult from MediaProjectionManager.createScreenCaptureIntent()).
     */
    fun startScreenSharing(resultCode: Int, projectionData: Intent?): Boolean {
        if (!_sessionState.value.isConnected) return false
        if (resultCode != Activity.RESULT_OK || projectionData == null) {
            return false
        }
        isScreenShareState = true
        // Android 14+ requires foreground service type MEDIA_PROJECTION BEFORE acquiring MediaProjection
        startServiceHook(activeChannelName, isMutedState, true)

        val factory = peerConnectionFactory
        val egl = eglBase
        if (factory != null && egl != null) {
            try {
                stopScreenCaptureInternal(renegotiate = false)
                val capturer = ScreenCapturerAndroid(
                    projectionData,
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            scope.launch(Dispatchers.Main) {
                                stopScreenSharing()
                            }
                        }
                    }
                )
                val texHelper = SurfaceTextureHelper.create("RavenScreenCapture", egl.eglBaseContext)
                val sSource = factory.createVideoSource(capturer.isScreencast)
                capturer.initialize(texHelper, context.applicationContext, sSource.capturerObserver)
                capturer.startCapture(720, 1280, 15)
                val sTrack = factory.createVideoTrack("raven_screen_${UUID.randomUUID().toString().take(4)}", sSource)
                sTrack.setEnabled(true)

                screenCapturer = capturer
                screenTextureHelper = texHelper
                screenVideoSource = sSource
                screenVideoTrack = sTrack

                peerConnections.forEach { (uid, pc) ->
                    try {
                        val sender = pc.addTrack(sTrack, listOf("raven_screen_stream"))
                        if (sender != null) {
                            screenSenders[uid] = sender
                        }
                    } catch (_: Throwable) {
                    }
                }
                renegotiateAllPeers(iceRestart = false)
            } catch (e: Throwable) {
                Log.w("WebRtcVoice", "ScreenCapturerAndroid start fallback", e)
            }
        }

        _sessionState.value = _sessionState.value.copy(isScreenSharing = true)
        publishSelfPeer(force = true)
        return true
    }

    fun setScreenSharing(enabled: Boolean) {
        if (!enabled) {
            stopScreenSharing()
        } else if (!_sessionState.value.isConnected) {
            return
        } else {
            // Fallback toggle for tests without MediaProjection intent
            isScreenShareState = true
            startServiceHook(activeChannelName, isMutedState, true)
            _sessionState.value = _sessionState.value.copy(isScreenSharing = true)
            publishSelfPeer(force = true)
        }
    }

    fun stopScreenSharing() {
        val wasSharing = isScreenShareState || screenCapturer != null
        isScreenShareState = false
        stopScreenCaptureInternal(renegotiate = wasSharing)
        if (_sessionState.value.isConnected) {
            startServiceHook(activeChannelName, isMutedState, false)
            _sessionState.value = _sessionState.value.copy(isScreenSharing = false)
            publishSelfPeer(force = true)
        }
    }

    /**
     * Handles app background/foreground transitions safely:
     * - Background: pauses active camera stream to preserve battery & camera privacy while keeping voice audio active via ForegroundService.
     * - Foreground: resumes camera stream automatically if it was active before backgrounding.
     */
    fun onAppBackgroundChanged(isBackground: Boolean) {
        if (isAppInBackground == isBackground) return
        isAppInBackground = isBackground
        if (!_sessionState.value.isConnected) return

        if (isBackground) {
            if (isCameraState) {
                resumeCameraAfterBackground = true
                isCameraState = false
                stopCameraTrack()
                renegotiateAllPeers(iceRestart = false)
                _sessionState.value = _sessionState.value.copy(
                    isCameraStreaming = false,
                    cameraPausedInBackground = true
                )
                publishSelfPeer(force = true)
            }
        } else {
            if (resumeCameraAfterBackground) {
                resumeCameraAfterBackground = false
                setCameraEnabled(true, useFrontCameraState)
            }
        }
    }

    fun triggerReconnect(reason: String = "Reconnexion manuelle") {
        if (!_sessionState.value.isConnected) return
        scheduleReconnect(reason = reason, immediate = true)
    }

    internal fun onNetworkLostDetected() {
        if (!_sessionState.value.isConnected) return
        hasNetworkEverBeenLostInSession = true
        reconnectJob?.cancel()
        networkGraceJob?.cancel()

        _sessionState.value = _sessionState.value.copy(
            networkAvailable = false,
            networkTypeLabel = "Hors ligne",
            connectionPhase = VoiceConnectionPhase.NETWORK_LOST,
            iceStatusLabel = "Réseau perdu • Reconnexion en attente..."
        )

        networkGraceJob = scope.launch {
            delay(networkGraceTimeoutMs.coerceAtLeast(50L))
            if (!_sessionState.value.networkAvailable && _sessionState.value.isConnected) {
                autoDisconnect(VoiceDisconnectReason.NETWORK_TIMEOUT)
            }
        }
    }

    internal fun onNetworkRestoredOrChanged(
        typeLabel: String = "Wi-Fi / Données",
        networkSwitched: Boolean = true
    ) {
        networkGraceJob?.cancel()
        networkGraceJob = null

        val wasOffline = !_sessionState.value.networkAvailable
        _sessionState.value = _sessionState.value.copy(
            networkAvailable = true,
            networkTypeLabel = typeLabel
        )
        if (_sessionState.value.isConnected && (wasOffline || networkSwitched)) {
            hasNetworkEverBeenLostInSession = false
            scheduleReconnect(
                reason = if (wasOffline) "Réseau rétabli ($typeLabel)" else "Bascule réseau ($typeLabel)",
                immediate = true
            )
        }
    }

    internal fun onIceConnectionStateChanged(
        remoteUid: String,
        iceState: PeerConnection.IceConnectionState
    ) {
        val updatedStates = _sessionState.value.peerConnectionStates + (remoteUid to iceState.name)
        val updatedPeers = _sessionState.value.remotePeers.map {
            if (it.uid == remoteUid) it.copy(connectionState = iceState.name) else it
        }
        val nextPhase = when (iceState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> VoiceConnectionPhase.CONNECTED
            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.FAILED -> VoiceConnectionPhase.RECONNECTING
            else -> _sessionState.value.connectionPhase
        }
        val nextLabel = when (iceState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> "RTC Connecté (${updatedPeers.size} pairs P2P)"
            PeerConnection.IceConnectionState.CHECKING -> "Négociation ICE..."
            PeerConnection.IceConnectionState.DISCONNECTED -> "Connexion instable • Reconnexion..."
            PeerConnection.IceConnectionState.FAILED -> "Échec ICE • Tentative de reprise..."
            else -> _sessionState.value.iceStatusLabel
        }
        _sessionState.value = _sessionState.value.copy(
            peerConnectionStates = updatedStates,
            remotePeers = updatedPeers,
            connectionPhase = nextPhase,
            iceStatusLabel = nextLabel,
            reconnectAttempt = if (nextPhase == VoiceConnectionPhase.CONNECTED) 0 else _sessionState.value.reconnectAttempt
        )

        if (iceState == PeerConnection.IceConnectionState.CONNECTED ||
            iceState == PeerConnection.IceConnectionState.COMPLETED
        ) {
            reconnectJob?.cancel()
        } else if (iceState == PeerConnection.IceConnectionState.FAILED ||
            iceState == PeerConnection.IceConnectionState.DISCONNECTED
        ) {
            if (reconnectJob?.isActive != true) {
                scheduleReconnect(reason = "Interruption ICE ($remoteUid)", immediate = false)
            }
        }
    }

    private fun scheduleReconnect(reason: String, immediate: Boolean) {
        if (!_sessionState.value.isConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive && _sessionState.value.isConnected) {
                attempt++
                if (attempt > maxReconnectAttempts) {
                    autoDisconnect(VoiceDisconnectReason.RECONNECT_EXHAUSTED)
                    return@launch
                }

                _sessionState.value = _sessionState.value.copy(
                    connectionPhase = VoiceConnectionPhase.RECONNECTING,
                    reconnectAttempt = attempt,
                    iceStatusLabel = "$reason • Tentative $attempt/$maxReconnectAttempts"
                )

                val delayMs = if (immediate && attempt == 1) {
                    150L
                } else {
                    min(reconnectBackoffBaseMs * (1L shl (attempt - 1)), 15_000L)
                }
                delay(delayMs)

                if (!_sessionState.value.isConnected) return@launch
                if (!_sessionState.value.networkAvailable) {
                    // Wait for network callback / grace timeout to fire
                    continue
                }

                val restartAttempted = performIceRestartAndRepublish()
                if (restartAttempted && peerConnections.isEmpty()) {
                    // Signaling cloud active without remote peers yet -> restore CONNECTED cleanly
                    _sessionState.value = _sessionState.value.copy(
                        connectionPhase = VoiceConnectionPhase.CONNECTED,
                        reconnectAttempt = 0,
                        iceStatusLabel = "RTC Prêt • En attente de pairs"
                    )
                    return@launch
                }
            }
        }
    }

    private fun performIceRestartAndRepublish(): Boolean {
        return try {
            publishSelfPeer(force = true)
            if (peerConnections.isNotEmpty()) {
                peerConnections.values.forEach { pc ->
                    try {
                        pc.restartIce()
                    } catch (_: Throwable) {
                    }
                }
                renegotiateAllPeers(iceRestart = true)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun autoDisconnect(reason: VoiceDisconnectReason) {
        leaveRoom(reason = reason, preserveDisconnectReason = true)
        onAutoDisconnected(reason)
    }

    private fun initializeWebRtcEngine() {
        if (peerConnectionFactory != null) {
            createLocalAudioTrack()
            return
        }
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions()
            )
            val egl = EglBase.create()
            eglBase = egl
            val audioModule = JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule()
            val factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .createPeerConnectionFactory()
            audioModule.release()
            peerConnectionFactory = factory
            createLocalAudioTrack()
        } catch (e: Throwable) {
            Log.w("WebRtcVoice", "Native WebRTC initialization skipped (fallback signaling mode)", e)
        }
    }

    private fun createLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return
        try {
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack(
                "raven_audio_${UUID.randomUUID().toString().take(4)}",
                localAudioSource
            )?.apply {
                setEnabled(!isMutedState && !isDeafenedState)
            }
        } catch (e: Throwable) {
            Log.w("WebRtcVoice", "Failed creating local audio track", e)
        }
    }

    private fun startCameraTrack(useFront: Boolean) {
        val factory = peerConnectionFactory ?: return
        val egl = eglBase ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            stopCameraTrack()
            val enumerator = Camera2Enumerator(context.applicationContext)
            val deviceName = enumerator.deviceNames.firstOrNull {
                if (useFront) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
            } ?: enumerator.deviceNames.firstOrNull() ?: return

            val capturer = enumerator.createCapturer(deviceName, null) ?: return
            val texHelper = SurfaceTextureHelper.create("RavenCamCapture", egl.eglBaseContext)
            val vSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(texHelper, context.applicationContext, vSource.capturerObserver)
            capturer.startCapture(640, 480, 24)
            val vTrack = factory.createVideoTrack("raven_video_${UUID.randomUUID().toString().take(4)}", vSource)
            vTrack.setEnabled(true)

            videoCapturer = capturer
            cameraTextureHelper = texHelper
            localVideoSource = vSource
            localVideoTrack = vTrack
            _localVideoTrack.value = vTrack

            peerConnections.forEach { (uid, pc) ->
                try {
                    val sender = pc.addTrack(vTrack, listOf("raven_stream"))
                    if (sender != null) {
                        videoSenders[uid] = sender
                    }
                } catch (_: Throwable) {
                }
            }
            renegotiateAllPeers(iceRestart = false)
        } catch (e: Throwable) {
            Log.w("WebRtcVoice", "Failed to start WebRTC camera track", e)
        }
    }

    private fun stopCameraTrack() {
        peerConnections.forEach { (uid, pc) ->
            videoSenders.remove(uid)?.let { sender ->
                try { pc.removeTrack(sender) } catch (_: Throwable) {}
            }
        }
        try {
            videoCapturer?.stopCapture()
        } catch (_: Throwable) {
        }
        try {
            videoCapturer?.dispose()
            localVideoTrack?.dispose()
            localVideoSource?.dispose()
            cameraTextureHelper?.dispose()
        } catch (_: Throwable) {
        } finally {
            videoCapturer = null
            localVideoTrack = null
            localVideoSource = null
            cameraTextureHelper = null
            _localVideoTrack.value = null
        }
    }

    private fun stopScreenCaptureInternal(renegotiate: Boolean) {
        peerConnections.forEach { (uid, pc) ->
            screenSenders.remove(uid)?.let { sender ->
                try { pc.removeTrack(sender) } catch (_: Throwable) {}
            }
        }
        try {
            screenCapturer?.stopCapture()
        } catch (_: Throwable) {
        }
        try {
            screenCapturer?.dispose()
            screenVideoTrack?.dispose()
            screenVideoSource?.dispose()
            screenTextureHelper?.dispose()
        } catch (_: Throwable) {
        } finally {
            screenCapturer = null
            screenVideoTrack = null
            screenVideoSource = null
            screenTextureHelper = null
        }
        if (renegotiate && peerConnections.isNotEmpty()) {
            renegotiateAllPeers(iceRestart = false)
        }
    }

    private fun renegotiateAllPeers(iceRestart: Boolean) {
        peerConnections.keys.toList().forEach { remoteUid ->
            initiateOfferToPeer(remoteUid, iceRestart = iceRestart)
        }
    }

    private fun configureAudioRoutingForCall() {
        val am = audioManager ?: return
        try {
            if (previousAudioMode == null) previousAudioMode = am.mode
            @Suppress("DEPRECATION")
            if (previousSpeakerphone == null) previousSpeakerphone = am.isSpeakerphoneOn
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = isSpeakerphoneOnState

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest == null) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            }
        } catch (_: Throwable) {
        }
    }

    private fun restoreAudioRoutingAfterCall() {
        val am = audioManager ?: return
        try {
            previousAudioMode?.let { am.mode = it }
            @Suppress("DEPRECATION")
            previousSpeakerphone?.let { am.isSpeakerphoneOn = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            }
        } catch (_: Throwable) {
        } finally {
            previousAudioMode = null
            previousSpeakerphone = null
            audioFocusRequest = null
        }
    }

    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallbackRegistered) return
        val cm = connectivityManager ?: return
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, networkCallback)
            networkCallbackRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Throwable) {
        } finally {
            networkCallbackRegistered = false
        }
    }

    private fun checkCurrentNetworkAvailable(): Boolean {
        val cm = connectivityManager ?: return true
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            true
        }
    }

    private fun detectCurrentNetworkLabel(): String {
        val cm = connectivityManager ?: return "Wi-Fi / Données"
        return try {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            resolveNetworkLabel(caps)
        } catch (_: Throwable) {
            "Wi-Fi / Données"
        }
    }

    private fun resolveNetworkLabel(caps: NetworkCapabilities?): String {
        if (caps == null) return "Réseau actif"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G / 4G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Réseau actif"
        }
    }

    private fun publishSelfPeer(force: Boolean = false) {
        val db = activeFirestore ?: return
        val srv = activeServerId ?: return
        val ch = activeChannelId ?: return
        val uid = activeUid ?: return
        if (srv == "dm_tab") return

        val now = System.currentTimeMillis()
        if (!force && (now - lastPresencePublishMs) < 250L) return
        lastPresencePublishMs = now

        val speakingToPublish = currentSpeakingState && !isMutedState && !isDeafenedState
        val stateLabel = _sessionState.value.connectionPhase.name
        val attempt = _sessionState.value.reconnectAttempt

        scope.launch(Dispatchers.IO) {
            try {
                db.collection("servers").document(srv)
                    .collection("channels").document(ch)
                    .collection("voice_peers").document(uid)
                    .set(
                        mapOf(
                            "uid" to uid,
                            "displayName" to activeDisplayName.ifBlank { "Membre" },
                            "isMuted" to isMutedState,
                            "isDeafened" to isDeafenedState,
                            "isCameraOn" to isCameraState,
                            "isScreenSharing" to isScreenShareState,
                            "isSpeaking" to speakingToPublish,
                            "connectionState" to stateLabel,
                            "reconnectAttempt" to attempt,
                            "memberIds" to activeMemberIds,
                            "isPublic" to activeIsPublic,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
            } catch (e: Exception) {
                Log.w("WebRtcVoice", "Failed to publish voice peer presence", e)
            }
        }
    }

    private fun subscribePeersAndSignals(
        db: FirebaseFirestore,
        serverId: String,
        channelId: String,
        currentUid: String
    ) {
        val channelRef = db.collection("servers").document(serverId)
            .collection("channels").document(channelId)

        val peersQuery = if (activeIsPublic) {
            channelRef.collection("voice_peers").whereEqualTo("isPublic", true)
        } else {
            channelRef.collection("voice_peers").whereArrayContains("memberIds", currentUid)
        }

        peersListener = peersQuery.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val remotes = snapshot.documents.mapNotNull { doc ->
                val uid = doc.getString("uid") ?: return@mapNotNull null
                if (uid == currentUid) return@mapNotNull null
                val muted = doc.getBoolean("isMuted") ?: false
                val deaf = doc.getBoolean("isDeafened") ?: false
                val speaking = (doc.getBoolean("isSpeaking") ?: false) && !muted && !deaf
                RemoteVoicePeerState(
                    uid = uid,
                    displayName = doc.getString("displayName") ?: "Pair",
                    isMuted = muted,
                    isDeafened = deaf,
                    isCameraOn = doc.getBoolean("isCameraOn") ?: false,
                    isScreenSharing = doc.getBoolean("isScreenSharing") ?: false,
                    isSpeaking = speaking,
                    connectionState = doc.getString("connectionState") ?: "CONNECTED"
                )
            }

            // Clean up PeerConnections for peers who departed from voice_peers
            val activeRemoteIds = remotes.map { it.uid }.toSet()
            val departedIds = peerConnections.keys.filter { it !in activeRemoteIds }
            departedIds.forEach { goneUid -> closePeerConnection(goneUid) }

            _sessionState.value = _sessionState.value.copy(
                remotePeers = remotes,
                iceStatusLabel = when {
                    _sessionState.value.connectionPhase == VoiceConnectionPhase.RECONNECTING -> _sessionState.value.iceStatusLabel
                    remotes.isEmpty() -> "RTC Prêt • En attente de pairs"
                    else -> "RTC Connecté (${remotes.size} pairs P2P)"
                }
            )
            remotes.forEach { remote ->
                if (!peerConnections.containsKey(remote.uid) && currentUid < remote.uid) {
                    initiateOfferToPeer(remote.uid, iceRestart = false)
                }
            }
        }

        signalsListener = channelRef.collection("rtc_signals")
            .whereEqualTo("toUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    if (!handledSignalIds.add(doc.id)) continue
                    val fromUid = doc.getString("fromUid") ?: continue
                    val type = doc.getString("type") ?: continue
                    val payload = doc.getString("payload") ?: continue
                    val sdpMid = doc.getString("sdpMid")
                    val sdpMLineIndex = doc.getLong("sdpMLineIndex")?.toInt() ?: 0
                    handleIncomingSignal(fromUid, type, payload, sdpMid, sdpMLineIndex)
                    doc.reference.delete()
                }
            }
    }

    private fun closePeerConnection(remoteUid: String) {
        peerConnections.remove(remoteUid)?.let { pc ->
            try { pc.close(); pc.dispose() } catch (_: Throwable) {}
        }
        videoSenders.remove(remoteUid)
        screenSenders.remove(remoteUid)
        remoteAudioTracks.remove(remoteUid)
        remoteDescSetPeers.remove(remoteUid)
        pendingIceCandidates.remove(remoteUid)
        if (_remoteVideoTracks.value.containsKey(remoteUid)) {
            _remoteVideoTracks.value = _remoteVideoTracks.value - remoteUid
        }
        if (_sessionState.value.peerConnectionStates.containsKey(remoteUid)) {
            _sessionState.value = _sessionState.value.copy(
                peerConnectionStates = _sessionState.value.peerConnectionStates - remoteUid
            )
        }
    }

    private fun getOrCreatePeerConnection(remoteUid: String): PeerConnection? {
        peerConnections[remoteUid]?.let { return it }
        val factory = peerConnectionFactory ?: return null
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                state ?: return
                onIceConnectionStateChanged(remoteUid, state)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                sendSignal(
                    toUid = remoteUid,
                    type = "ICE",
                    payload = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    remoteAudioTracks[remoteUid] = track
                    track.setEnabled(!isDeafenedState)
                    val vol = peerVolumes[remoteUid] ?: 1f
                    track.setVolume(if (isDeafenedState) 0.0 else (vol * 2.0))
                } else if (track is VideoTrack) {
                    _remoteVideoTracks.value = _remoteVideoTracks.value + (remoteUid to track)
                }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {}
        }) ?: return null

        localAudioTrack?.let { pc.addTrack(it, listOf("raven_stream")) }
        localVideoTrack?.let { vt ->
            pc.addTrack(vt, listOf("raven_stream"))?.let { sender -> videoSenders[remoteUid] = sender }
        }
        screenVideoTrack?.let { st ->
            pc.addTrack(st, listOf("raven_screen_stream"))?.let { sender -> screenSenders[remoteUid] = sender }
        }
        peerConnections[remoteUid] = pc
        return pc
    }

    private fun initiateOfferToPeer(remoteUid: String, iceRestart: Boolean) {
        val pc = getOrCreatePeerConnection(remoteUid) ?: return
        val constraints = MediaConstraints().apply {
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sendSignal(remoteUid, "OFFER", sdp.description)
                    }
                }, sdp)
            }
        }, constraints)
    }

    private fun handleIncomingSignal(
        fromUid: String,
        type: String,
        payload: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val pc = getOrCreatePeerConnection(fromUid) ?: return
        when (type) {
            "OFFER" -> {
                val myUid = activeUid.orEmpty()
                val isPolite = myUid > fromUid
                if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    if (!isPolite) {
                        // Impolite peer drops colliding offer
                        return
                    }
                    pc.setLocalDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ROLLBACK, ""))
                }
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescSetPeers.add(fromUid)
                        flushPendingIceCandidates(fromUid, pc)
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(sdp: SessionDescription?) {
                                sdp ?: return
                                pc.setLocalDescription(object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        sendSignal(fromUid, "ANSWER", sdp.description)
                                    }
                                }, sdp)
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, payload))
            }
            "ANSWER" -> {
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescSetPeers.add(fromUid)
                        flushPendingIceCandidates(fromUid, pc)
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, payload))
            }
            "ICE" -> {
                val candidate = IceCandidate(sdpMid ?: "0", sdpMLineIndex, payload)
                if (fromUid in remoteDescSetPeers) {
                    pc.addIceCandidate(candidate)
                } else {
                    pendingIceCandidates.getOrPut(fromUid) { mutableListOf() }.add(candidate)
                }
            }
        }
    }

    private fun flushPendingIceCandidates(uid: String, pc: PeerConnection) {
        pendingIceCandidates.remove(uid)?.forEach { candidate ->
            try {
                pc.addIceCandidate(candidate)
            } catch (_: Throwable) {
            }
        }
    }

    private fun sendSignal(
        toUid: String,
        type: String,
        payload: String,
        sdpMid: String? = null,
        sdpMLineIndex: Int? = null
    ) {
        val db = activeFirestore ?: return
        val srv = activeServerId ?: return
        val ch = activeChannelId ?: return
        val fromUid = activeUid ?: return
        if (srv == "dm_tab") return

        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf<String, Any>(
                    "fromUid" to fromUid,
                    "toUid" to toUid,
                    "type" to type,
                    "payload" to payload,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                sdpMid?.let { data["sdpMid"] = it }
                sdpMLineIndex?.let { data["sdpMLineIndex"] = it }
                db.collection("servers").document(srv)
                    .collection("channels").document(ch)
                    .collection("rtc_signals").document("sig_${UUID.randomUUID().toString().take(8)}")
                    .set(data)
            } catch (e: Exception) {
                Log.w("WebRtcVoice", "Failed to send RTC signal", e)
            }
        }
    }

    private fun updateSpeakingSample(speaking: Boolean, level: Float) {
        val prev = currentSpeakingState
        currentSpeakingState = speaking && !isMutedState && !isDeafenedState
        _sessionState.value = _sessionState.value.copy(
            localMicSpeaking = currentSpeakingState,
            localMicLevel = if (isMutedState || isDeafenedState) 0f else level
        )
        if (prev != currentSpeakingState) {
            publishSelfPeer(force = false)
        }
    }

    private fun startMicMeteringLoop() {
        micMeterJob?.cancel()
        micMeterJob = scope.launch(Dispatchers.IO) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMicPermission) {
                while (isActive) {
                    delay(400L)
                    updateSpeakingSample(speaking = false, level = 0f)
                }
                return@launch
            }

            var recorder: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1024)
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buf = ShortArray(minBuf / 2)
                    while (isActive) {
                        if (isMutedState || isDeafenedState) {
                            updateSpeakingSample(speaking = false, level = 0f)
                            delay(250L)
                            continue
                        }
                        val read = recorder.read(buf, 0, buf.size)
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += buf[i] * buf[i].toDouble()
                            }
                            val rms = sqrt(sum / read)
                            val level = (rms / 3500.0).toFloat().coerceIn(0f, 1f)
                            updateSpeakingSample(speaking = level > 0.06f, level = level)
                        }
                        delay(150L)
                    }
                }
            } catch (_: Throwable) {
                while (isActive) {
                    delay(400L)
                    updateSpeakingSample(speaking = !isMutedState && !isDeafenedState, level = if (!isMutedState && !isDeafenedState) 0.2f else 0f)
                }
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
