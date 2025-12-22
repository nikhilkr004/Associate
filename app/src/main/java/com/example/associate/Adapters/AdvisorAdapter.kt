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
            binding.advisorName.text = advisor.basicInfo.name
            
            // Subtitle: "Specialization(Exp years Exp)"
            val spec = advisor.professionalInfo.specializations.firstOrNull() ?: "Advisor"
            val exp = advisor.professionalInfo.experience
            binding.advisorExp.text = "$spec($exp years Exp)"
            
            // Rating
            binding.advisorRating.text = String.format("%.1f", advisor.performanceInfo.rating)
            
            // Price (Using Scheduled Video Fee as representative or instant)
            // Ideally display range or 'starts from' but fulfilling design "₹ 180"
            val price = advisor.pricingInfo.instantVideoFee 
            binding.advisorPrice.text = "₹ $price"
            
            // Availability (Mocking 'Available Now' based on status or instant availability)
            // 'isactive' is a String "true"/"false" in database
            val isOnline = advisor.basicInfo.isactive == "true"
            
            // Set online indicator visibility
            binding.icOnlineStatus.visibility = if (isOnline) {
                android.view.View.VISIBLE 
            } else {
                 android.view.View.GONE
            }

            // If instant video is enabled -> Show "Video Consult" green
            // Else -> maybe hide or show "Scheduled"
            // For now, mirroring image style static or simple dynamic
            if (advisor.availabilityInfo.instantAvailability.isVideoCallEnabled) {
               binding.tvServiceType.text = "Video Consult"
               binding.tvAvailabilityLabel.text = "Available Now"
               binding.tvAvailabilityLabel.setTextColor(android.graphics.Color.parseColor("#191C1F"))
            } else {
               binding.tvServiceType.text = "Schedule Only"
               binding.tvAvailabilityLabel.text = "Busy"
               binding.tvAvailabilityLabel.setTextColor(android.graphics.Color.GRAY)
            }
            
            Glide.with(context).load(advisor.basicInfo.profileImage).placeholder(R.drawable.user).into(binding.profileImage)
            
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