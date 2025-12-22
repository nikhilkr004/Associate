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
import com.example.associate.Dialogs.RatingDialog
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
import java.util.Timer
import java.util.TimerTask

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Window flags for lock screen
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
            Log.e("AudioCall", "Failed to stop notification service: ${e.message}")
        }
    }

    private fun initializeUI() {
        val advisorName = intent.getStringExtra("ADVISOR_NAME") ?: "Advisor"
        val advisorAvatar = intent.getStringExtra("ADVISOR_AVATAR") ?: ""
        
        binding.tvAdvisorName.text = advisorName
        binding.tvTimer.text = "00:00"
        binding.tvPaymentInfo.text = "Rate: ₹60/min"
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
                // Strictly disable camera for Audio Call
                zegoManager.enableCamera(false)
                
                // Join room
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
                Log.e("AudioCall", "Failed to update call status: ${e.message}")
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
                Log.e("AudioCall", "Failed to fetch Instant booking. Trying fallback.")
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
                    Log.e("AudioCall", "Booking not found in Scheduled either. Fetching Advisor Rate.")
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
        
        repository.fetchInstantRate(advisorId ?: "", "AUDIO") { rate ->
            Log.d("AudioCall", "Fetched dynamic rate from Advisor Profile: $rate")
            startPaymentService(rate)
        }
    }

    private fun startPaymentService(rate: Double = 60.0) {
        try {
            binding.tvPaymentInfo.text = "Rate: ₹$rate/min"
            val serviceIntent = Intent(this, VideoCallService::class.java).apply {
                action = "START_PAYMENT"
                putExtra("CALL_ID", currentCallId)
                putExtra("RATE_PER_MINUTE", rate)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Log.e("AudioCall", "Failed to start payment service: ${e.message}")
        }
    }
    
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
            // Optional: minimize or simple back? 
            // Usually in call screens, back button works same as home (background) or strictly ends call.
            // Let's make it act like home/background for safety, or just ignore. 
            // User requested 'Back button', let's assume minimizing or back press behavior.
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
        // Update icon tint or background to show active state if needed?
        // Using Alpha for simple feedback
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
            Log.e("AudioCall", "Failed to register broadcast receiver: ${e.message}")
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
        // Update Live Spent Amount
        binding.tvSpentAmount.text = "₹${String.format("%.2f", totalAmountSpent)}"
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
            finish()
            return
        }
        isCallActive = false
        stopPaymentService()
        try {
            val roomID = intent.getStringExtra("CHANNEL_NAME") ?: AppConstants.DEFAULT_CHANNEL_NAME
            zegoManager.leaveRoom(roomID)
        } catch (e: Exception) {}

        updateCallEndStatus()
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
        } catch (e: Exception) {}
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
            .addOnCompleteListener { 
                 updateBookingStatusToEnded()
            }
    }

    private fun updateBookingStatusToEnded() {
        val bookingId = intent.getStringExtra("CHANNEL_NAME") ?: return
        val instantRef = db.collection("instant_bookings").document(bookingId)
        instantRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                instantRef.update("bookingStatus", "ended")
            } else {
                val scheduledRef = db.collection("scheduled_bookings").document(bookingId)
                scheduledRef.update("bookingStatus", "ended")
            }
        }
    }

    // Zego Event Listeners
    override fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: JSONObject) {
        runOnUiThread {
            if (errorCode == 0) {
                binding.tvConnectionStatus.text = "Connected"
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvConnectionStatus.visibility = View.INVISIBLE
                }, 2000)
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
                    
                    // Play remote audio
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

    override fun onRemoteCameraStateUpdate(streamID: String, state: Int) {
        // Not used in Audio Call
    }

    private fun showError(message: String) {
        Log.e("AudioCall", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
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
        } catch (e: Exception) {}
        try {
            unregisterReceiver(balanceReceiver)
        } catch (e: Exception) {}
        stopPaymentService()
    }
}
