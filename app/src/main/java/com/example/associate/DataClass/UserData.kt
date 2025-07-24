package com.example.associate.DataClass

import java.util.Date

data class UserData(
    // Registration & Profile
    val userId: String,
    val name: String,
    val email: String,
    val phone: String,
    val profilePhotoUrl: String? = null,
    val dateOfBirth: Date? = null,
    val location: String? = null,
    val preferredLanguage: String = "en",



    // Preferences
    val preferredAdvisorCategories: List<String> = emptyList(), // e.g., ["Tax", "Retirement"]
    val budgetPreference: Double? = null // Max fee per session
)