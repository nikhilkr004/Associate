package com.example.associate.Repositorys




import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.VideoCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CallHistoryRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getUserVideoCalls(): List<VideoCall> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return try {
            db.collection("videoCalls")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(VideoCall::class.java)?.copy(id = document.id)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAdvisorDetails(advisorId: String): AdvisorDataClass? {
        return try {
            db.collection("advisors")
                .document(advisorId)
                .get()
                .await()
                .toObject(AdvisorDataClass::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getVideoCallsWithAdvisorDetails(): List<Pair<VideoCall, AdvisorDataClass?>> {
        val videoCalls = getUserVideoCalls()
        val result = mutableListOf<Pair<VideoCall, AdvisorDataClass?>>()

        for (call in videoCalls) {
            val advisor = if (call.advisorId.isNotEmpty()) {
                getAdvisorDetails(call.advisorId)
            } else {
                null
            }
            result.add(Pair(call, advisor))
        }

        return result
    }
}