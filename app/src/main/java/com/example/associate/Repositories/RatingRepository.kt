package com.example.associate.Repositories

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

    fun getReviewsForAdvisor(advisorId: String, callback: (List<com.example.associate.DataClass.ReviewDisplayModel>) -> Unit) {
        ratingsCollection.whereEqualTo("advisorId", advisorId)
            .get()
            .addOnSuccessListener { documents ->
                val reviews = mutableListOf<com.example.associate.DataClass.ReviewDisplayModel>()
                val ratings = documents.toObjects(Rating::class.java)

                if (ratings.isEmpty()) {
                    callback(emptyList())
                    return@addOnSuccessListener
                }

                var processedCount = 0
                ratings.forEach { rating ->
                    // Fetch User Details for each rating
                    db.collection("users").document(rating.userId).get()
                        .addOnSuccessListener { userDoc ->
                            val userName = userDoc.getString("name") ?: "Anonymous User"
                            val userImage = userDoc.getString("profilePhotoUrl") ?: "" // Corrected key matching UserData

                            reviews.add(
                                com.example.associate.DataClass.ReviewDisplayModel(
                                    id = rating.id,
                                    reviewerName = userName,
                                    reviewerImage = userImage,
                                    rating = rating.rating,
                                    review = rating.review,
                                    timestamp = rating.timestamp
                                )
                            )

                            processedCount++
                            // Check if all processed
                            if (processedCount == ratings.size) {
                                // Sort by timestamp descending
                                callback(reviews.sortedByDescending { it.timestamp })
                            }
                        }
                        .addOnFailureListener {
                            // If user fetch fails, still add review with default name
                            reviews.add(
                                com.example.associate.DataClass.ReviewDisplayModel(
                                    id = rating.id,
                                    reviewerName = "User",
                                    reviewerImage = "",
                                    rating = rating.rating,
                                    review = rating.review,
                                    timestamp = rating.timestamp
                                )
                            )
                            processedCount++
                            if (processedCount == ratings.size) {
                                callback(reviews.sortedByDescending { it.timestamp })
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("RatingRepository", "Error getting reviews: ${e.message}")
                callback(emptyList())
            }
    }
}
