package com.temuin.temuin.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TemuinApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize Google Places API
        if (!Places.isInitialized()) {
            val ai: ApplicationInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )

            val apiKey = ai.metaData?.getString("com.google.android.geo.API_KEY")

            // Get API key from AndroidManifest.xml metadata
            try {
                if (apiKey != null) {
                    Places.initialize(applicationContext, apiKey)
                } else {
                    // Fallback to a placeholder for development
                    Places.initialize(applicationContext, "API Key is null")
                }
            } catch (e: Exception) {
                // Fallback initialization
                Places.initialize(applicationContext, "API Key is null")
            }
        }

        // Get FCM token and save it
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Save token to user's profile if logged in
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // This will be handled by the TemuinMessagingService onNewToken method
            }
        }
    }
}