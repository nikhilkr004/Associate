package com.example.associate.Repositories

import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.UserData
import com.example.associate.DataClass.VideoCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository responsible for fetching Call History data.
 * Handles interaction with Firestore collections 'videoCalls' and 'advisors'.
 */
class CallHistoryRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val TAG = "CallHistoryRepo"

    /**
     * Fetches the video calls where the current user is the receiver.
     * @return List of [VideoCall] objects sorted by start time descending.
     */
    suspend fun getUserVideoCalls(): List<VideoCall> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return try {
            val documents = db.collection("videoCalls")
                .whereEqualTo("receiverId", userId)
                .get()
                .await()
                .documents

            documents.mapNotNull { document ->
                document.toObject(VideoCall::class.java)?.copy(id = document.id)
            }.sortedByDescending { it.callStartTime }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching calls", e)
            emptyList()
        }
    }

    /**
     * Fetches details of a specific Advisor by ID.
     * Mapped to [UserData] for compatibility with existing Adapters.
     * @param userId The ID of the advisor to fetch.
     * @return [UserData] object or null if not found/error.
     */
    suspend fun getUserDetails(userId: String): UserData? {
        return try {
            val document = db.collection("advisors")
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                val advisor = document.toObject(AdvisorDataClass::class.java)
                advisor?.let {
                    UserData(
                        userId = it.basicInfo.id,
                        name = it.basicInfo.name,
                        email = it.basicInfo.email,
                        phone = it.basicInfo.phoneNumber,
                        profilePhotoUrl = it.basicInfo.profileImage
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching advisor details: ${e.message}")
            null
        }
    }

    /**
     * Fetches ALL bookings (Instant + Scheduled) for the current user.
     * Merges them and attaches Advisor details (like photo).
     */
    suspend fun getBookingsWithAdvisorDetails(): List<Pair<com.example.associate.DataClass.SessionBookingDataClass, UserData?>> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        val resultList = mutableListOf<Pair<com.example.associate.DataClass.SessionBookingDataClass, UserData?>>()

        try {
            // 1. Fetch Instant Bookings
            val instantSnapshot = db.collection("instant_bookings")
                .whereEqualTo("studentId", userId)
                .get()
                .await()
            android.util.Log.d(TAG, "Instant Bookings found: ${instantSnapshot.size()}")

            // 2. Fetch Scheduled Bookings
            val scheduledSnapshot = db.collection("scheduled_bookings")
                .whereEqualTo("studentId", userId)
                .get()
                .await()
            android.util.Log.d(TAG, "Scheduled Bookings found: ${scheduledSnapshot.size()}")
            
            val allDocs = instantSnapshot.documents + scheduledSnapshot.documents

            for (doc in allDocs) {
                try {
                    val booking = doc.toObject(com.example.associate.DataClass.SessionBookingDataClass::class.java)
                    if (booking != null) {
                        // Mark urgency if missing
                        if (booking.urgencyLevel.isEmpty()) {
                             val isScheduled = scheduledSnapshot.documents.any { it.id == doc.id }
                             booking.urgencyLevel = if(isScheduled) "Scheduled" else "Instant"
                        }
                        
                        // Fallback: Check for alternative timestamp fields if bookingTimestamp is null
                        if (booking.bookingTimestamp == null) {
                            val data = doc.data
                            val possibleTimestamp = data?.get("slotStartTime") ?: data?.get("createdAt") ?: data?.get("startTime")
                            
                            if (possibleTimestamp is com.google.firebase.Timestamp) {
                                booking.bookingTimestamp = possibleTimestamp
                            } else if (possibleTimestamp is String) {
                                // Try parsing string if needed, or ignore
                                // android.util.Log.w(TAG, "Timestamp is string: $possibleTimestamp")
                            }
                        }

                        // Fetch advisor details
                        val advisor = if (booking.advisorId.isNotEmpty()) getUserDetails(booking.advisorId) else null
                        resultList.add(Pair(booking, advisor))
                    } else {
                        android.util.Log.e(TAG, "Booking is null for doc: ${doc.id}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing booking ${doc.id}: ${e.message}")
                }
            }

            // Sort by timestamp descending
            resultList.sortByDescending { it.first.bookingTimestamp }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching bookings history", e)
        }

        return resultList
    }

    /**
     * Cancels the booking in Firestore.
     * Identifies the collection based on urgencyLevel.
     */
    suspend fun cancelBooking(bookingId: String, urgencyLevel: String): Boolean {
        return try {
            val collectionName = if (urgencyLevel.equals("Scheduled", ignoreCase = true)) {
                "scheduled_bookings"
            } else {
                "instant_bookings"
            }

            db.collection(collectionName).document(bookingId)
                .update("bookingStatus", "cancelled")
                .await()
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error cancelling booking: ${e.message}")
            false
        }
    }
}