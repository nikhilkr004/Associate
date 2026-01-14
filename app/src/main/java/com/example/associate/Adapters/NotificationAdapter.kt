package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.NotificationDataClass
import com.example.associate.R
import com.example.associate.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var list: MutableList<NotificationDataClass>,
    private val onItemClick: (NotificationDataClass) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = list[position]
        
        // Hide Title if empty, use message as main text
        // Ideally we might want Title + Subtitle, but design shows "Message" as main text
        // Let's assume 'title' is internal or optional, and 'message' is what we show
        
        holder.binding.tvMessage.text = notification.message
        
        // Format Time
        val timeDiff = System.currentTimeMillis() - notification.timestamp.toDate().time
        holder.binding.tvTime.text = getTimeAgo(timeDiff)

        // Icon
        if (notification.iconUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(notification.iconUrl)
                .placeholder(R.drawable.user)
                .error(R.drawable.user)
                .into(holder.binding.ivIcon)
        } else {
             holder.binding.ivIcon.setImageResource(R.drawable.user)
        }

        // Unread Indicator
        if (!notification.isRead) {
            holder.binding.ivStatusIndicator.visibility = View.VISIBLE
            // Use blue for general, maybe orange for warnings?
            // For now default blue
        } else {
            holder.binding.ivStatusIndicator.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(notification)
        }
    }

    fun updateList(newList: List<NotificationDataClass>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
    
    private fun getTimeAgo(diffMillis: Long): String {
        val second = 1000L
        val minute = 60 * second
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diffMillis < minute -> "${diffMillis / second}s ago"
            diffMillis < hour -> "${diffMillis / minute}m ago"
            diffMillis < day -> "${diffMillis / hour}h ago"
            else -> {
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                sdf.format(Date(System.currentTimeMillis() - diffMillis))
            }
        }
    }
}

// Updated for repository activity
