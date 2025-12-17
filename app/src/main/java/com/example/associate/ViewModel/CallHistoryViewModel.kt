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

    private val _callHistoryList = MutableLiveData<List<Pair<VideoCall, UserData?>>>()
    val callHistoryList: LiveData<List<Pair<VideoCall, UserData?>>> = _callHistoryList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * Fetches video calls from the repository.
     * Updates [callHistoryList], [isLoading], and [errorMessage].
     */
    fun loadCallHistory() {
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                val result = repository.getVideoCallsWithUserDetails()
                _callHistoryList.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load history: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
