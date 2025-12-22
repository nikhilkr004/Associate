package com.example.associate.DataClass

import com.google.firebase.Timestamp
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// 🔹 1. Basic Info
@Parcelize
data class BasicInfo(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val gender: String = "",
    val city: String = "",
    val profileImage: String = "",
    val status: String = "",
    val isactive: String = "false"
) : Parcelable

// 🔹 2. Professional Info
@Parcelize
data class ProfessionalInfo(
    val designation: String = "",
    val department: String = "",
    val experience: Int = 0,
    val yearsInOrganization: Int = 0,
    val employeeId: String = "",
    val bio: String = "",
    val officeLocation: String = "",
    val specializations: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    // 🔥 NEW: Map of Specialization Name -> Certificate URL
    val specializationUrls: Map<String, String> = emptyMap()
) : Parcelable

// 🔹 3. Education
@Parcelize
data class EducationInfo(
    val highestQualification: String = "",
    val qualificationField: String = "",
    val university: String = "",
    // 🔥 NEW: URL for the highest qualification certificate
    val highestQualificationUrl: String = ""
) : Parcelable

// 🔹 4. Availability & Scheduling
@Parcelize
data class AvailabilitySchedule(
    val startTime: String = "",
    val endTime: String = "",
    val activeDays: List<String> = emptyList(),
    val duration: Int = 15,
    val generatedSlots: List<String> = emptyList()
) : Parcelable

@Parcelize
data class AvailabilityInfo(
    val workingDays: List<String> = emptyList(),
    val workingHoursStart: String = "",
    val workingHoursEnd: String = "",
    val appointmentDuration: Int = 30,
    val maxDailyAppointments: Int = 10,
    val scheduledAvailability: ScheduledAvailabilityConfig = ScheduledAvailabilityConfig(),
    val instantAvailability: InstantAvailabilityConfig = InstantAvailabilityConfig(),
    val virtualSchedule: AvailabilitySchedule = AvailabilitySchedule(),
    val inPersonSchedule: AvailabilitySchedule = AvailabilitySchedule()
) : Parcelable

// 🔹 5. Pricing Information
@Parcelize
data class PricingInfo(
    // Instant (Per Minute)
    val instantChatFee: Int = 0,
    val instantAudioFee: Int = 0,
    val instantVideoFee: Int = 0,

    // Scheduled (Per Session - e.g. 30 mins)
    val scheduledChatFee: Int = 0,
    val scheduledAudioFee: Int = 0,
    val scheduledVideoFee: Int = 0,
    val scheduledInPersonFee: Int = 0
) : Parcelable

// 🔹 6. Communication Preferences
@Parcelize
data class ContactPreferences(
    val preferredContactMethod: String = "",
    val responseTime: String = ""
) : Parcelable

// 🔹 7. System & Permissions
@Parcelize
data class SystemInfo(
    val userRole: String = "advisor",
    val accessLevel: String = "basic",
    val canGenerateReports: Boolean = false,
    val canManageResources: Boolean = false
) : Parcelable

// 🔹 8. Performance & Reviews
@Parcelize
data class PerformanceInfo(
    val totalStudentsAdvised: Int = 0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0
) : Parcelable

// 🔹 9. Links & Documents
@Parcelize
data class Resources(
    val linkedinProfile: String = "",
    val website: String = "",
    val documentUrls: Map<String, String> = emptyMap()
) : Parcelable

// 🔹 10. Timestamps
@Parcelize
data class TimeInfo(
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    val lastLogin: Timestamp = Timestamp.now()
) : Parcelable

// 🚀 Final Advisor Data Class (MINIMUM NODES)
@Parcelize
data class AdvisorDataClass(
    val basicInfo: BasicInfo = BasicInfo(),
    val professionalInfo: ProfessionalInfo = ProfessionalInfo(),
    val educationInfo: EducationInfo = EducationInfo(),
    val availabilityInfo: AvailabilityInfo = AvailabilityInfo(),
    val pricingInfo: PricingInfo = PricingInfo(),
    val contactPreferences: ContactPreferences = ContactPreferences(),
    val systemInfo: SystemInfo = SystemInfo(),
    val performanceInfo: PerformanceInfo = PerformanceInfo(),
    val resources: Resources = Resources(),
    val timeInfo: TimeInfo = TimeInfo()
) : Parcelable {
    // Get full professional title
    fun getProfessionalTitle(): String {
        return "${professionalInfo.designation} - ${professionalInfo.department}"
    }
}
