package com.example.associate.Dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.DialogUtils

import com.example.associate.Repositories.SessionBookingManager
import com.example.associate.databinding.DialogInstantBookingBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class InstantBookingDialog(
    private val advisor: AdvisorDataClass,
    private val onBookingSuccess: () -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: DialogInstantBookingBinding
    private lateinit var bookingManager: SessionBookingManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogInstantBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bookingManager = SessionBookingManager(requireContext())
        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        setupAdvisorInfo()
        setupSpinners()
    }

    private fun setupAdvisorInfo() {
//        binding.tvAdvisorName.text = advisor.name
//        binding.tvAdvisorSpecialization.text = advisor.getSpecializationsString()
//        binding.tvAdvisorExperience.text = "${advisor.experience} years experience"
//        binding.tvSessionInfo.text = "Advisor will call you within 5 minutes"
    }

    private fun setupSpinners() {
        // Purpose Spinner
        binding.spinnerPurpose.adapter = createAdapter(DialogUtils.getBookingPurposes())

        // Language Spinner
        binding.spinnerLanguage.adapter = createAdapter(DialogUtils.getPreferredLanguages())

        // Language Spinner
        binding.spinnerLanguage.adapter = createAdapter(DialogUtils.getPreferredLanguages())

        setupWordCountWatcher()
    }

    private fun setupWordCountWatcher() {
        binding.etAdditionalNotes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val charCount = s?.toString()?.length ?: 0
                binding.wordCountTxt.text = "$charCount / 2000 characters"

                if (charCount > 2000) {
                    binding.wordCountTxt.setTextColor(android.graphics.Color.RED)
                } else {
                    val defaultColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.associate.R.color.text_color)
                    binding.wordCountTxt.setTextColor(defaultColor)
                }
            }
        })
    }

    private fun createAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            items
        ).apply {
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
        val notes = binding.etAdditionalNotes.text.toString().trim()

        if (!validateInputs(purpose, language, notes)) return

        startBookingProcess(purpose, language, notes)
    }

    private fun validateInputs(purpose: String, language: String, notes: String): Boolean {
        if (!DialogUtils.isValidPurpose(purpose)) {
            showToast("Please select purpose")
            return false
        }

        if (!DialogUtils.isValidLanguage(language)) {
            showToast("Please select language")
            return false
        }

        if (notes.isEmpty()) {
            showToast("Please provide additional notes")
            return false
        }

        // Character count check
        val charCount = notes.length
        if (charCount > 200) {
            showToast("Notes cannot exceed 2000 characters. Current: $charCount")
            return false
        }

        return true
    }

    private fun startBookingProcess(purpose: String, language: String, notes: String) {
        setLoading(true)

        bookingManager.createInstantBooking(
            advisorId = advisor.id,
            advisorName = advisor.name,
            purpose = purpose,
            preferredLanguage = language,
            additionalNotes = notes,
            urgencyLevel = "Medium", // Default value since we removed selector
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
        Toast.makeText(requireContext(), message, Toast .LENGTH_LONG).show()
    }
}