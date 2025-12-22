package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class NotificationDataClass(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "general", // general, booking, call, warning
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val iconUrl: String = "" // Optional URL (fallback to default user icon)
)
