package com.example.associate.DataClass

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class PaymentDataClass(
    val paymentId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val status: String = "", // "pending", "success", "failed"
    val razorpayOrderId: String = "",
    val razorpayPaymentId: String = "",
    val razorpaySignature: String = "",
    val description: String = "Wallet Top-up",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),

    // Additional fields for security and tracking
    val deviceInfo: String = "",
    val ipAddress: String = "",
    val appVersion: String = "",
    val type: String = "", // "topup", "video_call", etc.

    // 🔥 Added to match Firestore Transaction Records
    val callId: String = "",
    val bookingId: String = "",
    val errorMessage: String = "",
    val userRole: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

@IgnoreExtraProperties
data class WalletDataClass(
    val userId: String = "",
    // Firestore stores "walletBalance" (or sometimes "balance"). 
    // We map "balance" property to "walletBalance" field.
    // However, if Firestore has BOTH, we need to be careful.
    // The warning says "No setter/field for walletBalance". 
    // This usually happens when the framework tries to write "walletBalance" into a property but finds "balance".
    // Or when reading.
    // Best practice: Use @PropertyName on the property itself.
    // Best practice: Use @PropertyName on the property itself.
    var balance: Double = 0.0,
    val lastUpdated: Timestamp = Timestamp.now(),
    @get:PropertyName("lastTransactionTime")
    @set:PropertyName("lastTransactionTime")
    var lastTransactionTime: Timestamp? = null,
    val totalAdded: Double = 0.0,
    val totalSpent: Double = 0.0,
    val transactionCount: Long = 0
)

@IgnoreExtraProperties
data class RazorpayOrderResponse(
    val id: String = "",
    val amount: Int = 0,
    val currency: String = "INR",
    val receipt: String = "",
    val status: String = ""
)

@IgnoreExtraProperties
data class Transaction(
    val id: String,
    val type: String,
    val amount: String,
    val date: String,
    val transactionId: String,
    val status: String,
    val paymentData: PaymentDataClass? = null,
    val timestamp: Timestamp = Timestamp.now()
)
// Updated for repository activity
