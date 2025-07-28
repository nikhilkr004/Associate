package com.example.associate.Repositorys

import com.example.associate.DataClass.AdvisorDataClass
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class AdvisorRepository{
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun saveAdvisor(advisor: AdvisorDataClass.AdvisorBasicInfo): Boolean {
        return try {
            val data = hashMapOf(
                "advisorId" to advisor.advisorId,
                "name" to advisor.name,
                "email" to advisor.email,
                "phone" to advisor.phone,
                "profilePhotoUrl" to advisor.profilePhotoUrl,
                "city" to advisor.city,
                "gender" to advisor.gender,
                "joinAt" to advisor.joinAt,
                "profileCompletion" to advisor.profileCompletion
            )

            firestore.collection("advisors")
                .document(advisor.advisorId)
                .set(data)
                .await()


            true // Success
        } catch (e: Exception) {
            false // Failure
        }
    }
}