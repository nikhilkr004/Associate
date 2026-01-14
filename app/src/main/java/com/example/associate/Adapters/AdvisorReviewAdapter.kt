package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.ReviewDisplayModel
import com.example.associate.R
import java.text.SimpleDateFormat
import java.util.Locale

class AdvisorReviewAdapter(
    private val reviews: List<ReviewDisplayModel>
) : RecyclerView.Adapter<AdvisorReviewAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivReviewerImage: ImageView = itemView.findViewById(R.id.ivReviewerImage)
        val tvReviewerName: TextView = itemView.findViewById(R.id.tvReviewerName)
        val tvReviewDate: TextView = itemView.findViewById(R.id.tvReviewDate)
        val rbReviewRating: RatingBar = itemView.findViewById(R.id.rbReviewRating)
        val tvReviewText: TextView = itemView.findViewById(R.id.tvReviewText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_advisor_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        holder.tvReviewerName.text = review.reviewerName
        holder.tvReviewText.text = review.review
        holder.rbReviewRating.rating = review.rating

        // Date Formatting
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        holder.tvReviewDate.text = sdf.format(review.timestamp.toDate())

        // Load Avatar
        Glide.with(holder.itemView.context)
            .load(review.reviewerImage)
            .placeholder(R.drawable.user)
            .into(holder.ivReviewerImage)
    }

    override fun getItemCount(): Int = reviews.size
}

// Updated for repository activity
