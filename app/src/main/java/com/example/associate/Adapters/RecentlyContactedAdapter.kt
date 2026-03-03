package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.R
import com.example.associate.databinding.ItemRecentlyContactedBinding

data class RecentlyContactedItem(
    val advisorId: String,
    val name: String,
    val category: String,
    val profileImage: String
)

class RecentlyContactedAdapter(
    private val items: List<RecentlyContactedItem>,
    private val onPayNowClick: (RecentlyContactedItem) -> Unit
) : RecyclerView.Adapter<RecentlyContactedAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemRecentlyContactedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentlyContactedItem) {
            binding.rcName.text = item.name
            binding.rcCategory.text = item.category.uppercase()

            if (item.profileImage.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .load(item.profileImage)
                    .placeholder(R.drawable.circle_bg_green_light)
                    .circleCrop()
                    .into(binding.rcAvatar)
            }

            // Color badge based on category
            val badgeColor = when (item.category.lowercase()) {
                "legal" -> 0xFFFF8C00.toInt()
                "tech", "technology" -> 0xFF4A90D9.toInt()
                "health", "medical" -> 0xFFE91E63.toInt()
                "finance" -> 0xFF4CAF50.toInt()
                else -> 0xFF888888.toInt()
            }
            binding.rcCategory.setTextColor(badgeColor)

            binding.rcPayNow.setOnClickListener { onPayNowClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentlyContactedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
