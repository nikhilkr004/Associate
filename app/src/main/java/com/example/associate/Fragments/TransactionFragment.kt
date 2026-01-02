import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
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
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class TransactionFragment : Fragment() {
    private lateinit var binding: FragmentTransactionBinding

    private lateinit var transactionAdapter: TransactionAdapter
    private val transactionList = mutableListOf<Transaction>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Listeners for Real-time Updates
    private var paymentListener: ListenerRegistration? = null
    private var transactionListener: ListenerRegistration? = null
    
    // Separate lists for merging
    private val paymentTxns = ArrayList<Transaction>()
    private val callTxns = ArrayList<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTransactionBinding.inflate(inflater, container, false)

        setupRecyclerView()
        fetchWalletBalance() 
        fetchTransactionData()
        setupClickListeners()

        return binding.root
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        paymentListener?.remove()
        transactionListener?.remove()
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
                .addSnapshotListener { document, e ->
                    if (e != null) {
                        Log.e("TransactionFragment", "Wallet Listen Failed: ${e.message}")
                        return@addSnapshotListener
                    }
                    
                    if (document != null && document.exists()) {
                        val wallet = document.toObject(WalletDataClass::class.java)
                        wallet?.let {
                            binding.currentBalance.text = "₹${String.format("%.2f", it.balance)}"
                        }
                    } else {
                         // Create if not exists (Only once, check existence first prevents loop if creation is slow)
                         // Actually, we shouldn't create it in a listener loop if it triggers again. 
                         // But here we just set text 0.00 if null. Creation logic is better elsewhere or specific action.
                         binding.currentBalance.text = "₹0.00"
                    }
                }
        }
    }

    // New Data Fetching Logic (Real-time)
    private fun fetchTransactionData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // binding.loadingProgressBar.visibility = View.VISIBLE
        DialogUtils.showLoadingDialog(requireContext(), "Loading...")

        // 1. Listen to 'payments' (Top-ups)
        paymentListener = firestore.collection("payments")
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                paymentTxns.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val payment = doc.toObject(PaymentDataClass::class.java)
                        payment?.let {
                            paymentTxns.add(convertPaymentToTransaction(it, doc.id))
                        }
                    }
                }
                updateMergedList()
            }

        // 2. Listen to 'transactions' (Call Debits/Credits) - ROOT COLLECTION
        transactionListener = firestore.collection("transactions")
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                callTxns.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        try {
                            val data = doc.data ?: continue
                            val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                            val type = data["type"] as? String ?: "debit"
                            val timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now()
                            val bookingId = data["bookingId"] as? String ?: data["relatedBookingId"] ?: doc.id
                            val description = data["description"] as? String ?: "Transaction"
                            val category = data["category"] as? String ?: ""
                            
                            val isDebit = type.equals("debit", ignoreCase = true) || category == "call_payment"
                            
                            val formattedAmount = if (isDebit) {
                                "- ₹${String.format("%.2f", amount)}"
                            } else {
                                "+ ₹${String.format("%.2f", amount)}"
                            }

                            val transaction = Transaction(
                                id = doc.id,
                                type = description,
                                amount = formattedAmount,
                                date = formatDate(timestamp),
                                transactionId = bookingId.toString(),
                                status = if (isDebit) "Debited" else "Credited",
                                paymentData = null,
                                timestamp = timestamp
                            )
                            callTxns.add(transaction)
                        } catch (e: Exception) {
                            Log.e("TransactionFragment", "Error parsing transaction: ${e.message}")
                        }
                    }
                }
                updateMergedList()
            }
    }
    
    private fun updateMergedList() {
        val mergedList = ArrayList<Transaction>()
        mergedList.addAll(paymentTxns)
        mergedList.addAll(callTxns)
        
        // Sort by Date Descending
        mergedList.sortByDescending { it.timestamp }
        
        transactionList.clear()
        transactionList.addAll(mergedList)
        transactionAdapter.notifyDataSetChanged()
        
        // binding.loadingProgressBar.visibility = View.GONE
        DialogUtils.hideLoadingDialog()
        
        if (transactionList.isEmpty()) {
             // Handle empty state visibility if view exists
        }
    }

    private fun convertPaymentToTransaction(payment: PaymentDataClass, documentId: String): Transaction {
        return Transaction(
            id = documentId,
            type = getTransactionType(payment),
            amount = getFormattedAmount(payment),
            date = formatDate(payment.createdAt),
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
        if (payment.status == "pending") return "Pending"
        if (payment.status == "failed") return "Failed"
        if (payment.status == "success") return "Debited" 

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
            Toast.makeText(requireContext(), "Live Updating...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // No need to fetch manually, listeners are active.
        // But if listeners were removed in onPause, add them back here.
        // We removed in onDestroyView, which is fine for Fragments usually.
    }

    companion object {
        fun newInstance() = TransactionFragment()
    }
}