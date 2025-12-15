package com.example.associate.Utils

object AppConstants {
    // ZegoCloud Configuration
    const val ZEGO_APP_ID: Long = 1090399585L // Replace with your App ID
    const val ZEGO_APP_SIGN = "332c1e72681654c47dd6836e7e834f3346d25c25e93afcdb757b84ddbcacc388" // Replace with your App Sign
    const val DEFAULT_CHANNEL_NAME = "test_channel" // Default channel name

    // Payment Configuration
    const val PAYMENT_INTERVAL_MS = 10000L // 10 seconds
    const val PAYMENT_AMOUNT = 1.0 // 10 rupees per 10 seconds
    const val PAYMENT_RATE_PER_MINUTE = 10.0 // 60 rupees per minute
}