package com.example.associate.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.associate.DataClass.UserData
import com.example.associate.DataClass.VideoCall
import com.example.associate.Repositories.CallHistoryRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the Call History UI state.
 * Handles fetching video calls and associated user details.
 */
class CallHistoryViewModel : ViewModel() {

    private val repository = CallHistoryRepository()

    // Holds the complete fetched list
    private var fullList: List<Pair<com.example.associate.DataClass.SessionBookingDataClass, UserData?>> = emptyList()

    // Holds the filtered list currently displayed
    private val _callHistoryList = MutableLiveData<List<Pair<com.example.associate.DataClass.SessionBookingDataClass, UserData?>>>()
    val callHistoryList: LiveData<List<Pair<com.example.associate.DataClass.SessionBookingDataClass, UserData?>>> = _callHistoryList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Track current tab
    private var currentTabIndex = 0

    /**
     * Fetches bookings from the repository.
     */
    fun loadCallHistory() {
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                // Fetch unified bookings
                fullList = repository.getBookingsWithAdvisorDetails()
                
                // Apply current filter
                filterByTab(currentTabIndex)
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load history: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun filterByTab(tabIndex: Int) {
        currentTabIndex = tabIndex
        val filtered = when (tabIndex) {
            0 -> fullList.filter { 
                val status = it.first.bookingStatus.lowercase()
                status == "pending" || status == "accepted" || status == "upcoming" 
            }
            1 -> fullList.filter { 
                val status = it.first.bookingStatus.lowercase()
                status == "completed" || status == "ended" || status == "end" 
            }
            2 -> fullList.filter { 
                val status = it.first.bookingStatus.lowercase()
                status == "cancelled" || status == "rejected" || status == "expired"
            }
            else -> fullList
        }
        _callHistoryList.value = filtered
    }

    /**
     * Cancels a booking and refreshes the list.
     */
    fun cancelBooking(booking: com.example.associate.DataClass.SessionBookingDataClass) {
        _isLoading.value = true
        viewModelScope.launch {
            val success = repository.cancelBooking(booking.bookingId, booking.urgencyLevel)
            if (success) {
                // Refresh data
                loadCallHistory()
            } else {
                _isLoading.value = false
                _errorMessage.value = "Failed to cancel booking"
            }
        }
    }

    fun submitReview(booking: com.example.associate.DataClass.SessionBookingDataClass, ratingValue: Float, reviewText: String, callback: (Boolean) -> Unit) {
        _isLoading.value = true
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        
        if (userId.isEmpty()) {
            _errorMessage.value = "User not logged in"
            _isLoading.value = false
            callback(false)
            return
        }

        val rating = com.example.associate.DataClass.Rating(
            advisorId = booking.advisorId,
            userId = userId,
            rating = ratingValue,
            review = reviewText,
            callId = booking.bookingId, // Using bookingId as callId reference
            timestamp = com.google.firebase.Timestamp.now()
        )

        val ratingRepo = com.example.associate.Repositories.RatingRepository()
        ratingRepo.saveRating(rating) { success, error ->
            _isLoading.value = false
            if (success) {
                callback(true)
            } else {
                _errorMessage.value = error ?: "Failed to submit review"
                callback(false)
            }
        }
    }
}
