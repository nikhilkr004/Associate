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
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.AppConstants
import com.example.associate.PreferencesHelper.ZegoCallManager
import com.example.associate.R
import com.example.associate.Repositorys.VideoCallService
import com.example.associate.databinding.ActivityVideoCallBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoUser
import org.json.JSONObject
import java.util.ArrayList
import java.util.Timer
import java.util.TimerTask

class VideoCallActivity : AppCompatActivity(), ZegoCallManager.ZegoCallListener {

    private val binding by lazy {
        ActivityVideoCallBinding.inflate(layoutInflater)
    }

    private lateinit var zegoManager: ZegoCallManager
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var totalAmountSpent: Double = 0.0
    private var currentCallId: String = ""
    private var callStartTime: Long = 0
    private var callTimer: Timer? = null
    private var isAudioMuted = false
    private var isVideoEnabled = true
    private var isCallActive = false
    private var localUserID: String = ""
    private var localUserName: String = ""

    // Modern permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeZego()
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
        
        // Generate a random User ID and Name if not available (or use Auth)
        localUserID = auth.currentUser?.uid ?: "user_${System.currentTimeMillis()}"
        localUserName = auth.currentUser?.displayName ?: "User"

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
        stopCallNotificationService()
    }

    private fun stopCallNotificationService() {
        try {
            val intent = Intent(this, com.example.associate.NotificationFCM.CallNotificationService::class.java).apply {
                action = com.example.associate.NotificationFCM.CallNotificationService.ACTION_STOP_SERVICE
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("VideoCall", "Failed to stop notification service: ${e.message}")
        }
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

        // Make video views visible by default
        binding.localVideoView.visibility = View.VISIBLE
        binding.remoteVideoView.visibility = View.VISIBLE
    }

    private fun checkPermissionsAndInitialize() {
        if (hasPermissions()) {
            initializeZego()
        } else {
            requestPermissions()
        }
    }

    private fun initializeZego() {
        updateConnectionStatus("Initializing video call...")
        Log.d("VideoCall", "Initializing Zego with App ID: ${AppConstants.ZEGO_APP_ID}")

        try {
            zegoManager = ZegoCallManager(this, this)
            val initialized = zegoManager.initializeEngine(AppConstants.ZEGO_APP_ID, AppConstants.ZEGO_APP_SIGN)

            if (initialized) {
                updateConnectionStatus("Setting up video...")
                Log.d("VideoCall", "Zego engine initialized successfully")

                // Setup local video
                val localTextureView = TextureView(this)
                binding.localVideoView.addView(localTextureView)
                zegoManager.setupLocalVideo(localTextureView)
                Log.d("VideoCall", "Local video setup completed")

                // Join room - use the actual channel name (roomID) from intent if available
                val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
                Log.d("VideoCall", "Joining room: $roomID")

                zegoManager.joinRoom(roomID, localUserID, localUserName)
                
                // We assume successful join request initiated
                updateConnectionStatus("Joining call...")
                updateCallStatusInFirestore("ongoing")
                
                // Start timer and payment service immediately or wait for onRoomStateChanged
                // For Zego, loginRoom is async but we can assume start. 
                // Better to wait for callback but for simplicity we start tracking.
                isCallActive = true
                callStartTime = System.currentTimeMillis()
                startCallTimer()
                startPaymentService()
                
            } else {
                showError("Failed to initialize Zego engine")
                Log.e("VideoCall", "Zego engine initialization failed")
            }
        } catch (e: Exception) {
            Log.e("VideoCall", "Zego initialization error: ${e.message}", e)
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
    }

    private fun toggleAudio() {
        if (!isCallActive) return

        isAudioMuted = !isAudioMuted
        zegoManager.muteMicrophone(isAudioMuted)

        binding.btnMute.setBackgroundColor(
            if (isAudioMuted) Color.RED else Color.parseColor("#4CAF50")
        )

        Toast.makeText(
            this,
            if (isAudioMuted) "Microphone muted" else "Microphone unmuted",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleVideo() {
        if (!isCallActive) return

        isVideoEnabled = !isVideoEnabled
        zegoManager.enableCamera(isVideoEnabled)

        binding.btnVideoToggle.setBackgroundColor(
            if (isVideoEnabled) Color.parseColor("#4CAF50") else Color.RED
        )

        // Show/hide local video view
        binding.localVideoView.visibility = if (isVideoEnabled) View.VISIBLE else View.GONE

        Toast.makeText(
            this,
            if (isVideoEnabled) "Video enabled" else "Video disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun switchCamera() {
        if (!isCallActive) return
        zegoManager.switchCamera()
        Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
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
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcastReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("INSUFFICIENT_BALANCE")
                addAction("PAYMENT_CALCULATED")
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

    private fun endCall() {
        if (!isCallActive) {
            finish()
            return
        }

        Log.d("VideoCall", "Ending call... Total spent: ₹$totalAmountSpent")
        isCallActive = false

        stopPaymentService()
        
        val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
        zegoManager.leaveRoom(roomID)

        updateCallEndStatus()

        Toast.makeText(
            this,
            "Call completed! Total spent: ₹${String.format("%.2f", totalAmountSpent)}",
            Toast.LENGTH_LONG
        ).show()

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
    }

    private fun stopPaymentService() {
        try {
            val serviceIntent = Intent(this, VideoCallService::class.java).apply {
                action = "STOP_PAYMENT"
                putExtra("CALL_ID", currentCallId)
            }
            startService(serviceIntent)
            Log.d("VideoCall", "STOP_PAYMENT signal sent to service")
        } catch (e: Exception) {
            Log.e("VideoCall", "Failed to stop payment service: ${e.message}")
        }
    }

    private fun updateCallEndStatus() {
        val endTime = Timestamp.now()
        val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000

        val updates = hashMapOf<String, Any>(
            "status" to "ended",
            "callEndTime" to endTime,
            "duration" to elapsedSeconds
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

    // Zego Event Listeners
    override fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: JSONObject) {
        runOnUiThread {
            if (errorCode == 0) {
                updateCallStatus("Connected to room: $roomID")
                binding.tvConnectionStatus.visibility = View.GONE
            } else {
                showError("Room state changed error: $errorCode")
            }
        }
    }

    override fun onRoomUserUpdate(roomID: String, updateType: ZegoUpdateType, userList: ArrayList<ZegoUser>) {
        runOnUiThread {
            if (updateType == ZegoUpdateType.ADD) {
                for (user in userList) {
                    Log.d("VideoCall", "User joined: ${user.userID}")
                    updateCallStatus("Advisor joined: ${user.userName}")
                    Toast.makeText(this, "Advisor joined!", Toast.LENGTH_SHORT).show()
                    
                    // In Zego, we play stream when publisher updates, but if we know the stream ID convention:
                    val streamID = "stream_${user.userID}"
                    val remoteTextureView = TextureView(this)
                    binding.remoteVideoView.removeAllViews() // Clear previous
                    binding.remoteVideoView.addView(remoteTextureView)
                    zegoManager.setupRemoteVideo(remoteTextureView, streamID)
                    binding.remoteVideoView.visibility = View.VISIBLE
                }
            } else if (updateType == ZegoUpdateType.DELETE) {
                for (user in userList) {
                    Log.d("VideoCall", "User left: ${user.userID}")
                    updateCallStatus("Advisor left the call")
                    Handler(Looper.getMainLooper()).postDelayed({
                        endCall()
                    }, 2000)
                }
            }
        }
    }

    override fun onRemoteCameraStateUpdate(streamID: String, state: Int) {
        // Handle remote camera state changes if needed
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
        callTimer?.cancel()
        callTimer = null
        
        try {
            val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
            zegoManager.leaveRoom(roomID)
        } catch (e: Exception) {
            Log.e("VideoCall", "Error destroying Zego: ${e.message}")
        }

        try {
            unregisterReceiver(balanceReceiver)
        } catch (e: Exception) {}

        stopPaymentService()
    }
}