package com.example.associate.DataClass

import com.google.firebase.Timestamp
import java.util.Date

class AdvisorDataClass {

    // For certifications/credentials
    data class Certification(
        val name: String,          // e.g., "CFP", "SEBI Registered"
        val issuingAuthority: String,
        val validity: Date?        // Optional expiry date
    )

    // For availability slots
    data class AvailabilitySlot(
        val startTime: Date,
        val endTime: Date,
        val isBooked: Boolean = false
    )

    // Main Advisor Model
    data class AdvisorBasicInfo(
        // Core Info
        val advisorId: String,      // Firebase Auth UID or custom ID
        val name: String,
        val email: String,
        val phone: String,
        val accountStatus: String,
        val joinAt: Timestamp,
        val city: String,
        val profilePhotoUrl: String? = null,
        val gender: String,
        val profileCompletion: Int = 0

    )

    data class AdvisorAdvanceInfo(

        // Professional Details
        val specializations: List<String>, // e.g., ["Tax", "Retirement"]
        val certifications: List<Certification>,
        val experienceYears: Int,
        val languages: List<String>,      // e.g., ["en", "hi"]
        val bio: String? = null,          // Short introduction

        // Pricing
        val feePerMinute: Double,
        val feePerSession: Double,        // For fixed-duration sessions
        val currency: String = "INR",     // or "USD"

        // Availability
        val weeklyAvailability: List<AvailabilitySlot>, // Recurring slots
        val instantConnectEnabled: Boolean = false,     // Pay-per-minute

        // Stats (updated dynamically)
        val rating: Double = 0.0,         // Average rating (1-5)
        val totalSessions: Int = 0,
        val documents: List<String> = emptyList() // PDF URLs (SEBI proofs, etc.)
    )
}