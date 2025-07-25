package com.example.associate.Repositorys

import com.example.associate.DataClass.UserData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    sealed class Result {
        data class Success(val documentId: String) : Result()
        data class Failure(val exception: Exception) : Result()
    }

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    suspend fun addUser(user: UserData): Result {
        return try {
            val document = usersCollection.add(user).await()
            Result.Success(document.id)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }


}