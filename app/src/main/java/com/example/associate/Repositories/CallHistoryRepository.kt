package com.example.associate.Repositories

import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.UserData
import com.example.associate.DataClass.VideoCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository responsible for fetching Call History data.
 * Handles interaction with Firestore collections 'videoCalls' and 'advisors'.
 */
class CallHistoryRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val TAG = "CallHistoryRepo"

    /**
     * Fetches the video calls where the current user is the receiver.
     * @return List of [VideoCall] objects sorted by start time descending.
     */
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
            }.sortedByDescending { it.callStartTime }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching calls", e)
            emptyList()
        }
    }

    /**
     * Fetches details of a specific Advisor by ID.
     * Mapped to [UserData] for compatibility with existing Adapters.
     * @param userId The ID of the advisor to fetch.
     * @return [UserData] object or null if not found/error.
     */
    suspend fun getUserDetails(userId: String): UserData? {
        return try {
            val document = db.collection("advisors")
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                // Map AdvisorDataClass to UserData to maintain Adapter compatibility
                val advisor = document.toObject(AdvisorDataClass::class.java)
                advisor?.let {
                    UserData(
                        userId = it.id,
                        name = it.name,
                        email = it.email,
                        phone = it.phoneNumber,
                        profilePhotoUrl = it.profileimage
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching advisor details: ${e.message}")
            null
        }
    }

    /**
     * Fetches video calls along with the details of the other party (Advisor).
     * @return List of Pairs containing the Call and the associated User/Advisor data.
     */
    suspend fun getVideoCallsWithUserDetails(): List<Pair<VideoCall, UserData?>> {
        val videoCalls = getUserVideoCalls()
        val result = mutableListOf<Pair<VideoCall, UserData?>>()

        for (call in videoCalls) {
            // Determine the target ID (The other person in the call).
            // For received calls, the callerId represents the Advisor.
            val targetId = if (call.callerId.isNotEmpty()) call.callerId else call.advisorId
            
            val user = if (targetId.isNotEmpty()) {
                getUserDetails(targetId)
            } else {
                null
            }
            result.add(Pair(call, user))
        }

        return result
    }
}