package com.temuin.temuin.domain.repository

interface ChatRepository {

    fun initializeChat(
        otherUserId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun sendMessage(
        recipientId: String,
        message: String,
        hasInternetConnection: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun markMessageAsDelivered(recipientId: String, messageId: String)
    fun markMessageAsRead(originalSenderId: String, messageId: String)
    fun markAllMessagesAsRead(chatId: String)
    fun cleanupMessageListeners(chatId: String)
    fun updateLastSeen()
}