package com.example.associate.DataClass

import com.google.firebase.Timestamp

data class AdvisorStatusDataClass(
    val advisorId: String = "",
    var isActive: Boolean = false,
    var status: String = "offline",
    var startTime: Timestamp? = null,
    var endTime: Timestamp? = null,
    var lastActive: Timestamp = Timestamp.now(),
    var totalActiveTime: Long = 0,
    var sessionCount: Int = 0,
    var currentSessionId: String? = null,
    var availability: Map<String, Any> = emptyMap(),
    var statusMessage: String = "",
    var acceptNewBookings: Boolean = true,
    var maxParallelSessions: Int = 3,
    var currentActiveSessions: Int = 0,
    var settings: Map<String, Any> = mapOf(
        "auto_accept_booking" to false,
        "notification_enabled" to true,
        "max_daily_sessions" to 10,
        "break_time_minutes" to 15,
        "working_hours_start" to "09:00",
        "working_hours_end" to "18:00"
    )
) {
    fun startSession(statusType: String = "online", message: String = "") {
        this.isActive = true
        this.status = statusType
        this.startTime = Timestamp.now()
        this.lastActive = Timestamp.now()
        this.sessionCount++
        this.currentSessionId = generateSessionId()
        this.acceptNewBookings = statusType != "busy"
        this.statusMessage = message
    }

    fun endSession() {
        this.isActive = false
        this.status = "offline"
        this.endTime = Timestamp.now()

        startTime?.let { start ->
            endTime?.let { end ->
                val sessionDuration = end.toDate().time - start.toDate().time
                this.totalActiveTime += sessionDuration
            }
        }

        this.currentActiveSessions = 0
        this.currentSessionId = null
    }

    // 🔥 NEW: Check if advisor can accept new calls
    fun canAcceptNewCalls(): Boolean {
        return isActive &&
                acceptNewBookings &&
                currentActiveSessions < maxParallelSessions &&
                status != "busy" &&
                status != "away"
    }

    // 🔥 NEW: Start a call session
    fun startCallSession(bookingId: String) {
        this.currentActiveSessions++
        this.acceptNewBookings = currentActiveSessions < maxParallelSessions
        this.currentSessionId = bookingId
        this.status = if (currentActiveSessions >= maxParallelSessions) "busy" else "online"
    }

    // 🔥 NEW: End a call session
    fun endCallSession() {
        this.currentActiveSessions = maxOf(0, currentActiveSessions - 1)
        this.acceptNewBookings = currentActiveSessions < maxParallelSessions
        this.status = if (currentActiveSessions == 0) "online" else "busy"
        if (currentActiveSessions == 0) {
            this.currentSessionId = null
        }
    }

    // 🔥 NEW: Get availability status message
    fun getAvailabilityMessage(): String {
        return when {
            !isActive -> "Advisor is offline"
            status == "busy" -> "Advisor is busy in another call"
            currentActiveSessions >= maxParallelSessions -> "Advisor has reached maximum sessions"
            !acceptNewBookings -> "Advisor is not accepting new bookings"
            else -> "Available for booking"
        }
    }

    fun getFormattedTotalActiveTime(): String {
        val hours = totalActiveTime / (1000 * 60 * 60)
        val minutes = (totalActiveTime % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }

    fun getTodayActiveTime(): String {
        return getFormattedTotalActiveTime()
    }

    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_$advisorId"
    }

    companion object {
        fun createDefault(advisorId: String): AdvisorStatusDataClass {
            return AdvisorStatusDataClass(
                advisorId = advisorId,
                settings = mapOf(
                    "auto_accept_booking" to false,
                    "notification_enabled" to true,
                    "max_daily_sessions" to 10,
                    "break_time_minutes" to 15,
                    "working_hours_start" to "09:00",
                    "working_hours_end" to "18:00"
                )
            )
        }
    }
}
// Updated for repository activity
