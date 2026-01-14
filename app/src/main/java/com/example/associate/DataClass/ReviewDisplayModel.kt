package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class ReviewDisplayModel(
    val id: String,
    val reviewerName: String,
    val reviewerImage: String,
    val rating: Float,
    val review: String,
    val timestamp: Timestamp
)

// Updated for repository activity
