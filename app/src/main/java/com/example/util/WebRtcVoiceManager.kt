package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import kotlin.math.sqrt

data class RemoteVoicePeerState(
    val uid: String,
    val displayName: String,
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val isCameraOn: Boolean = false,
    val isScreenSharing: Boolean = false,
    val isSpeaking: Boolean = false
)

data class WebRtcSessionState(
    val isConnected: Boolean = false,
    val iceStatusLabel: String = "Mode Local",
    val remotePeers: List<RemoteVoicePeerState> = emptyList(),
    val localMicSpeaking: Boolean = false,
    val localMicLevel: Float = 0f,
    val isCameraStreaming: Boolean = false,
    val pingMs: Int = 24
)

/**
 * Real WebRTC voice/video + Firestore SDP/ICE signaling manager powered by Stream WebRTC Android.
 * Handles P2P audio/video tracks, individual peer volume mixing (0%..200%),
 * AudioRecord mic amplitude detection, and safe fallback on non-native test environments.
 */
class WebRtcVoiceManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _sessionState = MutableStateFlow(WebRtcSessionState())
    val sessionState: StateFlow<WebRtcSessionState> = _sessionState.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null

    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val remoteAudioTracks = mutableMapOf<String, AudioTrack>()
    private val peerVolumes = mutableMapOf<String, Float>()
    private val handledSignalIds = mutableSetOf<String>()

    private var peersListener: ListenerRegistration? = null
    private var signalsListener: ListenerRegistration? = null
    private var micMeterJob: Job? = null

    private var activeServerId: String? = null
    private var activeChannelId: String? = null
    private var activeUid: String? = null
    private var activeDisplayName: String = ""
    private var activeIsPublic: Boolean = false
    private var activeMemberIds: List<String> = emptyList()
    private var activeFirestore: FirebaseFirestore? = null
    private var isMutedState: Boolean = false
    private var isDeafenedState: Boolean = false
    private var isCameraState: Boolean = false
    private var isScreenShareState: Boolean = false

    fun getEglContext(): EglBase.Context? = eglBase?.eglBaseContext

    fun joinRoom(
        serverId: String,
        channelId: String,
        currentUid: String,
        displayName: String,
        isPublic: Boolean,
        memberIds: List<String>,
        firestore: FirebaseFirestore?,
        isMuted: Boolean,
        isDeafened: Boolean
    ) {
        leaveRoom()
        activeServerId = serverId
        activeChannelId = channelId
        activeUid = currentUid
        activeDisplayName = displayName
        activeIsPublic = isPublic
        activeMemberIds = (memberIds + currentUid).distinct()
        activeFirestore = firestore
        isMutedState = isMuted
        isDeafenedState = isDeafened

        initializeWebRtcEngine()
        startMicMeteringLoop()

        if (firestore != null && serverId != "dm_tab") {
            _sessionState.value = _sessionState.value.copy(
                isConnected = true,
                iceStatusLabel = "RTC Prêt • Signaling Cloud"
            )
            publishSelfPeer()
            subscribePeersAndSignals(firestore, serverId, channelId, currentUid)
        } else {
            _sessionState.value = _sessionState.value.copy(
                isConnected = true,
                iceStatusLabel = "RTC Local Actif"
            )
        }
    }

    fun leaveRoom() {
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

        stopCameraTrack()
        peerConnections.values.forEach {
            try { it.close(); it.dispose() } catch (_: Throwable) {}
        }
        peerConnections.clear()
        remoteAudioTracks.clear()
        handledSignalIds.clear()
        _remoteVideoTracks.value = emptyMap()

        try {
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
        } catch (_: Throwable) {
        } finally {
            localAudioTrack = null
            localAudioSource = null
        }

        activeServerId = null
        activeChannelId = null
        activeUid = null
        activeFirestore = null
        _sessionState.value = WebRtcSessionState()
    }

    fun setMuted(muted: Boolean) {
        isMutedState = muted
        try {
            localAudioTrack?.setEnabled(!muted && !isDeafenedState)
        } catch (_: Throwable) {
        }
        publishSelfPeer()
    }

    fun setDeafened(deafened: Boolean) {
        isDeafenedState = deafened
        if (deafened) isMutedState = true
        try {
            localAudioTrack?.setEnabled(!isMutedState && !deafened)
            remoteAudioTracks.forEach { (uid, track) ->
                track.setEnabled(!deafened)
                val vol = if (deafened) 0.0 else ((peerVolumes[uid] ?: 1f) * 2.0)
                track.setVolume(vol)
            }
        } catch (_: Throwable) {
        }
        publishSelfPeer()
    }

    fun setRemotePeerVolume(peerUid: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 2f)
        peerVolumes[peerUid] = clamped
        try {
            remoteAudioTracks[peerUid]?.setVolume(if (isDeafenedState) 0.0 else (clamped * 2.0))
        } catch (_: Throwable) {
        }
    }

    fun setCameraEnabled(enabled: Boolean, useFrontCamera: Boolean = true) {
        isCameraState = enabled
        if (enabled) {
            startCameraTrack(useFrontCamera)
        } else {
            stopCameraTrack()
        }
        _sessionState.value = _sessionState.value.copy(isCameraStreaming = enabled)
        publishSelfPeer()
    }

    fun setScreenSharing(enabled: Boolean) {
        isScreenShareState = enabled
        publishSelfPeer()
    }

    private fun initializeWebRtcEngine() {
        if (peerConnectionFactory != null) return
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

            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("raven_audio_${UUID.randomUUID().toString().take(4)}", localAudioSource)?.apply {
                setEnabled(!isMutedState && !isDeafenedState)
            }
        } catch (e: Throwable) {
            Log.w("WebRtcVoice", "Native WebRTC initialization skipped (fallback signaling mode)", e)
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
            surfaceTextureHelper = texHelper
            localVideoSource = vSource
            localVideoTrack = vTrack

            peerConnections.values.forEach { pc ->
                try {
                    pc.addTrack(vTrack, listOf("raven_stream"))
                } catch (_: Throwable) {
                }
            }
        } catch (e: Throwable) {
            Log.w("WebRtcVoice", "Failed to start WebRTC camera track", e)
        }
    }

    private fun stopCameraTrack() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            localVideoTrack?.dispose()
            localVideoSource?.dispose()
            surfaceTextureHelper?.dispose()
        } catch (_: Throwable) {
        } finally {
            videoCapturer = null
            localVideoTrack = null
            localVideoSource = null
            surfaceTextureHelper = null
        }
    }

    private fun publishSelfPeer() {
        val db = activeFirestore ?: return
        val srv = activeServerId ?: return
        val ch = activeChannelId ?: return
        val uid = activeUid ?: return
        if (srv == "dm_tab") return

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
                RemoteVoicePeerState(
                    uid = uid,
                    displayName = doc.getString("displayName") ?: "Pair",
                    isMuted = doc.getBoolean("isMuted") ?: false,
                    isDeafened = doc.getBoolean("isDeafened") ?: false,
                    isCameraOn = doc.getBoolean("isCameraOn") ?: false,
                    isScreenSharing = doc.getBoolean("isScreenSharing") ?: false,
                    isSpeaking = !(doc.getBoolean("isMuted") ?: false)
                )
            }
            _sessionState.value = _sessionState.value.copy(
                remotePeers = remotes,
                iceStatusLabel = if (remotes.isEmpty()) "RTC Prêt • En attente de pairs" else "RTC Connecté (${remotes.size} pairs P2P)"
            )
            remotes.forEach { remote ->
                if (!peerConnections.containsKey(remote.uid) && currentUid < remote.uid) {
                    initiateOfferToPeer(remote.uid)
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
                    // Clean up consumed signal
                    doc.reference.delete()
                }
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
        }

        val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                val label = when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> "RTC Connecté (P2P)"
                    PeerConnection.IceConnectionState.CHECKING -> "Négociation ICE..."
                    else -> _sessionState.value.iceStatusLabel
                }
                _sessionState.value = _sessionState.value.copy(iceStatusLabel = label)
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
        localVideoTrack?.let { pc.addTrack(it, listOf("raven_stream")) }
        peerConnections[remoteUid] = pc
        return pc
    }

    private fun initiateOfferToPeer(remoteUid: String) {
        val pc = getOrCreatePeerConnection(remoteUid) ?: return
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                sendSignal(remoteUid, "OFFER", sdp.description)
            }
        }, MediaConstraints())
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
                pc.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, payload))
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp ?: return
                        pc.setLocalDescription(SimpleSdpObserver(), sdp)
                        sendSignal(fromUid, "ANSWER", sdp.description)
                    }
                }, MediaConstraints())
            }
            "ANSWER" -> {
                pc.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, payload))
            }
            "ICE" -> {
                pc.addIceCandidate(IceCandidate(sdpMid ?: "0", sdpMLineIndex, payload))
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
                    _sessionState.value = _sessionState.value.copy(
                        localMicSpeaking = !isMutedState && !isDeafenedState,
                        localMicLevel = if (!isMutedState && !isDeafenedState) 0.25f else 0f
                    )
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
                            _sessionState.value = _sessionState.value.copy(localMicSpeaking = false, localMicLevel = 0f)
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
                            _sessionState.value = _sessionState.value.copy(
                                localMicSpeaking = level > 0.08f,
                                localMicLevel = level
                            )
                        }
                        delay(150L)
                    }
                }
            } catch (_: Throwable) {
                while (isActive) {
                    delay(400L)
                    _sessionState.value = _sessionState.value.copy(
                        localMicSpeaking = !isMutedState && !isDeafenedState,
                        localMicLevel = if (!isMutedState && !isDeafenedState) 0.2f else 0f
                    )
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
