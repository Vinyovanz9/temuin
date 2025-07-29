package com.temuin.temuin.ui.screens.schedules

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.R
import com.temuin.temuin.data.model.ParticipantStatus
import com.temuin.temuin.data.model.ReminderTime
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.data.model.ScheduleStatus
import com.temuin.temuin.data.model.User
import java.io.ByteArrayInputStream
import java.time.*
import java.time.format.DateTimeFormatter

// Add this data class at the top level
private data class ScheduleData(
    val title: String,
    val description: String,
    val location: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val reminderHours: Int,
    val allowReschedule: Boolean,
    val participants: List<String>
)

// Add this function before any composables
private fun getMinimumTime(): LocalTime {
    val currentTime = LocalTime.now()
    val totalMinutes = currentTime.hour * 60 + currentTime.minute
    val roundedMinutes = ((totalMinutes + 4) / 5) * 5 // Round to nearest 5 minutes
    return if (roundedMinutes >= 1440) {
        LocalTime.of(0, 0)
    } else {
        LocalTime.of(roundedMinutes / 60, roundedMinutes % 60)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    scheduleId: String?,  // Make scheduleId nullable - null means creating new schedule
    onNavigateBack: () -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel()
) {
    var schedule by remember { mutableStateOf<Schedule?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    
    // Calculate initial times
    val initialStartTime = remember {
        val currentTime = LocalTime.now()
        val totalMinutes = currentTime.hour * 60 + currentTime.minute
        val roundedMinutes = ((totalMinutes + 4) / 5) * 5 // Round to nearest 5 minutes
        if (roundedMinutes >= 1440) { // 24 hours = 1440 minutes
            LocalTime.of(0, 0)
        } else {
            LocalTime.of(roundedMinutes / 60, roundedMinutes % 60)
        }
    }
    
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(initialStartTime) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var endTime by remember { mutableStateOf(initialStartTime.plusMinutes(30)) }
    var reminderHours by remember { mutableStateOf(1) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf<List<String>>(emptyList()) }
    var allowReschedule by remember { mutableStateOf(true) }
    
    // Dialog States
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showReminderOptions by remember { mutableStateOf(false) }
    var showAddParticipant by remember { mutableStateOf(false) }
    
    // Add conflict checking states
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictingSchedules by remember { mutableStateOf<List<Schedule>>(emptyList()) }
    var pendingScheduleData by remember { mutableStateOf<ScheduleData?>(null) }

    // Selected participants state from ViewModel
    val selectedParticipants by viewModel.selectedParticipants.collectAsState()

    // Load schedule details if editing
    LaunchedEffect(scheduleId) {
        if (scheduleId != null) {
        viewModel.getScheduleDetails(scheduleId) { fetchedSchedule ->
            schedule = fetchedSchedule
            if (fetchedSchedule != null) {
                title = fetchedSchedule.title
                description = fetchedSchedule.description
                location = fetchedSchedule.location
                val startDateTime = Instant.ofEpochMilli(fetchedSchedule.startTime)
                    .atZone(ZoneId.systemDefault())
                startDate = startDateTime.toLocalDate()
                startTime = startDateTime.toLocalTime()
                val endDateTime = Instant.ofEpochMilli(fetchedSchedule.endTime)
                    .atZone(ZoneId.systemDefault())
                endDate = endDateTime.toLocalDate()
                endTime = endDateTime.toLocalTime()
                reminderHours = fetchedSchedule.reminderHours
                participants = fetchedSchedule.participants
                    allowReschedule = fetchedSchedule.allowReschedule
                
                // Load participant details
                participants.forEach { userId ->
                    if (selectedParticipants[userId] == null) {
                        viewModel.getParticipantDetails(userId)
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            onDateSelected = { selectedDate ->
                startDate = selectedDate
                if (selectedDate.isAfter(endDate)) {
                    endDate = selectedDate
                }
                showStartDatePicker = false
            },
            initialDate = startDate
        )
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            onDateSelected = { selectedDate ->
                if (!selectedDate.isBefore(startDate)) {
                    endDate = selectedDate
                }
                showEndDatePicker = false
            },
            initialDate = endDate
        )
    }

    // Time Picker Dialogs
    if (showStartTimePicker) {
        CustomTimePicker(
            selectedTime = startTime,
            onTimeSelected = { newTime ->
                startTime = newTime
                if (startDate == endDate && startTime.isAfter(endTime)) {
                    endTime = startTime.plusHours(1)
                }
                showStartTimePicker = false
            },
            selectedDate = startDate,
            isStartTime = true
        )
    }

    if (showEndTimePicker) {
        CustomTimePicker(
            selectedTime = endTime,
            onTimeSelected = { newTime ->
                val newEndDateTime = LocalDateTime.of(endDate, newTime)
                val startDateTime = LocalDateTime.of(startDate, startTime)
                if (!newEndDateTime.isBefore(startDateTime.plusMinutes(30))) {
                    endTime = newTime
                }
                showEndTimePicker = false
            },
            selectedDate = endDate,
            isStartTime = false
        )
    }

    // Reminder Options Dialog
    if (showReminderOptions) {
        AlertDialog(
            onDismissRequest = { showReminderOptions = false },
            title = { Text("Reminder Before") },
            text = {
                Column {
                    ReminderTime.values().forEach { reminder ->
                        TextButton(
                            onClick = {
                                reminderHours = reminder.hours
                                showReminderOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(reminder.displayText)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReminderOptions = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Participant Dialog
    if (showAddParticipant) {
        EditScheduleAddParticipantDialog(
            onDismiss = { showAddParticipant = false },
            onParticipantsSelected = { selectedUsers ->
                selectedUsers.forEach { user ->
                    if (!participants.contains(user.userId)) {
                        viewModel.getParticipantDetails(user.userId)
                    }
                }
                participants = (participants + selectedUsers.map { it.userId }).distinct()
            },
            viewModel = viewModel,
            existingParticipants = participants
        )
    }

    // Show conflict dialog if needed
    if (showConflictDialog && pendingScheduleData != null) {
        AlertDialog(
            onDismissRequest = { 
                showConflictDialog = false
                pendingScheduleData = null
            },
            title = { 
                Text(
                    "Activity Conflict",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
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
                        if (scheduleId == null) 
                            "Are you sure you want to create this activity? It will overlap with your existing activity."
                        else
                            "Are you sure you want to save these changes? The activity will overlap with your existing activity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConflictDialog = false
                        pendingScheduleData?.let { data ->
                            if (scheduleId == null) {
                                // Creating new schedule
                                viewModel.createSchedule(
                                    data.title,
                                    data.description,
                                    data.location,
                                    data.startDateTime,
                                    data.endDateTime,
                                    data.reminderHours,
                                    "",  // category
                                    data.allowReschedule,
                                    data.participants,
                                    null,  // latitude
                                    null   // longitude
                                )
                            } else {
                                // Updating existing schedule
                                schedule?.let { currentSchedule ->
                                    val updatedParticipantStatus = data.participants.associateWith { ParticipantStatus.PENDING }
                                    
                                    val updatedSchedule = currentSchedule.copy(
                                        title = data.title,
                                        description = data.description,
                                        location = data.location,
                                        startTime = data.startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                        endTime = data.endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                        reminderHours = data.reminderHours,
                                        participants = data.participants,
                                        participantStatus = updatedParticipantStatus,
                                        status = ScheduleStatus.PENDING,
                                        allowReschedule = data.allowReschedule,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    viewModel.updateScheduleAndNotify(updatedSchedule)
                                }
                            }
                        }
                        pendingScheduleData = null
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (scheduleId == null) "Create Anyway" else "Save Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showConflictDialog = false
                        pendingScheduleData = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
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
                title = { Text(if (scheduleId == null) "New Activity" else "Edit Activity") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onNavigateBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotBlank()
                    ) {
                        Text(if (scheduleId == null) "Create" else "Save Changes" )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (schedule == null && scheduleId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    supportingText = if (title.isBlank()) {
                        { Text("Title is required") }
                    } else null,
                    isError = title.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    minLines = 1,
                    maxLines = 3,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // Date and Time Selection
                Text(
                    text = "Schedule Time",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Start Date & Time
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Start date",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(0.9f)) {
                                    CustomDatePicker(
                                        selectedDate = startDate,
                                        onDateSelected = { 
                                            startDate = it
                                            if (it.isEqual(LocalDate.now())) {
                                                val currentTime = LocalTime.now()
                                                val totalMinutes = currentTime.hour * 60 + currentTime.minute
                                                val roundedTotalMinutes = ((totalMinutes + 14) / 15) * 15 + 5 // Add 5 minutes
                                                
                                                // Handle day overflow safely
                                                if (roundedTotalMinutes >= 1440) { // 24 hours = 1440 minutes
                                                    startTime = LocalTime.of(0, 0) // Start of next day
                                                } else {
                                                    startTime = LocalTime.of(
                                                        roundedTotalMinutes / 60,
                                                        roundedTotalMinutes % 60
                                                    )
                                                }
                                            }
                                            if (endDate.isBefore(it)) {
                                                endDate = it
                                                endTime = startTime.plusHours(1)
                                            }
                                        },
                                        minDate = LocalDate.now()
                                    )
                                }

                                Box(modifier = Modifier.weight(0.6f)) {
                                    CustomTimePicker(
                                        selectedTime = startTime,
                                        onTimeSelected = { newTime -> 
                                            startTime = newTime
                                            // If end time is less than 30 minutes after new start time, adjust it
                                            val startDateTime = LocalDateTime.of(startDate, newTime)
                                            val endDateTime = LocalDateTime.of(endDate, endTime)
                                            if (endDateTime.isBefore(startDateTime.plusMinutes(30))) {
                                                endTime = newTime.plusMinutes(30)
                                            }
                                        },
                                        selectedDate = startDate,
                                        isStartTime = true
                                    )
                                }
                            }
                        }

                        // End Date & Time
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "End date",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(0.9f)) {
                                    CustomDatePicker(
                                        selectedDate = endDate,
                                        onDateSelected = { 
                                            endDate = it
                                            if (it.isEqual(startDate)) {
                                                endTime = startTime.plusHours(1)
                                            }
                                        },
                                        minDate = startDate
                                    )
                                }

                                Box(modifier = Modifier.weight(0.6f)) {
                                    CustomTimePicker(
                                        selectedTime = endTime,
                                        onTimeSelected = { newTime -> 
                                            endTime = newTime
                                        },
                                        selectedDate = endDate,
                                        isStartTime = false,
                                        startTime = if (endDate.isEqual(startDate)) startTime else null
                                    )
                                }
                            }
                        }
                    }
                }

                // Participants Section
                Text(
                    text = "Participants",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        participants.forEach { userId ->
                                val user = selectedParticipants[userId]
//                            val status = schedule?.participantStatus?.get(userId) ?: ParticipantStatus.PENDING

                                Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (user == null) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                val profileBitmap = remember(user.profileImage) {
                                                    user.profileImage?.let { base64Image ->
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
                                                        contentDescription = "Profile picture of ${user.name}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column {
                                        if (user == null) {
                                        Text(
                                                text = "Loading...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = user.name.takeIf { it.isNotEmpty() }
                                                    ?: user.phoneNumber.takeIf { it.isNotEmpty() }
                                                    ?: "Unknown User",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (!user.phoneNumber.isNullOrEmpty()) {
                                                Text(
                                                    text = user.phoneNumber,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                    }
                                        }
                                    }
                                }

                                    IconButton(
                                        onClick = {
                                            participants = participants - userId
                                            viewModel.removeParticipant(userId)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove participant",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                        TextButton(
                            onClick = { showAddParticipant = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp)
                        ){
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Add Participant",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                // Reminder Selection
                Text(
                    text = "Other",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Allow Reschedule Option
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (allowReschedule) Icons.Default.Check else Icons.Default.Clear,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Allow Reschedule",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Switch(
                            checked = allowReschedule,
                            onCheckedChange = { newValue ->
                                allowReschedule = newValue
                            }
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Remind me before",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showReminderOptions = true }
                        ) {
                            Text(ReminderTime.values().find { it.hours == reminderHours }?.displayText ?: "${reminderHours} hours")
                        }
                    }
                }
            }
        }
    }

    fun validateDateTime(): Boolean {
        val startDateTime = LocalDateTime.of(startDate, startTime)
        val endDateTime = LocalDateTime.of(endDate, endTime)
        val now = LocalDateTime.now()
        val minimumTime = LocalDateTime.of(LocalDate.now(), getMinimumTime())

        return when {
            // For editing existing schedules, check if start time is in the past
            scheduleId != null && startDateTime.isBefore(now) -> {
                errorMessage = "Cannot schedule activity in the past"
                false
            }
            // For new schedules and today's date, check minimum time
            scheduleId == null && startDate.isEqual(LocalDate.now()) && startDateTime.isBefore(minimumTime) -> {
                errorMessage = "Start time must be at least ${getMinimumTime().format(DateTimeFormatter.ofPattern("HH:mm"))} for today"
                false
            }
            // For editing schedules on today's date, check minimum time
            scheduleId != null && startDate.isEqual(LocalDate.now()) && startDateTime.isBefore(minimumTime) -> {
                errorMessage = "Start time must be at least ${getMinimumTime().format(DateTimeFormatter.ofPattern("HH:mm"))} for today"
                false
            }
            endDateTime.isBefore(startDateTime) -> {
                errorMessage = "End time must be later than start time"
                false
            }
            startDateTime.isEqual(endDateTime) -> {
                errorMessage = "Start and end time cannot be the same"
                false
            }
            endDateTime.isBefore(startDateTime.plusMinutes(30)) -> {
                errorMessage = "Schedule duration must be at least 30 minutes"
                false
            }
            else -> true
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSaveDialog = false
                showError = false
            },
            title = { Text(if (scheduleId == null) "Create Activity" else "Save Changes") },
            text = { 
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showError) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Text(if (scheduleId == null) "Are you sure you want to create this activity?" else "Are you sure you want to save these changes?")
                    }
                }
            },
            confirmButton = {
                if (!showError) {
                    TextButton(
                        onClick = {
                            if (validateDateTime()) {
                                val startDateTime = LocalDateTime.of(startDate, startTime)
                                val endDateTime = LocalDateTime.of(endDate, endTime)
                                
                                // Check for conflicts
                                viewModel.checkTimeConflicts(
                                    startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    scheduleId  // Pass the current scheduleId to exclude it from conflict check
                                ) { conflicts ->
                                    if (conflicts.isNotEmpty()) {
                                        conflictingSchedules = conflicts
                                        pendingScheduleData = ScheduleData(
                                            title = title,
                                            description = description,
                                            location = location,
                                            startDateTime = startDateTime,
                                            endDateTime = endDateTime,
                                            reminderHours = reminderHours,
                                            allowReschedule = allowReschedule,
                                            participants = participants
                                        )
                                        showConflictDialog = true
                                    } else {
                                        if (scheduleId == null) {
                                            // Creating new schedule
                                            viewModel.createSchedule(
                                                title,
                                                description,
                                                location,
                                                startDateTime,
                                                endDateTime,
                                                reminderHours,
                                                "",  // category
                                                allowReschedule,
                                                participants,
                                                null,  // latitude
                                                null   // longitude
                                            )
                                        } else {
                                            // Updating existing schedule
                                            schedule?.let { currentSchedule ->
                                val updatedParticipantStatus = participants.associateWith { ParticipantStatus.PENDING }
                                
                                val updatedSchedule = currentSchedule.copy(
                                    title = title,
                                    description = description,
                                    location = location,
                                    startTime = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    endTime = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    reminderHours = reminderHours,
                                    participants = participants,
                                                    participantStatus = updatedParticipantStatus,
                                                    status = ScheduleStatus.PENDING,
                                                    allowReschedule = allowReschedule,
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.updateScheduleAndNotify(updatedSchedule)
                            }
                                        }
                            onNavigateBack()
                        }
                                }
                                showSaveDialog = false
                            } else {
                                showError = true
                            }
                        }
                    ) {
                        Text(if (scheduleId == null) "Create" else "Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSaveDialog = false
                    showError = false
                }) {
                    Text(if (showError) "OK" else "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDismissRequest: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    initialDate: LocalDate
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        onDateSelected(selectedDate)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp), // Increased height to accommodate the calendar better
                    colors = DatePickerDefaults.colors(
                        dayContentColor = MaterialTheme.colorScheme.onSurface,
                        weekdayContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Text(
                            text = "Select date",
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimePicker(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    selectedDate: LocalDate = LocalDate.now(),
    isStartTime: Boolean = true,
    startTime: LocalTime? = null // Add startTime parameter for end time calculation
) {
    var showDialog by remember { mutableStateOf(false) }
    val isToday = selectedDate.isEqual(LocalDate.now())

    Surface(
        onClick = { showDialog = true },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    if (showDialog) {
        // Calculate the appropriate minimum time
        val minimumTime = when {
            // For end time picker, minimum is start time + 30 minutes
            !isStartTime && startTime != null -> startTime.plusMinutes(30)
            // For start time picker on today, use general minimum time
            isStartTime && selectedDate.isEqual(LocalDate.now()) -> getMinimumTime()
            // For future dates or no specific constraints
            else -> LocalTime.MIN
        }
        
        // For today's date, use minimum time if selected time is before it
        val initialTime = if (selectedDate.isEqual(LocalDate.now()) || (!isStartTime && startTime != null)) {
            if (selectedTime.isBefore(minimumTime)) minimumTime else selectedTime
        } else {
            selectedTime
        }
        
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute
        )

    AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { 
                Column {
                    Text(if (isStartTime) "Select Start Time" else "Select End Time")
                    if (isToday || (!isStartTime && startTime != null)) {
                        Text(
                            "Minimum time: ${minimumTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                }
            },
        confirmButton = {
            TextButton(
                onClick = { 
                        val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        
                        if (!newTime.isBefore(minimumTime)) {
                            onTimeSelected(newTime)
                            showDialog = false
                        }
                },
                enabled = {
                    val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    !newTime.isBefore(minimumTime)
                }()
            ) {
                Text("OK")
            }
        },
        dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                Text("Cancel")
            }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    minDate: LocalDate = LocalDate.now()
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay()
                .atZone(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
            yearRange = IntRange(LocalDate.now().year, LocalDate.now().year + 5),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                    return !date.isBefore(minDate)
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            onDateSelected(newDate)
                        }
                        showDialog = false
        }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleAddParticipantDialog(
    onDismiss: () -> Unit,
    onParticipantsSelected: (List<User>) -> Unit,
    viewModel: SchedulesViewModel,
    existingParticipants: List<String>
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val allFriends by viewModel.allFriends.collectAsState()
    var selectedUsers by remember { mutableStateOf(existingParticipants.toSet()) }

    // Load all friends when dialog opens
    LaunchedEffect(Unit) {
        viewModel.loadAllFriends()
    }

    // Filter friends based on search query
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            viewModel.searchUsers(searchQuery)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Participants",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // Show search results if searching, otherwise show all friends
                val displayList = if (searchQuery.isNotEmpty()) searchResults else allFriends
                
                if (displayList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No friends found" 
                                  else "No matching results",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = displayList,
                            key = { user: User -> user.userId }
                        ) { user: User ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedUsers = if (selectedUsers.contains(user.userId)) {
                                            selectedUsers - user.userId
                                        } else {
                                            selectedUsers + user.userId
                                        }
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // User Icon
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                val profileBitmap = remember(user.profileImage) {
                                                    user.profileImage?.let { base64Image ->
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
                                                        contentDescription = "Profile picture of ${user.name}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                            }
                                        }

                                        // User Details
                                        Column {
                                            Text(
                                                text = user.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (user.phoneNumber.isNotEmpty()) {
                                                Text(
                                                    text = user.phoneNumber,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Checkbox(
                                        checked = selectedUsers.contains(user.userId),
                                        onCheckedChange = { checked ->
                                            selectedUsers = if (checked) {
                                                selectedUsers + user.userId
                                            } else {
                                                selectedUsers - user.userId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val selectedList = if (searchQuery.isNotEmpty()) {
                            searchResults.filter { user -> selectedUsers.contains(user.userId) }
                        } else {
                            allFriends.filter { user -> selectedUsers.contains(user.userId) }
                        }
                        onParticipantsSelected(selectedList)
                        onDismiss()
                    }
                ) {
                    Text("Add Selected")
                }
            }
        }
    )
}

 