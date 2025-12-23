package com.example.associate.Repositories

import com.example.associate.DataClass.AdvisorDataClass
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching Advisor data.
 */
class AdvisorRepository {
    private val db = FirebaseFirestore.getInstance()
    private val advisorsCollection = db.collection("advisors")

    /**
     * Fetches the list of active advisors.
     * @return List of [AdvisorDataClass].
     */
    suspend fun getActiveAdvisors(): List<AdvisorDataClass> {
        return try {
            val documents = advisorsCollection
                // Assuming "active" means the advisor account is valid/approved.
                // If you want to filter by "active" account status:
                .whereEqualTo("basicInfo.status", "active") 
                .get()
                .await()

            documents.mapNotNull { document ->
                try {
                    val advisor = document.toObject(AdvisorDataClass::class.java)
                    // Ensure ID is set from document ID
                    advisor?.copy(basicInfo = advisor.basicInfo.copy(
                        id = document.id,
                        // If 'isactive' is missing in some docs, default to "false" 
                        // (though data class default handles this if null, Firestore might return null if field missing)
                        isactive = document.getString("basicInfo.isactive") ?: advisor.basicInfo.isactive
                    ))
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAdvisorById(advisorId: String): AdvisorDataClass? {
        return try {
            val document = advisorsCollection.document(advisorId).get().await()
            if (document.exists()) {
                val advisor = document.toObject(AdvisorDataClass::class.java)
                advisor?.copy(basicInfo = advisor.basicInfo.copy(id = document.id))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }



    /**
     * Fetches the specific Instant Call Rate for an Advisor.
     * @param advisorId The ID of the advisor.
     * @param type "AUDIO" or "VIDEO".
     * @param onResult Callback with the rate (Double). Returns default 60.0 on failure.
     */
    fun fetchInstantRate(advisorId: String, type: String, onResult: (Double) -> Unit) {
        if (advisorId.isEmpty()) {
            onResult(60.0)
            return
        }

        advisorsCollection.document(advisorId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val pricingInfo = document.get("pricingInfo") as? Map<String, Any>
                    val field = if (type.equals("AUDIO", ignoreCase = true)) "instantAudioFee" else "instantVideoFee"
                    
                    // Robust parsing: Handle Int, Long, Double, String
                    val rate = when (val value = pricingInfo?.get(field)) {
                        is Number -> value.toDouble()
                        is String -> value.toDoubleOrNull() ?: 60.0
                        else -> 60.0
                    }
                    onResult(rate)
                } else {
                    onResult(60.0)
                }
            }
            .addOnFailureListener {
                onResult(60.0)
            }
    }

    suspend fun addAdvisorReview(advisorId: String, userId: String, rating: Double, review: String): Boolean {
        return try {
            val reviewData = hashMapOf(
                "advisorId" to advisorId,
                "userId" to userId,
                "rating" to rating,
                "review" to review,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            val adId = if(advisorId.isEmpty()) "unknown_advisor" else advisorId
            advisorsCollection.document(adId)
                .collection("reviews")
                .add(reviewData)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}