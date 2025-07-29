package com.temuin.temuin.ui.screens.auth

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.data.model.CountryCode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAuthScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToMain: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val activity = LocalContext.current as Activity

    var selectedCountry by remember { mutableStateOf(CountryCode.INDONESIA) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isPhoneFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    // Retry cooldown state
    var isRetryOnCooldown by remember { mutableStateOf(false) }
    var cooldownSeconds by remember { mutableStateOf(0) }

    // Initialize reCAPTCHA when the screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.initializeRecaptcha(activity)
    }
    
    // Cooldown timer
    LaunchedEffect(isRetryOnCooldown) {
        if (isRetryOnCooldown) {
            cooldownSeconds = 60
            while (cooldownSeconds > 0) {
                delay(1000)
                cooldownSeconds--
            }
            isRetryOnCooldown = false
        }
    }

    // Function to convert error messages to human readable format
    fun getHumanReadableError(error: String): String {
        return when {
            error.contains("invalid-verification-code", ignoreCase = true) -> 
                "The verification code is invalid. Please check and enter the correct verification code again."
            error.contains("invalid-phone-number", ignoreCase = true) -> 
                "The phone number you entered is invalid. Please check the number and try again."
            error.contains("too-many-requests", ignoreCase = true) -> 
                "Too many attempts. Please wait a moment before trying again."
            error.contains("network", ignoreCase = true) -> 
                "Network connection error. Please check your internet connection and try again."
            error.contains("recaptcha", ignoreCase = true) -> 
                "reCAPTCHA verification failed. Please try sending the code again."
            error.contains("quota-exceeded", ignoreCase = true) -> 
                "SMS quota exceeded. Please try again later."
            error.contains("captcha-check-failed", ignoreCase = true) -> 
                "Security verification failed. Please try again."
            error.contains("credential-already-in-use", ignoreCase = true) -> 
                "This phone number is already registered with another account."
            error.contains("operation-not-allowed", ignoreCase = true) -> 
                "Phone authentication is currently disabled. Please contact support."
            error.contains("web-context-already-presented", ignoreCase = true) -> 
                "Authentication is already in progress. Please wait or restart the app."
            error.contains("web-context-cancelled", ignoreCase = true) -> 
                "Authentication was cancelled. Please try again."
            else -> "Something went wrong. Please try again or contact support if the problem persists."
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (authState) {
                is AuthState.Ideal -> {
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
                                text = "Phone Verification",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = "Temuin will need to verify your phone number.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Country code dropdown
                                ExposedDropdownMenuBox(
                                    expanded = isDropdownExpanded,
                                    onExpandedChange = { isDropdownExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = "${selectedCountry.code} ${selectedCountry.displayPrefix()}",
                                        onValueChange = { },
                                        readOnly = true,
                                        label = { Text("Country") },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Select country"
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        )
                                    )

                                    ExposedDropdownMenu(
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
                                    },
                                    label = { Text("Phone number") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isPhoneFieldFocused = it.isFocused },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            if (phoneNumber.isNotBlank()) {
                                                val processedNumber = if (phoneNumber.startsWith("0")) {
                                                    phoneNumber.substring(1)
                                                } else {
                                                    phoneNumber
                                                }
                                                viewModel.sendVerificationCode("${selectedCountry.prefix}$processedNumber", activity)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    singleLine = true
                                )
                            }

                            Text(
                                text = "Carrier charges may apply",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (phoneNumber.isNotBlank()) {
                                        val processedNumber = if (phoneNumber.startsWith("0")) {
                                            phoneNumber.substring(1)
                                        } else {
                                            phoneNumber
                                        }
                                        viewModel.sendVerificationCode("${selectedCountry.prefix}$processedNumber", activity)
                                        focusManager.clearFocus()
                                    } else {
                                        Toast.makeText(context, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = phoneNumber.isNotBlank(),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                Text("Send Verification Code")
                            }
                        }
                    }
                }

                is AuthState.CodeSent -> {
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
                                text = "Enter Verification Code",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val displayNumber = if (phoneNumber.startsWith("0")) {
                                phoneNumber.substring(1)
                            } else {
                                phoneNumber
                            }
                            
                            Text(
                                text = "We have sent you an SMS with the code to ${selectedCountry.prefix}$displayNumber",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = otp,
                                onValueChange = { 
                                    if (it.length <= 6) {
                                        otp = it.filter { char -> char.isDigit() }
                                    }
                                },
                                label = { Text("Verification code") },
                                supportingText = { Text("Enter the 6-digit code from SMS") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (otp.length == 6) {
                                            viewModel.verifyCode(otp, context)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textAlign = TextAlign.Center
                                )
                            )

                            Button(
                                onClick = {
                                    if (otp.length == 6) {
                                        viewModel.verifyCode(otp, context)
                                        focusManager.clearFocus()
                                    } else {
                                        Toast.makeText(context, "Please enter a 6-digit code", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = otp.length == 6,
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                Text("Verify")
                            }
                            
                            // Retry button
                            OutlinedButton(
                                onClick = {
                                    val processedNumber = if (phoneNumber.startsWith("0")) {
                                        phoneNumber.substring(1)
                                    } else {
                                        phoneNumber
                                    }
                                    viewModel.sendVerificationCode("${selectedCountry.prefix}$processedNumber", activity)
                                    isRetryOnCooldown = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRetryOnCooldown,
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    if (isRetryOnCooldown) 
                                        "Resend code in ${cooldownSeconds}s" 
                                    else 
                                        "Resend code"
                                )
                            }
                        }
                    }
                }

                is AuthState.Loading -> {
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
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Please wait...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is AuthState.Success -> {
                    LaunchedEffect(Unit) {
                        val user = (authState as AuthState.Success).user
                        if (user.name.isNullOrBlank()) {
                            onNavigateToProfile()
                        } else {
                            onNavigateToMain()
                        }
                        viewModel.resetAuthState()
                    }
                }

                is AuthState.Error -> {
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
                                text = "Verification Failed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            
                            // Error message card
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = getHumanReadableError((authState as AuthState.Error).message),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Retry button
                                Button(
                                    onClick = {
                                        if (otp.isNotEmpty()) {
                                            // If we have OTP, retry verification
                                            viewModel.verifyCode(otp, context)
                                        } else {
                                            // If no OTP, resend code
                                            val processedNumber = if (phoneNumber.startsWith("0")) {
                                                phoneNumber.substring(1)
                                            } else {
                                                phoneNumber
                                            }
                                            viewModel.sendVerificationCode("${selectedCountry.prefix}$processedNumber", activity)
                                            isRetryOnCooldown = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRetryOnCooldown,
                                    contentPadding = PaddingValues(vertical = 16.dp)
                                ) {
                                    Text(
                                        if (isRetryOnCooldown) 
                                            "Retry in ${cooldownSeconds}s"
                                        else if (otp.isNotEmpty())
                                            "Retry Verification"
                                        else
                                            "Resend Code"
                                    )
                                }
                                
                                // Back button
                                OutlinedButton(
                                    onClick = { 
                                        viewModel.resetAuthState()
                                        otp = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text("Back to Phone Number")
                                }
                            }
                        }
                    }
                }

                is AuthState.AUTHENTICATED -> {
                    LaunchedEffect(Unit) {
                        onNavigateToMain()
                        viewModel.resetAuthState()
                    }
                }
            }
        }
    }
} 