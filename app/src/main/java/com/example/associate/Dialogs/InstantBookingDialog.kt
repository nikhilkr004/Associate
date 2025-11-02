package com.example.associate.Dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.DialogUtils
import com.example.associate.Managers.SessionBookingManager
import com.example.associate.databinding.DialogInstantBookingBinding

class InstantBookingDialog(
    private val advisor: AdvisorDataClass,
    private val onBookingSuccess: () -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogInstantBookingBinding
    private lateinit var bookingManager: SessionBookingManager

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        bookingManager = SessionBookingManager(requireContext())
        binding = DialogInstantBookingBinding.inflate(layoutInflater)

        setupUI()
        setupClickListeners()

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setTitle("Instant Session with ${advisor.name}")
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun setupUI() {
        setupAdvisorInfo()
        setupSpinners()
    }

    private fun setupAdvisorInfo() {
//        binding.tvAdvisorName.text = advisor.name
//        binding.tvAdvisorSpecialization.text = advisor.getSpecializationsString()
//        binding.tvAdvisorExperience.text = "${advisor.experience} years experience"
        binding.tvSessionInfo.text = "Advisor will call you within 5 minutes\nAmount: ₹100 (deducted only when advisor calls)"
    }

    private fun setupSpinners() {
        // Purpose Spinner
        binding.spinnerPurpose.adapter = createAdapter(DialogUtils.getBookingPurposes())

        // Language Spinner
        binding.spinnerLanguage.adapter = createAdapter(DialogUtils.getPreferredLanguages())

        // Urgency Spinner
        binding.spinnerUrgency.adapter = createAdapter(DialogUtils.getUrgencyLevels())
    }

    private fun createAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun setupClickListeners() {
        binding.btnBookInstant.setOnClickListener {
            bookInstantSession()
        }
    }

    private fun bookInstantSession() {
        val purpose = binding.spinnerPurpose.selectedItem.toString()
        val language = binding.spinnerLanguage.selectedItem.toString()
        val urgency = binding.spinnerUrgency.selectedItem.toString()
        val notes = binding.etAdditionalNotes.text.toString().trim()

        if (!validateInputs(purpose, language)) return

        startBookingProcess(purpose, language, urgency, notes)
    }

    private fun validateInputs(purpose: String, language: String): Boolean {
        if (!DialogUtils.isValidPurpose(purpose)) {
            showToast("Please select purpose")
            return false
        }

        if (!DialogUtils.isValidLanguage(language)) {
            showToast("Please select language")
            return false
        }

        return true
    }

    private fun startBookingProcess(purpose: String, language: String, urgency: String, notes: String) {
        setLoading(true)

        bookingManager.createInstantBooking(
            advisorId = advisor.id,
            advisorName = advisor.name,
            purpose = purpose,
            preferredLanguage = language,
            additionalNotes = notes,
            urgencyLevel = urgency,
            onSuccess = { message ->
                setLoading(false)
                showToast(message)
                onBookingSuccess()
                dismiss()
            },
            onFailure = { error ->
                setLoading(false)
                showToast(error)
            }
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.btnBookInstant.isEnabled = !loading
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}