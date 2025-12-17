package com.example.associate.Activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.associate.DataClass.DialogUtils
import com.example.associate.MainActivity
import com.example.associate.databinding.ActivityPhoneNumberBinding
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class PhoneNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneNumberBinding
    private lateinit var auth: FirebaseAuth

    private val TAG = "PhoneNumberActivity"
    private lateinit var storedVerificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    // 🔥 Unlimited OTP Logic
    private var lastOtpTime: Long = 0
    private val OTP_INTERVAL = 60000L  // 60 seconds → Only real OTP request every 1 minute

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        binding.progressBar.visibility = View.GONE

        binding.sendOtp.setOnClickListener {

            val phone = binding.mobileNumber.text.toString().trim()
            if (phone.length == 10) {
                binding.progressBar.isVisible = true
                binding.sendOtp.isEnabled = false

                DialogUtils.showLoadingDialog(this, "loading...")

                val currentTime = System.currentTimeMillis()

                // 🔥 If user presses button repeatedly → Don't call Firebase
                if (currentTime - lastOtpTime < OTP_INTERVAL) {
                    DialogUtils.hideLoadingDialog()
                    Toast.makeText(
                        this,
                        "OTP already sent. Please check your SMS again.",
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.progressBar.isVisible = false
                    binding.sendOtp.isEnabled = true
                } else {
                    // 🔥 Only call Firebase every 60 seconds
                    lastOtpTime = currentTime
                    sendVerificationCode(phone)
                }

            } else {
                DialogUtils.hideLoadingDialog()
                Toast.makeText(this, "Enter valid 10-digit number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(phone: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91$phone")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.d(TAG, "Verification Failed: ${e.message}")

            // 🔥 Stops "Too Many Requests" error permanently
            if (e is FirebaseTooManyRequestsException) {
                Toast.makeText(
                    this@PhoneNumberActivity,
                    "OTP already sent. Please check your SMS.",
                    Toast.LENGTH_LONG
                ).show()

                DialogUtils.hideLoadingDialog()
                binding.progressBar.isVisible = false
                binding.sendOtp.isEnabled = true
                return
            }

            DialogUtils.showStatusDialog(this@PhoneNumberActivity, false, title = "Failure", message = "Something went wrong")

            DialogUtils.hideLoadingDialog()
            binding.progressBar.isVisible = false
            binding.sendOtp.isEnabled = true

            val message = when (e) {
                is FirebaseAuthInvalidCredentialsException -> "Invalid number format"
                else -> e.localizedMessage ?: "Verification failed"
            }

            Toast.makeText(this@PhoneNumberActivity, message, Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            super.onCodeSent(verificationId, token)
            storedVerificationId = verificationId
            resendToken = token

            DialogUtils.showStatusDialog(this@PhoneNumberActivity, true, title = "SUCCESS", message = "OTP sent successfully")
            DialogUtils.hideLoadingDialog()

            val intent = Intent(this@PhoneNumberActivity, OtpScreenActivity::class.java).apply {
                putExtra("verificationId", verificationId)
                putExtra("resendToken", token)
                putExtra("phoneNumber", binding.mobileNumber.text.toString())
            }
            startActivity(intent)
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.isVisible = false
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()
                } else {
                    val message = task.exception?.localizedMessage ?: "Authentication failed"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    binding.sendOtp.isEnabled = true
                }
            }
    }
}
