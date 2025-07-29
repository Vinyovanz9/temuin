package com.temuin.temuin.ui.screens.groupchat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.temuin.temuin.data.repository.GroupChatRepositoryImpl
import com.temuin.temuin.data.model.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import com.google.firebase.database.GenericTypeIndicator
import com.temuin.temuin.data.model.GroupChatMessage
import com.temuin.temuin.data.model.GroupInfo
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val groupChatRepositoryImpl: GroupChatRepositoryImpl,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = MutableStateFlow<List<GroupChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _groupInfo = MutableStateFlow<GroupInfo?>(null)
    val groupInfo = _groupInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    private var messagesListener: ChildEventListener? = null
    private var groupInfoListener: ValueEventListener? = null
    private var allMessages = mutableListOf<GroupChatMessage>()
    private var currentGroupId: String? = null
    private var groupOverallStatusListener: ValueEventListener? = null
    
    private var lastLoadedMessageKey: String? = null
    private var isAllMessagesLoaded = false
    private val PAGE_SIZE = 30

    fun loadGroupChat(groupId: String) {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true
        currentGroupId = groupId
        
        allMessages.clear()
        _messages.value = emptyList()
        lastLoadedMessageKey = null
        isAllMessagesLoaded = false

        // Mark group chat as read by current user (resets unreadCount)
        markGroupChatAsReadForCurrentUser(groupId)
        
        // Mark user as actively viewing this group
        updateActiveViewingStatus(groupId, true)

        // Setup delivery tracking
        groupChatRepositoryImpl.setupGroupMessageDeliveryTracking(groupId)

        removeListeners()

        // Listen for group info changes
        setupGroupInfoListener(groupId)

        // Load initial messages (most recent PAGE_SIZE messages)
        loadInitialMessages(groupId)
    }

    private fun setupGroupInfoListener(groupId: String) {
        val groupRef = database.reference.child("group_chats").child(groupId)
        groupInfoListener = groupRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Unknown Group"
                val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                val members = snapshot.child("members").getValue(membersType)?.keys?.toList() ?: emptyList()
                val profileImage = snapshot.child("profileImage").getValue(String::class.java)
                val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L

                _groupInfo.value = GroupInfo(
                    id = groupId,
                    name = name,
                    members = members,
                    profileImage = profileImage,
                    createdBy = createdBy,
                    createdAt = createdAt
                )
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
            }
        })
    }

    private fun loadInitialMessages(groupId: String) {
        if (!_isLoading.value) _isLoading.value = true
        val messagesRef = database.reference.child("group_messages").child(groupId)
        
        // Query last PAGE_SIZE messages
        messagesRef.orderByChild("timestamp")
            .limitToLast(PAGE_SIZE)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        _isLoading.value = false
                        return
                    }

                    val messages = mutableListOf<GroupChatMessage>()
                    snapshot.children.forEach { messageSnapshot ->
                        if (messageSnapshot.key != "_meta") {
                            parseAndAddMessage(messageSnapshot, auth.currentUser?.uid ?: "", groupId)?.let {
                                messages.add(it)
                            }
                        }
                    }

                    // Store the key of the oldest message for pagination
                    lastLoadedMessageKey = messages.minByOrNull { it.timestamp }?.id

                    // Setup real-time listener for new messages
                    setupRealtimeMessageListener(groupId)
                    
                    _isLoading.value = false
                }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                    _isLoading.value = false
                }
            })
    }

    fun loadMoreMessages() {
        if (isAllMessagesLoaded || _isLoadingMore.value || lastLoadedMessageKey == null || currentGroupId == null) {
            return
        }

        _isLoadingMore.value = true
        
        val messagesRef = database.reference.child("group_messages").child(currentGroupId!!)
        
        // Query messages before the last loaded message
        messagesRef.orderByChild("timestamp")
            .endBefore(allMessages.find { it.id == lastLoadedMessageKey }?.timestamp?.toDouble() ?: 0.0)
            .limitToLast(PAGE_SIZE)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        isAllMessagesLoaded = true
                        _isLoadingMore.value = false
                        return
                    }

                    val newMessages = mutableListOf<GroupChatMessage>()
                    snapshot.children.forEach { messageSnapshot ->
                        if (messageSnapshot.key != "_meta") {
                            parseAndAddMessage(messageSnapshot, auth.currentUser?.uid ?: "", currentGroupId!!)?.let {
                                newMessages.add(it)
                            }
                        }
                    }

                    if (newMessages.isEmpty()) {
                        isAllMessagesLoaded = true
                    } else {
                        // Update the last loaded message key
                        lastLoadedMessageKey = newMessages.minByOrNull { it.timestamp }?.id
                        
                        // Add new messages to the beginning of the list
                        allMessages.addAll(0, newMessages)
                        _messages.value = allMessages.sortedBy { it.timestamp }
                    }
                    
                    _isLoadingMore.value = false
                }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                    _isLoadingMore.value = false
                }
            })
    }

    private fun setupRealtimeMessageListener(groupId: String) {
        val messagesRef = database.reference.child("group_messages").child(groupId)
        
        messagesListener = messagesRef.orderByChild("timestamp")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    if (snapshot.key == "_meta") return
                    
                    // Only add messages that are newer than our latest message
                    val latestTimestamp = allMessages.maxByOrNull { it.timestamp }?.timestamp ?: 0
                    val newMessageTimestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0
                    
                    if (newMessageTimestamp > latestTimestamp) {
                        parseAndAddMessage(snapshot, auth.currentUser?.uid ?: "", groupId)?.let { newMessage ->
                            allMessages.add(newMessage)
                            _messages.value = allMessages.sortedBy { it.timestamp }
                        }
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    if (snapshot.key == "_meta") return
                    
                    val messageId = snapshot.key ?: return
                    val statusStr = snapshot.child("status").getValue(String::class.java) ?: return
                    
                    try {
                        val newStatus = MessageStatus.valueOf(statusStr)
                        val messageIndex = allMessages.indexOfFirst { it.id == messageId }
                        if (messageIndex != -1 && allMessages[messageIndex].status != newStatus) {
                            allMessages[messageIndex] = allMessages[messageIndex].copy(status = newStatus)
                            _messages.value = allMessages.sortedBy { it.timestamp }
                        }
                    } catch (e: Exception) {
                        Log.e("GroupChatVM", "Invalid message status: $statusStr")
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    if (snapshot.key == "_meta") return
                    
                    val messageId = snapshot.key ?: return
                    allMessages.removeAll { it.id == messageId }
                    _messages.value = allMessages.sortedBy { it.timestamp }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                }
            })
    }

    private fun parseAndAddMessage(snapshot: DataSnapshot, currentUserId: String, groupId: String): GroupChatMessage? {
        try {
            val messageId = snapshot.key ?: return null
            val content = snapshot.child("content").getValue(String::class.java) ?: ""
            val senderId = snapshot.child("senderId").getValue(String::class.java) ?: ""
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            val statusStr = snapshot.child("status").getValue(String::class.java) ?: MessageStatus.SENT.name
            val status = try { MessageStatus.valueOf(statusStr) } catch (e: Exception) { MessageStatus.SENT }
            val addedUserId = snapshot.child("addedUserId").getValue(String::class.java)

            // If message is from another user and current user is viewing, mark it as read
            if (senderId != currentUserId) {
                groupChatRepositoryImpl.markGroupMessageAsRead(groupId, messageId, currentUserId)
            }

            // Get sender's name and profile image
            var senderName = if (senderId == currentUserId) "You" else "Unknown"
            var senderProfileImage: String? = null

            // Fetch sender info from Firebase
            database.reference.child("users").child(senderId).get().addOnSuccessListener { userSnapshot ->
                senderName = if (senderId == currentUserId) "You" else (userSnapshot.child("name").getValue(String::class.java) ?: "Unknown")
                senderProfileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                
                // Update message with sender info
                val messageIndex = allMessages.indexOfFirst { it.id == messageId }
                if (messageIndex != -1) {
                    allMessages[messageIndex] = allMessages[messageIndex].copy(
                        senderName = senderName,
                        senderProfileImage = senderProfileImage
                    )
                    _messages.value = allMessages.sortedBy { it.timestamp }
                }
            }

            return GroupChatMessage(
                id = messageId,
                content = content,
                senderId = senderId,
                senderName = senderName,
                timestamp = timestamp,
                isFromCurrentUser = senderId == currentUserId,
                senderProfileImage = senderProfileImage,
                status = status,
                addedUserId = addedUserId
            )
        } catch (e: Exception) {
            Log.e("GroupChatVM", "Error parsing message: ${e.message}")
            return null
        }
    }

    private fun removeListeners() {
        messagesListener?.let {
            currentGroupId?.let { database.reference.child("group_messages").child(it).removeEventListener(messagesListener!!) }
        }
        messagesListener = null

        groupInfoListener?.let {
            currentGroupId?.let { database.reference.child("group_chats").child(it).removeEventListener(groupInfoListener!!) }
        }
        groupInfoListener = null

        groupOverallStatusListener?.let {
            currentGroupId?.let { database.reference.child("group_chats").child(it).child("lastMessageStatus").removeEventListener(groupOverallStatusListener!!) }
        }
        groupOverallStatusListener = null
        
        // Remove the delivery tracking listener from the repository
        currentGroupId?.let {
            groupChatRepositoryImpl.removeDeliveryTrackingListener(it)
        }
    }

    private fun markGroupChatAsReadForCurrentUser(groupId: String) {
        val currentUser = auth.currentUser ?: return
        // Reset unread count in user's specific group chat view
        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("groupChats")
            .child(groupId)
            .updateChildren(mapOf(
                "unreadCount" to 0,
                "lastMessageRead" to true
            ))
            .addOnSuccessListener { Log.d("GroupChatVM", "Successfully reset unread count to 0 for group $groupId") }
            .addOnFailureListener { e -> Log.e("GroupChatVM", "Failed to reset unread count for group $groupId: ${e.message}") }

        // Mark all messages in this group as read by the current user
        database.reference.child("group_messages").child(groupId).get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { messageSnapshot ->
                val messageId = messageSnapshot.key
                if (messageId != null && messageId != "_meta") {
                    val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                    if (senderId != currentUser.uid) { // Only mark others' messages as read for this user
                        groupChatRepositoryImpl.markGroupMessageAsRead(groupId, messageId, currentUser.uid, triggerRecalculation = true)
                    }
                }
            }
             // After marking, explicitly recalculate status for the last message of the group to refresh UI ticks for sender
            groupChatRepositoryImpl.recalculateAndSetOverallGroupMessageStatus(groupId, "") // Pass empty messageId to trigger for last group message
        }
    }

    private fun updateActiveViewingStatus(groupId: String, isViewing: Boolean) {
        val currentUser = auth.currentUser ?: return
        val userActiveViewerRef = database.reference
            .child("group_chats")
            .child(groupId)
            .child("activeViewers")
            .child(currentUser.uid)
        
        if (isViewing) {
            userActiveViewerRef.setValue(System.currentTimeMillis())
        } else {
            userActiveViewerRef.removeValue()
        }
    }

    fun sendMessage(message: String) {
        val groupId = currentGroupId ?: return
        if (message.isBlank()) return

        _isSending.value = true
        
        val currentUser = auth.currentUser ?: return
        val messageId = database.reference.child("group_messages").child(groupId).push().key ?: return

        // Create message with actual ID
        val newMessage = GroupChatMessage(
            id = messageId,
            content = message,
            senderId = currentUser.uid,
            senderName = "You",
            timestamp = System.currentTimeMillis(),
            isFromCurrentUser = true,
            senderProfileImage = null,
            status = MessageStatus.SENT
        )

        // TODO: Replace NetworkUtils.isNetworkAvailable with your actual implementation
        val hasInternetConnection = true

        groupChatRepositoryImpl.sendGroupMessage(
            groupId = groupId,
            message = message,
            messageId = messageId,
            hasInternetConnection = hasInternetConnection,
            onSuccess = {
                // Only add message to local list after successful send
                if (!allMessages.any { it.id == messageId }) {
                    allMessages.add(newMessage)
                    _messages.value = allMessages.toList().sortedBy { it.timestamp }
                }
                _isSending.value = false
                _error.value = null
            },
            onError = { errorMsg ->
                _isSending.value = false
                _error.value = errorMsg
                // Remove the failed message from local list if it exists
                allMessages.removeAll { it.id == messageId }
                _messages.value = allMessages.toList().sortedBy { it.timestamp }
            }
        )
    }

    fun searchMessages(query: String) {
        if (query.isBlank()) {
            _messages.value = allMessages.toList().sortedBy { it.timestamp }
            return
        }
        // Search logic not fully implemented here as per previous scope, keep all messages shown.
        _messages.value = allMessages.toList().sortedBy { it.timestamp }
    }

    fun clearSearch() {
        _messages.value = allMessages.toList().sortedBy { it.timestamp }
    }

    override fun onCleared() {
        super.onCleared()
        currentGroupId?.let {
            updateActiveViewingStatus(it, false)
            // Optionally, trigger a final status check or save state if needed
            val currentUser = auth.currentUser
            if (currentUser != null) {
                database.reference
                    .child("users")
                    .child(currentUser.uid)
                    .child("groupChats")
                    .child(it)
                    .child("unreadCount")
                    .setValue(0)
                    .addOnSuccessListener { Log.d("GroupChatVM", "Explicitly set unread count to 0 in onCleared for group $it") }
                    .addOnFailureListener { e -> Log.e("GroupChatVM", "Failed to set unread count to 0 in onCleared for group $it: ${e.message}") }
            }
        }
        removeListeners()
    }
} 