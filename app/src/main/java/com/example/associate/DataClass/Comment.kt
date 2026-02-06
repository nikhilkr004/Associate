package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val userAvatar: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likesCount: Int = 0,
    val replies: List<Comment> = emptyList(), // Nested replies
    val isLiked: Boolean = false
)
