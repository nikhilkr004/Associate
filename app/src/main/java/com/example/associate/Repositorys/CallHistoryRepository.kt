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
                android.util.Log.d("CallHistoryRepo", "Raw Document Data for ${document.id}: ${document.data}")
                document.toObject(VideoCall::class.java)?.copy(id = document.id)
            }.sortedByDescending { it.callStartTime } // Sort in memory
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserDetails(userId: String): UserData? {
        return try {
            android.util.Log.d("CallHistoryRepo", "Fetching ADVISOR details for ID: $userId")
            val document = db.collection("advisors")
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                android.util.Log.d("CallHistoryRepo", "Advisor found: ${document.id}")
                // Map AdvisorDataClass to UserData to maintain Adapter compatibility
                val advisor = document.toObject(com.example.associate.DataClass.AdvisorDataClass::class.java)
                advisor?.let {
                    android.util.Log.d("CallHistoryRepo", "   -> Advisor Data: name='${it.name}', image='${it.profileimage}'")
                    UserData(
                        userId = it.id,
                        name = it.name,
                        email = it.email,
                        phone = it.phoneNumber, // Map phoneNumber to phone
                        profilePhotoUrl = it.profileimage // Map profileimage to profilePhotoUrl
                    )
                }
            } else {
                android.util.Log.e("CallHistoryRepo", "Advisor document not found for ID: $userId")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CallHistoryRepo", "Error fetching advisor details: ${e.message}")
            null
        }
    }

    suspend fun getVideoCallsWithUserDetails(): List<Pair<VideoCall, UserData?>> {
        val videoCalls = getUserVideoCalls()
        android.util.Log.d("CallHistoryRepo", "Fetched ${videoCalls.size} video calls")
        
        val result = mutableListOf<Pair<VideoCall, UserData?>>()

        for (call in videoCalls) {
            android.util.Log.d("CallHistoryRepo", "Processing call: ${call.id}")
            android.util.Log.d("CallHistoryRepo", "   -> call.callerId: '${call.callerId}'")
            android.util.Log.d("CallHistoryRepo", "   -> call.receiverId: '${call.receiverId}'")
            
            // We want to show the OTHER person. 
            // If I am the receiver (User), I want to see the Caller (Advisor).
            val targetId = if (call.callerId.isNotEmpty()) call.callerId else call.advisorId
            
            val user = if (targetId.isNotEmpty()) {
                val fetchedUser = getUserDetails(targetId)
                if (fetchedUser != null) {
                    android.util.Log.d("CallHistoryRepo", "   -> Advisor found: ${fetchedUser.name}")
                } else {
                    android.util.Log.e("CallHistoryRepo", "   -> Advisor fetch returned NULL for ID: $targetId")
                }
                fetchedUser
            } else {
                android.util.Log.w("CallHistoryRepo", "   -> Skipping fetch: callerId/advisorId is EMPTY")
                null
            }
            result.add(Pair(call, user))
        }

        return result
    }
}