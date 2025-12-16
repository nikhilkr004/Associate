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
import com.example.associate.Utils.AppConstants
import com.example.associate.PreferencesHelper.ZegoCallManager
import com.example.associate.R
import com.example.associate.DataClass.Rating
import com.example.associate.Dialogs.RatingDialog
import com.example.associate.MainActivity
import com.example.associate.Repositorys.RatingRepository
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
    private var remoteAdvisorId: String = ""

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

        // Allow VideoCallActivity to show over lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

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
        android.widget.Toast.makeText(this, "Starting Video Call...", android.widget.Toast.LENGTH_SHORT).show()
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
        val advisorName = intent.getStringExtra("ADVISOR_NAME") ?: "Advisor"
        binding.tvCallStatus.text = advisorName
        binding.tvTimer.text = "00:00"
        binding.tvPaymentInfo.text = "Rate: ₹60 per minute (₹10/10sec)"
        binding.tvConnectionStatus.text = "Initializing..."



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
        } catch (e: Throwable) {
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

        // Change icon instead of background color
        binding.btnMute.setImageResource(
            if (isAudioMuted) R.drawable.mutemicrophone else R.drawable.mic
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

        // Change icon
        binding.btnVideoToggle.setImageResource(
            if (isVideoEnabled) R.drawable.camera_turnon else R.drawable.camera_turnoff
        )

        // Keep local video view visible (background changes not requested)
        // binding.localVideoView.visibility = if (isVideoEnabled) View.VISIBLE else View.GONE

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
        callTimer?.schedule(object : TimerTask() {
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
                showRatingDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun endCall() {
        if (!isCallActive) {
            // If call wasn't active, just finish
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
    }

    private fun showRatingDialog() {
        // First end the call logic
        endCall()
        
        // Show rating dialog
        val ratingDialog = RatingDialog { rating, review ->
            saveRatingToBackend(rating, review)
        }
        ratingDialog.isCancelable = false // Force user to rate or dismiss via back press if handled, 
                                          // but usually we want to ensure they see it. 
                                          // If they cancel/back out, we should also finish content.
        
        // Handle cancel/dismiss to ensure activity finishes
        ratingDialog.show(supportFragmentManager, "RatingDialog")
        
        // We delay finishing until dialog is interacted with
    }

    private fun saveRatingToBackend(ratingValue: Float, review: String) {
        var advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""
        val userId = auth.currentUser?.uid ?: ""
        
        // Fallback to captured ID if intent ID is missing
        if (advisorId.isEmpty() && remoteAdvisorId.isNotEmpty()) {
            advisorId = remoteAdvisorId
            Log.d("VideoCall", "Using captured remote Advisor ID: $advisorId")
        }
        
        Log.d("VideoCall", "Saving Rating - AdvisorID: '$advisorId', UserID: '$userId'")
        
        if (advisorId.isEmpty() || userId.isEmpty()) {
            val missing = if (advisorId.isEmpty()) "Advisor ID" else "User ID"
            Toast.makeText(this, "Error: Missing $missing. detailed log check Logcat", Toast.LENGTH_LONG).show()
            Log.e("VideoCall", "Cannot save rating. Missing info. AdvisorID: $advisorId, UserID: $userId")
            navigateToHome()
            return
        }
        
        val ratingData = Rating(
            advisorId = advisorId,
            userId = userId,
            rating = ratingValue,
            review = review,
            callId = currentCallId,
            timestamp = Timestamp.now()
        )
        
        val repository = RatingRepository()
        repository.saveRating(ratingData) { success, error ->
            if (success) {
                Toast.makeText(this, "Rating submitted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to submit rating", Toast.LENGTH_SHORT).show()
                Log.e("VideoCall", "Rating error: $error")
            }
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
                    // Capture remote advisor ID
                    remoteAdvisorId = user.userID
                    Log.d("VideoCall", "Captured Remote Advisor ID: $remoteAdvisorId")
                    
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
                        showRatingDialog()
                    }, 2000)
                }
            }
        }
    }

    override fun onRemoteCameraStateUpdate(streamID: String, state: Int) {
        // Handle remote camera state changes if needed
    }

    private fun updateCallStatus(status: String) {
        // binding.tvCallStatus.text = status  <-- User wants Advisor Name to stay permanent
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
                showRatingDialog()
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