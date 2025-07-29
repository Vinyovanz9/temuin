package com.temuin.temuin.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.R

@Composable
fun TermsScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    val isFirstTime by viewModel.isFirstTime.collectAsState()
    val agreeAndContinue = "Agree and continue"

    LaunchedEffect(isFirstTime) {
        if (!isFirstTime) {
            onNavigateToAuth()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // Main content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.temuin_logo),
                    contentDescription = "Temuin Logo",
                    modifier = Modifier.size(160.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )

                Text(
                    text = "Welcome to Temuin",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val annotatedString = buildAnnotatedString {
                    append("Read our ")
                    
                    // Privacy Policy link
                    pushStringAnnotation(tag = "privacy_policy", annotation = "")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Privacy Policy")
                    }
                    pop()
                    
                    append(". Tap ")
                    append(agreeAndContinue)
                    append(" to accept the ")
                    
                    // Terms of Service link
                    pushStringAnnotation(tag = "terms_of_service", annotation = "")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Terms of Service")
                    }
                    pop()
                    
                    append(".")
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(
                            tag = "privacy_policy",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let {
                            onNavigateToPrivacyPolicy()
                        }
                        
                        annotatedString.getStringAnnotations(
                            tag = "terms_of_service",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let {
                            onNavigateToTermsOfService()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Button
            Button(
                onClick = {
                    viewModel.setFirstTimeDone()
                    onNavigateToAuth()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    text = agreeAndContinue,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
} 