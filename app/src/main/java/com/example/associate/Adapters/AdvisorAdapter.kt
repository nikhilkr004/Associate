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
            val context=binding.root.context
            binding.advisorName.text = advisor.name
//            binding.userCity.text = advisor.city
            binding.advisorExp.text = "${advisor.experience} years experience"
            binding.advisorSpec.text = advisor.specializations.joinToString(", ")
            binding.advisorLang.text = advisor.languages.joinToString(", ")
            Glide.with(context).load(advisor.profileimage).placeholder(R.drawable.user).into(binding.profileImage)
            binding.root.setOnClickListener { onItemClick(advisor) }
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