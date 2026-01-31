package com.example.associate.Adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.Post
import com.example.associate.R
import de.hdodenhof.circleimageview.CircleImageView

class CommunityAdapter(
    private val onProfileClick: (String) -> Unit,
    private val onBookSessionClick: (String) -> Unit,
    private val onPostVisible: (String) -> Unit
) : ListAdapter<Post, CommunityAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_community_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)
        onPostVisible(post.postId)
    }

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAdvisorProfile: CircleImageView = itemView.findViewById(R.id.ivAdvisorProfile)
        private val tvAdvisorName: TextView = itemView.findViewById(R.id.tvAdvisorName)
        private val ivPostImage: ImageView = itemView.findViewById(R.id.ivPostImage) // Single Image
        private val viewPagerMedia: androidx.viewpager2.widget.ViewPager2 = itemView.findViewById(R.id.viewPagerMedia) // Carousel
        private val tabLayoutIndicator: com.google.android.material.tabs.TabLayout = itemView.findViewById(R.id.tabLayoutIndicator) // Indicators
        private val playerView: PlayerView = itemView.findViewById(R.id.playerView)
        private val tvCaption: TextView = itemView.findViewById(R.id.tvCaption)
        private val tvLikes: TextView = itemView.findViewById(R.id.tvLikes)
        private val tvViews: TextView = itemView.findViewById(R.id.tvViews)
        private val btnBookSession: View = itemView.findViewById(R.id.btnBookSession)
        
        private var player: ExoPlayer? = null
        private val context: Context = itemView.context

        fun bind(post: Post) {
            tvAdvisorName.text = post.advisorName
            tvCaption.text = post.caption
            tvLikes.text = "${post.likesCount} Likes"
            tvViews.text = "${post.viewCount} Views"

            Glide.with(context).load(post.advisorProfileImage).into(ivAdvisorProfile)

            // Click Listeners
            val headerClickListener = View.OnClickListener { onProfileClick(post.advisorId) }
            ivAdvisorProfile.setOnClickListener(headerClickListener)
            tvAdvisorName.setOnClickListener(headerClickListener)
            
            btnBookSession.setOnClickListener { onBookSessionClick(post.advisorId) }

            // Reset visibilities
            ivPostImage.visibility = View.GONE
            viewPagerMedia.visibility = View.GONE
            tabLayoutIndicator.visibility = View.GONE
            playerView.visibility = View.GONE
            releasePlayer()

            // MediaType Handling
            if (post.mediaType == "VIDEO") {
                playerView.visibility = View.VISIBLE
                initializePlayer(post.mediaUrl)
            } else {
                // Image Handling
                val images = post.mediaUrls
                if (!images.isNullOrEmpty() && images.size > 1) {
                    // Show Carousel
                    viewPagerMedia.visibility = View.VISIBLE
                    tabLayoutIndicator.visibility = View.VISIBLE
                    
                    val sliderAdapter = ImageSliderAdapter(images)
                    viewPagerMedia.adapter = sliderAdapter
                    
                    // Attach TabLayout to ViewPager2
                    com.google.android.material.tabs.TabLayoutMediator(tabLayoutIndicator, viewPagerMedia) { _, _ ->
                        // No text, just dots
                    }.attach()
                    
                } else {
                    // Show Single Image (fallback to mediaUrl if mediaUrls is empty/single)
                    ivPostImage.visibility = View.VISIBLE
                    val urlToLoad = if (!images.isNullOrEmpty()) images[0] else post.mediaUrl
                    Glide.with(context).load(urlToLoad).into(ivPostImage)
                }
            }
        }

        private fun initializePlayer(url: String) {
            if (player == null) {
                player = ExoPlayer.Builder(context).build()
                playerView.player = player
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.playWhenReady = false // Auto-play handled by scroll listener logic ideally, but false by default to save resources
            }
        }

        fun releasePlayer() {
            player?.release()
            player = null
            playerView.player = null
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.postId == newItem.postId
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
