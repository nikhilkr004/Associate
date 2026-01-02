package com.example.associate.Dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.R
import com.example.associate.Repositories.SessionBookingManager
import com.example.associate.ViewModels.InstantBookingViewModel
import com.example.associate.ViewModels.InstantBookingViewModel.BookingType
import com.example.associate.databinding.DialogInstantBookingBinding
import com.example.associate.DataClass.DialogUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class InstantBookingDialog(
    private val advisor: AdvisorDataClass,
    private val onBookingSuccess: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogInstantBookingBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookingManager: SessionBookingManager
    
    // ViewModel integration
    private val viewModel: InstantBookingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogInstantBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bookingManager = SessionBookingManager(requireContext())
        
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
                android.util.Log.d("InstantBooking", "📄 Document exists: ${document.exists()}")
                android.util.Log.d("InstantBooking", "📄 Document data: ${document.data}")
                
                if (document.exists()) {
                    // Try multiple field names - PRIORITIZE 'balance' (correct field)
                    val balanceFromBalance = document.getDouble("balance")
                    val balanceFromWalletBalance = document.getDouble("walletBalance")
                    
                    android.util.Log.d("InstantBooking", "💰 balance field: $balanceFromBalance")
                    android.util.Log.d("InstantBooking", "💰 walletBalance field: $balanceFromWalletBalance")
                    
                    userWalletBalance = balanceFromBalance ?: balanceFromWalletBalance ?: 0.0
                    android.util.Log.d("InstantBooking", "✅ Final Wallet Balance: ₹$userWalletBalance")
                }
                isBalanceLoaded = true
                binding.btnProcess.isEnabled = true
                binding.btnProcess.text = "Process"
            }
            .addOnFailureListener { e ->
                android.util.Log.e("InstantBooking", "❌ Failed to fetch balance: ${e.message}")
                isBalanceLoaded = true
                binding.btnProcess.isEnabled = true
                binding.btnProcess.text = "Process"
            }
    }
    
    private fun setupPricing() {
        binding.tvChatPrice.text = "₹ ${advisor.pricingInfo.instantChatFee}"
        binding.tvAudioPrice.text = "₹ ${advisor.pricingInfo.instantAudioFee}"
        binding.tvVideoPrice.text = "₹ ${advisor.pricingInfo.instantVideoFee}"
        
        // Handle availability (disable cards if not available)
        val availability = advisor.availabilityInfo.instantAvailability
        
        if (!availability.isChatEnabled) {
            disableCard(binding.cardChat, binding.imgChat, binding.tvChatPrice)
        }
        if (!availability.isAudioCallEnabled) {
            disableCard(binding.cardAudio, binding.imgAudio, binding.tvAudioPrice)
        }
        if (!availability.isVideoCallEnabled) {
            disableCard(binding.cardVideo, binding.imgVideo, binding.tvVideoPrice)
        }
    }
    
    private fun disableCard(card: View, icon: android.widget.ImageView, text: android.widget.TextView) {
        card.isEnabled = false
        card.alpha = 0.5f
    }

    private fun setupObservers() {
        viewModel.selectedType.observe(viewLifecycleOwner) { type ->
            updateSelectionUI(type)
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
            else -> {}
        }
    }

    private fun setupClickListeners() {
        binding.cardChat.setOnClickListener {
            viewModel.selectType(BookingType.CHAT, advisor.pricingInfo.instantChatFee)
        }
        binding.cardAudio.setOnClickListener {
            viewModel.selectType(BookingType.AUDIO, advisor.pricingInfo.instantAudioFee)
        }
        binding.cardVideo.setOnClickListener {
            viewModel.selectType(BookingType.VIDEO, advisor.pricingInfo.instantVideoFee)
        }
        
        binding.btnProcess.setOnClickListener {
            processBooking()
        }
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
                    binding.tvCharCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray)) // Assuming gray exists or hardcode color
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
            Toast.makeText(requireContext(), "Agenda is too long (max 250 chars)", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 🔥 STRICT RULE: Minimum Balance Check
        if (userWalletBalance < 100) {
             Toast.makeText(requireContext(), "Insufficient balance. Minimum ₹100 required.", Toast.LENGTH_LONG).show()
             return
        }
        
        // Start Booking Process
        startBookingProcess(viewModel.selectedType.value!!, agenda)
    }

    private fun startBookingProcess(type: BookingType, agenda: String) {
        setLoading(true)
        
        // Map BookingType to purpose/method string if needed
        // For now using the type as purpose or passing it appropriately
        val purpose = "Instant ${type.name}" // Simplification
        
        bookingManager.createInstantBooking(
            advisorId = advisor.basicInfo.id,
            advisorName = advisor.basicInfo.name,
            purpose = purpose,
            preferredLanguage = "English", // Defaulting as simplified inputs
            additionalNotes = agenda,
            bookingType = type.name, // ✅ Pass correctly
            urgencyLevel = "Medium",
            sessionAmount = viewModel.totalPrice.value?.toDouble() ?: 100.0, // ✅ Pass Price 
            onSuccess = { message ->
                setLoading(false)
                
                // Format Date for Instant
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val today = java.util.Date()
                val dateString = dateFormat.format(today)
                val timeString = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(today)

                DialogUtils.showStatusDialog(
                    requireContext(),
                    isSuccess = true,
                    title = "Booking Successful",
                    message = "Your booking with ${advisor.basicInfo.name} has been confirmed for $dateString at $timeString.",
                    buttonText = "Go to Home",
                    action = {
                        onBookingSuccess(message)
                        dismiss()
                    }
                )
            },
            onFailure = { error ->
                setLoading(false)
                DialogUtils.showStatusDialog(
                    requireContext(),
                    isSuccess = false,
                    title = "Booking Failed",
                    message = error,
                    buttonText = "Try Again",
                    action = {
                        // Just dismiss dialog, allowing user to retry in the form? Or dismiss sheet?
                        // User likely wants to retry. 'Try Again' could just close error dialog.
                    }
                )
            }
        )
    }
    
    private fun setLoading(loading: Boolean) {
        binding.btnProcess.isEnabled = !loading
        binding.btnProcess.text = if (loading) "Processing..." else "Process"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int {
        return R.style.CustomBottomSheetDialogTheme
    }
}