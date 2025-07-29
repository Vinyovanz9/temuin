package com.temuin.temuin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    val id: String = "",
    val userId: String = "",  // Creator/owner of the schedule
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startTime: Long = 0,
    val endTime: Long = 0,
    val reminderHours: Int = 1, // hours before start time
    val allowReschedule: Boolean = true,
    val participants: List<String> = emptyList(), // List of participant user IDs
    val participantStatus: Map<String, ParticipantStatus> = emptyMap(), // Track each participant's response
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Location data for Google Maps integration

/*
@Serializable
data class LocationData(
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeId: String? = null // Google Places ID for additional details
)
*/

enum class ScheduleStatus {
    PENDING,    // Initial state when created with participants or Update schedule
    ACTIVE,     // When at least one participant accepts or no participants
    ONGOING,    // When the schedule is currently in progress
    CANCELLED,  // When all participants decline or creator cancels
    COMPLETED   // When the schedule time has passed
}

enum class ParticipantStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

enum class ReminderTime(val hours: Int, val displayText: String) {
    ONE_HOUR(1, "1 hour before"),
    THREE_HOURS(3, "3 hours before"),
    SIX_HOURS(6, "6 hours before"),
    TWELVE_HOURS(12, "12 hours before"),
    TWENTY_FOUR_HOURS(24, "24 hours before")
} 