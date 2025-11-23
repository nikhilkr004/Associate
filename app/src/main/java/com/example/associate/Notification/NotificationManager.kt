package com.example.associate.Notification

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class NotificationManager {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "NotificationManager"
    }

    fun sendBookingNotificationToAdvisor(
        advisorId: String,
        studentName: String,
        bookingId: String,
        advisorName: String
    ) {
        // Pehle advisor ka data fetch karen
        db.collection("advisors").document(advisorId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val advisorFCMToken = document.getString("fcmToken")

                    // Firestore mein notification record create karen
                    createNotificationRecord(advisorId, studentName, bookingId, advisorName)

                    Log.d(TAG, "Booking notification sent to advisor: $advisorName")
                } else {
                    Log.w(TAG, "Advisor document not found: $advisorId")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch advisor data: ${e.message}")
            }
    }

    private fun createNotificationRecord(
        advisorId: String,
        studentName: String,
        bookingId: String,
        advisorName: String
    ) {
        val notificationData = hashMapOf(
            "advisorId" to advisorId,
            "studentName" to studentName,
            "bookingId" to bookingId,
            "advisorName" to advisorName,
            "type" to "instant_booking",
            "title" to "New Instant Session Request",
            "message" to "$studentName wants an instant session with you",
            "timestamp" to Timestamp.now(),
            "isRead" to false,
            "notificationSeen" to false
        )

        db.collection("notifications")
            .add(notificationData)
            .addOnSuccessListener {
                Log.d(TAG, "Notification record created successfully for advisor: $advisorId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create notification record: ${e.message}")
            }
    }
}