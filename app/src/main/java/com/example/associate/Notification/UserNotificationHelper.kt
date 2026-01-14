package com.example.associate.Notification



import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class UserNotificationHelper {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "UserNotificationHelper"
    }

    fun sendBookingAlertToAdvisor(
        advisorId: String,
        studentName: String,
        bookingId: String,
        advisorName: String
    ) {
        // Firestore mein alert record create karen
        createBookingAlert(advisorId, studentName, bookingId, advisorName)
    }

    private fun createBookingAlert(
        advisorId: String,
        studentName: String,
        bookingId: String,
        advisorName: String
    ) {
        val alertData = hashMapOf(
            "advisorId" to advisorId,
            "studentName" to studentName,
            "bookingId" to bookingId,
            "advisorName" to advisorName,
            "alertType" to "new_booking",
            "title" to "New Session Request",
            "message" to "$studentName wants an instant session",
            "createdAt" to Timestamp.now(),
            "isSeen" to false,
            "alertActive" to true
        )

        db.collection("booking_alerts")
            .add(alertData)
            .addOnSuccessListener {
                Log.d(TAG, "Booking alert sent to advisor: $advisorName")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send booking alert: ${e.message}")
            }
    }
}
// Updated for repository activity
