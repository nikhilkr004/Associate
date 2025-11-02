package com.example.associate.Fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Activitys.AdvisorProfileActivity
import com.example.associate.Adapters.AdvisorAdapter
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.WalletDataClass
import com.example.associate.Payment.LoadMoneyActivity
import com.example.associate.databinding.FragmentHomeBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var adapter: AdvisorAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val advisorsList = mutableListOf<AdvisorDataClass>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding_init()
        setupRecyclerView()
        fetchBalance()
        fetchAdvisorsData()
    }

    private fun fetchBalance() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("wallets")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val wallet = document.toObject(WalletDataClass::class.java)
                        wallet?.let {
                            binding.currentBalance.text = "₹${String.format("%.2f", it.balance)}"
                        }
                    } else {
                        createWalletDocument(currentUser.uid)
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to fetch balance: ${exception.message}", Toast.LENGTH_SHORT).show()
                    binding.currentBalance.text = "₹0.00"
                }
        } else {
            binding.currentBalance.text = "₹0.00"
        }
    }

    private fun createWalletDocument(userId: String) {
        val wallet = WalletDataClass(
            userId = userId,
            balance = 0.0,
            lastUpdated = Timestamp.now(),
            totalAdded = 0.0,
            totalSpent = 0.0,
            transactionCount = 0
        )

        db.collection("wallets")
            .document(userId)
            .set(wallet)
            .addOnSuccessListener {
                binding.currentBalance.text = "₹0.00"
                Toast.makeText(requireContext(), "Wallet created successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to create wallet: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.currentBalance.text = "₹0.00"
            }
    }

    private fun binding_init() {
        binding.loadMoneyLayout.setOnClickListener {
            startActivity(Intent(requireContext(), LoadMoneyActivity::class.java))
        }

        // SwipeRefreshLayout setup
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchAdvisorsData()
            fetchBalance()

            Handler(Looper.getMainLooper()).postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 2000)
        }
    }

    private fun setupRecyclerView() {
        // 🔥 Simple list pass karein aur click listener handle karein
        adapter = AdvisorAdapter(advisorsList) { advisor ->
            // Yahan direct advisor object mil jayega
            handleAdvisorClick(advisor)
        }

        binding.topAdvisoreRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.topAdvisoreRecyclerview.adapter = adapter
    }

    // 🔥 Separate function for handling click
    private fun handleAdvisorClick(advisor: AdvisorDataClass) {
        // Yahan aap kuch bhi kar sakte hain - activity start karein, dialog show karein, etc.
        val intent = Intent(requireContext(), AdvisorProfileActivity::class.java).apply {
            // 🔥 Pure object pass karne ke liye Parcelable ya Serializable use karein
            putExtra("ADVISOR_DATA", advisor) // Agar AdvisorDataClass Parcelable hai to
        }
        startActivity(intent)

        // Ya simple toast show karein
        Toast.makeText(requireContext(), "Clicked: ${advisor.name}", Toast.LENGTH_SHORT).show()
    }

    private fun fetchAdvisorsData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        Log.d("DEBUG", "Fetching advisors...")

        db.collection("advisors")
            .whereEqualTo("status", "active")
            .whereEqualTo("isactive", "true")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                advisorsList.clear()

                Log.d("DEBUG", "Documents found: ${documents.size()}")

                for (document in documents) {
                    try {
                        val advisor = document.toObject(AdvisorDataClass::class.java)
                        advisor.id = document.id
                        advisorsList.add(advisor)

                        Log.d("DEBUG", "Added advisor: ${advisor.name}")
                    } catch (e: Exception) {
                        Log.e("DEBUG", "Error parsing advisor: ${e.message}")
                    }
                }

                Log.d("DEBUG", "Final list size: ${advisorsList.size}")

                // 🔥 Adapter ko notify karein
                adapter.notifyDataSetChanged()

                if (advisorsList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.topAdvisorLayout.visibility = View.GONE
                    Log.d("DEBUG", "Showing empty state")
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.topAdvisorLayout.visibility = View.VISIBLE
                    Log.d("DEBUG", "Showing advisors list")
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.topAdvisorLayout.visibility = View.GONE

                Log.e("DEBUG", "Error: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to fetch advisors", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}