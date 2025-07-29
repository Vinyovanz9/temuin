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
import androidx.compose.ui.unit.dp
import com.temuin.temuin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            // Security Notifications
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Security notifications",
                onClick = onNavigateToSecurity
            )

            // Terms of Service
            SettingsItem(
                icon = painterResource(R.drawable.baseline_tos_24),
                title = "Terms of Service",
                onClick = onNavigateToTerms
            )

            // Privacy Policy
            SettingsItem(
                icon = painterResource(R.drawable.outline_privacy_policy_24),
                title = "Privacy Policy",
                onClick = onNavigateToPrivacyPolicy
            )

//            Divider(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(vertical = 8.dp)
//            )
//
//            // Delete Account
//            SettingsItem(
//                icon = Icons.Default.Delete,
//                title = "Delete account",
//                onClick = onNavigateToDeleteAccount,
//                tint = MaterialTheme.colorScheme.error
//            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: Any,
    title: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (icon) {
                is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint
                )
                is Painter -> Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = tint
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}