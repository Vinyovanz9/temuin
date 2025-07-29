package com.temuin.temuin.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.temuin.temuin.data.model.ChatPreview
import com.temuin.temuin.data.model.MessageStatus
import com.temuin.temuin.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount = _totalUnreadCount.asStateFlow()

    private var userChatsListener: com.google.firebase.database.ValueEventListener? = null
    private var userSpecificGroupDataListener = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private var groupChatDetailsListener = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()

    // Keep track of both private and group chats
    private val privateChats = mutableListOf<ChatPreview>()
    private val groupChats = mutableListOf<ChatPreview>()

    // Add a map to track all message listeners for private chats
    private val messageListeners = mutableMapOf<String, com.google.firebase.database.ChildEventListener>()

    // Add a map to track all message listeners for group chats
    private val groupMessageListeners = mutableMapOf<String, com.google.firebase.database.ChildEventListener>()

    init {
        loadChats()
        loadFriends()
        setupIncomingMessageListeners() // For private chats
    }

    private fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentUser = auth.currentUser ?: return@launch
            val currentUserId = currentUser.uid

            removeListeners() // Clear all existing listeners before attaching new ones

                privateChats.clear()
                groupChats.clear()
            _chats.value = emptyList()
            _totalUnreadCount.value = 0

            // Listener for Private Chats
            userChatsListener = database.reference
                .child("user_chats")
                    .child(currentUserId)
                .orderByChild("timestamp")
                    .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                                privateChats.clear()
                        var loadedChatsCount = 0
                        val totalChats = snapshot.childrenCount.toInt()
                        
                        if (totalChats == 0) {
                            _isLoading.value = false
                            updateCombinedChats()
                            return
                        }

                        snapshot.children.forEach { chatSnapshot ->
                            val chatId = chatSnapshot.key ?: return@forEach
                                    val lastMessage = chatSnapshot.child("lastMessage").getValue(String::class.java) ?: ""
                                    val timestamp = chatSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val unreadCount = chatSnapshot.child("unreadCount").getValue(Long::class.java)?.toInt() ?: 0
                            val isPinned = chatSnapshot.child("isPinned").getValue(Boolean::class.java) ?: false
                            val lastMessageSenderId = chatSnapshot.child("lastMessageSenderId").getValue(String::class.java) ?: ""
                            
                            val lastMessageStatusStr = chatSnapshot.child("lastMessageStatus").getValue(String::class.java)
                            val firebaseLastMessageStatus = try {
                                lastMessageStatusStr?.let { MessageStatus.valueOf(it) } ?: MessageStatus.SENT
                            } catch (e: Exception) {
                                MessageStatus.SENT
                            }

                            database.reference.child("users").child(chatId).get()
                                .addOnSuccessListener { userSnapshot ->
                                    val profilePicture = userSnapshot.child("profileImage").getValue(String::class.java) ?: ""
                                    val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                                    val userPhone = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""

                                    privateChats.removeAll { it.id == chatId }

                                    privateChats.add(ChatPreview(
                                        id = chatId,
                                        name = userName,
                                        phoneNumber = userPhone,
                                        lastMessage = lastMessage,
                                        timestamp = timestamp,
                                        profilePicture = profilePicture,
                                        isGroup = false,
                                        unreadCount = unreadCount,
                                        isPinned = isPinned,
                                        lastMessageStatus = firebaseLastMessageStatus,
                                        lastMessageSenderId = lastMessageSenderId
                                    ))
                                    
                                    loadedChatsCount++
                                    if (loadedChatsCount >= totalChats) {
                                        _isLoading.value = false
                                    }
                                            updateCombinedChats()
                                        }
                                .addOnFailureListener {
                                    loadedChatsCount++
                                    if (loadedChatsCount >= totalChats) {
                                        _isLoading.value = false
                                    }
                                    updateCombinedChats()
                                }
                        }
                        }

                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                            _error.value = error.message
                        _isLoading.value = false
                        updateCombinedChats()
                    }
                })

            // Listener for User's Group Chat List (to know which groups the user is in)
            database.reference.child("users").child(currentUserId).child("groupChats")
                .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(userGroupsSnapshot: DataSnapshot) {
                        // Detach old listeners for group details that are no longer relevant
                        val currentGroupIds = userGroupsSnapshot.children.mapNotNull { it.key }.toSet()
                        groupChatDetailsListener.keys.filterNot { it in currentGroupIds }.forEach { oldGroupId ->
                            groupChatDetailsListener.remove(oldGroupId)?.let {
                                database.reference.child("group_chats").child(oldGroupId).removeEventListener(it)
                            }
                            userSpecificGroupDataListener.remove(oldGroupId)?.let {
                                database.reference.child("users").child(currentUserId).child("groupChats").child(oldGroupId).removeEventListener(it)
                            }
                        }
                        groupChats.removeAll { it.id !in currentGroupIds } // Clean up local list

                        if (!userGroupsSnapshot.exists()) {
                            updateCombinedChats()
                            return
                        }

                        userGroupsSnapshot.children.forEach { userGroupEntry ->
                            val groupId = userGroupEntry.key ?: return@forEach
                            
                            // Listener for user-specific data in a group (unreadCount, isPinned, lastMessageStatus for this user)
                            userSpecificGroupDataListener[groupId]?.let {
                                database.reference.child("users").child(currentUserId).child("groupChats").child(groupId).removeEventListener(it)
                            }
                            val userSpecificListener = database.reference.child("users")
                                .child(currentUserId).child("groupChats").child(groupId)
                                .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                                    override fun onDataChange(userGroupDataSnapshot: DataSnapshot) {
                                        val unreadCount = userGroupDataSnapshot.child("unreadCount").getValue(Long::class.java)?.toInt() ?: 0
                                        val isPinned = userGroupDataSnapshot.child("isPinned").getValue(Boolean::class.java) ?: false
                                        val lastMessageStatusStr = userGroupDataSnapshot.child("lastMessageStatus").getValue(String::class.java)
                                        val userSpecificLastMessageStatus = try { lastMessageStatusStr?.let { MessageStatus.valueOf(it) } ?: MessageStatus.SENT } catch (e: Exception) { MessageStatus.SENT }
                                        var finalLastMessagePreview = userGroupDataSnapshot.child("lastMessage").getValue(String::class.java) ?: ""
                                        val lastMsgTimestamp = userGroupDataSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L
                                        val lastMsgSenderId = userGroupDataSnapshot.child("lastMessageSenderId").getValue(String::class.java) ?: ""

                                        // Now get general group details (name, profileImage)
                                        groupChatDetailsListener[groupId]?.let {
                                            database.reference.child("group_chats").child(groupId).removeEventListener(it)
                                        }
                                        val groupDetailListener = database.reference.child("group_chats").child(groupId)
                                            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                                                override fun onDataChange(groupDetailsSnapshot: DataSnapshot) {
                                                    val groupName = groupDetailsSnapshot.child("name").getValue(String::class.java) ?: "Unknown Group"
                                                    val profileImage = groupDetailsSnapshot.child("profileImage").getValue(String::class.java) ?: ""
                                                    
                                                    // Logic to format last message preview with sender name
                                                    if (lastMsgSenderId.isNotEmpty() && lastMsgSenderId != currentUserId) {
                                                        database.reference.child("users").child(lastMsgSenderId).child("name").get()
                                                            .addOnSuccessListener { senderNameSnapshot ->
                                                                val senderName = senderNameSnapshot.getValue(String::class.java) ?: "Someone"
                                                                // Check if already prefixed (e.g. by repository for non-"You:" cases)
                                                                if (!finalLastMessagePreview.startsWith("$senderName:") && !finalLastMessagePreview.startsWith("You:")) {
                                                                     finalLastMessagePreview = "$senderName: $finalLastMessagePreview"
                                                                }
                                                                updateOrAddGroupChatPreview(
                                                                    groupId, groupName, profileImage, unreadCount, isPinned,
                                                                    userSpecificLastMessageStatus, finalLastMessagePreview,
                                                                    lastMsgTimestamp, lastMsgSenderId
                                                                )
                                                            }
                                                            .addOnFailureListener {
                                                                // Failed to get sender name, use a placeholder or original message
                                                                if (!finalLastMessagePreview.startsWith("Someone:") && !finalLastMessagePreview.startsWith("You:")) {
                                                                    finalLastMessagePreview = "Someone: $finalLastMessagePreview"
                                                                }
                                                                updateOrAddGroupChatPreview(
                                                                    groupId, groupName, profileImage, unreadCount, isPinned,
                                                                    userSpecificLastMessageStatus, finalLastMessagePreview,
                                                                    lastMsgTimestamp, lastMsgSenderId
                                                                )
                                                            }
                                            } else {
                                                        // If sender is current user, or senderId is empty (e.g. system message)
                                                        // The finalLastMessagePreview should already be correctly formatted (e.g. "You: ..." or system message)
                                                        updateOrAddGroupChatPreview(
                                                            groupId, groupName, profileImage, unreadCount, isPinned,
                                                            userSpecificLastMessageStatus, finalLastMessagePreview,
                                                            lastMsgTimestamp, lastMsgSenderId
                                                        )
                                                    }
                                                }
                                                override fun onCancelled(error: com.google.firebase.database.DatabaseError) { _error.value = "Error loading group details: ${error.message}"; updateCombinedChats() }
                                            })
                                        groupChatDetailsListener[groupId] = groupDetailListener
                                    }
                                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) { _error.value = "Error loading user group data: ${error.message}"; updateCombinedChats() }
                                })
                            userSpecificGroupDataListener[groupId] = userSpecificListener
                            }
                        }
                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                            _error.value = error.message
                        groupChats.clear()
                        updateCombinedChats()
                        }
                    })
        }
    }

    private fun removeListeners() {
        userChatsListener?.let {
            auth.currentUser?.uid?.let { userId ->
                database.reference.child("user_chats").child(userId).removeEventListener(it)
            }
        }
        userChatsListener = null

        userSpecificGroupDataListener.forEach { (groupId, listener) ->
            auth.currentUser?.uid?.let {
                database.reference.child("users").child(it).child("groupChats").child(groupId).removeEventListener(listener)
            }
        }
        userSpecificGroupDataListener.clear()

        groupChatDetailsListener.forEach { (groupId, listener) ->
            database.reference.child("group_chats").child(groupId).removeEventListener(listener)
        }
        groupChatDetailsListener.clear()
        
        messageListeners.forEach { (chatId, listener) ->
            auth.currentUser?.uid?.let {
                database.reference.child("private_messages").child(it).child(chatId).removeEventListener(listener)
            }
        }
        messageListeners.clear()

        // Remove group message listeners
        groupMessageListeners.forEach { (groupId, listener) ->
            database.reference.child("group_messages").child(groupId).removeEventListener(listener)
        }
        groupMessageListeners.clear()
    }

    override fun onCleared() {
        super.onCleared()
        removeListeners()
    }

    private fun updateCombinedChats() {
        val combined = (privateChats + groupChats).sortedWith(
            compareByDescending<ChatPreview> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
        _chats.value = combined
        _totalUnreadCount.value = combined.count { it.unreadCount > 0 }
    }

    fun loadFriends() {
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

                        database.reference
                            .child("users")
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                for (userSnapshot in usersSnapshot.children) {
                                    val userId = userSnapshot.key
                                    if (userId in friendIds) {
                                        try {
                                            // Extract user data manually to avoid deserialization issues
                                            val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                                            val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                                            val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                                            val status = userSnapshot.child("status").getValue(String::class.java) ?: ""
                                            val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java)

                                            val friend = User(
                                                userId = userId ?: "",
                                                name = name,
                                                phoneNumber = phoneNumber,
                                                profileImage = profileImage,
                                                status = status,
//                                                friends = emptyMap(), // We don't need friends data for friend list display
                                                fcmToken = fcmToken
                                            )
                                            friendsList.add(friend)
                                        } catch (e: Exception) {
                                            _error.value = "Error loading friend data: ${e.message}"
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

    fun createGroupChat(name: String, members: List<User>, profileImage: String? = null) {
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                val groupChatRef = database.reference.child("group_chats").push()
                val groupChatId = groupChatRef.key ?: return@launch

                val memberIds = members.map { it.userId } + currentUserId
                val membersMap = memberIds.associateWith { true }

                val groupChatData = mapOf(
                    "name" to name,
                    "members" to membersMap,
                    "createdBy" to currentUserId,
                    "createdAt" to System.currentTimeMillis(),
                    "profileImage" to (profileImage ?: ""),
                    "lastMessage" to "Group created", // Initial last message
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "lastMessageSenderId" to currentUserId,
                    "lastMessageStatus" to MessageStatus.SENT.name // Initial status
                )

                val updates = hashMapOf<String, Any>()
                updates["group_chats/$groupChatId"] = groupChatData

                memberIds.forEach { memberId ->
                    updates["users/$memberId/groupChats/$groupChatId"] = hashMapOf(
                        "unreadCount" to if (memberId == currentUserId) 0 else 1, // Creator has 0, others 1 due to "Group created" message
                        "isPinned" to false,
                        "lastMessageStatus" to MessageStatus.SENT.name,
                        "lastMessage" to if (memberId == currentUserId) "You: Group created" else "Group created",
                        "lastMessageSenderId" to currentUserId,
                        "lastMessageTimestamp" to System.currentTimeMillis(),
                        "statusPreserved" to false
                    )
                }
                
                // Initialize messages node with a creation message
                val initialMessageId = database.reference.child("group_messages").child(groupChatId).push().key ?: "initial"
                updates["group_messages/$groupChatId/$initialMessageId"] = hashMapOf(
                    "content" to "Group created",
                    "senderId" to currentUserId,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to MessageStatus.SENT.name,
                    "memberStatus" to memberIds.associateWith { if (it == currentUserId) MessageStatus.READ.name else MessageStatus.SENT.name }
                )
                updates["group_messages/$groupChatId/_meta"] = hashMapOf("type" to "group")

                database.reference.updateChildren(updates)
                    .addOnSuccessListener { /* Handle success if needed */ }
                    .addOnFailureListener { e -> _error.value = "Failed to create group chat: ${e.message}" }
            } catch (e: Exception) {
                _error.value = "Error creating group chat: ${e.message}"
            }
        }
    }

    fun togglePinChat(chatId: String) {
        val currentUser = auth.currentUser ?: return
        val isGroupChat = groupChats.any { it.id == chatId }
        val chatRefPath = if (isGroupChat) "users/${currentUser.uid}/groupChats/$chatId" else "user_chats/${currentUser.uid}/$chatId"

        database.reference.child(chatRefPath).child("isPinned").get().addOnSuccessListener { pinnedSnapshot ->
            val currentPinned = pinnedSnapshot.getValue(Boolean::class.java) ?: false
            if (!currentPinned && _chats.value.count { it.isPinned } >= 3) {
                _error.value = "You can only pin up to 3 chats"
                return@addOnSuccessListener
            }
            database.reference.child(chatRefPath).child("isPinned").setValue(!currentPinned)
        }
    }

    fun markChatAsRead(chatId: String) {
        val currentUser = auth.currentUser ?: return
        val isGroupChat = groupChats.any { it.id == chatId }
        val chatRefPath = if (isGroupChat) "users/${currentUser.uid}/groupChats/$chatId" else "user_chats/${currentUser.uid}/$chatId"
        
        database.reference.child(chatRefPath).updateChildren(mapOf("unreadCount" to 0, "lastMessageRead" to true))
    }

    fun markChatAsUnread(chatId: String) {
        val currentUser = auth.currentUser ?: return
        val isGroupChat = groupChats.any { it.id == chatId }
        val chatRefPath = if (isGroupChat) "users/${currentUser.uid}/groupChats/$chatId" else "user_chats/${currentUser.uid}/$chatId"
        
        database.reference.child(chatRefPath).updateChildren(mapOf("unreadCount" to 1, "lastMessageRead" to false))
    }

    fun deleteChat(chatId: String) {
        val currentUser = auth.currentUser ?: return
        val isGroupChat = groupChats.any { it.id == chatId }

        if (isGroupChat) {
            // For group chats, treat delete as "leave group"
            leaveGroup(chatId, currentUser.uid)
        } else {
            // For private chats, just remove from user's chat list
            database.reference.child("user_chats/${currentUser.uid}/$chatId").removeValue()
            // Also delete messages for this private chat
            database.reference.child("private_messages/${currentUser.uid}/$chatId").removeValue()
            database.reference.child("private_messages/$chatId/${currentUser.uid}").removeValue()
        }
    }

    private fun leaveGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                // First, check how many members are in the group
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : com.google.firebase.database.GenericTypeIndicator<Map<String, Boolean>>() {}
                        val currentMembers = membersSnapshot.getValue(membersType)?.keys?.toList() ?: emptyList()
                        
                        if (currentMembers.size <= 1) {
                            // User is the last member, delete the entire group
                            deleteEntireGroup(groupId, userId)
                        } else {
                            // There are other members, just remove this user
                            removeUserFromGroup(groupId, userId)
                        }
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to check group members: ${e.message}"
                        android.util.Log.e("ChatListVM", "Failed to check group members: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error leaving group: ${e.message}"
                android.util.Log.e("ChatListVM", "Error in leaveGroup: ${e.message}")
            }
        }
    }
    
    private fun removeUserFromGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                val updates = hashMapOf<String, Any?>()
                
                // Remove user from group's member list
                updates["group_chats/$groupId/members/$userId"] = null
                
                // Remove group from user's groupChats list
                updates["users/$userId/groupChats/$groupId"] = null
                
                // Apply the updates atomically
                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        android.util.Log.d("ChatListVM", "Successfully left group $groupId")
                        
                        // Add a "user left" message to the group
                        addUserLeftMessage(groupId, userId)
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to leave group: ${e.message}"
                        android.util.Log.e("ChatListVM", "Failed to leave group $groupId: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error removing user from group: ${e.message}"
                android.util.Log.e("ChatListVM", "Error in removeUserFromGroup: ${e.message}")
            }
        }
    }
    
    private fun deleteEntireGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                // First get all members to remove the group from their lists
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : com.google.firebase.database.GenericTypeIndicator<Map<String, Boolean>>() {}
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
                        android.util.Log.d("ChatListVM", "Successfully deleted empty group $groupId")
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to delete group: ${e.message}"
                        android.util.Log.e("ChatListVM", "Failed to delete group $groupId: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        _error.value = "Failed to get group members: ${e.message}"
                        android.util.Log.e("ChatListVM", "Failed to get group members: ${e.message}")
                    }
            } catch (e: Exception) {
                _error.value = "Error deleting group: ${e.message}"
                android.util.Log.e("ChatListVM", "Error in deleteEntireGroup: ${e.message}")
            }
        }
    }
    
    private fun addUserLeftMessage(groupId: String, userId: String) {
        // Get user's name first, then add the system message
        database.reference.child("users").child(userId).child("name").get()
            .addOnSuccessListener { nameSnapshot ->
                val userName = nameSnapshot.getValue(String::class.java) ?: "Someone"
                val currentUserId = auth.currentUser?.uid ?: return@addOnSuccessListener
                
                // Add system message about user leaving
                val messageRef = database.reference.child("group_messages").child(groupId).push()
                val messageId = messageRef.key ?: return@addOnSuccessListener
                
                // Get remaining group members to set up memberStatus
                database.reference.child("group_chats").child(groupId).child("members").get()
                    .addOnSuccessListener { membersSnapshot ->
                        val membersType = object : com.google.firebase.database.GenericTypeIndicator<Map<String, Boolean>>() {}
                        val remainingMembers = membersSnapshot.getValue(membersType)?.keys?.toList() ?: emptyList()
                        
                        if (remainingMembers.isNotEmpty()) {
                            val memberStatusMap = remainingMembers.associateWith { MessageStatus.SENT.name }
                            val leftMessage = if (userId == currentUserId) "You left" else "$userName left"
                            
                            val messageData = hashMapOf(
                                "content" to leftMessage,
                                "senderId" to userId,
                                "timestamp" to System.currentTimeMillis(),
                                "status" to MessageStatus.SENT.name,
                                "memberStatus" to memberStatusMap
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
                            }
                            
                            database.reference.updateChildren(messageUpdates)
                        }
                    }
        }
    }

    fun getPinnedChatsCount(): Int {
        return chats.value.count { it.isPinned }
    }

    fun clearError() {
        _error.value = null
    }

    fun onResume() {
        viewModelScope.launch {
            _isLoading.value = true
            loadChats() // This will re-attach listeners and refresh data
            // No need for explicit refreshGroupChatStatuses or refreshPrivateChatStatuses as loadChats handles it with new listener structure
            // verifyUnreadCounts might still be useful for private chats if direct manipulation is possible elsewhere
            verifyPrivateChatUnreadCounts() 
        }
    }

    private fun verifyPrivateChatUnreadCounts() {
        val currentUser = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            privateChats.forEach { chatPreview ->
                if (chatPreview.lastMessageSenderId != currentUser) {
                    database.reference.child("private_messages").child(currentUser).child(chatPreview.id)
                        .orderByChild("read").equalTo(false).get()
                        .addOnSuccessListener { messagesSnapshot ->
                            var realUnreadCount = 0
                            messagesSnapshot.children.forEach { msgSnap ->
                                if (msgSnap.child("senderId").getValue(String::class.java) == chatPreview.id) {
                                    realUnreadCount++
                                }
                            }
                            if (chatPreview.unreadCount != realUnreadCount) {
                                database.reference.child("user_chats").child(currentUser).child(chatPreview.id)
                                    .child("unreadCount").setValue(realUnreadCount)
                            }
                        }
                }
            }
        }
    }

    private fun setupIncomingMessageListeners() {
        val currentUser = auth.currentUser ?: return
        
        // Clear existing listeners
        messageListeners.forEach { (_, listener) -> 
            database.reference.removeEventListener(listener) 
        }
        messageListeners.clear()
        
        groupMessageListeners.forEach { (_, listener) ->
            database.reference.removeEventListener(listener)
        }
        groupMessageListeners.clear()

        // Setup private chat listeners
        database.reference.child("user_chats").child(currentUser.uid).get().addOnSuccessListener { chatsSnapshot ->
            chatsSnapshot.children.forEach { chatSnapshot ->
                val chatId = chatSnapshot.key ?: return@forEach
                if (privateChats.any { it.id == chatId && !it.isGroup }) {
                    setupIndividualChatMessageListener(chatId)
                }
            }
        }

        // Setup group chat listeners
        database.reference.child("users").child(currentUser.uid).child("groupChats").get()
            .addOnSuccessListener { groupChatsSnapshot ->
                groupChatsSnapshot.children.forEach { groupSnapshot ->
                    val groupId = groupSnapshot.key ?: return@forEach
                    setupGroupMessageListener(groupId)
                }
            }
    }

    private fun setupIndividualChatMessageListener(chatId: String) { // For Private Chats
        val currentUser = auth.currentUser?.uid ?: return
        if (messageListeners.containsKey(chatId)) return

        val listener = database.reference.child("private_messages").child(currentUser).child(chatId)
            .orderByChild("timestamp").limitToLast(1)
            .addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val senderId = snapshot.child("senderId").getValue(String::class.java) ?: return
                    val content = snapshot.child("content").getValue(String::class.java) ?: return
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0
                    val isRead = snapshot.child("read").getValue(Boolean::class.java) ?: false

                    if (senderId == chatId) { // Message from the other user in private chat
                        val chatRef = database.reference.child("user_chats").child(currentUser).child(chatId)
                        chatRef.get().addOnSuccessListener { chatDataSnapshot ->
                            val currentTimestamp = chatDataSnapshot.child("timestamp").getValue(Long::class.java) ?: 0
                            if (timestamp > currentTimestamp) {
                                val updates = hashMapOf<String, Any>(
                                    "lastMessage" to content,
                                    "timestamp" to timestamp,
                                    "lastMessageSenderId" to senderId
                                )
                                if (!isRead) {
                                    val currentUnread = chatDataSnapshot.child("unreadCount").getValue(Long::class.java) ?: 0L
                                    updates["unreadCount"] = currentUnread + 1
                                    updates["lastMessageRead"] = false
                                }
                                chatRef.updateChildren(updates)
                            }
                        }
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) { /* Handle status changes if needed */ }
                override fun onChildRemoved(snapshot: DataSnapshot) { /* Handle deleted messages */ }
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* Handle moved messages */ }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) { _error.value = "Private message listener error: ${error.message}" }
            })
        messageListeners[chatId] = listener
    }

    private fun setupGroupMessageListener(groupId: String) {
        val currentUser = auth.currentUser?.uid ?: return
        if (groupMessageListeners.containsKey(groupId)) return

        val listener = database.reference.child("group_messages").child(groupId)
            .orderByChild("timestamp").limitToLast(1)
            .addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    if (snapshot.key == "_meta") return // Skip metadata node
                    
                    val senderId = snapshot.child("senderId").getValue(String::class.java) ?: return
                    val content = snapshot.child("content").getValue(String::class.java) ?: return
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0
                    
                    // Get user's group chat data
                    val userGroupChatRef = database.reference
                        .child("users")
                        .child(currentUser)
                        .child("groupChats")
                        .child(groupId)

                    userGroupChatRef.get().addOnSuccessListener { userGroupDataSnapshot ->
                        val currentTimestamp = userGroupDataSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0
                        
                        if (timestamp > currentTimestamp) {
                            // Format message preview
                            val messagePreview = if (senderId == currentUser) {
                                "You: $content"
                            } else {
                                // Get sender's name
                                database.reference.child("users").child(senderId).child("name").get()
                                    .addOnSuccessListener { senderNameSnapshot ->
                                        val senderName = senderNameSnapshot.getValue(String::class.java) ?: "Someone"
                                        val finalContent = "$senderName: $content"
                                        
                                        val updates = hashMapOf<String, Any>(
                                            "lastMessage" to finalContent,
                                            "lastMessageTimestamp" to timestamp,
                                            "lastMessageSenderId" to senderId,
                                            "lastMessageStatus" to MessageStatus.SENT.name
                                        )
                                        
                                        // Increment unread count if message is from someone else
                                        if (senderId != currentUser) {
                                            val currentUnread = userGroupDataSnapshot.child("unreadCount").getValue(Long::class.java) ?: 0L
                                            updates["unreadCount"] = currentUnread + 1
                                            updates["lastMessageRead"] = false
                                        }
                                        
                                        userGroupChatRef.updateChildren(updates)
                                    }
                            }
                        }
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) { /* Handle status changes if needed */ }
                override fun onChildRemoved(snapshot: DataSnapshot) { /* Handle deleted messages */ }
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* Handle moved messages */ }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) { _error.value = "Group message listener error: ${error.message}" }
            })
        groupMessageListeners[groupId] = listener
    }

    private fun updateOrAddGroupChatPreview(
        groupId: String, groupName: String, profileImage: String,
        unreadCount: Int, isPinned: Boolean, lastMessageStatus: MessageStatus,
        lastMessage: String, lastMessageTimestamp: Long, lastMessageSenderId: String
    ) {
        val existingChatIndex = groupChats.indexOfFirst { it.id == groupId }
        if (existingChatIndex != -1) {
            groupChats[existingChatIndex] = groupChats[existingChatIndex].copy(
                name = groupName, profilePicture = profileImage, unreadCount = unreadCount,
                isPinned = isPinned, lastMessageStatus = lastMessageStatus,
                lastMessage = lastMessage, timestamp = lastMessageTimestamp,
                lastMessageSenderId = lastMessageSenderId
            )
        } else {
            groupChats.add(ChatPreview(
                id = groupId, name = groupName, phoneNumber = "", lastMessage = lastMessage,
                timestamp = lastMessageTimestamp, profilePicture = profileImage, isGroup = true,
                unreadCount = unreadCount, isPinned = isPinned, lastMessageStatus = lastMessageStatus,
                lastMessageSenderId = lastMessageSenderId
            ))
        }
        updateCombinedChats()
    }
} 
