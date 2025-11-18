package com.example.associate.Repositorys

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.associate.AppConstants
import com.example.associate.DataClass.WalletDataClass
import com.example.associate.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Timer
import java.util.TimerTask

class VideoCallService : Service() {

    private var paymentTimer: Timer? = null
    private var callStartTime: Long = 0
    private var currentCallId: String = ""
    private var totalDeductions: Double = 0.0
    private val CHANNEL_ID = "video_call_channel"
    private val NOTIFICATION_ID = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            "START_PAYMENT" -> {
                currentCallId = intent.getStringExtra("CALL_ID") ?: ""
                callStartTime = System.currentTimeMillis()
                totalDeductions = 0.0
                startPaymentCalculation()
                showNotification()

                val startedIntent = Intent("PAYMENT_SERVICE_STARTED")
                sendBroadcast(startedIntent)
                android.util.Log.d("VideoCallService", "Payment service STARTED")
            }
            "STOP_PAYMENT" -> {
                android.util.Log.d("VideoCallService", "STOP_PAYMENT received, total: ₹$totalDeductions")
                stopPaymentCalculation()

                // IMPORTANT: Wait for final update to complete before stopping
                updateFinalPayment { success ->
                    android.util.Log.d("VideoCallService", "Final payment update completed: $success")
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Call Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing video call and payment calculations"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun showNotification() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Video Call Ongoing")
                .setContentText("Payment calculation active - ₹10 per 10 seconds")
                .setSmallIcon(R.drawable.videocall)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 1)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPaymentCalculation() {
        paymentTimer?.cancel()
        paymentTimer = Timer()
        paymentTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                calculatePayment()
            }
        }, 0, AppConstants.PAYMENT_INTERVAL_MS)
    }

    private fun calculatePayment() {
        totalDeductions += AppConstants.PAYMENT_AMOUNT

        val intent = Intent("PAYMENT_CALCULATED").apply {
            putExtra("TOTAL_AMOUNT", totalDeductions)
            putExtra("CALL_ID", currentCallId)
        }
        sendBroadcast(intent)

        android.util.Log.d("VideoCallService", "Payment calculated locally: ₹$totalDeductions")
    }

    // CHANGED: Added callback to wait for Firebase update
    private fun updateFinalPayment(onComplete: (Boolean) -> Unit) {
        if (totalDeductions <= 0) {
            android.util.Log.d("VideoCallService", "No amount to deduct")
            onComplete(true)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: run {
            android.util.Log.e("VideoCallService", "User ID not found")
            onComplete(false)
            return
        }

        android.util.Log.d("VideoCallService", "Processing final payment: ₹$totalDeductions for user: $userId")

        // Check wallet balance first
        db.collection("wallets").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val wallet = document.toObject(WalletDataClass::class.java)
                    wallet?.let {
                        if (it.balance >= totalDeductions) {
                            val newBalance = it.balance - totalDeductions
                            val newTotalSpent = it.totalSpent + totalDeductions

                            db.collection("wallets").document(userId)
                                .update(
                                    "balance", newBalance,
                                    "lastUpdated", Timestamp.now(),
                                    "totalSpent", newTotalSpent
                                )
                                .addOnSuccessListener {
                                    android.util.Log.d("VideoCallService", "Wallet updated successfully")
                                    savePaymentRecord(userId, totalDeductions) { success ->
                                        onComplete(success)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("VideoCallService", "Failed to update wallet: ${e.message}")
                                    onComplete(false)
                                }
                        } else {
                            android.util.Log.e("VideoCallService", "Insufficient balance: ${it.balance} < $totalDeductions")
                            notifyInsufficientBalance()
                            onComplete(false)
                        }
                    }
                } else {
                    android.util.Log.e("VideoCallService", "Wallet document not found")
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("VideoCallService", "Failed to fetch wallet: ${e.message}")
                onComplete(false)
            }
    }

    // CHANGED: Added callback for payment record
    private fun savePaymentRecord(userId: String, amount: Double, onComplete: (Boolean) -> Unit) {
        try {
            val paymentRecord = hashMapOf(
                "userId" to userId,
                "amount" to amount,
                "timestamp" to Timestamp.now(),
                "type" to "video_call",
                "callId" to currentCallId,
                "description" to "Video call payment - ₹$amount"
            )

            FirebaseFirestore.getInstance().collection("payments")
                .add(paymentRecord)
                .addOnSuccessListener {
                    android.util.Log.d("VideoCallService", "Payment record saved: ₹$amount")

                    // Also update videoCalls collection with final amount
                    updateVideoCallWithFinalAmount(amount) { success ->
                        onComplete(success)
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VideoCallService", "Failed to save payment record: ${e.message}")
                    onComplete(false)
                }
        } catch (e: Exception) {
            android.util.Log.e("VideoCallService", "Error saving payment record: ${e.message}")
            onComplete(false)
        }
    }

    // NEW: Update videoCalls collection with final amount
    private fun updateVideoCallWithFinalAmount(amount: Double, onComplete: (Boolean) -> Unit) {
        try {
            val updates = hashMapOf<String, Any>(
                "totalAmount" to amount,
                "status" to "completed",
                "callEndTime" to Timestamp.now(),
                "duration" to ((System.currentTimeMillis() - callStartTime) / 1000)
            )

            FirebaseFirestore.getInstance().collection("videoCalls")
                .document(currentCallId)
                .update(updates)
                .addOnSuccessListener {
                    android.util.Log.d("VideoCallService", "Video call updated with final amount: ₹$amount")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VideoCallService", "Failed to update video call: ${e.message}")
                    onComplete(false)
                }
        } catch (e: Exception) {
            android.util.Log.e("VideoCallService", "Error updating video call: ${e.message}")
            onComplete(false)
        }
    }

    private fun notifyInsufficientBalance() {
        try {
            val intent = Intent("INSUFFICIENT_BALANCE").apply {
                putExtra("CALL_ID", currentCallId)
                putExtra("AMOUNT", totalDeductions)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            android.util.Log.e("VideoCallService", "Failed to send insufficient balance notification: ${e.message}")
        }
    }

    private fun stopPaymentCalculation() {
        paymentTimer?.cancel()
        paymentTimer = null
        android.util.Log.d("VideoCallService", "Payment calculation stopped. Total: ₹$totalDeductions")
    }

    override fun onDestroy() {
        stopPaymentCalculation()
        android.util.Log.d("VideoCallService", "Service DESTROYED - Final amount: ₹$totalDeductions")
        super.onDestroy()
    }
}