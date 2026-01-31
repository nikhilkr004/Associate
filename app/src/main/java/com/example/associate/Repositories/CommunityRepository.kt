package com.example.associate.Repositories

import android.util.Log
import com.example.associate.DataClass.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CommunityRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val postsRef = firestore.collection("community_posts")

    suspend fun getFeedPosts(): List<Post> {
        return try {
            val snapshot = postsRef
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.toObjects(Post::class.java)
        } catch (e: Exception) {
            Log.e("CommunityRepository", "Error fetching posts", e)
            emptyList()
        }
    }

    fun incrementViewCount(postId: String) {
        if (postId.isEmpty()) return
        
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(postsRef.document(postId))
            if (snapshot.exists()) {
                val newViews = (snapshot.getLong("viewCount") ?: 0) + 1
                transaction.update(postsRef.document(postId), "viewCount", newViews)
            }
        }.addOnFailureListener { e ->
            Log.e("CommunityRepository", "Error incrementing view count for $postId", e)
        }
    }

    fun incrementProfileVisit(postId: String) {
        if (postId.isEmpty()) return

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(postsRef.document(postId))
            if (snapshot.exists()) {
                val newConnects = (snapshot.getLong("profileConnectCount") ?: 0) + 1
                transaction.update(postsRef.document(postId), "profileConnectCount", newConnects)
            }
        }.addOnFailureListener { e ->
            Log.e("CommunityRepository", "Error incrementing profile visit for $postId", e)
        }
    }
}
