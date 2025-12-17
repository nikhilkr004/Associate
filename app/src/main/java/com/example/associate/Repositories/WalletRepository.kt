package com.example.associate.Repositories

import com.example.associate.DataClass.PaymentDataClass
import com.example.associate.DataClass.WalletDataClass
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class WalletRepository {
    private val db = FirebaseFirestore.getInstance()
    private val walletsCollection = db.collection("wallets")
    private val paymentsCollection = db.collection("payments")

    suspend fun getWalletBalance(userId: String): Double {
        return try {
            val document = walletsCollection.document(userId).get().await()
            if (document.exists()) {
                document.getDouble("balance") ?: 0.0
            } else {
                // Initialize wallet if not exists
                createWallet(userId)
                0.0
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun createWallet(userId: String) {
        val walletData = WalletDataClass(
            userId = userId,
            balance = 0.0,
            totalAdded = 0.0,
            totalSpent = 0.0,
            transactionCount = 0
        )
        walletsCollection.document(userId).set(walletData, SetOptions.merge()).await()
    }

    suspend fun createPaymentOrder(paymentData: PaymentDataClass): String {
        return try {
            paymentsCollection.document(paymentData.paymentId).set(paymentData, SetOptions.merge()).await()
            paymentData.paymentId
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun updatePaymentStatus(paymentId: String, status: String, razorpayPaymentId: String = "", errorMessage: String = "") {
        val updates = hashMapOf<String, Any>(
            "status" to status,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
        if (razorpayPaymentId.isNotEmpty()) updates["razorpayPaymentId"] = razorpayPaymentId
        if (errorMessage.isNotEmpty()) updates["errorMessage"] = errorMessage

        paymentsCollection.document(paymentId).update(updates).await()
    }

    suspend fun addMoneyToWallet(userId: String, amount: Double) {
        val walletRef = walletsCollection.document(userId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(walletRef)
            val currentBalance = snapshot.getDouble("balance") ?: 0.0
            val totalAdded = snapshot.getDouble("totalAdded") ?: 0.0
            val transactionCount = (snapshot.getLong("transactionCount") ?: 0) + 1

            transaction.update(walletRef, mapOf(
                "balance" to currentBalance + amount,
                "totalAdded" to totalAdded + amount,
                "transactionCount" to transactionCount
            ))
        }.await()
    }
}
