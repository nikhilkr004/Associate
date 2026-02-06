package com.example.associate.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class PostType {
    IMAGE, VIDEO_REEL
}

@Parcelize
data class CommunityPost(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userAvatarUrl: String = "",
    val mediaUrl: String = "",
    val contentDescription: String = "", // Caption
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: PostType = PostType.IMAGE,
    val isLiked: Boolean = false
) : Parcelable
