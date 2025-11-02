package com.example.associate.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AdvisorDataClass(
    var id: String = "",
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
    val profileimage: String = "",
    val isactive: String = "",

    // Professional Details
    val designation: String = "",
    val department: String = "",
    val employeeId: String = "",
    val bio: String = "",
    val officeLocation: String = "",
    val phoneNumber: String = "",

    // Availability & Scheduling
    val workingDays: List<String> = emptyList(),
    val workingHoursStart: String = "",
    val workingHoursEnd: String = "",
    val appointmentDuration: Int = 30,
    val maxDailyAppointments: Int = 10,

    // Contact Preferences
    val preferredContactMethod: String = "",
    val responseTime: String = "",

    // Educational Background
    val highestQualification: String = "",
    val qualificationField: String = "",
    val university: String = "",

    // System & Permissions
    val userRole: String = "advisor",
    val accessLevel: String = "basic",
    val canGenerateReports: Boolean = false,
    val canManageResources: Boolean = false,

    // Additional Professional Info
    val yearsInOrganization: Int = 0,
    val totalStudentsAdvised: Int = 0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,

    // Social & Additional Links
    val linkedinProfile: String = "",
    val website: String = "",

    // Document References
    val documentUrls: Map<String, String> = emptyMap(),

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = 0
) : Parcelable {

    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        email = "",
        city = "",
        gender = "",
        experience = 0,
        languages = emptyList(),
        specializations = emptyList(),
        certifications = emptyList(),
        status = "",
        timestamp = 0,
        profileimage = "",
        isactive = "",
        designation = "",
        department = "",
        employeeId = "",
        bio = "",
        officeLocation = "",
        phoneNumber = "",
        workingDays = emptyList(),
        workingHoursStart = "",
        workingHoursEnd = "",
        appointmentDuration = 30,
        maxDailyAppointments = 10,
        preferredContactMethod = "",
        responseTime = "",
        highestQualification = "",
        qualificationField = "",
        university = "",
        userRole = "advisor",
        accessLevel = "basic",
        canGenerateReports = false,
        canManageResources = false,
        yearsInOrganization = 0,
        totalStudentsAdvised = 0,
        rating = 0.0,
        reviewCount = 0,
        linkedinProfile = "",
        website = "",
        documentUrls = emptyMap(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        lastLogin = 0
    )

    // Helper method to check if advisor is active
    fun isActive(): Boolean = status == "active" && isactive == "true"

    // Check if available for scheduling
    fun isAvailableForAppointments(): Boolean {
        return isActive() && maxDailyAppointments > 0
    }

    // Get full professional title
    fun getProfessionalTitle(): String {
        return "$designation - $department"
    }

    // Get experience as string
    fun getExperienceString(): String {
        return "$experience years experience"
    }

    // Get languages as comma separated string
    fun getLanguagesString(): String {
        return languages.joinToString(", ")
    }

    // Get specializations as comma separated string
    fun getSpecializationsString(): String {
        return specializations.joinToString(", ")
    }
}