package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.DialogUtils
import com.example.associate.R

import com.example.associate.databinding.ActivityAdvisorProfileBinding
import com.google.firebase.firestore.FirebaseFirestore

class AdvisorProfileActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityAdvisorProfileBinding.inflate(layoutInflater)
    }
    private val db = FirebaseFirestore.getInstance()
    private lateinit var advisorId: String
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
        // Get advisor ID from intent
        advisorId = intent.getStringExtra("ADVISOR_ID") ?: ""
        if (advisorId.isEmpty()) {
            finish()
            return
        }



        /// setup back button
        binding.imgBack.setOnClickListener {
            finish()
        }

        fetchAdvisorDetails()
    }


    private fun fetchAdvisorDetails() {
        DialogUtils.showLoadingDialog(this, "Fetching Advisor Details")

        db.collection("advisors").document(advisorId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    advisor = document.toObject(AdvisorDataClass::class.java)!!
                    advisor.id = document.id
                    populateAdvisorData()
                } else {
                    // Handle case where advisor doesn't exist
                    finish()
                }
                DialogUtils.hideLoadingDialog()
            }
            .addOnFailureListener { exception ->
                // Handle error
                DialogUtils.hideLoadingDialog()
                finish()
            }
    }

    private fun populateAdvisorData() {
        // Set basic information
        binding.tvName.text = advisor.name
        binding.tvTitle.text = advisor.name
        binding.aboutNameTxt.text="About ${advisor.name}"
//        binding.userRole.text = advisor.specializations!!
        binding.tvExperience.text = "0 - ${advisor.experience} Years Experience"

        Glide.with(this).load(advisor.profileimage).placeholder(R.drawable.user)
            .into(binding.profileImage)

        // Set languages
        // Set languages
        val languagesText = advisor.languages.joinToString(", ")
        binding.tvLanguages.text = languagesText
        // Setup specializations
        setupSpecializations(advisor.specializations)


    }


    private fun setupSpecializations(specializations: List<String>) {
        if (specializations.isEmpty()) {
//       binding.specializationsSection.visibility = View.GONE
            return
        }

//        binding.specializationsSection.visibility = View.VISIBLE

        // You can use a recyclerview or simply add chips
        val container = binding.specializationsContainer
        container.removeAllViews()
        specializations.forEach { specialization ->

            val chip =
                layoutInflater.inflate(R.layout.chip_specialization, container, false) as TextView
            chip.text = specialization
            container.addView(chip)
        }
    }


}