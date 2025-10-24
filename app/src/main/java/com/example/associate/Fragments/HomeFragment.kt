package com.example.associate.Fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Activitys.AdvisorProfileActivity
import com.example.associate.Adapters.AdvisorAdapter
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.DialogUtils
import com.example.associate.R
import com.example.associate.databinding.FragmentHomeBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var adapter: AdvisorAdapter
    private val db = FirebaseFirestore.getInstance()
    private val advisorsList = mutableListOf<AdvisorDataClass>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        fetchAdvisors()
        setupRecyclerView()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = AdvisorAdapter(advisorsList) { advisor ->
            // Handle item click - navigate to advisor details
            val intent = Intent(requireContext(), AdvisorProfileActivity::class.java).apply {
                putExtra("ADVISOR_ID", advisor.id)

            }
            startActivity(intent)
        }

        binding.topAdvisoreRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.topAdvisoreRecyclerview.adapter = adapter
    }

    private fun fetchAdvisors() {
        // Show progress bar
        DialogUtils.showLoadingDialog(requireContext(), "Loading")

        db.collection("advisors")
            .whereEqualTo("status", "active")

            .addSnapshotListener { snapshot, error ->
                DialogUtils.hideLoadingDialog()

                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        "Error fetching advisors: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }

                advisorsList.clear()
                snapshot?.documents?.forEach { document ->
                    val advisor = document.toObject(AdvisorDataClass::class.java)
                        ?.copy(id = document.id)
                    advisor?.let {
                        advisorsList.add(it)
                        Log.d("DEBUG", advisorsList.size.toString())
                    }
                }

                adapter.notifyDataSetChanged()

//                if (advisorsList.isEmpty()) {
//                    binding.tvEmptyState.visibility = View.VISIBLE
//                } else {
//                    binding.tvEmptyState.visibility = View.GONE
//                }
            }
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}