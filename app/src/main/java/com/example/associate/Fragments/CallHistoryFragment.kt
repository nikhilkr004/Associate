package com.example.associate.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Adapters.CallHistoryAdapter
import com.example.associate.Repositorys.CallHistoryRepository
import com.example.associate.databinding.FragmentCallHistoryBinding
import kotlinx.coroutines.launch

class CallHistoryFragment : Fragment() {
    private lateinit var binding: FragmentCallHistoryBinding
    private lateinit var videoCallAdapter: CallHistoryAdapter
    private val repository = CallHistoryRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadVideoCalls()
    }

    private fun setupRecyclerView() {
        videoCallAdapter = CallHistoryAdapter()
        binding.recyclerViewVideoCalls.apply {
            adapter = videoCallAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun loadVideoCalls() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            try {
                val videoCallsWithAdvisors = repository.getVideoCallsWithAdvisorDetails()

                if (videoCallsWithAdvisors.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerViewVideoCalls.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerViewVideoCalls.visibility = View.VISIBLE
                    videoCallAdapter.updateList(videoCallsWithAdvisors)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load video calls", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    companion object {
        fun newInstance() = CallHistoryFragment()
    }
}