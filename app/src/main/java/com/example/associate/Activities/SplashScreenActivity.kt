package com.example.associate.Activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.associate.MainActivity
import com.example.associate.R
import com.example.associate.Repositories.AppConfigRepository
import com.example.associate.Utils.AppConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Fetch remote config (API keys, app icon) from Firestore, then navigate
        lifecycleScope.launch {
            // Allow up to 5 seconds; fall back to hardcoded defaults on timeout/failure
            withTimeoutOrNull(5000L) {
                AppConfigRepository.fetchAndApply(this@SplashScreenActivity)
            } ?: Log.w("SplashScreen", "Config fetch timed out. Using defaults.")

            // If admin has set an app icon URL, load it into the splash logo ImageView
            val logoView = findViewById<ImageView>(R.id.imageView)
            if (logoView != null) {
                com.example.associate.Utils.AppIconLoader.loadIcon(this@SplashScreenActivity, logoView)
            }
            
            // Apply dynamic icon to "Recent Apps" OS overview
            com.example.associate.Utils.AppIconLoader.applyTaskDescriptionIcon(this@SplashScreenActivity)

            // Navigate to the appropriate screen
            val auth = FirebaseAuth.getInstance()
            val destination = if (auth.currentUser != null) {
                Intent(this@SplashScreenActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashScreenActivity, PhoneNumberActivity::class.java)
            }
            startActivity(destination)
            finish()
        }
    }
}
// Updated for repository activity
