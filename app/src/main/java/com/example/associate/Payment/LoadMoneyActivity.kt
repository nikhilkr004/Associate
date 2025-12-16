package com.example.associate.Payment

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.associate.DataClass.PaymentDataClass
import com.example.associate.DataClass.WalletDataClass
import com.example.associate.R
import com.example.associate.databinding.ActivityLoadMoneyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject

class LoadMoneyActivity : AppCompatActivity(), PaymentResultListener {

    private val binding by lazy {
        ActivityLoadMoneyBinding.inflate(layoutInflater)
    }
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentAmount: Double = 400.0
    private var currentWalletBalance: Double = 0.0
    private var razorpayOrderId: String = ""
    private var currentPaymentId: String = ""

    // Configuration
    companion object {
        private const val TAG = "LoadMoneyActivity"
        private const val RAZORPAY_KEY_ID = "rzp_test_N9hgXP1L6tCGPm" // Replace with your key
        private const val MIN_RECHARGE_AMOUNT = 100.0
        private const val MAX_RECHARGE_AMOUNT = 1000.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeRazorpay()
        setupFirebase()
        loadCurrentWalletBalance()
        setupClickListeners()
        setupAmountListeners()
    }

    private fun initializeRazorpay() {
        try {
            Checkout.preload(applicationContext)
            // Set your Razorpay Key ID
            Checkout().setKeyID(RAZORPAY_KEY_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializing Razorpay", e)
            showToast("Payment service initialization failed")
        }
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }

    private fun loadCurrentWalletBalance() {
        val user = auth.currentUser
        if (user == null) {
            showToast("Please login to continue")
            finish()
            return
        }

        showLoading("Loading wallet...")

        db.collection("wallets")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                hideLoading()
                if (document.exists()) {
                    currentWalletBalance = document.getDouble("balance") ?: 0.0
                    updateBalanceUI()
                } else {
                    // Create wallet if doesn't exist
                    createNewWallet(user.uid)
                }
            }
            .addOnFailureListener { e ->
                hideLoading()
                Log.e(TAG, "Error loading wallet balance", e)
                showToast("Failed to load wallet balance")
            }
    }

    private fun createNewWallet(userId: String) {
        val walletData = WalletDataClass(
            userId = userId,
            balance = 0.0,
            totalAdded = 0.0,
            totalSpent = 0.0,
            transactionCount = 0
        )

        db.collection("wallets")
            .document(userId)
            .set(walletData, SetOptions.merge())
            .addOnSuccessListener {
                currentWalletBalance = 0.0
                updateBalanceUI()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating wallet", e)
                showToast("Failed to initialize wallet")
            }
    }

    @SuppressLint("SetTextI18n")
    private fun updateBalanceUI() {
        // You can show current balance in UI if you have a TextView
        // binding.currentBalanceText.text = "Current Balance: ₹$currentWalletBalance"
        Log.d(TAG, "Current wallet balance: ₹$currentWalletBalance")
    }

    @SuppressLint("SetTextI18n")
    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener {
            finish()
        }

        // Quick amount buttons
        binding.amountButton100.setOnClickListener {
            setAmount(100.0)
        }

        binding.amountButton200.setOnClickListener {
            setAmount(200.0)
        }

        binding.amountButton400.setOnClickListener {
            setAmount(400.0)
        }

        // Pay button
        binding.payButton.setOnClickListener {
            if (currentAmount > 0) {
                validateAndStartPayment()
            } else {
                showToast("Please enter a valid amount")
            }
        }
    }

    private fun setupAmountListeners() {
        binding.amountEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            @SuppressLint("SetTextI18n")
            override fun afterTextChanged(s: android.text.Editable?) {
                val amountText = s.toString()
                if (amountText.isNotEmpty()) {
                    try {
                        val enteredAmount = amountText.toDouble()
                        currentAmount = enteredAmount
                        updatePayButton()
                    } catch (e: NumberFormatException) {
                        currentAmount = 0.0
                        updatePayButton()
                    }
                } else {
                    currentAmount = 0.0
                    updatePayButton()
                }
            }
        })
    }

    private fun setAmount(amount: Double) {
        currentAmount = amount
        binding.amountEditText.setText(amount.toInt().toString())
        updatePayButton()
    }

    private fun updateAmountFromEditText() {
        // Redundant with TextWatcher, but keeping for focus change if needed.
        // Simplified to just ensure currentAmount is synced.
        try {
            val amountText = binding.amountEditText.text.toString()
            if (amountText.isNotEmpty()) {
                currentAmount = amountText.toDouble()
            }
        } catch (e: NumberFormatException) {
            currentAmount = 0.0
        }
        updatePayButton()
    }

    private fun validateAmount(amount: Double): Boolean {
        // This function returns false if invalid, and also shows toast.
        // We will misuse it slightly or just rewrite the check in validateAndStartPayment
        // to be explicit about the message.
        return amount in MIN_RECHARGE_AMOUNT..MAX_RECHARGE_AMOUNT
    }

    private fun validateAndStartPayment() {
        if (!validateAmount(currentAmount)) {
            showToast("Minimum topup amount is ₹$MIN_RECHARGE_AMOUNT and Maximum is ₹$MAX_RECHARGE_AMOUNT")
            return
        }
        startPaymentProcess()
    }

    @SuppressLint("SetTextI18n")
    private fun updatePayButton() {
        if (currentAmount > 0) {
            binding.payButton.text = "Pay ₹${currentAmount.toInt()} Now"
        } else {
            binding.payButton.text = "Pay"
        }
        // Always enable to allow user to click and see the validation Toast
        binding.payButton.isEnabled = true
    }

    private fun startPaymentProcess() {
        val user = auth.currentUser
        if (user == null) {
            showToast("Please login to continue")
            return
        }

        if (!validateAmount(currentAmount)) {
            return
        }

        showLoading("Creating payment order...")

        // Create payment record first
        createPaymentRecord(user.uid) { paymentId ->
            if (paymentId.isNotEmpty()) {
                currentPaymentId = paymentId
                createRazorpayOrder(paymentId)
            } else {
                hideLoading()
                showToast("Failed to create payment order")
            }
        }
    }

    private fun createPaymentRecord(userId: String, callback: (String) -> Unit) {
        val paymentId = db.collection("payments").document().id

        val paymentData = PaymentDataClass(
            paymentId = paymentId,
            userId = userId,
            amount = currentAmount,
            status = PaymentDataClass.STATUS_PENDING,
            description = "Wallet Top-up",
            deviceInfo = getDeviceInfo(),
            appVersion = getAppVersion(),
            type = "topup"
        )

        db.collection("payments")
            .document(paymentId)
            .set(paymentData, SetOptions.merge())
            .addOnSuccessListener {
                callback(paymentId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating payment record", e)
                callback("")
            }
    }

    private fun createRazorpayOrder(paymentId: String) {
        try {
            val co = Checkout()
            co.setKeyID(RAZORPAY_KEY_ID)

            val options = JSONObject().apply {
                put("amount", (currentAmount * 100).toInt()) // Amount in paise
                put("currency", "INR")
                put("receipt", paymentId)
                put("payment_capture", 1)

                // Add customer details for better tracking
                val user = auth.currentUser
                val prefill = JSONObject().apply {
                    put("email", user?.email ?: "")
                    put("contact", user?.phoneNumber ?: "")
                }
                put("prefill", prefill)

                // Add notes for additional data
                val notes = JSONObject().apply {
                    put("payment_id", paymentId)
                    put("user_id", user?.uid ?: "")
                    put("purpose", "wallet_topup")
                    put("current_balance", currentWalletBalance)
                }
                put("notes", notes)
            }

            co.open(this, options)

        } catch (e: Exception) {
            hideLoading()
            Log.e(TAG, "Error in starting Razorpay checkout", e)
            showToast("Payment initialization failed")
            updatePaymentStatus(paymentId, PaymentDataClass.STATUS_FAILED, e.message ?: "Unknown error")
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        hideLoading()

        if (razorpayPaymentId != null && currentPaymentId.isNotEmpty()) {
            showToast("Payment successful! Money added to wallet")

            // Update payment status
            updatePaymentStatus(currentPaymentId, PaymentDataClass.STATUS_SUCCESS, razorpayPaymentId)

            // Add money to wallet
            addMoneyToWallet(currentAmount)

            // Finish activity after successful payment
            finish()
        } else {
            showToast("Payment verification failed")
            Log.e(TAG, "Payment success but missing payment IDs")
        }
    }

    override fun onPaymentError(errorCode: Int, response: String?) {
        hideLoading()

        Log.e(TAG, "Payment failed: $errorCode - $response")

        when (errorCode) {
            Checkout.NETWORK_ERROR -> showToast("Network error. Please check your internet connection")
            Checkout.INVALID_OPTIONS -> showToast("Invalid payment options")
            Checkout.PAYMENT_CANCELED -> showToast("Payment cancelled by user")
            else -> showToast("Payment failed: $response")
        }

        if (currentPaymentId.isNotEmpty()) {
            updatePaymentStatus(currentPaymentId, PaymentDataClass.STATUS_FAILED, response ?: "Unknown error")
        }
    }

    private fun updatePaymentStatus(paymentId: String, status: String, razorpayPaymentId: String = "") {
        val updates = hashMapOf<String, Any>(
            "status" to status,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        if (razorpayPaymentId.isNotEmpty()) {
            updates["razorpayPaymentId"] = razorpayPaymentId
        }

        if (status == PaymentDataClass.STATUS_FAILED) {
            updates["errorMessage"] = razorpayPaymentId
        }

        db.collection("payments")
            .document(paymentId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating payment status", e)
            }
    }

    private fun addMoneyToWallet(amount: Double) {
        val user = auth.currentUser ?: return

        val walletRef = db.collection("wallets").document(user.uid)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(walletRef)
            val currentBalance = if (snapshot.exists()) {
                snapshot.getDouble("balance") ?: 0.0
            } else {
                0.0
            }

            val newBalance = currentBalance + amount
            val newTotalAdded = (snapshot.getDouble("totalAdded") ?: 0.0) + amount
            val transactionCount = (snapshot.getLong("transactionCount") ?: 0) + 1

            val walletData = WalletDataClass(
                userId = user.uid,
                balance = newBalance,
                totalAdded = newTotalAdded,
                transactionCount = transactionCount
            )

            transaction.set(walletRef, walletData, SetOptions.merge())
        }.addOnSuccessListener {
            Log.d(TAG, "Wallet updated successfully")
            showToast("₹$amount added to your wallet")

            // Update local balance
            currentWalletBalance += amount
            updateBalanceUI()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error updating wallet", e)
            showToast("Payment successful but wallet update failed. Contact support.")
        }
    }

    // Security and Utility functions
    private fun getDeviceInfo(): String {
        return "Android ${android.os.Build.VERSION.RELEASE} - ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "Unknown"
        }.toString()
    }

    private fun showLoading(message: String) {
        // Implement your loading dialog
        // DialogUtils.showLoadingDialog(this, message)
    }

    private fun hideLoading() {
        // Implement your loading dialog hide
        // DialogUtils.hideLoadingDialog()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideLoading()
    }
}