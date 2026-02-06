package com.example.associate.Adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.Activities.AdvisorProfileActivity
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.R
import de.hdodenhof.circleimageview.CircleImageView

class AiAdvisorAdapter(private val advisors: List<AdvisorDataClass>) :
    RecyclerView.Adapter<AiAdvisorAdapter.AdvisorViewHolder>() {

    inner class AdvisorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProfile: CircleImageView = itemView.findViewById(R.id.imgAdvisorProfile)
        val tvName: TextView = itemView.findViewById(R.id.tvAdvisorName)
        val tvRole: TextView = itemView.findViewById(R.id.tvAdvisorRole)
        val tvRating: TextView = itemView.findViewById(R.id.tvAdvisorRating)
        val btnViewProfile: TextView = itemView.findViewById(R.id.tvViewProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdvisorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_advisor_card, parent, false)
        return AdvisorViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdvisorViewHolder, position: Int) {
        val advisor = advisors[position]

        holder.tvName.text = advisor.basicInfo.name
        holder.tvRole.text = advisor.getProfessionalTitle()
        holder.tvRating.text = advisor.performanceInfo.rating.toString()

        Glide.with(holder.itemView.context)
            .load(advisor.basicInfo.profileImage)
            .placeholder(R.drawable.user)
            .into(holder.imgProfile)

        holder.itemView.setOnClickListener {
            openAdvisorProfile(holder.itemView, advisor)
        }
        
        holder.btnViewProfile.setOnClickListener {
            openAdvisorProfile(holder.itemView, advisor)
        }
    }
    
    private fun openAdvisorProfile(view: View, advisor: AdvisorDataClass) {
        val intent = Intent(view.context, AdvisorProfileActivity::class.java)
        intent.putExtra("ADVISOR_DATA", advisor)
        view.context.startActivity(intent)
    }

    override fun getItemCount(): Int = advisors.size
}
