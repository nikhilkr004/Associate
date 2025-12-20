package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.R
import com.example.associate.databinding.ItemTimeSlotBinding

class TimeSlotAdapter(
    private val onSlotSelected: (String) -> Unit
) : RecyclerView.Adapter<TimeSlotAdapter.ViewHolder>() {

    private var slots = listOf<String>()
    private var selectedSlot: String? = null

    fun submitList(newSlots: List<String>) {
        slots = newSlots
        notifyDataSetChanged()
    }
    
    fun setSelectedSlot(slot: String?) {
        selectedSlot = slot
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemTimeSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(slot: String) {
            binding.tvTimeSlot.text = slot
            
            val isSelected = slot == selectedSlot
            binding.tvTimeSlot.isSelected = isSelected
            
            binding.root.setOnClickListener {
                onSlotSelected(slot)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimeSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(slots[position])
    }

    override fun getItemCount() = slots.size
}
