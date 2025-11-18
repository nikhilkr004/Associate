package com.example.associate

import android.content.Context
import android.view.SurfaceView
import android.widget.FrameLayout
import io.agora.rtc2.*
import io.agora.rtc2.IRtcEngineEventHandler.RtcStats
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

class AgoraManager(
    private val context: Context,
    private val listener: AgoraEventListener
) {
    private var agoraEngine: RtcEngine? = null
    private var isJoined = false

    interface AgoraEventListener {
        fun onUserJoined(uid: Int)
        fun onUserOffline(uid: Int, reason: Int)
        fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int)
        fun onLeaveChannel(stats: RtcStats)
        fun onError(err: Int)
        fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int)
    }

    fun initializeAgoraEngine(appId: String): Boolean {
        return try {
            val config = RtcEngineConfig()
            config.mContext = context
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler

            agoraEngine = RtcEngine.create(config)
            setupVideoConfig()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun setupVideoConfig() {
        agoraEngine?.apply {
            // Enable video FIRST
            enableVideo()

            // Configure video encoder with better settings
            setVideoEncoderConfiguration(VideoEncoderConfiguration().apply {
                dimensions = VideoEncoderConfiguration.VD_640x360
//                frameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15
                bitrate = VideoEncoderConfiguration.STANDARD_BITRATE
                orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
                mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_AUTO
            })

            // Enable audio with better configuration
            enableAudio()
            setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_CHATROOM)

            // IMPORTANT: Set channel profile for communication
            setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)

            // Enable speakerphone by default for better audio
            setEnableSpeakerphone(true)

            // Set audio route to speaker for better voice quality
            setDefaultAudioRoutetoSpeakerphone(true)
        }
    }

    fun joinChannel(channelName: String = "ASSOCIATE"): Int {
        return agoraEngine?.joinChannel(null, channelName, "", 0) ?: -1
    }

    fun leaveChannel() {
        agoraEngine?.leaveChannel()
        isJoined = false
    }

    fun setupLocalVideo(container: FrameLayout): SurfaceView {
        val surfaceView = SurfaceView(context)
        surfaceView.setZOrderMediaOverlay(true)
        container.addView(surfaceView)

        // FIX: Use FIT mode instead of HIDDEN for better video display
        val canvas = VideoCanvas(surfaceView, Constants.RENDER_MODE_FIT, 0)
        agoraEngine?.setupLocalVideo(canvas)

        // IMPORTANT: Start local preview to see your own video
        agoraEngine?.startPreview()

        return surfaceView
    }

    fun setupRemoteVideo(container: FrameLayout, uid: Int): SurfaceView {
        val surfaceView = SurfaceView(context)
        container.addView(surfaceView)

        // FIX: Use FIT mode instead of HIDDEN for better video display
        val canvas = VideoCanvas(surfaceView, Constants.RENDER_MODE_FIT, uid)
        agoraEngine?.setupRemoteVideo(canvas)

        return surfaceView
    }

    fun muteLocalAudio(mute: Boolean): Int {
        return agoraEngine?.muteLocalAudioStream(mute) ?: -1
    }

    fun enableLocalVideo(enable: Boolean): Int {
        return agoraEngine?.enableLocalVideo(enable) ?: -1
    }

    fun switchCamera(): Int {
        return agoraEngine?.switchCamera() ?: -1
    }

    // NEW: Start/stop local video preview
    fun startPreview(): Int {
        return agoraEngine?.startPreview() ?: -1
    }

    fun stopPreview(): Int {
        return agoraEngine?.stopPreview() ?: -1
    }

    // NEW: Audio management methods
    fun setEnableSpeakerphone(enable: Boolean): Int {
        return agoraEngine?.setEnableSpeakerphone(enable) ?: -1
    }

    fun setDefaultAudioRoutetoSpeakerphone(default: Boolean): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            agoraEngine?.setDefaultAudioRoutetoSpeakerphone(default) ?: -1
        } else {
            -1
        }
    }

    // NEW: Get connection state
    fun getConnectionState(): Int {
        return agoraEngine?.connectionState ?: Constants.CONNECTION_STATE_DISCONNECTED
    }

    fun destroy() {
        try {
            agoraEngine?.stopPreview()
            agoraEngine?.leaveChannel()
            RtcEngine.destroy()
            agoraEngine = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            listener.onJoinChannelSuccess(channel, uid, elapsed)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            listener.onUserJoined(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            listener.onUserOffline(uid, reason)
        }

        override fun onLeaveChannel(stats: RtcStats) {
            isJoined = false
            listener.onLeaveChannel(stats)
        }

        override fun onError(err: Int) {
            listener.onError(err)
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            listener.onRemoteVideoStateChanged(uid, state, reason, elapsed)
        }

        // NEW: Audio state callbacks
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            // You can handle remote audio state changes here if needed
            when (state) {
                Constants.REMOTE_AUDIO_STATE_STARTING -> {
                    android.util.Log.d("AgoraManager", "Remote audio starting - UID: $uid")
                }
                Constants.REMOTE_AUDIO_STATE_DECODING -> {
                    android.util.Log.d("AgoraManager", "Remote audio active - UID: $uid")
                }
                Constants.REMOTE_AUDIO_STATE_STOPPED -> {
                    android.util.Log.d("AgoraManager", "Remote audio stopped - UID: $uid")
                }
            }
        }

        // NEW: Local audio state callback
        override fun onLocalAudioStateChanged(state: Int, error: Int) {
            when (state) {
                Constants.LOCAL_AUDIO_STREAM_STATE_RECORDING -> {
                    android.util.Log.d("AgoraManager", "Local audio recording started")
                }
                Constants.LOCAL_AUDIO_STREAM_STATE_FAILED -> {
                    android.util.Log.d("AgoraManager", "Local audio failed: $error")
                }
            }
        }
    }
}