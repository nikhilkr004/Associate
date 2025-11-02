package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.Dialogs.InstantBookingDialog
import com.example.associate.R
import com.example.associate.databinding.ActivityAdvisorProfileBinding
import com.google.firebase.auth.FirebaseAuth

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
    }

    private fun setupClickListeners() {
        binding.imgBack.setOnClickListener { finish() }
        binding.bookAdvisorBtn.setOnClickListener { showBookingDialog() }
    }

    private fun displayAdvisorData() {
        binding.tvName.text = advisor.name
        binding.aboutTxt.text = advisor.bio
        binding.tvLanguages.text = advisor.getLanguagesString()
        binding.tvCompany.text = advisor.officeLocation
        binding.tvExperience.text = advisor.getExperienceString()

        setupSpecializations(advisor.specializations)
        setupProfileImage()
        setupAdvisorStatus()
    }

    private fun setupSpecializations(specializations: List<String>) {
        if (specializations.isEmpty()) {

            return
        }

        val container = binding.specializationsContainer
        container.removeAllViews()
        specializations.forEach { specialization ->

            val chip =
                layoutInflater.inflate(R.layout.chip_specialization, container, false) as TextView
            chip.text = specialization
            container.addView(chip)
        }
    }

    private fun setupProfileImage() {
        Glide.with(this)
            .load(advisor.profileimage)
            .placeholder(R.drawable.user)
            .into(binding.profileImage)
    }

    private fun setupAdvisorStatus() {
        binding.advisorStatus.text = if (advisor.isactive == "true") "Online" else "Offline"
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