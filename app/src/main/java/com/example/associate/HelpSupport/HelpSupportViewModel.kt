package com.example.associate.HelpSupport

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HelpSupportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HelpSupportRepository()
    
    private val _submitStatus = MutableLiveData<Result<Boolean>>()
    val submitStatus: LiveData<Result<Boolean>> = _submitStatus

    private val _tickets = MutableLiveData<Result<List<TicketDataClass>>>()
    val tickets: LiveData<Result<List<TicketDataClass>>> = _tickets
    
    // For UI State loading
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun submitTicket(ticket: TicketDataClass, imageUris: List<Uri>) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.submitTicket(ticket, imageUris, getApplication())
            _submitStatus.value = result
            _isLoading.value = false
        }
    }

    fun getMyTickets(userId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.getMyTickets(userId)
            _tickets.value = result
            _isLoading.value = false
        }
    }
}

// Updated for repository activity
