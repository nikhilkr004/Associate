package com.example.associate.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.associate.DataClass.PaymentDataClass
import com.example.associate.Repositories.WalletRepository
import kotlinx.coroutines.launch

class WalletViewModel : ViewModel() {

    private val repository = WalletRepository()

    private val _walletBalance = MutableLiveData<Double>()
    val walletBalance: LiveData<Double> = _walletBalance

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _paymentState = MutableLiveData<PaymentState>()
    val paymentState: LiveData<PaymentState> = _paymentState

    private val _validationMessage = MutableLiveData<String?>()
    val validationMessage: LiveData<String?> = _validationMessage

    // Constants
    private val MIN_RECHARGE = 100.0
    private val MAX_RECHARGE = 1000.0

    fun loadBalance(userId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val balance = repository.getWalletBalance(userId)
                _walletBalance.value = balance
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun validateAmount(amount: Double): Boolean {
        return if (amount < MIN_RECHARGE || amount > MAX_RECHARGE) {
            _validationMessage.value = "Minimum topup amount is ₹$MIN_RECHARGE and Maximum is ₹$MAX_RECHARGE"
            false
        } else {
            _validationMessage.value = null
            true
        }
    }

    fun initiatePayment(paymentData: PaymentDataClass) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val paymentId = repository.createPaymentOrder(paymentData)
                _paymentState.value = PaymentState.OrderCreated(paymentId)
            } catch (e: Exception) {
                _paymentState.value = PaymentState.Error(e.message ?: "Failed to create order")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun handlePaymentSuccess(paymentId: String, razorpayPaymentId: String, amount: Double, userId: String) {
        viewModelScope.launch {
            try {
                repository.updatePaymentStatus(paymentId, PaymentDataClass.STATUS_SUCCESS, razorpayPaymentId)
                repository.addMoneyToWallet(userId, amount)
                
                // Refresh balance
                val newBalance = repository.getWalletBalance(userId)
                _walletBalance.value = newBalance
                
                _paymentState.value = PaymentState.Success
            } catch (e: Exception) {
                _paymentState.value = PaymentState.Error("Payment success but update failed")
            }
        }
    }

    fun handlePaymentFailure(paymentId: String, code: Int, response: String?) {
        viewModelScope.launch {
            repository.updatePaymentStatus(paymentId, PaymentDataClass.STATUS_FAILED, errorMessage = "$code: $response")
            _paymentState.value = PaymentState.Error(response ?: "Payment Failed")
        }
    }

    sealed class PaymentState {
        data class OrderCreated(val paymentId: String) : PaymentState()
        object Success : PaymentState()
        data class Error(val message: String) : PaymentState()
    }
}
