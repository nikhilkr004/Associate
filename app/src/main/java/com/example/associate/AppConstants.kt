package com.example.associate
object AppConstants {
    // Agora Configuration
    const val AGORA_APP_ID = "0fdb614f8d0f4ed8a8281cef6a3c0fdb" // Replace with your Agora App ID
    const val DEFAULT_CHANNEL_NAME = "ASSOCIATE"
    const val AGORA_TOKEN = "" // Leave empty for testing, use token for production

    // Payment Configuration
    const val PAYMENT_INTERVAL_MS = 10000L // 10 seconds
    const val PAYMENT_AMOUNT = 1.0 // 10 rupees per 10 seconds
    const val PAYMENT_RATE_PER_MINUTE = 10.0 // 60 rupees per minute
}