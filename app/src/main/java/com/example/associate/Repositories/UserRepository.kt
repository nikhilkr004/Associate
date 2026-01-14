package com.example.associate.Repositories

import android.util.Log
import com.example.associate.DataClass.UserData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {

    // Sealed class for addUser operation
    sealed class AddUserResult {
        data class Success(val documentId: String) : AddUserResult()
        data class Failure(val exception: Exception) : AddUserResult()
    }

    // Generic sealed class for other operations
    sealed class Result<T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Failure<T>(val exception: Exception) : Result<T>()
    }

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")



    suspend fun getUserById(userId: String): Result<UserData> {
        return try {
            Log.d("UserRepository", "Fetching user for ID: $userId")
            val document = usersCollection.document(userId).get().await()

            if (document.exists()) {
                val user = document.toObject(UserData::class.java)
                if (user != null) {
                    Log.d("UserRepository", "User found: ${user.name}")
                    Result.Success(user)
                } else {
                    Log.e("UserRepository", "User data is null for ID: $userId")
                    Result.Failure(Exception("User data is null"))
                }
            } else {
                Log.e("UserRepository", "User document does not exist for ID: $userId")
                Result.Failure(Exception("User not found in database"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching user: ${e.message}", e)
            Result.Failure(e)
        }
    }
    suspend fun updateUserProfileImage(userId: String, imageUrl: String): Result<Any> {
        return try {
            usersCollection.document(userId)
                .update("profilePhotoUrl", imageUrl)
                .await()
            Result.Success(true)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }


    suspend fun createOrUpdateUser(user: UserData): Result<Boolean> {
        return try {
            // Use userId as document ID for easy retrieval
            usersCollection.document(user.userId)
                .set(user)
                .await()
            Result.Success(true)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // Add FCM token to user document
    suspend fun saveFCMToken(userId: String, token: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("fcmToken", token)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // Get FCM token for a specific user
    suspend fun getFCMToken(userId: String): Result<String> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val token = document.getString("fcmToken") ?: ""
            Result.Success(token)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // Get all user FCM tokens (for broadcasting)
    suspend fun getAllUserTokens(): Result<List<String>> {
        return try {
            val querySnapshot = usersCollection.get().await()
            val tokens = mutableListOf<String>()
            for (document in querySnapshot) {
                document.getString("fcmToken")?.let { token ->
                    if (token.isNotBlank()) {
                        tokens.add(token)
                    }
                }
            }
            Result.Success(tokens)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

//    // Get user data by user ID
//    suspend fun getUserById(userId: String): Result<UserData> {
//        return try {
//            val document = usersCollection.document(userId).get().await()
//            val user = document.toObject(UserData::class.java) ?: UserData()
//            Result.Success(user)
//        } catch (e: Exception) {
//            Result.Failure(e)
//        }
//    }

    // Update user data
    suspend fun updateUser(userId: String, userData: Map<String, Any>): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update(userData)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // Check if user exists by phone number
    suspend fun userExistsByPhone(phoneNumber: String): Result<Boolean> {
        return try {
            val query = usersCollection.whereEqualTo("phone", phoneNumber).get().await()
            Result.Success(!query.isEmpty)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // Get all users
    suspend fun getAllUsers(): Result<List<UserData>> {
        return try {
            val querySnapshot = usersCollection.get().await()
            val users = mutableListOf<UserData>()
            for (document in querySnapshot) {
                document.toObject(UserData::class.java)?.let { user ->
                    users.add(user)
                }
            }
            Result.Success(users)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    // Favorite functionality
    suspend fun addFavoriteAdvisor(userId: String, advisorId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("favoriteAdvisors", com.google.firebase.firestore.FieldValue.arrayUnion(advisorId))
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun removeFavoriteAdvisor(userId: String, advisorId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("favoriteAdvisors", com.google.firebase.firestore.FieldValue.arrayRemove(advisorId))
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun getFavoriteAdvisors(userId: String): Result<List<String>> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val favorites = document.get("favoriteAdvisors") as? List<String> ?: emptyList()
            Result.Success(favorites)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
// Updated for repository activity
