package com.temuin.temuin.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.temuin.temuin.data.model.MessageStatus
import com.temuin.temuin.domain.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
): ChatRepository {
    override fun initializeChat(
        otherUserId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: run {
            onError("User not authenticated")
            return
        }

        // First check if chat already exists
        database.reference
            .child("user_chats")
            .child(currentUser.uid)
            .child(otherUserId)
            .get()
            .addOnSuccessListener { existingChatSnapshot ->
                if (existingChatSnapshot.exists()) {
                    // Chat already exists, just call onSuccess
                    onSuccess()
                } else {
                    // Create new chat entries for both users
                    val updates = hashMapOf<String, Any>()

                    // Current user's chat entry
                    updates["user_chats/${currentUser.uid}/$otherUserId"] = hashMapOf(
                        "lastMessage" to "",
                        "timestamp" to ServerValue.TIMESTAMP,
                        "unreadCount" to 0,
                        "isPinned" to false,
                        "lastMessageStatus" to MessageStatus.SENT.name,
                        "lastMessageSenderId" to currentUser.uid
                    )

                    // Other user's chat entry
                    updates["user_chats/$otherUserId/${currentUser.uid}"] = hashMapOf(
                        "lastMessage" to "",
                        "timestamp" to ServerValue.TIMESTAMP,
                        "unreadCount" to 0,
                        "isPinned" to false,
                        "lastMessageStatus" to MessageStatus.SENT.name,
                        "lastMessageSenderId" to currentUser.uid
                    )

                    // Initialize message nodes for both users
                    updates["private_messages/${currentUser.uid}/$otherUserId"] = hashMapOf<String, Any>()
                    updates["private_messages/$otherUserId/${currentUser.uid}"] = hashMapOf<String, Any>()

                    database.reference.updateChildren(updates)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError(it.message ?: "Failed to initialize chat") }
                }
            }
            .addOnFailureListener { 
                onError(it.message ?: "Failed to check existing chat")
            }
    }

    override fun sendMessage(
        recipientId: String,
        message: String,
        hasInternetConnection: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return
        val initialStatus = if (hasInternetConnection) MessageStatus.DELIVERED else MessageStatus.SENT
        
        android.util.Log.d("ChatRepository", "Sending message: $message to $recipientId with internet: $hasInternetConnection, initialStatus: ${initialStatus.name}")
        
        val messageData = hashMapOf(
            "content" to message,
            "senderId" to currentUser.uid,
            "timestamp" to ServerValue.TIMESTAMP,
            "status" to initialStatus.name,
            "delivered" to hasInternetConnection,
            "read" to false
        )

        val senderMessageRef = database.reference
            .child("private_messages")
            .child(currentUser.uid)
            .child(recipientId)
            .push()
        
        val messageId = senderMessageRef.key ?: return

        val recipientMessageData = messageData.toMutableMap()

        // First check if recipient is viewing this chat
        database.reference
            .child("user_chats")
            .child(recipientId)
            .child(currentUser.uid)
            .child("isViewing")
            .get()
            .addOnSuccessListener { viewingSnapshot ->
                val isRecipientViewing = viewingSnapshot.getValue(Boolean::class.java) ?: false
                
                // Create a batch update for better synchronization
                val updates = hashMapOf<String, Any>(
                    "private_messages/${currentUser.uid}/$recipientId/$messageId" to messageData,
                    "private_messages/$recipientId/${currentUser.uid}/$messageId" to recipientMessageData,
                    "user_chats/${currentUser.uid}/$recipientId/lastMessage" to message,
                    "user_chats/${currentUser.uid}/$recipientId/timestamp" to ServerValue.TIMESTAMP,
                    "user_chats/${currentUser.uid}/$recipientId/lastMessageSenderId" to currentUser.uid,
                    "user_chats/${currentUser.uid}/$recipientId/lastMessageStatus" to initialStatus.name,
                    "user_chats/$recipientId/${currentUser.uid}/lastMessage" to message,
                    "user_chats/$recipientId/${currentUser.uid}/timestamp" to ServerValue.TIMESTAMP,
                    "user_chats/$recipientId/${currentUser.uid}/lastMessageSenderId" to currentUser.uid,
                    "user_chats/$recipientId/${currentUser.uid}/lastMessageStatus" to initialStatus.name
                )
                
                if (!isRecipientViewing) {
                    database.reference
                        .child("user_chats")
                        .child(recipientId)
                        .child(currentUser.uid)
                        .child("unreadCount")
                        .get()
                        .addOnSuccessListener { countSnapshot ->
                            val currentCount = countSnapshot.getValue(Int::class.java) ?: 0
                            updates["user_chats/$recipientId/${currentUser.uid}/unreadCount"] = currentCount + 1
                            updates["user_chats/$recipientId/${currentUser.uid}/lastMessageRead"] = false
                            
                            database.reference.updateChildren(updates)
                                .addOnSuccessListener {
                                    android.util.Log.d("ChatRepository", "Message batch update with unread count success")
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("ChatRepository", "Failed to send message with unread: ${e.message}")
                                    onError(e.message ?: "Failed to send message")
                                }
                        }
                        .addOnFailureListener {
                             android.util.Log.e("ChatRepository", "Failed to get unread count: ${it.message}")
                             // Proceed without unread count update if fetching fails
                             database.reference.updateChildren(updates)
                                .addOnSuccessListener { 
                                    android.util.Log.d("ChatRepository", "Message batch update (unread fetch failed) success")
                                    onSuccess() 
                                }
                                .addOnFailureListener { e -> 
                                    android.util.Log.e("ChatRepository", "Failed to send message (unread fetch failed): ${e.message}")
                                    onError(e.message ?: "Failed to send message") 
                                }
                        }
                } else {
                    database.reference.updateChildren(updates)
                        .addOnSuccessListener {
                            android.util.Log.d("ChatRepository", "Message batch update success (recipient viewing)")
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("ChatRepository", "Failed to send message (recipient viewing): ${e.message}")
                            onError(e.message ?: "Failed to send message")
                        }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRepository", "Failed to check if recipient is viewing: ${e.message}")
                onError(e.message ?: "Failed to check recipient status")
            }
    }

    override fun markMessageAsDelivered(recipientId: String, messageId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        android.util.Log.d("ChatRepoDeliver", "markMessageAsDelivered called. currentUserId (sender): $currentUserId, recipientId (receiver): $recipientId, messageId: $messageId")

        val senderMessageStatusRef = database.reference
            .child("private_messages")
            .child(currentUserId) // This is the sender of the original message
            .child(recipientId)   // The recipient of the original message
            .child(messageId)
            .child("status")

        senderMessageStatusRef.get().addOnSuccessListener { snapshot ->
            val currentStatus = snapshot.getValue(String::class.java)
            // Only update if current status is SENT, to avoid regressing from READ
            if (currentStatus == MessageStatus.SENT.name) {
                senderMessageStatusRef.setValue(MessageStatus.DELIVERED.name).addOnSuccessListener {
                    // Update the sender's chat preview
                    database.reference
                        .child("user_chats")
                        .child(currentUserId)
                        .child(recipientId)
                        .child("lastMessageStatus")
                        .setValue(MessageStatus.DELIVERED.name)
                    
                    android.util.Log.d("ChatRepository", "Message $messageId marked as DELIVERED for $currentUserId view of $recipientId")
                }
            }
        }

        // Mark as delivered in the recipient's copy of the message for their local state if needed
        val recipientMessageDeliveredRef = database.reference
            .child("private_messages")
            .child(recipientId)  // Recipient
            .child(currentUserId) // Sender
            .child(messageId)
            .child("delivered")
        recipientMessageDeliveredRef.setValue(true)
    }

    override fun markMessageAsRead(originalSenderId: String, messageId: String) {
        val readerUserId = auth.currentUser?.uid ?: return 
        android.util.Log.d("ChatRepoRead", "markMessageAsRead: originalSenderId: $originalSenderId, readerUserId: $readerUserId, messageId: $messageId")

        val senderMessageStatusRef = database.reference
            .child("private_messages")
            .child(originalSenderId)
            .child(readerUserId)
            .child(messageId)
            .child("status")
        senderMessageStatusRef.setValue(MessageStatus.READ.name)
            .addOnSuccessListener {
                android.util.Log.d("ChatRepoRead", "Set private_messages status to READ for $messageId for sender $originalSenderId")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRepoRead", "Failed to set private_messages status to READ for $messageId: ${e.message}")
            }

        // Update the 'read' flag in the reader's copy of the message
        val readerMessageReadRef = database.reference
            .child("private_messages")
            .child(readerUserId)
            .child(originalSenderId)
            .child(messageId)
            .child("read")
        readerMessageReadRef.setValue(true)

        // Update the sender's chat preview to show their message was read
        database.reference
            .child("user_chats")
            .child(originalSenderId)
            .child(readerUserId)
            .child("lastMessageStatus")
            .setValue(MessageStatus.READ.name)
            .addOnSuccessListener {
                android.util.Log.d("ChatRepoRead", "Set user_chats lastMessageStatus to READ for sender $originalSenderId, chat with $readerUserId")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRepoRead", "Failed to set user_chats lastMessageStatus to READ for $originalSenderId: ${e.message}")
            }
        
        android.util.Log.d("ChatRepository", "Message $messageId from $originalSenderId marked as READ by $readerUserId")
    }

    override fun markAllMessagesAsRead(chatId: String) {
        val currentUser = auth.currentUser ?: return
        
        // First mark all messages as delivered
        checkAndUpdatePreviousMessages(chatId)
        
        // Then get all messages that aren't READ yet
        database.reference
            .child("private_messages")
            .child(currentUser.uid)
            .child(chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { messageSnapshot ->
                    val status = messageSnapshot.child("status").getValue(String::class.java)
                    val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                    
                    // Mark all messages from the other user as READ
                    if (senderId == chatId && status != MessageStatus.READ.name) {
                        markMessageAsRead(chatId, messageSnapshot.key!!)
                    }
                }
            }
            
        // Also update the chat preview status
        database.reference
            .child("user_chats")
            .child(currentUser.uid)
            .child(chatId)
            .child("lastMessageSenderId")
            .get()
            .addOnSuccessListener { senderSnapshot ->
                val lastMessageSenderId = senderSnapshot.getValue(String::class.java)
                if (lastMessageSenderId == chatId) {
                    val updates = hashMapOf<String, Any>(
                        "/user_chats/$chatId/${currentUser.uid}/lastMessageStatus" to MessageStatus.READ.name,
                        "/user_chats/${currentUser.uid}/$chatId/lastMessageStatus" to MessageStatus.READ.name
                    )
        database.reference.updateChildren(updates)
                }
            }
    }

    // Store the listener reference in a map for cleanup
    private val messageListeners = mutableMapOf<String, com.google.firebase.database.ChildEventListener>()

    // Helper function to remove message listeners when no longer needed
    override fun cleanupMessageListeners(chatId: String) {
        val currentUser = auth.currentUser ?: return
        
        messageListeners[chatId]?.let { listener ->
            database.reference
                .child("private_messages")
                .child(currentUser.uid)
                .child(chatId)
                .removeEventListener(listener)
            
            messageListeners.remove(chatId)
        }
    }

    // Add function to update user's lastSeen timestamp
    override fun updateLastSeen() {
        val currentUser = auth.currentUser ?: return
        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("lastSeen")
            .setValue(System.currentTimeMillis())
    }

    private fun checkAndUpdatePreviousMessages(recipientId: String) {
        val currentUser = auth.currentUser ?: return

        // Get all messages sent to this recipient that aren't delivered yet
        database.reference.child("private_messages")
            .child(currentUser.uid)
            .child(recipientId)
            .orderByChild("status")
            .equalTo(MessageStatus.SENT.name)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { messageSnapshot ->
                    markMessageAsDelivered(recipientId, messageSnapshot.key!!)
                }
            }
    }

}