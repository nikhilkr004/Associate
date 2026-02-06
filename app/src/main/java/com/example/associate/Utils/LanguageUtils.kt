package com.example.associate.Utils

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import java.util.Locale

object LanguageUtils {

    private val languageIdentifier: LanguageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    /**
     * Detects the language of the input text.
     * @param text The text to analyze.
     * @param onResult Callback with the detected language code (e.g., "en", "hi") or "und" if undetermined.
     */
    fun detectLanguage(text: String, onResult: (String) -> Unit) {
        if (text.isBlank()) {
            onResult("en")
            return
        }

        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") {
                    Log.i("LanguageUtils", "Language: Undetermined")
                    onResult("en") // Default to English
                } else {
                    Log.i("LanguageUtils", "Language: $languageCode")
                    onResult(languageCode)
                }
            }
            .addOnFailureListener {
                Log.e("LanguageUtils", "Language ID Error: ${it.message}")
                onResult("en") // Default to English on error
            }
    }
}
