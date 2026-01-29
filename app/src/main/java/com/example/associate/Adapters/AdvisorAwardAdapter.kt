package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.Award
import com.example.associate.R
import com.example.associate.databinding.ItemAwardBinding

class AdvisorAwardAdapter(private val awards: List<Award>) :
    RecyclerView.Adapter<AdvisorAwardAdapter.AwardViewHolder>() {

    class AwardViewHolder(val binding: ItemAwardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AwardViewHolder {
        val binding = ItemAwardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AwardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AwardViewHolder, position: Int) {
        val award = awards[position]
        holder.binding.tvAwardTitle.text = award.title
        holder.binding.tvAwardIssuer.text = award.issuer
        holder.binding.tvAwardDate.text = award.date

        Glide.with(holder.itemView.context)
            .load(award.url)
            .placeholder(R.drawable.portfolio) 
            .error(R.drawable.portfolio)
            .into(holder.binding.imgAward)
            
        // Optional: Open image in full screen or show details on click?
        // For now, just display.
    }

    override fun getItemCount() = awards.size
}
