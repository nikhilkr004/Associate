package com.example.associate.Activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.associate.Adapters.AdvisorReviewAdapter
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.Dialogs.InstantBookingDialog
import com.example.associate.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.associate.Repositories.RatingRepository
import com.example.associate.databinding.ActivityAdvisorProfileBinding
import com.example.associate.Dialogs.AppointmentTypeDialog
import com.example.associate.Dialogs.ScheduleBookingDialog
import com.example.associate.MainActivity
import com.example.associate.Adapters.AdvisorAwardAdapter
import com.google.android.material.chip.Chip

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

        // Display initial data from intent immediately
        displayAdvisorData()
        setupReviews()

        // Fetch fresh data from network to get latest availability
        fetchFreshAdvisorData()
    }

    private fun fetchFreshAdvisorData() {
        android.util.Log.d("AdvisorProfile", "Fetching fresh data for: ${advisor.basicInfo.id}")
        lifecycleScope.launch {
            val repository = com.example.associate.Repositories.AdvisorRepository()
            val freshAdvisor = repository.getAdvisorById(advisor.basicInfo.id)

            if (freshAdvisor != null) {
                advisor = freshAdvisor
                android.util.Log.d("AdvisorProfile", "Fresh data fetched. Updating UI.")
                displayAdvisorData()
            }
        }
    }

    private fun setupClickListeners() {
        binding.imgBack.setOnClickListener { finish() }
        binding.bookAdvisorBtn.setOnClickListener { showBookingDialog() }
    }

    private fun displayAdvisorData() {
        // Name & Bio
        binding.tvName.text = advisor.basicInfo.name
        binding.aboutTxt.text = advisor.professionalInfo.bio

        // Stats Grid
        binding.tvExperience.text = "${advisor.professionalInfo.experience} Years"
        binding.tvLanguages.text = advisor.professionalInfo.languages.joinToString(", ")
        binding.tvLocation.text = advisor.professionalInfo.officeLocation
        binding.tvAUM.text = "0-50 Cr" // Placeholder as per design

        // Rating
        binding.ratingTxt.text = advisor.performanceInfo.rating.toString()

        // Specialization Chips
        setupSpecializationChips()

        setupProfileImage()
        setupAvailabilityUI()
        
        // Restored Sections
        setupSocialLinks()
        setupAwards()
    }

    private fun setupSpecializationChips() {
        binding.specializationChipGroup.removeAllViews()
        val specializations = advisor.professionalInfo.specializations
        
        for (spec in specializations) {
            val chip = Chip(this)
            chip.text = spec
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E0F2F1"))
            chip.setTextColor(Color.parseColor("#00695C"))
            chip.isClickable = false
            chip.isCheckable = false
            binding.specializationChipGroup.addView(chip)
        }
    }

    private fun setupAvailabilityUI() {
        val availability = advisor.availabilityInfo.scheduledAvailability
        
        if (availability.isChatEnabled) {
            binding.chatLayout.alpha = 1.0f
            binding.chatLayout.setOnClickListener {
                checkBalanceAndStartChat()
            }
        } else {
            binding.chatLayout.alpha = 0.5f
            binding.chatLayout.setOnClickListener(null)
        }

        if (availability.isVideoCallEnabled) {
            binding.videoLayout.alpha = 1.0f
        } else {
             binding.videoLayout.alpha = 0.5f
        }

        if (availability.isAudioCallEnabled) {
            binding.audioLayout.alpha = 1.0f
        } else {
             binding.audioLayout.alpha = 0.5f
        }
    }

    private fun checkBalanceAndStartChat() {
        val repo = com.example.associate.Repositories.WalletRepository()
        val currentUserId =
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val balance = repo.getWalletBalance(currentUserId)
                if (balance > 10) {
                    startChatActivity()
                } else {
                    Toast.makeText(
                        this@AdvisorProfileActivity,
                        "Insufficient balance for chat",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AdvisorProfileActivity,
                    "Error checking balance",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startChatActivity(bookingId: String = "") {
        val intent = android.content.Intent(this, com.example.associate.Activities.ChatActivity::class.java)
        intent.putExtra(
            "CHAT_ID",
            bookingId
        )
        intent.putExtra(
            "BOOKING_ID",
            bookingId
        )
        intent.putExtra("ADVISOR_ID", advisor.basicInfo.id)
        intent.putExtra("ADVISOR_NAME", advisor.basicInfo.name)
        intent.putExtra("ADVISOR_AVATAR", advisor.basicInfo.profileImage)
        startActivity(intent)
    }

    private fun setupReviews() {
        val repository = RatingRepository()

        binding.advisorRecyclerview.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        repository.getReviewsForAdvisor(advisor.basicInfo.id) { reviews ->
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
                binding.rbReviewRating.rating = averageRating.toFloat()
                binding.tvReviewCount.text = "(${reviews.size} Reviews)"

            } else {
                binding.advisorRecyclerview.visibility = android.view.View.GONE
                binding.ratingTxt.text = "0.0"
                binding.rbReviewRating.rating = 0f
                binding.tvReviewCount.text = "(0 Reviews)"
            }
        }
    }

    private fun setupProfileImage() {
        Glide.with(this)
            .load(advisor.basicInfo.profileImage)
            .placeholder(R.drawable.user)
            .into(binding.profileImage)
    }

    private fun setupSocialLinks() {
        val resources = advisor.resources
        
        setupSocialIcon(binding.imgLinkedin, resources.linkedinProfile)
        setupSocialIcon(binding.imgTwitter, resources.twitterProfile)
        setupSocialIcon(binding.imgInstagram, resources.instagramProfile)
        setupSocialIcon(binding.imgWebsite, resources.website)
    }

    private fun setupSocialIcon(view: android.view.View, url: String) {
        if (url.isNotEmpty()) {
            view.visibility = android.view.View.VISIBLE
            view.setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                     Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            view.visibility = android.view.View.GONE
        }
    }
    
    private fun setupAwards() {
        val awards = advisor.professionalInfo.awards
        if (awards.isNotEmpty()) {
            binding.rvAwards.visibility = android.view.View.VISIBLE
            binding.tvAwardsTitle.visibility = android.view.View.VISIBLE
            binding.rvAwards.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.rvAwards.adapter = AdvisorAwardAdapter(awards)
        } else {
            binding.rvAwards.visibility = android.view.View.GONE
            binding.tvAwardsTitle.visibility = android.view.View.GONE
        }
    }

    private fun showBookingDialog() {
        val dialog = AppointmentTypeDialog(
            advisor = advisor,
            onInstantClick = {
                showInstantBookingDialog()
            },
            onScheduleClick = {
                showScheduleBookingDialog()
            }
        )
        dialog.show(supportFragmentManager, "AppointmentTypeDialog")
    }

    private fun showInstantBookingDialog() {
        val dialog = InstantBookingDialog(advisor) { bookingId ->
            // User requested to go to Home Fragment
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags =
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        dialog.show(supportFragmentManager, "InstantBookingDialog")
    }

    private fun showScheduleBookingDialog() {
        val dialog = ScheduleBookingDialog(advisor) {
            Toast.makeText(this, "Session scheduled successfully!", Toast.LENGTH_SHORT).show()
        }
        dialog.show(supportFragmentManager, "ScheduleBookingDialog")
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
