package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class Post(
    val postId: String = "",
    val advisorId: String = "",
    val advisorName: String = "",
    val advisorProfileImage: String = "",
    val mediaUrl: String = "",
    val mediaUrls: List<String> = emptyList(),
    val mediaType: String = "", // "IMAGE" or "VIDEO"
    val caption: String = "",
    val timestamp: Timestamp? = null,
    var likesCount: Int = 0,
    val viewCount: Int = 0,     // Total impressions
    val profileConnectCount: Int = 0, // Taps on profile
    val category: String = "",        // e.g., "Mutual Funds", "Stocks"
    val marketSentiment: String = "",  // "BULLISH" or "BEARISH"
    // Local state for UI
    var isLiked: Boolean = false,
    var commentsCount: Int = 0 
)
