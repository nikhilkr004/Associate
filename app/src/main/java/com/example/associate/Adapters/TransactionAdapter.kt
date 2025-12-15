package com.example.associate.Adapters

import com.example.associate.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.payment_history_item, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        holder.transactionType.text = transaction.type
        holder.amountText.text = transaction.amount
        holder.dateText.text = transaction.date
        holder.transactionIdText.text = "TriD. ${transaction.transactionId}"
        holder.statusText.text = transaction.status

        // Amount color based on type
        if (transaction.amount.startsWith("-") || transaction.type == "Video Call Payment") {
            holder.amountText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
        } else {
            holder.amountText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.green))
        }

        // Status color
        when (transaction.status) {
            "Completed" -> holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.green))
            "Pending" -> holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.orange))
            "Debited" -> holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
            "Failed" -> holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
            else -> holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
        }
    }

    override fun getItemCount(): Int = transactions.size
}