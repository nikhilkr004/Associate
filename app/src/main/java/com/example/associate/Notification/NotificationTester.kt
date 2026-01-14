package com.example.associate.Notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.associate.R

/**
 * Helper class to test notification functionality
 * Use this to verify notifications are working correctly
 */
class NotificationTester(private val context: Context) {

    companion object {
        private const val TAG = "NotificationTester"
        private const val CHANNEL_ID = "user_calls_channel"
        private const val TEST_NOTIFICATION_ID = 9999
    }

    /**
     * Test if notification channel is created
     */
    fun testNotificationChannel(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            
            return if (channel != null) {
                Log.d(TAG, "✅ Notification channel exists: ${channel.name}")
                true
            } else {
                Log.e(TAG, "❌ Notification channel NOT found")
                createNotificationChannel()
                false
            }
        }
        return true // Channels not needed for older Android versions
    }

    /**
     * Create notification channel if it doesn't exist
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "User Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls and bookings"
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }

    /**
     * Send a test notification
     */
    fun sendTestNotification() {
        testNotificationChannel()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle("Test Notification")
            .setContentText("If you see this, notifications are working! ✅")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(TEST_NOTIFICATION_ID, notification)
        Log.d(TAG, "✅ Test notification sent")
    }

    /**
     * Check if notifications are enabled for the app
     */
    fun areNotificationsEnabled(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val enabled = notificationManager.areNotificationsEnabled()
            if (enabled) {
                Log.d(TAG, "✅ Notifications are enabled")
            } else {
                Log.e(TAG, "❌ Notifications are DISABLED in system settings")
            }
            enabled
        } else {
            true
        }
    }

    /**
     * Run all notification tests
     */
    fun runAllTests(): TestResult {
        Log.d(TAG, "========== NOTIFICATION TESTS ==========")
        
        val channelExists = testNotificationChannel()
        val notificationsEnabled = areNotificationsEnabled()
        
        Log.d(TAG, "========== TEST RESULTS ==========")
        Log.d(TAG, "Channel exists: $channelExists")
        Log.d(TAG, "Notifications enabled: $notificationsEnabled")
        
        val allPassed = channelExists && notificationsEnabled
        
        if (allPassed) {
            Log.d(TAG, "✅ ALL TESTS PASSED - Sending test notification...")
            sendTestNotification()
        } else {
            Log.e(TAG, "❌ SOME TESTS FAILED - Check logs above")
        }
        
        return TestResult(
            channelExists = channelExists,
            notificationsEnabled = notificationsEnabled,
            allPassed = allPassed
        )
    }

    data class TestResult(
        val channelExists: Boolean,
        val notificationsEnabled: Boolean,
        val allPassed: Boolean
    )
}

/**
 * HOW TO USE:
 * 
 * In any Activity (e.g., MainActivity):
 * 
 * val tester = NotificationTester(this)
 * val result = tester.runAllTests()
 * 
 * if (result.allPassed) {
 *     Toast.makeText(this, "Notifications working!", Toast.LENGTH_SHORT).show()
 * } else {
 *     Toast.makeText(this, "Notification setup issue - check logs", Toast.LENGTH_LONG).show()
 * }
 */

// Updated for repository activity
