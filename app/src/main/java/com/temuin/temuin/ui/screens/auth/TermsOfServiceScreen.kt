package com.temuin.temuin.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last updated: ${java.time.LocalDate.now()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TermsSection(
                title = "Acceptance of Terms",
                content = "By accessing or using Temuin, you agree to be bound by these Terms of Service. If you disagree with any part of the terms, you may not access the service."
            )

            TermsSection(
                title = "User Account",
                content = "• You are responsible for maintaining the confidentiality of your account\n" +
                        "• You must provide accurate and complete information\n" +
                        "• You are responsible for all activities under your account\n" +
                        "• You must notify us of any security breaches"
            )

            TermsSection(
                title = "Acceptable Use",
                content = "You agree not to:\n" +
                        "• Use the service for any illegal purposes\n" +
                        "• Share inappropriate or harmful content\n" +
                        "• Impersonate others\n" +
                        "• Interfere with the service's operation\n" +
                        "• Attempt to gain unauthorized access"
            )

            TermsSection(
                title = "Content Guidelines",
                content = "• You retain rights to content you share\n" +
                        "• You grant us license to use your content\n" +
                        "• You must not violate others' intellectual property\n" +
                        "• We may remove content that violates these terms"
            )

            TermsSection(
                title = "Service Modifications",
                content = "We reserve the right to:\n" +
                        "• Modify or discontinue the service\n" +
                        "• Change fees for the service\n" +
                        "• Limit feature availability\n" +
                        "• Update these terms"
            )

            TermsSection(
                title = "Termination",
                content = "We may terminate or suspend your account if you violate these terms. Upon termination, your right to use the service will immediately cease."
            )

            TermsSection(
                title = "Limitation of Liability",
                content = "We are not liable for any indirect, incidental, special, consequential, or punitive damages resulting from your use of the service."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TermsSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} 