package com.example.associate.Activitys

import android.content.ContentValues.TAG
import android.os.Bundle
import android.content.Intent
import android.nfc.Tag

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.associate.Activitys.PhoneNumberActivity
import com.example.associate.MainActivity
import com.example.associate.R
import com.example.associate.databinding.ActivityOtpScreenBinding
import com.google.firebase.FirebaseException

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

    class OtpScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityOtpScreenBinding.inflate(layoutInflater)
    }
companion object{
    private lateinit var auth: FirebaseAuth
    private lateinit var OTP: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

}


    private val otpFields = mutableListOf<EditText>()

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

        // Get verification ID and phone number from intent
        OTP = intent.getStringExtra("verificationId") ?: ""
        phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
        resendToken= intent.getParcelableExtra("resendToken")!!
        // Display phone number
        binding.enteredMobileNumber.text = "Verify ${phoneNumber}"

        // Initialize OTP fields
        otpFields.apply {
            add(binding.otp1)
            add(binding.otp2)
            add(binding.otp3)
            add(binding.otp4)
            add(binding.otp5)
            add(binding.otp6)
        }

        setupOtpFields()

        binding.resendOtpBtn.setOnClickListener {
         resendVarificationCode()
        }

        binding.verifyOtpBtn.setOnClickListener {
            verifyOtp()
        }
    }


    private fun resendVarificationCode() {
        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+91$phoneNumber")
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .setForceResendingToken(resendToken)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: IllegalArgumentException) {
            // Handle the error gracefully
            Toast.makeText(this, "Error starting verification: ${e.message}", Toast.LENGTH_SHORT).show()

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

                val errorMessage = when {
                    e is FirebaseAuthInvalidCredentialsException -> "Invalid phone number format"
                    e.message?.contains("RECAPTCHA") == true -> {
                        // This should now be avoided with in-app verification
                        "Verification failed. Please try again"
                    }
                    else -> "Error: ${e.localizedMessage}"
                }
                Toast.makeText(this@OtpScreenActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            // OTP sent successfully - proceed to verification screevar
           OTP =verificationId
            resendToken=token
        }
    }


    private fun setupOtpFields() {
        for (i in 0 until otpFields.size) {
            otpFields[i].addTextChangedListener(OtpTextWatcher(i))
            otpFields[i].setOnKeyListener(OtpKeyListener(i))

            // Handle keyboard done action on last field
            if (i == otpFields.size - 1) {
                otpFields[i].imeOptions = EditorInfo.IME_ACTION_DONE
                otpFields[i].setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        verifyOtp()
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    
    private fun verifyOtp() {
        val otp = otpFields.joinToString("") { it.text.toString() }

        if (otp.length == 6) {
//            binding.progressBar.visibility = View.VISIBLE
            val credential = PhoneAuthProvider.getCredential(OTP, otp)
            signInWithPhoneAuthCredential(credential)
        } else {
            Toast.makeText(this, "Please enter complete OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
//                binding.progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    // Verification successful
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    // Verification failed
                    Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                    // Clear all fields on failure
                    otpFields.forEach { it.text.clear() }
                    otpFields[0].requestFocus()
                }
            }
    }

    inner class OtpTextWatcher(private val currentIndex: Int) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (s?.length == 1) {
                // Move focus to next field
                if (currentIndex < otpFields.size - 1) {
                    otpFields[currentIndex + 1].requestFocus()
                } else {
                    // Last field - verify OTP automatically
                    verifyOtp()
                }
            } else if (s?.isEmpty() == true && currentIndex > 0) {
                // Move focus to previous field on backspace
                otpFields[currentIndex - 1].requestFocus()
            }
        }
    }

    inner class OtpKeyListener(private val currentIndex: Int) : View.OnKeyListener {
        override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_DEL && event?.action == KeyEvent.ACTION_DOWN) {
                if (otpFields[currentIndex].text.isEmpty() && currentIndex > 0) {
                    // Move focus to previous field on backspace
                    otpFields[currentIndex - 1].requestFocus()
                    return true
                }
            }
            return false
        }
    }
}

