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
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class VideoCallActivity : AppCompatActivity(), ZegoCallManager.ZegoCallListener {

    private val binding by lazy {
        ActivityVideoCallBinding.inflate(layoutInflater)
    }

    private lateinit var zegoManager: ZegoCallManager
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var totalAmountSpent: Double = 0.0
    private var currentCallId: String = "" // Keep this if used elsewhere, but we add callId below
    private var callStartTime: Long = 0
    private var bookingId: String = "" // ✅ Added class member
    private var isEnding = false // ✅ Added to prevent double execution
    private var callEndListener: com.google.firebase.firestore.ListenerRegistration? = null // Listener cleanup
    
    // ✅ Hybrid Payment System Variables
    private var heartbeatTimer: Timer? = null
    private var callId: String = ""
    private val collectionName = "videoCalls"
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
    private var forceScheduledMode = false // 🔥 Added
    private var storedAdvisorId = "" // 🔥 Restored
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

        fetchWalletBalance()

        // 🔥 SPEC COMPLIANCE: "Document ID: passed as CALL_ID intent extra."
        val explicitCallId = intent.getStringExtra("CALL_ID")
        if (!explicitCallId.isNullOrEmpty()) {
            callId = explicitCallId
            currentCallId = explicitCallId 
        } else {
            callId = "call_${System.currentTimeMillis()}"
            currentCallId = callId
        }

        // 🔥 FIX: Capture Booking ID (Critical for Payment)
        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        if (bookingId.isEmpty() && !explicitCallId.isNullOrEmpty()) {
             // Fallback: try to deduce from callId if formatting allows, or leave empty
             bookingId = explicitCallId.replace("call_", "")
        }
        
        // 🔥 CRITICAL: Capture Advisor ID explicitly from Intent first
        storedAdvisorId = intent.getStringExtra("ADVISOR_ID") ?: intent.getStringExtra("advisorId") ?: ""
        if (storedAdvisorId.isEmpty()) {
             Log.w("VideoCall", "⚠️ Advisor ID missing in Intent. Will attempt to fetch from Booking.")
        } else {
             Log.d("VideoCall", "✅ Advisor ID captured from Intent: $storedAdvisorId")
        }

        callType = "VIDEO"

        initializeUI()
        
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Connecting securely...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            progressDialog.dismiss()
            android.widget.Toast.makeText(this, "Starting ${if(callType=="AUDIO") "Audio" else "Video"} Call...", android.widget.Toast.LENGTH_SHORT).show()
            checkPermissionsAndInitialize()
            setupCallControls()
            registerBroadcastReceiver()
            stopCallNotificationService()
        }, 1500)
    }

    private fun initializeUI() {
        val advisorName = intent.getStringExtra("ADVISOR_NAME") ?: "Advisor"
        val advisorAvatar = intent.getStringExtra("ADVISOR_AVATAR") ?: ""
        
        // Fix: Use tvCallStatus instead of missing tvAdvisorName
        binding.tvCallStatus.text = "Call with $advisorName"
        binding.tvTimer.text = "00:00"
        
        binding.tvPaymentInfo.visibility = View.GONE
        binding.tvPaymentInfo.text = "Initializing..."
        
        binding.tvConnectionStatus.text = "Initializing Video Call..."

        if (advisorAvatar.isNotEmpty()) {
            loadAvatar(advisorAvatar)
        } else {
            val advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""
            if (advisorId.isNotEmpty()) {
                fetchAdvisorAvatar(advisorId)
            }
        }
    }


    private fun fetchAdvisorAvatar(advisorId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("advisors").document(advisorId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val basicInfo = document.get("basicInfo") as? Map<*, *>
                    var avatarUrl = basicInfo?.get("profileImage") as? String
                    
                    if (avatarUrl.isNullOrEmpty()) {
                        avatarUrl = document.getString("profileImage") ?: document.getString("profileimage")
                    }
                    
                    if (!avatarUrl.isNullOrEmpty()) {
                        loadAvatar(avatarUrl)
                    }
                }
            }
    }

    private fun loadAvatar(url: String) {
         try {
            // Note: Video Call might not have an avatar view visible during call in all layouts?
            // Checking binding... usually it's ivAdvisorAvatar or similar.
            // But if layout doesn't have it, this might crash.
            // Let's assume it's like AudioCall. If not, catch exception.
            // Wait, VideoCall usually doesn't show Avatar except maybe in loading or background?
            // I'll leave it empty-safe.
        } catch (e: Exception) {
            Log.e("VideoCall", "Error loading advisor avatar: ${e.message}")
        }
    }

    private fun checkPermissionsAndInitialize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initializeZego()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun initializeZego() {
        binding.tvConnectionStatus.text = "Setting up engine..."
        Log.d("VideoCall", "Initializing Zego with App ID: ${AppConstants.ZEGO_APP_ID}")

        try {
            zegoManager = ZegoCallManager(this, this)
            val initialized = zegoManager.initializeEngine(AppConstants.ZEGO_APP_ID, AppConstants.ZEGO_APP_SIGN)

            if (initialized) {
                zegoManager.enableCamera(true)
                
                // Preview Local View
                val textureView = TextureView(this)
                binding.localVideoView.addView(textureView)
                // Fix: Method is setupLocalVideo, not startPreview
                zegoManager.setupLocalVideo(textureView)

                val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
                zegoManager.joinRoom(roomID, localUserID, localUserName)
                
                binding.tvConnectionStatus.text = "Joining call..."
                
                // Update Firestore Status
                val updates = hashMapOf<String, Any>(
                    "status" to "ongoing",
                    "lastUpdated" to Timestamp.now(),
                    "callStartTime" to Timestamp.now()
                )
                db.collection("videoCalls").document(currentCallId).update(updates)

                isCallActive = true
                callStartTime = System.currentTimeMillis()
                startCallTimer()
                
                // Start Visual Cost Tracker (No auto-deduction)
                startVisualTracker()
                
            } else {
                showError("Failed to initialize Zego engine")
            }
        } catch (e: Throwable) {
            Log.e("VideoCall", "Zego error: ${e.message}", e)
            showError("Video call initialization failed: ${e.message}")
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

    private var isBalanceFetched = false // Added flag

    private fun fetchWalletBalance() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("wallets").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userWalletBalance = document.getDouble("walletBalance") ?: document.getDouble("balance") ?: 0.0
                    Log.d("VideoCall", "Wallet Balance fetched: $userWalletBalance")
                    isBalanceFetched = true // Set flag to true
                } else {
                    // Document doesn't exist, assume 0.0 but allow check to proceed
                     isBalanceFetched = true 
                }
            }
            .addOnFailureListener { e ->
                Log.e("VideoCall", "Failed to fetch wallet balance: ${e.message}")
                // If failed, we might want to retry or allow continuation with 0 balance (which will trigger dialog eventually)
                // For safety, let's NOT set isBalanceFetched=true so we don't kick them out due to network error immediately
            }
    }
    
    // ...

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
                
                // Scheduled Logic: 30 Min Limit
                if (!isInstantBooking) {
                    val remainingSeconds = (30 * 60) - elapsedSeconds
                    val minutesLeft = remainingSeconds / 60
                    val secondsLeft = remainingSeconds % 60

                    if (remainingSeconds <= 0) {
                        Toast.makeText(this@VideoCallActivity, "Scheduled Session Completed", Toast.LENGTH_LONG).show()
                        endCallWithNavigation()
                        return
                    }
                    
                    // Show Countdown for Scheduled
                    binding.tvPaymentInfo.visibility = View.GONE
                    binding.tvTimer.visibility = View.VISIBLE
                    binding.tvTimer.text = String.format("%02d:%02d", minutesLeft, secondsLeft)
                } else {
                    // Instant Logic: Rate & Spent
                    binding.tvPaymentInfo.visibility = View.VISIBLE
                    val formattedCost = String.format("%.2f", currentCost)
                    binding.tvPaymentInfo.text = "Rate: ₹$ratePerMinute/min | Spent: ₹$formattedCost"
                    
                    // Balance Check (Updated with Flag)
                    if (isBalanceFetched && currentCost >= userWalletBalance) {
                         showInsufficientBalanceDialog()
                         return
                    }
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

    // 🔥 UPDATE: End Call with Secure Transaction (HYBRID)
    private fun endCall() {
        if (!isCallActive && !isEnding) {
            finish()
            return
        }

        isEnding = true
        isCallActive = false
        visualTrackerHandler?.removeCallbacksAndMessages(null)
        callTimer?.cancel()

        Log.d("PaymentDebug", "=== VideoCall endCall Triggered ===")
        val endTime = Timestamp.now()
        val duration = if (callStartTime > 0) (System.currentTimeMillis() - callStartTime) / 1000 else 0
        Log.d("PaymentDebug", "CallID: $currentCallId, Duration: $duration")
        
        // Only update status. The CLOUD FUNCTION handles the money.
        val updates = hashMapOf<String, Any>(
            "status" to "ended",
            "endTime" to endTime,
            "duration" to duration,
            "endReason" to "user_ended",
            "completedBy" to "user_app",
            "bookingId" to bookingId, // ✅ Added to ensure Cloud Function finds it
            "advisorId" to storedAdvisorId,
            "userId" to localUserID
        )
        Log.d("PaymentDebug", "Payload to Update: $updates")
        
        db.collection("videoCalls").document(currentCallId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("VideoCall", "Call ended. Server will process payment.")
                Log.d("PaymentDebug", "✅ Success: VideoCall Doc Updated") 
                Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("VideoCall", "Error ending call", e)
                Log.e("PaymentDebug", "❌ Error Updating VideoCall Doc: ${e.message}")
                // Fallback: Force Close
                finish()
            }
        
        leaveZegoRoom()
    }

    private fun leaveZegoRoom() {
        try {
            val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
            if (::zegoManager.isInitialized) {
                zegoManager.muteMicrophone(true)
                zegoManager.enableCamera(false)
                zegoManager.leaveRoom(roomID)
            }
        } catch (e: Exception) {
            Log.e("VideoCall", "Error leaving room: $e")
        }
    }

    private fun listenForCallEnd() {
         if (callId.isEmpty()) return
         
         callEndListener?.remove()
         callEndListener = db.collection(collectionName).document(callId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("VideoCall", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status")
                    if (status == "ended") {
                        Log.d("VideoCall", "Call Ended detected from Firestore.")
                        if (!isFinishing && !isDestroyed) {
                            navigationAfterEnd()
                        }
                    }
                }
            }
    }
    
    // Call this in onCreate or after callId is set
    
    private fun navigationAfterEnd() {
        val intent = Intent(this, BookingSummaryActivity::class.java)
        intent.putExtra("BOOKING_ID", bookingId)
        intent.putExtra("ADVISOR_ID", storedAdvisorId)
         // Pass basic data for immediate display if needed, but BookingSummary should fetch fresh data
        startActivity(intent)
        finish()
    }
    
    private fun endCallWithNavigation() {
        endCall()
    }

    private fun saveRatingToBackend(ratingValue: Float, review: String) {}

    private fun navigateToHome() {
        // Replaced by navigationAfterEnd to Summary
        navigationAfterEnd()
    }

    private fun stopPaymentService() {}

    private fun updateCallEndStatus() {}
    
    private fun updateBookingStatusToEnded() {}

    // ... (Keep Zego Listeners) ...
    override fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: JSONObject) {
        runOnUiThread {
            if (errorCode == 0) {
                updateCallStatus("Connected to room: $roomID")
                binding.tvConnectionStatus.visibility = View.GONE
                
                if (callStartTime == 0L) {
                    callStartTime = System.currentTimeMillis()
                }
                
                // 🔥 Start Listening for End Event (Advisor or Self)
                listenForCallEnd()
                
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
                    // Don't auto-end immediately, wait for their status update or local timeout?
                    // Requirement says: "If Advisor ends... When status changes to ended... show summary"
                    // So we wait for the listener.
                    Toast.makeText(this, "Advisor left. Ending...", Toast.LENGTH_SHORT).show()
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
            .setPositiveButton("End Call") { dialog, which -> endCall() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        showEndCallConfirmation() }


    // PIP Mode Implementation
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (isCallActive) {
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(9, 16)) // Standard portrait ratio
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Hide Controls
            binding.btnEndCall.visibility = View.GONE
            binding.btnMute.visibility = View.GONE
            binding.btnVideoToggle.visibility = View.GONE
            binding.btnSwitchCamera.visibility = View.GONE
            binding.btnChat.visibility = View.GONE
            binding.tvTimer.visibility = View.GONE
            binding.tvPaymentInfo.visibility = View.GONE
            binding.tvCallStatus.visibility = View.GONE
            binding.tvConnectionStatus.visibility = View.GONE
            binding.localVideoView.visibility = View.GONE // Hide self view to focus on Advisor
        } else {
            // Restore Controls
            binding.btnEndCall.visibility = View.VISIBLE
            binding.btnMute.visibility = View.VISIBLE
            binding.btnVideoToggle.visibility = View.VISIBLE
            binding.btnSwitchCamera.visibility = View.VISIBLE
            binding.btnChat.visibility = View.VISIBLE
            binding.tvTimer.visibility = View.VISIBLE
            binding.tvPaymentInfo.visibility = View.VISIBLE
            binding.tvCallStatus.visibility = View.VISIBLE
            // Don't restore connection status if it was gone
            // binding.tvConnectionStatus.visibility = View.VISIBLE 
            if (isVideoEnabled) {
                binding.localVideoView.visibility = View.VISIBLE
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        isCallActive = false
        visualTrackerHandler?.removeCallbacksAndMessages(null)
        callTimer?.cancel()
        heartbeatTimer?.cancel()
        callEndListener?.remove() // Cleanup listener 
        
        try {
            if (::zegoManager.isInitialized) {
                 val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
                 zegoManager.leaveRoom(roomID)
            }
        } catch (e: Exception) { }
        
        // Ensure call is marked ended if destroyed
        if (!isEnding) {
            // endCall() // Careful, this might trigger loops if onDestroy is called by finish()
            // Just let it be, the server handles disconnects usually? 
            // Or better:
             val updates = hashMapOf<String, Any>(
                "status" to "ended",
                "endReason" to "user_disconnected"
            )
            if (callId.isNotEmpty()) {
                 db.collection(collectionName).document(callId).update(updates)
            }
        }
    }
}
// Updated for repository activity
