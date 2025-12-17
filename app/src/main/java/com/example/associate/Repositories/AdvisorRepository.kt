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
                .whereEqualTo("basicInfo.status", "active")
                .get()
                .await()

            documents.mapNotNull { document ->
                try {
                    val advisor = document.toObject(AdvisorDataClass::class.java)
                    advisor?.copy(basicInfo = advisor.basicInfo.copy(id = document.id))
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
}