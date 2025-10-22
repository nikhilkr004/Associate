package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Log.e
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
                ///loading screen
                DialogUtils.showLoadingDialog(this,"loading...")

                sendVerificationCode(phone)

            } else {
                DialogUtils.hideLoadingDialog()
                Toast.makeText(this, "Enter valid 10-digit number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(phone: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91$phone")       // Your phone number
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(this)                 // Activity for callback binding
            .setCallbacks(callbacks)           // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto OTP or instant verification
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.d(TAG, "Verification Failed: ${e.message}")

            DialogUtils.showStatusDialog(this@PhoneNumberActivity,false,title="Failure", message = "Some went wrong")
            // after error hide loading screen
            DialogUtils.hideLoadingDialog()
            binding.progressBar.isVisible = false
            binding.sendOtp.isEnabled = true

            val message = when (e) {
                is FirebaseAuthInvalidCredentialsException -> "Invalid number format"
                is FirebaseTooManyRequestsException -> "Too many requests. Try again later."
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

            DialogUtils.showStatusDialog(this@PhoneNumberActivity,true,title="SUCCESS", message = "Otp has been successfully send")
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
