package com.example.associate.Repositories

import android.content.Context
import android.util.Log
import com.example.associate.DataClass.RemoteAppConfig
import com.example.associate.Utils.AppConstants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fetches remote app configuration from Firestore.
 *
 * Firestore path:
 *   Collection : app_config
 *   Document   : settings
 *
 * Fields expected in the document:
 *   appIconUrl   (String)  – Firebase Storage URL of the app icon
 *   zegoAppId    (Number)  – ZegoCloud App ID
 *   zegoAppSign  (String)  – ZegoCloud App Sign
 *   geminiApiKey (String)  – Google Gemini API Key
 *   lastUpdated  (Timestamp) – When the config was last updated by admin
 */
object AppConfigRepository {

    private const val TAG = "AppConfigRepository"
    private const val COLLECTION = "app_config"
    private const val DOCUMENT = "settings"

    private val db = FirebaseFirestore.getInstance()

    /**
     * Fetches the remote config from Firestore and applies it to [AppConstants].
     * Falls back to hardcoded defaults if the fetch fails or fields are empty.
     *
     * Call this once during app startup (e.g., from SplashScreenActivity).
     * Now downloads and saves the icon to local storage `custom_app_logo.png`.
     *
     * @return The fetched [RemoteAppConfig], or null if fetch failed.
     */
    suspend fun fetchAndApply(context: Context): RemoteAppConfig? {
        return try {
            val snapshot = db.collection(COLLECTION)
                .document(DOCUMENT)
                .get()
                .await()

            if (!snapshot.exists()) {
                Log.w(TAG, "app_config/settings document does not exist yet. Using defaults.")
                return null
            }

            val config = RemoteAppConfig(
                appIconUrl   = snapshot.getString("appIconUrl")   ?: "",
                zegoAppId    = snapshot.getLong("zegoAppId")      ?: AppConstants.ZEGO_APP_ID_DEFAULT,
                zegoAppSign  = snapshot.getString("zegoAppSign")  ?: "",
                geminiApiKey = snapshot.getString("geminiApiKey") ?: "",
                lastUpdated  = snapshot.getTimestamp("lastUpdated")
            )

            // Apply to AppConstants live singleton
            if (config.zegoAppId != 0L) {
                AppConstants.ZEGO_APP_ID = config.zegoAppId
                Log.d(TAG, "Applied remote Zego App ID: ${config.zegoAppId}")
            }
            if (config.zegoAppSign.isNotEmpty()) {
                AppConstants.ZEGO_APP_SIGN = config.zegoAppSign
                Log.d(TAG, "Applied remote Zego App Sign")
            }
            if (config.geminiApiKey.isNotEmpty()) {
                AppConstants.GEMINI_API_KEY = config.geminiApiKey
                Log.d(TAG, "Applied remote Gemini API Key")
            }
            
            if (config.appIconUrl.isNotEmpty() && config.appIconUrl != AppConstants.APP_ICON_URL) {
                AppConstants.APP_ICON_URL = config.appIconUrl
                Log.d(TAG, "Applied remote App Icon URL: ${config.appIconUrl}")
                
                // Explicitly download and save the icon locally
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val bitmap = com.bumptech.glide.Glide.with(context)
                            .asBitmap()
                            .load(config.appIconUrl)
                            .submit()
                            .get()
                            
                        val file = java.io.File(context.filesDir, "custom_app_logo.png")
                        java.io.FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        Log.i(TAG, "Successfully downloaded and saved custom app logo to local storage.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download and save remote app icon to internal storage", e)
                    }
                }
            } else if (config.appIconUrl.isNotEmpty()) {
                AppConstants.APP_ICON_URL = config.appIconUrl
            }

            Log.i(TAG, "Remote config fetched and applied successfully.")
            config

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config: ${e.message}. Using hardcoded defaults.", e)
            null
        }
    }
}
