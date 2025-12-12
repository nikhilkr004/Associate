package com.example.associate

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.associate.Fragments.CallHistoryFragment
import com.example.associate.Fragments.HomeFragment
import com.example.associate.Fragments.ProfileFragment
import com.example.associate.Fragments.TransactionFragment
import com.example.associate.Repositorys.UserRepository
import com.example.associate.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
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

        setupBottomNavigation()

        // Load the default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment.newInstance())
                .commit()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment.newInstance())
                    true
                }

                R.id.transactionFragment -> {
                    loadFragment(TransactionFragment.newInstance())
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(ProfileFragment.newInstance())
                    true
                }
                R.id.callHistoryFragment -> {
                    loadFragment(CallHistoryFragment.newInstance())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onStart() {
        super.onStart()
        updateFCMToken()
    }

    private fun updateFCMToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d("MainActivity", "FCM Token: $token")

                // Save to Firestore
                val userRepository = UserRepository()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        userRepository.saveFCMToken(currentUser.uid, token)
                        withContext(Dispatchers.Main) {
//                            Toast.makeText(this@MainActivity, "Notification Service Connected ✅", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to save token", e)
                    }
                }
            }
        }
    }
}