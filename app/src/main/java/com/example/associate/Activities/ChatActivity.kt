package com.example.associate.Activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.associate.Adapters.ChatActivityAdapter
import com.example.associate.R
import com.example.associate.Repositories.StorageRepository
import com.example.associate.databinding.ActivityChatBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Timer
import java.util.TimerTask
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class ChatActivity : AppCompatActivity(), com.example.associate.PreferencesHelper.ZegoCallManager.ZegoCallListener {

    private val binding by lazy { ActivityChatBinding.inflate(layoutInflater) }
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storageRepository = StorageRepository()
    
    // Zego Manager
    private lateinit var zegoManager: com.example.associate.PreferencesHelper.ZegoCallManager
    private var isAudioMuted = false
    private var isSpeakerOn = false // Default to Speaker or Earpiece? Usually Speaker for chat app if hands-free.

    private lateinit var chatAdapter: ChatActivityAdapter
    private var listenerRegistration: ListenerRegistration? = null
    private val chatMessages = ArrayList<HashMap<String, Any>>() // Persist messages across adapter recreations
    
    // ✅ Hybrid Payment System Variables
    private var heartbeatTimer: Timer? = null
    private var callId: String = ""
    private var collectionName = "chats"

    // ... (rest of class)

    private fun setupMessageListener(docIdInput: String) {
        // Resolve Doc ID: Prioritize Input, fallback to Intent/Booking logic if needed
        // But typically this function is called with the *Resolved* ID.
        // Let's ensure currentChatDocId is set here.
        val docId = docIdInput.ifEmpty { if (intent.hasExtra("CHANNEL_NAME")) intent.getStringExtra("CHANNEL_NAME") else bookingId }
        
        if (docId.isNullOrEmpty()) {
             Log.e("ChatDebug", "setupMessageListener Failed: docId is empty")
             return
        }
        currentChatDocId = docId 
        Log.e("ChatDebug", "setupMessageListener Started. Collection: $collectionName, DocID: $currentChatDocId") // Log Collection!

        listenerRegistration = db.collection(collectionName)
            .document(docId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ChatDebug", "Listen Failed: ${e.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                   Log.e("ChatDebug", "Snapshot Received. Docs: ${snapshot.size()}")
                   
                   chatMessages.clear() 
                   val tempMessages = ArrayList<HashMap<String, Any>>()
                   for (doc in snapshot.documents) {
                       val data = doc.data
                       if (data != null) {
                           Log.e("ChatDebug", "Msg Payload: $data") // 🔥 TRACE PAYLOAD
                           val msgMap = HashMap(data)
                           tempMessages.add(msgMap)
                           
                           val senderId = msgMap["senderId"] as? String
                           Log.e("ChatDebug", "Msg Sender: $senderId vs Current: $currentUserId")
                           
                           val status = msgMap["status"] as? String
                           if (!isHistoryView && senderId != currentUserId && status != "seen") { 
                               doc.reference.update("status", "seen")
                           }
                       }
                   }
                    chatMessages.addAll(tempMessages)
                    
                    if (::chatAdapter.isInitialized) {
                        chatAdapter.updateList(tempMessages)
                    } else {
                        Log.e("ChatDebug", "Adapter NOT initialized yet!")
                    }
                   
                   if (chatMessages.isNotEmpty()) {
                       binding.rvChatMessages.scrollToPosition(chatMessages.size - 1)
                   }
                }
            }
    }


    private var chatId: String = ""
    private var bookingId: String = "" // Store bookingId 
    private var currentChatDocId: String = "" // 🔥 Unified Doc ID for Send & Listen 
    private var advisorId: String = ""
    private var advisorName: String = ""
    private var advisorAvatar: String = ""
    private var currentUserId: String = ""
    private var ratePerMinute: Double = 0.0
    private var userWalletBalance: Double = 0.0
    private var isHistoryView = false // Flag for history mode
    // collectionName declared above at line 66, removing this duplicate
    
    // Timer & Billing
    private var startTime: Long = 0
    private var billingTimer: Timer? = null
    private var handler = Handler(Looper.getMainLooper())
    private var isChatActive = true

    // Audio Recording
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    // Player
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val REQUEST_MIC_PERMISSION_FOR_CALL = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        currentUserId = auth.currentUser?.uid ?: ""
        if (currentUserId.isEmpty()) {
             finish() 
             return
        }

        // Get Intent Data
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        advisorName = intent.getStringExtra("ADVISOR_NAME") ?: "Advisor"
        advisorAvatar = intent.getStringExtra("ADVISOR_AVATAR") ?: ""
        // Capture Booking ID explicitly if passed (e.g. from Incoming Call or Notification)
        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        
        // 🔥 CRITICAL: Capture Advisor ID explicitly from Intent first
        advisorId = intent.getStringExtra("ADVISOR_ID") ?: intent.getStringExtra("advisorId") ?: ""
        if (advisorId.isEmpty()) {
             Log.w("ChatActivity", "⚠️ Advisor ID missing in Intent.")
        } else {
             Log.d("ChatActivity", "✅ Advisor ID captured from Intent: $advisorId")
        }
        
        // 🔥 SPEC COMPLIANCE: "Document ID: passed as CALL_ID intent extra."
        val explicitCallId = intent.getStringExtra("CALL_ID")
        if (!explicitCallId.isNullOrEmpty()) {
            callId = explicitCallId
            currentChatDocId = explicitCallId
            bookingId = bookingId.ifEmpty { explicitCallId.replace("call_", "") } 
        } else {
            // Fallback if not provided
            callId = "call_${System.currentTimeMillis()}"
        }

        startStatusListener() // ✅ HOOK UP LISTENER HERE


        // Determine Collection Name
        val passedCollection = intent.getStringExtra("COLLECTION_NAME")
        if (!passedCollection.isNullOrEmpty()) {
            collectionName = passedCollection
        } else {
            val type = intent.getStringExtra("CALL_TYPE") ?: "CHAT"
            collectionName = when (type.lowercase()) {
                "video" -> "videoCalls"
                "audio" -> "audioCalls"
                else -> "chats"
            }
        }

        if (chatId.isEmpty() && advisorId.isNotEmpty()) {
            chatId = generateChatId(currentUserId, advisorId)
        }

        // Check History Mode
        isHistoryView = intent.getBooleanExtra("IS_HISTORY", false)
        
        setupUI()
        setupRecyclerView()
        
        if (isHistoryView) {
            // History Mode Logic
            binding.bottomLayout.visibility = View.GONE
            binding.btnAudioMute.visibility = View.GONE
            binding.tvStatus.text = "Chat History"
            
            val paidAmount = intent.getDoubleExtra("PAID_AMOUNT", 0.0)
            binding.tvTimerRight.text = String.format("Paid: ₹%.2f", paidAmount)
            binding.tvTimerRight.visibility = View.VISIBLE
            
            // Load messages only
            listenForMessages()
            
            // Do NOT init Zego or Billing
        } else {
            // Normal Live Chat Logic
            fetchWalletAndRate()
            
            // Listen messages
            listenForMessages()
            
            // Typing Status
            setupTypingListener()
            
            // Initialize Zego (Audio Only)
            checkMicPermissionAndInitZego()
        }
    }
    
    private fun checkMicPermissionAndInitZego() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION_FOR_CALL)
        } else {
            initializeZego()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_PERMISSION_FOR_CALL) {
             if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 initializeZego()
             } else {
                 Toast.makeText(this, "Microphone access needed for audio call.", Toast.LENGTH_SHORT).show()
             }
        }
        // Handle REQUEST_RECORD_AUDIO_PERMISSION if needed separately, but it's the same permission.
    }

    private fun initializeCall() {
        val currentUserId = auth.currentUser?.uid ?: ""
        
        val callData = hashMapOf<String, Any>(
            "callId" to callId,
            // "advisorJoined" -> Do NOT touch this field (User App doesn't set it true)
            "userJoined" to true, 
            "status" to "ongoing",
            "startTime" to FieldValue.serverTimestamp(),
            "lastHeartbeat" to FieldValue.serverTimestamp(),
            "ratePerMinute" to ratePerMinute,
            "callType" to "CHAT" // Or dynamic based on intent if this activity supports others
        )
        
        // ID SAFETY: Only set if value exists
        if (currentUserId.isNotEmpty()) {
            callData["userId"] = currentUserId
        }
        
        if (advisorId.isNotEmpty()) {
            callData["advisorId"] = advisorId
        }
        
        if (bookingId.isNotEmpty()) {
            callData["bookingId"] = bookingId
        }
        
        // Try to update first (advisor may have created it)
        db.collection(collectionName).document(callId)
            .update(callData)
            .addOnSuccessListener {
                Log.i("ChatActivity", "Call document updated: $callId")
                startHeartbeat()
            }
            .addOnFailureListener { e ->
                // Document doesn't exist, create it
                db.collection(collectionName).document(callId)
                    .set(callData)
                    .addOnSuccessListener {
                        Log.i("ChatActivity", "Call document created: $callId")
                        startHeartbeat()
                    }
                    .addOnFailureListener { err ->
                        Log.e("ChatActivity", "Failed to initialize call: ${err.message}")
                    }
            }
    }
    private fun initializeZego() {
        val appId = com.example.associate.Utils.AppConstants.ZEGO_APP_ID
        val appSign = com.example.associate.Utils.AppConstants.ZEGO_APP_SIGN
        
        zegoManager = com.example.associate.PreferencesHelper.ZegoCallManager(this, this)
        val success = zegoManager.initializeEngine(appId, appSign)
        if (success) {
            // Audio Only Mode: Disable Camera
            zegoManager.enableCamera(false)
            
            // Join Room
            // Use BookingID or ChatID. Advisor likely joins BookingID (Channel Name)
            // If BookingID not yet created (fresh chat), we should wait? 
            // In "fresh chat" from profile, we create booking in 'onRateSet'. 
            // We should join room AFTER booking ID is ready? 
            // Or use chatId? Let's use 'chatId' for now if bookingId is empty, but warn.
            // Wait! The user prompt said: "Initialize Zego Engine... so they can talk while chatting".
            // If this is an accepted request, we have a booking ID (Room ID).
            // If it's a new chat, the Advisor isn't there yet anyway.
            
            // NOTE: We'll initialize here, but join room might need to happen once we have a valid Room ID.
            // If bookingId is present (from IncomingCall), join immediately.
            if (bookingId.isNotEmpty()) {
                joinZegoRoom("call_$bookingId") // MATCH ADVISOR "call_" prefix
            }
        }
    }
    
    private fun joinZegoRoom(channelName: String) {
        val userName = auth.currentUser?.displayName ?: "User"
        zegoManager.joinRoom(channelName, currentUserId, userName)
        Toast.makeText(this, "Joined Audio: $channelName", Toast.LENGTH_SHORT).show()
    }

    private fun setupUI() {
        binding.tvAdvisorName.text = advisorName
        if (advisorAvatar.isNotEmpty()) {
             Glide.with(this).load(advisorAvatar).placeholder(R.drawable.user).into(binding.ivAdvisorAvatar)
        } else {
             fetchAdvisorAvatar()
        }
        
        // Initial Mute Icon state
        binding.btnAudioMute.setImageResource(R.drawable.mic) 
    }

    private fun fetchAdvisorAvatar() {
        db.collection("advisors").document(advisorId).get().addOnSuccessListener { 
            val url = it.getString("profileImage")
            if (!url.isNullOrEmpty()) {
                Glide.with(this).load(url).placeholder(R.drawable.user).into(binding.ivAdvisorAvatar)
            }
        }
    }

    private fun setupRecyclerView() {
        // We need user avatar before init adapter if possible, or update it later.
        // Let's create specific method to setup adapter once user data fetched or pass empty first.
        
        chatAdapter = ChatActivityAdapter(
            currentUserId,
            "", // User Avatar (Placeholder)
            advisorAvatar, // Advisor Avatar
            chatMessages,
            onItemClick = { content, type ->
                when(type) {
                    "image", "document" -> openFile(content, type)
                    "audio" -> playAudio(content)
                }
            }
        )
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.rvChatMessages.layoutManager = layoutManager
        binding.rvChatMessages.adapter = chatAdapter
        
        // Fetch User Data and Re-init Adapter or (better) add setter in Adapter?
        // Simpler to just re-set adapter or fetch before.
        fetchCurrentUserProfile()
    }

    private fun fetchCurrentUserProfile() {
       Log.e("ChatDebug", "Fetching User Profile...")
       db.collection("users").document(currentUserId).get().addOnSuccessListener {
           val userAvatar = it.getString("profileImage") ?: ""
           
           // 🔥 FIX: Do not overwrite adapter. Just update avatar if possible, or re-create safely.
           // Better: We should have initialized adapter in setupRecyclerView already.
           // If we re-create, we lose the previous reference if anyone held it (listener doesn't hold ref, it accesses property).
           // But 'chatMessages' is shared.
           
           Log.e("ChatDebug", "UserProfile fetched. Avatar: $userAvatar")
           
           // Re-create adapter to apply avatar
           chatAdapter = ChatActivityAdapter(
               currentUserId,
               userAvatar,
               advisorAvatar,
               chatMessages, 
               onItemClick = { content, type ->
                   when(type) {
                       "image", "document" -> openFile(content, type)
                       "audio" -> playAudio(content)
                   }
               }
           )
           // Re-bind to RV
           binding.rvChatMessages.adapter = chatAdapter
           // Force refresh list to ensure view matches data
           chatAdapter.notifyDataSetChanged()
       }




        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text, "text", "", "text")
                binding.etMessage.setText("")
            }
        }

        binding.btnAttach.setOnClickListener {
            showAttachmentMenu()
        }
        
        // Mute Toggle (ensure it works with right side layout)
        binding.btnAudioMute.setOnClickListener {
             if (::zegoManager.isInitialized) {
                 isAudioMuted = !isAudioMuted
                 zegoManager.muteMicrophone(isAudioMuted)
                 binding.btnAudioMute.setImageResource(if (isAudioMuted) R.drawable.mutemicrophone else R.drawable.mic)
                 Toast.makeText(this, if (isAudioMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
             }
        }

        // Mic Handling
        binding.btnMic.setOnTouchListener { view, motionEvent ->
             when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (::zegoManager.isInitialized) zegoManager.muteMicrophone(true)
                    checkPermissionAndStartRecording()
                    view.alpha = 0.5f 
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecordingAndSend()
                    if (::zegoManager.isInitialized) zegoManager.muteMicrophone(isAudioMuted)
                    view.alpha = 1.0f
                }
            }
            true
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    binding.btnMic.visibility = View.VISIBLE
                    binding.btnSend.visibility = View.GONE
                } else {
                    binding.btnMic.visibility = View.GONE
                    binding.btnSend.visibility = View.VISIBLE
                }
                updateTypingStatus(true)
            }
            override fun afterTextChanged(s: Editable?) {
                handler.postDelayed({ updateTypingStatus(false) }, 2000)
            }
        })
    }
    
    override fun onBackPressed() {
        if (isHistoryView) {
            super.onBackPressed()
        } else {
            com.example.associate.DataClass.DialogUtils.showExitDialog(this) {
                endChatSession()
                super.onBackPressed()
            }
        }
    }

    private val bookingRepository = com.example.associate.Repositories.BookingRepository() // Init Repository



    // --- Billing & Timer ---
    
    private var isInstantBooking = true // Default to Instant/Medium logic
    private var urgencyLevel = "Medium"

    private fun fetchWalletAndRate() {
        // Step 1: Check Intent for Urgency
        val intentUrgency = intent.getStringExtra("urgencyLevel")
        if (intentUrgency != null) {
            urgencyLevel = intentUrgency
        }

        // Check if Scheduled (Urgency != Medium)
        // User rule: "if urgencyLevel 'Medium' -> Instant (Deduct per min), Else -> Scheduled (Fixed)"
        if (!urgencyLevel.equals("Medium", ignoreCase = true)) {
            isInstantBooking = false
        }
        
        db.collection("wallets").document(currentUserId).get().addOnSuccessListener {
            userWalletBalance = it.getDouble("balance") ?: 0.0
            
            // Step 2: Fetch Rate/Session Amount based on Booking ID if exists, else Advisor Profile
            if (bookingId.isNotEmpty()) {
                fetchBookingDetails()
            } else {
                fetchAdvisorChatRate()
            }
        }
    }
    
    private fun fetchBookingDetails() {
        // Try Scheduled First
        db.collection("scheduled_bookings").document(bookingId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    isInstantBooking = false
                    urgencyLevel = doc.getString("urgencyLevel") ?: "Scheduled"
                    ratePerMinute = doc.getDouble("sessionAmount") ?: 100.0 // Full Session Fee
                    onRateSet()
                } else {
                    // Try Instant
                    db.collection("instant_bookings").document(bookingId).get()
                        .addOnSuccessListener { instantDoc ->
                            if (instantDoc.exists()) {
                                urgencyLevel = instantDoc.getString("urgencyLevel") ?: "Medium"
                                isInstantBooking = urgencyLevel.equals("Medium", ignoreCase = true)
                                ratePerMinute = instantDoc.getDouble("sessionAmount") 
                                    ?: instantDoc.getDouble("rate") ?: 10.0
                                onRateSet()
                            } else {
                                fetchAdvisorChatRate()
                            }
                        }
                }
            }
    }

    private fun fetchAdvisorChatRate() {
         db.collection("advisors").document(advisorId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                 ratePerMinute = doc.getDouble("chatRate") ?: 10.0 
                 onRateSet()
            } else {
                 Log.e("ChatActivity", "Advisor document not found for: $advisorId")
                 ratePerMinute = 10.0 // Default fallback
                 onRateSet()
            }
        }.addOnFailureListener { e ->
            Log.e("ChatActivity", "Failed to fetch advisor rate: ${e.message}")
            ratePerMinute = 10.0 // Default fallback
            onRateSet()
            }
    }
    
    
    private fun onRateSet() {
        Log.e("ChatPayment", "=== onRateSet CALLED ===")
        Log.e("ChatPayment", "ratePerMinute: $ratePerMinute")
        Log.e("ChatPayment", "isInstantBooking: $isInstantBooking")
        Log.e("ChatPayment", "advisorId: $advisorId")
        
        // Validate required data
        if (advisorId.isEmpty()) {
            Log.e("ChatPayment", "ERROR: advisorId is EMPTY - cannot start billing")
            Toast.makeText(this, "Error: Advisor ID missing", Toast.LENGTH_LONG).show()
            return
        }
        
        if (ratePerMinute <= 0) {
            Log.e("ChatPayment", "WARNING: ratePerMinute is 0 or negative: $ratePerMinute")
        }
        
        // Start the billing timer
        startBillingTimer()
        
        Log.e("ChatPayment", "Billing timer started. startTime: $startTime")
        
        // \u2705 Initialize call document for Cloud Function
        initializeCall()
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
                            Log.e("ChatActivity", "Heartbeat failed: ${e.message}")
                        }
                }
            }
        }, 30000, 30000) // Update every 30 seconds
    }

    private var statusListenerRegistration: ListenerRegistration? = null

    private fun startStatusListener() {
        if (callId.isEmpty()) return
        
        statusListenerRegistration = db.collection(collectionName).document(callId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status")
                    if (status == "ended") {
                        val endReason = snapshot.getString("endReason")
                        Log.w("ChatActivity", "Chat ended by other party. Status: $status")
                        
                        // Prevent double end call logic
                        if (!isEnding) {
                            isEnding = true
                            billingTimer?.cancel()
                            heartbeatTimer?.cancel()
                            
                            val msg = if (endReason == "advisor_ended") "Advisor ended the chat." else "Chat ended."
                            Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_LONG).show()
                            
                            // Finish gracefully
                            finish()
                        }
                    }
                }
            }
    }
    


    private fun startBillingTimer() {
        startTime = System.currentTimeMillis()
        billingTimer = Timer()
        billingTimer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    if (!isChatActive) return@runOnUiThread
                    
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                    
                    if (isInstantBooking) {
                        // --- INSTANT MODE (Medium) ---
                        // Show Spent Amount: ₹ XX.XX
                        // Deduction: Per Minute (Handled by Repo Transaction at end, but we allow session to run)
                        
                        val minutes = elapsedSeconds / 60
                        val seconds = elapsedSeconds % 60
                        val cost = (elapsedSeconds / 60.0) * ratePerMinute
                        
                        binding.tvTimerRight.text = String.format("₹ %.2f", cost)
                        binding.tvTimerRight.visibility = View.VISIBLE
                        
                        // Check Balance using 5 min buffer (optional) or immediate check
                        if (cost >= userWalletBalance) {
                            showInsufficientBalance()
                        }
                        
                    } else {
                        // --- SCHEDULED/FIXED MODE (Not Medium) ---
                        // Show Remaining Time (30 mins limit logic)
                        // Deduction: Fixed Session Amount (One Time)
                        
                        val maxDurationSeconds = 30 * 60 // 30 Minutes
                        val remainingSeconds = maxDurationSeconds - elapsedSeconds
                        
                        if (remainingSeconds <= 0) {
                            Toast.makeText(this@ChatActivity, "Session Completed", Toast.LENGTH_LONG).show()
                            onBackPressed() // End Chat
                            return@runOnUiThread
                        }
                        
                        val rMinutes = remainingSeconds / 60
                        val rSeconds = remainingSeconds % 60
                        binding.tvTimerRight.text = String.format("%02d:%02d", rMinutes, rSeconds)
                        binding.tvTimerRight.visibility = View.VISIBLE
                    }
                }
            }
        }, 1000, 1000)
    }

    private fun showInsufficientBalance() {
        isChatActive = false
        billingTimer?.cancel()
        AlertDialog.Builder(this)
            .setTitle("Insufficient Balance")
            .setMessage("Your wallet balance is empty.")
            .setPositiveButton("End Chat") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // --- Media & Messages ---

    private fun listenForMessages() {
        // 🔥 SPEC COMPLIANCE: If we have an explicit Document ID (CALL_ID), USE IT.
        if (currentChatDocId.isNotEmpty()) {
            setupMessageListener(currentChatDocId)
            return
        }

        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Error: Booking ID is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Prioritize Direct Channel Name from Intent
        val directChannelName = intent.getStringExtra("CHANNEL_NAME")
        if (!directChannelName.isNullOrEmpty()) {
            setupMessageListener(directChannelName)
            return
        }

        val trimmedBId = bookingId.trim()
        val collection = if (isInstantBooking) "instant_bookings" else "scheduled_bookings"
        
        // 2. Try to fetch explicit channel name from booking
        db.collection(collection).document(trimmedBId).get()
            .addOnSuccessListener { doc ->
                val rId = if (doc.exists()) doc.getString("channelName") else null
                
                if (!rId.isNullOrEmpty()) {
                    setupMessageListener(rId)
                } else {
                    // 3. SMART RESOLUTION: Check both possibilities
                    val callIdCandidate = "call_$trimmedBId"
                    val rawIdCandidate = trimmedBId
                    
                    val callRef = db.collection("chats").document(callIdCandidate).collection("messages").limit(1)
                    val rawRef = db.collection("chats").document(rawIdCandidate).collection("messages").limit(1)
                    
                    callRef.get().addOnSuccessListener { callSnap ->
                        if (!callSnap.isEmpty) {
                            Log.e("ChatActivity", "Resolved: Existing Standard Chat ($callIdCandidate)")
                            setupMessageListener(callIdCandidate)
                        } else {
                            rawRef.get().addOnSuccessListener { rawSnap ->
                                if (!rawSnap.isEmpty) {
                                    Log.e("ChatActivity", "Resolved: Legacy Chat ($rawIdCandidate)")
                                    setupMessageListener(rawIdCandidate)
                                } else {
                                    // BOTH EMPTY -> NEW CHAT via this path
                                    // DEFAULT TO STANDARD (call_)
                                    Log.e("ChatActivity", "Resolved: New Chat -> Enforcing Standard ($callIdCandidate)")
                                    setupMessageListener(callIdCandidate)
                                }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                 // Network failed? Default to Standard.
                 val callIdCandidate = "call_$trimmedBId"
                 setupMessageListener(callIdCandidate)
            }
    }
    
    // Legacy Search Functions Removed as per User Request for Cleanup



    private fun sendMessage(content: String, type: String, name: String, fileType: String, duration: Long = 0) {
        // Use currentChatDocId if set, else fallback to bookingId
        val targetDocId = if (currentChatDocId.isNotEmpty()) currentChatDocId else bookingId
        if (targetDocId.isEmpty()) return

        val msg = hashMapOf<String, Any>(
            "content" to content,
            "senderId" to currentUserId,
            "name" to name, 
            "type" to type,
            "timestamp" to Timestamp.now(),
            "status" to "sent"
        )
        if (duration > 0) {
            msg["duration"] = duration
        }
        
        db.collection(collectionName).document(targetDocId).collection("messages").add(msg)
        
        // Update main document for last message
        // Update main document for last message & Metadata for Chat List
        // 🔥 FIX: Do NOT set receiverId/callerId here. 
        // If we set 'receiverId' = currentUserId, Cloud Function sends "Incoming Call" to US! (Loop)
        // If this doc is new, we leave receiverId null so Cloud Function skips notification.
        // We only update content relevant for Chat List sorting.
        val metadata = hashMapOf(
            "lastMessage" to (if (type == "text") content else type),
            "lastUpdated" to Timestamp.now(),
            "lastMessageTime" to Timestamp.now(),
            "participants" to listOf(currentUserId, advisorId)
        )
        
        db.collection(collectionName).document(targetDocId).set(metadata, com.google.firebase.firestore.SetOptions.merge())
    }

    // --- Typing ---
    private fun updateTypingStatus(isTyping: Boolean) {
        val targetDocId = if (currentChatDocId.isNotEmpty()) currentChatDocId else bookingId
        if (targetDocId.isEmpty()) return
        db.collection(collectionName).document(targetDocId).update("typing_$currentUserId", isTyping)
    }
    
    private fun setupTypingListener() {
        val targetDocId = if (currentChatDocId.isNotEmpty()) currentChatDocId else bookingId
        if (targetDocId.isEmpty()) return
        db.collection(collectionName).document(targetDocId).addSnapshotListener { doc, e ->
             if (e != null || doc == null || !doc.exists()) return@addSnapshotListener
             
             val isAdvisorTyping = doc.getBoolean("typing_$advisorId") ?: false
             binding.tvStatus.text = if (isAdvisorTyping) "Typing..." else "Online"
        }
    }

    // --- Attachments ---
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadFile(uri, true)
    }
    
    private val pickDoc = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadFile(uri, false)
    }

    private fun showAttachmentMenu() {
        val options = arrayOf("Image", "Document")
        AlertDialog.Builder(this)
            .setTitle("Attach")
            .setItems(options) { _, which ->
                if (which == 0) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else pickDoc.launch("application/pdf")
            }
            .show()
    }

    private fun uploadFile(uri: Uri, isImage: Boolean) {
        val pd = ProgressDialog(this)
        pd.setMessage("Uploading...")
        pd.show()
        
        storageRepository.uploadFile(this, uri, isImage = isImage, callback = object : StorageRepository.UploadCallback {
            override fun onSuccess(downloadUrl: String, fileName: String, fileType: String) {
                pd.dismiss()
                val type = if (isImage) "image" else "document"
                sendMessage(downloadUrl, type, fileName, type)
            }
            override fun onFailure(error: String) {
                pd.dismiss()
                Toast.makeText(this@ChatActivity, "Failed: $error", Toast.LENGTH_SHORT).show()
            }
            override fun onProgress(progress: Int) {
                pd.setMessage("Uploading $progress%")
            }
        })
    }
    
    // --- Audio Recording ---
    
    private fun checkPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            audioFile = File.createTempFile("voice_", ".3gp", externalCacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ChatActivity", "Recorder failed: ${e.message}")
        }
    }

    private fun stopRecordingAndSend() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) { }
        mediaRecorder = null
        isRecording = false
        
        if (audioFile != null && audioFile!!.exists()) {
            val uri = Uri.fromFile(audioFile)
            
            // Calculate Duration
            var duration = 0L
            try {
                val mp = MediaPlayer.create(this, uri)
                duration = mp.duration.toLong()
                mp.release()
            } catch (e: Exception) { e.printStackTrace() }
            
            val pd = ProgressDialog(this)
            pd.setMessage("Sending Voice Note...")
            pd.show()
            
            storageRepository.uploadFile(this, uri, isImage = false, callback = object : StorageRepository.UploadCallback {
                override fun onSuccess(downloadUrl: String, fileName: String, fileType: String) {
                     pd.dismiss()
                     sendMessage(downloadUrl, "audio", "Voice Note", "audio", duration)
                }
                override fun onFailure(error: String) { pd.dismiss() }
                override fun onProgress(progress: Int) {}
            })
        }
    }

    // --- Player ---
    private fun playAudio(url: String) {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            chatAdapter.setPlayingAudio(null)
            
            // If clicking the same audio, just stop (toggle behavior)
            // But we don't have previous URL easily available unless we store it.
            // Simplified: Stop current, Start new. 
            // If logic requires pause/resume for SAME file, we need track currentUrl.
        }
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepare() // Use prepareAsync() for network streams to avoid UI freeze, but prepare() is ok for small files or if simple. 
                // Suggest prepareAsync if causing lag.
                setOnCompletionListener {
                    chatAdapter.setPlayingAudio(null)
                }
                start()
            }
            chatAdapter.setPlayingAudio(url)
            Toast.makeText(this, "Playing...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Play failed", Toast.LENGTH_SHORT).show()
            chatAdapter.setPlayingAudio(null)
        }
    }
    
    private fun openFile(url: String, type: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }
    // --- Cleanup & Transaction ---

    private fun createChatBooking() {
        // Need to set correct data based on determination
        // If we determined it's Scheduled (Fixed), we usually shouldn't be creating an "Instant Booking" 
        // unless it's a fallback.
        // Assuming if bookingId was empty, it's a new Instant Chat (Medium).
        
        if (!isInstantBooking && bookingId.isNotEmpty()) {
             // It's likely an existing booking we just joined. No need to create/overwrite in 'instant_bookings'.
             // Just ensure listener setup.
             listenForMessages()
             setupTypingListener()
             return
        }

        val bookingData = hashMapOf(
            "userId" to currentUserId,
            "advisorId" to advisorId,
            "status" to "ongoing",
            "type" to "chat",
            "timestamp" to Timestamp.now(),
            "rate" to ratePerMinute,
            "urgencyLevel" to "Medium", // Default for new chats
            "channelName" to "call_${intent.getStringExtra("BOOKING_ID") ?: "chat_${System.currentTimeMillis()}"}" // 🔥 PRE-EMPTIVE SYNC
        )

        var existingBooking = intent.getStringExtra("BOOKING_ID")
        if (existingBooking.isNullOrEmpty()) {
            val newBookingId = "chat_${System.currentTimeMillis()}"
            // Fix: Update map with actual ID
            bookingData["channelName"] = "call_$newBookingId"
            
            intent.putExtra("BOOKING_ID", newBookingId)
            bookingId = newBookingId 
            
            // Create in Instant Bookings with Channel Name
            db.collection("instant_bookings").document(newBookingId).set(bookingData)
            
            // Ensure videoCalls doc exists with channelName
            db.collection("videoCalls").document(newBookingId).set(
                mapOf("channelName" to "call_$newBookingId", "lastUpdated" to Timestamp.now()), 
                com.google.firebase.firestore.SetOptions.merge()
            )
            
            if (::zegoManager.isInitialized) {
                 joinZegoRoom("call_$newBookingId")
            }
            
        } else {
             // Update Existing
             bookingId = existingBooking
             if (::zegoManager.isInitialized) {
                 joinZegoRoom("call_$existingBooking")
             }
             
             // Ensure videoCalls doc exists with channelName (Fix for History Lookup)
            db.collection("videoCalls").document(bookingId).set(
                mapOf("channelName" to "call_$bookingId", "lastUpdated" to Timestamp.now()), 
                com.google.firebase.firestore.SetOptions.merge()
            )

             if (isInstantBooking) {
                 // Update instant_bookings too
                 db.collection("instant_bookings").document(existingBooking).update(
                     mapOf("status" to "ongoing", "channelName" to "call_$existingBooking")
                 )
             }
        }
        
        listenForMessages()
        setupTypingListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        isChatActive = false
        billingTimer?.cancel()
        heartbeatTimer?.cancel()
        statusListenerRegistration?.remove() // ✅ ADD THIS
        listenerRegistration?.remove()
        mediaPlayer?.release()
        mediaRecorder?.release()
        
        if (::zegoManager.isInitialized) {
            if (bookingId.isNotEmpty()) zegoManager.leaveRoom("call_$bookingId")
        }
        
        endChatSession()
    }
    
    private var isEnding = false

    private fun endChatSession() {
        if (isEnding) return
        isEnding = true
        
        Log.e("ChatPayment", "=== endChatSession CALLED (HYBRID) ===")
        
        if (bookingId.isEmpty()) {
            Log.e("ChatPayment", "bookingId is EMPTY - skipping payment")
            return
        }
        
        // Show progress only if activity is valid
        var pd: ProgressDialog? = null
        if (!isFinishing && !isDestroyed) {
            pd = ProgressDialog(this)
            pd.setMessage("Processing payment...")
            pd.setCancelable(false)
            pd.show()
        }
        
        // Calculate duration
        val durationSeconds = if (startTime > 0) {
            (System.currentTimeMillis() - startTime) / 1000
        } else {
            0
        }
        
        if (durationSeconds < 5) {
            Log.e("ChatPayment", "Duration too short: $durationSeconds - skipping")
            pd?.dismiss()
            finish()
            return
        }
        
        Log.e("ChatPayment", "Duration: $durationSeconds seconds")
        
        // ✅ STEP 1: Update Firestore (triggers Cloud Function)
        val updates = hashMapOf<String, Any>(
            "status" to "ended",
            "endTime" to FieldValue.serverTimestamp(),
            "callEndTime" to FieldValue.serverTimestamp(), // Redundancy
            "duration" to durationSeconds,
            "endReason" to "user_ended",
            "completedBy" to "user_app",
            "bookingId" to bookingId,
            "advisorId" to advisorId,
            "userId" to currentUserId,
            "studentId" to currentUserId // Redundancy
        )
        
        if (callId.isNotEmpty()) {
            db.collection(collectionName).document(callId)
                .update(updates)
                .addOnSuccessListener {
                    Log.i("ChatPayment", "Firestore updated. Cloud Function will process payment.")
                    // ✅ UPDATE: Wait for Server Confirmation
                    waitForServerConfirmation(bookingId, pd)
                }
                .addOnFailureListener { e ->
                    Log.e("ChatPayment", "Firestore update failed: ${e.message}")
                    // Fallback: Ensure document exists
                     db.collection(collectionName).document(callId).set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            waitForServerConfirmation(bookingId, pd)
                        }
                        .addOnFailureListener {
                            pd?.dismiss()
                            Toast.makeText(this@ChatActivity, "Error ending session", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
        } else {
            // No callId, cannot update. Just finish
            Log.e("ChatPayment", "No callId, cannot trigger payment.")
            pd?.dismiss()
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
                Toast.makeText(this, "Session Ended. Payment processing in background.", Toast.LENGTH_LONG).show()
                finish()
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
                    finish()
                }
            }
        }
    }

    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    // Zego Listeners
    override fun onRoomStateChanged(roomID: String, reason: Int, errorCode: Int, extendedData: org.json.JSONObject) {
        if (errorCode != 0) {
            Toast.makeText(this, "Audio Connection Error: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRoomUserUpdate(roomID: String, updateType: im.zego.zegoexpress.constants.ZegoUpdateType, userList: java.util.ArrayList<im.zego.zegoexpress.entity.ZegoUser>) {
        // Show "Advisor joined audio" toast?
        if (updateType == im.zego.zegoexpress.constants.ZegoUpdateType.ADD) {
             for (user in userList) {
                 if (user.userID == advisorId) {
                     Toast.makeText(this, "Advisor connected to audio", Toast.LENGTH_SHORT).show()
                 }
             }
        }
    }

    override fun onRemoteCameraStateUpdate(streamID: String, state: Int) { }
}
