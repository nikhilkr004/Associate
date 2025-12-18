package com.example.associate.Adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.UserData
import com.example.associate.DataClass.VideoCall
import com.example.associate.R
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryAdapter(
    private var videoCallList: List<Pair<VideoCall, UserData?>> = emptyList()
) : RecyclerView.Adapter<CallHistoryAdapter.VideoCallViewHolder>() {

    inner class VideoCallViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val advisorImage: CircleImageView = itemView.findViewById(R.id.advisorImage)
        private val advisorName: TextView = itemView.findViewById(R.id.advisorName)
        private val callDate: TextView = itemView.findViewById(R.id.callDate)
        private val callDuration: TextView = itemView.findViewById(R.id.callDuration)
        private val callAmount: TextView = itemView.findViewById(R.id.callAmount)
        private val callStatus: TextView = itemView.findViewById(R.id.callStatus)
        private val professionalTitle: TextView = itemView.findViewById(R.id.professionalTitle)

        fun bind(videoCall: VideoCall, user: UserData?) {
            // User details
            if (user != null) {
                advisorName.text = user.name.toString()
                professionalTitle.text = user.phone.ifEmpty { user.email }

                if (!user.profilePhotoUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(user.profilePhotoUrl)
                        .placeholder(R.drawable.user)
                        .into(advisorImage)
                }
            } else {
               Log.d("LOG","user is empty ")
                advisorName.text = "Unknown User"
                professionalTitle.text = "N/A"
                advisorImage.setImageResource(R.drawable.user)
            }

            // Call date
            videoCall.callStartTime?.toDate()?.let { date ->
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                callDate.text = dateFormat.format(date)
            }

            // Call duration
            val duration = calculateCallDuration(videoCall)
            callDuration.text = duration

            // Amount
            callAmount.text = "₹${videoCall.totalAmount}"

            // Status with color
            callStatus.text = videoCall.status.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }

            // Set status color
            when (videoCall.status.lowercase()) {
                "completed", "ended" -> callStatus.setTextColor(itemView.context.getColor(R.color.green))
                "ongoing" -> callStatus.setTextColor(itemView.context.getColor(R.color.orange))
                "initiated" -> callStatus.setTextColor(itemView.context.getColor(R.color.blue))
                else -> callStatus.setTextColor(itemView.context.getColor(R.color.gray))
            }
        }

        private fun calculateCallDuration(videoCall: VideoCall): String {
            return if (videoCall.callStartTime != null && videoCall.callEndTime != null) {
                val start = videoCall.callStartTime.toDate().time
                val end = videoCall.callEndTime.toDate().time
                val durationMillis = end - start
                val minutes = durationMillis / (1000 * 60)
                val seconds = (durationMillis % (1000 * 60)) / 1000
                "${minutes}m ${seconds}s"
            } else {
                "N/A"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoCallViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.call_history_item, parent, false)
        return VideoCallViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoCallViewHolder, position: Int) {
        val (videoCall, user) = videoCallList[position]
        holder.bind(videoCall, user)
    }

    override fun getItemCount(): Int = videoCallList.size

    fun updateList(newList: List<Pair<VideoCall, UserData?>>) {
        videoCallList = newList
        notifyDataSetChanged()
    }

}