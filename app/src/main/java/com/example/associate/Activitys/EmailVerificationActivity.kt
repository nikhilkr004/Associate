package com.example.associate.Activitys

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.R
import com.example.associate.databinding.ActivityEmailVerificationBinding
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent

import android.view.View
import android.widget.Toast
import com.example.associate.MainActivity
import com.google.firebase.auth.FirebaseUser

class EmailVerificationActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityEmailVerificationBinding.inflate(layoutInflater)
    }
    private var emailVerified = false
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        auth = FirebaseAuth.getInstance()

        setupClickListeners()
        displayUserEmail()
        checkCurrentUser()
        startEmailCheckTimer()
    }

    private fun displayUserEmail() {
        val user = auth.currentUser
        user?.email?.let { email ->
            binding.tvEmailAddress.text = email
        } ?: run {
//            // If no user found, go back to login
//            Toast.makeText(this, "No user found. Please sign in again.", Toast.LENGTH_LONG).show()
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
        }
    }

    private fun setupClickListeners() {
        binding.btnVerify.setOnClickListener {
            checkEmailVerification()
        }

        binding.btnResend.setOnClickListener {
            resendVerificationEmail()
        }

        binding.btnLogout.setOnClickListener {
            logoutUser()
        }
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (currentUser.isEmailVerified) {
                // User already verified
                emailVerified = true
                proceedToNextStep()
            } else {
                // User not verified, show verification UI
                showVerificationUI(currentUser)
            }
        } else {
//            // No user logged in, redirect to login
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
        }
    }

    private fun showVerificationUI(user: FirebaseUser) {
        binding.tvEmailAddress.text = user.email
        binding.tvInstruction.text = "We've sent a verification link to your email address. Please click the link to verify your account."
        binding.btnVerify.visibility = View.VISIBLE
        binding.btnResend.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.tvTimerInfo.visibility = View.VISIBLE
    }

    private fun checkEmailVerification() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnVerify.isEnabled = false

        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { task ->
            binding.progressBar.visibility = View.GONE
            binding.btnVerify.isEnabled = true

            if (task.isSuccessful) {
                if (user.isEmailVerified) {
                    emailVerified = true
                    showToast("Email verified successfully! 🎉")
                    proceedToNextStep()
                } else {
                    showToast("Email not verified yet. Please check your inbox and click the verification link.")
                    updateTimerInfo()
                }
            } else {
                showToast("Failed to check verification status: ${task.exception?.message}")
            }
        }
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        if (user != null) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnResend.isEnabled = false

            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnResend.isEnabled = true

                    if (task.isSuccessful) {
                        showToast("Verification email sent successfully! 📧")
                        binding.tvInstruction.text = "New verification link sent! Please check your email and click the link to verify."
                        startEmailCheckTimer()
                    } else {
                        showToast("Failed to send verification email: ${task.exception?.message}")
                    }
                }
        } else {
            showToast("No user found. Please sign in again.")
        }
    }

    private fun startEmailCheckTimer() {
        // Optional: You can implement a timer that automatically checks verification status
        // This is useful if you want to check automatically without user clicking the button
        binding.tvTimerInfo.text = "Checking verification status automatically..."

        val handler = android.os.Handler()
        handler.postDelayed({
            if (!emailVerified) {
                checkEmailVerification()
            }
        }, 30000) // Check every 30 seconds
    }

    private fun updateTimerInfo() {
        binding.tvTimerInfo.text = "Still waiting for verification? Click 'I've Verified My Email' to check status."
    }

    private fun proceedToNextStep() {
        // Save verification status in SharedPreferences
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("is_email_verified", true)
            putString("user_email", auth.currentUser?.email)
            apply()
        }

        // Navigate to main activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun logoutUser() {
        auth.signOut()

        // Clear shared preferences
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }

//        startActivity(Intent(this, LoginActivity::class.java))
//        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        // Prevent going back to login without verification
        if (emailVerified) {
            super.onBackPressed()
        } else {
            // Show message that verification is required
            showToast("Please verify your email to continue")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any handlers or listeners if needed
    }
}