package com.example.associate.Activitys

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.AgoraManager
import com.example.associate.AppConstants
import com.example.associate.R
import com.example.associate.Repositorys.VideoCallService
import com.example.associate.databinding.ActivityVideoCallBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.IRtcEngineEventHandler.RtcStats
import java.util.Timer
import java.util.TimerTask

class VideoCallActivity : AppCompatActivity(), AgoraManager.AgoraEventListener {

    private val binding by lazy {
        ActivityVideoCallBinding.inflate(layoutInflater)
    }

    private lateinit var agoraManager: AgoraManager
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var totalAmountSpent: Double = 0.0
    private var currentCallId: String = ""
    private var callStartTime: Long = 0
    private var callTimer: Timer? = null
    private var isAudioMuted = false
    private var isVideoEnabled = true
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null
    private var isCallActive = false
    private var localUid: Int = 0
    private var remoteUid: Int = 0

    // Modern permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeAgora()
        } else {
            showError("Camera and microphone permissions are required for video calls")
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        currentCallId = intent.getStringExtra("CALL_ID") ?: ""
        if (currentCallId.isEmpty()) {
            Toast.makeText(this, "Call ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeUI()
        checkPermissionsAndInitialize()
        setupCallControls()
        registerBroadcastReceiver()
    }

    private fun initializeUI() {
        binding.tvCallStatus.text = "Connecting..."
        binding.tvTimer.text = "00:00"
        binding.tvPaymentInfo.text = "Rate: ₹60 per minute (₹10/10sec)"
        binding.tvConnectionStatus.text = "Initializing..."

        // Set initial button states
        binding.btnMute.setBackgroundColor(Color.parseColor("#4CAF50"))
        binding.btnMute.setColorFilter(R.color.green)
        binding.btnVideoToggle.setBackgroundColor(Color.parseColor("#4CAF50"))
//        binding.btnVideoToggle.text = "Video On"

        // Make video views visible by default
        binding.localVideoView.visibility = android.view.View.VISIBLE
        binding.remoteVideoView.visibility = android.view.View.VISIBLE
    }

    private fun checkPermissionsAndInitialize() {
        if (hasPermissions()) {
            initializeAgora()
        } else {
            requestPermissions()
        }
    }

    private fun initializeAgora() {
        updateConnectionStatus("Initializing video call...")
        Log.d("VideoCall", "Initializing Agora with App ID: ${AppConstants.AGORA_APP_ID}")

        try {
            agoraManager = AgoraManager(this, this)
            val initialized = agoraManager.initializeAgoraEngine(AppConstants.AGORA_APP_ID)

            if (initialized) {
                updateConnectionStatus("Setting up video...")
                Log.d("VideoCall", "Agora engine initialized successfully")

                // Setup local video FIRST
                localSurfaceView = agoraManager.setupLocalVideo(binding.localVideoView)
                Log.d("VideoCall", "Local video setup completed")

                // Join channel - use the actual channel name from intent if available
                val channelName = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
                Log.d("VideoCall", "Joining channel: $channelName")

                val result = agoraManager.joinChannel(channelName)
                Log.d("VideoCall", "Join channel result: $result")

                if (result == 0) {
                    updateConnectionStatus("Joining call...")
                    updateCallStatusInFirestore("ongoing")

                    // Enable speakerphone for better audio
                    agoraManager.setEnableSpeakerphone(true)
                    Log.d("VideoCall", "Speakerphone enabled")
                } else {
                    showError("Failed to join call: Error $result")
                    Log.e("VideoCall", "Join channel failed with error: $result")
                }
            } else {
                showError("Failed to initialize Agora engine")
                Log.e("VideoCall", "Agora engine initialization failed")
            }
        } catch (e: Exception) {
            Log.e("VideoCall", "Agora initialization error: ${e.message}", e)
            showError("Video call initialization failed: ${e.message}")
        }
    }

    private fun updateCallStatusInFirestore(status: String) {
        val updates = hashMapOf<String, Any>(
            "status" to status,
            "lastUpdated" to Timestamp.now()
        )

        if (status == "ongoing") {
            updates["callStartTime"] = Timestamp.now()
        }

        db.collection("videoCalls")
            .document(currentCallId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.e("VideoCall", "Failed to update call status: ${e.message}")
            }
    }

    private fun startPaymentService() {
        try {
            val serviceIntent = Intent(this, VideoCallService::class.java).apply {
                action = "START_PAYMENT"
                putExtra("CALL_ID", currentCallId)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d("VideoCall", "Payment service started")
        } catch (e: Exception) {
            Log.e("VideoCall", "Failed to start payment service: ${e.message}")
        }
    }

    private fun setupCallControls() {
        binding.btnEndCall.setOnClickListener {
            showEndCallConfirmation()
        }

        binding.btnMute.setOnClickListener {
            toggleAudio()
        }

        binding.btnVideoToggle.setOnClickListener {
            toggleVideo()
        }

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        // NEW: Add speakerphone toggle button
        binding.btnSwitchCamera.setOnLongClickListener {
            toggleSpeakerphone()
            true
        }
    }

    private fun toggleAudio() {
        if (!isCallActive) return

        isAudioMuted = !isAudioMuted
        val result = agoraManager.muteLocalAudio(isAudioMuted)

        if (result == 0) {
            binding.btnMute.setBackgroundColor(
                if (isAudioMuted) Color.RED else Color.parseColor("#4CAF50")
            )
//            binding.btnMute.text = if (isAudioMuted) "Unmute" else "Mute"

            Toast.makeText(
                this,
                if (isAudioMuted) "Microphone muted" else "Microphone unmuted",
                Toast.LENGTH_SHORT
            ).show()
            Log.d("VideoCall", "Audio ${if (isAudioMuted) "muted" else "unmuted"}")
        } else {
            Log.e("VideoCall", "Failed to toggle audio: Error $result")
        }
    }

    private fun toggleVideo() {
        if (!isCallActive) return

        isVideoEnabled = !isVideoEnabled
        val result = agoraManager.enableLocalVideo(isVideoEnabled)

        if (result == 0) {
            binding.btnVideoToggle.setBackgroundColor(
                if (isVideoEnabled) Color.parseColor("#4CAF50") else Color.RED
            )
//            binding.btnVideoToggle.text = if (isVideoEnabled) "Video On" else "Video Off"

            // Show/hide local video view
            binding.localVideoView.visibility = if (isVideoEnabled) android.view.View.VISIBLE else android.view.View.GONE

            Toast.makeText(
                this,
                if (isVideoEnabled) "Video enabled" else "Video disabled",
                Toast.LENGTH_SHORT
            ).show()
            Log.d("VideoCall", "Video ${if (isVideoEnabled) "enabled" else "disabled"}")
        } else {
            Log.e("VideoCall", "Failed to toggle video: Error $result")
        }
    }

    private fun switchCamera() {
        if (!isCallActive) return

        val result = agoraManager.switchCamera()
        if (result == 0) {
            Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
            Log.d("VideoCall", "Camera switched successfully")
        } else {
            Log.e("VideoCall", "Failed to switch camera: Error $result")
        }
    }

    // NEW: Toggle speakerphone function
    private fun toggleSpeakerphone(): Boolean {
        if (!isCallActive) return false

        val currentState = agoraManager.getConnectionState()
        val newState = !isSpeakerphoneEnabled()

        val result = agoraManager.setEnableSpeakerphone(newState)
        if (result == 0) {
            Toast.makeText(
                this,
                if (newState) "Speakerphone ON" else "Speakerphone OFF",
                Toast.LENGTH_SHORT
            ).show()
            Log.d("VideoCall", "Speakerphone ${if (newState) "enabled" else "disabled"}")
            return true
        } else {
            Log.e("VideoCall", "Failed to toggle speakerphone: Error $result")
            return false
        }
    }

    // NEW: Check if speakerphone is enabled
    private fun isSpeakerphoneEnabled(): Boolean {
        // This is a simple implementation - you might need to track this state separately
        return true // Default to true as we enable it initially
    }

    private fun startCallTimer() {
        callTimer?.cancel()
        callTimer = Timer()
        callTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateCallTimer()
                }
            }
        }, 1000, 1000)
    }

    private fun updateCallTimer() {
        val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)

        // Payment UI will be updated by broadcast, so no calculation here
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcastReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("INSUFFICIENT_BALANCE")
                addAction("PAYMENT_CALCULATED") // NEW ACTION
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(balanceReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(balanceReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("VideoCall", "Failed to register broadcast receiver: ${e.message}")
        }
    }

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                "INSUFFICIENT_BALANCE" -> {
                    runOnUiThread {
                        showInsufficientBalanceDialog()
                    }
                }
                "PAYMENT_CALCULATED" -> {
                    // Update local amount only
                    totalAmountSpent = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0)
                    runOnUiThread {
                        updatePaymentUI()
                    }
                }
            }
        }
    }

    private fun updatePaymentUI() {
        val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60

        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
        binding.tvPaymentInfo.text = "Rate: ₹60/min | Spent: ₹${String.format("%.2f", totalAmountSpent)}"
    }


    private fun showInsufficientBalanceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Insufficient Balance")
            .setMessage("Your wallet balance is low. The call will be ended.")
            .setPositiveButton("OK") { dialog, which ->
                endCall()
            }
            .setCancelable(false)
            .show()
    }

    // End call function update karein
    private fun endCall() {
        if (!isCallActive) {
            finish()
            return
        }

        Log.d("VideoCall", "Ending call... Total spent: ₹$totalAmountSpent")
        isCallActive = false

        // Pehle payment service stop karein (ye final update karega)
        stopPaymentService()

        // Phir Agora call end karein
        agoraManager.leaveChannel()

        // Firestore status update karein
        updateCallEndStatus()

        // Show completion message
        Toast.makeText(
            this,
            "Call completed! Total spent: ₹${String.format("%.2f", totalAmountSpent)}",
            Toast.LENGTH_LONG
        ).show()

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000) // Thoda time dein service ko complete karne ke liye
    }
    private fun stopPaymentService() {
        try {
            val serviceIntent = Intent(this, VideoCallService::class.java).apply {
                action = "STOP_PAYMENT"
                putExtra("CALL_ID", currentCallId)
            }
            startService(serviceIntent) // CHANGED: stopService() ki jagah startService() use karein
            Log.d("VideoCall", "STOP_PAYMENT signal sent to service")
        } catch (e: Exception) {
            Log.e("VideoCall", "Failed to stop payment service: ${e.message}")
        }
    }

    // Update call end status
    private fun updateCallEndStatus() {
        val endTime = Timestamp.now()
        val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000

        val updates = hashMapOf<String, Any>(
            "status" to "ended",
            "callEndTime" to endTime,
            "duration" to elapsedSeconds

            // totalAmount service update karega
        )

        db.collection("videoCalls")
            .document(currentCallId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("VideoCall", "Call end status updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("VideoCall", "Failed to update call end status: ${e.message}")
            }
    }

    // Agora Event Listeners
    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
        runOnUiThread {
            isCallActive = true
            localUid = uid
            callStartTime = System.currentTimeMillis()
            startCallTimer()
            startPaymentService()
            updateCallStatus("Connected to call - Your UID: $uid")
            binding.tvConnectionStatus.visibility = android.view.View.GONE

            // Make sure local video is visible
            binding.localVideoView.visibility = android.view.View.VISIBLE

            Log.d("VideoCall", "Successfully joined channel: $channel, UID: $uid, elapsed: $elapsed ms")
            Toast.makeText(this, "Connected to call!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserJoined(uid: Int) {
        runOnUiThread {
            remoteUid = uid
            Log.d("VideoCall", "Remote user joined: $uid")
            remoteSurfaceView = agoraManager.setupRemoteVideo(binding.remoteVideoView, uid)
            updateCallStatus("Advisor joined the call - UID: $uid")
            binding.remoteVideoView.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "Advisor joined the call!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserOffline(uid: Int, reason: Int) {
        runOnUiThread {
            updateCallStatus("Advisor left the call")
            Log.d("VideoCall", "User offline: $uid, reason: $reason")

            // End call after delay
            Handler(Looper.getMainLooper()).postDelayed({
                endCall()
            }, 2000)
        }
    }

    override fun onLeaveChannel(stats: RtcStats) {
        Log.d("VideoCall", "Left channel: duration=${stats.totalDuration}ms, txBytes=${stats.txBytes}, rxBytes=${stats.rxBytes}")
    }

    override fun onError(err: Int) {
        runOnUiThread {
            val errorMsg = when (err) {
                io.agora.rtc2.Constants.ERR_NOT_INITIALIZED -> "SDK not initialized"
                io.agora.rtc2.Constants.ERR_INVALID_ARGUMENT -> "Invalid argument"
                io.agora.rtc2.Constants.ERR_JOIN_CHANNEL_REJECTED -> "Join channel rejected"
                io.agora.rtc2.Constants.ERR_LEAVE_CHANNEL_REJECTED -> "Leave channel rejected"
//                io.agora.rtc2.Constants. -> "Start call error"
                io.agora.rtc2.Constants.ERR_INVALID_APP_ID -> "Invalid App ID"
                else -> "Error code: $err"
            }
            showError("Video call error: $errorMsg")
            Log.e("VideoCall", "Agora error: $errorMsg ($err)")
        }
    }

    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
        runOnUiThread {
            Log.d("VideoCall", "Remote video state changed - UID: $uid, State: $state, Reason: $reason")

            when (state) {
                io.agora.rtc2.Constants.REMOTE_VIDEO_STATE_STARTING -> {
                    updateCallStatus("Advisor video starting...")
                    binding.remoteVideoView.visibility = android.view.View.VISIBLE
                }
                io.agora.rtc2.Constants.REMOTE_VIDEO_STATE_DECODING -> {
                    updateCallStatus("Advisor video active")
                    binding.remoteVideoView.visibility = android.view.View.VISIBLE
                    Toast.makeText(this, "Advisor video is now active", Toast.LENGTH_SHORT).show()
                }
                io.agora.rtc2.Constants.REMOTE_VIDEO_STATE_FROZEN -> {
                    updateCallStatus("Advisor video frozen")
                }
                io.agora.rtc2.Constants.REMOTE_VIDEO_STATE_STOPPED -> {
                    updateCallStatus("Advisor video stopped")
                    binding.remoteVideoView.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun updateCallStatus(status: String) {
        binding.tvCallStatus.text = status
        Log.d("VideoCall", "Call status: $status")
    }


    private fun updateConnectionStatus(status: String) {
        binding.tvConnectionStatus.text = status
    }

    private fun showError(message: String) {
        Log.e("VideoCall", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    private fun showEndCallConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("End Call")
            .setMessage("Are you sure you want to end the call?")
            .setPositiveButton("End Call") { dialog, which ->
                endCall()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        showEndCallConfirmation()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up timers
        callTimer?.cancel()
        callTimer = null

        // Clean up views
        localSurfaceView?.let {
            binding.localVideoView.removeView(it)
        }
        remoteSurfaceView?.let {
            binding.remoteVideoView.removeView(it)
        }

        // Destroy Agora engine
        try {
            agoraManager.destroy()
        } catch (e: Exception) {
            Log.e("VideoCall", "Error destroying Agora: ${e.message}")
        }

        // Unregister receiver
        try {
            unregisterReceiver(balanceReceiver)
        } catch (e: Exception) {
            // Ignore if receiver wasn't registered
        }

        // Stop payment service
        stopPaymentService()

        Log.d("VideoCall", "VideoCallActivity destroyed - Call duration: ${(System.currentTimeMillis() - callStartTime) / 1000}s")
    }
}