package com.example.associate.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class SessionBookingDataClass(
    var bookingId: String = "",
    val studentId: String = "",
    val advisorId: String = "",
    val studentName: String = "",
    val advisorName: String = "",
    val purpose: String = "",
    val preferredLanguage: String = "",
    val additionalNotes: String = "",
    var urgencyLevel: String = "",
    var bookingSlot: String = "",
    var bookingDate: String = "",
    val bookingType: String = "AUDIO", // CHAT, AUDIO, VIDEO
    
    // 🔥 UPDATED: Use Timestamp instead of Long
    @com.google.firebase.firestore.PropertyName("timestamp")
    var bookingTimestamp: Timestamp? = null,
    val advisorResponseDeadline: Timestamp? = null,
    val advisorCalledAt: Timestamp? = null,
    val sessionAmount: Double = 100.0,
    val bookingStatus: String = "",
    val paymentStatus: String = "",
    val studentPhone: String = "",
    val callDuration: Int = 0,
    val callStartedAt: Timestamp? = null,
    val callEndedAt: Timestamp? = null
) : Parcelable {
    // Constructor for Firestore
    constructor() : this(
        bookingId = "",
        studentId = "", 
        // ... (other fields default to empty/null)
        bookingTimestamp = null
    )
    
    // Helper functionality
    fun getBookingTimestampAsLong(): Long {
        return bookingTimestamp?.toDate()?.time ?: System.currentTimeMillis()
    }

    fun getAdvisorResponseDeadlineAsLong(): Long {
        return advisorResponseDeadline?.toDate()?.time ?: 0L
    }
    
    fun isResponseExpired(): Boolean {
        return System.currentTimeMillis() > getAdvisorResponseDeadlineAsLong()
    }

    fun isActive(): Boolean {
        return bookingStatus == "pending" && !isResponseExpired()
    }
}