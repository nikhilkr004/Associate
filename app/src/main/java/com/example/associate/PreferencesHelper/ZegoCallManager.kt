package com.example.associate.PreferencesHelper

import android.app.Application
import android.content.Context
import android.view.TextureView
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoUser
import im.zego.zegoexpress.constants.ZegoViewMode
import org.json.JSONObject
import java.util.ArrayList
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoPublisherState

class ZegoCallManager(private val context: Context, private val listener: ZegoCallListener) {
    private var engine: ZegoExpressEngine? = null
    private var isFrontCamera = true

    interface ZegoCallListener {
        fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: JSONObject)
        fun onRoomUserUpdate(roomID: String, updateType: ZegoUpdateType, userList: ArrayList<ZegoUser>)
        fun onRemoteCameraStateUpdate(streamID: String, state: Int)
    }

    fun initializeEngine(appID: Long, appSign: String): Boolean {
        val profile = ZegoEngineProfile()
        profile.appID = appID
        profile.appSign = appSign
        profile.scenario = ZegoScenario.DEFAULT
        profile.application = context.applicationContext as Application
        
        engine = ZegoExpressEngine.createEngine(profile, eventHandler)
        return engine != null
    }

    fun joinRoom(roomID: String, userID: String, userName: String) {
        val user = ZegoUser(userID, userName)
        val config = im.zego.zegoexpress.entity.ZegoRoomConfig()
        config.isUserStatusNotify = true
        engine?.loginRoom(roomID, user, config)
        
        // Start publishing local stream (User's video)
        engine?.startPublishingStream("stream_$userID")
    }

    fun leaveRoom(roomID: String) {
        engine?.logoutRoom(roomID)
        ZegoExpressEngine.destroyEngine(null)
    }

    fun setupLocalVideo(view: TextureView) {
        val canvas = ZegoCanvas(view)
        canvas.viewMode = ZegoViewMode.ASPECT_FILL
        engine?.startPreview(canvas)
    }

    fun setupRemoteVideo(view: TextureView, streamID: String) {
        val canvas = ZegoCanvas(view)
        canvas.viewMode = ZegoViewMode.ASPECT_FILL
        engine?.startPlayingStream(streamID, canvas)
    }

    fun muteMicrophone(mute: Boolean) {
        engine?.muteMicrophone(mute)
    }

    fun enableCamera(enable: Boolean) {
        engine?.enableCamera(enable)
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        engine?.useFrontCamera(isFrontCamera)
    }


    private val eventHandler = object : IZegoEventHandler() {
        override fun onRoomStateChanged(roomID: String?, reason: ZegoRoomStateChangedReason?, errorCode: Int, extendedData: JSONObject?) {
            super.onRoomStateChanged(roomID, reason, errorCode, extendedData)
            if (roomID != null && extendedData != null) {
                listener.onRoomStateChanged(roomID, reason?.value() ?: 0, errorCode, extendedData)
            }
        }

        override fun onRoomUserUpdate(roomID: String?, updateType: ZegoUpdateType?, userList: ArrayList<ZegoUser>?) {
            super.onRoomUserUpdate(roomID, updateType, userList)
            if (roomID != null && updateType != null && userList != null) {
                listener.onRoomUserUpdate(roomID, updateType, userList)
            }
        }
        
        override fun onPublisherStateUpdate(streamID: String?, state: ZegoPublisherState?, errorCode: Int, extendedData: JSONObject?) {
             super.onPublisherStateUpdate(streamID, state, errorCode, extendedData)
             // Handle stream updates if needed
        }
        
        // Note: Zego handles remote stream adding automatically via onRoomStreamUpdate usually, 
        // but for simplicity, you can trigger setupRemoteVideo when you know the streamID (e.g. from user list or convention)
    }
}
