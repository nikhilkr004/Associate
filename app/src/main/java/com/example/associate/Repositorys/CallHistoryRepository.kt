package com.example.associate.Repositorys

import com.example.associate.DataClass.UserData
import com.example.associate.DataClass.VideoCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CallHistoryRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getUserVideoCalls(): List<VideoCall> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return try {
            val documents = db.collection("videoCalls")
                .whereEqualTo("receiverId", userId)
                .get()
                .await()
                .documents

            documents.mapNotNull { document ->
                document.toObject(VideoCall::class.java)?.copy(id = document.id)
            }.sortedByDescending { it.callStartTime } // Sort in memory
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserDetails(userId: String): UserData? {
        return try {
            android.util.Log.d("CallHistoryRepo", "Fetching user details for ID: $userId")
            val document = db.collection("users")
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                android.util.Log.d("CallHistoryRepo", "User found: ${document.id}")
                document.toObject(UserData::class.java)
            } else {
                android.util.Log.e("CallHistoryRepo", "User document not found for ID: $userId")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CallHistoryRepo", "Error fetching user details: ${e.message}")
            null
        }
    }

    suspend fun getVideoCallsWithUserDetails(): List<Pair<VideoCall, UserData?>> {
        val videoCalls = getUserVideoCalls()
        android.util.Log.d("CallHistoryRepo", "Fetched ${videoCalls.size} video calls")
        
        val result = mutableListOf<Pair<VideoCall, UserData?>>()

        for (call in videoCalls) {
            android.util.Log.d("CallHistoryRepo", "Processing call: ${call.id}, userId: ${call.userId}")
            val user = if (call.userId.isNotEmpty()) {
                getUserDetails(call.userId)
            } else {
                android.util.Log.w("CallHistoryRepo", "Call ${call.id} has empty userId")
                null
            }
            result.add(Pair(call, user))
        }

        return result
    }
}