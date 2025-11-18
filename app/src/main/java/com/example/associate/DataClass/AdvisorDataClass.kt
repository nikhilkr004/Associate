package com.example.associate.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


import com.google.firebase.Timestamp
import java.util.Date

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
//    val isactive: String = "",

    // 🔥 NEW FIELDS FOR COMPLETE FUNCTIONALITY:

    // Professional Details
    val designation: String = "", // "Senior Advisor", "Career Counselor" etc.
    val department: String = "", // "Computer Science", "Finance" etc.
    val employeeId: String = "", // Official ID
    val bio: String = "", // Professional introduction
    val officeLocation: String = "", // Physical office address
    val phoneNumber: String = "", // Contact number

    // Availability & Scheduling
    val workingDays: List<String> = emptyList(), // ["Monday", "Tuesday", ...]
    val workingHoursStart: String = "", // "09:00"
    val workingHoursEnd: String = "", // "17:00"
    val appointmentDuration: Int = 30, // Default 30 minutes
    val maxDailyAppointments: Int = 10, // Limit appointments per day

    // Contact Preferences
    val preferredContactMethod: String = "", // "email", "phone", "in_person"
    val responseTime: String = "", // "24 hours", "48 hours"

    // Educational Background
    val highestQualification: String = "", // "Masters", "PhD" etc.
    val qualificationField: String = "", // Field of study
    val university: String = "", // University name

    // System & Permissions
    val userRole: String = "advisor", // Default role
    val accessLevel: String = "basic", // "basic", "senior", "admin"
    val canGenerateReports: Boolean = false,
    val canManageResources: Boolean = false,

    // Additional Professional Info
    val yearsInOrganization: Int = 0,
    val totalStudentsAdvised: Int = 0,
    val rating: Double = 0.0, // Average rating from students
    val reviewCount: Int = 0, // Number of reviews

    // Social & Additional Links
    val linkedinProfile: String = "",
    val website: String = "",

    // Document References
    val documentUrls: Map<String, String> = emptyMap(), // {"id_card": "url", "certificate": "url"}

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = 0
) : Parcelable {
    // Helper method to check if advisor is active
//    fun isActive(): Boolean = status == "active" && isactive == "true"

//    // Check if available for scheduling
//    fun isAvailableForAppointments(): Boolean {
//        return isActive() && maxDailyAppointments > 0
//    }

    // Get full professional title
    fun getProfessionalTitle(): String {
        return "$designation - $department"
    }
}