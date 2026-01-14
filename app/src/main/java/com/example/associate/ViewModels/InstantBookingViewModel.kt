package com.example.associate.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.associate.DataClass.AdvisorDataClass

class InstantBookingViewModel : ViewModel() {

    private val _selectedType = MutableLiveData<BookingType>(BookingType.NONE)
    val selectedType: LiveData<BookingType> = _selectedType

    private val _totalPrice = MutableLiveData<Int>(0)
    val totalPrice: LiveData<Int> = _totalPrice
    
    // For now we assume a standard session or per minute. 
    // The prompt says "procint is per min", so we display per min price.
    // But how do we calculate total price? 
    // Assuming for now "Process" just initiates the booking, and the price shown continuously is the "per minute" rate,
    // OR maybe we need to multiply by duration? 
    // The image shows "₹ 0.0" at the bottom. 
    // Let's assume the bottom price updates based on selection.

    enum class BookingType {
        NONE, CHAT, AUDIO, VIDEO
    }

    fun selectType(type: BookingType, pricePerMin: Int) {
        _selectedType.value = type
        _totalPrice.value = pricePerMin // Showing rate for now as total, or 0 if none
    }

    fun validateBooking(agenda: String): String? {
        if (_selectedType.value == BookingType.NONE) {
            return "Please select an appointment type"
        }
        if (agenda.isBlank()) {
            return "Please enter an agenda"
        }
        return null
    }
}

// Updated for repository activity
