package com.temuin.temuin.data.model

import com.temuin.temuin.ui.screens.chats.formatTimestamp
import kotlinx.serialization.Serializable

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    var status: MessageStatus = MessageStatus.SENT
)

data class GroupInfo(
    val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    val profileImage: String? = null,
    val createdBy: String = "",
    val createdAt: Long = 0L
)

data class GroupProfileInfo(
    val id: String = "",
    val name: String = "",
    val profileImage: String? = null,
    val createdBy: String = "",
    val creatorName: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class GroupMember(
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val profileImage: String? = null,
    val isAdmin: Boolean = false,
    val status: String = ""
)

data class GroupChatMessage(
    val id: String = "",
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0L,
    val isFromCurrentUser: Boolean = false,
    val senderProfileImage: String? = null,
    var status: MessageStatus = MessageStatus.SENT, // Made var to allow direct update
    val addedUserId: String? = null, // For tracking who was added in join messages
    val removedUserId: String? = null, // For tracking who was removed in remove messages
    val newAdminId: String? = null // For tracking who became admin in admin change messages
) {
    val formattedTime: String
        get() = formatTimestamp(timestamp)
}

data class ChatPreview(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val lastMessage: String,
    val timestamp: Long,
    val profilePicture: String = "",
    val isGroup: Boolean = false,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val lastMessageStatus: MessageStatus = MessageStatus.SENT,
    val lastMessageSenderId: String = ""
)

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
} 