package com.example.associate.Repositories

import android.content.Context
import android.util.Log
import com.example.associate.DataClass.SessionBookingDataClass
import com.example.associate.DataClass.WalletDataClass
import com.example.associate.Notification.NotificationManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class SessionBookingManager(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val notificationManager = NotificationManager() // ✅ ADD THIS

    fun createInstantBooking(
        advisorId: String,
        advisorName: String,
        purpose: String,
        preferredLanguage: String,
        additionalNotes: String = "",
        urgencyLevel: String = "medium",
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: run {
            onFailure("User not logged in")
            return
        }

        val studentId = currentUser.uid
        val studentName = currentUser.displayName ?: "Student"

        checkActiveBooking(studentId) { hasActiveBooking ->
            if (hasActiveBooking) {
                onFailure("You already have an active session request. Please wait for advisor response or until it expires.")
                return@checkActiveBooking
            }

            checkWalletBalance(studentId) { balance ->
                if (balance < 100.0) {
                    onFailure("Insufficient balance. Minimum ₹100 required.")
                    return@checkWalletBalance
                }

                createBooking(advisorId, advisorName, studentId, studentName, purpose, preferredLanguage, additionalNotes, urgencyLevel, onSuccess, onFailure)
            }
        }
    }

    private fun createBooking(
        advisorId: String,
        advisorName: String,
        studentId: String,
        studentName: String,
        purpose: String,
        preferredLanguage: String,
        additionalNotes: String,
        urgencyLevel: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val bookingId = generateReadableId()

        val bookingData = SessionBookingDataClass(
            bookingId = bookingId,
            studentId = studentId,
            advisorId = advisorId,
            studentName = studentName,
            advisorName = advisorName,
            purpose = purpose,
            preferredLanguage = preferredLanguage,
            additionalNotes = additionalNotes,
            urgencyLevel = urgencyLevel,
            bookingTimestamp = Timestamp.now(),
            advisorResponseDeadline = Timestamp(Date(System.currentTimeMillis() + (5 * 60 * 1000))),
            sessionAmount = 100.0,
            paymentStatus = "pending",
            bookingStatus = "pending"
        )

        db.collection("instant_bookings")
            .document(bookingId)
            .set(bookingData)
            .addOnSuccessListener {
                Log.d("DEBUG", "Booking created successfully: $bookingId")

                // ✅ SEND NOTIFICATION TO ADVISOR
                sendBookingNotificationToAdvisor(advisorId, studentName, bookingId, advisorName)

                onSuccess("Booking request sent! Advisor has 5 minutes to call you.")
                startResponseTimer(bookingId)
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUG", "Booking creation failed: ${exception.message}")
                onFailure("Booking failed: ${exception.message}")
            }
    }

    // ✅ NEW: Send notification to advisor
    private fun sendBookingNotificationToAdvisor(
        advisorId: String,
        studentName: String,
        bookingId: String,
        advisorName: String
    ) {
        notificationManager.sendBookingNotificationToAdvisor(
            advisorId = advisorId,
            studentName = studentName,
            bookingId = bookingId,
            advisorName = advisorName
        )
    }

    // ... (Rest of your existing methods remain same)
    private fun checkActiveBooking(studentId: String, onResult: (Boolean) -> Unit) {
        Log.d("DEBUG", "Checking active bookings for student: $studentId")

        db.collection("instant_bookings")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("bookingStatus", "pending")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("DEBUG", "Found ${documents.size()} documents")

                val hasActive = documents.any { document ->
                    try {
                        val booking = document.toObject(SessionBookingDataClass::class.java)
                        Log.d("DEBUG", "Booking parsed: ${booking.studentName}, Active: ${booking.isActive()}")
                        booking.isActive()
                    } catch (e: Exception) {
                        Log.e("DEBUG", "Error parsing booking document: ${e.message}")
                        Log.e("DEBUG", "Document data: ${document.data}")
                        false
                    }
                }
                Log.d("DEBUG", "Has active booking: $hasActive")
                onResult(hasActive)
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUG", "Error checking active bookings: ${exception.message}")
                onResult(false)
            }
    }

    private fun checkWalletBalance(userId: String, onBalanceChecked: (Double) -> Unit) {
        db.collection("wallets")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val wallet = document.toObject(WalletDataClass::class.java)
                    val balance = wallet?.balance ?: 0.0
                    Log.d("DEBUG", "Wallet balance: $balance")
                    onBalanceChecked(balance)
                } else {
                    Log.d("DEBUG", "Wallet document does not exist")
                    onBalanceChecked(0.0)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUG", "Error checking wallet balance: ${exception.message}")
                onBalanceChecked(0.0)
            }
    }

    private fun startResponseTimer(bookingId: String) {
        Log.d("DEBUG", "Starting 5-minute response timer for booking: $bookingId")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkAndExpireBooking(bookingId)
        }, 5 * 60 * 1000)
    }

    private fun checkAndExpireBooking(bookingId: String) {
        Log.d("DEBUG", "Checking if booking should expire: $bookingId")

        db.collection("instant_bookings")
            .document(bookingId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        val booking = document.toObject(SessionBookingDataClass::class.java)
                        if (booking?.bookingStatus == "pending" && booking.advisorCalledAt == null) {
                            Log.d("DEBUG", "Expiring booking: $bookingId")
                            expireBooking(bookingId)
                        } else {
                            Log.d("DEBUG", "Booking does not need expiration: ${booking?.bookingStatus}")
                        }
                    } catch (e: Exception) {
                        Log.e("DEBUG", "Error parsing booking for expiration: ${e.message}")
                    }
                } else {
                    Log.d("DEBUG", "Booking document not found: $bookingId")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUG", "Error checking booking expiration: ${exception.message}")
            }
    }

    private fun expireBooking(bookingId: String) {
        val updates = hashMapOf<String, Any>(
            "bookingStatus" to "expired",
            "updatedAt" to Timestamp.now()
        )

        db.collection("instant_bookings")
            .document(bookingId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("DEBUG", "Booking expired successfully: $bookingId")
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUG", "Error expiring booking: ${exception.message}")
            }
    }

    fun generateReadableId(): String {
        val date = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val random = (100000 + _root_ide_package_.kotlin.random.Random.nextInt(0, 900000)).toString()
        return "BK$date$random"
    }

    fun getBookingById(bookingId: String, onResult: (SessionBookingDataClass?) -> Unit) {
        db.collection("instant_bookings")
            .document(bookingId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        val booking = document.toObject(SessionBookingDataClass::class.java)
                        onResult(booking)
                    } catch (e: Exception) {
                        Log.e("DEBUG", "Error parsing booking: ${e.message}")
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("DEBUG", "Error getting booking: ${exception.message}")
                onResult(null)
            }
    }
}