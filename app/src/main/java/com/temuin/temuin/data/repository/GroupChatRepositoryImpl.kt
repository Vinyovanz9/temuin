package com.temuin.temuin.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.temuin.temuin.data.model.MessageStatus
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.database.Transaction
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.ChildEventListener
import com.temuin.temuin.domain.repository.ChatRepository
import com.temuin.temuin.domain.repository.GroupChatRepository
import com.google.firebase.database.ServerValue

@Singleton
class GroupChatRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
): GroupChatRepository {
    private val groupMessageChangeListeners = mutableMapOf<String, ChildEventListener>()

    override fun sendGroupMessage(
        groupId: String,
        message: String,
        messageId: String,
        hasInternetConnection: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            val currentUserId = currentUser.uid
            val initialStatus = if (hasInternetConnection) MessageStatus.DELIVERED else MessageStatus.SENT

            database.reference.child("group_chats")
                .child(groupId)
                .child("members")
                .get()
                .addOnSuccessListener { membersSnapshot ->
                    val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                    val membersMap = membersSnapshot.getValue(membersType) ?: emptyMap()
                    val memberIds = membersMap.keys.toList()

                    // Initialize memberStatus: sender is READ, others are SENT
                    val memberStatusMap = memberIds.associateWith { memberId ->
                        if (memberId == currentUserId) MessageStatus.READ.name else MessageStatus.SENT.name
                    }

                    val messageData = hashMapOf(
                        "content" to message,
                        "senderId" to currentUserId,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "status" to initialStatus.name,
                        "memberStatus" to memberStatusMap
                    )

                    // Atomically update message and last message previews
                    val updates = hashMapOf<String, Any>()
                    updates["group_messages/$groupId/$messageId"] = messageData
                    updates["group_chats/$groupId/lastMessage"] = message
                    updates["group_chats/$groupId/lastMessageSenderId"] = currentUserId
                    updates["group_chats/$groupId/lastMessageTimestamp"] = ServerValue.TIMESTAMP
                    updates["group_chats/$groupId/lastMessageStatus"] = initialStatus.name

                    // Update last message status for each member's view of the group chat
                    memberIds.forEach { memberId ->
                        updates["users/$memberId/groupChats/$groupId/lastMessage"] = if (memberId == currentUserId) "You: $message" else message
                        updates["users/$memberId/groupChats/$groupId/lastMessageSenderId"] = currentUserId
                        updates["users/$memberId/groupChats/$groupId/lastMessageTimestamp"] = ServerValue.TIMESTAMP
                        updates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = 
                            if (memberId == currentUserId) initialStatus.name else MessageStatus.SENT.name
                        updates["users/$memberId/groupChats/$groupId/statusPreserved"] = false
                    }
                    
                    database.reference.updateChildren(updates)
                        .addOnSuccessListener {
                            // Increment unread count for other members if they are not actively viewing
                            memberIds.forEach { memberId ->
                                if (memberId != currentUserId) {
                                    incrementGroupUnreadCountSafe(groupId, memberId)
                                }
                            }
                            onSuccess()
                        }
                        .addOnFailureListener { e -> onError(e.message ?: "Failed to send message or update previews") }
                }
                .addOnFailureListener { e -> onError(e.message ?: "Failed to get group members") }
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error in sendGroupMessage")
        }
    }

    override fun markGroupMessageAsDelivered(groupId: String, messageId: String, userId: String, triggerRecalculation: Boolean) {
        // Check if user is actively viewing first
        database.reference.child("group_chats").child(groupId).child("activeViewers").child(userId).get()
            .addOnSuccessListener { activeViewerSnapshot ->
                val isViewing = activeViewerSnapshot.exists() && (activeViewerSnapshot.getValue(Long::class.java) ?: 0L > System.currentTimeMillis() - 60000)
                
                // Only proceed if user is not actively viewing
                if (!isViewing) {
                    val messageMemberStatusRef = database.reference
                        .child("group_messages")
                        .child(groupId)
                        .child(messageId)
                        .child("memberStatus")
                        .child(userId)
                    
                    messageMemberStatusRef.get().addOnSuccessListener { currentStatusSnapshot ->
                        // Only update if current status is SENT or null
                        val currentStatus = currentStatusSnapshot.getValue(String::class.java)
                        if (currentStatus == MessageStatus.SENT.name || currentStatus == null) {
                            messageMemberStatusRef.setValue(MessageStatus.DELIVERED.name)
                                .addOnSuccessListener {
                                    if (triggerRecalculation) {
                                        recalculateAndSetOverallGroupMessageStatus(groupId, messageId)
                                    }
                                }
                        }
                    }
                }
            }
    }

    override fun markGroupMessageAsRead(groupId: String, messageId: String, userId: String, triggerRecalculation: Boolean) {
        // Check if user is actively viewing first
        database.reference.child("group_chats").child(groupId).child("activeViewers").child(userId).get()
            .addOnSuccessListener { activeViewerSnapshot ->
                val isViewing = activeViewerSnapshot.exists() && (activeViewerSnapshot.getValue(Long::class.java) ?: 0L > System.currentTimeMillis() - 60000)
                
                // Only proceed if user is actively viewing
                if (isViewing) {
                    val messageMemberStatusRef = database.reference
                        .child("group_messages")
                        .child(groupId)
                        .child(messageId)
                        .child("memberStatus")
                        .child(userId)

                    messageMemberStatusRef.setValue(MessageStatus.READ.name)
                        .addOnSuccessListener {
                            if (triggerRecalculation) {
                                recalculateAndSetOverallGroupMessageStatus(groupId, messageId)
                            }
                        }
                }
            }
    }

    override fun recalculateAndSetOverallGroupMessageStatus(groupId: String, messageId: String) {
        val messageRef = database.reference.child("group_messages").child(groupId).child(messageId)
        
        messageRef.get().addOnSuccessListener { messageSnapshot ->
            if (!messageSnapshot.exists()) return@addOnSuccessListener

            val memberStatusType = object : GenericTypeIndicator<Map<String, String>>() {}
            val memberStatusMap = messageSnapshot.child("memberStatus").getValue(memberStatusType) ?: emptyMap()
            val messageSenderId = messageSnapshot.child("senderId").getValue(String::class.java) ?: return@addOnSuccessListener

            database.reference.child("group_chats")
                .child(groupId)
                .child("members")
                .get()
                .addOnSuccessListener { membersSnapshot ->
                    val membersType = object : GenericTypeIndicator<Map<String, Boolean>>() {}
                    val allMemberIds = membersSnapshot.getValue(membersType)?.keys?.toList() ?: emptyList()
                    
                    if (allMemberIds.isEmpty()) return@addOnSuccessListener

                    var allOthersRead = true
                    var allOthersDelivered = true

                    for (memberId in allMemberIds) {
                        if (memberId == messageSenderId) continue // Skip sender for "other members" logic

                        val status = memberStatusMap[memberId]
                        if (status != MessageStatus.READ.name) {
                            allOthersRead = false
                        }
                        if (status != MessageStatus.READ.name && status != MessageStatus.DELIVERED.name) {
                            allOthersDelivered = false
                        }
                    }

                    val newOverallStatus = when {
                        allOthersRead -> MessageStatus.READ
                        allOthersDelivered -> MessageStatus.DELIVERED
                        else -> MessageStatus.DELIVERED // Need check again
                    }
                    
                    val currentOverallStatus = messageSnapshot.child("status").getValue(String::class.java)

                    // Prepare updates
                    val updates = hashMapOf<String, Any?>()
                    var needsUpdate = false

                    if (currentOverallStatus != newOverallStatus.name) {
                        updates["group_messages/$groupId/$messageId/status"] = newOverallStatus.name
                        needsUpdate = true
                    }

                    // Check if this message is the group's last message
                    database.reference.child("group_chats").child(groupId).get()
                        .addOnSuccessListener { groupChatSnapshot ->
                            val groupLastMessageTimestamp = groupChatSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L
                            val messageTimestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                            if (messageTimestamp == groupLastMessageTimestamp) { // Indicates it's the last message
                                if (groupChatSnapshot.child("lastMessageStatus").getValue(String::class.java) != newOverallStatus.name) {
                                    updates["group_chats/$groupId/lastMessageStatus"] = newOverallStatus.name
                                    needsUpdate = true
                                }
                                // Update each member's view of the last message status
                                allMemberIds.forEach { memberId ->
                                   updates["users/$memberId/groupChats/$groupId/lastMessageStatus"] = newOverallStatus.name
                                   updates["users/$memberId/groupChats/$groupId/statusPreserved"] = false
                                }
                            }
                            
                            if (needsUpdate) {
                                database.reference.updateChildren(updates).addOnSuccessListener {
                                    // Log success or trigger further UI updates if necessary via ViewModel
                                    android.util.Log.d("GroupChatRepo", "Overall status for msg $messageId updated to $newOverallStatus")
                                }.addOnFailureListener {
                                     android.util.Log.e("GroupChatRepo", "Failed to update overall status for $messageId")
                                }
                            }
                        }
                }
        }.addOnFailureListener {
            android.util.Log.e("GroupChatRepo", "Failed to get message $messageId for status recalc")
        }
    }

    override fun setupGroupMessageDeliveryTracking(groupId: String) {
        val currentUser = auth.currentUser ?: return
        val currentUserId = currentUser.uid

        // Remove existing listener for this group to prevent duplicates
        groupMessageChangeListeners[groupId]?.let { listener ->
            database.reference.child("group_messages").child(groupId).removeEventListener(listener)
            android.util.Log.d("GroupChatRepo", "Removed existing delivery tracking listener for group $groupId")
        }

        val newListener = object: ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    handleMessageSnapshot(snapshot, groupId, currentUserId)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    handleMessageSnapshot(snapshot, groupId, currentUserId, isChange = true)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                     android.util.Log.e("GroupChatRepo", "Delivery tracking cancelled for group $groupId: ${error.message}")
                }
        }
        database.reference.child("group_messages").child(groupId).addChildEventListener(newListener)
        groupMessageChangeListeners[groupId] = newListener // Store the new listener
        android.util.Log.d("GroupChatRepo", "Setup new delivery tracking listener for group $groupId")
    }

    // New function to remove the listener when the ViewModel is cleared
    override fun removeDeliveryTrackingListener(groupId: String) {
        groupMessageChangeListeners[groupId]?.let { listener ->
            database.reference.child("group_messages").child(groupId).removeEventListener(listener)
            groupMessageChangeListeners.remove(groupId)
            android.util.Log.d("GroupChatRepo", "Successfully removed delivery tracking listener for group $groupId")
        }
    }

    private fun incrementGroupUnreadCountSafe(groupId: String, memberId: String) {
        val userGroupChatRef = database.reference.child("users").child(memberId).child("groupChats").child(groupId)

        // First, check if this memberId is an activeViewer of this groupId
        database.reference.child("group_chats").child(groupId).child("activeViewers").child(memberId).get()
            .addOnSuccessListener { activeViewerSnapshot ->
                if (activeViewerSnapshot.exists()) {
                    // User is actively viewing, do not increment unread count.
                    // Ensure lastMessageRead is true because they are about to see this new message.
                    userGroupChatRef.child("lastMessageRead").setValue(true)
                    android.util.Log.d("GroupChatRepo", "User $memberId is active in $groupId, not incrementing unread.")
                } else {
                    // User is not actively viewing, proceed with unread count increment.
                    userGroupChatRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val currentUnreadCount = currentData.child("unreadCount").getValue(Long::class.java) ?: 0L
                            currentData.child("unreadCount").value = currentUnreadCount + 1
                            currentData.child("lastMessageRead").value = false // New message is unread
                            android.util.Log.d("GroupChatRepo", "Incremented unread for $memberId in $groupId to ${currentUnreadCount + 1}")
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(databaseError: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
                            if (databaseError != null) {
                                android.util.Log.e("GroupChatRepo", "incrementGroupUnreadCountSafe transaction failed for $memberId, $groupId: ${databaseError.message}")
                            }
                        }
                    })
                }
            }.addOnFailureListener {
                android.util.Log.e("GroupChatRepo", "Failed to check activeViewer for $memberId in $groupId: ${it.message}")
                // Fallback: If checking activeViewer fails, proceed with incrementing unread count as a safety measure,
                // so messages aren't missed. Alternatively, choose not to increment.
                // For now, let's stick to the original behavior on failure to avoid silent drops of unread status.
                userGroupChatRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentUnreadCount = currentData.child("unreadCount").getValue(Long::class.java) ?: 0L
                        currentData.child("unreadCount").value = currentUnreadCount + 1
                        currentData.child("lastMessageRead").value = false
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(databaseError: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
                        if (databaseError != null) {
                            android.util.Log.w("GroupChatRepo", "incrementGroupUnreadCountSafe (fallback after activeViewer check fail) transaction failed for $memberId, $groupId: ${databaseError.message}")
                        }
                    }
                })
            }
    }

    private fun handleMessageSnapshot(snapshot: DataSnapshot, groupId: String, currentUserId: String, isChange: Boolean = false) {
        val messageId = snapshot.key ?: return
        if (messageId == "_meta") return

        val senderId = snapshot.child("senderId").getValue(String::class.java) ?: return
        val memberStatusType = object : GenericTypeIndicator<Map<String, String>>() {}
        val memberStatusMap = snapshot.child("memberStatus").getValue(memberStatusType) ?: emptyMap()

        // If current user sent the message, check status of other members
        if (senderId == currentUserId) {
            recalculateAndSetOverallGroupMessageStatus(groupId, messageId)
        } else {
            // If message is from another user, mark as delivered for current user if applicable
            val currentUserStatus = memberStatusMap[currentUserId]
            if (currentUserStatus == null || currentUserStatus == MessageStatus.SENT.name) {
                // Check if user is actively viewing THIS group
                database.reference.child("group_chats").child(groupId).child("activeViewers").child(currentUserId).get()
                    .addOnSuccessListener { activeViewerSnapshot ->
                        val isViewing = activeViewerSnapshot.exists() && (activeViewerSnapshot.getValue(Long::class.java) ?: 0L > System.currentTimeMillis() - 60000)
                        if (isViewing) {
                            // Only mark as read if user is actively viewing AND the message is new
                            if (!isChange) {
                                markGroupMessageAsRead(groupId, messageId, currentUserId)
                            }
                        } else {
                            markGroupMessageAsDelivered(groupId, messageId, currentUserId)
                            if (!isChange) { // Only increment unread if it's a new message
                                incrementGroupUnreadCountSafe(groupId, currentUserId)
                            }
                        }
                    }
            }
        }
    }

    // Add this new function to preserve previous message status
    // This function seems to be incorrectly placed or named based on its previous implementation.
    // It was trying to set the *new* message's status based on the *old* last message's status, which is not the desired logic.
    // The status of a new message should always start as SENT and progress from there.
    // The `lastMessageStatus` for the group and for each user will be updated by `recalculateAndSetOverallMessageStatus`.
    // For now, I will comment it out to avoid confusion and ensure the new logic is implemented cleanly.
    /*
    private fun preservePreviousMessageStatus(groupId: String, updateMap: HashMap<String, Any>) {
        // Get current message status from group chat
        database.reference
            .child("group_chats")
            .child(groupId)
            .child("lastMessageStatus")
            .get()
            .addOnSuccessListener { statusSnapshot ->
                val currentStatus = statusSnapshot.getValue(String::class.java)

                // Only update if there's a meaningful status (READ or DELIVERED)
                if (currentStatus == MessageStatus.READ.name || currentStatus == MessageStatus.DELIVERED.name) {
                    // Save this status to the new message
                    updateMap["lastMessageStatus"] = currentStatus
                }

                // Apply the updates
                database.reference
                    .child("group_chats")
                    .child(groupId)
                    .updateChildren(updateMap)
            }
    }
    */
}