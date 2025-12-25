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
        
        binding.tvCallerName.text = callerName
        
        // Update UI based on Call Type
        if (callType == "AUDIO") {
             binding.tvCallerName.text = "$callerName (Audio Call)"
        }

        // Load Avatar
        if (advisorAvatar.isNotEmpty()) {
            loadAvatar(advisorAvatar)
        } else {
            val advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""
            if (advisorId.isNotEmpty()) {
                fetchAdvisorAvatar(advisorId)
            }
        }

        val advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""

        binding.btnAccept.setOnClickListener {
            stopService()
            acceptCall(callId, channelName, callerName, advisorId, callType)
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
        
        binding.tvCallerName.text = callerName
        
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

    private fun stopService() {
        val intent = Intent(this, com.example.associate.NotificationFCM.CallNotificationService::class.java).apply {
            action = com.example.associate.NotificationFCM.CallNotificationService.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    private fun acceptCall(callId: String, channelName: String, advisorName: String, advisorId: String, callType: String) {
        android.widget.Toast.makeText(this, "Accepting ${if(callType=="AUDIO") "Audio" else "Video"} call...", android.widget.Toast.LENGTH_SHORT).show()
        val advisorAvatar = intent.getStringExtra("advisorAvatar") ?: "" // Retrieve avatar
        
        stopService()
        val targetActivity = if (callType == "AUDIO") AudioCallActivity::class.java else VideoCallActivity::class.java
        
        // 🔥 Capture from Activity Intent first!
        val urgencyLevel = this.intent.getStringExtra("urgencyLevel") 
        
        val newIntent = Intent(this, targetActivity).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ADVISOR_NAME", advisorName)
            putExtra("ADVISOR_ID", advisorId)
            putExtra("CALL_TYPE", callType)
            putExtra("ADVISOR_AVATAR", advisorAvatar)
            putExtra("urgencyLevel", urgencyLevel) // Pass captured value
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(newIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
