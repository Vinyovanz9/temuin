package com.temuin.temuin.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.temuin.temuin.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.io.ByteArrayOutputStream
import androidx.core.content.edit
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import com.google.firebase.database.database
import com.temuin.temuin.data.local.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.temuin.temuin.BuildConfig
import com.google.firebase.messaging.FirebaseMessaging

@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Ideal)
    val authState = _authState.asStateFlow()
    private val userRef = Firebase.database.reference.child("users")
    private val firebaseAuth: FirebaseAuth = Firebase.auth
    private val database: FirebaseDatabase = Firebase.database
    private var recaptchaClient: RecaptchaClient? = null

    // Terms screen state
    val isFirstTime: StateFlow<Boolean> = preferences.isFirstTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun setFirstTimeDone() {
        viewModelScope.launch {
            preferences.setFirstTimeDone()
        }
    }

    private fun getRecaptchaSiteKey(activity: Activity): String {
        val applicationInfo = activity.packageManager.getApplicationInfo(
            activity.packageName,
            PackageManager.GET_META_DATA
        )
        return applicationInfo.metaData.getString("com.google.android.recaptcha.enterprise.site_key")
            ?: throw IllegalStateException("reCAPTCHA site key not found in AndroidManifest.xml")
    }

    fun initializeRecaptcha(activity: Activity) {
        viewModelScope.launch {
            try {
                val siteKey = BuildConfig.RECAPTCHA_SITE_KEY.trim().replace("'", "")
                val result = Recaptcha.getClient(activity.application, siteKey)
                recaptchaClient = result.getOrThrow()
                
                // Execute reCAPTCHA verification
                val recaptchaResult = recaptchaClient?.execute(RecaptchaAction.LOGIN)
                val token = recaptchaResult?.getOrNull()
                if (token != null) {
                    Log.d("Recaptcha", "Token retrieved successfully")
                } else {
                    Log.e("Recaptcha", "Failed to get token")
                    _authState.value = AuthState.Error("Failed to verify security token. Please try again.")
                }
            } catch (e: Exception) {
                Log.e("Recaptcha", "Error initializing reCAPTCHA: ${e.message}", e)
                _authState.value = AuthState.Error("Failed to initialize security verification. Please try again.")
            }
        }
    }

    private fun executeRecaptcha(activity: Activity, onSuccess: (String) -> Unit) {
        val client = recaptchaClient
        if (client == null) {
            initializeRecaptcha(activity)
            _authState.value = AuthState.Error("Security verification not ready. Please try again.")
            return
        }

        viewModelScope.launch {
            try {
                val result = client.execute(RecaptchaAction.LOGIN)
                val token = result.getOrNull()
                if (token != null) {
                    onSuccess(token)
                } else {
                    _authState.value = AuthState.Error("Security verification failed. Please try again.")
                }
            } catch (e: Exception) {
                Log.e("Recaptcha", "Error executing reCAPTCHA", e)
                _authState.value = AuthState.Error("Security verification failed. Please try again.")
            }
        }
    }

    fun sendVerificationCode(phoneNumber: String, activity: Activity) {
        _authState.value = AuthState.Loading

        executeRecaptcha(activity) { recaptchaToken ->
            val option = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    super.onCodeSent(id, token)
                    Log.d("PhoneAuth", "onCodeSent triggered, verification ID: $id")
                    _authState.value = AuthState.CodeSent(verificationId = id)
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signinWithCredential(credential, context = activity)
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    Log.e("PhoneAuth", "Verification failed", exception)
                    
                    val errorMessage = when {
                        exception.message?.contains("17499") == true || 
                        exception.message?.contains("BILLING_NOT_ENABLED") == true -> {
                            "Firebase Phone Authentication billing is not enabled. Please contact the app administrator."
                        }
                        exception.message?.contains("No Recaptcha Enterprise siteKey configured") == true -> {
                            "reCAPTCHA verification is not properly configured. Please contact the app administrator."
                        }
                        exception.message?.contains("We have blocked all requests") == true -> {
                            "Too many requests. Please try again later."
                        }
                        exception.message?.contains("The format of the phone number provided is incorrect") == true -> {
                            "Invalid phone number format. Please check the number and try again."
                        }
                        else -> exception.message ?: "Verification failed. Please try again."
                    }
                    
                    _authState.value = AuthState.Error(errorMessage)
                }
            }

            try {
                val phoneAuthOptions = PhoneAuthOptions.newBuilder(firebaseAuth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(option)
                    .build()

                PhoneAuthProvider.verifyPhoneNumber(phoneAuthOptions)
            } catch (e: Exception) {
                Log.e("PhoneAuth", "Error setting up phone authentication", e)
                _authState.value = AuthState.Error("Failed to initialize phone authentication. Please try again.")
            }
        }
    }

    private fun signinWithCredential(credential: PhoneAuthCredential, context: Context) {
        _authState.value = AuthState.Loading

        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = getCurrentUser()
                    if (user != null) {
                        // Get FCM token first
                        FirebaseMessaging.getInstance().token
                            .addOnCompleteListener { tokenTask ->
                                val fcmToken = if (tokenTask.isSuccessful) tokenTask.result else null
                                
                        // Check if user profile exists
                        userRef.child(user.uid).get()
                            .addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    val userProfile = snapshot.getValue(User::class.java)
                                    if (userProfile != null && !userProfile.name.isNullOrBlank()) {
                                                // Update FCM token for existing user
                                                if (fcmToken != null) {
                                                    userRef.child(user.uid).child("fcmToken").setValue(fcmToken)
                                                }
                                        markUserAsSignedIn(context)
                                        _authState.value = AuthState.Success(userProfile)
                                    } else {
                                        // Profile exists but name is empty or null
                                        val newUser = User(
                                            userId = user.uid,
                                            phoneNumber = user.phoneNumber ?: "",
                                            name = "",
                                            status = "",
                                                    profileImage = null,
                                                    fcmToken = fcmToken
                                        )
                                        _authState.value = AuthState.Success(newUser)
                                    }
                                } else {
                                    // No profile exists, create a new user
                                    val newUser = User(
                                        userId = user.uid,
                                        phoneNumber = user.phoneNumber ?: "",
                                        name = "",
                                                status = "Hey there! I'm using Temuin",
                                                profileImage = null,
                                                fcmToken = fcmToken
                                    )
                                            userRef.child(user.uid).setValue(newUser)
                                    _authState.value = AuthState.Success(newUser)
                                }
                            }
                            .addOnFailureListener {
                                _authState.value = AuthState.Error("Failed to fetch user profile")
                                    }
                            }
                    } else {
                        _authState.value = AuthState.Error("Authentication failed")
                    }
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Sign-in failed")
                }
            }
    }

    fun markUserAsSignedIn(context: Context){
        val sharedPreference = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreference.edit { putBoolean("isSignedIn", true) }
    }

    fun verifyCode(otp: String, context: Context){

        val currentAuthState = _authState.value
        if (currentAuthState !is AuthState.CodeSent || currentAuthState.verificationId.isEmpty()){
            Log.e("PhoneAuth", "Attempting to verify OTP without a valid verification ID")
            return
        }

        val credential = PhoneAuthProvider.getCredential(currentAuthState.verificationId, otp)
        signinWithCredential(credential, context)
    }

    fun getCurrentUserProfile(userId: String, callback: (User) -> Unit) {
        userRef.child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    callback(user)
                } else {
                    // If user data can't be parsed, treat as new user
                    callback(User(
                        userId = userId,
                        phoneNumber = getCurrentUser()?.phoneNumber ?: "",
                        name = "",
                        status = "",
                        profileImage = null,
                        fcmToken = null
                    ))
                }
            } else {
                // If no profile exists, create a new user object
                callback(User(
                    userId = userId,
                    phoneNumber = getCurrentUser()?.phoneNumber ?: "",
                    name = "",
                    status = "",
                    profileImage = null,
                    fcmToken = null
                ))
            }
        }.addOnFailureListener { exception ->
            Log.e("PhoneAuthViewModel", "Error getting user profile: ${exception.message}")
            // In case of error, return empty user to ensure profile setup
            callback(User(
                userId = userId,
                phoneNumber = getCurrentUser()?.phoneNumber ?: "",
                name = "",
                status = "",
                profileImage = null,
                fcmToken = null
            ))
        }
    }

    suspend fun saveUserProfile(userId: String, name: String, status: String, profileImage: Bitmap?) {
        try {
            if (name.isBlank()) {
                throw IllegalArgumentException("Name cannot be empty")
            }
            if (status.isBlank()) {
                throw IllegalArgumentException("Status cannot be empty")
            }

            // First get current user data
            val currentSnapshot = database.reference.child("users").child(userId).get().await()
            val userUpdates = mutableMapOf<String, Any?>()
            
            // Preserve existing data
            currentSnapshot.children.forEach { child ->
                when (child.key) {
                    "name", "status", "profileImage" -> {} // Skip these as we'll update them
                    else -> child.value?.let { userUpdates[child.key!!] = it }
                }
            }

            // Update with new data
            userUpdates["name"] = name
            userUpdates["status"] = status
            userUpdates["phoneNumber"] = firebaseAuth.currentUser?.phoneNumber ?: ""

            // Handle profile image
            if (profileImage != null) {
                val base64Image = convertBitmapToBase64(profileImage)
                userUpdates["profileImage"] = base64Image
            }

            // Get and update FCM token
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        userUpdates["fcmToken"] = task.result
                    }
            // Update the database with all data
            database.reference.child("users").child(userId)
                .updateChildren(userUpdates)
                .addOnSuccessListener {
                    Log.d("PhoneAuthViewModel", "User profile updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("PhoneAuthViewModel", "Error updating user profile", e)
                    throw e
                }
                }

        } catch (e: Exception) {
            Log.e("PhoneAuthViewModel", "Error in saveUserProfile", e)
            throw e
        }
    }

    internal fun convertBitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    fun resetAuthState(){

        _authState.value = AuthState.Ideal
    }

    fun signOut(activity: Activity) {
        try {
            // Get current user ID before signing out
            val currentUserId = getCurrentUser()?.uid
            
            // Sign out from Firebase Auth first
            firebaseAuth.signOut()
            
            // Disconnect from the database reference for the current user
            if (currentUserId != null) {
                database.reference.child("users").child(currentUserId).keepSynced(false)
            }
            
            // Clear shared preferences
            val sharedPreference = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sharedPreference.edit {
                putBoolean("isSignedIn", false)
                clear()
                apply()
            }
            
            // Reset auth state
            _authState.value = AuthState.Ideal
            
        } catch (e: Exception) {
            Log.e("PhoneAuthViewModel", "Error during sign out: ${e.message}")
            // Even if there's an error, try to sign out from Firebase Auth
            firebaseAuth.signOut()
            _authState.value = AuthState.Ideal
        }
    }

    fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    fun getCurrentUser() = firebaseAuth.currentUser

    suspend fun getCurrentUserName(): String? {
        return try {
            val userId = getCurrentUserId() ?: return null
            val snapshot = database.reference.child("users").child(userId).get().await()
            snapshot.child("name").getValue(String::class.java)
        } catch (e: Exception) {
            Log.e("PhoneAuthViewModel", "Error getting user name", e)
            null
        }
    }

    fun updateProfileImage(userId: String, base64Image: String?) {
        database.reference
            .child("users")
            .child(userId)
            .child("profileImage")
            .setValue(base64Image)
            .addOnFailureListener { e ->
                Log.e("PhoneAuthViewModel", "Error updating profile image", e)
                throw e
            }
    }

    fun deleteAccount(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val currentUser = getCurrentUser() ?: return@launch onComplete(false)
                val userId = currentUser.uid

                // Delete user data from Realtime Database
                database.reference.child("users").child(userId).removeValue().await()
                database.reference.child("friends").child(userId).removeValue().await()
                database.reference.child("chats").child(userId).removeValue().await()
                database.reference.child("notifications").child(userId).removeValue().await()
                database.reference.child("schedules").orderByChild("userId").equalTo(userId).get().await().children.forEach { snapshot ->
                    snapshot.ref.removeValue().await()
                }

                // Delete user from Authentication
                currentUser.delete().await()
                
                onComplete(true)
            } catch (e: Exception) {
                println("Error deleting account: ${e.message}")
                onComplete(false)
            }
        }
    }
}

sealed class AuthState {
    object Ideal:AuthState()
    object Loading:AuthState()
    data class CodeSent(val verificationId:String): AuthState()
    data class Success(val user: User): AuthState()
    data class Error(val message: String): AuthState()
    object AUTHENTICATED: AuthState()
}