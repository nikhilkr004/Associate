package com.example.associate.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.associate.DataClass.AdvisorDataClass
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.associate.Repositories.BookingRepository

class ScheduleBookingViewModel : ViewModel() {

    private val bookingRepository = BookingRepository()


    private val _selectedDate = MutableLiveData<Calendar?>()
    val selectedDate: LiveData<Calendar?> = _selectedDate

    private val _availableSlots = MutableLiveData<List<String>>()
    val availableSlots: LiveData<List<String>> = _availableSlots

    private val _selectedSlot = MutableLiveData<String?>()
    val selectedSlot: LiveData<String?> = _selectedSlot

    private val _selectedType = MutableLiveData<BookingType>(BookingType.AUDIO) // Default to Audio as in screenshot
    val selectedType: LiveData<BookingType> = _selectedType

    private val _totalPrice = MutableLiveData<Int>(0)
    val totalPrice: LiveData<Int> = _totalPrice
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    enum class BookingType {
        CHAT, AUDIO, VIDEO
    }

    private lateinit var advisor: AdvisorDataClass

    fun init(advisorData: AdvisorDataClass) {
        this.advisor = advisorData
        updatePrice(BookingType.AUDIO) // Default
        // No date selected initially
        _selectedDate.value = null
        _availableSlots.value = emptyList()
    }

    fun selectDate(calendar: Calendar) {
        _selectedDate.value = calendar
        // Launch coroutine to handle async fetching of booked slots
        viewModelScope.launch {
            generateSlotsForDate(calendar)
        }
        _selectedSlot.value = null // Reset slot when date changes
    }

    fun selectSlot(slot: String) {
        _selectedSlot.value = slot
    }

    fun selectType(type: BookingType) {
        _selectedType.value = type
        updatePrice(type)
        
        // Data Refresh: Reset date to null (nothing selected)
        _selectedDate.value = null
        _availableSlots.value = emptyList()
        _selectedSlot.value = null
    }

    private fun updatePrice(type: BookingType) {
        val price = when (type) {
            BookingType.CHAT -> advisor.pricingInfo.scheduledChatFee
            BookingType.AUDIO -> advisor.pricingInfo.scheduledAudioFee
            BookingType.VIDEO -> advisor.pricingInfo.scheduledVideoFee
        }
        _totalPrice.value = price
    }

    private suspend fun generateSlotsForDate(date: Calendar) {
        _isLoading.value = true
        val schedule = advisor.availabilityInfo.virtualSchedule
        
        // 0. Check Active Days
        val dayOfWeekInt = date.get(Calendar.DAY_OF_WEEK)
        val dayString = when (dayOfWeekInt) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> ""
        }
        
        // If activeDays is not empty and dayString is NOT in it -> No slots
        if (schedule.activeDays.isNotEmpty() && !schedule.activeDays.contains(dayString)) {
             _availableSlots.value = emptyList()
             _isLoading.value = false
             return
        }

        // Fetch Booked Slots from Firestore
        val dateFormatForDb = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateString = dateFormatForDb.format(date.time)
        val bookedSlots = bookingRepository.getBookedSlots(advisor.basicInfo.id, dateString)

        // 1. Priority: Use pre-generated slots if available
        if (schedule.generatedSlots.isNotEmpty()) {
            val validSlots = schedule.generatedSlots.filter { !bookedSlots.contains(it) }
            _availableSlots.value = validSlots
            _isLoading.value = false
            return
        }

        // 2. Strict Start/End Time Usage
        val startStr = schedule.startTime
        val endStr = schedule.endTime
        
        if (startStr.isEmpty() || endStr.isEmpty()) {
            _availableSlots.value = emptyList() // No availability set
            _isLoading.value = false
            return
        }

        val duration = 30 // User request: "show only 30 min slot"

        val slots = mutableListOf<String>()
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault()) // Assumes 24h format in DB "14:30"
        val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Display "02:30 PM"

        try {
            val startCal = Calendar.getInstance().apply {
                time = dateFormat.parse(startStr)!!
                set(Calendar.YEAR, date.get(Calendar.YEAR))
                set(Calendar.MONTH, date.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
            }

            val endCal = Calendar.getInstance().apply {
                time = dateFormat.parse(endStr)!!
                set(Calendar.YEAR, date.get(Calendar.YEAR))
                set(Calendar.MONTH, date.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
            }

            val now = Calendar.getInstance()
            
            // ✅ Compare Dates Properly (Year + Month + Day)
            val isToday = date.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                         date.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                         date.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
            
            while (startCal.before(endCal)) {
                val slotStart = displayFormat.format(startCal.time)
                
                val tempCal = startCal.clone() as Calendar
                tempCal.add(Calendar.MINUTE, duration)
                
                if (tempCal.after(endCal)) break
                
                val slotEnd = displayFormat.format(tempCal.time)
                
                // ✅ Filter Past Slots: Only include slots that are in the future
                val shouldIncludeSlot = if (isToday) {
                    // For today: Slot start time must be AFTER current time
                    startCal.after(now)
                } else {
                    // For future dates: Include all slots
                    true
                }
                
                if (shouldIncludeSlot) {
                     val fullSlot = "$slotStart - $slotEnd"
                     if (!bookedSlots.contains(fullSlot)) {
                        slots.add(fullSlot)
                     }
                }
               
                startCal.add(Calendar.MINUTE, duration)
            }
            
            _availableSlots.value = slots

        } catch (e: Exception) {
            _availableSlots.value = emptyList()
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    fun validateBooking(agenda: String): String? {
        if (_selectedSlot.value == null) return "Please select a time slot"
        if (agenda.isBlank()) return "Please enter an agenda"
        return null
    }
}
