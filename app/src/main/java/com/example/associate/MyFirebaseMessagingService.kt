package com.example.associate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

import com.example.associate.Repositorys.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "associate_channel"
        const val CHANNEL_NAME = "Associate Notifications"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        // Save this token to your server/database for sending notifications later
        saveTokenToDatabase(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "New Message"
            val body = notification.body ?: "You have a new notification"

            // Show notification
            sendNotification(title, body, remoteMessage.data)
        }

        // Also handle data payload if needed
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            val title = remoteMessage.data["title"] ?: "New Message"
            val body = remoteMessage.data["body"] ?: "You have a new notification"

            sendNotification(title, body, remoteMessage.data)
        }
    }

    private fun sendNotification(title: String, message: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Add any data you want to pass to the activity
            putExtra("notification_data", data.toString())
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification) // Make sure this icon exists in your drawable folder
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun saveTokenToDatabase(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            // Launch a coroutine to save the token
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (val result = UserRepository().saveFCMToken(userId, token)) {
                        is UserRepository.Result.Success -> {
                            Log.d("FCM", "FCM token saved successfully")
                        }
                        is UserRepository.Result.Failure -> {
                            Log.e("FCM", "Failed to save FCM token", result.exception)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FCM", "Error saving FCM token", e)
                }
            }
        } ?: run {
            Log.d("FCM", "User not logged in, cannot save FCM token")
            // You might want to save the token temporarily and associate it with the user later
        }
    }
}