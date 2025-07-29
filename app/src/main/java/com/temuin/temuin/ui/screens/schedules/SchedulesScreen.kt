package com.temuin.temuin.ui.screens.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.temuin.temuin.R
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.data.model.ScheduleStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import com.temuin.temuin.ui.screens.auth.PhoneAuthViewModel
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.temuin.temuin.data.model.NotificationStatus
import com.temuin.temuin.data.model.ParticipantStatus
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToEditSchedule: (String?) -> Unit,
    onLogout: () -> Unit,
    onScheduleClick: (String) -> Unit,
    schedulesViewModel: SchedulesViewModel = hiltViewModel(),
    authViewModel: PhoneAuthViewModel = hiltViewModel()
) {
    val schedules by schedulesViewModel.schedules.collectAsState()
    val selectedDate by schedulesViewModel.selectedDate.collectAsState()
    val isDateManuallySelected by schedulesViewModel.isDateManuallySelected.collectAsState()
    val monthSchedules by schedulesViewModel.monthSchedules.collectAsState()
    val currentUserId = schedulesViewModel.getCurrentUserId()
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var calendarDragOffset by remember { mutableStateOf(0f) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Filter schedules for the selected date
    val filteredSchedules = remember(schedules, selectedDate, isDateManuallySelected) {
        schedules.filter { schedule ->
            val scheduleDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(schedule.startTime),
                ZoneId.systemDefault()
            ).toLocalDate()
            val targetDate = if (isDateManuallySelected) selectedDate else LocalDate.now()

            scheduleDate == targetDate &&
            schedule.status != ScheduleStatus.CANCELLED &&  // Explicitly filter out cancelled schedules
            (schedule.userId == currentUserId ||
            (schedule.participants.contains(currentUserId) && 
            schedule.participantStatus[currentUserId] == ParticipantStatus.ACCEPTED))
        }.sortedBy { it.startTime }
    }

    // Determine if calendar should be minimized based on drag gesture
    val isCalendarMinimized by remember {
        derivedStateOf {
            // Minimize when calendar is dragged up
            calendarDragOffset < -50f
        }
    }
    
    // Animate drag handle opacity based on interaction
    val dragHandleAlpha by animateFloatAsState(
        targetValue = if (calendarDragOffset != 0f) 0.8f else 0.4f,
        animationSpec = tween(200),
        label = "drag_handle_alpha"
    )

    // Create a set of dates that have schedules
    val datesWithSchedules = remember(monthSchedules) {
        monthSchedules
            .filter { schedule -> 
                // Only show non-cancelled schedules
                schedule.status != ScheduleStatus.CANCELLED &&
                // For participant schedules, only show if accepted
                (!schedule.participants.contains(currentUserId) || 
                schedule.participantStatus[currentUserId] == ParticipantStatus.ACCEPTED)
            }
            .map { schedule ->
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(schedule.startTime),
                    ZoneId.systemDefault()
                ).toLocalDate()
            }.toSet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                title = { Text("Temuin", fontWeight = FontWeight.Bold) },
                actions = {
                    // Notification Badge
                    val notifications by schedulesViewModel.notifications.collectAsState()
                    val pendingNotifications = notifications.count { it.status == NotificationStatus.PENDING }
                    
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                        if (pendingNotifications > 0) {
                            Badge(
                                modifier = Modifier.offset(x = 8.dp, y = (-6).dp),
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (pendingNotifications > 9) "9+" else pendingNotifications.toString(),
                                    color = MaterialTheme.colorScheme.background
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Profile") },
                            onClick = {
                                showMenu = false
                                onNavigateToProfile()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                showMenu = false
                                showLogoutDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            }
                        )
                    }

                    // Logout confirmation dialog
                    if (showLogoutDialog) {
                        AlertDialog(
                            onDismissRequest = { showLogoutDialog = false },
                            title = { Text("Confirm Logout") },
                            text = { Text("Are you sure you want to logout?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showLogoutDialog = false
                                    authViewModel.signOut(context as Activity)
                                    onLogout()
                                }) {
                                    Text("Logout", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogoutDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditSchedule(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(painter = painterResource(R.drawable.calendar_add_24), contentDescription = "Add Schedule")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 0.dp)
        ) {
            // Calendar - switches between full and weekly view
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                // Reset or snap to final position
                                if (calendarDragOffset < -100f) {
                                    calendarDragOffset = -100f
                                } else if (calendarDragOffset > 0f) {
                                    calendarDragOffset = 0f
                                }
                            }
                        ) { change, dragAmount ->
                            calendarDragOffset += dragAmount.y
                            // Limit the drag range
                            calendarDragOffset = calendarDragOffset.coerceIn(-200f, 50f)
                        }
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                    // Month navigation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                currentMonth = currentMonth.minusMonths(1)
                                schedulesViewModel.loadMonthSchedules(currentMonth)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Month",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Text(
                            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(
                            onClick = {
                                currentMonth = currentMonth.plusMonths(1)
                                schedulesViewModel.loadMonthSchedules(currentMonth)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Month",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = isCalendarMinimized,
                        transitionSpec = {
                            if (targetState) {
                                // Transitioning to weekly view (minimizing)
                                slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { -it / 2 }
                                ) togetherWith slideOutVertically(
                                    animationSpec = tween(300),
                                    targetOffsetY = { it / 2 }
                                )
                            } else {
                                // Transitioning to monthly view (expanding)
                                slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { it / 2 }
                                ) togetherWith slideOutVertically(
                                    animationSpec = tween(300),
                                    targetOffsetY = { -it / 2 }
                                )
                            }
                        },
                        label = "calendar_transition"
                    ) { minimized ->
                        if (minimized) {
                            // Weekly view
                            WeeklyCalendarView(
                                selectedDate = selectedDate,
                                datesWithSchedules = datesWithSchedules,
                                onDateSelected = { date ->
                                    if (date == LocalDate.now()) {
                                        schedulesViewModel.resetToToday()
                                    } else {
                                        schedulesViewModel.selectDate(date, true)
                                    }
                                }
                            )
                        } else {
                            // Full monthly view
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))

                                // Calendar grid
                                val calendarState = rememberCalendarState(
                                    startMonth = currentMonth,
                                    endMonth = currentMonth,
                                    firstVisibleMonth = currentMonth,
                                    firstDayOfWeek = firstDayOfWeekFromLocale()
                                )

                                HorizontalCalendar(
                                    state = calendarState,
                                    dayContent = { day ->
                                        Day(
                                            day = day,
                                            isSelected = selectedDate == day.date,
                                            hasSchedule = datesWithSchedules.contains(day.date),
                                            onDateSelected = { date ->
                                                // Handle next/previous month navigation
                                                if (day.position == DayPosition.InDate) {
                                                    // Previous month date clicked
                                                    currentMonth = currentMonth.minusMonths(1)
                                                    schedulesViewModel.loadMonthSchedules(currentMonth)
                                                } else if (day.position == DayPosition.OutDate) {
                                                    // Next month date clicked
                                                    currentMonth = currentMonth.plusMonths(1)
                                                    schedulesViewModel.loadMonthSchedules(currentMonth)
                                                }
                                                
                                                // Select the date
                                                if (date == LocalDate.now()) {
                                                    schedulesViewModel.resetToToday()
                                                } else {
                                                    schedulesViewModel.selectDate(date, true)
                                                }
                                            }
                                        )
                                    },
                                    monthHeader = {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            daysOfWeek.forEach { dayOfWeek ->
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // Drag handle indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dragHandleAlpha),
                            shape = MaterialTheme.shapes.small
                        ) {}
                    }
                }
            }


            // Upcoming activities
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isDateManuallySelected && selectedDate.isBefore(LocalDate.now())) {
                            "Past Activities"
                        } else if (isDateManuallySelected) {
                            "Upcoming Activities"
                        } else {
                            "Today's Activities"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    val scheduleCount = schedules.count { schedule ->
                        val scheduleDate = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(schedule.startTime),
                            ZoneId.systemDefault()
                        ).toLocalDate()
                        val targetDate = if (isDateManuallySelected) selectedDate else LocalDate.now()

                        scheduleDate == targetDate &&
                        schedule.status != ScheduleStatus.CANCELLED &&
                        (schedule.userId == currentUserId ||
                        (schedule.participants.contains(currentUserId) && 
                        schedule.participantStatus[currentUserId] == ParticipantStatus.ACCEPTED))
                    }
                    
                    Text(
                        text = "$scheduleCount ${if (scheduleCount == 1) "Schedule" else "Schedules"} found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDateManuallySelected) {
                    Button(
                        onClick = { 
                            schedulesViewModel.resetToToday()
                            currentMonth = YearMonth.now()
                            schedulesViewModel.loadMonthSchedules(currentMonth)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(start = 12.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Today")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredSchedules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 16.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isDateManuallySelected) {
                                        "No activities for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd"))}"
                                    } else {
                                        "No activities for today"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap the + button to create a new schedule",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp
                )
            ) {
                items(
                    items = filteredSchedules,
                    key = { it.id }
                ) { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onClick = { onScheduleClick(schedule.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun Day(
    day: CalendarDay,
    isSelected: Boolean,
    hasSchedule: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val isPastDate = day.date.isBefore(LocalDate.now())
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(
                onClick = { onDateSelected(day.date) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isPastDate -> MaterialTheme.colorScheme.surfaceVariant
                            day.position != DayPosition.MonthDate -> Color.Transparent
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isPastDate -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        day.position != DayPosition.MonthDate -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (hasSchedule && day.position == DayPosition.MonthDate) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(4.dp)
                        .background(
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isPastDate -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

private val daysOfWeek = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY
)

@Composable
fun ScheduleItem(
    schedule: Schedule,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.status == ScheduleStatus.COMPLETED) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row: Title and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (schedule.status == ScheduleStatus.COMPLETED)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                ScheduleStatusChip(schedule.status)
            }
            
            // Description if available
            if (schedule.description.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = schedule.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Date and Time
            val startDateTime = Instant.ofEpochMilli(schedule.startTime)
                .atZone(ZoneId.systemDefault())
            val endDateTime = Instant.ofEpochMilli(schedule.endTime)
                .atZone(ZoneId.systemDefault())
            val isMultiDay = startDateTime.toLocalDate() != endDateTime.toLocalDate()
            
            if (isMultiDay) {
                // Multi-day activity display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${startDateTime.format(DateTimeFormatter.ofPattern("MMM d HH:mm"))} - ${endDateTime.format(DateTimeFormatter.ofPattern("MMM d HH:mm"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Single day activity display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = startDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Time
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.time_24),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "${startDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${endDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }
            }

            // Bottom row: Participants and Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Participants count
                val totalParticipants = schedule.participants.size + 1 // +1 for creator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (totalParticipants > 1) {
                        Icon(
                            painterResource(R.drawable.baseline_groups_24),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (schedule.status == ScheduleStatus.COMPLETED)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (schedule.status == ScheduleStatus.COMPLETED)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = if (totalParticipants > 1) "$totalParticipants Participants" else "Just You",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (schedule.status == ScheduleStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Location if available
                if (schedule.location.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (schedule.status == ScheduleStatus.COMPLETED)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = schedule.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (schedule.status == ScheduleStatus.COMPLETED)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyCalendarView(
    selectedDate: LocalDate,
    datesWithSchedules: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit
) {
    // Get the week containing the selected date
    val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value % 7L)
    val weekDates = (0..6).map { startOfWeek.plusDays(it.toLong()) }
    
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Week day headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            daysOfWeek.forEach { dayOfWeek ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Week dates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekDates.forEach { date ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    WeekDay(
                        date = date,
                        isSelected = selectedDate == date,
                        hasSchedule = datesWithSchedules.contains(date),
                        onDateSelected = onDateSelected
                    )
                }
            }
        }
    }
}

@Composable
fun WeekDay(
    date: LocalDate,
    isSelected: Boolean,
    hasSchedule: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val isPastDate = date.isBefore(LocalDate.now())
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable { onDateSelected(date) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isPastDate -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isPastDate -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (hasSchedule) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(4.dp)
                        .background(
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isPastDate -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}