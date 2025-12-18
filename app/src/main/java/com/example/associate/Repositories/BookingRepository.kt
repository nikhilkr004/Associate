package com.example.associate.Repositories

import com.example.associate.DataClass.SessionBookingDataClass
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for handling Booking operations.
 */
class BookingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val bookingsCollection = db.collection("instant_bookings")

    /**
     * Cancels an existing booking.
     * @param bookingId The ID of the booking to cancel.
     */
    suspend fun cancelBooking(bookingId: String) {
        val updates = hashMapOf<String, Any>(
            "bookingStatus" to "cancelled",
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
        bookingsCollection.document(bookingId).update(updates).await()
    }

    /**
     * Returns a Flow that emits the current active booking for the user.
     * Uses a Firestore SnapshotListener.
     */
    fun getActiveBookingFlow(): Flow<SessionBookingDataClass?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = bookingsCollection
            .whereEqualTo("studentId", userId)
            .whereEqualTo("bookingStatus", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("BookingRepository", "Listen failed.", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    android.util.Log.d("BookingRepository", "Found ${snapshots.size()} documents for studentId: $userId")
                    val document = snapshots.documents[0]
                    try {
                        val booking = document.toObject(SessionBookingDataClass::class.java)
                        android.util.Log.d("BookingRepository", "Parsed booking: ${booking?.bookingId}, Status: ${booking?.bookingStatus}")
                        if (booking != null) {
                            trySend(booking)
                        } else {
                            android.util.Log.e("BookingRepository", "Booking is null after parsing")
                            trySend(null)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BookingRepository", "Error deserializing booking: ${e.message}", e)
                        
                        // 🔥 SELF-HEALING: Manually parse if toObject fails (likely due to Timestamp/Long mismatch)
                        val data = document.data
                        if (data != null) {
                            try {
                                val bookingId = data["bookingId"] as? String ?: ""
                                val advisorName = data["advisorName"] as? String ?: "Advisor"
                                val bookingStatus = data["bookingStatus"] as? String ?: "pending"
                                val studentIdStr = data["studentId"] as? String ?: userId ?: ""
                                
                                // Fix Timestamp
                                val rawDeadline = data["advisorResponseDeadline"]
                                val fixedDeadline: com.google.firebase.Timestamp? = when (rawDeadline) {
                                    is com.google.firebase.Timestamp -> rawDeadline
                                    is Long -> com.google.firebase.Timestamp(java.util.Date(rawDeadline))
                                    else -> null
                                }

                                android.util.Log.w("BookingRepository", "Rescuing booking $bookingId. Manual parsing successful.")

                                // Construct rescued object (Critical fields only)
                                val rescuedBooking = SessionBookingDataClass(
                                    bookingId = bookingId,
                                    studentId = studentIdStr,
                                    advisorName = advisorName,
                                    bookingStatus = bookingStatus,
                                    advisorResponseDeadline = fixedDeadline
                                )

                                // Auto-fix Firestore if needed
                                if (rawDeadline is Long) {
                                    android.util.Log.i("BookingRepository", "Auto-correcting Long to Timestamp in Firestore for $bookingId")
                                    document.reference.update("advisorResponseDeadline", fixedDeadline)
                                }

                                trySend(rescuedBooking)
                            } catch (manualEx: Exception) {
                                android.util.Log.e("BookingRepository", "Manual parsing also failed: ${manualEx.message}")
                                trySend(null)
                            }
                        } else {
                            trySend(null)
                        }
                    }
                } else {
                    android.util.Log.d("BookingRepository", "No pending bookings found for studentId: $userId")
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }
}
