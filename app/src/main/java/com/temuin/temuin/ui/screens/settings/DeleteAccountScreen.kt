package com.temuin.temuin.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.ui.screens.auth.PhoneAuthViewModel
import com.temuin.temuin.data.model.CountryCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    var phoneNumber by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(CountryCode.INDONESIA) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isPhoneFieldFocused by remember { mutableStateOf(false) }

    fun validateAndDelete() {
        if (phoneNumber.isBlank()) {
            errorMessage = "Please enter your phone number"
            showError = true
            return
        }

        // Process phone number
        val processedNumber = if (phoneNumber.startsWith("0")) {
            phoneNumber.substring(1)
        } else {
            phoneNumber
        }
        val fullPhoneNumber = "${selectedCountry.prefix}$processedNumber"

        // Get current user's phone number and compare
        val currentUser = viewModel.getCurrentUser()
        if (currentUser?.phoneNumber == fullPhoneNumber) {
            showConfirmDialog = true
        } else {
            errorMessage = "Phone number doesn't match your account"
            showError = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delete account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Warning Icon and Message
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "If you delete this account:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            // Warning Points
            Column(
                modifier = Modifier.padding(start = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WarningPoint("The account will be deleted from Temuin and all your devices")
                WarningPoint("Your message history will be erased")
                WarningPoint("You will be removed from all your Temuin groups")
                WarningPoint("Your activity history will be deleted")
                WarningPoint("Your friend list will be deleted")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Phone Number Verification
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "To delete your account, confirm your phone number.",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (showError) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Country code dropdown
                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = it },
                        modifier = Modifier.width(100.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCountry.displayPrefix(),
                            onValueChange = { },
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select country"
                                )
                            },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        ExposedDropdownMenu(
                            modifier = Modifier.width(200.dp),
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            CountryCode.countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text(country.toString()) },
                                    onClick = {
                                        selectedCountry = country
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Phone number field
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { input ->
                            phoneNumber = input.filter { it.isDigit() }
                            showError = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isPhoneFieldFocused = it.isFocused },
                        placeholder = { 
                            Text(
                                text = "Phone number",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Start
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        isError = showError
                    )
                }
            }

            Button(
                onClick = { validateAndDelete() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete account")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = {
                    Text(
                        "Delete Account Permanently?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "This action cannot be undone. All your data will be permanently deleted.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAccount { success ->
                                if (success) {
                                    onNavigateToAuth()
                                } else {
                                    errorMessage = "Failed to delete account. Please try again."
                                    showError = true
                                    showConfirmDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun WarningPoint(text: String) {
    Row {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} 