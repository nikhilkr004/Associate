package com.example.associate.DataClass

/**
 * Data class representing the `app_config/settings` document in Firestore.
 *
 * Firestore path: app_config → settings
 */
data class RemoteAppConfig(
    val appIconUrl: String = "",
    val zegoAppId: Long = 0L,
    val zegoAppSign: String = "",
    val geminiApiKey: String = "",
    val lastUpdated: com.google.firebase.Timestamp? = null
)

