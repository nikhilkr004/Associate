# 📱 Advisor App - Complete Implementation Guide

## Overview

This guide provides complete code for the **Advisor App** to:
1. ✅ **Receive booking notifications** when users book sessions
2. ✅ **Send video call notifications** to users
3. ✅ Work in background/foreground/killed state

---

## 🔥 Firebase Cloud Functions (Already Deployed)

The Firebase functions are already deployed and working:

### Function 1: `sendBookingNotification`
- **Trigger**: Document created in `bookings/{bookingId}`
- **Target**: Advisors
- **Sends**: Booking request notification

### Function 2: `sendCallNotification`
- **Trigger**: Document created in `videoCalls/{callId}`
- **Target**: Users  
- **Sends**: Incoming call notification

---

## 📋 Step 1: Create FCM Service for Advisor App

### File: `AdvisorFirebaseMessagingService.kt`

**Location**: `app/src/main/java/com/yourpackage/notifications/`

```kotlin
package com.yourpackage.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yourpackage.MainActivity
import com.yourpackage.R

class AdvisorFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AdvisorFCMService"
        private const val CHANNEL_ID = "advisor_session_alerts"
        private const val CHANNEL_NAME = "Session Alerts"
        private const val CHANNEL_DESCRIPTION = "Notifications for new session bookings"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Advisor FCM Service Created")
    }

    /**
     * Called when a new FCM token is generated
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")
        
        // Save token to Firestore for current advisor
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
            "booking_request", "instant_booking" -> {
                // New booking request - show notification
                Log.d(TAG, "Handling booking request notification")
                showBookingNotification(message.data, message.notification)
                return
            }
        }
        
        // Fallback: Check if message contains notification payload
        message.notification?.let { notification ->
            Log.d(TAG, "Notification Title: ${notification.title}")
            Log.d(TAG, "Notification Body: ${notification.body}")
            
            showStandardNotification(
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
            
            showStandardNotification(title, body, message.data)
        }
    }

    /**
     * Show booking notification with action buttons
     */
    private fun showBookingNotification(data: Map<String, String>, notification: RemoteMessage.Notification?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val bookingId = data["bookingId"] ?: ""
        val studentName = data["studentName"] ?: "Student"
        val title = notification?.title ?: "New Session Request 🎯"
        val body = notification?.body ?: "$studentName wants to connect with you"
        
        Log.d(TAG, "Showing booking notification - BookingID: $bookingId, Student: $studentName")
        
        // Intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BOOKING_ID", bookingId)
            putExtra("STUDENT_NAME", studentName)
            putExtra("NOTIFICATION_TYPE", "booking_request")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            bookingId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Replace with your icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        
        // Add sound and vibration
        notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
        
        // Show notification
        notificationManager.notify(bookingId.hashCode(), notificationBuilder.build())
        Log.d(TAG, "Booking notification displayed")
    }

    /**
     * Show standard notification
     */
    private fun showStandardNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            .setSmallIcon(R.drawable.ic_notification) // Replace with your icon
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
        
        Log.d(TAG, "Standard notification displayed: $title")
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel for booking notifications
            val bookingChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(bookingChannel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    /**
     * Save FCM token to Firestore for the current advisor
     */
    private fun saveFCMTokenToFirestore(token: String) {
        val advisorId = FirebaseAuth.getInstance().currentUser?.uid
        
        if (advisorId != null) {
            val db = FirebaseFirestore.getInstance()
            
            // Update advisor document with FCM token
            db.collection("advisors")
                .document(advisorId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM Token saved to Firestore for advisor: $advisorId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save FCM token: ${e.message}")
                    
                    // If update fails, try to set the field (in case document doesn't exist yet)
                    db.collection("advisors")
                        .document(advisorId)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "FCM Token set in Firestore for advisor: $advisorId")
                        }
                        .addOnFailureListener { e2 ->
                            Log.e(TAG, "Failed to set FCM token: ${e2.message}")
                        }
                }
        } else {
            Log.w(TAG, "Cannot save FCM token - advisor not logged in")
        }
    }
}
```

---

## 📋 Step 2: Register Service in AndroidManifest.xml

Add this inside the `<application>` tag:

```xml
<!-- Firebase Cloud Messaging Service -->
<service
    android:name=".notifications.AdvisorFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>

<!-- Default Notification Channel -->
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="advisor_session_alerts" />

<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@drawable/ic_notification" />
```

---

## 📋 Step 3: Add Required Permissions

Add these permissions in `AndroidManifest.xml` (before `<application>` tag):

```xml
<!-- FCM Permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
```

---

## 📋 Step 4: Add Firebase Dependencies

In `app/build.gradle`:

```gradle
dependencies {
    // Firebase
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-messaging-ktx'
    implementation 'com.google.firebase:firebase-firestore-ktx'
    implementation 'com.google.firebase:firebase-auth-ktx'
}
```

---

## 📋 Step 5: Request Notification Permission (Android 13+)

In your MainActivity or login screen:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.d("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Request notification permission for Android 13+
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    Log.d("MainActivity", "Notification permission already granted")
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
```

---

## 📋 Step 6: Send Video Call to User

### Function to Create Video Call Document

Add this function in your advisor's HomeFragment or wherever you initiate calls:

```kotlin
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

fun sendVideoCallToUser(userId: String, userName: String) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    
    val advisorId = auth.currentUser?.uid ?: return
    val advisorName = auth.currentUser?.displayName ?: "Advisor"
    
    // Generate unique channel name
    val channelName = "call_${advisorId}_${userId}_${System.currentTimeMillis()}"
    
    // Create call document
    val callData = hashMapOf(
        "receiverId" to userId,  // User who will receive the call
        "callerId" to advisorId,  // Advisor making the call
        "advisorName" to advisorName,
        "userName" to userName,
        "channelName" to channelName,
        "status" to "initiated",
        "createdAt" to Timestamp.now()
    )
    
    db.collection("videoCalls")
        .add(callData)
        .addOnSuccessListener { documentReference ->
            val callId = documentReference.id
            Log.d("AdvisorHome", "Video call created: $callId")
            
            // Navigate to video call activity
            val intent = Intent(this, VideoCallActivity::class.java).apply {
                putExtra("CALL_ID", callId)
                putExtra("CHANNEL_NAME", channelName)
                putExtra("USER_ID", userId)
                putExtra("USER_NAME", userName)
            }
            startActivity(intent)
        }
        .addOnFailureListener { e ->
            Log.e("AdvisorHome", "Failed to create call: ${e.message}")
            Toast.makeText(this, "Failed to initiate call", Toast.LENGTH_SHORT).show()
        }
}
```

---

## 🧪 Testing Guide

### Test 1: Booking Notification (Advisor Receives)

1. **Login to Advisor App**
2. **Get Advisor UID** from Firebase Console
3. **Create Test Booking** in Firestore:
   - Collection: `bookings`
   - Document: Auto-ID
   - Fields:
     ```
     advisorId: <advisor-uid>
     studentName: "Test Student"
     ```
4. **Expected**: Advisor receives notification "New Session Request 🎯"

### Test 2: Send Video Call (User Receives)

1. **Login to Advisor App**
2. **Get User UID** from Firebase Console
3. **Call** `sendVideoCallToUser(userId, "Test User")`
4. **Expected**: 
   - Document created in `videoCalls` collection
   - User receives "Incoming Video Call 🎥" notification

---

## ✅ Checklist

- [ ] Created `AdvisorFirebaseMessagingService.kt`
- [ ] Registered service in `AndroidManifest.xml`
- [ ] Added required permissions
- [ ] Added Firebase dependencies
- [ ] Requested notification permission
- [ ] Implemented `sendVideoCallToUser()` function
- [ ] Tested booking notification
- [ ] Tested video call sending

---

## 🎯 Summary

### What This Implementation Does:

✅ **Receives Booking Notifications** when users book sessions  
✅ **Sends Video Calls** to users via Firestore  
✅ **Works in Background/Killed State** using FCM  
✅ **Saves FCM Token** automatically on login  
✅ **Handles Notification Routing** based on type  

### Firebase Functions (Already Deployed):

✅ `sendBookingNotification` - Sends to advisors  
✅ `sendCallNotification` - Sends to users  

---

## 📝 Important Notes

1. **Package Name**: Replace `com.yourpackage` with your actual package name
2. **Icon**: Replace `R.drawable.ic_notification` with your notification icon
3. **MainActivity**: Update the class name if different
4. **FCM Token**: Automatically saved when advisor logs in
5. **Testing**: Use Firestore Console to create test documents

---

## 🆘 Troubleshooting

### Notification Not Appearing:
- Check FCM token is saved in Firestore (`advisors/{uid}/fcmToken`)
- Verify notification permission granted
- Check Firebase Functions logs
- Verify document structure in Firestore

### Call Not Sending:
- Verify `receiverId` is correct user UID
- Check Firestore rules allow write to `videoCalls`
- Verify Firebase Functions are deployed

---

**Ready to implement! Copy-paste the code and follow the steps.** 🚀
