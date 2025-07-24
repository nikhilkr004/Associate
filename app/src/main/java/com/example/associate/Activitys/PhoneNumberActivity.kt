package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.associate.MainActivity
import com.example.associate.R
import com.example.associate.databinding.ActivityPhoneNumberBinding
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class PhoneNumberActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityPhoneNumberBinding.inflate(layoutInflater)
    }

    private lateinit var auth: FirebaseAuth
    private val TAG = "PhoneNumberActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        binding.progressBar.visibility=View.GONE
        binding.sendOtp.setOnClickListener {
            val phoneNumber = binding.mobileNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                if (phoneNumber.length >= 10) {  // Basic phone number validation
                    binding.progressBar.isVisible = true
                    binding.sendOtp.isEnabled = false
                    sendVerificationCode(phoneNumber)
                } else {
                    Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+91$phoneNumber")
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: IllegalArgumentException) {
            // Handle the error gracefully
            Toast.makeText(this, "Error starting verification: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.progressBar.isVisible = false
            binding.sendOtp.isEnabled = true
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification (without SMS)
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "Verification failed", e)
            runOnUiThread {
                binding.progressBar.isVisible = false
                binding.sendOtp.isEnabled = true

                val errorMessage = when {
                    e is FirebaseAuthInvalidCredentialsException -> "Invalid phone number format"
                    e.message?.contains("RECAPTCHA") == true -> {
                        // This should now be avoided with in-app verification
                        "Verification failed. Please try again"
                    }
                    else -> "Error: ${e.localizedMessage}"
                }
                Toast.makeText(this@PhoneNumberActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            // OTP sent successfully - proceed to verification screen
            val intent = Intent(this@PhoneNumberActivity, OtpScreenActivity::class.java).apply {
                putExtra("verificationId", verificationId)
                putExtra("resendToken",token)
                putExtra("phoneNumber", binding.mobileNumber.text.toString())
            }
            startActivity(intent)
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()  // Clear back stack
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.progressBar.isVisible = false
                    binding.sendOtp.isEnabled = true
                }
            }
    }
}