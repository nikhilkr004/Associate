package com.example.associate.Activitys

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.R
import com.example.associate.databinding.ActivityUserAdvisorOnboardingBinding
import org.jetbrains.annotations.ApiStatus

class UserAdvisorOnboardingActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityUserAdvisorOnboardingBinding.inflate(layoutInflater)
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

        val userNumber =intent.getStringExtra("user_number")
        binding.joinAsUser.setOnClickListener {
           val intent= Intent(this, PersonalScreenActivity::class.java)
            intent.putExtra("user_number",userNumber)
            startActivity(intent)
        }


        binding.joinAsAdvisor.setOnClickListener {
            val intent= Intent(this, AdvisorDetailsActivity::class.java)
            intent.putExtra("user_number",userNumber)
            startActivity(intent)
        }
    }
}