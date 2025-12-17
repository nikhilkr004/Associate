package com.example.associate.Payment

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.associate.DataClass.PaymentDataClass
import com.example.associate.R
import com.example.associate.ViewModel.WalletViewModel
import com.example.associate.databinding.ActivityLoadMoneyBinding
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject

class LoadMoneyActivity : AppCompatActivity(), PaymentResultListener {

    private val binding by lazy {
        ActivityLoadMoneyBinding.inflate(layoutInflater)
    }
    private lateinit var viewModel: WalletViewModel
    private lateinit var auth: FirebaseAuth

    private var currentAmount: Double = 400.0
    private var currentPaymentId: String = ""

    companion object {
        private const val TAG = "LoadMoneyActivity"
        private const val RAZORPAY_KEY_ID = "rzp_test_N9hgXP1L6tCGPm" 
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
        setupViewModel()
        setupClickListeners()
        setupAmountListeners()
        observeViewModel()
    }

    private fun initializeRazorpay() {
        Checkout.preload(applicationContext)
        Checkout().setKeyID(RAZORPAY_KEY_ID)
    }

    private fun setupViewModel() {
        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this)[WalletViewModel::class.java]
        
        auth.currentUser?.uid?.let { userId ->
            viewModel.loadBalance(userId)
        } ?: run {
            showToast("Please login first")
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.walletBalance.observe(this) { balance ->
            // Update UI with balance if needed
            Log.d(TAG, "Current balance: ₹$balance")
        }

        viewModel.validationMessage.observe(this) { message ->
            message?.let { showToast(it) }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) showLoading() else hideLoading()
        }

        viewModel.paymentState.observe(this) { state ->
            when (state) {
                is WalletViewModel.PaymentState.OrderCreated -> {
                    currentPaymentId = state.paymentId
                    startRazorpayCheckout(state.paymentId)
                }
                is WalletViewModel.PaymentState.Success -> {
                    showToast("Payment Successful! Money added.")
                    finish()
                }
                is WalletViewModel.PaymentState.Error -> {
                    showToast("Error: ${state.message}")
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener { finish() }

        binding.amountButton100.setOnClickListener { setAmount(100.0) }
        binding.amountButton200.setOnClickListener { setAmount(200.0) }
        binding.amountButton400.setOnClickListener { setAmount(400.0) }

        binding.payButton.setOnClickListener {
            if (viewModel.validateAmount(currentAmount)) {
                startPaymentProcess()
            }
        }
    }

    private fun setupAmountListeners() {
        binding.amountEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentAmount = s.toString().toDoubleOrNull() ?: 0.0
                updatePayButton()
            }
        })
    }

    private fun setAmount(amount: Double) {
        currentAmount = amount
        binding.amountEditText.setText(amount.toInt().toString())
        updatePayButton()
    }

    @SuppressLint("SetTextI18n")
    private fun updatePayButton() {
        binding.payButton.text = if (currentAmount > 0) "Pay ₹${currentAmount.toInt()} Now" else "Pay"
        binding.payButton.isEnabled = true
    }

    private fun startPaymentProcess() {
        val user = auth.currentUser ?: return
        
        val paymentData = PaymentDataClass(
            paymentId = java.util.UUID.randomUUID().toString(), // Temp ID, Repo uses document ID usually but here we generate
            userId = user.uid,
            amount = currentAmount,
            status = PaymentDataClass.STATUS_PENDING,
            description = "Wallet Top-up",
            deviceInfo = "Android",
            appVersion = "1.0",
            type = "topup"
        )
        // Note: In original code, ID was generated by Firestore. 
        // Here specific logic might depend on Repo. Repo uses ID from data class.
        // So we generate ID here.
        val id = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("payments").document().id
        val finalData = paymentData.copy(paymentId = id)
        
        viewModel.initiatePayment(finalData)
    }

    private fun startRazorpayCheckout(paymentId: String) {
        try {
            val co = Checkout()
            val options = JSONObject().apply {
                put("amount", (currentAmount * 100).toInt())
                put("currency", "INR")
                put("receipt", paymentId)
                put("prefill", JSONObject().apply {
                    put("email", auth.currentUser?.email)
                    put("contact", auth.currentUser?.phoneNumber)
                })
            }
            co.open(this, options)
        } catch (e: Exception) {
            showToast("Error initializing payment")
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        if (razorpayPaymentId != null) {
            viewModel.handlePaymentSuccess(currentPaymentId, razorpayPaymentId, currentAmount, auth.currentUser!!.uid)
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        viewModel.handlePaymentFailure(currentPaymentId, code, response)
    }

    private fun showLoading() { /* Show Dialog */ }
    private fun hideLoading() { /* Hide Dialog */ }
    private fun showToast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}