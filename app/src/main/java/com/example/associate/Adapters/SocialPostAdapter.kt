package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.Post
import com.example.associate.R
import com.example.associate.DataClass.CommunityPost // Keep for compatibility if needed, but we switch to Post

class SocialPostAdapter(
    private var posts: List<Post> = emptyList(), 
    private val onBookSessionClick: (String) -> Unit,
    private val onPostClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit, // New
    private val onCommentClick: (Post) -> Unit // New
) : RecyclerView.Adapter<SocialPostAdapter.PostViewHolder>() {

    val currentList: List<Post>
        get() = posts

    fun submitList(newPosts: List<Post>) {
        posts = newPosts
        notifyDataSetChanged()
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgPostAvatar)
        val tvUsername: TextView = itemView.findViewById(R.id.tvPostUsername)
        val imgMedia: ImageView = itemView.findViewById(R.id.imgPostMedia)
        val tvLikes: TextView = itemView.findViewById(R.id.tvPostLikes)
        val tvComments: TextView = itemView.findViewById(R.id.tvPostComments) // New binding
        val tvCaption: TextView = itemView.findViewById(R.id.tvPostCaption)
        val tvCategory: TextView = itemView.findViewById(R.id.tvPostCategory) // New
        val tvSentiment: TextView = itemView.findViewById(R.id.tvSentiment) // New
        // Buttons
        val btnLike: ImageButton = itemView.findViewById(R.id.btnPostLike)
        val btnComment: ImageButton = itemView.findViewById(R.id.btnPostComment)
        val btnBook: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnBookSession)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_social_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]

        holder.tvUsername.text = post.advisorName
        holder.tvCaption.text = post.caption
        holder.tvLikes.text = "${post.likesCount}" 
        holder.tvComments.text = "${post.commentsCount}" // Bind Comment Count
        
        // Category
        if (post.category.isNotEmpty()) {
            holder.tvCategory.text = "${post.category} • Now" // Mock time
            holder.tvCategory.visibility = View.VISIBLE
        } else {
             holder.tvCategory.visibility = View.GONE
        }

        // Sentiment Tag
        if (post.marketSentiment.isNotEmpty()) {
            holder.tvSentiment.text = post.marketSentiment
            holder.tvSentiment.visibility = View.VISIBLE
            
            // Color Logic
            val bg = holder.tvSentiment.background as android.graphics.drawable.GradientDrawable
            if (post.marketSentiment.equals("BEARISH", ignoreCase = true)) {
                bg.setColor(android.graphics.Color.parseColor("#E53935")) // Red
            } else {
                bg.setColor(android.graphics.Color.parseColor("#43A047")) // Green
            }
        } else {
            holder.tvSentiment.visibility = View.GONE
        }

        // Update Like Icon based on state
        if (post.isLiked) {
            holder.btnLike.setImageResource(R.drawable.heart) 
            holder.btnLike.setColorFilter(android.graphics.Color.RED)
        } else {
            holder.btnLike.setImageResource(R.drawable.heart)
            holder.btnLike.setColorFilter(android.graphics.Color.WHITE) // White for dark card
        }

        Glide.with(holder.itemView.context)
            .load(post.advisorProfileImage)
            .placeholder(R.drawable.ic_launcher_background)
            .circleCrop()
            .into(holder.imgAvatar)

        // Load Media
        val mediaUrl = if (post.mediaUrls.isNotEmpty()) post.mediaUrls[0] else post.mediaUrl
        Glide.with(holder.itemView.context)
            .load(mediaUrl)
            .placeholder(android.R.color.darker_gray)
            .into(holder.imgMedia)

        // Listeners
        // Listeners
        holder.itemView.setOnClickListener { onPostClick(post) }
        
        holder.btnBook.setOnClickListener { onBookSessionClick(post.advisorId) }
        
        holder.btnLike.setOnClickListener { 
            // Optimistic UI Update in Adapter? Or rely on Callback?
            // User requested "count increse on that". 
            // We can update the text view directly here for instant feedback, 
            // but the Fragment/Callback should handle the data persistence.
            // Let's do a simple visual toggle here + callback.
            val currentCount = post.likesCount
            if (post.isLiked) {
                // Unlike
                post.isLiked = false
                post.likesCount = if (currentCount > 0) currentCount - 1 else 0 // Prevent negative
                holder.btnLike.setImageResource(R.drawable.heart)
                holder.btnLike.setColorFilter(android.graphics.Color.WHITE)
            } else {
                // Like
                post.isLiked = true
                post.likesCount = currentCount + 1
                holder.btnLike.setImageResource(R.drawable.heart)
                holder.btnLike.setColorFilter(android.graphics.Color.RED)
            }
            holder.tvLikes.text = "${post.likesCount}" 
            onLikeClick(post) // Propagate to parent
        }
        
        holder.btnComment.setOnClickListener { onCommentClick(post) }
    }

    override fun getItemCount(): Int = posts.size
}
