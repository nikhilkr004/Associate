package com.example.associate.Fragments

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Activities.AdvisorProfileActivity
import com.example.associate.Adapters.AdvisorAdapter
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.SessionBookingDataClass
import com.example.associate.Dialogs.IncomingCallDialog
import com.example.associate.Payment.LoadMoneyActivity
import com.example.associate.ViewModel.HomeViewModel
import com.example.associate.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.TimeUnit

/**
 * Home screen fragment.
 * Refactored to MVVM.
 * Note: Call listening logic is preserved here to ensure stability.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AdvisorAdapter
    private lateinit var viewModel: HomeViewModel
    
    // Call Logic (Preserved)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var callListener: ListenerRegistration? = null
    
    // Timer for booking
    private var responseTimer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupRecyclerView()
        binding_init()
        observeViewModel()
        
        // Load initial data
        viewModel.loadData()

        // Keep Call Logic
        Log.d("HomeFragment", "Initializing call listener...")
//        listenForIncomingCalls()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
    }

    private fun binding_init() {
        binding.loadMoneyLayout.setOnClickListener {
            startActivity(Intent(requireContext(), LoadMoneyActivity::class.java))
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData()
            Handler(Looper.getMainLooper()).postDelayed({
                if (_binding != null) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }, 2000)
        }

        binding.notifictionBtn.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.associate.Activities.NotificationActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = AdvisorAdapter(mutableListOf()) { advisor ->
            handleAdvisorClick(advisor)
        }

        binding.topAdvisoreRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.topAdvisoreRecyclerview.adapter = adapter
    }

    private fun observeViewModel() {
        // Advisors List
        viewModel.advisors.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list)
            
            if (list.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.topAdvisorLayout.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.topAdvisorLayout.visibility = View.VISIBLE
            }
        }


        // Wallet Balance
        viewModel.walletBalance.observe(viewLifecycleOwner) { balance ->
            binding.currentBalance.text = "₹${String.format("%.2f", balance)}"
        }

        // Active Booking
        viewModel.activeBooking.observe(viewLifecycleOwner) { booking ->
            if (booking != null) {
                showActiveBookingCard(booking)
            } else {
                hideActiveBookingCard()
            }
        }

        // Loading
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
             binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Error
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun handleAdvisorClick(advisor: AdvisorDataClass) {
        val intent = Intent(requireContext(), AdvisorProfileActivity::class.java).apply {
            putExtra("ADVISOR_DATA", advisor)
        }
        startActivity(intent)
    }

    // --- Booking UI Logic ---

    private fun showActiveBookingCard(booking: SessionBookingDataClass) {
        binding.currentBookingCard.visibility = View.VISIBLE
        binding.bookingAdvisorName.text = "Advisor: ${booking.advisorName}"
        binding.bookingId.text = "ID: #${booking.bookingId}"
        binding.bookingPurpose.text = "Purpose: ${booking.purpose}"

        when (booking.bookingStatus.lowercase()) {
            "pending" -> {
                binding.bookingStatusTitle.text = "Requesting Session..."
                binding.bookingStatusTitle.setTextColor(android.graphics.Color.BLACK)
                binding.btnCancelBooking.visibility = View.VISIBLE
                binding.btnCancelBooking.isEnabled = true
                binding.btnCancelBooking.text = "Cancel Request"
                binding.bookingTimer.visibility = View.VISIBLE
                binding.bookingTimerLabel.visibility = View.VISIBLE

                startResponseTimer(booking.getAdvisorResponseDeadlineAsLong())

                binding.btnCancelBooking.setOnClickListener {
                    binding.btnCancelBooking.text = "Cancelling..."
                    binding.btnCancelBooking.isEnabled = false
                    viewModel.cancelBooking(booking.bookingId)
                }
            }
            "accepted" -> {
                binding.bookingStatusTitle.text = "Request Accepted"
                binding.bookingStatusTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                binding.btnCancelBooking.visibility = View.GONE
                
                binding.bookingTimer.visibility = View.VISIBLE
                binding.bookingTimerLabel.visibility = View.VISIBLE
                binding.bookingTimerLabel.text = "Advisor connecting in"
                
                startResponseTimer(booking.getAdvisorResponseDeadlineAsLong())
            }
            "rejected" -> {
                binding.bookingStatusTitle.text = "Booking Rejected"
                binding.bookingStatusTitle.setTextColor(android.graphics.Color.RED)
                binding.btnCancelBooking.visibility = View.GONE
                
                responseTimer?.cancel()
                binding.bookingTimer.text = "Closed"
                binding.bookingTimerLabel.visibility = View.GONE
            }
            else -> {
                binding.bookingStatusTitle.text = "Status: ${booking.bookingStatus}"
                binding.btnCancelBooking.visibility = View.GONE
                responseTimer?.cancel()
            }
        }
    }

    private fun hideActiveBookingCard() {
        binding.currentBookingCard.visibility = View.GONE
        responseTimer?.cancel()
    }

    private fun startResponseTimer(deadline: Long) {
        responseTimer?.cancel()
        val currentTime = System.currentTimeMillis()
        val timeRemaining = deadline - currentTime

        if (timeRemaining > 0) {
            responseTimer = object : CountDownTimer(timeRemaining, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    binding.bookingTimer.text = String.format("%02d:%02d", minutes, seconds)
                }
                override fun onFinish() {
                    binding.bookingTimer.text = "00:00"
                }
            }.start()
        } else {
            binding.bookingTimer.text = "00:00"
        }
    }

    // --- Incoming Call Logic (Preserved) ---

    private fun listenForIncomingCalls() {
        val currentUserId = auth.currentUser?.uid ?: return

        callListener = db.collection("videoCalls")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", "initiated")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                
                snapshots?.documents?.forEach { document ->
                    val callData = document.data
                    callData?.let { data ->
                        val callId = data["id"] as? String ?: ""
                        val advisorName = data["advisorName"] as? String ?: "Advisor"
                        val channelName = data["channelName"] as? String ?: ""

                        showIncomingCallDialog(callId, advisorName, channelName)
                        updateCallStatus(callId, "ringing")
                    }
                }
            }
    }

    private fun showIncomingCallDialog(callId: String, advisorName: String, channelName: String) {
        if (!isAdded || requireActivity().isFinishing) return
        try {
            IncomingCallDialog(requireContext(), callId, advisorName, channelName).show()
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error showing dialog", e)
        }
    }

    private fun updateCallStatus(callId: String, status: String) {
        db.collection("videoCalls").document(callId).update("status", status)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        callListener?.remove()
        responseTimer?.cancel()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
