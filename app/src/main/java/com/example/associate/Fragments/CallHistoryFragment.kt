package com.example.associate.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Adapters.CallHistoryAdapter
import com.example.associate.ViewModel.CallHistoryViewModel
import com.example.associate.databinding.FragmentCallHistoryBinding

/**
 * Fragment to display the list of past video calls.
 * Uses [CallHistoryViewModel] to fetch and manage data.
 */
class CallHistoryFragment : Fragment() {
    
    private var _binding: FragmentCallHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var videoCallAdapter: CallHistoryAdapter
    private lateinit var viewModel: CallHistoryViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupRecyclerView()
        setupTabs()
        observeViewModel()
        
        // Load data
        viewModel.loadCallHistory()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[CallHistoryViewModel::class.java]
    }

    private fun setupRecyclerView() {
        videoCallAdapter = CallHistoryAdapter { action, booking, advisor ->
            when (action) {
                CallHistoryAdapter.CallHistoryAction.CANCEL -> {
                    viewModel.cancelBooking(booking)
                    Toast.makeText(requireContext(), "Cancelling booking...", Toast.LENGTH_SHORT).show()
                }
                
                CallHistoryAdapter.CallHistoryAction.REBOOK,
                CallHistoryAdapter.CallHistoryAction.RESCHEDULE -> {
                    navigateToAdvisorProfile(booking)
                }
                
                CallHistoryAdapter.CallHistoryAction.ITEM_CLICK -> {
                     val status = booking.bookingStatus.lowercase()
                     if (status == "completed" || status == "ended") {
                         val intent = android.content.Intent(requireContext(), com.example.associate.Activities.BookingSummaryActivity::class.java)
                         intent.putExtra("BOOKING_DATA", booking)
                         if (advisor != null) {
                             intent.putExtra("ADVISOR_DATA", advisor)
                         }
                         startActivity(intent)
                     } else {
                         // Pending/Upcoming -> Maybe show details too OR go to Profile
                         // User asked specifically for "booking complete" items.
                         // For now, let's keep others pointing to profile OR do nothing/toast.
                         // But usually clicking an item should do something useful.
                         navigateToAdvisorProfile(booking)
                     }
                }
                
                CallHistoryAdapter.CallHistoryAction.REVIEW -> {
                    showReviewDialog(booking)
                }
            }
        }
        binding.recyclerViewVideoCalls.apply {
            adapter = videoCallAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun navigateToAdvisorProfile(booking: com.example.associate.DataClass.SessionBookingDataClass) {
        if (booking.advisorId.isEmpty()) {
            Toast.makeText(requireContext(), "Error: Advisor ID missing", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create minimal AdvisorDataClass to pass ID
        val advisorData = com.example.associate.DataClass.AdvisorDataClass(
            basicInfo = com.example.associate.DataClass.BasicInfo(
                id = booking.advisorId,
                name = booking.advisorName,
                // We could pass more if available from UserData, but ID is critical
            )
        )
        
        val intent = android.content.Intent(requireContext(), com.example.associate.Activities.AdvisorProfileActivity::class.java)
        intent.putExtra("ADVISOR_DATA", advisorData)
        startActivity(intent)
    }

    private fun showReviewDialog(booking: com.example.associate.DataClass.SessionBookingDataClass) {
        val ratingDialog = com.example.associate.Dialogs.RatingDialog { rating, review ->
            viewModel.submitReview(booking, rating, review) { success ->
                if (success) {
                    com.example.associate.DataClass.DialogUtils.showStatusDialog(
                        requireContext(), true, "Success", "Review Submitted Successfully"
                    ) {}
                }
            }
        }
        ratingDialog.show(parentFragmentManager, "RatingDialog")
    }
    
    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let { viewModel.filterByTab(it.position) }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun observeViewModel() {
        // Observe list data
        viewModel.callHistoryList.observe(viewLifecycleOwner) { historyList ->
            if (historyList.isNullOrEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerViewVideoCalls.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerViewVideoCalls.visibility = View.VISIBLE
                videoCallAdapter.updateList(historyList)
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CallHistoryFragment()
    }
}