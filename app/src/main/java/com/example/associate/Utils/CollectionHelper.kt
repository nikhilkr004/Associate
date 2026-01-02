package com.example.associate.Utils

object CollectionHelper {
    fun getCollectionName(callType: String): String {
        return when (callType.lowercase()) {
            "video", "video_call" -> "videoCalls"
            "audio", "audio_call" -> "audioCalls"
            "chat", "message" -> "chats"
            else -> "videoCalls" // Default or fallback
        }
    }
}
