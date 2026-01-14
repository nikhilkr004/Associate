package com.example.associate.HelpSupport

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class TicketDataClass(
    var ticketId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val category: String = "", // e.g., "Payment", "App Issue"
    val subject: String = "",
    val description: String = "",
    val status: String = "OPEN", // OPEN, IN_PROGRESS, CLOSED, RESOLVED
    val imageUrls: List<String> = emptyList(),
    val timestamp: Timestamp = Timestamp.now(),
    val lastUpdated: Timestamp = Timestamp.now(),
    val adminResponse: String = ""
) : Parcelable

// Updated for repository activity
