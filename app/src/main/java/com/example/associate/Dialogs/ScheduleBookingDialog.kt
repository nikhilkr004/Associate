package com.example.associate.Dialogs

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.example.associate.Adapters.TimeSlotAdapter
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.R
import com.example.associate.Repositories.SessionBookingManager
import com.example.associate.ViewModels.ScheduleBookingViewModel
import com.example.associate.ViewModels.ScheduleBookingViewModel.BookingType
import com.example.associate.databinding.DialogScheduleBookingBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleBookingDialog(
    private val advisor: AdvisorDataClass,
    private val onBookingSuccess: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogScheduleBookingBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookingManager: SessionBookingManager
    
    // ViewModel integration
    private val viewModel: ScheduleBookingViewModel by viewModels()
    private lateinit var slotAdapter: TimeSlotAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogScheduleBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bookingManager = SessionBookingManager(requireContext())
        
        // Initialize ViewModel
        viewModel.init(advisor)

        setupRecyclerView()
        setupPricing()
        setupObservers()
        setupClickListeners()
        setupCharCounter()
        fetchWalletBalance()
    }

    private var userWalletBalance = 0.0
    private var isBalanceLoaded = false

    private fun fetchWalletBalance() {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        // Disable button while loading
        binding.btnProcess.isEnabled = false
        binding.btnProcess.text = "Loading..."
        
        db.collection("wallets").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val balanceFromBalance = document.getDouble("balance")
                    val balanceFromWalletBalance = document.getDouble("walletBalance")
                    userWalletBalance = balanceFromBalance ?: balanceFromWalletBalance ?: 0.0
                }
                isBalanceLoaded = true
                binding.btnProcess.isEnabled = true
                binding.btnProcess.text = "Confirm Schedule"
            }
            .addOnFailureListener {
                isBalanceLoaded = true
                binding.btnProcess.isEnabled = true
                binding.btnProcess.text = "Confirm Schedule"
            }
    }
    
    private fun setupRecyclerView() {
        slotAdapter = TimeSlotAdapter { slot ->
            viewModel.selectSlot(slot)
        }
        binding.rvTimeSlots.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvTimeSlots.adapter = slotAdapter
    }
    
    private fun setupPricing() {
        binding.tvChatPrice.text = "₹ ${advisor.pricingInfo.scheduledChatFee}"
        binding.tvAudioPrice.text = "₹ ${advisor.pricingInfo.scheduledAudioFee}"
        binding.tvVideoPrice.text = "₹ ${advisor.pricingInfo.scheduledVideoFee}"
        
        val availability = advisor.availabilityInfo.scheduledAvailability
        
        if (!availability.isChatEnabled) disableCard(binding.cardChat, binding.imgChat, binding.tvChatPrice)
        if (!availability.isAudioCallEnabled) disableCard(binding.cardAudio, binding.imgAudio, binding.tvAudioPrice)
        if (!availability.isVideoCallEnabled) disableCard(binding.cardVideo, binding.imgVideo, binding.tvVideoPrice)
    }
    
    private fun disableCard(card: View, icon: android.widget.ImageView, text: android.widget.TextView) {
        card.isEnabled = false
        card.isClickable = false
        card.background = ContextCompat.getDrawable(requireContext(), R.drawable.card_bg_disabled)
        card.alpha = 0.5f 
    }

    private fun setupObservers() {
        viewModel.selectedType.observe(viewLifecycleOwner) { type ->
            updateSelectionUI(type)
        }
        
        viewModel.selectedDate.observe(viewLifecycleOwner) { calendar ->
            if (calendar != null) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvSelectedDate.text = dateFormat.format(calendar.time)
            } else {
                 binding.tvSelectedDate.text = "Select Booking Date"
            }
        }
        
        viewModel.availableSlots.observe(viewLifecycleOwner) { slots ->
            slotAdapter.submitList(slots)
            
            if (slots.isEmpty()) {
                if (viewModel.selectedDate.value != null) {
                    binding.rvTimeSlots.visibility = View.GONE
                    binding.lottieEmptySlots.visibility = View.VISIBLE
                    binding.lottieEmptySlots.playAnimation()
                } else {
                     binding.rvTimeSlots.visibility = View.GONE
                     binding.lottieEmptySlots.visibility = View.GONE
                }
            } else {
                binding.rvTimeSlots.visibility = View.VISIBLE
                binding.lottieEmptySlots.visibility = View.GONE
                binding.lottieEmptySlots.cancelAnimation()
            }
        }
        
        viewModel.selectedSlot.observe(viewLifecycleOwner) { slot ->
            slotAdapter.setSelectedSlot(slot)
        }
    }

    private fun updateSelectionUI(type: BookingType) {
        // Reset all backgrounds
        binding.cardChat.setBackgroundResource(R.drawable.card_bg_unselected)
        binding.cardAudio.setBackgroundResource(R.drawable.card_bg_unselected)
        binding.cardVideo.setBackgroundResource(R.drawable.card_bg_unselected)
        
        // Update selected
        when (type) {
            BookingType.CHAT -> binding.cardChat.setBackgroundResource(R.drawable.card_bg_selected)
            BookingType.AUDIO -> binding.cardAudio.setBackgroundResource(R.drawable.card_bg_selected)
            BookingType.VIDEO -> binding.cardVideo.setBackgroundResource(R.drawable.card_bg_selected)
        }
    }

    private fun setupClickListeners() {
        binding.cardChat.setOnClickListener { viewModel.selectType(BookingType.CHAT) }
        binding.cardAudio.setOnClickListener { viewModel.selectType(BookingType.AUDIO) }
        binding.cardVideo.setOnClickListener { viewModel.selectType(BookingType.VIDEO) }
        
        binding.layoutDatePicker.setOnClickListener { showDatePicker() }
        
        binding.btnProcess.setOnClickListener { processBooking() }
    }
    
    private fun showDatePicker() {
        val calendar = viewModel.selectedDate.value ?: Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newDate = Calendar.getInstance()
                newDate.set(year, month, dayOfMonth)
                viewModel.selectDate(newDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }
    
    private fun setupCharCounter() {
        binding.etAgenda.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val count = s?.length ?: 0
                binding.tvCharCount.text = "$count/250"
                if (count > 250) {
                    binding.tvCharCount.setTextColor(android.graphics.Color.RED)
                } else {
                    binding.tvCharCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                }
            }
        })
    }

    private fun processBooking() {
        val agenda = binding.etAgenda.text.toString().trim()
        val error = viewModel.validateBooking(agenda)
        
        if (error != null) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (agenda.length > 250) {
            Toast.makeText(requireContext(), "Agenda is too long", Toast.LENGTH_SHORT).show()
            return
        }
        
        val requiredAmount = viewModel.totalPrice.value?.toDouble() ?: 0.0
        if (userWalletBalance < requiredAmount) {
             Toast.makeText(requireContext(), "Insufficient balance. Required: ₹$requiredAmount", Toast.LENGTH_LONG).show()
             return
        }
        
        startBookingProcess(agenda)
    }

    private fun startBookingProcess(agenda: String) {
        showLoadingDialog()
        
        val date = viewModel.selectedDate.value!!
        val slot = viewModel.selectedSlot.value!!
        val type = viewModel.selectedType.value!!
        
        val purpose = "Scheduled ${type.name}: $slot"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateString = dateFormat.format(date.time)

        bookingManager.createScheduledBooking(
            advisorId = advisor.basicInfo.id,
            advisorName = advisor.basicInfo.name,
            purpose = purpose,
            preferredLanguage = "English",
            additionalNotes = agenda,
            bookingType = type.name,
            bookingSlot = slot,
            bookingDate = dateString,
            urgencyLevel = "Scheduled",
            sessionAmount = viewModel.totalPrice.value?.toString() ?: "0",
            onSuccess = { message ->
                dismissLoadingDialog()
                onBookingSuccess()
                dismiss()
                Toast.makeText(requireContext(), "Scheduled Successfully!", Toast.LENGTH_LONG).show()
            },
            onFailure = { error ->
                dismissLoadingDialog()
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }
    
    private var progressDialog: android.app.ProgressDialog? = null

    private fun showLoadingDialog() {
        if (progressDialog == null) {
            progressDialog = android.app.ProgressDialog(requireContext())
            progressDialog?.setMessage("Processing Booking...")
            progressDialog?.setCancelable(false)
        }
        progressDialog?.show()
    }

    private fun dismissLoadingDialog() {
        progressDialog?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int {
        return R.style.CustomBottomSheetDialogTheme
    }
}
