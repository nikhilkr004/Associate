package com.example.associate.DataClass

data class AiChatMessage(
    val message: String = "",
    val isUser: Boolean = false,
    val isLoading: Boolean = false, // Helper for UI loading state
    val attachmentUri: String? = null, // For images/PDFs
    val advisors: List<AdvisorDataClass>? = null // List of advisors to display
)
