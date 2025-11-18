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
        sessionAmount = 0.0,
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


}