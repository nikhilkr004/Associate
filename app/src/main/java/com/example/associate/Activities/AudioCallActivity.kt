package com.example.associate.Activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.associate.DataClass.Rating
import com.example.associate.MainActivity
import com.example.associate.PreferencesHelper.ZegoCallManager
import com.example.associate.R
import com.example.associate.Repositories.RatingRepository
import com.example.associate.Repositories.VideoCallService
import com.example.associate.Utils.AppConstants
import com.example.associate.databinding.ActivityAudioCallBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoUser
import org.json.JSONObject
import java.util.ArrayList
import android.app.ProgressDialog
import java.util.Timer
import java.util.TimerTask
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class AudioCallActivity : AppCompatActivity(), ZegoCallManager.ZegoCallListener {

    private val binding by lazy {
        ActivityAudioCallBinding.inflate(layoutInflater)
    }

    private lateinit var zegoManager: ZegoCallManager
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var totalAmountSpent: Double = 0.0
    private var currentCallId: String = ""
    private var callStartTime: Long = 0
    private var callTimer: Timer? = null
    private var isAudioMuted = false
    private var isSpeakerOn = false
    private var isCallActive = false
    private var localUserID: String = ""
    private var localUserName: String = ""
    private var remoteAdvisorId: String = ""
    private var storedAdvisorId: String = "" // 🔥 Added to persist ID
    private var bookingId: String = "" // ✅ Added class member
    private var isEnding = false // ✅ Added to prevent double execution

    // ✅ Hybrid Payment System Variables
    private var heartbeatTimer: Timer? = null
    private var callId: String = ""
    private val collectionName = "audioCalls"

    // Modern permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeZego()
        } else {
            showError("Microphone permission is required for audio calls")
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


    
    private fun fetchWalletBalance() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("wallets").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userWalletBalance = document.getDouble("walletBalance") ?: document.getDouble("balance") ?: 0.0
                    Log.d("AudioCall", "Wallet Balance fetched: $userWalletBalance")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AudioCall", "Failed to fetch wallet balance: ${e.message}")
            }
    }

    private fun stopCallNotificationService() {
        try {
            val intent = Intent(this, com.example.associate.NotificationFCM.CallNotificationService::class.java).apply {
                action = com.example.associate.NotificationFCM.CallNotificationService.ACTION_STOP_SERVICE
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("AudioCall", "Failed to stop notification service: ${e.message}")
        }
    }

    private fun initializeUI() {
        val advisorName = intent.getStringExtra("ADVISOR_NAME") ?: "Advisor"
        val advisorAvatar = intent.getStringExtra("ADVISOR_AVATAR") ?: ""
        
        binding.tvAdvisorName.text = advisorName
        binding.tvTimer.text = "00:00"
        
        // 🔥 Hide Payment Info Initially to prevent flashing wrong state
        binding.tvPaymentInfo.visibility = View.GONE
        binding.tvSpentAmount.visibility = View.GONE
        binding.tvPaymentInfo.text = "Initializing..."
        binding.tvSpentAmount.text = "..."

        binding.tvConnectionStatus.text = "Initializing Audio Call..."

        // Load Advisor Avatar with Fallback
        if (advisorAvatar.isNotEmpty()) {
            loadAvatar(advisorAvatar)
        } else {
            val advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""
            if (advisorId.isNotEmpty()) {
                fetchAdvisorAvatar(advisorId)
            }
        }
    }

    // ... (keep fetchAdvisorAvatar, loadAvatar, checkPermissionsAndInitialize) ...
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
            com.bumptech.glide.Glide.with(this)
                .load(url)
                .placeholder(R.drawable.user)
                .error(R.drawable.user)
                .circleCrop()
                .into(binding.ivAdvisorAvatar)
        } catch (e: Exception) {
            Log.e("AudioCall", "Error loading advisor avatar: ${e.message}")
        }
    }

    private fun checkPermissionsAndInitialize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initializeZego()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun initializeZego() {
        binding.tvConnectionStatus.text = "Setting up engine..."
        Log.d("AudioCall", "Initializing Zego with App ID: ${AppConstants.ZEGO_APP_ID}")

        try {
            zegoManager = ZegoCallManager(this, this)
            val initialized = zegoManager.initializeEngine(AppConstants.ZEGO_APP_ID, AppConstants.ZEGO_APP_SIGN)

            if (initialized) {
                zegoManager.enableCamera(false)
                
                val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
                zegoManager.joinRoom(roomID, localUserID, localUserName)
                
                binding.tvConnectionStatus.text = "Joining call..."
                updateCallStatusInFirestore("ongoing")
                isCallActive = true
                callStartTime = System.currentTimeMillis()
                startCallTimer()
                fetchBookingDetailsAndStartService()
            } else {
                showError("Failed to initialize Zego engine")
            }
        } catch (e: Throwable) {
            Log.e("AudioCall", "Zego error: ${e.message}", e)
            showError("Audio call initialization failed: ${e.message}")
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

        db.collection("audioCalls")
            .document(currentCallId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.e("AudioCall", "Failed to update call status: ${e.message}")
            }
    }


    private var forceScheduledMode = false // 🔥 Prevent Overwrites

    private fun fetchBookingDetailsAndStartService() {
        val channelName = intent.getStringExtra("CHANNEL_NAME") 
        val bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotEmpty() } ?: channelName ?: return

        Log.w("AudioCall", "Step 1: Fetching details for bookingId: $bookingId (Channel: $channelName)")

        // 🚨 CLIENT-SIDE RULE: Check Intent Extra from Notification for Immediate UI
        val intentUrgency = intent.getStringExtra("urgencyLevel")
        Log.w("AudioCall", "🔍 RAW INTENT URGENCY: '$intentUrgency'")

        if (intentUrgency != null && intentUrgency.equals("Scheduled", ignoreCase = true)) {
             Log.w("AudioCall", "Client-Side Rule: Intent says Scheduled. Forcing Scheduled Mode immediately.")
             isInstantBooking = false
             forceScheduledMode = true // 🔥 Lock mode
             onRateSet() // ✅ Changed from startVisualTracker
        }

        // 🚨 NEW LOGIC: Check 'urgencyLevel' field (Requested by User)
        // Check Scheduled Collection and verify urgencyLevel
        db.collection("scheduled_bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val urgencyLevel = document.getString("urgencyLevel") ?: ""
                    Log.w("AudioCall", "Step 2A: Found in Scheduled. Urgency: $urgencyLevel")
                    
                    // Only update if not forced
                    if (!forceScheduledMode) {
                        if (urgencyLevel.equals("Scheduled", ignoreCase = true)) {
                            isInstantBooking = false
                            ratePerMinute = document.getDouble("sessionAmount") ?: 100.0
                            onRateSet() // ✅ Changed
                        } else {
                             // Fallback
                             isInstantBooking = false 
                             ratePerMinute = document.getDouble("sessionAmount") ?: 100.0
                             onRateSet() // ✅ Changed
                        }
                    } else {
                         // Even if forced, update rate if available
                         ratePerMinute = document.getDouble("sessionAmount") ?: 100.0
                         onRateSet() // ✅ Changed
                    }
                } else {
                    Log.w("AudioCall", "Step 2B: NOT found in scheduled_bookings. Checking Instant...")
                    // Only check instant if not forced!
                    if (!forceScheduledMode) {
                        fetchInstantBooking(bookingId)
                    } else {
                        Log.w("AudioCall", "Step 2C: Forced Scheduled Mode. Ignoring Instant lookup.")
                    }
                }
            }
            .addOnFailureListener {
                 Log.e("AudioCall", "Step 2 Error: Failed to check scheduled booking: ${it.message}. Trying Instant.")
                 if (!forceScheduledMode) fetchInstantBooking(bookingId)
            }
    }


    private fun fetchInstantBooking(bookingId: String) {
        db.collection("instant_bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val urgencyLevel = document.getString("urgencyLevel") ?: ""
                    storedAdvisorId = document.getString("advisorId") ?: "" // 🔥 Save Advisor ID
                    Log.w("AudioCall", "Step 3A: Found Instant Booking. Urgency: $urgencyLevel, AdvisorID: $storedAdvisorId")
                    
                    if (urgencyLevel.equals("Scheduled", ignoreCase = true)) {
                        isInstantBooking = false
                        ratePerMinute = document.getDouble("sessionAmount") ?: 100.0
                    } else {
                        isInstantBooking = true
                        ratePerMinute = document.getDouble("sessionAmount") ?: 60.0
                    }
                    onRateSet() // ✅ Changed
                } else {
                    Log.e("AudioCall", "Step 3B: Booking not found. Defaulting to Instant Profile Rate.")
                    fetchAdvisorRateFromProfile()
                }
            }
            .addOnFailureListener {
                Log.e("AudioCall", "Step 3 Error: Failed to check instant booking: ${it.message}. Falling back to profile.")
                fetchAdvisorRateFromProfile()
            }
    }

    private fun fetchScheduledBooking(bookingId: String) {
        db.collection("scheduled_bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    isInstantBooking = false
                    storedAdvisorId = document.getString("advisorId") ?: "" // 🔥 Save Advisor ID
                    ratePerMinute = document.getDouble("sessionAmount") ?: 100.0 // Fixed
                    onRateSet() // ✅ Changed
                } else {
                    Log.e("AudioCall", "Booking not found in Scheduled either. Fetching Advisor Rate.")
                    fetchAdvisorRateFromProfile()
                }
            }
            .addOnFailureListener {
                 fetchAdvisorRateFromProfile()
            }
    }

    private fun fetchAdvisorRateFromProfile() {
        if (forceScheduledMode) {
             Log.w("AudioCall", "Step 3C: Force Scheduled Mode active. Skipping Advisor Profile Rate fetch to prevent overwriting.")
             // Might need to set default rate or minimal behavior, but definitely NOT Instant.
             // Ensure loop continues or start tracker with current state?
             // Since we forced it, we likely already called onRateSet in Step 1.
             // If we are here, it means Step 2A/B failed/not found. 
             // If Step 1 called onRateSet, we are good.
             return
        }

        val advisorId = intent.getStringExtra("ADVISOR_ID")
        val repository = com.example.associate.Repositories.AdvisorRepository()
        
        repository.fetchInstantRate(advisorId ?: "", "AUDIO") { rate ->
            Log.d("AudioCall", "Fetched dynamic rate from Advisor Profile: $rate")
            isInstantBooking = true
            ratePerMinute = rate
            onRateSet() // ✅ Changed
        }
    }

    private fun onRateSet() {
        Log.e("AudioPayment", "=== onRateSet CALLED ===")
        Log.e("AudioPayment", "ratePerMinute: $ratePerMinute")
        Log.e("AudioPayment", "isInstantBooking: $isInstantBooking")
        Log.e("AudioPayment", "callStartTime: $callStartTime")
        Log.e("AudioPayment", "storedAdvisorId: $storedAdvisorId")
        
        // Initialize call document for Cloud Function
        initializeCall()
        
        // Start UI updates
        startVisualTracker()
    }
    
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

        // 🔥 FIX: Fetch wallet balance immediately to prevent "Insufficient Balance" errors on incoming calls
        fetchWalletBalance()

        // 🔥 SPEC COMPLIANCE: "Document ID: passed as CALL_ID intent extra."
        val explicitCallId = intent.getStringExtra("CALL_ID")
        if (!explicitCallId.isNullOrEmpty()) {
            callId = explicitCallId
            currentCallId = explicitCallId 
            bookingId = bookingId.ifEmpty { explicitCallId.replace("call_", "") } 
        } else {
            // Fallback if not provided
            callId = "call_${System.currentTimeMillis()}"
            currentCallId = callId
        }
        
        // 🔥 CRITICAL: Capture Advisor ID explicitly from Intent first
        storedAdvisorId = intent.getStringExtra("ADVISOR_ID") ?: intent.getStringExtra("advisorId") ?: ""
        if (storedAdvisorId.isEmpty()) {
             Log.w("AudioCall", "⚠️ Advisor ID missing in Intent. Will attempt to fetch from Booking.")
        } else {
             Log.d("AudioCall", "✅ Advisor ID captured from Intent: $storedAdvisorId")
        }

        initializeUI()
        
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Connecting securely...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            progressDialog.dismiss()
            android.widget.Toast.makeText(this, "Starting Audio Call...", android.widget.Toast.LENGTH_SHORT).show()
            checkPermissionsAndInitialize()
            setupCallControls()
            registerBroadcastReceiver()
            stopCallNotificationService()
        }, 1500)
    }
    
    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (callId.isNotEmpty()) {
                    db.collection(collectionName).document(callId)
                        .update("lastHeartbeat", FieldValue.serverTimestamp())
                        .addOnFailureListener { e ->
                            Log.e("AudioCall", "Heartbeat failed: ${e.message}")
                        }
                }
            }
        }, 30000, 30000) // Update every 30 seconds
    }
    
    private fun initializeCall() {
        if (callId.isEmpty()) callId = "call_${System.currentTimeMillis()}"
        
        val currentUserId = auth.currentUser?.uid ?: ""
        
        val callData = hashMapOf<String, Any>(
            "callId" to callId,
            "status" to "ongoing",
            // "advisorJoined" -> Do NOT touch this field (User App doesn't set it true)
            "userJoined" to true, 
            "startTime" to FieldValue.serverTimestamp(),
            "lastHeartbeat" to FieldValue.serverTimestamp(),
            "ratePerMinute" to ratePerMinute,
            "callType" to "AUDIO"
        )
        
        // ID SAFETY: Only set if value exists
        if (currentUserId.isNotEmpty()) {
            callData["userId"] = currentUserId
        }
        
        if (storedAdvisorId.isNotEmpty()) {
            callData["advisorId"] = storedAdvisorId
        }
        
        if (bookingId.isNotEmpty()) {
            callData["bookingId"] = bookingId
        }
        
        // Try to update first (advisor may have created it)
        db.collection(collectionName).document(callId)
            .update(callData)
            .addOnSuccessListener {
                Log.i("AudioCall", "Call document updated: $callId")
                startHeartbeat()
            }
            .addOnFailureListener { e ->
                // Only create if update fails
                db.collection(collectionName).document(callId)
                    .set(callData)
                    .addOnSuccessListener {
                        Log.i("AudioCall", "Call document created: $callId")
                        startHeartbeat()
                    }
                    .addOnFailureListener { err ->
                        Log.e("AudioCall", "Failed to initialize call: ${err.message}")
                    }
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
                    ratePerMinute
                }

                // Scheduled Logic: 30 Min Limit
                if (!isInstantBooking) {
                    val remainingSeconds = (30 * 60) - elapsedSeconds
                    val minutesLeft = remainingSeconds / 60
                    val secondsLeft = remainingSeconds % 60

                    if (remainingSeconds <= 0) {
                        Toast.makeText(this@AudioCallActivity, "Scheduled Session Completed", Toast.LENGTH_LONG).show()
                        endCallWithNavigation()
                        return
                    }

                    // Show Countdown for Scheduled
                    binding.tvPaymentInfo.visibility = View.GONE
                    binding.tvSpentAmount.visibility = View.GONE
                    binding.tvTimer.text = String.format("%02d:%02d", minutesLeft, secondsLeft)
                } else {
                    // Instant Logic: Rate & Spent
                    binding.tvPaymentInfo.visibility = View.VISIBLE
                    binding.tvSpentAmount.visibility = View.VISIBLE
                    val formattedCost = String.format("%.2f", currentCost)
                    binding.tvPaymentInfo.text = "Rate: ₹$ratePerMinute/min"
                    binding.tvSpentAmount.text = "₹$formattedCost"
                    
                    // Normal Timer for Instant
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)

                    // Balance Check
                    if (currentCost >= userWalletBalance) {
                         showInsufficientBalanceDialog()
                         return
                    }
                }

                visualTrackerHandler?.postDelayed(this, 1000)
            }
        }
        visualTrackerHandler?.post(runnable)
    }

    private fun startPaymentService(rate: Double = 60.0) {
        // Deprecated
    }
    
    // ... (keep setupCallControls, toggles) ...
    private fun setupCallControls() {
        binding.btnEndCall.setOnClickListener {
            showEndCallConfirmation()
        }

        binding.btnMute.setOnClickListener {
            toggleAudio()
        }
        
        binding.btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }
        
        binding.btnBack.setOnClickListener {
            onBackPressed() 
        }
    }

    private fun toggleAudio() {
        if (!isCallActive) return
        isAudioMuted = !isAudioMuted
        zegoManager.muteMicrophone(isAudioMuted)
        binding.ivMuteIcon.setImageResource(if (isAudioMuted) R.drawable.mutemicrophone else R.drawable.mic)
    }
    
    private fun toggleSpeaker() {
        if (!isCallActive) return
        isSpeakerOn = !isSpeakerOn
        zegoManager.enableSpeaker(isSpeakerOn)
        binding.btnSpeaker.alpha = if(isSpeakerOn) 1.0f else 0.6f
        Toast.makeText(this, "Speaker ${if(isSpeakerOn) "On" else "Off"}", Toast.LENGTH_SHORT).show()
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
        // ...
    }
    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }

    private fun updatePaymentUI() {
        // Handled by visualTracker
    }
    
    // ... (Dialog and End Call) ...
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
        if (!isCallActive) {
            finish()
            return
        }
        
        isCallActive = false
        visualTrackerHandler?.removeCallbacksAndMessages(null)
        callTimer?.cancel()

        val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
        
        // 1. Stop Zego Publishing first (Stop Streams)
        try {
            if (::zegoManager.isInitialized) {
                 zegoManager.muteMicrophone(true)
            }
        } catch (e: Exception) { Log.e("AudioCall", "Error stopping streams: $e") }

        val durationSeconds = if (callStartTime > 0) {
            (System.currentTimeMillis() - callStartTime) / 1000
        } else {
            0
        }
        
        if (durationSeconds < 5) {
             Log.e("AudioPayment", "Duration too short: $durationSeconds seconds - skipping payment")
             // Only navigate if UI is still valid
             if (!isFinishing && !isDestroyed) navigationAfterEnd()
             return
        }
        
        Log.e("AudioPayment", "Duration: $durationSeconds seconds")
        
        var progressDialog: ProgressDialog? = null
        if (!isFinishing && !isDestroyed) {
             progressDialog = ProgressDialog(this)
             progressDialog.setMessage("Processing payment...")
             progressDialog.setCancelable(false)
             progressDialog.show()
        }

        // ✅ STEP 1: Update Firestore (triggers Cloud Function)
        val updates = hashMapOf<String, Any>(
            "status" to "ended",
            "endTime" to FieldValue.serverTimestamp(),
            "callEndTime" to FieldValue.serverTimestamp(), // Redundancy
            "duration" to durationSeconds,
            "endReason" to "user_ended",
            "completedBy" to "user_app",
            "bookingId" to bookingId,
            "advisorId" to (storedAdvisorId.ifEmpty { intent.getStringExtra("ADVISOR_ID") ?: "" }),
            "userId" to (auth.currentUser?.uid ?: ""),
            "studentId" to (auth.currentUser?.uid ?: "") // Redundancy
        )
        
        if (bookingId.isEmpty()) {
             Log.e("AudioPayment", "bookingId is EMPTY - skipping payment")
             progressDialog?.dismiss()
             navigationAfterEnd()
             return
        }
        
        if (callId.isNotEmpty()) {
            db.collection(collectionName).document(callId)
                .update(updates)
                .addOnSuccessListener {
                    Log.i("AudioPayment", "Firestore updated. Waiting for Cloud Function...")
                    // ✅ UPDATE: Wait for Server Confirmation
                    waitForServerConfirmation(bookingId, progressDialog)
                }
                .addOnFailureListener { e ->
                    Log.e("AudioPayment", "Firestore update failed: ${e.message}")
                    // Fallback to ensure document exists if update failed
                     db.collection(collectionName).document(callId).set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            waitForServerConfirmation(bookingId, progressDialog)
                        }
                        .addOnFailureListener {
                            progressDialog?.dismiss()
                            Toast.makeText(this@AudioCallActivity, "Error ending call", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
        } else {
            // No callId, cannot update. Just finish
             Log.e("AudioPayment", "No callId, cannot trigger payment.")
             progressDialog?.dismiss()
             finish()
        }
    }

    private fun waitForServerConfirmation(bookingId: String, pd: ProgressDialog?) {
        val completionRef = db.collection("processed_transactions").document("${bookingId}_completion")
        
        // Timeout Handler
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                pd?.dismiss()
                Toast.makeText(this, "Call Ended. Payment processing in background.", Toast.LENGTH_LONG).show()
                navigationAfterEnd()
            }
        }
        handler.postDelayed(timeoutRunnable, 8000)

        // Listen for completion
        completionRef.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            
            if (snapshot != null && snapshot.exists()) {
                handler.removeCallbacks(timeoutRunnable) // Cancel timeout
                val status = snapshot.getString("status")
                
                if (!isFinishing && !isDestroyed) {
                    pd?.dismiss()
                    if (status == "paid") {
                        Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        val reason = snapshot.getString("failureReason") ?: "Unknown"
                        Toast.makeText(this, "Payment Failed: $reason", Toast.LENGTH_LONG).show()
                    }
                    navigationAfterEnd()
                }
            }
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
        // Logic moved to endCall
    }
    
    // Kept for compatibility but logic moved to Transaction
    private fun updateBookingStatusToEnded() {}

    // ... (Keep Zego Listeners) ...
    override fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: JSONObject) {
        runOnUiThread {
            if (errorCode == 0) {
                binding.tvConnectionStatus.text = "Connected"
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvConnectionStatus.visibility = View.INVISIBLE
                }, 2000)
                
                // ✅ Start Timer Logic
                if (callStartTime == 0L) {
                    callStartTime = System.currentTimeMillis()
                }
            } else {
                showError("Connection error: $errorCode")
            }
        }
    }

    override fun onRoomUserUpdate(roomID: String, updateType: ZegoUpdateType, userList: ArrayList<ZegoUser>) {
        runOnUiThread {
            if (updateType == ZegoUpdateType.ADD) {
                for (user in userList) {
                    remoteAdvisorId = user.userID
                    Toast.makeText(this, "Advisor joined!", Toast.LENGTH_SHORT).show()
                    
                    val streamID = "stream_${user.userID}"
                    zegoManager.startPlayingAudio(streamID)
                }
            } else if (updateType == ZegoUpdateType.DELETE) {
                Toast.makeText(this, "Advisor left the call", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    endCallWithNavigation()
                }, 2000)
            }
        }
    }

    override fun onRemoteCameraStateUpdate(streamID: String, state: Int) {}

    private fun showError(message: String) {
        Log.e("AudioCall", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
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
        stopPaymentService()

        // ✅ IMPORTANT: Attempt to process payment if getting destroyed unexpectedly (and not already ending)
        if (!isEnding) {
            endCall()
        }
    }
    // PIP Mode Implementation
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (isCallActive) {
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(1, 1)) // Square for Audio/Avatar
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
            binding.btnSpeaker.visibility = View.GONE
            binding.btnBack.visibility = View.GONE
            
            binding.tvTimer.visibility = View.GONE
            binding.tvPaymentInfo.visibility = View.GONE
            binding.tvSpentAmount.visibility = View.GONE
            binding.tvAdvisorName.visibility = View.GONE
            binding.tvConnectionStatus.visibility = View.GONE
            
            // Adjust Avatar to fill/center
            // Assuming constraint layout, we might just let it be. 
            // If it's the only thing visible, it should look okay.
            
        } else {
            // Restore Controls
            binding.btnEndCall.visibility = View.VISIBLE
            binding.btnMute.visibility = View.VISIBLE
            binding.btnSpeaker.visibility = View.VISIBLE
            binding.btnBack.visibility = View.VISIBLE
            
            binding.tvTimer.visibility = View.VISIBLE
            binding.tvPaymentInfo.visibility = View.VISIBLE
            binding.tvSpentAmount.visibility = View.VISIBLE
            binding.tvAdvisorName.visibility = View.VISIBLE
            binding.tvConnectionStatus.visibility = View.GONE // Usually gone unless connecting
        }
    }
}
