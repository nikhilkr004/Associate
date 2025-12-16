package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.Adapters.AdvisorReviewAdapter
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.Dialogs.InstantBookingDialog
import com.example.associate.R
import com.example.associate.Repositorys.RatingRepository
import com.example.associate.databinding.ActivityAdvisorProfileBinding

class AdvisorProfileActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityAdvisorProfileBinding.inflate(layoutInflater)
    }
    private lateinit var advisor: AdvisorDataClass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        getIntentData()
        setupClickListeners()
    }



    private fun getIntentData() {
        advisor = intent.getParcelableExtra("ADVISOR_DATA") ?: run {
            showErrorAndFinish("Advisor data not found")
            return
        }
        displayAdvisorData()
        setupReviews()
    }

    private fun setupClickListeners() {
        binding.imgBack.setOnClickListener { finish() }
        binding.bookAdvisorBtn.setOnClickListener { showBookingDialog() }
    }

    private fun displayAdvisorData() {
        binding.tvName.text = advisor.name
        binding.aboutTxt.text = advisor.bio
        binding.tvLanguages.text = advisor.languages.toString()
        binding.tvCompany.text = advisor.officeLocation
        binding.tvExperience.text = advisor.experience.toString()+" years"
        binding.tvExperience.text = advisor.experience.toString()+" years"
        
        // Use Pipe Separator for Specializations
        binding.sepcializationTxt.text = advisor.specializations.joinToString(" | ")
        
        // Set Rating
        binding.ratingTxt.text = advisor.rating.toString()
        
        setupProfileImage()
//        setupAdvisorStatus()
    }

    private fun setupReviews() {
        val repository = RatingRepository()
        
        // Show loading state if needed, or just fetch

        binding.advisorRecyclerview.layoutManager= LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,false)
        
        repository.getReviewsForAdvisor(advisor.id) { reviews ->
            if (reviews.isNotEmpty()) {
                val adapter = AdvisorReviewAdapter(reviews)
                binding.advisorRecyclerview.adapter = adapter
                binding.advisorRecyclerview.visibility = android.view.View.VISIBLE

                // Calculate Average Rating
                val totalRating = reviews.map { it.rating }.sum()
                val averageRating = totalRating / reviews.size

                // Update UI with calculated rating
                val formattedRating = String.format("%.1f", averageRating)
                binding.ratingTxt.text = formattedRating
                binding.rbReviewRating.rating = averageRating

            } else {
                binding.advisorRecyclerview.visibility = android.view.View.GONE
                binding.ratingTxt.text = "0.0"
                binding.rbReviewRating.rating = 0f
            }
        }
    }

    private fun setupProfileImage() {
        Glide.with(this)
            .load(advisor.profileimage)
            .placeholder(R.drawable.user)
            .into(binding.profileImage)
    }



    private fun showBookingDialog() {
        val dialog = InstantBookingDialog(advisor) {
            // Booking success callback
            Toast.makeText(this, "Session booked successfully!", Toast.LENGTH_SHORT).show()
        }
        dialog.show(supportFragmentManager, "InstantBookingDialog")
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}