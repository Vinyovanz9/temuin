package com.temuin.temuin.data.model

import kotlinx.serialization.Serializable

enum class NotificationType {
    SCHEDULE_INVITE,
    SCHEDULE_UPDATE,
    SCHEDULE_CANCELLED,
    SCHEDULE_REMINDER
}

enum class NotificationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    READ
}

@Serializable
data class Notification(
    val id: String = "",
    val type: NotificationType = NotificationType.SCHEDULE_INVITE,
    val timestamp: Long = System.currentTimeMillis(),
    val senderId: String = "",
    val scheduleId: String? = null,
    val title: String = "",
    val startTime: Long = 0,
    val status: NotificationStatus = NotificationStatus.PENDING
)