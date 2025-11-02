package com.example.associate.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class SessionBookingDataClass(
    // Basic Booking Info
    val bookingId: String = "",
    val studentId: String = "",
    val advisorId: String = "",
    val studentName: String = "",
    val advisorName: String = "",

    // User Provided Details
    val purpose: String = "",
    val preferredLanguage: String = "",
    val additionalNotes: String = "",
    val urgencyLevel: String = "",

    // Session Details
    val sessionType: String = "instant_call",

    // 🔥 FIX: Use Timestamp but with proper constructor
    val bookingTimestamp: Timestamp? = null,

    // Response Time Tracking
    val advisorResponseDeadline: Long = 0,
    val advisorRespondedAt: Long = 0,
    val advisorCalledAt: Long = 0,

    // Payment Details
    val sessionAmount: Double = 100.0,
    val paymentStatus: String = "pending",
    val paymentTimestamp: Long = 0,

    // Booking Status
    val bookingStatus: String = "pending"
) : Parcelable {

    // Empty constructor for Firestore
    constructor() : this(
        bookingId = "",
        studentId = "",
        advisorId = "",
        studentName = "",
        advisorName = "",
        purpose = "",
        preferredLanguage = "",
        additionalNotes = "",
        urgencyLevel = "",
        sessionType = "instant_call",
        bookingTimestamp = null,
        advisorResponseDeadline = 0,
        advisorRespondedAt = 0,
        advisorCalledAt = 0,
        sessionAmount = 100.0,
        paymentStatus = "pending",
        paymentTimestamp = 0,
        bookingStatus = "pending"
    )

    fun isResponseExpired(): Boolean {
        return System.currentTimeMillis() > advisorResponseDeadline
    }

    fun isActive(): Boolean {
        return bookingStatus == "pending" && !isResponseExpired()
    }

    fun getRemainingTimeFormatted(): String {
        val remaining = advisorResponseDeadline - System.currentTimeMillis()
        if (remaining <= 0) return "Expired"

        val minutes = (remaining / (60 * 1000)).toInt()
        val seconds = ((remaining % (60 * 1000)) / 1000).toInt()
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun getFormattedBookingTime(): String {
        val timestamp = bookingTimestamp ?: Timestamp.now()
        val date = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            .format(timestamp.toDate())
        return date
    }

    fun getBookingDate(): String {
        val timestamp = bookingTimestamp ?: Timestamp.now()
        val date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            .format(timestamp.toDate())
        return date
    }

    // Helper to get timestamp as Long
    fun getBookingTimestampAsLong(): Long {
        return bookingTimestamp?.toDate()?.time ?: System.currentTimeMillis()
    }
}