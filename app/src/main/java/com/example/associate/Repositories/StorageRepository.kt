package com.example.associate.Repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.util.UUID

class StorageRepository {

    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val storageRef: StorageReference = storage.reference

    interface UploadCallback {
        fun onSuccess(downloadUrl: String, fileName: String, fileType: String)
        fun onFailure(error: String)
        fun onProgress(progress: Int)
    }

    fun uploadFile(
        context: Context,
        fileUri: Uri,
        folderName: String = "uploads",
        isImage: Boolean,
        callback: UploadCallback
    ) {
        val fileName = "${UUID.randomUUID()}_${System.currentTimeMillis()}"
        val finalFileName = if (isImage) "$fileName.jpg" else "$fileName.pdf"
        val fileRef = storageRef.child("$folderName/$finalFileName")

        val uploadTask = if (isImage) {
            val compressedData = compressImage(context, fileUri)
            if (compressedData != null) {
                fileRef.putBytes(compressedData)
            } else {
                callback.onFailure("Failed to compress image")
                return
            }
        } else {
            fileRef.putFile(fileUri)
        }

        uploadTask.addOnSuccessListener {
            fileRef.downloadUrl.addOnSuccessListener { uri ->
                callback.onSuccess(uri.toString(), finalFileName, if (isImage) "image" else "document")
            }.addOnFailureListener { e ->
                callback.onFailure("Failed to get download URL: ${e.message}")
            }
        }.addOnFailureListener { e ->
            callback.onFailure("Upload failed: ${e.message}")
        }.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
            callback.onProgress(progress)
        }
    }

    private fun compressImage(context: Context, imageUri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val outputStream = ByteArrayOutputStream()
            // Compress to JPEG with 60% quality
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e("StorageRepository", "Compression error: ${e.message}")
            null
        }
    }
    
    fun deleteFile(fileUrl: String, callback: (Boolean) -> Unit) {
        try {
            val fileRef = storage.getReferenceFromUrl(fileUrl)
            fileRef.delete().addOnSuccessListener {
                callback(true)
            }.addOnFailureListener {
                callback(false)
            }
        } catch (e: Exception) {
             Log.e("StorageRepository", "Delete error: ${e.message}")
             callback(false)
        }
    }
}
