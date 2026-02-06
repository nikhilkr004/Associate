package com.example.associate.Utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.IOException

object PdfUtils {

    fun readPdfAsBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
