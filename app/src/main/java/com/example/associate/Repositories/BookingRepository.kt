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
            android.util.Log.e("BookingRepository", "=== TRANSACTION START ===")
            android.util.Log.e("BookingRepository", "bookingId: $bookingId")
            android.util.Log.e("BookingRepository", "userId: $userId")
            android.util.Log.e("BookingRepository", "advisorId: $advisorId")
            android.util.Log.e("BookingRepository", "duration: $callDurationSeconds seconds")
            android.util.Log.e("BookingRepository", "ratePerMinute: $ratePerMinute")
            android.util.Log.e("BookingRepository", "isInstant: $isInstant")
            
            db.runTransaction { transaction ->
                val userRef = db.collection("users").document(userId)
                val advisorRef = db.collection("advisors").document(advisorId)
                
                // 1. Resolve Booking Document
                var bookingRef = db.collection(if (isInstant) "instant_bookings" else "scheduled_bookings").document(bookingId)
                var bookingSnapshot = transaction.get(bookingRef)
                
                // Check Alternate Collection if not found
                if (!bookingSnapshot.exists()) {
                     val altRef = db.collection(if (isInstant) "scheduled_bookings" else "instant_bookings").document(bookingId)
                     val altSnap = transaction.get(altRef)
                     if (altSnap.exists()) {
                         bookingRef = altRef
                         bookingSnapshot = altSnap
                         android.util.Log.w("BookingRepository", "Found booking in alternate collection: ${altRef.path}")
                     }
                }

                // 2. Ghost Booking Recovery: If specific doc still missing, CREATE NEW INSTANT DOC.
                var isGhostBooking = false
                if (!bookingSnapshot.exists()) {
                     android.util.Log.w("BookingRepository", "Booking Doc Missing. Creating Ghost Booking to preserve transaction.")
                     isGhostBooking = true
                     bookingRef = db.collection("instant_bookings").document(bookingId) 
                     // No snapshot to read from yet
                } else if (bookingSnapshot.getString("bookingStatus") == "completed") {
                    android.util.Log.w("BookingRepository", "Booking ALREADY COMPLETED. Skipping.")
                    return@runTransaction true
                }

                // 3. Determine Payment Type
                val isReallyScheduled = bookingRef.path.contains("scheduled_bookings")
                val shouldDeductFromUser = if (isGhostBooking) true else !isReallyScheduled

                // 4. Calculate Cost
                val totalCost = if (shouldDeductFromUser) {
                     val rawCost = (callDurationSeconds / 60.0) * ratePerMinute
                     if (rawCost < 0) 0.0 else rawCost
                } else {
                     ratePerMinute // Pre-paid Session Amount
                }
                val finalCost = java.math.BigDecimal(totalCost).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
                
                android.util.Log.e("BookingRepository", "Transaction Decision -> isReallyScheduled: $isReallyScheduled, shouldDeduct: $shouldDeductFromUser")
                android.util.Log.e("BookingRepository", "Calculated Cost: $finalCost")

                // 5. User Wallet Deduction & Logic
                var isPaid = false
                var paymentStatus = "pending"
                
                if (shouldDeductFromUser) {
                    // --- INSTANT CALL LOGIC (User pays now) ---
                    val walletRef = db.collection("wallets").document(userId)
                    val walletSnapshot = transaction.get(walletRef)
                    val currentBalance = if (walletSnapshot.exists()) walletSnapshot.getDouble("balance") ?: 0.0 else 0.0
                    
                    if (currentBalance >= finalCost) {
                        // SUCCESSFUL PAYMENT
                        isPaid = true
                        paymentStatus = "paid"
                        
                        // Deduct
                        val newBalance = currentBalance - finalCost
                        if (walletSnapshot.exists()) {
                            transaction.update(walletRef, "balance", newBalance)
                            transaction.update(walletRef, "totalSpent", (walletSnapshot.getDouble("totalSpent") ?: 0.0) + finalCost)
                            transaction.update(walletRef, "transactionCount", (walletSnapshot.getLong("transactionCount") ?: 0) + 1)
                        } else {
                             val newWallet = hashMapOf(
                                "userId" to userId, "balance" to newBalance, "totalSpent" to finalCost, "transactionCount" to 1
                            )
                            transaction.set(walletRef, newWallet)
                        }
                        
                        // User History (Success)
                        val userTxRef = userRef.collection("transactions").document()
                        val userTx = hashMapOf(
                            "amount" to finalCost,
                            "type" to "DEBIT",
                            "status" to "success",
                            "description" to "Call Fee",
                            "timestamp" to FieldValue.serverTimestamp(),
                            "relatedBookingId" to bookingId
                        )
                        transaction.set(userTxRef, userTx)
                        
                        // Credit Advisor
                        creditAdvisor(transaction, advisorRef, finalCost, bookingId, "Session Earning")
                        
                    } else {
                        // INSUFFICIENT BALANCE -> PENDING
                        isPaid = false
                        paymentStatus = "pending"
                        android.util.Log.w("BookingRepository", "Insufficient Balance: $currentBalance < $finalCost")
                        
                        // User History (Pending) - To track the attempt
                        val userTxRef = userRef.collection("transactions").document()
                        val userTx = hashMapOf(
                            "amount" to finalCost,
                            "type" to "DEBIT",
                            "status" to "pending",
                            "description" to "Call Fee - Insufficient Balance",
                            "timestamp" to FieldValue.serverTimestamp(),
                            "relatedBookingId" to bookingId
                        )
                        transaction.set(userTxRef, userTx)
                        // Do NOT credit advisor yet
                    }
                } else {
                    // --- SCHEDULED CALL LOGIC (Pre-paid) ---
                    // User already paid. Skip deduction.
                    // Important: Confirmed by user plan: "Credits Advisor (moves from Pending Balance to Earnings...)"
                    
                    isPaid = true
                    paymentStatus = "paid" // It was pre-paid
                    
                    // Credit Advisor (Funds move from Platform to Advisor)
                    creditAdvisor(transaction, advisorRef, finalCost, bookingId, "Session Earning (Scheduled)")
                }

                // 7. Finalize Booking Doc (Ghost or Real)
                val bookingData: MutableMap<String, Any> = hashMapOf(
                    "bookingStatus" to "completed", // Always mark session as completed? Or pending? User said "set status is pending"
                    // If payment is pending, booking is technically "completed" (call ended) but payment is pending.
                    // Let's keep bookingStatus completed, paymentStatus pending.
                    "paymentStatus" to paymentStatus,
                    "sessionAmount" to finalCost,
                    "callDuration" to callDurationSeconds,
                    "channelName" to "call_$bookingId" 
                )
                
                if (isGhostBooking) {
                    bookingData["userId"] = userId
                    bookingData["advisorId"] = advisorId
                    bookingData["type"] = "instant"
                    bookingData["urgencyLevel"] = "Medium"
                    bookingData["bookingId"] = bookingId
                    bookingData["timestamp"] = FieldValue.serverTimestamp()
                    transaction.set(bookingRef, bookingData)
                } else {
                    transaction.update(bookingRef, bookingData)
                }
                
                isPaid // Return the payment success state
            }.await()
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

    /**
     * Updates the status of a booking document.
     * Useful for enforcing "completed" status if Cloud Function doesn't update it effectively.
     */
    suspend fun updateBookingStatus(bookingId: String, status: String, isInstant: Boolean) {
        try {
            val collectionName = if (isInstant) "instant_bookings" else "scheduled_bookings"
            val updates = mapOf(
                "bookingStatus" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection(collectionName).document(bookingId).update(updates).await()
            android.util.Log.i("BookingRepository", "Forced booking status update: $bookingId -> $status")
        } catch (e: Exception) {
            android.util.Log.e("BookingRepository", "Failed to update booking status: ${e.message}")
        }
    }

    // Helper to credit advisor wallet
    private fun creditAdvisor(
        transaction: com.google.firebase.firestore.Transaction,
        advisorRef: com.google.firebase.firestore.DocumentReference,
        amount: Double,
        bookingId: String,
        description: String
    ) {
        val advisorSnapshot = transaction.get(advisorRef)
        if (advisorSnapshot.exists()) {
            val earningsInfo = advisorSnapshot.get("earningsInfo") as? Map<String, Any> ?: emptyMap()
            val currentLifetime = (earningsInfo["totalLifetimeEarnings"] as? Number)?.toDouble() ?: 0.0
            val currentToday = (earningsInfo["todayEarnings"] as? Number)?.toDouble() ?: 0.0
            val currentPending = (earningsInfo["pendingBalance"] as? Number)?.toDouble() ?: 0.0
            
            // Assuming we update pendingBalance; change logic if it should go directly to available.
            transaction.update(advisorRef, "earningsInfo.totalLifetimeEarnings", currentLifetime + amount)
            transaction.update(advisorRef, "earningsInfo.todayEarnings", currentToday + amount)
            transaction.update(advisorRef, "earningsInfo.pendingBalance", currentPending + amount)
            
            val advisorTxRef = advisorRef.collection("transactions").document()
            val advisorTx = hashMapOf(
                "amount" to amount,
                "type" to "CREDIT",
                "status" to "success",
                "description" to description,
                "timestamp" to FieldValue.serverTimestamp(),
                "relatedBookingId" to bookingId
            )
            transaction.set(advisorTxRef, advisorTx)
        }
    }
}
