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
                var bookingRef = db.collection(bookingCollection).document(bookingId)

                // 🔥 FORCE CHECK: If it's claimed as "Instant" but exists in "scheduled_bookings", treat as Scheduled (Prepaid)
                if (isInstant) {
                     android.util.Log.d("BookingRepository", "Client claims Instant. Checking Scheduled existence for ID: $bookingId")
                     val scheduledRef = db.collection("scheduled_bookings").document(bookingId)
                     try {
                        val scheduledSnapshot = transaction.get(scheduledRef)
                        if (scheduledSnapshot.exists()) {
                             android.util.Log.w("BookingRepository", "Force-Correcting to Scheduled (Prepaid) - Found in scheduled_bookings")
                             bookingRef = scheduledRef
                        } else {
                             android.util.Log.d("BookingRepository", "Not found in scheduled_bookings. Proceeding as Instant.")
                        }
                     } catch (e: Exception) {
                         android.util.Log.e("BookingRepository", "Error checking scheduled_bookings: ${e.message}")
                     }
                }

                val userSnapshot = transaction.get(userRef)
                val advisorSnapshot = transaction.get(advisorRef)
                // Ensure booking doc is read early for txn consistency rules
                val bookingSnapshot = transaction.get(bookingRef)
                
                // 🛑 SAFETY: If booking doesn't exist, DO NOT DEDUCT.
                if (!bookingSnapshot.exists()) {
                     android.util.Log.e("BookingRepository", "Booking Document NOT FOUND at ${bookingRef.path}. Aborting transaction to prevent double/ghost deduction.")
                     throw com.google.firebase.firestore.FirebaseFirestoreException("Booking not found", com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED)
                }
                
                // 🛑 IDEMPOTENCY CHECK: If already completed, DO NOT DEDUCT AGAIN.
                if (bookingSnapshot.getString("bookingStatus") == "completed") {
                    android.util.Log.w("BookingRepository", "Booking $bookingId is ALREADY COMPLETED. Skipping transaction.")
                    return@runTransaction // Exit transaction successfully but do nothing
                }

                val currentPath = bookingSnapshot.reference.path
                val isReallyScheduled = currentPath.contains("scheduled_bookings")
                val shouldDeductFromUser = !isReallyScheduled

                android.util.Log.d("BookingRepository", "Transaction Decision -> Path: $currentPath, isReallyScheduled: $isReallyScheduled, shouldDeduct: $shouldDeductFromUser")

                // 1. Calculate Costs
                val totalCost = if (shouldDeductFromUser) {
                     (callDurationSeconds / 60.0) * ratePerMinute
                } else {
                     ratePerMinute // For scheduled, ratePerMinute is treated as fixed sessionAmount
                }
                
                // Round to 2 decimals
                val finalCost = java.math.BigDecimal(totalCost).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

                val walletRef = db.collection("wallets").document(userId)
                
                // Read wallet instead of user doc for balance
                val walletSnapshot = transaction.get(walletRef)
                
                // ...
                
                // 2. User Deduction
                // IF INSTANT: Deduct now. IF SCHEDULED: Already deducted (Prepaid), so skip deduction.
                if (shouldDeductFromUser) {
                     android.util.Log.d("BookingRepository", "DEDUCTING FROM USER: $finalCost")
                    val currentBalance = if (walletSnapshot.exists()) {
                        walletSnapshot.getDouble("balance") ?: 0.0
                    } else {
                        0.0
                    }

                    val newBalance = currentBalance - finalCost

                    if (walletSnapshot.exists()) {
                        transaction.update(walletRef, "balance", newBalance)
                        val totalSpent = walletSnapshot.getDouble("totalSpent") ?: 0.0
                        val count = walletSnapshot.getLong("transactionCount") ?: 0
                        transaction.update(walletRef, "totalSpent", totalSpent + finalCost)
                        transaction.update(walletRef, "transactionCount", count + 1)
                    } else {
                        val newWallet = hashMapOf(
                            "userId" to userId,
                            "balance" to newBalance,
                            "totalSpent" to finalCost,
                            "transactionCount" to 1,
                            "lastUpdated" to FieldValue.serverTimestamp()
                        )
                        transaction.set(walletRef, newWallet)
                    }

                    // 5a. Transaction History (User) - Only for Instant Deduction
                    val userTransactionRef = userRef.collection("transactions").document()
                    val userTx = hashMapOf(
                        "amount" to finalCost,
                        "type" to "DEBIT",
                        "description" to "Call Fee",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "relatedBookingId" to bookingId
                    )
                    transaction.set(userTransactionRef, userTx)
                }

                // 3. Advisor Credit
                // Access nested map "earningsInfo"
                val earningsInfo = advisorSnapshot.get("earningsInfo") as? Map<String, Any> ?: emptyMap()
                val currentLifetime = (earningsInfo["totalLifetimeEarnings"] as? Number)?.toDouble() ?: 0.0
                val currentToday = (earningsInfo["todayEarnings"] as? Number)?.toDouble() ?: 0.0
                val currentPending = (earningsInfo["pendingBalance"] as? Number)?.toDouble() ?: 0.0

                transaction.update(advisorRef, "earningsInfo.totalLifetimeEarnings", currentLifetime + finalCost)
                transaction.update(advisorRef, "earningsInfo.todayEarnings", currentToday + finalCost)
                transaction.update(advisorRef, "earningsInfo.pendingBalance", currentPending + finalCost)

                // 4. Update Booking (Only if exists)
                if (bookingSnapshot.exists()) {
                    transaction.update(bookingRef, mapOf(
                        "bookingStatus" to "completed",
                        "paymentStatus" to "paid",
                        "sessionAmount" to finalCost,
                        "callDuration" to callDurationSeconds,
                        "callEndedAt" to com.google.firebase.Timestamp.now()
                    ))
                }

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
    /**
     * Fetches booked slots for a specific advisor and date.
     * @param advisorId The ID of the advisor.
     * @param date The date string in "dd/MM/yyyy" format.
     * @return List of booked time slots strings.
     */
    suspend fun getBookedSlots(advisorId: String, date: String): List<String> {
        return try {
            val snapshots = db.collection("scheduled_bookings")
                .whereEqualTo("advisorId", advisorId)
                .whereEqualTo("bookingDate", date)
                .whereNotEqualTo("bookingStatus", "cancelled")
                .get()
                .await()

            snapshots.documents.mapNotNull { it.getString("bookingSlot") }
        } catch (e: Exception) {
            android.util.Log.e("BookingRepository", "Error fetching booked slots", e)
            emptyList()
        }
    }
}
