package com.temuin.temuin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val profileImage: String? = null,
    val friends: Map<String, Boolean> = emptyMap(),
    val status: String = "",
    val notifications: Map<String, Notification>? = null,
    val groupChats: Map<String, GroupMember>? = null,
    val fcmToken: String? = null
)