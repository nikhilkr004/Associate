package com.example.associate.Adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.R
import com.example.associate.Repositories.AiChatRepository.ChatHistoryItem
import java.util.Calendar
import java.util.Locale

class ChatHistoryAdapter(
    private val historyList: List<ChatHistoryItem>,
    private val onItemClick: (ChatHistoryItem) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuery: TextView = itemView.findViewById(R.id.tvHistoryQuery)
        val tvResponse: TextView = itemView.findViewById(R.id.tvHistoryResponse)
        val tvDate: TextView = itemView.findViewById(R.id.tvHistoryDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.tvQuery.text = item.query
        holder.tvResponse.text = item.response

        // Format Date
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = item.timestamp
        val date = DateFormat.format("MMM dd, hh:mm a", cal).toString()
        holder.tvDate.text = date

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = historyList.size
}
