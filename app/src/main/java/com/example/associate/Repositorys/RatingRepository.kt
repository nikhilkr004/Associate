package com.example.associate.Repositorys

import android.util.Log
import com.example.associate.DataClass.Rating
import com.google.firebase.firestore.FirebaseFirestore

class RatingRepository {

    private val db = FirebaseFirestore.getInstance()
    private val ratingsCollection = db.collection("ratings")

    fun saveRating(rating: Rating, callback: (Boolean, String?) -> Unit) {
        val newDocRef = ratingsCollection.document()
        rating.id = newDocRef.id

        newDocRef.set(rating)
            .addOnSuccessListener {
                Log.d("RatingRepository", "Rating saved successfully: ${rating.id}")
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e("RatingRepository", "Failed to save rating: ${e.message}")
                callback(false, e.message)
            }
    }
}
