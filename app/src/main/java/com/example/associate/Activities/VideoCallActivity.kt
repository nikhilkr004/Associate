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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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

    private lateinit var bookingRepository: com.example.associate.Repositories.BookingRepository
    private var isInstantBooking = false
    private var ratePerMinute = 0.0
    private var userWalletBalance = 0.0
    private var visualTrackerHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        
        bookingRepository = com.example.associate.Repositories.BookingRepository()
        
        // ... (keep existing window flags) ...
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

        // Fetch Wallet Balance immediately
        fetchWalletBalance()

        currentCallId = intent.getStringExtra("CALL_ID") ?: ""
        if (currentCallId.isEmpty()) {
            Toast.makeText(this, "Call ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        callType = "VIDEO"

        initializeUI()
        android.widget.Toast.makeText(this, "Starting ${if(callType=="AUDIO") "Audio" else "Video"} Call...", android.widget.Toast.LENGTH_SHORT).show()
        checkPermissionsAndInitialize()
        setupCallControls()
        registerBroadcastReceiver()
        stopCallNotificationService()
    }

    private fun fetchWalletBalance() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("wallets").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Try getting regular balance, or specific field if different
                    // Usually "balance" or "walletBalance". Repository used "walletBalance".
                    // Let's check WalletDataClass or Repository usage. 
                    // BookingRepository used "walletBalance".
                    userWalletBalance = document.getDouble("walletBalance") ?: document.getDouble("balance") ?: 0.0
                    Log.d("VideoCall", "Wallet Balance fetched: $userWalletBalance")
                }
            }
            .addOnFailureListener { e ->
                Log.e("VideoCall", "Failed to fetch wallet balance: ${e.message}")
            }
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
        binding.tvPaymentInfo.text = "Initializing..."
        binding.tvConnectionStatus.text = "Initializing..."

        binding.localVideoView.visibility = View.VISIBLE
        binding.remoteVideoView.visibility = View.VISIBLE
    }
    
    // ... (keep permissions check) ...
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

                zegoManager.enableCamera(true)  
                     
                val localTextureView = TextureView(this)
                binding.localVideoView.addView(localTextureView)
                zegoManager.setupLocalVideo(localTextureView)
                
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

    // ... (keep updateCallStatusInFirestore) ...
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
                    isInstantBooking = true
                    ratePerMinute = document.getDouble("sessionAmount") ?: 60.0 // Assuming sessionAmount is rate
                    startVisualTracker()
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
                    isInstantBooking = false
                    ratePerMinute = document.getDouble("sessionAmount") ?: 100.0 // Fixed Amount
                    startVisualTracker()
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
             isInstantBooking = true // Fallback assume instant
             ratePerMinute = rate
             startVisualTracker()
        }
    }

    // 🔥 NEW: Visual Cost Tracker (UI Only)
    private fun startVisualTracker() {
        visualTrackerHandler?.removeCallbacksAndMessages(null)
        visualTrackerHandler = Handler(Looper.getMainLooper())
        
        val runnable = object : Runnable {
            override fun run() {
                if (!isCallActive) return

                val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000
                val durationInMinutes = elapsedSeconds / 60.0
                
                val currentCost = if (isInstantBooking) {
                    durationInMinutes * ratePerMinute
                } else {
                    // For scheduled, we might show fixed price or nothing dynamic
                    ratePerMinute // ratePerMinute holds fixed sessionAmount for scheduled
                }
                
                // Update UI
                val formattedCost = String.format("%.2f", currentCost)
                binding.tvPaymentInfo.text = if (isInstantBooking) {
                    "Rate: ₹$ratePerMinute/min | Spent: ₹$formattedCost"
                } else {
                    "Scheduled Session | Fee: ₹$ratePerMinute"
                }

                // Balance Check
                if (isInstantBooking && currentCost >= userWalletBalance) {
                     showInsufficientBalanceDialog()
                     // Stop tracker
                     return
                }

                visualTrackerHandler?.postDelayed(this, 1000)
            }
        }
        visualTrackerHandler?.post(runnable)
    }

    // ... (keep default function) ...
    // Note: Replaces startPaymentService usage with startVisualTracker logic above
    // I will remove startPaymentService or leave it empty if called elsewhere to avoid errors?
    // User requested "Secure Transaction" so we should NOT use the old service for logic.
    private fun startPaymentService(rate: Double = 60.0) {
       // Deprecated: Logic moved to startVisualTracker and endCall transaction
    }
    
    // ... (keep setupCallControls) ...
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

        binding.btnVideoToggle.setImageResource(
            if (isVideoEnabled) R.drawable.camera_turnon else R.drawable.camera_turnoff
        )

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

    // ... (Keep BroadCast Receiver if needed, but we are moving logic to Activity) ...
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcastReceiver() {
        // Can be removed or kept empty if we don't trigger it anymore
    }
    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }

    private fun updatePaymentUI() {
        // Handled by visualTracker
    }

    private fun showInsufficientBalanceDialog() {
        if (!isFinishing) {
             AlertDialog.Builder(this)
            .setTitle("Insufficient Balance")
            .setMessage("Your wallet balance is low. The call will be ended.")
            .setPositiveButton("OK") { dialog, which ->
                endCallWithNavigation()
            }
            .setCancelable(false)
            .show()
        }
    }

    // 🔥 UPDATE: End Call with Secure Transaction
    private fun endCall() {
        if (!isCallActive) {
            finish()
            return
        }

        Log.d("VideoCall", "Ending call...")
        isCallActive = false
        visualTrackerHandler?.removeCallbacksAndMessages(null)

        val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
        try {
            zegoManager.leaveRoom(roomID)
        } catch (e: Exception) { Log.e("VideoCall", "Error leaving room: $e") }

        updateCallEndStatus() // Updates "videoCalls" doc status

        // 🔥 ATOMIC TRANSACTION
        val callDurationSeconds = (System.currentTimeMillis() - callStartTime) / 1000
        val bookingId = intent.getStringExtra("CHANNEL_NAME") ?: ""
        val advisorId = intent.getStringExtra("ADVISOR_ID") ?: remoteAdvisorId // Fallback to captured ID
        val userId = localUserID
        
        if (bookingId.isNotEmpty() && advisorId.isNotEmpty()) {
            Toast.makeText(this, "Processing Secure Payment...", Toast.LENGTH_SHORT).show()
            
            // Invoke Repository
            androidx.lifecycle.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = bookingRepository.completeBookingWithTransaction(
                    bookingId, userId, advisorId, callDurationSeconds, ratePerMinute, isInstantBooking
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@VideoCallActivity, "Payment Successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@VideoCallActivity, "Payment Processing Failed. Please contact support.", Toast.LENGTH_LONG).show()
                    }
                    navigationAfterEnd()
                }
            }
        } else {
             navigationAfterEnd()
        }
    }

    private fun navigationAfterEnd() {
        navigateToHome()
    }
    
    private fun endCallWithNavigation() {
        endCall()
        // navigateToHome is called inside endCall -> transaction scope
    }

    private fun saveRatingToBackend(ratingValue: Float, review: String) {}

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun stopPaymentService() {
        // No-op
    }

    private fun updateCallEndStatus() {
        val endTime = Timestamp.now()
        val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000
        val updates = hashMapOf<String, Any>(
            "status" to "ended",
            "callEndTime" to endTime,
            "duration" to elapsedSeconds
        )
        db.collection("videoCalls").document(currentCallId).update(updates)
    }
    
    // Kept for compatibility but logic moved to Transaction
    private fun updateBookingStatusToEnded() {}

    // ... (Keep Zego Listeners) ...
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
                    remoteAdvisorId = user.userID
                    updateCallStatus("Advisor joined: ${user.userName}")
                    Toast.makeText(this, "Advisor joined!", Toast.LENGTH_SHORT).show()
                    
                    val streamID = "stream_${user.userID}"
                    if (binding.remoteVideoView.visibility == View.VISIBLE) {
                        val remoteTextureView = TextureView(this)
                        binding.remoteVideoView.removeAllViews()
                        binding.remoteVideoView.addView(remoteTextureView)
                        zegoManager.setupRemoteVideo(remoteTextureView, streamID)
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

    override fun onRemoteCameraStateUpdate(streamID: String, state: Int) {}

    private fun updateCallStatus(status: String) {
        Log.d("VideoCall", "Call status: $status")
    }

    private fun updateConnectionStatus(status: String) {
        binding.tvConnectionStatus.text = status
    }

    private fun showError(message: String) {
        Log.e("VideoCall", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun showEndCallConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("End Call")
            .setMessage("Are you sure you want to end the call?")
            .setPositiveButton("End Call") { dialog, which -> endCallWithNavigation() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() { showEndCallConfirmation() }

    override fun onDestroy() {
        super.onDestroy()
        callTimer?.cancel()
        callTimer = null
        visualTrackerHandler?.removeCallbacksAndMessages(null)
        try {
            val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
            zegoManager.leaveRoom(roomID)
        } catch (e: Exception) {}
        try { unregisterReceiver(balanceReceiver) } catch (e: Exception) {}
    }
}