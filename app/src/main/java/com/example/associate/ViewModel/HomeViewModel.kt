package com.example.associate.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.associate.DataClass.AdvisorDataClass
import com.example.associate.DataClass.SessionBookingDataClass
import com.example.associate.Repositories.AdvisorRepository
import com.example.associate.Repositories.BookingRepository
import com.example.associate.Repositories.WalletRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val advisorRepository = AdvisorRepository()
    private val walletRepository = WalletRepository()
    private val bookingRepository = BookingRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _advisors = MutableLiveData<List<AdvisorDataClass>>()
    val advisors: LiveData<List<AdvisorDataClass>> = _advisors

    private val _walletBalance = MutableLiveData<Double>()
    val walletBalance: LiveData<Double> = _walletBalance

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Convert Flow to LiveData for easy observation in Fragment
    val activeBooking: LiveData<SessionBookingDataClass?> = bookingRepository.getActiveBookingFlow().asLiveData()

    fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        
        loadBalance(userId)
        loadAdvisors()
    }

    fun loadBalance(userId: String) {
        viewModelScope.launch {
            try {
                val balance = walletRepository.getWalletBalance(userId)
                _walletBalance.value = balance
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    fun loadAdvisors() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val list = advisorRepository.getActiveAdvisors()
                _advisors.value = list
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load advisors"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            try {
                bookingRepository.cancelBooking(bookingId)
                // Flow will automatically update UI to null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cancel booking"
            }
        }
    }
}
