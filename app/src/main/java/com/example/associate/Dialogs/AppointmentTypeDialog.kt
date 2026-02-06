package com.example.associate.Dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.R
import com.example.associate.databinding.DialogAppointmentTypeSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AppointmentTypeDialog(
    private val advisor: AdvisorDataClass,
    private val onInstantClick: () -> Unit,
    private val onScheduleClick: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAppointmentTypeSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAppointmentTypeSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set dynamic title
        val firstName = advisor.basicInfo.name.split(" ").firstOrNull() ?: "Advisor"
        binding.tvTitle.text = "Connect with $firstName"
        
        // Setup click listeners for Connect/Book buttons
        binding.btnInstantConnect.setOnClickListener {
            dismiss()
            onInstantClick()
        }

        binding.btnScheduleBook.setOnClickListener {
            dismiss()
            onScheduleClick()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int {
        return R.style.CustomBottomSheetDialogTheme
    }
}
