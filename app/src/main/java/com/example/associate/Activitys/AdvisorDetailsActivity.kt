package com.example.associate.Activitys

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.associate.Activitys.AdvisorAdcivitys.AdvisorHomeActivity
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.DialogUtils
import com.example.associate.R
import com.example.associate.Repositorys.AdvisorRepository
import com.example.associate.databinding.ActivityAdvisorDetailsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AdvisorDetailsActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityAdvisorDetailsBinding.inflate(layoutInflater)
    }

    private val repository= AdvisorRepository()

    private var selectedGender: String = "Male" // Default selection
    private lateinit var auth: FirebaseAuth
    private lateinit var userNumber: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth= FirebaseAuth.getInstance()
        userNumber=intent.getStringExtra("user_number").toString()


        binding.genderMale.setOnClickListener {
            selectGender(binding.genderMale, binding.genderFemale, "Male")
        }

        binding.genderFemale.setOnClickListener {
            selectGender(binding.genderFemale, binding.genderMale, "Female")
        }

        binding.enterBtn.setOnClickListener {
            if (validateForm()) {
                saveAdvisorDetails()
            }
        }

    }

    private fun selectGender(selectedView: TextView, unselectedView: TextView, gender: String) {

        // Update visual selection
        selectedView.background = ContextCompat.getDrawable(this, R.drawable.male_female_selected_bg)
        selectedView.setTextColor(ContextCompat.getColor(this, R.color.white))

        unselectedView.background = ContextCompat.getDrawable(this, R.drawable.male_female_bg)
        unselectedView.setTextColor(ContextCompat.getColor(this, R.color.black))

        // Store selected value
        selectedGender = gender

        // Optional: Log or toast to verify selection
        Log.d("GenderSelection", "Selected gender: $selectedGender")
        Toast.makeText(this, "Selected: $selectedGender", Toast.LENGTH_SHORT).show()
    }

    private fun validateForm(): Boolean {
        with(binding) {
            if (userName.text.isNullOrEmpty()) {
                userName.error = "Name is required"
                return false
            }
            if (userEmail.text.isNullOrEmpty()) {
                userEmail.error = "Email is required"
                return false
            }
            if (userCity.text.isNullOrEmpty()) {
                userCity.error = "City is required"
                return false
            }
            if (selectedGender.isEmpty()){
                Toast.makeText(this@AdvisorDetailsActivity, "select gender", Toast.LENGTH_SHORT).show()
            }
            return true
        }
    }

    private fun saveAdvisorDetails(){

        DialogUtils.showLoadingDialog(this, message = "Signing in...")
        val name =binding.userName.text.toString()
        val email=binding.userEmail.text.toString()
        val city=binding.userCity.text.toString()

        val advisor = AdvisorDataClass.AdvisorBasicInfo(
            advisorId = auth.currentUser!!.uid.toString(),
            name= name,
            email=email,
            city=city,
            joinAt = Timestamp.now(),
            profilePhotoUrl =null,
            phone = userNumber,
            accountStatus = "pending",
            gender = selectedGender.toString(),
            profileCompletion = 0

        )

        lifecycleScope.launch {
            val isSucess=repository.saveAdvisor(advisor)

            if (isSucess){

                DialogUtils.hideLoadingDialog()
                DialogUtils.showStatusDialog(this@AdvisorDetailsActivity,true,title="Success", message = "Sign In Successful!!"){
                    val intent = Intent(this@AdvisorDetailsActivity, AdvisorHomeActivity::class.java)
                    startActivity(intent)
                }

                Toast.makeText(this@AdvisorDetailsActivity, "saved successful", Toast.LENGTH_SHORT).show()


            }
            else {
                // show dialog when error
                DialogUtils.hideLoadingDialog()
                DialogUtils.showStatusDialog(this@AdvisorDetailsActivity, true, title="Error", message = "Error to Sign in...")
                Toast.makeText(this@AdvisorDetailsActivity, "error to save user input", Toast.LENGTH_SHORT).show()
            }
        }
    }
}





