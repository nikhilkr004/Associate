package com.example.associate.HelpSupport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.DataClass.DialogUtils
import com.example.associate.databinding.ActivityHelpSupportBinding
import com.google.firebase.auth.FirebaseAuth

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpSupportBinding
    private lateinit var viewModel: HelpSupportViewModel
    private lateinit var photosAdapter: SelectedPhotosAdapter
    private val selectedUris = mutableListOf<Uri>()
//    private var progressDialog: Dialog? = null

    private val categories = listOf(
        "Payment Issue",
        "Call Connectivity",
        "App Crash / Bug",
        "Advisor Behavior",
        "Account Issue",
        "Suggestion / Feedback",
        "Other"
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (selectedUris.size + uris.size > 3) {
                Toast.makeText(this, "You can select up to 3 photos", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            selectedUris.addAll(uris)
            updatePhotosList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[HelpSupportViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Back Button
        binding.backBtn.setOnClickListener { finish() }

        // Dropdown
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryDropdown.setAdapter(adapter)

        // Photos RecyclerView
        photosAdapter = SelectedPhotosAdapter { uri ->
            selectedUris.remove(uri)
            updatePhotosList()
        }
        binding.rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPhotos.adapter = photosAdapter

        // Buttons
        binding.btnAddPhotos.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnRaiseTicket.setOnClickListener {
            submitTicket()
        }
        
        binding.btnViewMyTickets.setOnClickListener {
             startActivity(Intent(this, MyTicketsActivity::class.java))
        }
    }

    private fun updatePhotosList() {
        photosAdapter.setPhotos(selectedUris)
        binding.rvPhotos.visibility = if (selectedUris.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun submitTicket() {
        val category = binding.categoryDropdown.text.toString()
        val subject = binding.etSubject.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        if (category.isEmpty() || category == "Select Category") {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        if (subject.isEmpty()) {
            binding.etSubject.error = "Required"
            return
        }

        if (description.isEmpty()) {
            binding.etDescription.error = "Required"
            return
        }

        val ticket = TicketDataClass(
            userId = currentUser.uid,
            userEmail = currentUser.email ?: "",
            category = category,
            subject = subject,
            description = description
        )

        android.util.Log.d("HelpSupport", "Submitting ticket: $ticket")
        try {
            viewModel.submitTicket(ticket, selectedUris)
        } catch (e: Exception) {
            android.util.Log.e("HelpSupport", "Crash during submission call: ${e.message}", e)
            Toast.makeText(this, "Error initiating submission: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                DialogUtils.showLoadingDialog(this)
            } else {
                DialogUtils.hideLoadingDialog()
            }
        }

        viewModel.submitStatus.observe(this) { result ->
            result.onSuccess {
                DialogUtils.showStatusDialog(
                    context = this,
                    isSuccess = true,
                    title = "Ticket Success",
                    message = "Ticket Raised Successfully! We will contact you soon.",
                    action = {
                        // Navigate to My Tickets or Finish
                        startActivity(Intent(this, MyTicketsActivity::class.java))
                        finish()
                    }
                )

            }.onFailure { e ->

                DialogUtils.showStatusDialog(this,  false , " Ticket Fail" ,"something went wrong")
                Toast.makeText(this, "Failed to submit ticket: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
