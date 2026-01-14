package com.example.associate.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.associate.R
import java.text.SimpleDateFormat
import java.util.Locale

class ChatActivityAdapter(
    private val currentUserId: String,
    private var currentUserAvatar: String, // Changed to var
    private var advisorAvatar: String, // Changed to var
    private val messages: ArrayList<HashMap<String, Any>>,
    private val onItemClick: (String, String) -> Unit // url, type
) : RecyclerView.Adapter<ChatActivityAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Sender Views
        val layoutSender: LinearLayout = itemView.findViewById(R.id.layoutSender)
        val tvSenderMessage: TextView = itemView.findViewById(R.id.tvSenderMessage)
        val ivSenderImage: ImageView = itemView.findViewById(R.id.ivSenderImage)
        val layoutSenderDoc: LinearLayout = itemView.findViewById(R.id.layoutSenderDoc)
        val tvSenderDocName: TextView = itemView.findViewById(R.id.tvSenderDocName)
        val layoutSenderVoice: LinearLayout = itemView.findViewById(R.id.layoutSenderVoice)
        val btnSenderPlay: ImageView = itemView.findViewById(R.id.btnSenderPlay)
        val tvSenderTime: TextView = itemView.findViewById(R.id.tvSenderTime)
        val tvSenderDuration: TextView = itemView.findViewById(R.id.tvSenderDuration)

        // Receiver Views
        val layoutReceiver: LinearLayout = itemView.findViewById(R.id.layoutReceiver)
        val tvReceiverMessage: TextView = itemView.findViewById(R.id.tvReceiverMessage)
        val ivReceiverImage: ImageView = itemView.findViewById(R.id.ivReceiverImage)
        val layoutReceiverDoc: LinearLayout = itemView.findViewById(R.id.layoutReceiverDoc)
        val tvReceiverDocName: TextView = itemView.findViewById(R.id.tvReceiverDocName)
        val layoutReceiverVoice: LinearLayout = itemView.findViewById(R.id.layoutReceiverVoice)
        val btnReceiverPlay: ImageView = itemView.findViewById(R.id.btnReceiverPlay)
        val tvReceiverTime: TextView = itemView.findViewById(R.id.tvReceiverTime)
        val tvReceiverDuration: TextView = itemView.findViewById(R.id.tvReceiverDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message_v2, parent, false)
        return ViewHolder(view)
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private var playingAudioUrl: String? = null

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        
        val senderId = message["senderId"] as? String ?: ""
        val type = message["type"] as? String ?: "text"
        val content = message["content"] as? String ?: ""
        val name = message["name"] as? String ?: ""
        
        val timestamp = message["timestamp"] as? com.google.firebase.Timestamp
        val durationObj = message["duration"]
        val duration = if (durationObj is Long) durationObj else (durationObj as? Double)?.toLong() ?: 0L

        val durationText = if(duration > 0) formatDuration(duration) else "0:00"

        val formattedTime = if (timestamp != null) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(timestamp.toDate())
        } else {
            "Just now"
        }

        if (senderId == currentUserId) {
            // SHOW SENDER
            holder.layoutSender.visibility = View.VISIBLE
            holder.layoutReceiver.visibility = View.GONE
            
            holder.tvSenderTime.text = formattedTime
            
            // Load Sender Avatar
            val ivSenderAvatar = holder.itemView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivSenderAvatar)
            if (currentUserAvatar.isNotEmpty()) {
                 Glide.with(holder.itemView.context).load(currentUserAvatar).placeholder(R.drawable.user).into(ivSenderAvatar)
            }

            // Reset Sender Views
            holder.tvSenderMessage.visibility = View.GONE
            holder.ivSenderImage.visibility = View.GONE
            holder.layoutSenderDoc.visibility = View.GONE
            holder.layoutSenderVoice.visibility = View.GONE

            when (type) {
                "text" -> {
                    holder.tvSenderMessage.visibility = View.VISIBLE
                    holder.tvSenderMessage.text = content
                }
                "image" -> {
                    holder.ivSenderImage.visibility = View.VISIBLE
                    Glide.with(holder.itemView.context).load(content).into(holder.ivSenderImage)
                    holder.ivSenderImage.setOnClickListener { onItemClick(content, "image") }
                }
                "document" -> {
                    holder.layoutSenderDoc.visibility = View.VISIBLE
                    holder.tvSenderDocName.text = name
                    holder.layoutSenderDoc.setOnClickListener { onItemClick(content, "document") }
                }
                "audio" -> {
                    holder.layoutSenderVoice.visibility = View.VISIBLE
                    holder.tvSenderDuration.text = durationText
                    
                    if (content == playingAudioUrl) {
                        holder.btnSenderPlay.setImageResource(R.drawable.pause)
                    } else {
                        holder.btnSenderPlay.setImageResource(R.drawable.play)
                    }
                    
                    holder.btnSenderPlay.setOnClickListener { onItemClick(content, "audio") }
                }
            }

        } else {
            // SHOW RECEIVER
            holder.layoutSender.visibility = View.GONE
            holder.layoutReceiver.visibility = View.VISIBLE
            
            holder.tvReceiverTime.text = formattedTime

            // Load Receiver (Advisor) Avatar
            val ivReceiverAvatar = holder.itemView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivReceiverAvatar)
            if (advisorAvatar.isNotEmpty()) {
                 Glide.with(holder.itemView.context).load(advisorAvatar).placeholder(R.drawable.user).into(ivReceiverAvatar)
            } else {
                 ivReceiverAvatar.setImageResource(R.drawable.user)
            }

            // Reset Receiver Views
            holder.tvReceiverMessage.visibility = View.GONE
            holder.ivReceiverImage.visibility = View.GONE
            holder.layoutReceiverDoc.visibility = View.GONE
            holder.layoutReceiverVoice.visibility = View.GONE

            when (type) {
                "text" -> {
                    holder.tvReceiverMessage.visibility = View.VISIBLE
                    holder.tvReceiverMessage.text = content
                }
                "image" -> {
                    holder.ivReceiverImage.visibility = View.VISIBLE
                    Glide.with(holder.itemView.context).load(content).into(holder.ivReceiverImage)
                    holder.ivReceiverImage.setOnClickListener { onItemClick(content, "image") }
                }
                "document" -> {
                    holder.layoutReceiverDoc.visibility = View.VISIBLE
                    holder.tvReceiverDocName.text = name
                    holder.layoutReceiverDoc.setOnClickListener { onItemClick(content, "document") }
                }
                "audio" -> {
                    holder.layoutReceiverVoice.visibility = View.VISIBLE
                    holder.tvReceiverDuration.text = durationText
                    
                    if (content == playingAudioUrl) {
                        holder.btnReceiverPlay.setImageResource(R.drawable.pause)
                    } else {
                        holder.btnReceiverPlay.setImageResource(R.drawable.play)
                    }
                    
                    holder.btnReceiverPlay.setOnClickListener { onItemClick(content, "audio") }
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateList(newMessages: List<HashMap<String, Any>>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun setPlayingAudio(url: String?) {
        playingAudioUrl = url
        notifyDataSetChanged()
    }

    fun updateAvatars(userUrl: String, advisorUrl: String) {
        if (userUrl.isNotEmpty()) currentUserAvatar = userUrl
        if (advisorUrl.isNotEmpty()) advisorAvatar = advisorUrl
        notifyDataSetChanged()
    }
}

// Updated for repository activity
