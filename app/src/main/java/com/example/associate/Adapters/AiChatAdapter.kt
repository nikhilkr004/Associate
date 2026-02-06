package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.DataClass.AiChatMessage
import com.example.associate.R
import io.noties.markwon.Markwon


class AiChatAdapter(private val messages: ArrayList<AiChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_USER = 1
    private val VIEW_TYPE_BOT = 2
    private val VIEW_TYPE_LOADING = 3
    private val VIEW_TYPE_ADVISOR_LIST = 4

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
    }

    inner class BotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvBotMessage)
    }
    
    inner class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    
    // New ViewHolder for Advisor List
    inner class AdvisorListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rvAdvisors: RecyclerView = itemView.findViewById(R.id.rvAdvisorList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_chat_user, parent, false)
                UserViewHolder(view)
            }
            VIEW_TYPE_BOT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_chat_bot, parent, false)
                BotViewHolder(view)
            }
            VIEW_TYPE_ADVISOR_LIST -> {
                // We need a container layout for the horizontal RecyclerView
                // For simplicity, reusing a basic Recyclerview item or creating a new frame
                // Note: We need a layout file for this list item
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_advisor_list, parent, false)
                AdvisorListViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_chat_loading, parent, false)
                LoadingViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        
        // Markdown Support
        val markwon = Markwon.create(holder.itemView.context)

        if (holder is UserViewHolder) {
            holder.tvMessage.text = msg.message
        } else if (holder is BotViewHolder) {
            markwon.setMarkdown(holder.tvMessage, msg.message)
        } else if (holder is AdvisorListViewHolder) {
            msg.advisors?.let { advisors ->
                val advisorAdapter = AiAdvisorAdapter(advisors)
                holder.rvAdvisors.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    holder.itemView.context, 
                    androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, 
                    false
                )
                holder.rvAdvisors.adapter = advisorAdapter
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (messages[position].isLoading) return VIEW_TYPE_LOADING
        if (messages[position].advisors != null) return VIEW_TYPE_ADVISOR_LIST
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    override fun getItemCount(): Int = messages.size
}
