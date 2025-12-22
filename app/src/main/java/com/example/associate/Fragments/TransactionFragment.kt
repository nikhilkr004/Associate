package com.example.associate.Fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Adapters.TransactionAdapter
import com.example.associate.DataClass.DialogUtils
import com.example.associate.DataClass.PaymentDataClass
import com.example.associate.DataClass.Transaction
import com.example.associate.DataClass.WalletDataClass
import com.example.associate.databinding.FragmentTransactionBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class TransactionFragment : Fragment() {
    private lateinit var binding: FragmentTransactionBinding

    private lateinit var transactionAdapter: TransactionAdapter
    private val transactionList = mutableListOf<Transaction>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTransactionBinding.inflate(inflater, container, false)

        setupRecyclerView()
        fetchWalletBalance() // Pehle balance fetch karo
        fetchTransactionData()
        setupClickListeners()

        return binding.root
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(transactionList)
        binding.transactionHistoryRecyclerview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun fetchWalletBalance() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("wallets")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // CORRECTION: WalletDataClass use karo, PaymentDataClass nahi
                        val wallet = document.toObject(WalletDataClass::class.java)
                        wallet?.let {
                            binding.currentBalance.text = "₹${String.format("%.2f", it.balance)}"
                        }
                    } else {
                        // Wallet document doesn't exist, create one
                        createWalletDocument(currentUser.uid)
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to fetch balance: ${exception.message}", Toast.LENGTH_SHORT).show()
                    // Fallback: Set default balance
                    binding.currentBalance.text = "₹0.00"
                }
        } else {
            binding.currentBalance.text = "₹0.00"
        }
    }

    private fun createWalletDocument(userId: String) {
        val wallet = WalletDataClass(
            userId = userId,
            balance = 0.0,
            lastUpdated = Timestamp.now(),
            totalAdded = 0.0,
            totalSpent = 0.0,
            transactionCount = 0
        )

        firestore.collection("wallets")
            .document(userId)
            .set(wallet)
            .addOnSuccessListener {
                binding.currentBalance.text = "₹0.00"
                Toast.makeText(requireContext(), "Wallet created successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to create wallet: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.currentBalance.text = "₹0.00"
            }
    }

    private fun fetchTransactionData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        DialogUtils.showLoadingDialog(requireContext(), "Loading...")

        val paymentsTask = firestore.collection("payments")
            .whereEqualTo("userId", currentUser.uid)
            .get()

        val callTransactionsTask = firestore.collection("users")
            .document(currentUser.uid)
            .collection("transactions")
            .get()

        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(paymentsTask, callTransactionsTask)
            .addOnSuccessListener { results ->
                transactionList.clear()

                // 1. Process 'payments' (Top-ups)
                val paymentsSnapshot = results[0]
                for (document in paymentsSnapshot.documents) {
                    val payment = document.toObject(PaymentDataClass::class.java)
                    payment?.let {
                        val transaction = convertPaymentToTransaction(it, document.id)
                        transactionList.add(transaction)
                    }
                }

                // 2. Process 'transactions' (Call Debits/Credits)
                val callTxnSnapshot = results[1]
                for (document in callTxnSnapshot.documents) {
                    try {
                        val data = document.data ?: continue
                        val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                        val type = data["type"] as? String ?: "DEBIT"
                        val timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now()
                        val relatedBookingId = data["relatedBookingId"] as? String ?: document.id
                        val description = data["description"] as? String ?: "Transaction"

                        val formattedAmount = if (type == "DEBIT") {
                             "- ₹${String.format("%.2f", amount)}"
                        } else {
                             "+ ₹${String.format("%.2f", amount)}"
                        }

                        val transaction = Transaction(
                            id = document.id,
                            type = description,
                            amount = formattedAmount,
                            date = formatDate(timestamp),
                            transactionId = relatedBookingId,
                            status = if (type == "DEBIT") "Debited" else "Credited",
                            paymentData = null,
                            timestamp = timestamp
                        )
                        transactionList.add(transaction)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Sort by Timestamp Descending
                transactionList.sortByDescending { it.timestamp }

                transactionAdapter.notifyDataSetChanged()
                DialogUtils.hideLoadingDialog()

                if (transactionList.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to fetch transactions: ${exception.message}", Toast.LENGTH_SHORT).show()
                DialogUtils.hideLoadingDialog()
                showEmptyState()
            }
    }

    private fun convertPaymentToTransaction(payment: PaymentDataClass, documentId: String): Transaction {
        return Transaction(
            id = documentId,
            type = getTransactionType(payment),
            amount = getFormattedAmount(payment),
            date = formatDate(payment.createdAt), // Ensure createdAt is used
            transactionId = payment.razorpayPaymentId.ifEmpty { payment.paymentId },
            status = getFormattedStatus(payment),
            paymentData = payment,
            timestamp = payment.createdAt
        )
    }

    private fun getTransactionType(payment: PaymentDataClass): String {
        return when {
            payment.type == "video_call" -> "Video Call Payment"
            payment.type == "topup" -> "Wallet Top-up"
            payment.amount > 0 -> "Wallet Top-up"
            payment.amount < 0 -> "Payment"
            else -> "Transaction"
        }
    }

    private fun getFormattedAmount(payment: PaymentDataClass): String {
        val symbol = when (payment.currency) {
            "INR" -> "₹"
            "USD" -> "$"
            else -> ""
        }

        return if (payment.amount >= 0) {
            "+ $symbol${String.format("%.2f", payment.amount)}"
        } else {
            // Ensure negative sign is present for negative amounts
            "- $symbol${String.format("%.2f", abs(payment.amount))}"
        }
    }

    private fun formatDate(timestamp: Timestamp): String {
        try {
            val date = timestamp.toDate()
            val sdf = SimpleDateFormat("dd-MM-yyyy 'at' HH:mm", Locale.getDefault())
            return sdf.format(date)
        } catch (e: Exception) {
            return "Date not available"
        }
    }

    private fun getFormattedStatus(payment: PaymentDataClass): String {
        // Custom status for video call payments
        if (payment.type == "video_call" || payment.amount < 0) {
            return "Debited"
        }

        return when (payment.status) {
            PaymentDataClass.STATUS_SUCCESS -> "Completed"
            PaymentDataClass.STATUS_PENDING -> "Pending"
            PaymentDataClass.STATUS_FAILED -> "Failed"
            else -> payment.status.replaceFirstChar { it.uppercase() }
        }
    }

    private fun showEmptyState() {
        if (transactionList.isEmpty()) {
            Toast.makeText(requireContext(), "No transactions found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        binding.refreshBtn.setOnClickListener {
            // Refresh both balance and transactions
            fetchWalletBalance()
            fetchTransactionData()
            Toast.makeText(requireContext(), "Refreshing...", Toast.LENGTH_SHORT).show()
        }
    }

    // Optional: Real-time balance updates ke liye
    override fun onResume() {
        super.onResume()
        fetchWalletBalance() // Fragment resume pe balance refresh karo
    }

    companion object {
        fun newInstance() = TransactionFragment()
    }
}