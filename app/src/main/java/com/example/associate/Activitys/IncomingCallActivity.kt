package com.example.associate.Activitys

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.associate.databinding.ActivityIncomingCallBinding
import com.example.associate.Activitys.VideoCallActivity

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake up screen and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent?.action == "ACTION_DECLINE") {
            stopRingtone()
            finish()
            return
        }

        // Get call details from intent
        val callId = intent.getStringExtra("CALL_ID") ?: ""
        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        val callerName = intent.getStringExtra("title") ?: "Unknown Caller"
        
        binding.tvCallerName.text = callerName

        playRingtone()

        binding.btnAccept.setOnClickListener {
            stopRingtone()
            acceptCall(callId, channelName)
        }

        binding.btnDecline.setOnClickListener {
            stopRingtone()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "ACTION_DECLINE") {
            stopRingtone()
            finish()
        }
    }

    private fun playRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acceptCall(callId: String, channelName: String) {
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }
}
