package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class VideoCall(
    val id: String = "",
    val bookingRequestId: String = "",
    val userId: String = "",
    val callerId: String = "", // Added to match Firestore
    val advisorId: String = "",
    val receiverId: String = "",
    val channelName: String = "ASSOCIATE",
    val callStartTime: Timestamp? = null,
    val callEndTime: Timestamp? = null,
    val status: String = "", // "initiated", "ongoing", "ended"
    val lastPaymentTime: Timestamp? = null,
    val totalAmount: Double = 0.0
)
// Updated for repository activity
