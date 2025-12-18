package com.example.associate.Adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.R
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String,
    private val attachmentList: ArrayList<HashMap<String, Any>>,
    private val onItemClick: (String, String) -> Unit, // url, type
    private val onItemLongClick: (String, String) -> Unit // messageId, senderId
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val msgContainer: LinearLayout = itemView.findViewById(R.id.msgContainer)
        val imgContent: ImageView = itemView.findViewById(R.id.imgContent)
        val docContainer: LinearLayout = itemView.findViewById(R.id.docContainer)
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvMessageContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val attachment = attachmentList[position]
        val messageId = attachment["id"] as? String ?: ""
        val senderId = attachment["senderId"] as? String ?: ""
        val type = attachment["type"] as? String ?: "text"
        val content = attachment["content"] as? String ?: "" // URL or Text
        val name = attachment["name"] as? String ?: ""
        val timestamp = attachment["timestamp"] as? Timestamp

        // Alignment & Color
        val params = holder.msgContainer.layoutParams as ConstraintLayout.LayoutParams
        if (senderId == currentUserId) {
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.UNSET
            holder.msgContainer.background.setTint(holder.itemView.context.getColor(R.color.white)) 
            holder.msgContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DCF8C6")) // WhatsApp User Green
        } else {
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET
            holder.msgContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F5F5F5"))
        }
        holder.msgContainer.layoutParams = params

        // Content
        holder.imgContent.visibility = View.GONE
        holder.docContainer.visibility = View.GONE
        holder.tvMessageContent.visibility = View.GONE

        when (type) {
            "image" -> {
                holder.imgContent.visibility = View.VISIBLE
                Glide.with(holder.itemView.context).load(content).into(holder.imgContent)
            }
            "document" -> {
                holder.docContainer.visibility = View.VISIBLE
                holder.tvFileName.text = name
            }
            else -> { // text
                holder.tvMessageContent.visibility = View.VISIBLE
                holder.tvMessageContent.text = content
            }
        }

        // Timestamp
        if (timestamp != null) {
            val date = timestamp.toDate()
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            holder.tvTimestamp.text = sdf.format(date)
        }

        // Listeners
        holder.itemView.setOnClickListener {
            if (type == "document" || type == "image") {
                onItemClick(content, type)
            }
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(messageId, senderId)
            true
        }
    }

    override fun getItemCount(): Int = attachmentList.size

    fun updateList(newList: List<HashMap<String, Any>>) {
        attachmentList.clear()
        attachmentList.addAll(newList)
        notifyDataSetChanged()
    }
}
