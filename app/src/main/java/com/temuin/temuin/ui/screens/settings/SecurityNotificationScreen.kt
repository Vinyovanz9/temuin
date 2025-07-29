package com.temuin.temuin.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.temuin.temuin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityNotificationScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security notifications") },
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
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Color.Transparent
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(150.dp)
                )
            }

            Text(
                "End-to-end encryption",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Description
            Text(
                "End-to-end encryption keeps your personal data between you and the people you choose. No one outside, not even Temuin, can read, listen to, or share them. This includes your:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Protected Features
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SecurityFeatureCard(
                    icon = painterResource(R.drawable.baseline_chat_24),
                    title = "Text messages",
                    description = "Your messages are secured and can only be read by you and the recipient.",
                    tint = MaterialTheme.colorScheme.primary
                )

                SecurityFeatureCard(
                    icon = painterResource(R.drawable.baseline_groups_24),
                    title = "Friend contact",
                    description = "Your contact information is protected and only shared with your consent.",
                    tint = MaterialTheme.colorScheme.primary
                )

                SecurityFeatureCard(
                    icon = Icons.Default.DateRange,
                    title = "Activity",
                    description = "Your schedule and activity details are encrypted and private.",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SecurityFeatureCard(
    icon: Any,
    title: String,
    description: String,
    tint: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = tint.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {
                when (icon) {
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                    is Painter -> Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 