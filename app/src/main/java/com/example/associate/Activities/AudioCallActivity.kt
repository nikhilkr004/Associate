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
    private var callEndListener: com.google.firebase.firestore.ListenerRegistration? = null // Listener cleanup

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
        bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotEmpty() } ?: channelName ?: return

        Log.w("AudioCall", "Step 1: Fetching details for bookingId: $bookingId (Channel: $channelName)")

        // 🚨 CLIENT-SIDE RULE: Check Intent Extra from Notification for Immediate UI
        val intentUrgency = intent.getStringExtra("urgencyLevel")
        Log.w("AudioCall", "🔍 RAW INTENT URGENCY: '$intentUrgency'")

        if (intentUrgency != null && intentUrgency.equals("Scheduled", ignoreCase = true)) {
             Log.w("AudioCall", "Client-Side Rule: Intent says Scheduled. Forcing Scheduled Mode immediately.")
             isInstantBooking = false
             forceScheduledMode = true 
             onRateSet() 
        }

        // 🚨 RECOVERY: If Booking ID matches Channel Name, it's likely a FALLBACK from missing intent data.
        // We must attempt to find the REAL booking ID from Firestore.
        if (bookingId == channelName && !forceScheduledMode) {
             Log.w("AudioCall", "⚠️ Suspicious Booking ID (Matches Channel/Call ID). Attempting Recovery...")
             recoverBookingId()
             return // Stop valid flow until recovery completes
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

    // 🔍 RECOVERY FUNCTION: Find active booking for this user/advisor
    private fun recoverBookingId() {
        val userId = auth.currentUser?.uid ?: return
        val advisorId = storedAdvisorId.takeIf { it.isNotEmpty() } ?: intent.getStringExtra("ADVISOR_ID") ?: ""
        
        if (advisorId.isEmpty()) {
            Log.e("AudioCall", "Recovery Failed: No Advisor ID available.")
            fetchAdvisorRateFromProfile()
            return
        }

        Log.d("AudioCall", "Recovery: Looking for accepted bookings for User $userId with Advisor $advisorId")

        // 1. Check Scheduled First (Higher Priority in general)
        db.collection("scheduled_bookings")
            .whereEqualTo("studentId", userId)
            .whereEqualTo("advisorId", advisorId)
            .whereIn("bookingStatus", listOf("accepted", "pending")) // 'accepted' is most likely
            // .whereGreaterThan("timestamp", ...) // Could optimize by date
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents.first()
                    bookingId = doc.id
                    Log.i("AudioCall", "✅ RECOVERY SUCCESS: Found Scheduled Booking ID: $bookingId")
                    isInstantBooking = false
                    forceScheduledMode = true
                    storedAdvisorId = advisorId // Ensure stored
                    
                    // Resume Normal Flow
                    ratePerMinute = doc.getDouble("sessionAmount") ?: 100.0
                    onRateSet()
                } else {
                    // 2. Check Instant Bookings
                    Log.d("AudioCall", "Recovery: No Scheduled found. Checking Instant...")
                    db.collection("instant_bookings")
                        .whereEqualTo("studentId", userId)
                        .whereEqualTo("advisorId", advisorId)
                        .whereIn("bookingStatus", listOf("accepted", "pending"))
                        .get()
                        .addOnSuccessListener { instantDocs ->
                             if (!instantDocs.isEmpty) {
                                val doc = instantDocs.documents.first()
                                bookingId = doc.id
                                Log.i("AudioCall", "✅ RECOVERY SUCCESS: Found Instant Booking ID: $bookingId")
                                isInstantBooking = true
                                storedAdvisorId = advisorId
                                
                                // Resume Normal Flow
                                ratePerMinute = doc.getDouble("sessionAmount") ?: 60.0
                                onRateSet()
                             } else {
                                Log.e("AudioCall", "❌ Recovery Failed: No active bookings found in either collection.")
                                // Fallback to profile rate (orphan call)
                                fetchAdvisorRateFromProfile()
                             }
                        }
                        .addOnFailureListener { e ->
                            Log.e("AudioCall", "Recovery Error (Instant): ${e.message}")
                            fetchAdvisorRateFromProfile()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AudioCall", "Recovery Error (Scheduled): ${e.message}")
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
        if (!isCallActive && !isEnding) {
            finish()
            return
        }
        
        isEnding = true
        isCallActive = false
        visualTrackerHandler?.removeCallbacksAndMessages(null)
        callTimer?.cancel()

        Log.d("PaymentDebug", "=== AudioCall endCall Triggered ===")
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
        
        db.collection("audioCalls").document(currentCallId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("AudioCall", "Call ended. Server will process payment.")
                Log.d("PaymentDebug", "✅ Success: AudioCall Doc Updated") 
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("AudioCall", "Error ending call", e)
                Log.e("PaymentDebug", "❌ Error Updating AudioCall Doc: ${e.message}")
                finish()
            }
        
        leaveZegoRoom()
    }

    private fun leaveZegoRoom() {
        try {
            val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
            if (::zegoManager.isInitialized) {
                zegoManager.muteMicrophone(true)
                zegoManager.leaveRoom(roomID)
            }
        } catch (e: Exception) {
            Log.e("AudioCall", "Error leaving room: $e")
        }
    }
    
    private fun listenForCallEnd() {
         if (callId.isEmpty()) return
         
         callEndListener?.remove()
         callEndListener = db.collection(collectionName).document(callId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { return@addSnapshotListener }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status")
                    if (status == "ended") {
                        if (!isFinishing && !isDestroyed) {
                            navigationAfterEnd()
                        }
                    }
                }
            }
    }

    private fun navigationAfterEnd() {
        val intent = Intent(this, BookingSummaryActivity::class.java)
        intent.putExtra("BOOKING_ID", bookingId)
        intent.putExtra("ADVISOR_ID", storedAdvisorId)
        startActivity(intent)
        finish()
    }
    
    private fun endCallWithNavigation() {
        endCall()
    }

    private fun saveRatingToBackend(ratingValue: Float, review: String) {}

    private fun navigateToHome() {
        navigationAfterEnd()
    }

    private fun stopPaymentService() {}

    private fun updateCallEndStatus() {}
    
    private fun updateBookingStatusToEnded() {}

    // ... (Keep Zego Listeners) ...
    override fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: JSONObject) {
        runOnUiThread {
            if (errorCode == 0) {
                binding.tvConnectionStatus.text = "Connected"
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvConnectionStatus.visibility = View.INVISIBLE
                }, 2000)
                
                if (callStartTime == 0L) {
                    callStartTime = System.currentTimeMillis()
                }
                
                // 🔥 Start Listener
                listenForCallEnd()
                
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
                 // Wait for listener or end locally?
                 // Requirement: Reaction: When status changes to ended...
                 // If advisor ended it, they updated status. Listener triggers. 
                 // If they just left (network), we might want to allow user to end.
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
            .setPositiveButton("End Call") { dialog, which -> endCall() }
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
        callEndListener?.remove()

        if (!isEnding) {
            // Disconnect safely
             val updates = hashMapOf<String, Any>(
                "status" to "ended",
                "endReason" to "user_disconnected"
            )
            if (callId.isNotEmpty()) {
                 db.collection(collectionName).document(callId).update(updates)
            }
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
            binding.tvConnectionStatus.visibility = View.GONE 
        }
    }
}

// Updated for repository activity
