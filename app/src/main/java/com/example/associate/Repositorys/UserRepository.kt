package com.example.associate.Repositorys

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

    // Add user to Firestore
    suspend fun addUser(user: UserData): AddUserResult {
        return try {
            val document = usersCollection.add(user).await()
            AddUserResult.Success(document.id)
        } catch (e: Exception) {
            AddUserResult.Failure(e)
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
}