package com.example.associate.Repositories

import android.graphics.Bitmap
import android.util.Log
import com.example.associate.DataClass.InsuranceData
import com.example.associate.Utils.AppConstants
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.flowOn

class AiChatRepository {

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = AppConstants.GEMINI_API_KEY
        )
    }

    private var chat: Chat? = null

    // Unified sendMessage for Text, Image, and PDF
    // Unified sendMessage for Text, Image, and PDF with Streaming
    fun sendMessage(
        userMessage: String, 
        context: String = "", 
        languageCode: String = "en",
        image: Bitmap? = null,
        pdfBase64: String? = null
    ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        
        try {
            // Initialize Chat if needed
            if (chat == null) {
                 chat = generativeModel.startChat()
            }

            // Prepare Content
            val languageInstruction = if (languageCode == "hi") {
                "Reply in Hindi (or Hinglish)."
            } else {
                "Reply in English."
            }
            
            val systemPrompt = """
                $context
                
                System Instruction:
                1. $languageInstruction
                2. STRICT SAFETY: Answer ONLY based on provided context/docs. Say "I don't know" if unsure.
                3. If asked for advisors/recommendations, include tag "[SHOW_ADVISORS]" and say "Here are top-rated advisors:".
            """.trimIndent()
            
            val finalPrompt = if (chat!!.history.isEmpty()) {
               "$systemPrompt\n\nUser: $userMessage"
            } else {
               "Instruction: $languageInstruction. Answer safely.\n\nUser: $userMessage"
            }

            val inputContent = content {
                if (image != null) {
                    image(image)
                }
                if (pdfBase64 != null) {
                    blob("application/pdf", android.util.Base64.decode(pdfBase64, android.util.Base64.NO_WRAP))
                }
                text(finalPrompt)
            }

            // Streaming Request
            val stream = chat!!.sendMessageStream(inputContent)
            
            stream.collect { chunk ->
                if (chunk.text != null) {
                    emit(chunk.text!!)
                }
            }
            
        } catch (e: Exception) {
            Log.e("AiChatRepo", "Gemini Error: ${e.message}", e)
            if (e.message != null && (e.message!!.contains("leaked", ignoreCase = true) || e.message!!.contains("API key", ignoreCase = true))) {
                emit("Configuration Error: The provided Gemini API Key is invalid or has been reported as leaked. Please update `AppConstants.GEMINI_API_KEY` with a valid key.")
            } else if (e.message != null && e.message!!.contains("403")) {
                 emit("Access Error: API Key Forbidden (403). Please check quota or billing.")
            } else {
                emit("I apologize, network error: ${e.localizedMessage ?: "Unknown Error"}")
            }
        }
    }.flowOn(Dispatchers.IO)


    fun getInitialContext(): String {
        return InsuranceData.getPromptContext()
    }
    
    // FETCH REAL ADVISORS FROM FIRESTORE
    suspend fun getTopAdvisors(): List<com.example.associate.DataClass.AdvisorDataClass> {
        val advisorRepo = AdvisorRepository()
        // Fetch active advisors
        val advisors = advisorRepo.getActiveAdvisors()
        
        // Sort by Rating (Descending) and take top 5
        return advisors.sortedByDescending { it.performanceInfo.rating }.take(5)
    }

    // -------------------------------------------------------------
    // CHAT PERSISTENCE (HISTORY)
    // -------------------------------------------------------------
    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    data class ChatHistoryItem(
        val docId: String = "",
        val query: String = "",
        val response: String = "",
        val timestamp: Long = 0
    )

    suspend fun saveChatToHistory(userId: String, query: String, response: String) {
        withContext(Dispatchers.IO) {
            try {
                val chatData = hashMapOf(
                    "query" to query,
                    "response" to response,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("users").document(userId)
                    .collection("chat_history")
                    .add(chatData)
            } catch (e: Exception) {
                Log.e("AiChatRepo", "Failed to save chat: ${e.message}")
            }
        }
    }

    suspend fun fetchChatHistory(userId: String): List<ChatHistoryItem> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = db.collection("users").document(userId)
                    .collection("chat_history")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()
                
                snapshot.documents.map { doc ->
                    ChatHistoryItem(
                        docId = doc.id,
                        query = doc.getString("query") ?: "",
                        response = doc.getString("response") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }
            } catch (e: Exception) {
                Log.e("AiChatRepo", "Failed to fetch history: ${e.message}")
                emptyList()
            }
        }
    }
}