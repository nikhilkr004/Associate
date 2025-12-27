package com.example.associate.NotificationFCM

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.associate.Activities.IncomingCallActivity
import com.example.associate.Activities.VideoCallActivity
import com.example.associate.R
import kotlinx.coroutines.launch

class CallNotificationService : Service() {

    private var ringtone: Ringtone? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    companion object {
        const val CHANNEL_ID = "call_channel"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Acquire WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK or
            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Associate:IncomingCallWakeLock"
        )
        wakeLock?.acquire(60 * 1000L) // 1 minute timeout
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val callId = intent?.getStringExtra("CALL_ID") ?: ""
        val channelName = intent?.getStringExtra("CHANNEL_NAME") ?: ""
        val callerName = intent?.getStringExtra("advisorName") ?: intent?.getStringExtra("title") ?: "Incoming Call"
        val advisorAvatar = intent?.getStringExtra("advisorAvatar") ?: ""
        val advisorId = intent?.getStringExtra("ADVISOR_ID") ?: ""
        val callType = intent?.getStringExtra("CALL_TYPE") ?: "VIDEO"
        val urgencyLevel = intent?.getStringExtra("urgencyLevel") ?: "Medium" // 🔥 Extraction
        
        createNotificationChannel()
        showNotification(callId, channelName, callerName, advisorAvatar, advisorId, callType, urgencyLevel)
        playRingtone()

        return START_NOT_STICKY
    }

    private fun showNotification(callId: String, channelName: String, callerName: String, advisorAvatar: String, advisorId: String, callType: String, urgencyLevel: String) {
        // Custom Layout
        val customView = RemoteViews(packageName, R.layout.notification_call)
        customView.setTextViewText(R.id.tv_caller_name, callerName)
        customView.setImageViewResource(R.id.iv_caller_avatar, R.drawable.user) // Default placeholder

        // Determine Target Activity
        val targetClass = when (callType) {
            "CHAT" -> com.example.associate.Activities.ChatActivity::class.java
            "AUDIO" -> com.example.associate.Activities.AudioCallActivity::class.java
            else -> VideoCallActivity::class.java
        }

        // Accept Intent
        val acceptIntent = Intent(this, targetClass).apply {
            putExtra("CALL_ID", callId)
            putExtra("ROOM_ID", callId) // Map callId to ROOM_ID
            putExtra("BOOKING_ID", callId) // For session reference
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ADVISOR_NAME", callerName) 
            putExtra("ADVISOR_ID", advisorId)
            putExtra("CALL_TYPE", callType)
            putExtra("urgencyLevel", urgencyLevel) 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            acceptIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Intent
        val declineIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "ACTION_DECLINE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this, 
            1, 
            declineIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Full Screen Intent (for Lock Screen)
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("ROOM_ID", callId) // Map callId to ROOM_ID
            putExtra("CHANNEL_NAME", channelName)
            putExtra("title", callerName)
            putExtra("advisorAvatar", advisorAvatar)
            putExtra("ADVISOR_NAME", callerName) 
            putExtra("ADVISOR_ID", advisorId)
            putExtra("CALL_TYPE", callType)
            putExtra("urgencyLevel", urgencyLevel) 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 
            2, 
            fullScreenIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Bind Buttons
        customView.setOnClickPendingIntent(R.id.btn_accept, acceptPendingIntent)
        customView.setOnClickPendingIntent(R.id.btn_decline, declinePendingIntent)

        // Try to start activity directly if permission granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
            try {
                startActivity(fullScreenIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // HIGH priority for background calls/heads-up
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Enable Full Screen Intent for background calls
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen

        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(123, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(123, notification)
        }

        // Load Avatar Asynchronously
        if (advisorAvatar.isNotEmpty()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val bitmap = com.bumptech.glide.Glide.with(applicationContext)
                        .asBitmap()
                        .load(advisorAvatar)
                        .submit(100, 100)
                        .get()
                    
                    // Update Notification
                    customView.setImageViewBitmap(R.id.iv_caller_avatar, bitmap)
                    val updatedNotification = notificationBuilder
                        .setCustomContentView(customView)
                        .setCustomBigContentView(customView)
                        .build()
                        
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(123, updatedNotification)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun playRingtone() {
        if (ringtone == null) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(applicationContext, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone?.isLooping = true
                }
                ringtone?.play()
                
                // Vibrate manually since notification is low priority
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
                } else {
                    vibrator.vibrate(longArrayOf(0, 1000, 1000), 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH // High importance for heads-up/full-screen
            ).apply {
                description = "Channel for incoming video calls"
                setSound(null, null)
                enableVibration(false) // Vibration handled manually or by system if set
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
