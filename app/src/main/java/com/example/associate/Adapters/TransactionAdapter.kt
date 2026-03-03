package com.example.associate.Adapters

import com.example.associate.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.DataClass.Transaction

class TransactionAdapter(private val transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val transactionType: TextView = itemView.findViewById(R.id.transaction_type)
        val amountText: TextView = itemView.findViewById(R.id.amountText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val transactionIdText: TextView = itemView.findViewById(R.id.transactionIdText)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val itemIcon: ImageView = itemView.findViewById(R.id.item_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.payment_history_item, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        val ctx = holder.itemView.context

        holder.transactionType.text = transaction.type
        holder.amountText.text = transaction.amount
        holder.dateText.text = transaction.date

        // Format ID nicely
        val rawId = transaction.transactionId.take(6).ifEmpty { "N/A" }
        holder.transactionIdText.text = "ID: #C-$rawId"

        holder.statusText.text = transaction.status.uppercase()

        // Icon by transaction type
        val iconRes = when {
            transaction.type.contains("Top-up", ignoreCase = true) ||
            transaction.type.contains("topup", ignoreCase = true) -> R.drawable.history
            transaction.type.contains("Video", ignoreCase = true) ||
            transaction.type.contains("Audio", ignoreCase = true) ||
            transaction.type.contains("Call", ignoreCase = true) -> R.drawable.session_history
            transaction.type.contains("Chat", ignoreCase = true) -> R.drawable.chat_bubble
            else -> R.drawable.transaction
        }
        holder.itemIcon.setImageResource(iconRes)

        // Amount color: green for credit, dark for debit
        if (transaction.amount.startsWith("+")) {
            holder.amountText.setTextColor(android.graphics.Color.parseColor("#10B981"))
        } else {
            holder.amountText.setTextColor(android.graphics.Color.parseColor("#111827"))
        }

        // Status badge: background + text color
        when (transaction.status.lowercase()) {
            "completed", "credited" -> {
                holder.statusText.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.statusText.background = ContextCompat.getDrawable(ctx, R.drawable.badge_bg_green)
            }
            "pending", "reviewing" -> {
                holder.statusText.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                holder.statusText.background = ContextCompat.getDrawable(ctx, R.drawable.badge_bg_orange)
            }
            "debited", "failed" -> {
                holder.statusText.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                holder.statusText.background = ContextCompat.getDrawable(ctx, R.drawable.badge_bg_red)
            }
            else -> {
                holder.statusText.setTextColor(android.graphics.Color.parseColor("#6B7280"))
                holder.statusText.background = null
            }
        }
    }

    override fun getItemCount(): Int = transactions.size
}
// Updated for repository activity
