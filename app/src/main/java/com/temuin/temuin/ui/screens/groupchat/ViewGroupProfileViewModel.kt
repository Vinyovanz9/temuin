package com.temuin.temuin.ui.screens.groupchat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.temuin.temuin.data.model.GroupMember
import com.temuin.temuin.data.model.GroupProfileInfo
import com.temuin.temuin.data.model.MessageStatus
import com.temuin.temuin.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewGroupProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _groupInfo = MutableStateFlow<GroupProfileInfo?>(null)
    val groupInfo = _groupInfo.asStateFlow()

    private val _members = MutableStateFlow<List<GroupMember>>(emptyList())
    val members = _members.asStateFlow()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun isCurrentUserAdmin(): Boolean {
        val currentUser = auth.currentUser?.uid
        return _groupInfo.value?.createdBy == currentUser
    }

    fun loadGroupProfile(groupId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val groupRef = database.reference.child("group_chats").child(groupId)
                
                groupRef.get().addOnSuccessListener { groupSnapshot ->
                    val name = groupSnapshot.child("name").getValue(String::class.java) ?: "Unknown Group"
                    val profileImage = groupSnapshot.child("profileImage").getValue(String::class.java)
                    val createdBy = groupSnapshot.child("createdBy").getValue(String::class.java) ?: ""
                    val createdAt = groupSnapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                    val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                    val memberIds = groupSnapshot.child("members").getValue(membersType)?.keys?.toList() ?: emptyList()

                    // Get creator's name
                    database.reference.child("users").child(createdBy).child("name")
                        .get()
                        .addOnSuccessListener { creatorSnapshot ->
                            val creatorName = creatorSnapshot.getValue(String::class.java) ?: "Unknown"
                            
                            _groupInfo.value = GroupProfileInfo(
                                id = groupId,
                                name = name,
                                profileImage = profileImage,
                                createdBy = createdBy,
                                creatorName = creatorName,
                                createdAt = createdAt
                            )

                            // Load members info
                            loadMembersInfo(memberIds, createdBy)
                            // Load friends for add member functionality
                            loadFriends(memberIds)
                        }
                }.addOnFailureListener { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    private fun loadMembersInfo(memberIds: List<String>, adminId: String) {
        if (memberIds.isEmpty()) {
            _members.value = emptyList()
            _isLoading.value = false
            return
        }

        database.reference.child("users")
            .get()
            .addOnSuccessListener { usersSnapshot ->
                val membersList = mutableListOf<GroupMember>()
                
                for (userSnapshot in usersSnapshot.children) {
                    val userId = userSnapshot.key
                    if (userId in memberIds) {
                        val name = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                        val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                        val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                        val status = userSnapshot.child("status").getValue(String::class.java) ?: ""
                        
                        membersList.add(
                            GroupMember(
                                userId = userId ?: "",
                                name = name,
                                phoneNumber = phoneNumber,
                                profileImage = profileImage,
                                isAdmin = userId == adminId,
                                status = status
                            )
                        )
                    }
                }
                
                _members.value = membersList.sortedWith(
                    compareByDescending<GroupMember> { it.isAdmin }
                        .thenBy { it.name }
                )
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                _error.value = e.message
                _isLoading.value = false
            }
    }

    private fun loadFriends(currentMemberIds: List<String>) {
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                database.reference
                    .child("users")
                    .child(currentUserId)
                    .child("friends")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val friendsList = mutableListOf<User>()
                        val friendIds = snapshot.children.mapNotNull { it.key }
                        
                        if (friendIds.isEmpty()) {
                            _friends.value = emptyList()
                            return@addOnSuccessListener
                        }

                        database.reference.child("users").get().addOnSuccessListener { usersSnapshot ->
                            for (userSnapshot in usersSnapshot.children) {
                                val userId = userSnapshot.key
                                // Only include friends who are not already group members
                                if (userId in friendIds && userId !in currentMemberIds) {
                                    try {
                                        // Extract user data manually to avoid deserialization issues
                                        val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                                        val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                                        val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                                        val status = userSnapshot.child("status").getValue(String::class.java) ?: ""

                                        val friend = User(
                                            userId = userId ?: "",
                                            name = name,
                                            phoneNumber = phoneNumber,
                                            profileImage = profileImage,
                                            status = status,
                                            friends = emptyMap() // We don't need friends data for friend list display
                                        )
                                        friendsList.add(friend)
                                    } catch (e: Exception) {
                                        Log.e("ViewGroupProfileVM", "Error loading friend data: ${e.message}")
                                        // Continue with next friend instead of breaking the whole loop
                                        continue
                                    }
                                }
                            }
                            _friends.value = friendsList.sortedBy { it.name }
                        }
                        .addOnFailureListener { e -> _error.value = e.message }
                    }
                    .addOnFailureListener { e -> _error.value = e.message }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun addMemberToGroup(groupId: String, newMemberId: String) {
        viewModelScope.launch {
            try {
                // Validate member ID
                if (newMemberId.isBlank()) {
                    _error.value = "Invalid member ID"
                    return@launch
                }

                // First, verify the user exists
                database.reference.child("users").child(newMemberId).get()
                    .addOnSuccessListener { userSnapshot ->
                        if (!userSnapshot.exists()) {
                            _error.value = "User not found"
                            return@addOnSuccessListener
                        }

                        // First, add member to group's member list
                        database.reference.child("group_chats").child(groupId).child("members")
                            .child(newMemberId)
                            .setValue(true)
                            .addOnSuccessListener {
                                // After member is added to group, update their groupChats list
                                val userGroupChatUpdates = hashMapOf<String, Any>(
                                    "unreadCount" to 1,
                                    "isPinned" to false,
                                    "lastMessageStatus" to MessageStatus.SENT.name,
                                    "lastMessage" to "Added to group",
                                    "lastMessageSenderId" to (auth.currentUser?.uid ?: ""),
                                    "lastMessageTimestamp" to System.currentTimeMillis(),
                                    "statusPreserved" to false
                                )
                                
                                database.reference.child("users").child(newMemberId).child("groupChats").child(groupId)
                                    .updateChildren(userGroupChatUpdates)
                                    .addOnSuccessListener {
                                        Log.d("ViewGroupProfileVM", "Successfully added member $newMemberId to group $groupId")
                                        
                                        // Add system message about new member
                                        addMemberJoinedMessage(groupId, newMemberId)
                                        
                                        // Reload the group profile to update UI
                                        loadGroupProfile(groupId)
                                    }
                                    .addOnFailureListener { e ->
                                        _error.value = "Failed to update member's group chat: ${e.message}"
                                        Log.e("ViewGroupProfileVM", "Failed to update member's group chat: ${e.message}")
                                    }
                            }
                            .addOnFailureListener { e ->
                                _error.value = "Failed to add member: ${e.message}"
                                Log.e("ViewGroupProfileVM", "Failed to add member: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to verify user: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to verify user: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error adding member: ${e.message}"
                Log.e("ViewGroupProfileVM", "Error in addMemberToGroup: ${e.message}")
            }
        }
    }

    private fun addMemberJoinedMessage(groupId: String, newMemberId: String) {
        // Get new member's name first
        database.reference.child("users").child(newMemberId).child("name").get()
            .addOnSuccessListener { nameSnapshot ->
                val memberName = nameSnapshot.getValue(String::class.java) ?: "Someone"
                val currentUserId = auth.currentUser?.uid ?: return@addOnSuccessListener
                
                // Add system message about member joining
                val messageRef = database.reference.child("group_messages").child(groupId).push()
                val messageId = messageRef.key ?: return@addOnSuccessListener
                
                // Get all group members to set up memberStatus
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                        val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                        val allMembers = membersMap.keys.toList()
                        
                        if (allMembers.isNotEmpty()) {
                            val memberStatusMap = allMembers.associateWith { MessageStatus.SENT.name }
                            val joinMessage = "$memberName joined"
                            
                            val messageData = hashMapOf<String, Any>(
                                "content" to joinMessage,
                                "senderId" to currentUserId, // Current user added them
                                "timestamp" to System.currentTimeMillis(),
                                "status" to MessageStatus.SENT.name,
                                "memberStatus" to memberStatusMap,
                                "addedUserId" to newMemberId // Store the ID of the added user
                            )
                            
                            val messageUpdates = hashMapOf<String, Any>()
                            messageUpdates["group_messages/$groupId/$messageId"] = messageData
                            messageUpdates["group_chats/$groupId/lastMessage"] = joinMessage
                            messageUpdates["group_chats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                            messageUpdates["group_chats/$groupId/lastMessageSenderId"] = currentUserId
                            messageUpdates["group_chats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                            
                            // Update each member's view
                            allMembers.forEach { memberId ->
                                val displayMessage = if (memberId == currentUserId) "You: $joinMessage" else joinMessage
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessage"] = displayMessage
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageSenderId"] = currentUserId
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                                
                                // Increment unread count for everyone except the person who added
                                if (memberId != currentUserId) {
                                    database.reference.child("users").child(memberId).child("groupChats").child(groupId).child("unreadCount").get()
                                        .addOnSuccessListener { unreadSnapshot ->
                                            val currentUnread = unreadSnapshot.getValue(Long::class.java) ?: 0L
                                            messageUpdates["users/$memberId/groupChats/$groupId/unreadCount"] = currentUnread + 1
                                        }
                                }
                            }
                            
                            database.reference.updateChildren(messageUpdates)
                        }
                    }
            }
    }

    fun removeMemberFromGroup(groupId: String, memberId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid
                if (!isCurrentUserAdmin()) {
                    _error.value = "Only admin can remove members"
                    return@launch
                }

                // Check if trying to remove admin (themselves)
                if (memberId == currentUserId) {
                    _error.value = "Admin cannot remove themselves. Use Exit Group instead."
                    return@launch
                }

                val updates = hashMapOf<String, Any?>()
                
                // Remove member from group's member list
                updates["group_chats/$groupId/members/$memberId"] = null
                
                // Remove group from member's groupChats list
                updates["users/$memberId/groupChats/$groupId"] = null
                
                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("ViewGroupProfileVM", "Successfully removed member $memberId from group $groupId")
                        
                        // Add system message about member being removed
                        addMemberRemovedMessage(groupId, memberId)
                        
                        // Reload the group profile to update UI
                        loadGroupProfile(groupId)
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to remove member: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to remove member: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error removing member: ${e.message}"
                Log.e("ViewGroupProfileVM", "Error in removeMemberFromGroup: ${e.message}")
            }
        }
    }

    private fun addMemberRemovedMessage(groupId: String, removedMemberId: String) {
        // Get removed member's name first
        database.reference.child("users").child(removedMemberId).child("name").get()
            .addOnSuccessListener { nameSnapshot ->
                val memberName = nameSnapshot.getValue(String::class.java) ?: "Someone"
                val currentUserId = auth.currentUser?.uid ?: return@addOnSuccessListener
                
                // Add system message about member being removed
                val messageRef = database.reference.child("group_messages").child(groupId).push()
                val messageId = messageRef.key ?: return@addOnSuccessListener
                
                // Get remaining group members to set up memberStatus
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                        val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                        val remainingMembers = membersMap.keys.toList()
                        
                        if (remainingMembers.isNotEmpty()) {
                            val memberStatusMap = remainingMembers.associateWith { MessageStatus.SENT.name }
                            val removedMessage = "You removed $memberName"
                            
                            val messageData = hashMapOf<String, Any>(
                                "content" to removedMessage,
                                "senderId" to currentUserId,
                                "timestamp" to System.currentTimeMillis(),
                                "status" to MessageStatus.SENT.name,
                                "memberStatus" to memberStatusMap
                            )
                            
                            val messageUpdates = hashMapOf<String, Any>()
                            messageUpdates["group_messages/$groupId/$messageId"] = messageData
                            messageUpdates["group_chats/$groupId/lastMessage"] = removedMessage
                            messageUpdates["group_chats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                            messageUpdates["group_chats/$groupId/lastMessageSenderId"] = currentUserId
                            messageUpdates["group_chats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                            
                            // Update each remaining member's view
                            remainingMembers.forEach { memberId ->
                                val displayMessage = if (memberId == currentUserId) "You: $removedMessage" else "$memberName was removed"
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessage"] = displayMessage
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageSenderId"] = currentUserId
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                                
                                // Increment unread count for everyone except admin who removed
                                if (memberId != currentUserId) {
                                    database.reference.child("users").child(memberId).child("groupChats").child(groupId).child("unreadCount").get()
                                        .addOnSuccessListener { unreadSnapshot ->
                                            val currentUnread = unreadSnapshot.getValue(Long::class.java) ?: 0L
                                            messageUpdates["users/$memberId/groupChats/$groupId/unreadCount"] = currentUnread + 1
                                        }
                                }
                            }
                            
                            database.reference.updateChildren(messageUpdates)
                        }
                    }
            }
    }

    fun leaveGroup(groupId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // First, get user's name and add the "left" message while user is still a member
                database.reference.child("users").child(currentUserId).child("name").get()
                    .addOnSuccessListener { nameSnapshot ->
                        val userName = nameSnapshot.getValue(String::class.java) ?: "Someone"
                        
                        // Add system message about user leaving
                        val messageRef = database.reference.child("group_messages").child(groupId).push()
                        val messageId = messageRef.key ?: return@addOnSuccessListener
                        
                        // Get all group members to set up memberStatus
                        database.reference.child("group_chats").child(groupId).child("members").get()
                            .addOnSuccessListener { membersSnapshot ->
                                val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                                val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                                val allMembers = membersMap.keys.toList()

                                if (allMembers.isNotEmpty()) {
                                            val memberStatusMap = allMembers.associateWith { MessageStatus.SENT.name }
                                    val leftMessage = "$userName left"
                                    val messageData = hashMapOf<String, Any>(
                                        "content" to leftMessage,
                                        "senderId" to currentUserId,
                                        "timestamp" to System.currentTimeMillis(),
                                        "status" to MessageStatus.SENT.name,
                                        "memberStatus" to memberStatusMap,
                                        "type" to "system"
                                    )

                                            val messageUpdates = hashMapOf<String, Any>()
                                            messageUpdates["group_messages/$groupId/$messageId"] = messageData
                                            messageUpdates["group_chats/$groupId/lastMessage"] = leftMessage
                                            messageUpdates["group_chats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                                            messageUpdates["group_chats/$groupId/lastMessageSenderId"] = currentUserId
                                            messageUpdates["group_chats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name

                                            // Update each member's view
                                            allMembers.forEach { memberId ->
                                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessage"] = leftMessage
                                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageSenderId"] = currentUserId
                                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name

                                                // Increment unread count for everyone except the leaving user
                                                if (memberId != currentUserId) {
                                                    database.reference.child("users").child(memberId).child("groupChats").child(groupId).child("unreadCount").get()
                                                        .addOnSuccessListener { unreadSnapshot ->
                                                            val currentUnread = unreadSnapshot.getValue(Long::class.java) ?: 0L
                                                            messageUpdates["users/$memberId/groupChats/$groupId/unreadCount"] = currentUnread + 1
                                                        }
                                                }
                                            }

                                            // Send the message first
                                            database.reference.updateChildren(messageUpdates)
                                                .addOnSuccessListener {
                                                    // After message is sent, proceed with removing the user
                                                    if (allMembers.size <= 1) {
                                    // User is the last member, delete the entire group
                                    deleteEntireGroup(groupId, currentUserId, onSuccess)
                                } else {
                                    // There are other members, just remove this user
                                    removeUserFromGroup(groupId, currentUserId, onSuccess)
                                }
                            }
                            .addOnFailureListener { e ->
                                                    _error.value = "Failed to add left message: ${e.message}"
                                                    Log.e("ViewGroupProfileVM", "Failed to add left message: ${e.message}")
                                                }
                                        } else {
                                            // No members found, just remove the user
                                            deleteEntireGroup(groupId, currentUserId, onSuccess)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        _error.value = "Failed to get members: ${e.message}"
                                        Log.e("ViewGroupProfileVM", "Failed to get members: ${e.message}")
                                    }
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to get user name: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to get user name: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error leaving group: ${e.message}"
                Log.e("ViewGroupProfileVM", "Error in leaveGroup: ${e.message}")
            }
        }
    }

    private fun removeUserFromGroup(groupId: String, userId: String, onSuccess: () -> Unit) {
        // Check if leaving user is admin and transfer admin rights if needed
        database.reference.child("group_chats").child(groupId).get()
            .addOnSuccessListener { groupSnapshot ->
                val createdBy = groupSnapshot.child("createdBy").getValue(String::class.java)
                val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                val membersMap = groupSnapshot.child("members").getValue(membersType) ?: emptyMap()
                val allMembers = membersMap.keys.toList()
                
                val updates = hashMapOf<String, Any?>()
                
                // If leaving user is admin and there are other members, transfer admin to first member
                if (createdBy == userId && allMembers.size > 1) {
                    val newAdmin = allMembers.firstOrNull { it != userId }
                    if (newAdmin != null) {
                        updates["group_chats/$groupId/createdBy"] = newAdmin
                        Log.d("ViewGroupProfileVM", "Transferring admin from $userId to $newAdmin")
                    }
                }
                
                // Remove user from group's member list
                updates["group_chats/$groupId/members/$userId"] = null
                
                // Remove group from user's groupChats list
                updates["users/$userId/groupChats/$groupId"] = null
                
                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("ViewGroupProfileVM", "Successfully left group $groupId")
                        
                        // Add "user left" message to the group
                        addUserLeftMessage(groupId, userId)
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to leave group: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to leave group: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                _error.value = "Failed to check group info: ${e.message}"
                Log.e("ViewGroupProfileVM", "Failed to check group info: ${e.message}")
            }
    }

    private fun deleteEntireGroup(groupId: String, userId: String, onSuccess: () -> Unit) {
        // First get all members to remove the group from their lists
        database.reference.child("group_chats").child(groupId).child("members").get()
            .addOnSuccessListener { membersSnapshot ->
                val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                val allMembers = membersMap.keys.toList()
                
                val updates = hashMapOf<String, Any?>()
        
                // Delete the entire group chat
                updates["group_chats/$groupId"] = null
        
                // Delete all group messages
                updates["group_messages/$groupId"] = null

                // Remove group from all members' groupChats lists
                allMembers.forEach { memberId ->
                    updates["users/$memberId/groupChats/$groupId"] = null
                }

                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("ViewGroupProfileVM", "Successfully deleted empty group $groupId")
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to delete group: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to delete group: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                _error.value = "Failed to get group members: ${e.message}"
                Log.e("ViewGroupProfileVM", "Failed to get group members: ${e.message}")
            }
    }

    private fun addUserLeftMessage(groupId: String, userId: String) {
        // Get user's name first, then add the system message
        database.reference.child("users").child(userId).child("name").get()
            .addOnSuccessListener { nameSnapshot ->
                val userName = nameSnapshot.getValue(String::class.java) ?: "Someone"
                
                // Add system message about user leaving
                val messageRef = database.reference.child("group_messages").child(groupId).push()
                val messageId = messageRef.key ?: return@addOnSuccessListener
                
                // Get remaining group members to set up memberStatus
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                        val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                        val remainingMembers = membersMap.keys.toList()
                        
                        if (remainingMembers.isNotEmpty()) {
                            val memberStatusMap = remainingMembers.associateWith { MessageStatus.SENT.name }
                            val leftMessage = if (userId == auth.currentUser?.uid) "You left" else "$userName left"
                            
                            val messageData = hashMapOf<String, Any>(
                                "content" to leftMessage,
                                "senderId" to userId,
                                "timestamp" to System.currentTimeMillis(),
                                "status" to MessageStatus.SENT.name,
                                "memberStatus" to memberStatusMap,
                                "type" to "system"
                            )
                            
                            val messageUpdates = hashMapOf<String, Any>()
                            messageUpdates["group_messages/$groupId/$messageId"] = messageData
                            messageUpdates["group_chats/$groupId/lastMessage"] = leftMessage
                            messageUpdates["group_chats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                            messageUpdates["group_chats/$groupId/lastMessageSenderId"] = userId
                            messageUpdates["group_chats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                            
                            // Update each remaining member's view
                            remainingMembers.forEach { memberId ->
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessage"] = leftMessage
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageTimestamp"] = System.currentTimeMillis()
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageSenderId"] = userId
                                messageUpdates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                                
                                // Increment unread count for remaining members
                                database.reference.child("users").child(memberId).child("groupChats").child(groupId).child("unreadCount").get()
                                    .addOnSuccessListener { unreadSnapshot ->
                                        val currentUnread = unreadSnapshot.getValue(Long::class.java) ?: 0L
                                        messageUpdates["users/$memberId/groupChats/$groupId/unreadCount"] = currentUnread + 1
                                    }
                            }
                            
                            database.reference.updateChildren(messageUpdates)
                                .addOnSuccessListener {
                                    Log.d("ViewGroupProfileVM", "Successfully added left message for $userName")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ViewGroupProfileVM", "Failed to add left message: ${e.message}")
            }
    }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ViewGroupProfileVM", "Failed to get remaining members: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ViewGroupProfileVM", "Failed to get user name: ${e.message}")
            }
    }

    fun updateGroupProfileImage(groupId: String, profileImage: String?) {
        viewModelScope.launch {
            try {
                val updates = hashMapOf<String, Any?>()  // Change Any to Any? to allow null values
                updates["group_chats/$groupId/profileImage"] = profileImage
                
                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("ViewGroupProfileVM", "Successfully updated group profile image")
                        // Reload the group profile to update UI
                        loadGroupProfile(groupId)
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to update profile image: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to update profile image: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error updating profile image: ${e.message}"
                Log.e("ViewGroupProfileVM", "Error in updateGroupProfileImage: ${e.message}")
            }
        }
    }
    
    fun updateGroupName(groupId: String, newName: String) {
        viewModelScope.launch {
            try {
                val updates = hashMapOf<String, Any>()
                updates["group_chats/$groupId/name"] = newName
                
                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("ViewGroupProfileVM", "Successfully updated group name")
                        // Reload the group profile to update UI
                        loadGroupProfile(groupId)
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to update group name: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to update group name: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error updating group name: ${e.message}"
                Log.e("ViewGroupProfileVM", "Error in updateGroupName: ${e.message}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun addAdminChangedMessage(groupId: String, newAdminId: String) {
        // Get new admin's name first
        database.reference.child("users").child(newAdminId).child("name").get()
            .addOnSuccessListener { newAdminSnapshot ->
                val newAdminName = newAdminSnapshot.getValue(String::class.java) ?: "Unknown"
                val currentUserId = auth.currentUser?.uid ?: return@addOnSuccessListener
                
                // Get remaining members to update their last message
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                        val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                        val remainingMembers = membersMap.keys.toList()
                        
                        // Create message updates
                        val messageId = database.reference.child("group_messages").child(groupId).push().key ?: return@addOnSuccessListener
                        val timestamp = System.currentTimeMillis()
                        
                        val messageUpdates = hashMapOf<String, Any>()
                        
                        // Add the message to group_messages
                        messageUpdates["group_messages/$groupId/$messageId"] = mapOf(
                            "id" to messageId,
                            "content" to "$newAdminName is now the group admin",
                            "senderId" to currentUserId,
                            "senderName" to "System",
                            "timestamp" to timestamp,
                            "status" to MessageStatus.SENT.name,
                            "newAdminId" to newAdminId
                        )
                        
                        // Update last message for all members
                        remainingMembers.forEach { memberId ->
                            val displayMessage = if (memberId == newAdminId) "You are now the group admin" else "$newAdminName is now the group admin"
                            messageUpdates["users/$memberId/groupChats/$groupId/lastMessage"] = displayMessage
                            messageUpdates["users/$memberId/groupChats/$groupId/lastMessageTimestamp"] = timestamp
                            messageUpdates["users/$memberId/groupChats/$groupId/lastMessageSenderId"] = currentUserId
                            messageUpdates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = MessageStatus.SENT.name
                            
                            // Increment unread count for everyone except current user
                            if (memberId != currentUserId) {
                                database.reference.child("users").child(memberId).child("groupChats").child(groupId).child("unreadCount").get()
                                    .addOnSuccessListener { unreadSnapshot ->
                                        val currentUnread = unreadSnapshot.getValue(Long::class.java) ?: 0L
                                        messageUpdates["users/$memberId/groupChats/$groupId/unreadCount"] = currentUnread + 1
                                    }
                            }
                        }
                        
                        database.reference.updateChildren(messageUpdates)
                    }
            }
    }

    fun transferAdminRights(groupId: String, newAdminId: String) {
        viewModelScope.launch {
            try {
                if (!isCurrentUserAdmin()) {
                    _error.value = "Only admin can transfer admin rights"
                    return@launch
                }

                // Get new admin's name first
                database.reference.child("users").child(newAdminId).child("name").get()
                    .addOnSuccessListener { nameSnapshot ->
                        val newAdminName = nameSnapshot.getValue(String::class.java) ?: return@addOnSuccessListener

                        val updates = hashMapOf<String, Any>()
                        updates["group_chats/$groupId/createdBy"] = newAdminId

                        database.reference.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d("ViewGroupProfileVM", "Successfully transferred admin rights to $newAdminId")
                                // Add admin changed message with the correct name
                                addAdminChangedMessage(groupId, newAdminId)
                                // Reload the group profile to update UI
                                loadGroupProfile(groupId)
                            }
                            .addOnFailureListener { e ->
                                _error.value = "Failed to transfer admin rights: ${e.message}"
                                Log.e("ViewGroupProfileVM", "Failed to transfer admin rights: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to get new admin's name: ${e.message}"
                        Log.e("ViewGroupProfileVM", "Failed to get new admin's name: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error transferring admin rights: ${e.message}"
                Log.e("ViewGroupProfileVM", "Error in transferAdminRights: ${e.message}")
            }
        }
    }
} 