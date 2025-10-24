package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
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
import com.example.associate.DataClass.DialogUtils
import com.example.associate.MainActivity
import com.example.associate.R
import com.example.associate.databinding.ActivityOtpScreenBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class OtpScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityOtpScreenBinding.inflate(layoutInflater)
    }

    // Companion object should only contain constants, not lateinit variables
    companion object {
        private const val TAG = "OtpScreenActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var verificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var phoneNumber: String

    // OTP field
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
        verificationId = intent.getStringExtra("verificationId") ?: ""
        phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
        resendToken = intent.getParcelableExtra("resendToken")!!

        // Validate required data
        if (verificationId.isEmpty() || phoneNumber.isEmpty()) {
            Toast.makeText(this, "Invalid verification data", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // Display phone number
        binding.enteredMobileNumber.text = "Verify $phoneNumber"

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
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.resendOtpBtn.setOnClickListener {
            DialogUtils.showLoadingDialog(this, "Resending OTP...")
            resendVerificationCode()
        }

        binding.verifyOtpBtn.setOnClickListener {
            verifyOtp()
        }

    }

    private fun resendVerificationCode() {
        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber) // Use the format that was originally used
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .setForceResendingToken(resendToken)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: IllegalArgumentException) {
            DialogUtils.hideLoadingDialog()
            Log.e(TAG, "IllegalArgumentException: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            DialogUtils.hideLoadingDialog()
            Log.e(TAG, "Exception during resend: ${e.message}", e)
            Toast.makeText(this, "Failed to resend OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification (without SMS) - This can happen in some cases
            Log.d(TAG, "onVerificationCompleted: Auto verification successful")
            DialogUtils.showLoadingDialog(this@OtpScreenActivity, "Verifying...")
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e(TAG, "Verification failed", e)
            runOnUiThread {
                DialogUtils.hideLoadingDialog()

                val errorMessage = when {
                    e is FirebaseAuthInvalidCredentialsException -> "Invalid phone number format"
                    e.message?.contains("QUOTA_EXCEEDED", ignoreCase = true) == true -> "SMS quota exceeded. Please try again later."
                    e.message?.contains("INVALID_SESSION_INFO", ignoreCase = true) == true -> "Invalid session. Please request a new OTP."
                    else -> "Verification failed: ${e.localizedMessage ?: "Unknown error"}"
                }

                DialogUtils.showStatusDialog(
                    this@OtpScreenActivity,
                    false,
                    title = "Verification Failed",
                    message = errorMessage
                )
            }
        }

        override fun onCodeSent(
            newVerificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(TAG, "onCodeSent: New OTP sent successfully")
            runOnUiThread {
                DialogUtils.hideLoadingDialog()
                verificationId = newVerificationId
                resendToken = token

                DialogUtils.showStatusDialog(
                    this@OtpScreenActivity,
                    true,
                    title = "OTP Sent",
                    message = "New OTP has been sent successfully"
                ) {
                    // Clear OTP fields after resend
                    clearOtpFields()
                    binding.otp1.requestFocus()
                }
            }
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
            Log.d(TAG, "onCodeAutoRetrievalTimeOut: Auto retrieval timeout")
            runOnUiThread {
                Toast.makeText(this@OtpScreenActivity, "Auto retrieval timeout", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOtpFields() {
        for (i in otpFields.indices) {
            val currentField = otpFields[i]

            currentField.addTextChangedListener(OtpTextWatcher(i))
            currentField.setOnKeyListener(OtpKeyListener(i))

            // Handle keyboard done action on last field
            if (i == otpFields.size - 1) {
                currentField.imeOptions = EditorInfo.IME_ACTION_DONE
                currentField.setOnEditorActionListener { _, actionId, _ ->
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
        val otp = otpFields.joinToString("") { it.text.toString().trim() }

        if (otp.length == 6) {
            DialogUtils.showLoadingDialog(this, "Verifying OTP...")
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                signInWithPhoneAuthCredential(credential)
            } catch (e: IllegalArgumentException) {
                DialogUtils.hideLoadingDialog()
                Log.e(TAG, "Invalid verification ID or OTP", e)
                DialogUtils.showStatusDialog(
                    this,
                    false,
                    title = "Error",
                    message = "Invalid verification session. Please request a new OTP."
                )
                clearOtpFields()
            }
        } else {
            DialogUtils.showStatusDialog(
                this,
                false,
                title = "Incomplete OTP",
                message = "Please enter all 6 digits"
            )
            // Highlight empty fields
            highlightEmptyFields()
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    // Check if user exists in Firestore
                    checkUserExists { exists ->
                        DialogUtils.hideLoadingDialog()
                        if (exists) {
                            // User exists - go to MainActivity
                            handleExistingUser()
                        } else {
                            // New user - go to PersonalScreenActivity or wherever new users should go
                            handleNewUser()
                        }
                    }
                } else {
                    DialogUtils.hideLoadingDialog()
                    Log.w(TAG, "signInWithCredential:failure", task.exception)

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Invalid OTP. Please check and try again."
                        else -> "Authentication failed: ${task.exception?.message ?: "Unknown error"}"
                    }

                    DialogUtils.showStatusDialog(
                        this,
                        false,
                        title = "Verification Failed",
                        message = errorMessage
                    ) {
                        // Clear all fields on failure
                        clearOtpFields()
                        otpFields[0].requestFocus()
                    }
                }
            }
    }

    private fun checkUserExists(callback: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    callback(document.exists())
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error checking user existence", exception)
                    // If we can't check Firestore, assume user doesn't exist to be safe
                    callback(false)
                }
        } else {
            callback(false)
        }
    }

    private fun handleExistingUser() {
        DialogUtils.showStatusDialog(
            this,
            true,
            title = "Success",
            message = "Login successful"
        ) {
            navigateToMainActivity()
        }
    }

    private fun handleNewUser() {
        DialogUtils.showStatusDialog(
            this,
            true,
            title = "Welcome",
            message = "Please complete your profile"
        ) {
            navigateToProfileSetup()
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun navigateToProfileSetup() {
        // TODO: Replace with your actual profile setup activity
        val intent = Intent(this, PersonalScreenActivity::class.java)
        intent.putExtra("phoneNumber", phoneNumber)
        startActivity(intent)
        finish()
    }

    private fun clearOtpFields() {
        otpFields.forEach { it.text.clear() }
    }

    private fun highlightEmptyFields() {
        for (i in otpFields.indices) {
            val field = otpFields[i]
            if (field.text.isEmpty()) {
                field.setBackgroundResource(R.drawable.otp_field_background) // Create this drawable
            } else {
                field.setBackgroundResource(R.drawable.otp_field_background) // Normal background
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
                    // Last field filled - auto verify after a short delay
                    binding.root.postDelayed({
                        verifyOtp()
                    }, 300)
                }
            } else if (s?.isEmpty() == true && currentIndex > 0) {
                // Move focus to previous field on backspace
                otpFields[currentIndex - 1].requestFocus()
            }

            // Update verify button state
            updateVerifyButtonState()
        }
    }

    inner class OtpKeyListener(private val currentIndex: Int) : View.OnKeyListener {
        override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_DEL && event?.action == KeyEvent.ACTION_DOWN) {
                if (otpFields[currentIndex].text.isEmpty() && currentIndex > 0) {
                    // Move focus to previous field on backspace when current field is empty
                    otpFields[currentIndex - 1].requestFocus()
                    otpFields[currentIndex - 1].text.clear()
                    return true
                }
            }
            return false
        }
    }

    private fun updateVerifyButtonState() {
        val otp = otpFields.joinToString("") { it.text.toString().trim() }
        binding.verifyOtpBtn.isEnabled = otp.length == 6
    }

//    override fun onBackPressed() {
//        // Show confirmation dialog before going back
//        DialogUtils.showConfirmationDialog(
//            this,
//            title = "Cancel Verification?",
//            message = "Are you sure you want to cancel OTP verification?",
//            positiveText = "Yes",
//            negativeText = "No"
//        ) {
//            super.onBackPressed()
//        }
//    }

    override fun onDestroy() {
        super.onDestroy()
        DialogUtils.hideLoadingDialog()
    }
}