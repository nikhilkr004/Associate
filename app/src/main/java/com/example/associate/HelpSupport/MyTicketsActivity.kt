package com.example.associate.HelpSupport

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.DataClass.DialogUtils
import com.example.associate.databinding.ActivityMyTicketsBinding
import com.google.firebase.auth.FirebaseAuth

class MyTicketsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyTicketsBinding
    private lateinit var viewModel: HelpSupportViewModel
    private val ticketsAdapter = TicketsAdapter()
    private var progressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyTicketsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[HelpSupportViewModel::class.java]

        setupUI()
        observeViewModel()
        loadTickets()
    }

    private fun setupUI() {
        binding.backBtn.setOnClickListener { finish() }
        
        binding.rvTickets.layoutManager = LinearLayoutManager(this)
        binding.rvTickets.adapter = ticketsAdapter
    }
    
    private fun loadTickets() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            viewModel.getMyTickets(userId)
        } else {
             Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                 // Use simple progress bar or DialogUtils if pertinent, but usually for list loading we use ProgressBar in layout or just silent/small loader.
                 // For now reusing DialogUtils for consistency with other screens if it's not a blocking full screen
                 if (progressDialog == null && !isFinishing) {
                     DialogUtils.showLoadingDialog(this)
                 }
            } else {
                DialogUtils.hideLoadingDialog()
                progressDialog = null
            }
        }

        viewModel.tickets.observe(this) { result ->
            result.onSuccess { tickets ->
                ticketsAdapter.setTickets(tickets)
            }.onFailure { e ->
                Log.e("MyTickets", "Error loading tickets", e)
                Toast.makeText(this, "Failed to load tickets", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
