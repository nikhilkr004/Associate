package com.example.associate.Fragments

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.Activities.AdvisorProfileActivity
import com.example.associate.Adapters.CommunityAdapter
import com.example.associate.DataClass.Post
import com.example.associate.Repositories.CommunityRepository
import com.example.associate.databinding.FragmentCommunityBinding
import kotlinx.coroutines.launch

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    private val repository = CommunityRepository()
    private lateinit var adapter: CommunityAdapter
    private val visibleHandler = Handler(Looper.getMainLooper())
    private val visiblePostTracker = mutableMapOf<String, Long>() // PostId -> StartTime
    private val viewedPosts = mutableSetOf<String>() // PostIds already incremented
    private val VISIBILITY_THRESHOLD_MS = 3000L

    private val visibilityRunnable = object : Runnable {
        override fun run() {
            checkVisibility()
            visibleHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadPosts()
    }

    private fun setupRecyclerView() {
        adapter = CommunityAdapter(
            onProfileClick = { advisorId ->
                val intent = Intent(requireContext(), AdvisorProfileActivity::class.java)
                intent.putExtra("advisorId", advisorId)
                startActivity(intent)
            },
            onBookSessionClick = { advisorId ->
                // Basic logic: Navigate to AdvisorProfile for booking, or open dialog if implemented
                val intent = Intent(requireContext(), AdvisorProfileActivity::class.java)
                intent.putExtra("advisorId", advisorId)
                intent.putExtra("openBooking", true) // Optional flag
                startActivity(intent)
            },
            onPostVisible = { postId ->
                // Handled by scroll check mainly, but this can be a fallback signal
            }
        )

        binding.rvCommunity.layoutManager = LinearLayoutManager(context)
        binding.rvCommunity.adapter = adapter
    }

    private fun loadPosts() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val posts = repository.getFeedPosts()
            adapter.submitList(posts)
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        visibleHandler.post(visibilityRunnable)
    }

    override fun onPause() {
        super.onPause()
        visibleHandler.removeCallbacks(visibilityRunnable)
        visiblePostTracker.clear()
    }

    private fun checkVisibility() {
        val layoutManager = binding.rvCommunity.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) return

        val currentVisiblePosts = mutableSetOf<String>()

        for (i in firstVisible..lastVisible) {
            val view = layoutManager.findViewByPosition(i) ?: continue
            val post = adapter.currentList.getOrNull(i) ?: continue

            if (isViewVisible(view)) {
                currentVisiblePosts.add(post.postId)
                if (viewedPosts.contains(post.postId)) continue

                if (!visiblePostTracker.containsKey(post.postId)) {
                    visiblePostTracker[post.postId] = System.currentTimeMillis()
                } else {
                    val startTime = visiblePostTracker[post.postId] ?: 0L
                    if (System.currentTimeMillis() - startTime >= VISIBILITY_THRESHOLD_MS) {
                        repository.incrementViewCount(post.postId)
                        viewedPosts.add(post.postId)
                        visiblePostTracker.remove(post.postId)
                    }
                }
            }
        }

        // Remove posts that are no longer visible
        val setIterator = visiblePostTracker.iterator()
        while (setIterator.hasNext()) {
            val entry = setIterator.next()
            if (!currentVisiblePosts.contains(entry.key)) {
                setIterator.remove()
            }
        }
    }

    private fun isViewVisible(view: View): Boolean {
        val rect = Rect()
        val isVisible = view.getGlobalVisibleRect(rect)
        if (!isVisible) return false

        val height = view.height
        val visibleHeight = rect.height()
        return visibleHeight >= height * 0.5 // >50% visible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
