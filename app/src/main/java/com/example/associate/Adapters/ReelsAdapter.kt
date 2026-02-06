package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.Post
import com.example.associate.R

class ReelsAdapter(
    private val reels: List<Post>,
    private val onReelClick: (Post) -> Unit
) : RecyclerView.Adapter<ReelsAdapter.ReelViewHolder>() {

    inner class ReelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playerView: PlayerView = itemView.findViewById(R.id.playerView)
        val tvUsername: TextView = itemView.findViewById(R.id.tvReelUsername)
        val tvCaption: TextView = itemView.findViewById(R.id.tvReelCaption)
        val tvLikes: TextView = itemView.findViewById(R.id.tvReelLikes)
        val tvComments: TextView = itemView.findViewById(R.id.tvReelComments)
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgReelAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reel_full_screen, parent, false)
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return ReelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
        val reel = reels[position]

        holder.tvUsername.text = reel.advisorName
        holder.tvCaption.text = reel.caption
        holder.tvLikes.text = "${reel.likesCount}"
        holder.tvComments.text = "0" // Comments count not yet in Post for Reels explicitly

        Glide.with(holder.itemView.context)
            .load(reel.advisorProfileImage)
            .circleCrop()
            .into(holder.imgAvatar)

        // Video setup would go here (managed by Fragment/Activity ideally)
        holder.itemView.setOnClickListener { onReelClick(reel) }
    }

    override fun getItemCount(): Int = reels.size
}
