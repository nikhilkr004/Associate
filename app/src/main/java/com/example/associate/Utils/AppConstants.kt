package com.example.associate.Utils

/**
 * App-wide constants. Live values (API keys, icons) are populated at startup
 * by [com.example.associate.Repositories.AppConfigRepository] from Firestore.
 *
 * Hardcoded `_DEFAULT` values are used as fallbacks when Firestore is unreachable.
 *
 * Firestore path: app_config → settings
 * Fields: appIconUrl, zegoAppId, zegoAppSign, geminiApiKey
 */
object AppConstants {

    // ─── Hardcoded Defaults (fallback only) ───────────────────────────────────
    const val ZEGO_APP_ID_DEFAULT: Long = 243845632L
    const val ZEGO_APP_SIGN_DEFAULT = "3f0daea606259e68c2c902051931ba67d34d4e9c92973466c02e7b1dd625e4bb"
    const val GEMINI_API_KEY_DEFAULT = "AIzaSyB9sdItbepvafid2T1HnFknwH-WH0qB9bA"

    // ─── Live Values (overridden by remote config at startup) ─────────────────
    @Volatile var ZEGO_APP_ID: Long = ZEGO_APP_ID_DEFAULT
    @Volatile var ZEGO_APP_SIGN: String = ZEGO_APP_SIGN_DEFAULT
    @Volatile var GEMINI_API_KEY: String = GEMINI_API_KEY_DEFAULT
    @Volatile var APP_ICON_URL: String = ""  // Set by admin via Firebase Storage

    // ─── Static Constants ─────────────────────────────────────────────────────
    const val DEFAULT_CHANNEL_NAME = "test_channel"
    const val PAYMENT_INTERVAL_MS = 10000L // 10 seconds
}

