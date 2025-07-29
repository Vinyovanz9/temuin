package com.temuin.temuin.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.temuin.temuin.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.ui.screens.auth.PhoneAuthViewModel

@Composable
fun SplashScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToProfileSetup: () -> Unit,
    onNavigateToTerms: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance()
    var isCheckingAuth by remember { mutableStateOf(true) }
    val isFirstTime by viewModel.isFirstTime.collectAsState()
    
    LaunchedEffect(true) {
        delay(1000) // Show splash for 1 second

        if (isFirstTime) {
            isCheckingAuth = false
            onNavigateToTerms()
            return@LaunchedEffect
        }
        
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, check if profile exists and is complete
            database.reference
                .child("users")
                .child(currentUser.uid)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val snapshot = task.result
                        if (snapshot != null && snapshot.exists()) {
                            // Check if profile is complete (has name)
                            val name = snapshot.child("name").getValue(String::class.java)
                            if (!name.isNullOrBlank()) {
                                // Profile is complete, go to main screen
                                onNavigateToMain()
                            } else {
                                // Profile exists but incomplete, go to profile setup
                                onNavigateToProfileSetup()
                            }
                        } else {
                            // No profile exists, go to profile setup
                            onNavigateToProfileSetup()
                        }
                    } else {
                        // On error, safely redirect to auth
                        onNavigateToAuth()
                    }
                    isCheckingAuth = false
                }
        } else {
            // No user signed in, go to auth screen
            isCheckingAuth = false
            onNavigateToAuth()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.temuin_logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Temuin",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
} 