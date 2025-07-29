package com.temuin.temuin.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue
import com.temuin.temuin.data.model.ChatMessage
import com.temuin.temuin.data.repository.ChatRepositoryImpl
import com.temuin.temuin.data.model.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepositoryImpl: ChatRepositoryImpl,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _recipientProfilePicture = MutableStateFlow<String?>(null)
    val recipientProfilePicture = _recipientProfilePicture.asStateFlow()

    private val _isRecipientFriend = MutableStateFlow(false)
    val isRecipientFriend = _isRecipientFriend.asStateFlow()

    private var messagesChildEventListener: ChildEventListener? = null
    private val messageStatusListeners = mutableMapOf<String, ValueEventListener>()
    private val allMessagesList = mutableListOf<ChatMessage>()
    private var currentRecipientId: String? = null
    private var pendingOptimisticMessageId: String? = null

    fun loadRecipientProfile(recipientId: String) {
        database.reference
            .child("users")
            .child(recipientId)
            .child("profileImage")
            .get()
            .addOnSuccessListener { snapshot ->
                _recipientProfilePicture.value = snapshot.getValue(String::class.java)
            }
    }

    private fun checkIfFriend(recipientId: String) {
        val currentUser = auth.currentUser ?: return
        
        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("friends")
            .child(recipientId)
            .get()
            .addOnSuccessListener { snapshot ->
                _isRecipientFriend.value = snapshot.exists()
            }
    }

    fun addAsFriend(recipientId: String, onSuccess: () -> Unit) {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true

        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("friends")
            .child(recipientId)
            .setValue(true)
            .addOnSuccessListener {
                // Also add current user to recipient's friend list
                database.reference
                    .child("users")
                    .child(recipientId)
                    .child("friends")
                    .child(currentUser.uid)
            .setValue(true)
            .addOnSuccessListener {
                _isRecipientFriend.value = true
                _isLoading.value = false
                onSuccess()
                    }
                    .addOnFailureListener { e ->
                         _error.value = "Failed to add friend to recipient's list: ${e.message}"
                        _isLoading.value = false
                    }
            }
            .addOnFailureListener { e ->
                _error.value = "Failed to add friend: ${e.message}"
                _isLoading.value = false
            }
    }

    private fun clearListeners() {
        val currentUser = auth.currentUser ?: return
        currentRecipientId?.let { recipientId ->
            messagesChildEventListener?.let { listener ->
                database.reference.child("private_messages")
                    .child(currentUser.uid)
                    .child(recipientId)
                    .removeEventListener(listener)
            }
            messagesChildEventListener = null

            messageStatusListeners.forEach { (messageId, listener) ->
                database.reference.child("private_messages")
                    .child(currentUser.uid)
                    .child(recipientId)
                    .child(messageId)
                    .child("status")
                    .removeEventListener(listener)
            }
            messageStatusListeners.clear()
        }
        allMessagesList.clear()
    }

    override fun onCleared() {
        super.onCleared()
        leaveChat()
        clearListeners()
        currentRecipientId?.let { chatRepositoryImpl.cleanupMessageListeners(it) }
    }

    fun enterChat(recipientId: String) {
        val currentUser = auth.currentUser ?: return
        currentRecipientId = recipientId // Ensure currentRecipientId is set before potential leaveChat call

        database.reference
            .child("user_chats")
            .child(currentUser.uid)
            .child(recipientId)
            .child("isViewing")
            .setValue(true)

        // Reset unread count and mark last message as read
        database.reference
            .child("user_chats")
            .child(currentUser.uid)
            .child(recipientId)
            .updateChildren(mapOf(
                "unreadCount" to 0,
                "lastMessageRead" to true
            ))
        
        // Mark all messages from this recipient as read
        chatRepositoryImpl.markAllMessagesAsRead(recipientId)
    }

    fun leaveChat() {
        val currentUser = auth.currentUser ?: return
        currentRecipientId?.let { rid ->
            database.reference
                .child("user_chats")
                .child(currentUser.uid)
                .child(rid)
                .child("isViewing")
                .setValue(false)
                .addOnCompleteListener {
                    // Update last seen when user leaves a chat
                    chatRepositoryImpl.updateLastSeen()
                }
        }
    }
    
    private fun attachStatusListenerToMessage(message: ChatMessage, recipientId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        if (message.senderId != currentUserId) return // Only for sent messages

        // Remove existing listener for this messageId to avoid duplicates
        messageStatusListeners.remove(message.messageId)?.let { oldListener ->
            database.reference.child("private_messages")
                .child(currentUserId)
                .child(recipientId)
                .child(message.messageId)
                .child("status")
                .removeEventListener(oldListener)
        }

        val newStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newStatusString = snapshot.getValue(String::class.java)
                try {
                    val newStatus = newStatusString?.let { MessageStatus.valueOf(it) }
                    newStatus?.let { status ->
                        val index = allMessagesList.indexOfFirst { it.messageId == message.messageId }
                        if (index != -1) {
                            // Use .copy to maintain immutability if ChatMessage remains a data class with val
                            // If status is var, direct assignment is fine but less idiomatic for StateFlow updates
                            allMessagesList[index] = allMessagesList[index].copy(status = status)
                            _messages.value = allMessagesList.toList().sortedBy { it.timestamp }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    android.util.Log.e("ChatViewModel", "Invalid message status: $newStatusString")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                 android.util.Log.e("ChatViewModel", "Status listener cancelled for ${message.messageId}: ${error.message}")
            }
        }
        messageStatusListeners[message.messageId] = newStatusListener
        database.reference.child("private_messages")
            .child(currentUserId)
            .child(recipientId)
            .child(message.messageId)
            .child("status")
            .addValueEventListener(newStatusListener)
    }

    fun loadMessages(recipientId: String) {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true
        
        clearListeners() // Clear previous listeners before setting up new ones
        currentRecipientId = recipientId // Set current recipient ID
        allMessagesList.clear()
        _messages.value = emptyList()

        enterChat(recipientId) // Set user as viewing and reset unread counts
        checkIfFriend(recipientId)
        loadRecipientProfile(recipientId)

        messagesChildEventListener = database.reference
            .child("private_messages")
            .child(currentUser.uid)
            .child(recipientId)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val recipientIdForLog = currentRecipientId ?: "UNKNOWN_RECIPIENT"
                    val currentUserForLog = auth.currentUser?.uid ?: "UNKNOWN_CURRENT_USER"
                    android.util.Log.d("ChatVMOnChildAdded", "onChildAdded triggered for recipient: $recipientIdForLog, currentUser: $currentUserForLog. Message Key: ${snapshot.key}")

                    viewModelScope.launch {
                        val messageId = snapshot.key ?: return@launch
                        val content = snapshot.child("content").getValue(String::class.java) ?: return@launch
                        val senderId = snapshot.child("senderId").getValue(String::class.java) ?: return@launch
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: return@launch
                        
                        var statusString = snapshot.child("status").getValue(String::class.java)
                        var messageStatus = try { statusString?.let { MessageStatus.valueOf(it) } ?: MessageStatus.SENT } catch (e: Exception) { MessageStatus.SENT }

                        val currentUserId = currentUser?.uid ?: ""

                        if (senderId == recipientId) { // Message from the other user.
                            // senderId is the original sender.
                            // auth.currentUser.uid is the current app user (recipient).
                            // recipientId in this ChatViewModel is the original sender.

                            val originalSender = senderId 
                            val thisAppUserId = auth.currentUser?.uid 

                            if (thisAppUserId != null) {
                                // When recipient is actively in the chat and loads/receives a message,
                                // it's immediately considered read by them. Update sender's status to READ.
                                chatRepositoryImpl.markMessageAsRead(
                                    originalSenderId = originalSender,
                                    messageId = messageId
                                    // readerUserId is implicitly thisAppUserId inside markMessageAsRead
                                )
                            } else {
                                android.util.Log.e("ChatVM", "Current user is null, cannot mark message from $originalSender as read.")
                            }
                            messageStatus = MessageStatus.READ // Optimistically update UI for recipient's screen
                        }
                        
                        val chatMessage = ChatMessage(
                            messageId = messageId,
                            senderId = senderId,
                            content = content,
                            timestamp = timestamp,
                            status = messageStatus
                        )

                        // If this newly added message is from the current user, and it matches a pending optimistic message,
                        // remove the optimistic one.
                        if (senderId == currentUserId && pendingOptimisticMessageId != null) {
                            allMessagesList.removeAll { it.messageId == pendingOptimisticMessageId }
                            pendingOptimisticMessageId = null // Clear the pending ID
                        }

                        // Add if not already present or update if it exists
                        val existingIndex = allMessagesList.indexOfFirst { it.messageId == chatMessage.messageId }
                        if (existingIndex == -1) {
                            allMessagesList.add(chatMessage)
                        } else { 
                            allMessagesList[existingIndex] = chatMessage
                        }

                        if (senderId == currentUserId) {
                            attachStatusListenerToMessage(chatMessage, recipientId)
                        }
                        
                        _messages.value = allMessagesList.toList().sortedBy { it.timestamp }
                        _isLoading.value = false // Set loading to false after first message or batch
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                     viewModelScope.launch {
                        val messageId = snapshot.key ?: return@launch
                        val content = snapshot.child("content").getValue(String::class.java)
                        val senderId = snapshot.child("senderId").getValue(String::class.java)
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java)
                        // Status is handled by individual listeners for sent messages.
                        // For received messages, status is READ.
                        // This is mainly for content changes if they were possible.

                        val index = allMessagesList.indexOfFirst { it.messageId == messageId }
                        if (index != -1) {
                            var updatedMessage = allMessagesList[index]
                            if (content != null) updatedMessage = updatedMessage.copy(content = content)
                            if (senderId != null) updatedMessage = updatedMessage.copy(senderId = senderId)
                            if (timestamp != null) updatedMessage = updatedMessage.copy(timestamp = timestamp)
                            
                            // If it's a received message, ensure its status stays READ in UI
                            if (updatedMessage.senderId == recipientId) {
                                updatedMessage = updatedMessage.copy(status = MessageStatus.READ)
                            }
                            
                            allMessagesList[index] = updatedMessage
                            _messages.value = allMessagesList.toList().sortedBy { it.timestamp }
                        }
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    viewModelScope.launch {
                        val messageId = snapshot.key ?: return@launch
                        allMessagesList.removeAll { it.messageId == messageId }
                        
                        // Remove status listener if it exists for this message
                        messageStatusListeners.remove(messageId)?.let { listener ->
                            database.reference.child("private_messages")
                                .child(currentUser.uid)
                                .child(recipientId)
                                .child(messageId)
                                .child("status")
                                .removeEventListener(listener)
                        }
                        _messages.value = allMessagesList.toList().sortedBy { it.timestamp }
                    }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* Not used */ }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                    _isLoading.value = false
                }
            })
    }

    fun searchMessages(query: String) {
        _messages.value = allMessagesList
    }

    fun clearSearch() {
        _messages.value = allMessagesList
    }

    fun sendMessage(recipientId: String, message: String) {
        if (message.isBlank()) return

        // TODO: Implement actual network check
        val hasInternetConnection = true // Placeholder - replace with actual network check
        val initialStatus = if (hasInternetConnection) MessageStatus.DELIVERED else MessageStatus.SENT

        val currentUser = auth.currentUser ?: return
        val tempMessageId = "temp_${System.currentTimeMillis()}"
        pendingOptimisticMessageId = tempMessageId // Store for replacement

        val tempMessage = ChatMessage(
            messageId = tempMessageId,
            senderId = currentUser.uid,
            content = message,
            timestamp = System.currentTimeMillis(),
            status = initialStatus // Use initialStatus for optimistic UI
        )
        
        if (allMessagesList.none { it.messageId == tempMessageId }) {
            allMessagesList.add(tempMessage)
            _messages.value = allMessagesList.toList().sortedBy { it.timestamp }
        }

        chatRepositoryImpl.sendMessage(
            recipientId = recipientId,
            message = message,
            hasInternetConnection = hasInternetConnection, // Pass the flag
            onSuccess = { 
                _error.value = null
                val currentTimestamp = System.currentTimeMillis()
                database.reference
                    .child("user_chats")
                    .child(currentUser.uid)
                    .child(recipientId)
                    .updateChildren(mapOf(
                        "lastMessage" to message,
                        "timestamp" to currentTimestamp,
                        "lastMessageSenderId" to currentUser.uid,
                        "lastMessageStatus" to initialStatus.name // Use initialStatus for preview
                    ))
                
                 database.reference
                    .child("user_chats")
                    .child(recipientId)
                    .child(currentUser.uid)
                     .updateChildren(mapOf(
                        "lastMessage" to message,
                        "timestamp" to currentTimestamp,
                        "lastMessageSenderId" to currentUser.uid,
                        "lastMessageStatus" to initialStatus.name // Use initialStatus for preview
                    ))
            },
            onError = { errorMessage ->
                _error.value = errorMessage
                // Remove temporary message on failure
                pendingOptimisticMessageId?.let { pId ->
                    allMessagesList.removeAll { it.messageId == pId }
                    _messages.value = allMessagesList.toList().sortedBy { it.timestamp }
                }
                pendingOptimisticMessageId = null
            }
        )
    }

    fun clearError() {
        _error.value = null
    }
} 