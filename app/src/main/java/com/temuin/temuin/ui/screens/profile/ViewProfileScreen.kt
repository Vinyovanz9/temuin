package com.temuin.temuin.ui.screens.profile

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onViewFullPhoto: (android.graphics.Bitmap) -> Unit,
    onNavigateToChat: (String, String, String) -> Unit,
    viewModel: ViewProfileViewModel = hiltViewModel()
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isFriend by viewModel.isFriend.collectAsState()
    var showErrorDialog by remember { mutableStateOf(false) }
    var profileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(userId) {
        viewModel.loadUserProfile(userId)
        viewModel.checkFriendshipStatus(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                userProfile?.let { profile ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Profile Picture
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(
                                    enabled = profileBitmap != null,
                                    onClick = { profileBitmap?.let { onViewFullPhoto(it) } }
                                )
                        ) {
                            if (profile.profileImage != null) {
                                profileBitmap = remember(profile.profileImage) {
                                    try {
                                        val imageBytes = Base64.decode(profile.profileImage, Base64.DEFAULT)
                                        val inputStream = ByteArrayInputStream(imageBytes)
                                        BitmapFactory.decodeStream(inputStream)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                
                                if (profileBitmap != null) {
                                    Image(
                                        bitmap = profileBitmap!!.asImageBitmap(),
                                        contentDescription = "Profile picture of ${profile.name}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Default profile picture",
                                        modifier = Modifier
                                            .padding(32.dp)
                                            .fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Default profile picture",
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Name
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Phone Number
                        Text(
                            text = profile.phoneNumber,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status
                        if (profile.status.isNotEmpty()) {
                            Text(
                                text = profile.status,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Buttons Column
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Message Button
                            Button(
                                onClick = { 
                                    onNavigateToChat(userId, profile.name, profile.phoneNumber)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Message")
                            }

                            // Add Friend Button
                            if (!isFriend) {
                                OutlinedButton(
                                    onClick = { 
                                        viewModel.addFriend(userId) {
                                            // Optional: Show success message or handle completion
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add Friend")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error Dialog
        error?.let { errorMessage ->
            if (showErrorDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showErrorDialog = false
                        viewModel.clearError()
                    },
                    title = { Text("Error") },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showErrorDialog = false
                                viewModel.clearError()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }

        LaunchedEffect(error) {
            if (error != null) {
                showErrorDialog = true
            }
        }
    }
} 