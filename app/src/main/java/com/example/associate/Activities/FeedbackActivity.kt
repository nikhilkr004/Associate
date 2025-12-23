package com.example.associate.Activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.associate.DataClass.SessionBookingDataClass
import com.example.associate.DataClass.DialogUtils
import com.example.associate.Repositories.UserRepository
import com.example.associate.databinding.ActivityFeedbackBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedbackActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityFeedbackBinding.inflate(layoutInflater)
    }

    private var bookingData: SessionBookingDataClass? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Get Booking Data
        bookingData = intent.getParcelableExtra("BOOKING_DATA")

        initListeners()
    }

    private fun initListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSubmit.setOnClickListener {
            submitFeedback()
        }
    }

    private fun submitFeedback() {
        val rating = binding.ratingBar.rating
        val likedMost = binding.etFeedbackLike.text.toString().trim()
        val improvement = binding.etFeedbackImprove.text.toString().trim()
        val additional = binding.etFeedbackAdditional.text.toString().trim()

        if (rating == 0f) {
            Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val fullReview = StringBuilder()
        if (likedMost.isNotEmpty()) fullReview.append("Liked: $likedMost\n")
        if (improvement.isNotEmpty()) fullReview.append("Improve: $improvement\n")
        if (additional.isNotEmpty()) fullReview.append("Comments: $additional")

        val reviewText = fullReview.toString().trim().ifEmpty { "No written feedback" }

        // Determine Advisor ID
        val advisorId = bookingData?.advisorId ?: ""
        
        if (advisorId.isEmpty()) {
            Toast.makeText(this, "Error: Advisor information missing", Toast.LENGTH_SHORT).show()
            return
        }

        // Submit to Repository (assuming UserRepository handles reviews or logic is here)
        // Since CallHistoryViewModel Logic was: viewModel.submitReview(booking, rating, review)
        // We will move that logic here or replicate it. 
        // For simplicity/directness, let's use the Repository or ViewModel logic. 
        // Since this is an Activity, we can use a ViewModel or direct Repo call.
        // Let's use direct Repo call to save time/complexity.

        // Submit to Repository
        val advisorRepository = com.example.associate.Repositories.AdvisorRepository()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
             CoroutineScope(Dispatchers.IO).launch {
                 try {
                     // Add Review
                     val success = advisorRepository.addAdvisorReview(
                         advisorId = advisorId,
                         userId = currentUser.uid,
                         rating = rating.toDouble(),
                         review = reviewText
                     )

                     if (success) {
                         // Also update booking status if needed, or link review to booking
                         withContext(Dispatchers.Main) {
                             DialogUtils.showStatusDialog(
                                 this@FeedbackActivity, 
                                 true, 
                                 "Success", 
                                 "Thank you for your feedback!"
                             ) {
                                 finish()
                             }
                         }
                     } else {
                         withContext(Dispatchers.Main) {
                             Toast.makeText(this@FeedbackActivity, "Failed to submit review", Toast.LENGTH_SHORT).show()
                         }
                     }
                 } catch (e: Exception) {
                     withContext(Dispatchers.Main) {
                         Toast.makeText(this@FeedbackActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                     }
                 }
             }
        }
    }
}
