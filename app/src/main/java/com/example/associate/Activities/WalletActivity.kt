package com.example.associate.Activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.associate.DataClass.PaymentDataClass
import com.example.associate.ViewModel.WalletViewModel
import com.example.associate.databinding.ActivityWalletBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import java.util.UUID

class WalletActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletBinding
    private val viewModel: WalletViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private var currentPaymentId: String = ""
    private var enteredAmount: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        // Load current balance
        viewModel.loadBalance(userId)

        setupObservers(userId)
        setupClickListeners(userId)
    }

    private fun setupObservers(userId: String) {
        viewModel.walletBalance.observe(this) { balance ->
            binding.walletBalance.text = String.format("%.1f", balance)
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.proceedBtn.isEnabled = !loading
            binding.proceedBtn.text = if (loading) "Processing..." else "Proceed to Pay"
        }

        viewModel.validationMessage.observe(this) { msg ->
            if (msg != null) {
                binding.validationMsg.text = msg
                binding.validationMsg.visibility = View.VISIBLE
            } else {
                binding.validationMsg.visibility = View.GONE
            }
        }

        viewModel.paymentState.observe(this) { state ->
            when (state) {
                is WalletViewModel.PaymentState.OrderCreated -> {
                    currentPaymentId = state.paymentId
                    // Simulate success for now (integrate Razorpay here)
                    simulatePaymentSuccess(userId)
                }
                is WalletViewModel.PaymentState.Success -> {
                    Toast.makeText(this, "✅ ₹${enteredAmount.toInt()} credited to your wallet!", Toast.LENGTH_LONG).show()
                    viewModel.loadBalance(userId)
                    binding.amountInput.setText("")
                }
                is WalletViewModel.PaymentState.Error -> {
                    Toast.makeText(this, "❌ ${state.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupClickListeners(userId: String) {
        binding.backBtn.setOnClickListener { finish() }

        // Quick-select chips
        binding.chip100.setOnClickListener { binding.amountInput.setText("100") }
        binding.chip500.setOnClickListener { binding.amountInput.setText("500") }
        binding.chip1000.setOnClickListener { binding.amountInput.setText("1000") }

        binding.proceedBtn.setOnClickListener {
            val amountStr = binding.amountInput.text.toString().trim()
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
            if (!viewModel.validateAmount(enteredAmount)) return@setOnClickListener

            val paymentId = UUID.randomUUID().toString()
            val paymentData = PaymentDataClass(
                paymentId = paymentId,
                userId = userId,
                amount = enteredAmount,
                currency = "INR",
                status = PaymentDataClass.STATUS_PENDING,
                description = "Wallet Top-up",
                type = "topup",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            viewModel.initiatePayment(paymentData)
        }
    }

    // For demo: simulate immediate success (replace with Razorpay SDK callback)
    private fun simulatePaymentSuccess(userId: String) {
        viewModel.handlePaymentSuccess(currentPaymentId, "sim_${System.currentTimeMillis()}", enteredAmount, userId)
    }
}
