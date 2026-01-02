package com.example.associate.HelpSupport

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.R
import java.text.SimpleDateFormat
import java.util.Locale

class TicketsAdapter : RecyclerView.Adapter<TicketsAdapter.TicketViewHolder>() {

    private val tickets = mutableListOf<TicketDataClass>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

    fun setTickets(newTickets: List<TicketDataClass>) {
        tickets.clear()
        tickets.addAll(newTickets)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ticket, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = tickets[position]
        holder.bind(ticket)
    }

    override fun getItemCount(): Int = tickets.size

    inner class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSubject: TextView = itemView.findViewById(R.id.tvSubject)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvTicketId: TextView = itemView.findViewById(R.id.tvTicketId)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(ticket: TicketDataClass) {
            tvSubject.text = ticket.subject
            tvCategory.text = "Category: ${ticket.category}"
            tvTicketId.text = "ID: ${ticket.ticketId.take(8).uppercase()}" // Show first 8 chars or full
            
            val date = ticket.timestamp.toDate()
            tvDate.text = dateFormat.format(date)
            
            tvStatus.text = ticket.status.uppercase()
            
            // Status Color
            val color = when(ticket.status.uppercase()) {
                "OPEN" -> "#C67C4E" // Orange/Brown
                "CLOSED" -> "#4CAF50" // Green
                "IN_PROGRESS" -> "#2196F3" // Blue
                else -> "#757575" // Gray
            }
            tvStatus.setTextColor(Color.parseColor(color))
        }
    }
}
