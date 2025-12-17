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
        observeViewModel()
        
        // Load data
        viewModel.loadCallHistory()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[CallHistoryViewModel::class.java]
    }

    private fun setupRecyclerView() {
        videoCallAdapter = CallHistoryAdapter()
        binding.recyclerViewVideoCalls.apply {
            adapter = videoCallAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
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