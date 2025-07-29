package com.temuin.temuin.domain.repository

interface GroupChatRepository {

    fun sendGroupMessage(
        groupId: String,
        message: String,
        messageId: String,
        hasInternetConnection: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun markGroupMessageAsDelivered(groupId: String, messageId: String, userId: String, triggerRecalculation: Boolean = true)
    fun markGroupMessageAsRead(groupId: String, messageId: String, userId: String, triggerRecalculation: Boolean = true)
    fun recalculateAndSetOverallGroupMessageStatus(groupId: String, messageId: String)
    fun setupGroupMessageDeliveryTracking(groupId: String)
    fun removeDeliveryTrackingListener(groupId: String)
}