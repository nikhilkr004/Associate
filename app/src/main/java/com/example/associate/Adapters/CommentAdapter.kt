package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.Comment
import com.example.associate.R

class CommentAdapter(
    private val comments: List<Comment>,
    private val onReplyClick: (Comment) -> Unit,
    private val onLikeClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgCommentAvatar)
        val tvUsername: TextView = itemView.findViewById(R.id.tvCommentUsername)
        val tvText: TextView = itemView.findViewById(R.id.tvCommentText)
        val tvTime: TextView = itemView.findViewById(R.id.tvCommentTime)
        val btnReply: TextView = itemView.findViewById(R.id.btnReply)
        val btnLike: ImageView = itemView.findViewById(R.id.btnCommentLike)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]

        holder.tvUsername.text = comment.username
        holder.tvText.text = comment.text
        holder.tvTime.text = "Now" // Placeholder or use timestamp logic

        Glide.with(holder.itemView.context)
            .load(comment.userAvatar)
            .placeholder(R.drawable.ic_launcher_background)
            .circleCrop()
            .into(holder.imgAvatar)

        holder.btnReply.setOnClickListener { onReplyClick(comment) }
        holder.btnLike.setOnClickListener { onLikeClick(comment) }
        
        // Handle Replied State or Nested Replies
        // For simple reply logic, we just trigger callback with the username
    }

    override fun getItemCount(): Int = comments.size
}
