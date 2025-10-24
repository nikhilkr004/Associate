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
import com.example.associate.Repositorys.UserRepository

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userPhoneNumber = intent.getStringExtra("user_number") ?: "7292921732"
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
        val userId = auth.currentUser?.uid ?: ""
        val user = UserData(
            name = binding.userName.text.toString().trim(),
            email = binding.userEmail.text.toString().trim(),
            phone = userPhoneNumber,
            city = binding.userCity.text.toString().trim(),
            jointAt = System.currentTimeMillis().toString(),
            userId = userId,
            fcmToken = "" // Will be updated after getting FCM token
        )

        binding.enterBtn.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val result = userRepository.addUser(user)) {
                    is UserRepository.Result.Success<*> -> {
                        // Get FCM token and update user document
                        val fcmToken = getFCMToken()
                        fcmToken?.let { token ->
                            when (val tokenResult = userRepository.saveFCMToken(userId, token)) {
                                is UserRepository.Result.Success -> {
                                    Log.d("FCM", "FCM token saved successfully")
                                }
                                is UserRepository.Result.Failure -> {
                                    Log.e("FCM", "Failed to save FCM token", tokenResult.exception)
                                }
                            }
                        }

                        // Send welcome notification to all existing users
                        sendWelcomeNotification(user.name,userId)

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

                    is UserRepository.Result.Failure<*> -> {
                        withContext(Dispatchers.Main) {
                            handleError(result.exception)
                        }
                    }

                    is UserRepository.AddUserResult.Failure -> TODO()
                    is UserRepository.AddUserResult.Success -> TODO()
                }
            } catch (e: Exception) {
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
            Log.e("FCM", "Error getting FCM token", e)
            null
        }
    }

    private suspend fun sendWelcomeNotification(newUserName: String, currentUserId: String) {
        try {
            // Get all existing user tokens (excluding the current user)
            when (val result = userRepository.getAllUsers()) {
                is UserRepository.Result.Success -> {
                    val allUsers = result.data
                    // Filter out current user and get tokens of other users only
                    val otherUsersTokens = allUsers
                        .filter { it.userId != currentUserId && it.fcmToken.isNotBlank() }
                        .map { it.fcmToken }

                    if (otherUsersTokens.isNotEmpty()) {
                        Log.d("Notification", "Sending welcome notification to ${otherUsersTokens.size} users")

                        // Simulate notification sending
                        simulateNotificationSend(otherUsersTokens, newUserName)

                        // In production: Call your server API here
                        // sendActualNotification(otherUsersTokens, newUserName)
                    } else {
                        Log.d("Notification", "No other users to send notification to")
                    }
                }
                is UserRepository.Result.Failure -> {
                    Log.e("Notification", "Failed to get users", result.exception)
                }
            }
        } catch (e: Exception) {
            Log.e("Notification", "Error sending welcome notification", e)
        }
    }

    private fun simulateNotificationSend(tokens: List<String>, newUserName: String) {
        // This simulates what would happen on your server
        // In production, you'd make an API call to your server
        Log.i("Notification", "Simulating notification send:")
        Log.i("Notification", "Recipients: ${tokens.size} users")
        Log.i("Notification", "Title: New User Joined")
        Log.i("Notification", "Message: $newUserName has joined the app!")
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