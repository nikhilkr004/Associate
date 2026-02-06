package com.example.associate.Activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
// import com.example.associate.Adapters.CommunityAdapter (Using SocialPostAdapter)
import com.example.associate.Adapters.ReelsAdapter
import com.example.associate.DataClass.CommunityPost
import com.example.associate.DataClass.PostType
import com.example.associate.databinding.ActivityCommunityBinding
import com.google.android.material.tabs.TabLayout

class CommunityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunityBinding
    
    // Mock Data using Post (shared model)
    private val mockPosts = listOf(
        com.example.associate.DataClass.Post(
            advisorName = "creative_designer", 
            caption = "Check out my new design! #art #design", 
            likesCount = 120, 
            mediaType = "IMAGE",
            category = "Design",
            marketSentiment = "NEUTRAL"
        ),
        com.example.associate.DataClass.Post(
            advisorName = "travel_addict", 
            caption = "Sunset in Bali 🌅", 
            likesCount = 3400, 
            mediaType = "IMAGE",
            category = "Travel",
             marketSentiment = "BULLISH"
        )
    )

    private val mockReels = listOf(
        com.example.associate.DataClass.Post(
            postId = "r1", 
            advisorName = "dance_star", 
            mediaUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", 
            caption = "Learning new moves! 💃", 
            likesCount = 500, 
            mediaType = "VIDEO",
            category = "Dance"
        ),
        com.example.associate.DataClass.Post(
            postId = "r2", 
            advisorName = "chef_mike", 
            mediaUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", 
            caption = "Perfect steak recipe 🥩", 
            likesCount = 12000, 
            mediaType = "VIDEO",
            category = "Cooking"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupFeed()
        setupReels()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showFeed() // Posts
                    1 -> showReels() // Reels
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showFeed() {
        binding.rvCommunityFeed.visibility = android.view.View.VISIBLE
        binding.viewPagerReels.visibility = android.view.View.GONE
    }

    private fun showReels() {
        binding.rvCommunityFeed.visibility = android.view.View.GONE
        binding.viewPagerReels.visibility = android.view.View.VISIBLE
    }

    private fun setupFeed() {
        val adapter = com.example.associate.Adapters.SocialPostAdapter(
            posts = mockPosts,
            onBookSessionClick = { 
                // Handle Book
            },
            onPostClick = { 
                // Handle Post
            },
            onLikeClick = { post ->
                 post.isLiked = !post.isLiked
                 binding.rvCommunityFeed.adapter?.notifyDataSetChanged()
            },
            onCommentClick = { post ->
                // Toast or Fragment
            }
        )
        binding.rvCommunityFeed.layoutManager = LinearLayoutManager(this)
        binding.rvCommunityFeed.adapter = adapter
    }

    private fun setupReels() {
        val adapter = ReelsAdapter(mockReels) { reel ->
            // Interaction
        }
        binding.viewPagerReels.adapter = adapter
        binding.viewPagerReels.orientation = ViewPager2.ORIENTATION_VERTICAL
    }
}
