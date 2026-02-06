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

class AiChatRepository {

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = AppConstants.GEMINI_API_KEY
        )
    }

    private var chat: Chat? = null

    // Unified sendMessage for Text, Image, and PDF
    suspend fun sendMessage(
        userMessage: String, 
        context: String = "", 
        languageCode: String = "en",
        image: Bitmap? = null,
        pdfBase64: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize Chat if needed
                if (chat == null) {
                    // Preload context as the first message or history
                    // We can just start chat.
                     chat = generativeModel.startChat()
                     
                     // Send initial system prompt invisibly to set behavior?
                     // Or just rely on the first message containing it.
                     // We will prepend context to the first message if chat was just created.
                }

                // Prepare Content
                val languageInstruction = if (languageCode == "hi") {
                    "Reply in Hindi (or Hinglish)."
                } else {
                    "Reply in English."
                }
                
                // If it's the very first message or we want to reinforce safety:
                val systemPrompt = """
                    $context
                    
                    System Instruction:
                    1. $languageInstruction
                    2. STRICT SAFETY: Answer ONLY based on provided context/docs. Say "I don't know" if unsure.
                    3. If asked for advisors/recommendations, include tag "[SHOW_ADVISORS]" and say "Here are top-rated advisors:".
                """.trimIndent()

                // We only attach the full system context to the message if it's text-heavy or first turn?
                // To save tokens, we might only send it once. But for now, let's send it with the request 
                // to ensuring strict adherence, or just the instruction part.
                
                val finalPrompt = if (chat!!.history.isEmpty()) {
                   "$systemPrompt\n\nUser: $userMessage"
                } else {
                   // For subsequent messages, just remind about language/safety briefly
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

                val response = chat!!.sendMessage(inputContent)
                response.text ?: "I am having trouble connecting right now."
            } catch (e: Exception) {
                Log.e("AiChatRepo", "Gemini Error: ${e.message}")
                "I apologize, network error: ${e.localizedMessage}"
            }
        }
    }


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