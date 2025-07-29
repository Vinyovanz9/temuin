package com.temuin.temuin.ui.screens.notifications

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.data.model.Notification
import com.temuin.temuin.data.model.NotificationStatus
import com.temuin.temuin.data.model.NotificationType
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.R
import com.temuin.temuin.ui.screens.schedules.SchedulesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onScheduleClick: (String) -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()

    // Debug logging for notification changes
    LaunchedEffect(notifications) {
        println("🔔 NotificationsScreen: Received ${notifications.size} notifications")
        notifications.filter { it.status == NotificationStatus.PENDING }.forEach { notification ->
            println("🔔 Pending notification: ${notification.type.name} - ${notification.title}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = TopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val pendingNotifications = notifications.filter { it.status == NotificationStatus.PENDING }
        if (pendingNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "No notifications",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = pendingNotifications,
                    key = { it.id }
                ) { notification ->
                    var show by remember { mutableStateOf(true) }
                    // Only enable swipe-to-dismiss for cancelled notifications
                    if (notification.type == NotificationType.SCHEDULE_CANCELLED) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    show = false
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = show,
                            exit = shrinkVertically(
                                animationSpec = tween(durationMillis = 300)
                            ) + fadeOut()
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    // Only show delete icon when being swiped
                                    if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                                        DismissBackground(dismissState.targetValue)
                                    }
                                }
                            ) {
                                NotificationItem(
                                    notification = notification,
                                    onAccept = { viewModel.handleScheduleInvite(notification, true) },
                                    onDecline = { viewModel.handleScheduleInvite(notification, false) },
                                    onClick = { notification.scheduleId?.let { onScheduleClick(it) } }
                                )
                            }
                        }

                        LaunchedEffect(show) {
                            if (!show) {
                                viewModel.markNotificationAsRead(notification.id)
                            }
                        }
                    } else {
                        // For non-cancelled notifications, just show the notification item without swipe
                        NotificationItem(
                            notification = notification,
                            onAccept = { viewModel.handleScheduleInvite(notification, true) },
                            onDecline = { viewModel.handleScheduleInvite(notification, false) },
                            onClick = { notification.scheduleId?.let { onScheduleClick(it) } }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(dismissValue: SwipeToDismissBoxValue) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = when (dismissValue) {
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationItem(
    notification: Notification,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel()
) {
    var schedule by remember { mutableStateOf<Schedule?>(null) }
    var previousSchedule by remember { mutableStateOf<Schedule?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictingSchedules by remember { mutableStateOf<List<Schedule>>(emptyList()) }

    // Load schedule details when notification is displayed
    LaunchedEffect(notification.scheduleId) {
        notification.scheduleId?.let { id ->
            viewModel.getScheduleDetails(id) { fetchedSchedule ->
                schedule = fetchedSchedule
                if (notification.type == NotificationType.SCHEDULE_UPDATE) {
                    viewModel.getPreviousScheduleVersion(id) { prevSchedule ->
                        previousSchedule = prevSchedule
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (notification.type) {
                NotificationType.SCHEDULE_CANCELLED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with type and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (notification.type) {
                            NotificationType.SCHEDULE_UPDATE -> Icons.Default.Edit
                            NotificationType.SCHEDULE_INVITE -> Icons.Default.DateRange
                            NotificationType.SCHEDULE_CANCELLED -> Icons.Default.Close
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = when (notification.type) {
                            NotificationType.SCHEDULE_CANCELLED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        when (notification.type) {
                            NotificationType.SCHEDULE_UPDATE -> "An activity has been updated"
                            NotificationType.SCHEDULE_INVITE -> "You've been invited to"
                            NotificationType.SCHEDULE_CANCELLED -> "An activity has been cancelled"
                            NotificationType.SCHEDULE_REMINDER -> "Activity reminder"
                            else -> "Notification"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    Instant.ofEpochMilli(notification.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MMM dd, HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Schedule details
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    notification.title.replace("Schedule Updated: ", ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                schedule?.let { currentSchedule ->
                    val startDateTime = Instant.ofEpochMilli(currentSchedule.startTime)
                        .atZone(ZoneId.systemDefault())
                    val endDateTime = Instant.ofEpochMilli(currentSchedule.endTime)
                        .atZone(ZoneId.systemDefault())
                    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    val shortDateFormatter = DateTimeFormatter.ofPattern("MMM dd")
                    val isMultiDay = startDateTime.toLocalDate() != endDateTime.toLocalDate()

                    if (notification.type == NotificationType.SCHEDULE_UPDATE && previousSchedule != null) {
                        Text(
                            "The schedule has been modified. Please review the changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        val prevStartDateTime = Instant.ofEpochMilli(previousSchedule!!.startTime)
                            .atZone(ZoneId.systemDefault())
                        val prevEndDateTime = Instant.ofEpochMilli(previousSchedule!!.endTime)
                            .atZone(ZoneId.systemDefault())
                        val prevIsMultiDay = prevStartDateTime.toLocalDate() != prevEndDateTime.toLocalDate()

                        Text(
                            "From: ${if (prevIsMultiDay) 
                                "${prevStartDateTime.format(shortDateFormatter)} ${prevStartDateTime.format(timeFormatter)} - ${prevEndDateTime.format(shortDateFormatter)} ${prevEndDateTime.format(timeFormatter)}"
                                else "${prevStartDateTime.format(dateFormatter)} ${prevStartDateTime.format(timeFormatter)} - ${prevEndDateTime.format(timeFormatter)}"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "To: ${if (isMultiDay) 
                                "${startDateTime.format(shortDateFormatter)} ${startDateTime.format(timeFormatter)} - ${endDateTime.format(shortDateFormatter)} ${endDateTime.format(timeFormatter)}"
                                else "${startDateTime.format(dateFormatter)} ${startDateTime.format(timeFormatter)} - ${endDateTime.format(timeFormatter)}"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (isMultiDay) 
                                    "${startDateTime.format(shortDateFormatter)} ${startDateTime.format(timeFormatter)} - ${endDateTime.format(shortDateFormatter)} ${endDateTime.format(timeFormatter)}"
                                else "${startDateTime.format(dateFormatter)} ${startDateTime.format(timeFormatter)} - ${endDateTime.format(timeFormatter)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (currentSchedule.location.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                currentSchedule.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentSchedule.participants.size > 1) {
                            Icon(
                                painterResource(R.drawable.baseline_groups_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${currentSchedule.participants.size} Participant${if(currentSchedule.participants.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // See Details Button
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            ) {
                Text("See details")
            }

            // Only show Accept/Decline buttons for invite and updated notifications
            if (notification.type != NotificationType.SCHEDULE_CANCELLED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = { 
                            // First check for conflicts
                            android.util.Log.d("ConflictDialog", "Checking for conflicts before accepting schedule: ${notification.scheduleId}")
                            notification.scheduleId?.let { scheduleId ->
                                viewModel.getConflictingSchedules(scheduleId) { conflicts ->
                                    android.util.Log.d("ConflictDialog", "Received ${conflicts.size} conflicts")
                                    conflicts.forEach { conflict ->
                                        android.util.Log.d("ConflictDialog", """
                                            Conflict found:
                                            Title: ${conflict.title}
                                            Time: ${conflict.startTime} - ${conflict.endTime}
                                            Status: ${conflict.status}
                                        """.trimIndent())
                                    }
                                    
                                    if (conflicts.isNotEmpty()) {
                                        android.util.Log.d("ConflictDialog", "Showing conflict dialog")
                                        conflictingSchedules = conflicts
                                        showConflictDialog = true
                                    } else {
                                        android.util.Log.d("ConflictDialog", "No conflicts found, accepting directly")
                                        // No conflicts, accept directly
                                        onAccept()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept")
                    }
                }
            }
        }
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { 
                Text(
                    "Activity Conflict",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "This activity conflicts with your existing activities:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            conflictingSchedules.forEach { conflictSchedule ->
                                val startDateTime = Instant.ofEpochMilli(conflictSchedule.startTime)
                                    .atZone(ZoneId.systemDefault())
                                val endDateTime = Instant.ofEpochMilli(conflictSchedule.endTime)
                                    .atZone(ZoneId.systemDefault())
                                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                                val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM dd")

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = conflictSchedule.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (conflictSchedule.location.isNotBlank()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = conflictSchedule.location,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (startDateTime.toLocalDate() == endDateTime.toLocalDate()) {
                                                "${startDateTime.format(dateFormatter)} ${startDateTime.format(timeFormatter)} - ${endDateTime.format(timeFormatter)}"
                                            } else {
                                                "${startDateTime.format(dateFormatter)} ${startDateTime.format(timeFormatter)} - ${endDateTime.format(dateFormatter)} ${endDateTime.format(timeFormatter)}"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                if (conflictSchedule != conflictingSchedules.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Are you sure you want to accept this activity? It will overlap with your existing schedule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConflictDialog = false
                        onAccept()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Accept Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConflictDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
} 