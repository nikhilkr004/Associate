// UserData.kt - Simple solution
package com.example.associate.DataClass

import java.util.Date

@kotlinx.parcelize.Parcelize
data class UserData(
    // Registration & Profile
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "",
    var gender: String = "",
    val profilePhotoUrl: String? = null,
    val dateOfBirth: Date? = null,
    val location: String? = null,
    val preferredLanguage: String = "en",
    val jointAt: String = "",
    val fcmToken: String = ""
) : android.os.Parcelable
// Kotlin automatically creates no-arg constructor when all parameters have default values
// Updated for repository activity
