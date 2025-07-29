package com.temuin.temuin.ui.screens.profile

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.ui.screens.auth.PhoneAuthViewModel
import kotlinx.coroutines.launch
import com.google.firebase.database.FirebaseDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onNavigateBack: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    var showCustomStatusDialog by remember { mutableStateOf(false) }
    var customStatus by remember { mutableStateOf("") }
    var currentStatus by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val predefinedStatuses = listOf(
        "Available",
        "Busy",
        "At school",
        "At the movies",
        "At work",
        "Battery about to die",
        "Can't meet right now",
        "In a meeting",
        "At the gym",
        "Sleeping"
    )

    // Load current user status
    LaunchedEffect(Unit) {
        val userId = viewModel.getCurrentUserId()
        Log.d("StatusScreen", "Current userId: $userId")
        if (userId != null) {
            viewModel.getCurrentUserProfile(userId) { user ->
                Log.d("StatusScreen", "Loaded user profile: $user")
                currentStatus = user.status
                customStatus = user.status
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Error: Not logged in")
            }
        }
    }

    fun updateStatus(newStatus: String) {
        val userId = viewModel.getCurrentUserId()

        if (userId != null) {
            scope.launch {
                try {
                    // Directly update only the status field
                    val database = FirebaseDatabase.getInstance()
                    database.reference
                        .child("users")
                        .child(userId)
                        .child("status")
                        .setValue(newStatus)
                        .addOnSuccessListener {
                            currentStatus = newStatus
                            onNavigateBack()
                        }
                        .addOnFailureListener { e ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Error: Failed to update status")
                            }
                        }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error: Failed to update status")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Error: Not logged in")
            }
        }
    }

    if (showCustomStatusDialog) {
        AlertDialog(
            onDismissRequest = { showCustomStatusDialog = false },
            title = { Text("Custom Status") },
            text = {
                OutlinedTextField(
                    value = customStatus,
                    onValueChange = { customStatus = it },
                    label = { Text("Enter your status") },
                    supportingText = if (customStatus.isBlank()) {
                        { Text("Status is required") }
                    } else null,
                    isError = customStatus.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customStatus.isNotBlank()) {
                            updateStatus(customStatus)
                            showCustomStatusDialog = false
                        }
                    },
                    enabled = customStatus.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCustomStatusDialog = false
                    // Reset custom status to current status when canceling
                    customStatus = currentStatus
                }) {
                    Text("Cancel")
                }
            }
        )
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
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Currently set to section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Currently set to",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentStatus,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(
                            onClick = {
                                customStatus = currentStatus
                                showCustomStatusDialog = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Status",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Divider
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Select About section
            item {
                Text(
                    text = "Select About",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Predefined statuses
            items(predefinedStatuses) { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { updateStatus(status) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (status == currentStatus) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}