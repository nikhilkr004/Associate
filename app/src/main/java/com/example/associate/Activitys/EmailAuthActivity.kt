package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.R
import com.example.associate.databinding.ActivityEmailAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class EmailAuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private val binding by lazy {
        ActivityEmailAuthBinding.inflate(layoutInflater)
    }

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
        setupTextWatchers()
    }

    private fun setupClickListeners() {
        binding.btnSignUp.setOnClickListener {
            signUpUser()
        }

//        binding.tvLogin.setOnClickListener {
//            // Navigate to Login Activity
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
//        }
    }

    private fun setupTextWatchers() {
        // Real-time validation
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmail(s.toString())
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validatePassword(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateConfirmPassword()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun signUpUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (validateAllFields(email, password, confirmPassword)) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSignUp.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnSignUp.isEnabled = true

                    if (task.isSuccessful) {
                        // Send verification email
                        sendVerificationEmail()
                    } else {
                        val errorMessage = when {
                            task.exception is FirebaseAuthUserCollisionException ->
                                "This email is already registered. Please login."
                            task.exception is FirebaseAuthWeakPasswordException ->
                                "Password is too weak. Please choose a stronger password."
                            task.exception is FirebaseAuthInvalidCredentialsException ->
                                "Invalid email format. Please check your email."
                            else -> "Sign up failed: ${task.exception?.message}"
                        }
                        showToast(errorMessage)
                    }
                }
        }
    }

    private fun sendVerificationEmail() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showSuccessDialog(user.email ?: "")
                } else {
                    showToast("Failed to send verification email: ${task.exception?.message}")
                }
            }
    }

    private fun showSuccessDialog(email: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Verification Email Sent")
            .setMessage("We've sent a verification link to:\n\n$email\n\nPlease check your inbox and verify your email to continue.")
            .setPositiveButton("Go to Verification") { dialog, which ->
                val intent = Intent(this, EmailVerificationActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    // Validation Methods
    private fun validateAllFields(email: String, password: String, confirmPassword: String): Boolean {
        return validateEmail(email) &&
                validatePassword(password) &&
                validateConfirmPassword() &&
                validatePasswordMatch(password, confirmPassword)
    }

    private fun validateEmail(email: String): Boolean {
        return if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            false
        } else {
            binding.etEmail.error = null
            true
        }
    }

    private fun validatePassword(password: String): Boolean {
        return if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            false
        } else {
            binding.etPassword.error = null
            true
        }
    }

    private fun validateConfirmPassword(): Boolean {
        val confirmPassword = binding.etConfirmPassword.text.toString()
        return if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm your password"
            false
        } else {
            binding.etConfirmPassword.error = null
            true
        }
    }

    private fun validatePasswordMatch(password: String, confirmPassword: String): Boolean {
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}