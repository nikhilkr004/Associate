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
        const val CHANNEL_ID = "call_channel_v3"
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
        // 🔥 Robust Extraction: ChannelName -> RoomID -> CallID
        val channelName = intent?.getStringExtra("CHANNEL_NAME") ?: intent?.getStringExtra("ROOM_ID") ?: callId
        val callerName = intent?.getStringExtra("advisorName") ?: intent?.getStringExtra("title") ?: "Incoming Call"
        val advisorAvatar = intent?.getStringExtra("advisorAvatar") ?: ""
        val advisorId = intent?.getStringExtra("ADVISOR_ID") ?: ""
        val callType = intent?.getStringExtra("CALL_TYPE") ?: "VIDEO"
        val urgencyLevel = intent?.getStringExtra("urgencyLevel") ?: "Medium" // 🔥 Extraction
        val bookingId = intent?.getStringExtra("BOOKING_ID") ?: "" // 🔥 No fallback to callId
        
        android.util.Log.e("DEBUG_SERVICE", "Service Started. CallID=$callId, BookingID=$bookingId, Urgency=$urgencyLevel")
        
        createNotificationChannel()
        showNotification(callId, channelName, callerName, advisorAvatar, advisorId, callType, urgencyLevel, bookingId)
        playRingtone()

        return START_NOT_STICKY
    }

    private fun showNotification(callId: String, channelName: String, callerName: String, advisorAvatar: String, advisorId: String, callType: String, urgencyLevel: String, bookingId: String) {
        
        // Determine Target Activity
        val targetClass = when (callType) {
            "CHAT" -> com.example.associate.Activities.ChatActivity::class.java
            "AUDIO" -> com.example.associate.Activities.AudioCallActivity::class.java
            else -> VideoCallActivity::class.java
        }

        // Full Screen Intent (IncomingCallActivity)
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            // putExtra("ROOM_ID", callId) // Removed implicit RoomID
            putExtra("CHANNEL_NAME", channelName)
            putExtra("title", callerName)
            putExtra("advisorAvatar", advisorAvatar)
            putExtra("ADVISOR_NAME", callerName) 
            putExtra("ADVISOR_ID", advisorId)
            putExtra("CALL_TYPE", callType)
            putExtra("urgencyLevel", urgencyLevel) 
            putExtra("BOOKING_ID", bookingId) // 🔥 PROPAGE BOOKING ID to Activity
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode() + 2, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔥 PRIORITY LAUNCH: Attempt to start Activity directly
        // We do this immediately to race against the notification tray
        try {
             android.util.Log.e("DEBUG_SERVICE", "FORCED LAUNCH: Attempting PendingIntent.send()")
             // Use PendingIntent.send() as it often has higher privileges than startActivity()
             // from the background.
             fullScreenPendingIntent.send()
             android.util.Log.e("DEBUG_SERVICE", "FORCED LAUNCH: Success (No Exception)")
        } catch (e: Exception) {
             android.util.Log.e("DEBUG_SERVICE", "FORCED LAUNCH: FAILED Exception", e)
             // Fallback to standard start
             try { 
                  android.util.Log.e("DEBUG_SERVICE", "FALLBACK LAUNCH: Attempting startActivity()")
                  startActivity(fullScreenIntent) 
             } catch (z: Exception) {
                  android.util.Log.e("DEBUG_SERVICE", "FALLBACK LAUNCH: FAILED Exception", z)
             }
        }

        // Accept Intent
        val acceptIntent = Intent(this, targetClass).apply {
            putExtra("CALL_ID", callId)
            // putExtra("ROOM_ID", callId) // Removed implicit RoomID
            putExtra("BOOKING_ID", bookingId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ADVISOR_NAME", callerName) 
            putExtra("ADVISOR_ID", advisorId)
            putExtra("CALL_TYPE", callType)
            putExtra("urgencyLevel", urgencyLevel) 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), acceptIntent, // Unique Request Code
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Intent
        val declineIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "ACTION_DECLINE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this, callId.hashCode() + 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle("Incoming $callType Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Essential for Lock Screen
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Add Actions
            .addAction(R.drawable.call_end, "Decline", declinePendingIntent)
            .addAction(R.drawable.call, "Accept", acceptPendingIntent)

        // Android 12+ CallStyle (Highly Recommended for Background Starts)
        /* 
         * Note: CallStyle requires API 31+ and Person object. 
         * For robustness, we stick to standard High Priority + FullScreenIntent unless requested.
         * The key to background start is FullScreenIntent + High Priority Channel + Permission.
         */

        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ requires SHORT_SERVICE for background starts
            startForeground(123, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            // Older versions: rely on manifest or default behavior
            startForeground(123, notification)
        }
        
        // Asynchronous Avatar Load - Minimal impact on initial show
         if (advisorAvatar.isNotEmpty()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val bitmap = com.bumptech.glide.Glide.with(applicationContext)
                        .asBitmap()
                        .load(advisorAvatar)
                        .submit()
                        .get()
                    
                    notificationBuilder.setLargeIcon(bitmap)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(123, notificationBuilder.build())
                } catch (e: Exception) { }
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
                NotificationManager.IMPORTANCE_MAX // MAX importance for popup
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

// Updated for repository activity
