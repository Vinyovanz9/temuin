package com.temuin.temuin.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.temuin.temuin.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.temuin.temuin.data.repository.ChatRepositoryImpl

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val chatRepositoryImpl: ChatRepositoryImpl
) : ViewModel() {

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _potentialFriend = MutableStateFlow<User?>(null)
    val potentialFriend = _potentialFriend.asStateFlow()

    private val friendDataListeners = mutableMapOf<String, ValueEventListener>()

    init {
        loadFriends()
    }

    internal fun loadFriends() {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true

        val friendsRef = database.reference
            .child("users")
            .child(currentUser.uid)
            .child("friends")

        val friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                    val friendIds = snapshot.children.mapNotNull { it.key }
                    if (friendIds.isEmpty()) {
                        _friends.value = emptyList()
                        _isLoading.value = false
                    return
                    }

                // Remove old listeners
                    friendDataListeners.forEach { (_, listener) ->
                        database.reference.child("users").removeEventListener(listener)
                    }
                    friendDataListeners.clear()

                // Create a new list to store friend data
                val currentFriends = mutableListOf<User>()

                // Set up listeners for each friend
                    friendIds.forEach { friendId ->
                        val listener = object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                            try {
                                // Extract user data manually to avoid deserialization issues
                                val userId = userSnapshot.key ?: return
                                val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                                val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                                val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                                val status = userSnapshot.child("status").getValue(String::class.java) ?: ""
                                val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java)

                                // Create User object with empty friends map
                                val friend = User(
                                    userId = userId,
                                    name = name,
                                    phoneNumber = phoneNumber,
                                    profileImage = profileImage,
                                    status = status,
                                    friends = emptyMap(), // We don't need friends data for friend list display
                                    fcmToken = fcmToken
                                )

                                val existingIndex = currentFriends.indexOfFirst { it.userId == friend.userId }
                                if (existingIndex != -1) {
                                    currentFriends[existingIndex] = friend
                                    } else {
                                    currentFriends.add(friend)
                                    }
                                _friends.value = currentFriends.sortedBy { it.name }
                            } catch (e: Exception) {
                                _error.value = "Error loading friend data: ${e.message}"
                            } finally {
                                _isLoading.value = false
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                _error.value = error.message
                            _isLoading.value = false
                            }
                        }

                        database.reference
                            .child("users")
                            .child(friendId)
                            .addValueEventListener(listener)
                        
                        friendDataListeners[friendId] = listener
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
                _isLoading.value = false
            }
        }

        friendsRef.addValueEventListener(friendsListener)
    }

    override fun onCleared() {
        super.onCleared()
        // Remove all listeners when ViewModel is cleared
        friendDataListeners.forEach { (_, listener) ->
            database.reference.child("users").removeEventListener(listener)
        }
        friendDataListeners.clear()
    }

    fun removeFriend(friendId: String) {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true
        
        // Remove from database
        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("friends")
            .child(friendId)
            .removeValue()
            .addOnSuccessListener {
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                _error.value = "Failed to remove friend: ${e.message}"
                _isLoading.value = false
            }
    }

    fun clearError() {
        _error.value = null
    }

    fun initializeChat(otherUserId: String, onSuccess: () -> Unit) {
        _isLoading.value = true

        chatRepositoryImpl.initializeChat(
            otherUserId = otherUserId,
            onSuccess = {
                _isLoading.value = false
                onSuccess()
            },
            onError = { error ->
                _isLoading.value = false
                _error.value = error
            }
        )
    }

    fun searchPotentialFriend(phoneNumber: String) {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true
        _error.value = null
        _potentialFriend.value = null

        // Search for user by phone number
        database.reference
            .child("users")
            .orderByChild("phoneNumber")
            .equalTo(phoneNumber)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    try {
                        val userSnapshot = snapshot.children.first()
                        val userId = userSnapshot.key ?: throw Exception("Invalid user data")
                        val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                        val phone = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                        val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                        val status = userSnapshot.child("status").getValue(String::class.java) ?: ""

                        val foundUser = User(
                            userId = userId,
                            name = name,
                            phoneNumber = phone,
                            profileImage = profileImage,
                            status = status,
                            friends = emptyMap()
                        )

                        if (foundUser.userId != currentUser.uid) {
                        // Check if already friends
                        database.reference
                            .child("users")
                            .child(currentUser.uid)
                            .child("friends")
                            .child(foundUser.userId)
                            .get()
                            .addOnSuccessListener { friendSnapshot ->
                                if (friendSnapshot.exists()) {
                                    _error.value = "This user is already your friend"
                                } else {
                                    _potentialFriend.value = foundUser
                                }
                                _isLoading.value = false
                            }
                        } else {
                        _error.value = "You cannot add yourself as a friend"
                            _isLoading.value = false
                        }
                    } catch (e: Exception) {
                        _error.value = "Error processing user data: ${e.message}"
                        _isLoading.value = false
                    }
                } else {
                    _error.value = "User not found with this phone number"
                    _isLoading.value = false
                }
            }
            .addOnFailureListener { e ->
                _error.value = "Error searching for user: ${e.message}"
                _isLoading.value = false
            }
    }

    fun confirmAddFriend() {
        val currentUser = auth.currentUser ?: return
        val friendToAdd = _potentialFriend.value ?: return
        _isLoading.value = true

        // Create a map for the friend entry
        val friendData = mapOf(
            friendToAdd.userId to true
        )

        // Update the friends map in the database
        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("friends")
            .updateChildren(friendData)
            .addOnSuccessListener {
                _error.value = null
                _potentialFriend.value = null
                _isLoading.value = false
                loadFriends() // Reload friends list to reflect changes
            }
            .addOnFailureListener { e ->
                _error.value = "Failed to add friend: ${e.message}"
                _isLoading.value = false
            }
    }

    fun clearPotentialFriend() {
        _potentialFriend.value = null
        _error.value = null
    }
} 