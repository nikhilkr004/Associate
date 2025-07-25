package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.DataClass.UserData
import com.example.associate.MainActivity
import com.example.associate.R
import com.example.associate.Repositorys.Result
import com.example.associate.Repositorys.UserRepository
import com.example.associate.databinding.ActivityPersonalScreenBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonalScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityPersonalScreenBinding.inflate(layoutInflater)
    }
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var userPhoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userPhoneNumber = intent.getStringExtra("user_number") ?: ""
        binding.enterBtn.setOnClickListener {
            if (validateForm()) {
                saveUserData()
            }
        }
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
            return true
        }
    }

    private fun saveUserData() {
        val user = UserData(
            name = binding.userName.text.toString().trim(),
            email = binding.userEmail.text.toString().trim(),
            phone = userPhoneNumber,
            city = binding.userCity.text.toString().trim(),
            jointAt = System.currentTimeMillis().toString(),
            userId = auth.currentUser?.uid ?: ""
        )

//        binding.progressBar.isVisible = true
        binding.enterBtn.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val result = userRepository.addUser(user)) {
                    is UserRepository.Result.Success -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@PersonalScreenActivity,
                                "Profile saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Navigate to MainActivity and clear back stack
                            Intent(this@PersonalScreenActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(this)
                            }
                        }
                    }


                    is UserRepository.Result.Failure -> {
                        withContext(Dispatchers.Main) {
                            handleError(result.exception)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleError(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
//                    binding.progressBar.isVisible = false
                    binding.enterBtn.isEnabled = true
                }
            }
        }
    }

    private fun handleError(exception: Exception) {
        Toast.makeText(
            this,
            "Error: ${exception.localizedMessage ?: "Failed to save profile"}",
            Toast.LENGTH_LONG
        ).show()
        Log.e("PersonalScreen", "Error saving user data", exception)
    }
}