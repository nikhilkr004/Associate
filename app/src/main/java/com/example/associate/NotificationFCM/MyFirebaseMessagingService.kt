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
        
        // Check if message contains notification payload
        message.notification?.let { notification ->
            Log.d(TAG, "Notification Title: ${notification.title}")
            Log.d(TAG, "Notification Body: ${notification.body}")
            
            showNotification(
                title = notification.title ?: "New Notification",
                body = notification.body ?: "You have a new update",
                data = message.data
            )
        }
        
        // Check if message contains data payload
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${message.data}")
            
            // Check for incoming call
            if (message.data["type"] == "call" || message.data.containsKey("callId")) {
                showIncomingCallNotification(message.data)
                return
            }
            
            // If only data payload (no notification), show notification manually
            if (message.notification == null) {
                val title = message.data["title"] ?: "New Notification"
                val body = message.data["message"] ?: message.data["body"] ?: "You have a new update"
                
                showNotification(title, body, message.data)
            }
        }
    }

    private fun showIncomingCallNotification(data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val callId = data["callId"] ?: ""
        val channelName = data["channelName"] ?: ""
        val callerName = data["title"] ?: "Incoming Call"
        
        val intent = Intent(this, com.example.associate.Activitys.IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("title", callerName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(callerName)
            .setContentText("Incoming Video Call")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true) // Keep notification until answered or timed out
            
        // Add Accept/Decline actions to notification as well (optional but good)
        val acceptIntent = Intent(this, com.example.associate.Activitys.VideoCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 
            callId.hashCode() + 1, 
            acceptIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, com.example.associate.Activitys.IncomingCallActivity::class.java).apply {
            action = "ACTION_DECLINE"
            putExtra("CALL_ID", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode() + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationBuilder.addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
        notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            
        notificationManager.notify(callId.hashCode(), notificationBuilder.build())
        Log.d(TAG, "Incoming call notification displayed")
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
            .setSmallIcon(R.drawable.notification) // Make sure this drawable exists
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
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
