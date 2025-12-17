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
                .whereEqualTo("status", "active")
                .whereEqualTo("isactive", true)
                .get()
                .await()

            documents.mapNotNull { document ->
                try {
                    val advisor = document.toObject(AdvisorDataClass::class.java)
                    advisor.id = document.id
                    advisor
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}