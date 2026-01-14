package com.example.associate.Activities

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
import com.example.associate.Repositories.UserRepository
import com.example.associate.databinding.ActivityPersonalScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PersonalScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityPersonalScreenBinding.inflate(layoutInflater)
    }

    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var userPhoneNumber: String
    private var selectedGender: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Receive data
        val intentNumber = intent.getStringExtra("user_number") ?: ""
        val intentEmail = intent.getStringExtra("user_email") ?: ""
        val intentName = intent.getStringExtra("user_name") ?: ""

        // Pre-fill data
        binding.userEmail.setText(intentEmail)
        binding.userName.setText(intentName)
        
        if (intentNumber.isNotEmpty()) {
            binding.userPhoneInput.setText(intentNumber)
            binding.userPhoneInput.isEnabled = false
            userPhoneNumber = intentNumber
        } else {
            binding.userPhoneInput.isEnabled = true
        }

        // Gender Click Listeners
        binding.genderMale.setOnClickListener { selectGender("Male") }
        binding.genderFemale.setOnClickListener { selectGender("Female") }

        // Save Button
        binding.enterBtn.setOnClickListener {
            if (validateForm()) {
                saveUserData()
            }
        }
    }

    private fun selectGender(gender: String) {
        selectedGender = gender

        if (gender == "Male") {
            binding.genderMale.setBackgroundResource(R.drawable.male_female_selected_bg)
            binding.genderFemale.setBackgroundResource(R.drawable.male_female_bg)
            binding.genderMale.setTextColor(getColor(R.color.white))
            binding.genderFemale.setTextColor(getColor(R.color.green))
        } else {
            binding.genderFemale.setBackgroundResource(R.drawable.male_female_selected_bg)
            binding.genderMale.setBackgroundResource(R.drawable.male_female_bg)
            binding.genderFemale.setTextColor(getColor(R.color.white))
            binding.genderMale.setTextColor(getColor(R.color.green))
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

            val phoneInput = userPhoneInput.text.toString().trim()
            if (phoneInput.length != 10) {
                userPhoneInput.error = "Valid 10-digit number required"
                return false
            }

            if (selectedGender.isEmpty()) {
                Toast.makeText(this@PersonalScreenActivity, "Please select your gender", Toast.LENGTH_SHORT).show()
                return false
            }

            return true
        }
    }

    // 🔥 FIXED: saveUserData function
    private fun saveUserData() {
        val userId = auth.currentUser?.uid ?: ""

        val user = UserData(
            name = binding.userName.text.toString().trim(),
            email = binding.userEmail.text.toString().trim(),
            phone = binding.userPhoneInput.text.toString().trim(),
            city = binding.userCity.text.toString().trim(),
            jointAt = System.currentTimeMillis().toString(),
            userId = userId,
            gender = selectedGender,
            fcmToken = "",
            profilePhotoUrl = ""
        )

        binding.enterBtn.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 🔥 CORRECTED: Use createOrUpdateUser instead of addUser
                when (val result = userRepository.createOrUpdateUser(user)) {
                    is UserRepository.Result.Success<*> -> {
                        Log.d("PersonalScreen", "User created successfully")

                        // Fetch & Save FCM Token
                        val token = getFCMToken()
                        if (!token.isNullOrEmpty()) {
                            userRepository.saveFCMToken(userId, token)
                            Log.d("PersonalScreen", "FCM Token saved: $token")
                        }

                        // Send welcome notification
                        sendWelcomeNotification(user.name, userId)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@PersonalScreenActivity,
                                "Profile saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            startActivity(
                                Intent(this@PersonalScreenActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }
                    }

                    is UserRepository.Result.Failure<*> -> {
                        Log.e("PersonalScreen", "Error creating user: ${result.exception}")
                        withContext(Dispatchers.Main) {
                            handleError(result.exception)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("PersonalScreen", "Exception in saveUserData: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    handleError(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.enterBtn.isEnabled = true
                }
            }
        }
    }



    private suspend fun getFCMToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e("FCM", "Token error", e)
            null
        }
    }

    private suspend fun sendWelcomeNotification(newUserName: String, currentUserId: String) {
        try {
            when (val result = userRepository.getAllUsers()) {
                is UserRepository.Result.Success -> {
                    val otherUsers = result.data
                        .filter { it.userId != currentUserId && it.fcmToken.isNotBlank() }
                        .map { it.fcmToken }

                    if (otherUsers.isNotEmpty()) {
                        simulateNotification(otherUsers, newUserName)
                    }
                }
                is UserRepository.Result.Failure -> {
                    Log.e("Notification", "Failed to fetch users", result.exception)
                }
            }
        } catch (e: Exception) {
            Log.e("Notification", "Error", e)
        }
    }

    private fun simulateNotification(tokens: List<String>, username: String) {
        Log.i("Notification", "Sending welcome message to ${tokens.size} users")
        Log.i("Notification", "Message: $username joined the app!")
    }

    private fun handleError(exception: Exception) {
        Toast.makeText(
            this,
            "Error: ${exception.localizedMessage ?: "Something went wrong"}",
            Toast.LENGTH_LONG
        ).show()

        Log.e("PersonalScreen", "Error saving data", exception)
    }
}
// Updated for repository activity
