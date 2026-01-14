package com.example.associate.HelpSupport

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.R
import com.google.android.material.imageview.ShapeableImageView

class SelectedPhotosAdapter(
    private val onRemoveClick: (Uri) -> Unit
) : RecyclerView.Adapter<SelectedPhotosAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()

    fun setPhotos(newPhotos: List<Uri>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_selected_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = photos[position]
        holder.bind(uri)
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto: ShapeableImageView = itemView.findViewById(R.id.ivPhoto)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)

        fun bind(uri: Uri) {
            ivPhoto.setImageURI(uri) // Simple loading for local URIs
            btnRemove.setOnClickListener {
                onRemoveClick(uri)
            }
        }
    }
}

// Updated for repository activity
