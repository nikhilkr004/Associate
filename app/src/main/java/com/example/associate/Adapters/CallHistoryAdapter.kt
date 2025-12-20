package com.example.associate.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.DataClass.SessionBookingDataClass
import com.example.associate.DataClass.UserData
import com.example.associate.R
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryAdapter(
    private var bookingList: List<Pair<SessionBookingDataClass, UserData?>> = emptyList(),
    private val onActionClick: (CallHistoryAction, SessionBookingDataClass) -> Unit
) : RecyclerView.Adapter<CallHistoryAdapter.BookingViewHolder>() {

    enum class CallHistoryAction {
        CANCEL, REBOOK, REVIEW, ITEM_CLICK, RESCHEDULE
    }

    inner class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Views from call_history_item.xml
        private val tvBookingDate: TextView = itemView.findViewById(R.id.tvBookingDate)
        private val advisorImage: CircleImageView = itemView.findViewById(R.id.advisorImage)
        private val advisorName: TextView = itemView.findViewById(R.id.advisorName)
        private val imgType: ImageView = itemView.findViewById(R.id.imgType)
        private val tvBookingType: TextView = itemView.findViewById(R.id.tvBookingType)
        private val tvBookingId: TextView = itemView.findViewById(R.id.tvBookingId)
        
        // Buttons
        private val btnReBook: TextView = itemView.findViewById(R.id.btnReBook)
        private val btnAddReview: TextView = itemView.findViewById(R.id.btnAddReview)
        private val btnCancel: TextView = itemView.findViewById(R.id.btnCancel)
        private val btnReschedule: TextView = itemView.findViewById(R.id.btnReschedule)
        private val actionButtonsLayout: View = itemView.findViewById(R.id.actionButtonsLayout)

        fun bind(booking: SessionBookingDataClass, advisor: UserData?) {
            // 0. Item Click (Redirect to Advisor Profile)
            itemView.setOnClickListener {
                onActionClick(CallHistoryAction.ITEM_CLICK, booking)
            }
            
            // 1. Advisor Details
            advisorName.text = booking.advisorName.ifEmpty { advisor?.name ?: "Unknown Advisor" }
            
            if (advisor?.profilePhotoUrl != null && advisor.profilePhotoUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(advisor.profilePhotoUrl)
                    .placeholder(R.drawable.user)
                    .into(advisorImage)
            } else {
                advisorImage.setImageResource(R.drawable.user)
            }

            // 2. Date Header
            val date = booking.bookingTimestamp?.toDate()
            if (date != null) {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                tvBookingDate.text = dateFormat.format(date)
            } else {
                tvBookingDate.text = "Date Unknown"
            }

            // 3. Booking Type & Source
            val type = booking.bookingType.uppercase()
            val urgency = if (booking.urgencyLevel.equals("Scheduled", ignoreCase = true)) "Scheduled" else "Instant"
            
            tvBookingType.text = "${type.capitalize()} Call ($urgency)"
            
            // Icon based on type
            val iconRes = when(type) {
                "VIDEO" -> R.drawable.videocall
                "AUDIO" -> R.drawable.call
                "CHAT" -> R.drawable.chat
                else -> R.drawable.videocall
            }
            imgType.setImageResource(iconRes)

            // 4. Booking ID
            tvBookingId.text = "#${booking.bookingId.takeLast(8).uppercase()}"

            // 5. Button Logic
            val status = booking.bookingStatus.lowercase()
            
            // Reset visibility
            btnCancel.visibility = View.GONE
            btnReschedule.visibility = View.GONE
            btnReBook.visibility = View.GONE
            btnAddReview.visibility = View.GONE

            if (status == "pending" || status == "accepted" || status == "upcoming" || status == "initiated") {
                // Upcoming
                btnCancel.visibility = View.VISIBLE
                btnCancel.setOnClickListener { onActionClick(CallHistoryAction.CANCEL, booking) }
                
            } else if (status == "completed" || status == "ended") {
                // Completed
                btnReBook.visibility = View.VISIBLE
                btnAddReview.visibility = View.VISIBLE
                
                btnReBook.setOnClickListener { onActionClick(CallHistoryAction.REBOOK, booking) }
                btnAddReview.setOnClickListener { onActionClick(CallHistoryAction.REVIEW, booking) }
                
            } else {
                // Cancelled
                btnReBook.visibility = View.VISIBLE
                btnReBook.setOnClickListener { onActionClick(CallHistoryAction.REBOOK, booking) }
            }
        }
        
        private fun String.capitalize(): String {
            return this.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.call_history_item, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val (booking, advisor) = bookingList[position]
        holder.bind(booking, advisor)
    }

    override fun getItemCount(): Int = bookingList.size

    fun updateList(newList: List<Pair<SessionBookingDataClass, UserData?>>) {
        bookingList = newList
        notifyDataSetChanged()
    }
}