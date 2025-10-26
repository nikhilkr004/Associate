package com.example.associate.DataClass


import com.google.firebase.Timestamp

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
    val appVersion: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

data class WalletDataClass(
    val userId: String = "",
    var balance: Double = 0.0,
    val lastUpdated: Timestamp = Timestamp.now(),
    val totalAdded: Double = 0.0,
    val totalSpent: Double = 0.0,
    val transactionCount: Long = 0
)

data class RazorpayOrderResponse(
    val id: String = "",
    val amount: Int = 0,
    val currency: String = "INR",
    val receipt: String = "",
    val status: String = ""
)