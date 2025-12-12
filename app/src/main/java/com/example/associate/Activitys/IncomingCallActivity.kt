package com.example.associate.Activitys

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
        
        binding.tvCallerName.text = callerName

        // Load Avatar
        if (advisorAvatar.isNotEmpty()) {
            try {
                com.bumptech.glide.Glide.with(this)
                    .load(advisorAvatar)
                    .placeholder(R.drawable.user)
                    .circleCrop()
                    .into(binding.ivCallerImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.btnAccept.setOnClickListener {
            stopService()
            acceptCall(callId, channelName)
        }

        binding.btnDecline.setOnClickListener {
            stopService()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "ACTION_DECLINE") {
            stopService()
            finish()
        }
    }

    private fun stopService() {
        val intent = Intent(this, com.example.associate.NotificationFCM.CallNotificationService::class.java).apply {
            action = com.example.associate.NotificationFCM.CallNotificationService.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    private fun acceptCall(callId: String, channelName: String) {
        stopService()
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
