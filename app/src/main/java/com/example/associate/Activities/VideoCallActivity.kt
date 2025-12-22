package com.example.associate.Activities

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
import com.example.associate.Repositories.RatingRepository
import com.example.associate.Repositories.VideoCallService
import com.example.associate.databinding.ActivityVideoCallBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoUser
import org.json.JSONObject
import java.util.ArrayList
import android.app.ProgressDialog
import android.net.Uri
import com.example.associate.Fragments.ChatBottomSheetFragment
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
    private var callType: String = "VIDEO"


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
        // ... (truncated for brevity, keep existing code) ...
        
        // Window flags (keep existing)
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
        
        localUserID = auth.currentUser?.uid ?: "user_${System.currentTimeMillis()}"
        localUserName = auth.currentUser?.displayName ?: "User"

        currentCallId = intent.getStringExtra("CALL_ID") ?: ""
        if (currentCallId.isEmpty()) {
            Toast.makeText(this, "Call ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Call Type is now handled by separate activities, default to VIDEO here for safety
        callType = "VIDEO"

        initializeUI()
        android.widget.Toast.makeText(this, "Starting ${if(callType=="AUDIO") "Audio" else "Video"} Call...", android.widget.Toast.LENGTH_SHORT).show()
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
        // ... keep existing UI init code ...
        binding.tvTimer.text = "00:00"
        binding.tvPaymentInfo.text = ""
        binding.tvConnectionStatus.text = "Initializing..."

        // VIDEO MODE - Ensure Video Views are Visible
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
        updateConnectionStatus("Initializing ${callType.lowercase()} call...")
        Log.d("VideoCall", "Initializing Zego with App ID: ${AppConstants.ZEGO_APP_ID}")

        try {
            zegoManager = ZegoCallManager(this, this)
            val initialized = zegoManager.initializeEngine(AppConstants.ZEGO_APP_ID, AppConstants.ZEGO_APP_SIGN)

            if (initialized) {
                updateConnectionStatus("Setting up engine...")
                Log.d("VideoCall", "Zego engine initialized successfully")

                // NEW: Configure Camera based on Type
                zegoManager.enableCamera(true)  // Enable Camera
                     
                // Setup local video only for Video Call
                val localTextureView = TextureView(this)
                binding.localVideoView.addView(localTextureView)
                zegoManager.setupLocalVideo(localTextureView)
                // ... join room code
                val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
                zegoManager.joinRoom(roomID, localUserID, localUserName)
                
                updateConnectionStatus("Joining call...")
                updateCallStatusInFirestore("ongoing")
                isCallActive = true
                callStartTime = System.currentTimeMillis()
                startCallTimer()
                fetchBookingDetailsAndStartService()
            } else {
                showError("Failed to initialize Zego engine")
            }
        } catch (e: Throwable) {
            Log.e("VideoCall", "Zego error: ${e.message}", e)
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

    private fun fetchBookingDetailsAndStartService() {
        val bookingId = intent.getStringExtra("CHANNEL_NAME") ?: return
        
        // Try Instant Bookings first
        db.collection("instant_bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rate = document.getDouble("sessionAmount") ?: 60.0
                    startPaymentService(rate)
                } else {
                    // Try Scheduled Bookings
                    fetchScheduledBooking(bookingId)
                }
            }
            .addOnFailureListener {
                Log.e("VideoCall", "Failed to fetch Instant booking. Trying fallback.")
                fetchAdvisorRateFromProfile()
            }
    }

    private fun fetchScheduledBooking(bookingId: String) {
        db.collection("scheduled_bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rate = document.getDouble("sessionAmount") ?: 60.0
                    startPaymentService(rate)
                } else {
                     Log.e("VideoCall", "Booking not found in Scheduled either. Fetching Advisor Rate.")
                     fetchAdvisorRateFromProfile()
                }
            }
            .addOnFailureListener {
                 fetchAdvisorRateFromProfile()
            }
    }

    private fun fetchAdvisorRateFromProfile() {
        val advisorId = intent.getStringExtra("ADVISOR_ID")
        val repository = com.example.associate.Repositories.AdvisorRepository()

        repository.fetchInstantRate(advisorId ?: "", "VIDEO") { rate ->
             Log.d("VideoCall", "Fetched dynamic rate from Advisor Profile: $rate")
             startPaymentService(rate)
        }
    }

    private fun startPaymentService(rate: Double = 60.0) {
        try {
            // Update UI with fetched rate
//            binding.tvPaymentInfo.text = "Rate: ₹$rate/min"
            
            val serviceIntent = Intent(this, VideoCallService::class.java).apply {
                action = "START_PAYMENT"
                putExtra("CALL_ID", currentCallId)
                putExtra("RATE_PER_MINUTE", rate)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d("VideoCall", "Payment service started with Rate: $rate")
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
        
        binding.btnVideoToggle.visibility = View.VISIBLE
        binding.btnSwitchCamera.visibility = View.VISIBLE
            
        binding.btnVideoToggle.setOnClickListener {
            toggleVideo()
        }

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        binding.btnChat.setOnClickListener {
            val chatBottomSheet = ChatBottomSheetFragment.newInstance(currentCallId)
            chatBottomSheet.show(supportFragmentManager, "ChatBottomSheet")
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
                endCallWithNavigation()
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

    private fun endCallWithNavigation() {
        endCall()
        navigateToHome()
    }

    private fun saveRatingToBackend(ratingValue: Float, review: String) {
         // Rating feature removed as per request
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        // Smooth animation (fade in/out) as requested
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
            .addOnCompleteListener { task ->
                 if (task.isSuccessful) {
                     Log.d("VideoCall", "Call end status updated successfully")
                 } else {
                     Log.e("VideoCall", "Failed to update call end status: ${task.exception?.message}")
                 }
                 // Always try to update booking status
                 updateBookingStatusToEnded()
            }
    }

    private fun updateBookingStatusToEnded() {
        val bookingId = intent.getStringExtra("CHANNEL_NAME") ?: return
        Log.d("VideoCall", "Attempting to update booking status for ID: $bookingId")
        
        // We need to find if it's instant or scheduled to update the correct collection
        // 1. Try Instant
        val instantRef = db.collection("instant_bookings").document(bookingId)
        instantRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                instantRef.update("bookingStatus", "ended")
                    .addOnSuccessListener { Log.d("VideoCall", "Instant Booking status updated to 'ended'") }
                    .addOnFailureListener { e -> Log.e("VideoCall", "Failed to update Instant Booking: ${e.message}") }
            } else {
                // 2. Try Scheduled
                val scheduledRef = db.collection("scheduled_bookings").document(bookingId)
                scheduledRef.get().addOnSuccessListener { schedDoc ->
                   if (schedDoc.exists()) {
                       scheduledRef.update("bookingStatus", "ended")
                           .addOnSuccessListener { Log.d("VideoCall", "Scheduled Booking status updated to 'ended'") }
                   } else {
                       Log.w("VideoCall", "Booking ID not found in instant or scheduled: $bookingId")
                   }
                }
            }
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
                    
                    // Only setup remote video if we are in VIDEO mode (checked via visibility)
                    if (binding.remoteVideoView.visibility == View.VISIBLE) {
                        val remoteTextureView = TextureView(this)
                        binding.remoteVideoView.removeAllViews() // Clear previous
                        binding.remoteVideoView.addView(remoteTextureView)
                        zegoManager.setupRemoteVideo(remoteTextureView, streamID)
                        // binding.remoteVideoView.visibility = View.VISIBLE // Already visible in Video Mode
                    }
                }
            } else if (updateType == ZegoUpdateType.DELETE) {
                for (user in userList) {
                    Log.d("VideoCall", "User left: ${user.userID}")
                    updateCallStatus("Advisor left the call")
                    Handler(Looper.getMainLooper()).postDelayed({
                        endCallWithNavigation()
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
                endCallWithNavigation()
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