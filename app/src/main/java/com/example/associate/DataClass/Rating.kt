package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class Rating(
    var id: String = "",
    val advisorId: String = "",
    val userId: String = "",
    val rating: Float = 0f,
    val review: String = "",
    val callId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
