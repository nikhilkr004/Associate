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
        val reviewText = binding.etReview.text.toString().trim()

        if (rating == 0f) {
            Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 Requirement: Review Text is Required
        if (reviewText.isEmpty()) {
            Toast.makeText(this, "Please write a review", Toast.LENGTH_SHORT).show()
            return
        }
        
        // No StringBuilder needed anymore, used direct text
        // val fullReview = ...

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
        // Submit to Repository
        val ratingRepository = com.example.associate.Repositories.RatingRepository()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
             // Create Rating Object
             val ratingObj = com.example.associate.DataClass.Rating(
                 advisorId = advisorId,
                 userId = currentUser.uid,
                 rating = rating,
                 review = reviewText,
                 callId = bookingData?.bookingId ?: "",
                 timestamp = com.google.firebase.Timestamp.now()
             )

             CoroutineScope(Dispatchers.Main).launch {
                 // Use callback based repository in coroutine? Or plain callback?
                 // RatingRepository.saveRating is callback based. 
                 // Let's run it on Main thread or handle callback properly.
                 
                 ratingRepository.saveRating(ratingObj) { success, error ->
                     if (success) {
                             DialogUtils.showStatusDialog(
                                 this@FeedbackActivity, 
                                 true, 
                                 "Success", 
                                 "Thank you for your feedback!",
                                 action = {
                                     finish()
                                 }
                             )
                         } else {
                             Toast.makeText(this@FeedbackActivity, "Failed: $error", Toast.LENGTH_SHORT).show()
                         }
                     }
                 }
             }
    }
}


// Updated for repository activity
