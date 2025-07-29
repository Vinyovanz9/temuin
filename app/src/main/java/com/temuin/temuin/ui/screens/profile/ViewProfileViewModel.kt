package com.temuin.temuin.ui.screens.profile

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

@HiltViewModel
class ViewProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isFriend = MutableStateFlow(false)
    val isFriend = _isFriend.asStateFlow()

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                database.reference.child("users").child(userId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val profile = snapshot.getValue(User::class.java)
                            _userProfile.value = profile
                            _isLoading.value = false
                        }

                        override fun onCancelled(error: DatabaseError) {
                            _error.value = error.message
                            _isLoading.value = false
                        }
                    })
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun checkFriendshipStatus(userId: String) {
        val currentUser = auth.currentUser ?: return
        
        database.reference.child("users")
            .child(currentUser.uid)
            .child("friends")
            .child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _isFriend.value = snapshot.exists()
                }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                }
            })
    }

    fun addFriend(userId: String, onComplete: () -> Unit = {}) {
        val currentUser = auth.currentUser ?: return
        
        // Add to current user's friends list
        database.reference
            .child("users")
            .child(currentUser.uid)
            .child("friends")
            .child(userId)
            .setValue(true)
            .addOnSuccessListener {
                // Add to other user's friends list
                database.reference
                    .child("users")
                    .child(userId)
                    .child("friends")
                    .child(currentUser.uid)
                    .setValue(true)
                    .addOnSuccessListener {
                        onComplete()
                    }
                    .addOnFailureListener { e ->
                        _error.value = e.message
                    }
            }
            .addOnFailureListener { e ->
                _error.value = e.message
            }
    }

    fun clearError() {
        _error.value = null
    }
} 