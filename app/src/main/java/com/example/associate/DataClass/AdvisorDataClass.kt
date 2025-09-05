package com.example.associate.DataClass

import com.google.firebase.Timestamp
import java.util.Date

class AdvisorDataClass {

    // Advisor.kt
    data class Advisor(
        val id: String = "",
        val name: String = "",
        val email: String = "",
        val city: String = "",
        val gender: String = "",
        val experience: Int = 0,
        val languages: List<String> = emptyList(),
        val specializations: List<String> = emptyList(),
        val certifications: List<String> = emptyList(),
        val status: String = "",
        val timestamp: Long = 0,
        val profileimage: String=""
    ) {
        // Helper method to check if advisor is active
        fun isActive(): Boolean = status == "active"
    }
}