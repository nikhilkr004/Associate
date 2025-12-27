package com.example.associate.Activities

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.associate.R
import com.example.associate.databinding.ActivityIncomingCallBinding

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var advisorId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake up screen and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // For older versions or additional safety
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent?.action == "ACTION_DECLINE") {
            stopService()
            finish()
            return
        }

        // Get call details from intent
        val callId = intent.getStringExtra("CALL_ID") ?: ""
        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        val callerName = intent.getStringExtra("title") ?: "Unknown Caller"
        val advisorAvatar = intent.getStringExtra("advisorAvatar") ?: ""
        val callType = intent.getStringExtra("CALL_TYPE") ?: "VIDEO"
        advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""
        
        binding.tvCallerName.text = callerName
        
        // Update UI based on Call Type
        setupUIForType(callType, callerName)

        binding.btnAccept.setOnClickListener {
            acceptCall(callId, channelName, callerName, advisorId, callType)
        }
        
        // New Start Chat Button
        binding.btnStartChat.setOnClickListener {
            acceptCall(callId, channelName, callerName, advisorId, callType)
        }
        
        // New Reject Text
        binding.tvRejectChat.setOnClickListener {
            stopService()
            finish()
        }

        binding.btnDecline.setOnClickListener {
            stopService()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        
        if (intent.action == "ACTION_DECLINE") {
            stopService()
            finish()
            return
        }
        
        // Update UI for new call
        val callerName = intent.getStringExtra("title") ?: "Unknown Caller"
        val advisorAvatar = intent.getStringExtra("advisorAvatar") ?: ""
        val callType = intent.getStringExtra("CALL_TYPE") ?: "VIDEO"
        
        setupUIForType(callType, callerName)
        
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
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("advisors").document(advisorId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Check for nested basicInfo first (AdvisorDataClass structure)
                    val basicInfo = document.get("basicInfo") as? Map<*, *>
                    var avatarUrl = basicInfo?.get("profileImage") as? String
                    
                    // Fallback to root level if not found
                    if (avatarUrl.isNullOrEmpty()) {
                        avatarUrl = document.getString("profileImage") ?: document.getString("profileimage")
                    }
                    
                    if (!avatarUrl.isNullOrEmpty()) {
                        // Update Intent for next activity
                        intent.putExtra("advisorAvatar", avatarUrl)
                        intent.putExtra("ADVISOR_AVATAR", avatarUrl)
                        loadAvatar(avatarUrl)
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    private fun loadAvatar(url: String) {
        try {
            com.bumptech.glide.Glide.with(this)
                .load(url)
                .placeholder(R.drawable.user)
                .circleCrop()
                .into(binding.ivCallerImage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUIForType(callType: String, callerName: String) {
        if (callType == "CHAT") {
            // Hide standard Call elements
            binding.tvCallerName.visibility = android.view.View.GONE
            binding.tvCallType.visibility = android.view.View.GONE
            binding.btnAccept.visibility = android.view.View.GONE
            binding.btnDecline.visibility = android.view.View.GONE
            // binding.tvAcceptLabel.visibility = android.view.View.GONE // If exists
            
            // Show Chat elements
            binding.tvChatTitle.visibility = android.view.View.VISIBLE
            binding.tvChatSubtitle.visibility = android.view.View.VISIBLE
            binding.btnStartChat.visibility = android.view.View.VISIBLE
            binding.tvRejectChat.visibility = android.view.View.VISIBLE
            
            // Adjust Background
            binding.root.background = null
            binding.root.setBackgroundColor(android.graphics.Color.WHITE)
            
            // Update Text
            binding.tvChatTitle.text = "Incoming Chat Request"
            binding.tvCallerName.text = callerName // Reusing TextView if needed, but we hid it.
            // Update subtitle with name if desired: "$callerName has accepted..."
            binding.tvChatSubtitle.text = "$callerName has accepted chat request.\nPlease accept to initiate the chat."

            // Text Color tweaks for White BG
            // binding.tvCallerName.setTextColor(...) // Already hidden
            
        } else {
             // Standard Call UI
            binding.tvCallerName.text = if (callType =="AUDIO") "$callerName (Audio Call)" else callerName
            // Ensure visibility (in case of re-use)
            binding.tvCallerName.visibility = android.view.View.VISIBLE
            binding.tvCallType.visibility = android.view.View.VISIBLE
            binding.btnAccept.visibility = android.view.View.VISIBLE
            binding.btnDecline.visibility = android.view.View.VISIBLE
            
             binding.tvChatTitle.visibility = android.view.View.GONE
            binding.tvChatSubtitle.visibility = android.view.View.GONE
            binding.btnStartChat.visibility = android.view.View.GONE
            binding.tvRejectChat.visibility = android.view.View.GONE
            
             binding.root.setBackgroundResource(R.drawable.gradient_bg)
        }
    }

    private fun stopService() {
        val intent = Intent(this, com.example.associate.NotificationFCM.CallNotificationService::class.java).apply {
            action = com.example.associate.NotificationFCM.CallNotificationService.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    private fun acceptCall(callId: String, channelName: String, advisorName: String, advisorId: String, callType: String) {
        stopService()
        
        val advisorAvatar = intent.getStringExtra("advisorAvatar") ?: "" 
        val urgencyLevel = intent.getStringExtra("urgencyLevel")

        val targetActivity = when (callType) {
            "CHAT" -> ChatActivity::class.java
            "AUDIO" -> AudioCallActivity::class.java
            else -> VideoCallActivity::class.java
        }

        val newIntent = Intent(this, targetActivity).apply {
            putExtra("CALL_ID", callId)
            putExtra("ROOM_ID", callId) // Map callId to ROOM_ID for direct lookup
            putExtra("BOOKING_ID", callId) // Pass callId as BOOKING_ID (Session)
            putExtra("CHAT_ID", "")        // Empty to force persistent User-Advisor chat generation
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ADVISOR_NAME", advisorName)
            putExtra("ADVISOR_ID", advisorId)
            putExtra("ADVISOR_AVATAR", advisorAvatar)
            putExtra("CALL_TYPE", callType)
            putExtra("urgencyLevel", urgencyLevel)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(newIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
