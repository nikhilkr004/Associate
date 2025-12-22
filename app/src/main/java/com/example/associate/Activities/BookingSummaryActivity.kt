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
        }

        if (advisor != null) {
            populateAdvisorInfo(advisor)
        } else if (booking != null) {
            // Fallback if full advisor data isn't passed but we have basics in booking
             binding.tvAdvisorName.text = booking.advisorName
             binding.tvAdvisorEmail.text = "Email not available" // Or hide
             binding.tvAdvisorGender.text = "Gender: N/A"
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
