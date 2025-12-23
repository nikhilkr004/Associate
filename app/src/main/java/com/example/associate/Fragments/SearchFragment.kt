package com.example.associate.Fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Activities.AdvisorProfileActivity
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.Repositories.UserRepository
import com.example.associate.databinding.FragmentSearchBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    
    private val searchList = mutableListOf<AdvisorDataClass>()
    private val allAdvisorsList = mutableListOf<AdvisorDataClass>()
    private val favoriteAdvisorIds = mutableListOf<String>() // Local cache of favorites
    
    private lateinit var searchAdapter: SearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        loadFavorites() // Load favorites first, then advisors
    }

    private fun loadFavorites() {
        val userId = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            when (val result = userRepository.getFavoriteAdvisors(userId)) {
                is UserRepository.Result.Success -> {
                    favoriteAdvisorIds.clear()
                    favoriteAdvisorIds.addAll(result.data)
                    withContext(Dispatchers.Main) {
                        loadAllAdvisors() // Load advisors after fetching favorites to correct initial state
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        loadAllAdvisors()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(searchList, favoriteAdvisorIds, 
            onClick = { advisor ->
                 val intent = android.content.Intent(requireContext(), AdvisorProfileActivity::class.java)
                 intent.putExtra("ADVISOR_DATA", advisor)
                 startActivity(intent)
            },
            onFavoriteClick = { advisor, position ->
                toggleFavorite(advisor, position)
            }
        )
        binding.rvAdvisors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAdvisors.adapter = searchAdapter
    }

    private fun toggleFavorite(advisor: AdvisorDataClass, position: Int) {
        val userId = auth.currentUser?.uid ?: return
        val advisorId = advisor.basicInfo.id
        
        if (advisorId.isEmpty()) return

        val isCurrentlyFavorite = favoriteAdvisorIds.contains(advisorId)
        
        // Optimistic Update
        val newStatus = !isCurrentlyFavorite
        if (newStatus) {
            favoriteAdvisorIds.add(advisorId)
        } else {
            favoriteAdvisorIds.remove(advisorId)
        }
        searchAdapter.notifyItemChanged(position)
        
        CoroutineScope(Dispatchers.IO).launch {
            if (newStatus) {
                userRepository.addFavoriteAdvisor(userId, advisorId)
            } else {
                userRepository.removeFavoriteAdvisor(userId, advisorId)
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(v.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun loadAllAdvisors() {
        db.collection("advisors")
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                allAdvisorsList.clear()
                searchList.clear()
                for (doc in documents) {
                    val advisor = doc.toObject(AdvisorDataClass::class.java)
                    val finalAdvisor = if (advisor.basicInfo.id.isEmpty()) {
                        advisor.copy(basicInfo = advisor.basicInfo.copy(id = doc.id))
                    } else advisor
                    
                    allAdvisorsList.add(finalAdvisor)
                    searchList.add(finalAdvisor)
                }
                searchAdapter.notifyDataSetChanged()
                
                if (searchList.isEmpty()) {
                    binding.tvEmptyState.text = "No advisors available"
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load advisors", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performSearch(query: String) {
        val lowerCaseQuery = query.toLowerCase().trim()
        
        searchList.clear()
        if (lowerCaseQuery.isEmpty()) {
            searchList.addAll(allAdvisorsList)
        } else {
            val filtered = allAdvisorsList.filter { advisor ->
                advisor.basicInfo.name.toLowerCase().contains(lowerCaseQuery) ||
                advisor.professionalInfo.specializations.any { it.toLowerCase().contains(lowerCaseQuery) } ||
                 advisor.professionalInfo.designation.toLowerCase().contains(lowerCaseQuery)
            }
            searchList.addAll(filtered)
        }
        searchAdapter.notifyDataSetChanged()

        if (searchList.isEmpty()) {
            binding.tvEmptyState.text = "No advisors found"
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SearchAdapter(
        private val advisors: List<AdvisorDataClass>,
        private val favoriteIds: List<String>,
        private val onClick: (AdvisorDataClass) -> Unit,
        private val onFavoriteClick: (AdvisorDataClass, Int) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

        inner class SearchViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val name: android.widget.TextView = view.findViewById(com.example.associate.R.id.tvAdvisorName)
            val special: android.widget.TextView = view.findViewById(com.example.associate.R.id.tvSpecialization)
            val price: android.widget.TextView = view.findViewById(com.example.associate.R.id.tvPrice)
            val image: android.widget.ImageView = view.findViewById(com.example.associate.R.id.ivAdvisorImage)
            val bookBtn: android.widget.Button = view.findViewById(com.example.associate.R.id.btnBook)
            val rating: android.widget.TextView = view.findViewById(com.example.associate.R.id.tvRating)
            val favoriteIcon: android.widget.ImageView = view.findViewById(com.example.associate.R.id.ivFavorite)

            fun bind(advisor: AdvisorDataClass, isFavorite: Boolean) {
                name.text = advisor.basicInfo.name
                val spec = if(advisor.professionalInfo.specializations.isNotEmpty()) 
                             advisor.professionalInfo.specializations[0] 
                           else advisor.professionalInfo.department
                special.text = spec.ifEmpty { "Advisor" }
                price.text = "${advisor.pricingInfo.scheduledVideoFee}$ Session"
                rating.text = advisor.performanceInfo.rating.toString()

                if (advisor.basicInfo.profileImage.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(itemView.context)
                        .load(advisor.basicInfo.profileImage)
                        .placeholder(com.example.associate.R.drawable.user)
                        .into(image)
                }

                // Initial Favorite State
                if (isFavorite) {
                    favoriteIcon.setImageResource(com.example.associate.R.drawable.filled_heart)
                } else {
                    favoriteIcon.setImageResource(com.example.associate.R.drawable.empty_heart)
                }

                favoriteIcon.setOnClickListener { onFavoriteClick(advisor, adapterPosition) }
                bookBtn.setOnClickListener { onClick(advisor) }
                itemView.setOnClickListener { onClick(advisor) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(com.example.associate.R.layout.item_advisor_search, parent, false)
            return SearchViewHolder(view)
        }

        override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
            val advisor = advisors[position]
            val isFav = favoriteIds.contains(advisor.basicInfo.id)
            holder.bind(advisor, isFav)
        }

        override fun getItemCount() = advisors.size
    }

    companion object {
        fun newInstance() = SearchFragment()
    }
}
