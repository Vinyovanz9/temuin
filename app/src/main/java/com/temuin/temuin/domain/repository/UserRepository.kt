package com.temuin.temuin.domain.repository

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.temuin.temuin.data.model.User
import kotlinx.coroutines.flow.StateFlow

interface UserRepository {

    val currentUser: StateFlow<FirebaseUser?>
//    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    suspend fun signInWithPhoneNumber(credential: PhoneAuthCredential): Result<FirebaseUser>
    suspend fun signOut()
    suspend fun createUserProfile(user: User)
    suspend fun getCurrentUserProfile(): User?
}