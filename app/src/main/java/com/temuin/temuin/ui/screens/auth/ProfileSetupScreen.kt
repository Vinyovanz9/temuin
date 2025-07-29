package com.temuin.temuin.ui.screens.auth

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onNavigateToSchedule: () -> Unit,
    onViewFullPhoto: (Bitmap) -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var profileImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var bitmapImage by rememberSaveable { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isStatusDropdownExpanded by remember { mutableStateOf(false) }
    var isCustomStatusDialogVisible by remember { mutableStateOf(false) }
    var customStatus by rememberSaveable { mutableStateOf("") }
    var showPhotoOptions by remember { mutableStateOf(false) }

    val predefinedStatuses = listOf(
        "Available",
        "At school",
        "At the gym",
        "At the movies",
        "At work",
        "Battery about to die",
        "Busy",
        "Can't meet right now",
        "In a meeting",
        "Sleeping",
        "Custom status..."
    )

    // Prevent back navigation during profile setup
    BackHandler {
        // Do nothing, disabling back navigation
    }

    val context = LocalContext.current
    val firebaseAuth = Firebase.auth
    val userId = firebaseAuth.currentUser?.uid ?: ""
    val scope = rememberCoroutineScope()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profileImageUri = uri
        uri?.let {
            bitmapImage = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            }
        }
    }

    // Photo options dialog
    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                            showPhotoOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change profile photo")
                    }

                    TextButton(
                        onClick = {
                            bitmapImage = null
                            profileImageUri = null
                            showPhotoOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Remove profile photo",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            properties = DialogProperties(dismissOnClickOutside = true)
        )
    }

    // Custom status dialog
    if (isCustomStatusDialogVisible) {
        AlertDialog(
            onDismissRequest = { isCustomStatusDialogVisible = false },
            title = { Text("Custom Status") },
            text = {
                OutlinedTextField(
                    value = customStatus,
                    onValueChange = { customStatus = it },
                    label = { Text("Enter your status") },
                    singleLine = true,
                    supportingText = if (customStatus.isBlank()){
                        { Text("Status is required") }
                    } else null,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customStatus.isNotBlank()) {
                            status = customStatus
                            isCustomStatusDialogVisible = false
                        }
                    },
                    enabled = customStatus.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCustomStatusDialogVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Load user data
    LaunchedEffect(Unit) {
        viewModel.getCurrentUserProfile(userId) { user ->
            if (user.name.isNullOrBlank()) {
                // Stay on profile setup screen
                name = user.name ?: ""
                status = user.status ?: ""
                customStatus = user.status ?: ""
            } else {
                onNavigateToSchedule()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (!isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Setup Your Profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        // Profile Image Section
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(
                                        enabled = bitmapImage != null,
                                        onClick = { bitmapImage?.let { onViewFullPhoto(it) } }
                                    )
                            ) {
                                if (bitmapImage != null) {
                                    Image(
                                        bitmap = bitmapImage!!.asImageBitmap(),
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Add Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Edit text button
                            TextButton(
                                onClick = { 
                                    if (bitmapImage != null) {
                                        showPhotoOptions = true
                                    } else {
                                        imagePickerLauncher.launch("image/*")
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = if (bitmapImage != null) "Edit" else "Add",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Your name") },
                                placeholder = { Text("Enter your name") },
                                supportingText = if (name.isBlank()) {
                                    { Text("Name is required") }
                                } else null,
                                isError = name.isBlank(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            )

                            // Status Dropdown
                            ExposedDropdownMenuBox(
                                expanded = isStatusDropdownExpanded,
                                onExpandedChange = { isStatusDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = status,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Your status") },
                                    placeholder = { Text("Select your status") },
                                    trailingIcon = {
                                        Icon(Icons.Default.KeyboardArrowDown, "Expand status options")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = isStatusDropdownExpanded,
                                    onDismissRequest = { isStatusDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    predefinedStatuses.forEach { predefinedStatus ->
                                        DropdownMenuItem(
                                            text = { Text(predefinedStatus) },
                                            onClick = {
                                                if (predefinedStatus == "Custom status...") {
                                                    isCustomStatusDialogVisible = true
                                                } else {
                                                    status = predefinedStatus
                                                }
                                                isStatusDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (name.isNotBlank() && status.isNotBlank()) {
                                        isLoading = true
                                        scope.launch {
                                            try {
                                                viewModel.saveUserProfile(userId, name, status, bitmapImage)
                                                onNavigateToSchedule()
                                            } catch (e: Exception) {
                                                // Show error message
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                enabled = name.isNotBlank() && status.isNotBlank() && !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Continue")
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}