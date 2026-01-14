package com.example.associate.HelpSupport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.associate.Repositories.StorageRepository
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.coroutines.tasks.await
import java.util.UUID

class HelpSupportRepository {

    private val db = FirebaseFirestore.getInstance()
    private val ticketsCollection = db.collection("tickets")
    private val storageRepository = StorageRepository()

    suspend fun submitTicket(ticket: TicketDataClass, imageUris: List<Uri>, context: Context): Result<Boolean> {
        return try {
            val uploadedImageUrls = mutableListOf<String>()

            // 1. Upload Images if any
            for (uri in imageUris) {
                // We use a suspendCoroutine or similar to wait for callback if StorageRepository uses callbacks
                // However, StorageRepository uses callback interface. 
                // We should ideally refactor StorageRepository to use Coroutines or wrap it here.
                // For simplicity, let's wrap it in suspendCancellableCoroutine or just use the logic directly here if possible?
                // Or better, let's look at StorageRepository again.
                // Actually, I'll write a simple wrapper function here for sequential upload.
                val url = uploadImageSuspend(context, uri)
                if (url != null) {
                    uploadedImageUrls.add(url)
                }
            }

            // 2. Update ticket with URLs
            val finalTicket = ticket.copy(
                imageUrls = uploadedImageUrls,
                ticketId = ticketsCollection.document().id // Generate ID if empty
            )

            // 3. Save to Firestore
            ticketsCollection.document(finalTicket.ticketId).set(finalTicket).await()
            Result.success(true)
        } catch (e: Exception) {
            Log.e("HelpSupportRepo", "Error submitting ticket", e)
            Result.failure(e)
        }
    }

    private suspend fun uploadImageSuspend(context: Context, uri: Uri): String? {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            storageRepository.uploadFile(context, uri, "ticket_images", true, object : StorageRepository.UploadCallback {
                override fun onSuccess(downloadUrl: String, fileName: String, fileType: String) {
                    continuation.resumeWith(Result.success(downloadUrl))
                }

                override fun onFailure(error: String) {
                    continuation.resumeWith(Result.success(null)) // Return null on failure to proceed
                    Log.e("HelpSupportRepo", "Image upload failed: $error")
                }

                override fun onProgress(progress: Int) {
                    // Optional: Update progress
                }
            })
        }
    }
    
    suspend fun getMyTickets(userId: String): Result<List<TicketDataClass>> {
         return try {
            val snapshot = ticketsCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val tickets = snapshot.toObjects(TicketDataClass::class.java)
            Result.success(tickets)
        } catch (e: Exception) {
             Log.e("HelpSupportRepo", "Error fetching tickets", e)
            Result.failure(e)
        }
    }
}

// Updated for repository activity
