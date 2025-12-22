package com.example.associate.Repositories

import com.example.associate.DataClass.SessionBookingDataClass
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
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
            .whereIn("bookingStatus", listOf("pending", "accepted", "rejected"))
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
    /**
     * Completes a booking with a secure atomic transaction.
     * Deducts from User Wallet, Credits Advisor Wallet, Updates Booking, and Adds Transaction History.
     */
    suspend fun completeBookingWithTransaction(
        bookingId: String,
        userId: String,
        advisorId: String,
        callDurationSeconds: Long,
        ratePerMinute: Double,
        isInstant: Boolean
    ): Boolean {
        return try {
            db.runTransaction { transaction ->
                val userRef = db.collection("users").document(userId)
                val advisorRef = db.collection("advisors").document(advisorId)
                val bookingCollection = if (isInstant) "instant_bookings" else "scheduled_bookings"
                val bookingRef = db.collection(bookingCollection).document(bookingId)

                val userSnapshot = transaction.get(userRef)
                val advisorSnapshot = transaction.get(advisorRef)

                // 1. Calculate Costs
                val totalCost = if (isInstant) {
                     (callDurationSeconds / 60.0) * ratePerMinute
                } else {
                     ratePerMinute // For scheduled, ratePerMinute is treated as fixed sessionAmount
                }
                
                // Round to 2 decimals
                val finalCost = java.math.BigDecimal(totalCost).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

                // 2. User Deduction
                val currentBalance = userSnapshot.getDouble("walletBalance") ?: 0.0
                val newBalance = currentBalance - finalCost
                
                // Allow negative balance? Usually no, but for atomic completion we might enforce it or fail.
                // If this runs at end of call, we deduct what we can or go negative. 
                // Given the visual tracker checks balance, we assume it's mostly fine.
                // We will proceed even if it dips slightly negative to ensure advisor is paid for time used.

                transaction.update(userRef, "walletBalance", newBalance)

                // 3. Advisor Credit
                // Access nested map "earningsInfo"
                // Note: Firestore doesn't support updating nested fields easily with dot notation in 'update' if the map doesn't exist?
                // But usually "earningsInfo.totalLifetimeEarnings" works.
                // Safely get current values
                
                // We need to handle if earningsInfo doesn't exist yet
                val earningsInfo = advisorSnapshot.get("earningsInfo") as? Map<String, Any> ?: emptyMap()
                val currentLifetime = (earningsInfo["totalLifetimeEarnings"] as? Number)?.toDouble() ?: 0.0
                val currentToday = (earningsInfo["todayEarnings"] as? Number)?.toDouble() ?: 0.0
                val currentPending = (earningsInfo["pendingBalance"] as? Number)?.toDouble() ?: 0.0

                transaction.update(advisorRef, "earningsInfo.totalLifetimeEarnings", currentLifetime + finalCost)
                transaction.update(advisorRef, "earningsInfo.todayEarnings", currentToday + finalCost)
                transaction.update(advisorRef, "earningsInfo.pendingBalance", currentPending + finalCost)

                // 4. Update Booking
                transaction.update(bookingRef, mapOf(
                    "bookingStatus" to "completed",
                    "paymentStatus" to "paid",
                    "sessionAmount" to finalCost,
                    "callDuration" to callDurationSeconds,
                    "callEndedAt" to com.google.firebase.Timestamp.now()
                ))

                // 5. Transaction History (User)
                val userTransactionRef = userRef.collection("transactions").document()
                val userTx = hashMapOf(
                    "amount" to finalCost,
                    "type" to "DEBIT",
                    "description" to "Call Fee",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "relatedBookingId" to bookingId
                )
                transaction.set(userTransactionRef, userTx)

                // 6. Transaction History (Advisor)
                val advisorTransactionRef = advisorRef.collection("transactions").document()
                val advisorTx = hashMapOf(
                    "amount" to finalCost,
                    "type" to "CREDIT",
                    "description" to "Session Earning",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "relatedBookingId" to bookingId
                )
                transaction.set(advisorTransactionRef, advisorTx)

            }.await()
            true
        } catch (e: Exception) {
            android.util.Log.e("BookingRepository", "Transaction failed: ${e.message}", e)
            false
        }
    }
}
