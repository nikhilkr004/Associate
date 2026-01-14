package com.example.associate.Activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.associate.DataClass.SessionBookingDataClass
import com.example.associate.DataClass.UserData
import com.example.associate.R
import com.example.associate.databinding.ActivityBookingSummaryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class BookingSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingSummaryBinding
    private var paymentListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onDestroy() {
        super.onDestroy()
        paymentListener?.remove()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadData() {
        val booking = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("BOOKING_DATA", SessionBookingDataClass::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("BOOKING_DATA")
        }

        val advisor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ADVISOR_DATA", UserData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ADVISOR_DATA")
        }

        if (booking != null) {
            populateBookingInfo(booking)
            listenForPaymentUpdates(booking.bookingId)
        } else {
             // Fallback if only ID passed in Intent (which we do in Call Activities now)
             val extraBookingId = intent.getStringExtra("BOOKING_ID")
             if (!extraBookingId.isNullOrEmpty()) {
                 listenForPaymentUpdates(extraBookingId)
             } else {
                 binding.tvTotalCost.text = "N/A"
                 binding.tvPaymentStatus.text = "Unknown"
             }
        }

        if (advisor != null) {
            populateAdvisorInfo(advisor)
        } else {
            // Check for Advisor ID string
            val advisorId = intent.getStringExtra("ADVISOR_ID")
            if (!advisorId.isNullOrEmpty()) {
                fetchAdvisorDetails(advisorId)
            } else if (booking != null) {
                 binding.tvAdvisorName.text = booking.advisorName
                 binding.tvAdvisorEmail.text = "Email not available" 
                 binding.tvAdvisorGender.text = "Gender: N/A"
            }
        }
    }
    
    private fun fetchAdvisorDetails(advisorId: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("advisors").document(advisorId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                     val name = document.getString("name") ?: "Advisor"
                     val email = document.getString("email") ?: ""
                     // Gender might be nested or direct, check safeguards
                     val gender = document.getString("gender") ?: "N/A"
                     
                     binding.tvAdvisorName.text = name
                     binding.tvAdvisorEmail.text = email
                     binding.tvAdvisorGender.text = "Gender: ${gender.replaceFirstChar { it.uppercase() }}"
                     
                     // Avatar
                     val basicInfo = document.get("basicInfo") as? Map<*, *>
                     var avatarUrl = basicInfo?.get("profileImage") as? String
                     if (avatarUrl.isNullOrEmpty()) {
                        avatarUrl = document.getString("profileImage")
                     }
                     
                     if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this).load(avatarUrl).placeholder(R.drawable.user).into(binding.imgAdvisor)
                     }
                }
            }
    }

    private fun listenForPaymentUpdates(bookingId: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        // Try Instant First, then Scheduled
        // Or better: Observe BOTH or try to deduce?
        // Let's assume we can try one and if fails try other?
        // Snapshot listener is persistent. 
        // We can check if document exists in one, if not try other.
        
        checkAndListen(db, "instant_bookings", bookingId) { found ->
            if (!found) {
                checkAndListen(db, "scheduled_bookings", bookingId) { _ -> }
            }
        }
    }
    
    private fun checkAndListen(db: com.google.firebase.firestore.FirebaseFirestore, collection: String, bookingId: String, onResult: (Boolean) -> Unit) {
        val docRef = db.collection(collection).document(bookingId)
        
        docRef.get().addOnSuccessListener { startDoc ->
            if (startDoc.exists()) {
                onResult(true)
                // Start Listener
                paymentListener?.remove() // Safe cleanup
                paymentListener = docRef.addSnapshotListener { snapshot, e ->
                     if (e != null) return@addSnapshotListener
                     if (snapshot != null && snapshot.exists()) {
                         val paymentStatus = snapshot.getString("paymentStatus") ?: "pending"
                         val sessionAmount = snapshot.getDouble("sessionAmount") ?: 0.0
                         val status = snapshot.getString("bookingStatus") ?: "" // or 'status'
                         
                         // Update UI
                         binding.tvTotalCost.text = "₹${String.format("%.2f", sessionAmount)}"
                         
                         binding.tvPaymentStatus.text = paymentStatus.replaceFirstChar { it.uppercase() }
                         
                         if (paymentStatus.equals("paid", ignoreCase = true) || paymentStatus.equals("completed", ignoreCase = true)) {

                             binding.tvPaymentStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                         } else if (paymentStatus.equals("failed", ignoreCase = true)) {
                             binding.tvPaymentStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                         } else {
                             binding.tvPaymentStatus.setTextColor(android.graphics.Color.parseColor("#F57C00"))
                         }
                         
                         // Fill other info if missing (e.g. from Intent)
                         if (binding.tvDateInfo.text == "N/A") {
                             val ts = snapshot.get("bookingTimestamp") as? com.google.firebase.Timestamp
                             if (ts != null) {
                                 val dateObj = ts.toDate()
                                 val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                 val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                 binding.tvDateInfo.text = dateFormat.format(dateObj)
                                 binding.tvTimeInfo.text = timeFormat.format(dateObj)
                             }
                         }
                     }
                }
            } else {
                onResult(false)
            }
        }
    }

    private fun populateAdvisorInfo(advisor: UserData) {
        binding.tvAdvisorName.text = advisor.name
        binding.tvAdvisorEmail.text = advisor.email
        binding.tvAdvisorGender.text = "Gender: ${advisor.gender.replaceFirstChar { it.uppercase() }}"

        if (!advisor.profilePhotoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(advisor.profilePhotoUrl)
                .placeholder(R.drawable.user)
                .into(binding.imgAdvisor)
        } else {
            binding.imgAdvisor.setImageResource(R.drawable.user)
        }
    }

    private fun populateBookingInfo(booking: SessionBookingDataClass) {
        // Appt Info
        binding.tvBookingIdInfo.text = booking.bookingId.uppercase() // Or take last 8 chars if prefer short
        binding.tvTypeInfo.text = booking.bookingType.uppercase()
        
        val dateObj = booking.bookingTimestamp?.toDate()
        if (dateObj != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            
            binding.tvDateInfo.text = dateFormat.format(dateObj)
            binding.tvTimeInfo.text = timeFormat.format(dateObj)
        } else {
             binding.tvDateInfo.text = "N/A"
             binding.tvTimeInfo.text = "N/A"
        }

        // Information
        val urgency = if (booking.urgencyLevel.equals("Scheduled", ignoreCase = true)) "Scheduled" else "Instant"
        val purpose = if (booking.purpose.isNotEmpty()) booking.purpose else "$urgency ${booking.bookingType}"
        
        binding.tvProblem.text = purpose
        binding.tvNotes.text = if (booking.additionalNotes.isNotEmpty()) booking.additionalNotes else "No additional notes"
    }
}

// Updated for repository activity
