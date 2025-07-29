package com.temuin.temuin.ui.screens.schedules

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.temuin.temuin.data.model.ParticipantStatus
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.data.model.ScheduleStatus
import com.temuin.temuin.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.ByteArrayInputStream

@Composable
fun ScheduleStatusChip(status: ScheduleStatus) {
    val (color, text) = when (status) {
        ScheduleStatus.PENDING -> MaterialTheme.colorScheme.tertiary to "Pending"
        ScheduleStatus.ACTIVE -> MaterialTheme.colorScheme.primary to "Active"
        ScheduleStatus.ONGOING -> MaterialTheme.colorScheme.tertiary to "Ongoing"
        ScheduleStatus.CANCELLED -> MaterialTheme.colorScheme.error to "Cancelled"
        ScheduleStatus.COMPLETED -> MaterialTheme.colorScheme.secondary to "Completed"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ParticipantStatusChip(status: ParticipantStatus) {
    val (color, text) = when (status) {
        ParticipantStatus.PENDING -> MaterialTheme.colorScheme.tertiary to "Pending"
        ParticipantStatus.ACCEPTED -> MaterialTheme.colorScheme.primary to "Accepted"
        ParticipantStatus.DECLINED -> MaterialTheme.colorScheme.error to "Declined"
//        ParticipantStatus.TENTATIVE -> MaterialTheme.colorScheme.secondary to "Maybe"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    scheduleId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    viewModel: SchedulesViewModel = hiltViewModel(),
    source: String = "upcoming", // "upcoming" or "notifications"
    currentUserId: String? = null,
    onAcceptInvite: (String) -> Unit = {},
    onDeclineInvite: (String) -> Unit = {}
) {
    var schedule by remember { mutableStateOf<Schedule?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isUpdating by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictingSchedules by remember { mutableStateOf<List<Schedule>>(emptyList()) }
    val selectedParticipants by viewModel.selectedParticipants.collectAsState()

    // Function to reload schedule data
    val reloadSchedule = {
        println("Reloading schedule data for: $scheduleId")
        viewModel.getScheduleDetails(scheduleId) { fetchedSchedule ->
            schedule = fetchedSchedule
            println("Schedule reloaded - status: ${fetchedSchedule?.status}")
            // Load creator details first
            fetchedSchedule?.userId?.let { creatorId ->
                viewModel.getParticipantDetails(creatorId)
            }
            // Then load participant details
            fetchedSchedule?.participants?.forEach { userId ->
                viewModel.getParticipantDetails(userId)
            }
        }
    }

    // Function to handle accept with conflict check
    val handleAcceptWithConflictCheck = {
        viewModel.getConflictingSchedules(scheduleId) { conflicts ->
            if (conflicts.isNotEmpty()) {
                conflictingSchedules = conflicts
                showConflictDialog = true
            } else {
                onAcceptInvite(scheduleId)
            }
        }
    }

    // Load schedule details and participant details
    LaunchedEffect(scheduleId, refreshTrigger) {
        reloadSchedule()
    }

    // Refresh data when returning to this screen (e.g., from edit screen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh data when screen becomes visible again
                    reloadSchedule()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                title = { Text("Activity Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (schedule == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Show updating indicator
            if (isUpdating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Updating activity...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 80.dp) // Add bottom padding for buttons
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Schedule Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = schedule!!.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (schedule!!.status == ScheduleStatus.CANCELLED)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        ScheduleStatusChip(schedule!!.status)
                    }

                    // Show status messages (cancelled, edited, etc.)
                    if (schedule!!.status == ScheduleStatus.CANCELLED) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "This activity has been cancelled by the Creator.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    // Check if it was auto-cancelled due to deadline
                                    if (System.currentTimeMillis() > schedule!!.startTime && 
                                        schedule!!.participants.isNotEmpty()) {
                                        Text(
                                            text = "Cancelled automatically due to deadline.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Show edited message if activity was recently modified
                    val timeSinceCreation = schedule!!.updatedAt - schedule!!.createdAt
                    if (schedule!!.status != ScheduleStatus.CANCELLED && timeSinceCreation > 60000) { // More than 1 minute difference
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "This activity has been updated by the Creator.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Last updated: ${
                                            Instant.ofEpochMilli(schedule!!.updatedAt)
                                                .atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                                        }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Description
                    if (schedule!!.description.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = schedule!!.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Date and Time
                    val startDateTime = Instant.ofEpochMilli(schedule!!.startTime)
                        .atZone(ZoneId.systemDefault())
                    val endDateTime = Instant.ofEpochMilli(schedule!!.endTime)
                        .atZone(ZoneId.systemDefault())
                    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Date & Time",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Check if activity spans multiple days
                            val isMultiDay = startDateTime.toLocalDate() != endDateTime.toLocalDate()
                            if (isMultiDay) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                            .align(Alignment.Top)
                                    )

                                    // Multi-day activity display
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Start date and time
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Starts",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${startDateTime.format(shortDateFormatter)} at ${startDateTime.format(timeFormatter)}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        // End date and time
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Ends",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${endDateTime.format(shortDateFormatter)} at ${endDateTime.format(timeFormatter)}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Single day activity display
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = startDateTime.format(dateFormatter),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.time_24),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${startDateTime.format(timeFormatter)} - ${endDateTime.format(timeFormatter)}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            if (schedule!!.location.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = schedule!!.location,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }

                    // Participants
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Participants",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Add creator first
                            schedule?.userId?.let { creatorId ->
                                val creator = selectedParticipants[creatorId]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (creatorId != currentUserId) {
                                                Modifier.clickable { onNavigateToProfile(creatorId) }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (creator == null) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                val profileBitmap = remember(creator.profileImage) {
                                                    creator.profileImage?.let { base64Image ->
                                                        try {
                                                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                                            val inputStream = ByteArrayInputStream(imageBytes)
                                                            BitmapFactory.decodeStream(inputStream)
                                                        } catch (e: Exception) {
                                                            null
                                                        }
                                                    }
                                                }

                                                if (profileBitmap != null) {
                                                    Image(
                                                        bitmap = profileBitmap.asImageBitmap(),
                                                        contentDescription = "Profile picture of ${creator.name}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = "Default profile picture",
                                                        modifier = Modifier.padding(8.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (creatorId == currentUserId) "You (Creator)"
                                                  else creator?.name ?: "Loading...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "Creator",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // Then show other participants
                            schedule?.participants?.forEach { userId ->
                                val participant = selectedParticipants[userId]
                                val status = schedule?.participantStatus?.get(userId) ?: ParticipantStatus.PENDING

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (userId != currentUserId) {
                                                Modifier.clickable { onNavigateToProfile(userId) }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (participant == null) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                val profileBitmap = remember(participant.profileImage) {
                                                    participant.profileImage?.let { base64Image ->
                                                        try {
                                                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                                            val inputStream = ByteArrayInputStream(imageBytes)
                                                            BitmapFactory.decodeStream(inputStream)
                                                        } catch (e: Exception) {
                                                            null
                                                        }
                                                    }
                                                }

                                                if (profileBitmap != null) {
                                                    Image(
                                                        bitmap = profileBitmap.asImageBitmap(),
                                                        contentDescription = "Profile picture of ${participant.name}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = "Default profile picture",
                                                        modifier = Modifier.padding(8.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (userId == currentUserId) "You"
                                                  else participant?.name ?: "Loading...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    ParticipantStatusChip(status)
                                }
                            }
                        }
                    }

                    // Allow Reschedule Info
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (schedule!!.allowReschedule) Icons.Default.Check else Icons.Default.Clear,
                                contentDescription = null,
                                tint = if (schedule!!.allowReschedule)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                            Column {
                                Text(
                                    text = if (schedule!!.allowReschedule) "Rescheduling Allowed" else "Rescheduling Not Allowed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (schedule!!.allowReschedule)
                                        "Participants can request to reschedule this activity"
                                    else
                                        "This activity cannot be rescheduled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Reminder Info
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Reminder",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (schedule!!.reminderHours) {
                                        1 -> "1 hour before the activity starts"
                                        24 -> "1 day before the activity starts"
                                        else -> "${schedule!!.reminderHours} hours before the activity starts"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        when (source) {
                            "upcoming" -> {
                                if (schedule!!.userId == currentUserId && 
                                    schedule!!.status != ScheduleStatus.COMPLETED && 
                                    schedule!!.status != ScheduleStatus.CANCELLED) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (schedule!!.allowReschedule) {
                                            Button(
                                                onClick = { onNavigateToEdit(scheduleId) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Edit Activity")
                                            }
                                        }
                                        Button(
                                            onClick = { showDeleteDialog = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Cancel Activity")
                                        }
                                    }
                                }
                            }
                            "notifications" -> {
                                // Only show Accept/Decline buttons if the activity is not cancelled
                                if (schedule?.status != ScheduleStatus.CANCELLED) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onDeclineInvite(scheduleId) },
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
                                                android.util.Log.d("ConflictDialog", "Checking for conflicts before accepting schedule: $scheduleId")
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
                                                        onAcceptInvite(scheduleId)
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
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Cancel Activity?") },
                text = { Text("Are you sure you want to cancel this activity?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            schedule?.let { currentSchedule ->
                                isUpdating = true
                                // Update schedule status to CANCELLED instead of deleting
                                val updatedSchedule = currentSchedule.copy(
                                    status = ScheduleStatus.CANCELLED,
                                    updatedAt = System.currentTimeMillis()
                                )
                                
                                // Immediately update local state to reflect cancellation
                                schedule = updatedSchedule
                                
                                // Trigger immediate UI refresh
                                refreshTrigger++
                                
                                viewModel.updateScheduleAndNotify(updatedSchedule)
                                
                                // Use a coroutine to delay the refresh slightly to ensure Firebase update completes
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(1500) // Wait 1.5 seconds for Firebase update
                                    refreshTrigger++
                                    viewModel.forceRefreshSchedules() // Force refresh the main schedules list
                                    isUpdating = false
                                }
                            }
                            showDeleteDialog = false
                            // Don't navigate back immediately, let user see the cancelled status
                            // onNavigateBack()
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("No")
                    }
                }
            )
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
                            onAcceptInvite(scheduleId)
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
} 