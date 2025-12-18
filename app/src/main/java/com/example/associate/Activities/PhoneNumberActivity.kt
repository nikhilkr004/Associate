package com.example.associate.Activities

import android.app.Activity.RESULT_OK
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
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.associate.Repositories.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhoneNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneNumberBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val userRepository = UserRepository()

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

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.example.associate.R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.googleSignInBtn.setOnClickListener {
            signInWithGoogle()
        }

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
            Log.e(TAG, "Verification Failed", e)

            DialogUtils.hideLoadingDialog()
            binding.progressBar.isVisible = false
            binding.sendOtp.isEnabled = true

            // 🔥 Stops "Too Many Requests" error permanently
            if (e is FirebaseTooManyRequestsException) {
                Toast.makeText(
                    this@PhoneNumberActivity,
                    "Quota exceeded or too many requests. Please try again later.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val errorMessage = when (e) {
                is FirebaseAuthInvalidCredentialsException -> "Invalid phone number format. Please check the number."
                is FirebaseAuthMissingActivityForRecaptchaException -> "reCAPTCHA verification failed. Please try again."
                else -> e.localizedMessage ?: "Verification failed. Please check your internet connection."
            }

            DialogUtils.showStatusDialog(
                this@PhoneNumberActivity,
                false,
                title = "Verification Failed",
                message = errorMessage + "\n\n(Tip: Check SHA-1/SHA-256 in Firebase Console)"
            )

            Toast.makeText(this@PhoneNumberActivity, errorMessage, Toast.LENGTH_LONG).show()
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

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        DialogUtils.showLoadingDialog(this, "Signing in with Google...")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    checkUserInFirestore(user)
                } else {
                    DialogUtils.hideLoadingDialog()
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserInFirestore(firebaseUser: FirebaseUser?) {
        if (firebaseUser == null) return

        CoroutineScope(Dispatchers.IO).launch {
            val result = userRepository.getUserById(firebaseUser.uid)
            withContext(Dispatchers.Main) {
                DialogUtils.hideLoadingDialog()
                when (result) {
                    is UserRepository.Result.Success -> {
                        // User exists -> Go to MainActivity
                        startActivity(Intent(this@PhoneNumberActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                    is UserRepository.Result.Failure -> {
                        // User does NOT exist -> Go to PersonalScreenActivity
                        val intent = Intent(this@PhoneNumberActivity, PersonalScreenActivity::class.java).apply {
                            putExtra("user_email", firebaseUser.email)
                            putExtra("user_name", firebaseUser.displayName)
                            putExtra("user_photo", firebaseUser.photoUrl?.toString())
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
