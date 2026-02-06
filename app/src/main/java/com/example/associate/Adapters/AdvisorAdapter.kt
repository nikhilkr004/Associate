package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.R
import com.example.associate.databinding.AdvisorDialogBinding

class AdvisorAdapter(
    private var advisors: MutableList<AdvisorDataClass>,
    private val onItemClick: (AdvisorDataClass) -> Unit
) : RecyclerView.Adapter<AdvisorAdapter.AdvisorViewHolder>() {

    fun updateList(newAdvisors: List<AdvisorDataClass>) {
        advisors.clear()
        advisors.addAll(newAdvisors)
        notifyDataSetChanged()
    }

    inner class AdvisorViewHolder(private val binding: AdvisorDialogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(advisor: AdvisorDataClass) {
            val context = binding.root.context
            
            // Name
            binding.advisorName.text = advisor.basicInfo.name
            
            // Subtitle: "Specialization • Exp years Exp"
            val spec = advisor.professionalInfo.specializations.firstOrNull() ?: "Advisor"
            val exp = advisor.professionalInfo.experience
            binding.advisorExp.text = "$spec • $exp years Exp"
            
            // Rating
            val rating = advisor.performanceInfo.rating
            binding.advisorRating.text = String.format("%.1f", rating)
            
            // Fee
            val fee = advisor.pricingInfo.instantVideoFee
            binding.advisorPrice.text = "₹ $fee"
            
            // Availability Status
            // FORCED: Always show "Available Now" as per user request
            binding.icOnlineStatus.visibility = android.view.View.VISIBLE
            binding.tvAvailabilityLabel.text = "• Available Now"
            binding.tvAvailabilityLabel.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Green

            // Image
            Glide.with(context)
                .load(advisor.basicInfo.profileImage)
                .placeholder(R.drawable.user)
                .into(binding.profileImage)
            
            // Clicks
            binding.root.setOnClickListener { onItemClick(advisor) }
            binding.btnBookAppointment.setOnClickListener { onItemClick(advisor) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdvisorViewHolder {
        val binding = AdvisorDialogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdvisorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdvisorViewHolder, position: Int) {
        holder.bind(advisors[position])
    }

    override fun getItemCount(): Int = advisors.size
}
