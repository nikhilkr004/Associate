package com.example.associate.NotificationFCM

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.associate.MainActivity
import com.example.associate.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "user_calls_channel"
        private const val CHANNEL_NAME = "User Calls"
        private const val CHANNEL_DESCRIPTION = "Notifications for incoming calls and bookings"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "FCM Service Created")
    }

    /**
     * Called when a new FCM token is generated
     * This happens on app install, reinstall, or token refresh
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")
        
        // Save token to Firestore for current user
        saveFCMTokenToFirestore(token)
    }

    /**
     * Called when a message is received from Firebase
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "Message received from: ${message.from}")
        Log.d(TAG, "Message data: ${message.data}")
        
        // Get notification type from data payload
        val notificationType = message.data["notificationType"] ?: message.data["type"] ?: ""
        
        Log.d(TAG, "Notification Type: $notificationType")
        
        // Handle based on notification type
        when (notificationType) {
            "video_call", "call_offer", "call" -> {
                // Incoming video call - show full screen call notification
                Log.d(TAG, "Handling incoming video call")
                showIncomingCallNotification(message.data)
                return
            }
            "booking_request", "instant_booking" -> {
                // Booking notification - show standard notification
                Log.d(TAG, "Handling booking notification")
                val title = message.notification?.title ?: message.data["title"] ?: "New Booking"
                val body = message.notification?.body ?: message.data["body"] ?: "You have a new booking"
                showNotification(title, body, message.data)
                return
            }
        }
        
        // Fallback: Check if message contains notification payload
        message.notification?.let { notification ->
            Log.d(TAG, "Notification Title: ${notification.title}")
            Log.d(TAG, "Notification Body: ${notification.body}")
            
            showNotification(
                title = notification.title ?: "New Notification",
                body = notification.body ?: "You have a new update",
                data = message.data
            )
            return
        }
        
        // If only data payload (no notification), show notification manually
        if (message.data.isNotEmpty()) {
            val title = message.data["title"] ?: "New Notification"
            val body = message.data["message"] ?: message.data["body"] ?: "You have a new update"
            
            showNotification(title, body, message.data)
        }
    }

    private fun showIncomingCallNotification(data: Map<String, String>) {
        val callId = data["callId"] ?: ""
        val channelName = data["channelName"] ?: ""
        val callerName = data["advisorName"] ?: data["title"] ?: "Incoming Call"
        val callerAvatar = data["advisorAvatar"] ?: ""
        
        // Robust ID extraction
        val advisorId = data["advisorId"] ?: data["senderId"] ?: data["advisorUid"] ?: data["uid"] ?: data["id"] ?: ""
        val callType = data["callType"] ?: "VIDEO"
        val urgencyLevel = data["urgencyLevel"] ?: "Medium" // 🔥 Default to Medium/Instant
        
        Log.d(TAG, "Notification Payload Data Keys: ${data.keys}")
        Log.d(TAG, "Starting CallNotificationService - CallID: $callId, Caller: $callerName, Type: $callType, Urgency: $urgencyLevel")
        
        val serviceIntent = Intent(this, CallNotificationService::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("advisorName", callerName)
            putExtra("advisorAvatar", callerAvatar)
            putExtra("ADVISOR_ID", advisorId)
            putExtra("CALL_TYPE", callType)
            putExtra("urgencyLevel", urgencyLevel) // 🔥 Propagate to Service
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    /**
     * Display notification to user
     */
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open app when notification is clicked
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add data to intent
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        
        // Add sound and vibration
        notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
        
        // Show notification
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Log.d(TAG, "Notification displayed: $title")
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel 1: User Calls Channel (for general notifications)
            val userCallsChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(userCallsChannel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
            
            // Channel 2: Call Channel (for incoming video calls)
            val callChannel = NotificationChannel(
                "call_channel",
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming video calls"
                enableLights(true)
                enableVibration(true)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(callChannel)
            Log.d(TAG, "Call notification channel created: call_channel")
        }
    }

    /**
     * Save FCM token to Firestore for the current user
     */
    private fun saveFCMTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        
        if (userId != null) {
            val db = FirebaseFirestore.getInstance()
            
            // Update user document with FCM token
            db.collection("users")
                .document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM Token saved to Firestore for user: $userId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save FCM token: ${e.message}")
                }
            
            // Also check if user is an advisor and update advisor collection
            db.collection("advisors")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        document.reference.update("fcmToken", token)
                            .addOnSuccessListener {
                                Log.d(TAG, "FCM Token saved to advisor document")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking advisor document: ${e.message}")
                }
        } else {
            Log.w(TAG, "Cannot save FCM token - user not logged in")
        }
    }
}
