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
import com.example.associate.Adapters.SocialPostAdapter
import com.example.associate.DataClass.Post
import com.example.associate.Repositories.CommunityRepository
import com.example.associate.databinding.FragmentCommunityBinding
import kotlinx.coroutines.launch

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    private val repository = CommunityRepository()
    private lateinit var adapter: SocialPostAdapter
    private val visibleHandler = Handler(Looper.getMainLooper())
    private val visiblePostTracker = mutableMapOf<String, Long>()
    private val viewedPosts = mutableSetOf<String>()
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

        setupTabs()
        setupRecyclerView()
        setupReels()
        loadPosts()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Posts
                        binding.rvCommunity.visibility = View.VISIBLE
                        binding.vpReels.visibility = View.GONE
                    }
                    1 -> { // Reels
                        binding.rvCommunity.visibility = View.GONE
                        binding.vpReels.visibility = View.VISIBLE
                        // Load reels if empty?
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupReels() {
        // Mock Reels Data
        val mockReels = listOf(
            Post(
                advisorName = "DanceChoreo",
                caption = "New moves! #dance",
                likesCount = 500,
                mediaUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                mediaType = "VIDEO",
                advisorProfileImage = "https://via.placeholder.com/150",
                category = "Lifestyle"
            ),
             Post(
                advisorName = "ChefMike",
                caption = "Best steak recipe 🥩",
                likesCount = 1200,
                mediaUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                mediaType = "VIDEO",
                advisorProfileImage = "https://via.placeholder.com/150",
                category = "Food"
            )
        )

        val reelsAdapter = com.example.associate.Adapters.ReelsAdapter(mockReels) {
            // Reel Click
        }
        binding.vpReels.adapter = reelsAdapter
        binding.vpReels.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL
    }

    private fun setupRecyclerView() {
        // Mock Data for Posts with Sentiment
        val mockPosts = listOf(
            Post(
                advisorName = "CA Rahul Sharma",
                caption = "The new tax regime changes for FY 2024-25 are here! 📊 If your income is above ₹15L...",
                likesCount = 2400,
                advisorProfileImage = "https://via.placeholder.com/150",
                mediaUrl = "https://via.placeholder.com/400x300",
                category = "Income Tax Specialist",
                marketSentiment = "BULLISH",
                advisorId = "1",
                commentsCount = 156
            ),
            Post(
                advisorName = "Ananya S.",
                caption = "BTC/INR testing major support at ₹54,00,000. Volume is picking up.",
                likesCount = 892,
                advisorProfileImage = "https://via.placeholder.com/150",
                mediaUrl = "https://via.placeholder.com/400x300",
                category = "Crypto Strategist",
                marketSentiment = "BEARISH",
                advisorId = "2",
                commentsCount = 45
            )
        )

        adapter = SocialPostAdapter(
            posts = mockPosts, // Passing mock data directly for demo
            onBookSessionClick = { advisorId ->
                val intent = Intent(requireContext(), AdvisorProfileActivity::class.java)
                intent.putExtra("advisorId", advisorId)
                intent.putExtra("openBooking", true) 
                startActivity(intent)
            },
            onPostClick = { post -> },
            onLikeClick = { post ->
                post.isLiked = !post.isLiked
                adapter.notifyDataSetChanged()
            },
            onCommentClick = { post ->
                showCommentsBottomSheet(post)
            }
        )

        binding.rvCommunity.layoutManager = LinearLayoutManager(context)
        binding.rvCommunity.adapter = adapter
    }

    private fun showCommentsBottomSheet(post: Post) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(com.example.associate.R.layout.bottom_sheet_comments, null)
        dialog.setContentView(view)

        val comments = mutableListOf(
            com.example.associate.DataClass.Comment("c1", post.postId, "u1", "alice", "", "Great post!"),
            com.example.associate.DataClass.Comment("c2", post.postId, "u2", "bob", "", "I agree with this.")
        )

        val rvComments = view.findViewById<RecyclerView>(com.example.associate.R.id.rvComments)
        val etInput = view.findViewById<android.widget.EditText>(com.example.associate.R.id.etCommentInput)
        val btnSend = view.findViewById<android.view.View>(com.example.associate.R.id.btnPostComment)
        
        val commentAdapter = com.example.associate.Adapters.CommentAdapter(
            comments, 
            onReplyClick = { comment ->
                etInput.setText("@${comment.username} ")
                etInput.setSelection(etInput.text.length)
                etInput.requestFocus()
            },
            onLikeClick = { comment -> }
        )
        rvComments.layoutManager = LinearLayoutManager(requireContext())
        rvComments.adapter = commentAdapter

        btnSend.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                comments.add(com.example.associate.DataClass.Comment(
                    java.util.UUID.randomUUID().toString(),
                    post.postId,
                    "me", "Me", "", text
                ))
                commentAdapter.notifyItemInserted(comments.size - 1)
                rvComments.smoothScrollToPosition(comments.size - 1)
                etInput.setText("")
                
                // Update Post Comment Count UI
                post.commentsCount += 1
                adapter.notifyDataSetChanged()
            }
        }
        dialog.show()
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

        val setIterator = visiblePostTracker.iterator()
        while (setIterator.hasNext()) {
            val entry = setIterator.next()
            if (!currentVisiblePosts.contains(entry.key)) {
                setIterator.remove()
            }
        }
    }

    private fun isViewVisible(view: View): Boolean {
        if (binding.rvCommunity.visibility != View.VISIBLE) return false
        val rect = Rect()
        val isVisible = view.getGlobalVisibleRect(rect)
        if (!isVisible) return false

        val height = view.height
        val visibleHeight = rect.height()
        return visibleHeight >= height * 0.5 
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CommunityFragment()
    }
}
