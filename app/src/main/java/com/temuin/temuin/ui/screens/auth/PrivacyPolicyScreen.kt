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
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
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

            PolicySection(
                title = "Information We Collect",
                content = "• Phone number for authentication\n" +
                        "• Profile information (name, status, profile picture)\n" +
                        "• Schedule and activity data\n" +
                        "• Chat messages and communication data\n" +
                        "• Device information and usage statistics"
            )

            PolicySection(
                title = "How We Use Your Information",
                content = "• To provide and maintain our service\n" +
                        "• To notify you about changes to our service\n" +
                        "• To provide customer support\n" +
                        "• To detect, prevent and address technical issues\n" +
                        "• To improve our service"
            )

            PolicySection(
                title = "Data Storage and Security",
                content = "• We use Firebase for secure data storage\n" +
                        "• Your data is encrypted in transit and at rest\n" +
                        "• We regularly backup your data\n" +
                        "• We implement industry-standard security measures"
            )

            PolicySection(
                title = "Data Sharing",
                content = "We do not share your personal information with third parties except:\n" +
                        "• When required by law\n" +
                        "• To protect our rights\n" +
                        "• With your explicit consent"
            )

            PolicySection(
                title = "Your Rights",
                content = "You have the right to:\n" +
                        "• Access your personal data\n" +
                        "• Correct inaccurate data\n" +
                        "• Request deletion of your data\n" +
                        "• Opt-out of communications"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicySection(
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