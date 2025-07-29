package com.temuin.temuin.ui.screens.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.data.model.ParticipantStatus
import com.temuin.temuin.data.model.User
import com.temuin.temuin.data.model.Notification
import com.temuin.temuin.data.model.NotificationStatus
import com.temuin.temuin.data.model.ScheduleStatus
import com.temuin.temuin.data.repository.UserRepositoryImpl
import com.temuin.temuin.data.repository.ScheduleRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.temuin.temuin.data.model.NotificationType
import com.temuin.temuin.service.NotificationHelper
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val scheduleRepositoryImpl: ScheduleRepositoryImpl,
    private val userRepository: UserRepositoryImpl,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _isDateManuallySelected = MutableStateFlow(false)
    val isDateManuallySelected: StateFlow<Boolean> = _isDateManuallySelected

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules

    private val _monthSchedules = MutableStateFlow<List<Schedule>>(emptyList())
    val monthSchedules: StateFlow<List<Schedule>> = _monthSchedules

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedParticipants = MutableStateFlow<Map<String, User>>(emptyMap())
    val selectedParticipants = _selectedParticipants.asStateFlow()

    private val _allFriends = MutableStateFlow<List<User>>(emptyList())
    val allFriends = _allFriends.asStateFlow()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    // Real-time listeners for schedules
    private var currentDailyListener: kotlinx.coroutines.Job? = null
    private var currentMonthlyListener: kotlinx.coroutines.Job? = null

    init {
        setupRealTimeScheduleListeners()
        listenToNotifications()
        cleanupOldNotifications()
        // Schedule notifications for existing schedules
        scheduleExistingNotifications()
    }

    private fun setupRealTimeScheduleListeners() {
        viewModelScope.launch {
            userRepository.currentUser.collect { user ->
                if (user != null) {
                    // Cancel existing listeners
                    currentDailyListener?.cancel()
                    currentMonthlyListener?.cancel()
                    
                    // Setup real-time listeners for daily schedules
                    setupDailyScheduleListener(user.uid)
                    
                    // Setup real-time listeners for monthly schedules
                    updateMonthlyScheduleListener(user.uid, YearMonth.from(LocalDate.now()))
                }
            }
        }
        
        // Also listen to date changes separately
        viewModelScope.launch {
            _selectedDate.collect { selectedDate ->
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    println("📅 Date changed to: $selectedDate, updating daily listener")
                    updateDailyScheduleListener(currentUser.uid, selectedDate)
                }
            }
        }
    }

    private fun setupDailyScheduleListener(userId: String) {
        // Initial setup for current selected date
        updateDailyScheduleListener(userId, _selectedDate.value)
    }
    
    private fun updateDailyScheduleListener(userId: String, selectedDate: LocalDate) {
        // Cancel existing daily listener
        currentDailyListener?.cancel()
        
        // Create new listener for the selected date
        currentDailyListener = viewModelScope.launch {
            val startOfDay = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            println("📅 Setting up daily listener for date: $selectedDate")
            println("📅 Time range: $startOfDay to $endOfDay")
            
            // Use the repository's real-time flow
            scheduleRepositoryImpl.getSchedulesForDate(userId, startOfDay, endOfDay)
                .collect { scheduleList ->
                    println("📅 Received ${scheduleList.size} schedules for $selectedDate")
                    
                    val filteredSchedules = scheduleList.filter { schedule ->
                        (schedule.userId == userId || schedule.participants.contains(userId))
                        // Don't filter out cancelled schedules here - let the UI decide what to show
                    }.map { schedule ->
                        val newStatus = determineScheduleStatus(schedule)
                        if (newStatus != schedule.status) {
                            val updatedSchedule = schedule.copy(status = newStatus)
                            viewModelScope.launch {
                                scheduleRepositoryImpl.updateSchedule(updatedSchedule)
                            }
                            updatedSchedule
                        } else {
                            schedule
                        }
                    }
                    
                    _schedules.value = filteredSchedules.sortedBy { it.startTime }
                    println("📅 Updated schedules list with ${filteredSchedules.size} schedules")
                    
                    // Schedule alarms only for ACTIVE schedules with ACCEPTED participants
                    filteredSchedules.forEach { schedule ->
                        scheduleAlarmsForActiveSchedule(schedule, userId)
                    }
                }
        }
    }



    private fun scheduleAlarmsForActiveSchedule(schedule: Schedule, userId: String) {
        // Only schedule alarms if:
        // 1. Schedule status is ACTIVE
        // 2. User is creator OR user is participant with ACCEPTED status
        if (schedule.status == ScheduleStatus.ACTIVE) {
            val shouldScheduleAlarm = when {
                // User is the creator
                schedule.userId == userId -> true
                // User is a participant with ACCEPTED status
                schedule.participants.contains(userId) && 
                schedule.participantStatus[userId] == ParticipantStatus.ACCEPTED -> true
                else -> false
            }
            
            if (shouldScheduleAlarm) {
                println("📅 Scheduling alarm for ACTIVE schedule: ${schedule.title} for user: $userId")
                // Clear dismissed flag before scheduling new alarm
                NotificationHelper.clearDismissedFlag(context, schedule.id, userId)
                NotificationHelper.scheduleReminderForUser(context, schedule, userId)
            } else {
                println("📅 Skipping alarm for schedule: ${schedule.title} - user $userId not accepted or not creator")
                // Cancel any existing alarms for this user/schedule
                NotificationHelper.cancelReminderForUser(context, schedule.id, userId)
            }
        } else {
            println("📅 Skipping alarm for non-ACTIVE schedule: ${schedule.title} (status: ${schedule.status})")
            // Cancel any existing alarms for this user/schedule
            NotificationHelper.cancelReminderForUser(context, schedule.id, userId)
        }
    }

    private fun determineScheduleStatus(schedule: Schedule): ScheduleStatus {
        val currentTime = System.currentTimeMillis()
        
        val newStatus = when {
            schedule.status == ScheduleStatus.CANCELLED -> ScheduleStatus.CANCELLED
            // If the activity is pending and start time has passed, cancel it automatically
            schedule.status == ScheduleStatus.PENDING && currentTime > schedule.startTime -> {
                println("Auto-cancelling schedule ${schedule.id} due to passed deadline")
                // Mark this as an automatic cancellation - remove pending notifications
                updateNotificationsForCancelledSchedule(schedule.id, isAutomaticCancellation = true)
                ScheduleStatus.CANCELLED
            }
            currentTime > schedule.endTime -> ScheduleStatus.COMPLETED
            currentTime in schedule.startTime..schedule.endTime -> ScheduleStatus.ONGOING
            schedule.participants.isEmpty() -> ScheduleStatus.ACTIVE
            schedule.participantStatus.values.all { it == ParticipantStatus.DECLINED } -> {
                println("Auto-cancelling schedule ${schedule.id} - all participants declined")
                // All declined - remove pending notifications since activity is cancelled
                updateNotificationsForCancelledSchedule(schedule.id, isAutomaticCancellation = true)
                ScheduleStatus.CANCELLED
            }
            schedule.participantStatus.values.any { it == ParticipantStatus.ACCEPTED } -> ScheduleStatus.ACTIVE
            else -> ScheduleStatus.PENDING
        }

        return newStatus
    }

    private fun updateNotificationsForCancelledSchedule(scheduleId: String, isAutomaticCancellation: Boolean = false) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                println("Updating notifications for cancelled schedule: $scheduleId, isAuto: $isAutomaticCancellation")

                // Get all participants who have pending notifications for this schedule
                database.reference
                    .child("users")
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        for (userSnapshot in usersSnapshot.children) {
                            val userId = userSnapshot.key ?: continue
                            
                            // Check notifications for each user
                            database.reference
                                .child("users")
                                .child(userId)
                                .child("notifications")
                                .orderByChild("scheduleId")
                                .equalTo(scheduleId)
                                .get()
                                .addOnSuccessListener { notificationsSnapshot ->
                                    for (notificationSnapshot in notificationsSnapshot.children) {
                                        val status = notificationSnapshot.child("status").getValue(String::class.java)
                                        val notificationType = notificationSnapshot.child("type").getValue(String::class.java)
                                        
                                        println("Found notification for user $userId: type=$notificationType, status=$status")
                                        
                                        // Handle PENDING notifications
                                        if (status == NotificationStatus.PENDING.name) {
                                            val notificationRef = database.reference
                                                .child("users")
                                                .child(userId)
                                                .child("notifications")
                                                .child(notificationSnapshot.key!!)

                                            if (isAutomaticCancellation) {
                                                // For automatic cancellations, remove pending invitations completely
                                                if (notificationType == NotificationType.SCHEDULE_INVITE.name || 
                                                    notificationType == NotificationType.SCHEDULE_UPDATE.name) {
                                                    println("Removing pending notification for auto-cancelled schedule")
                                                notificationRef.removeValue()
                                                }
                                            } else {
                                                // For manual cancellations, convert to cancellation notification
                                                val updatedNotification = mapOf(
                                                    "type" to NotificationType.SCHEDULE_CANCELLED.name,
                                                    "timestamp" to System.currentTimeMillis(),
                                                    "senderId" to currentUser.uid,
                                                    "scheduleId" to scheduleId,
                                                    "title" to (notificationSnapshot.child("title").getValue(String::class.java) ?: ""),
                                                    "startTime" to (notificationSnapshot.child("startTime").getValue(Long::class.java) ?: 0L),
                                                    "status" to NotificationStatus.PENDING.name
                                                )

                                                println("Converting notification to cancellation type")
                                                notificationRef.setValue(updatedNotification)
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    println("Error querying notifications for user $userId: ${e.message}")
                        }
                        }
                    }
                    .addOnFailureListener { e ->
                        println("Error querying users: ${e.message}")
                    }
            } catch (e: Exception) {
                println("Error updating notifications for cancelled schedule: ${e.message}")
            }
        }
    }

    fun loadMonthSchedules(yearMonth: YearMonth) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            println("📅 Loading month schedules for: $yearMonth")
            updateMonthlyScheduleListener(currentUser.uid, yearMonth)
        }
    }

    private fun updateMonthlyScheduleListener(userId: String, yearMonth: YearMonth) {
        // Cancel existing monthly listener
        currentMonthlyListener?.cancel()
        
        // Create new listener for the selected month
        currentMonthlyListener = viewModelScope.launch {
            val startOfMonth = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfMonth = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            println("📅 Setting up monthly listener for: $yearMonth")
            println("📅 Month range: $startOfMonth to $endOfMonth")
            
            scheduleRepositoryImpl.getSchedulesForMonth(userId, startOfMonth, endOfMonth)
                .collect { scheduleList ->
                    println("📅 Received ${scheduleList.size} schedules for month $yearMonth")
                    
                    val filteredSchedules = scheduleList.filter { schedule ->
                        (schedule.userId == userId || schedule.participants.contains(userId))
                        // Don't filter out cancelled schedules here - let the UI decide what to show
                    }.map { schedule ->
                        val newStatus = determineScheduleStatus(schedule)
                        if (newStatus != schedule.status) {
                            val updatedSchedule = schedule.copy(status = newStatus)
                            viewModelScope.launch {
                                scheduleRepositoryImpl.updateSchedule(updatedSchedule)
                            }
                            updatedSchedule
                        } else {
                            schedule
                        }
                    }
                    _monthSchedules.value = filteredSchedules.sortedBy { it.startTime }
                    println("📅 Updated month schedules list with ${filteredSchedules.size} schedules")
                    
                    // Schedule alarms only for ACTIVE schedules with ACCEPTED participants
                    filteredSchedules.forEach { schedule ->
                        scheduleAlarmsForActiveSchedule(schedule, userId)
                    }
                }
        }
    }

    fun selectDate(date: LocalDate, isManualSelection: Boolean = false) {
        if (date == LocalDate.now()) {
            resetToToday()
        } else {
            _selectedDate.value = date
            _isDateManuallySelected.value = isManualSelection
            // Real-time listener will automatically update schedules
        }
    }

    fun resetToToday() {
        _selectedDate.value = LocalDate.now()
        _isDateManuallySelected.value = false
        // Real-time listener will automatically update schedules
    }

    fun searchUsers(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                
                // First get the user's friends list
                database.reference
                    .child("users")
                    .child(currentUser.uid)
                    .child("friends")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val friendIds = snapshot.children.mapNotNull { it.key }
                        if (friendIds.isEmpty()) {
                            _searchResults.value = emptyList()
                            return@addOnSuccessListener
                        }

                        // Then search through friends
                        database.reference
                            .child("users")
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val results = mutableListOf<User>()
                                
                                for (userSnapshot in usersSnapshot.children) {
                                    val userId = userSnapshot.key
                                    // Only include if user is a friend
                                    if (userId in friendIds) {
                                        val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                                        val status = userSnapshot.child("status").getValue(String::class.java) ?: ""
                                        val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java)
                                        val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                                        val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java)
                                        
                                        // Check if the user matches the search query
                                        if (name.contains(query, ignoreCase = true) || 
                                            phoneNumber?.contains(query, ignoreCase = true) == true) {
                                            
                                            results.add(User(
                                                userId = userId ?: "",
                                                name = name,
                                                phoneNumber = phoneNumber.toString(),
                                                profileImage = profileImage,
                                                friends = emptyMap(),
                                                status = status,
                                                fcmToken = fcmToken
                                            ))
                                        }
                                    }
                                }
                                _searchResults.value = results
                            }
                            .addOnFailureListener {
                                _searchResults.value = emptyList()
                            }
                    }
                    .addOnFailureListener {
                        _searchResults.value = emptyList()
                    }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    private fun scheduleExistingNotifications() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                
                // Get all active schedules for the current user
                firestore.collection("schedules")
                    .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
                    .whereGreaterThan("startTime", System.currentTimeMillis())
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (document in snapshot.documents) {
                            try {
                                val schedule = document.toObject(Schedule::class.java)?.copy(id = document.id)
                                if (schedule != null) {
                                    // Only schedule if user is creator or accepted participant
                                    val shouldSchedule = when {
                                        schedule.userId == currentUser.uid -> true
                                        schedule.participants.contains(currentUser.uid) && 
                                        schedule.participantStatus[currentUser.uid] == ParticipantStatus.ACCEPTED -> true
                                        else -> false
                                    }
                                    
                                    if (shouldSchedule) {
                                        println("📅 Scheduling existing alarm for: ${schedule.title}")
                                        NotificationHelper.scheduleReminderForUser(context, schedule, currentUser.uid)
                                    } else {
                                        println("📅 Skipping existing alarm - user not creator or not accepted")
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip this schedule if there's an error
                            }
                        }
                    }
            } catch (e: Exception) {
                println("📱 Error scheduling existing notifications: ${e.message}")
            }
        }
    }

    fun createSchedule(
        title: String,
        description: String,
        location: String,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        reminderHours: Int,
        category: String,
        allowReschedule: Boolean,
        participants: List<String>,
        locationLatitude: Double? = null,
        locationLongitude: Double? = null
    ) {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.currentUser.value ?: return@launch
                
                println("📱 Creating schedule: $title")
                println("📱 Start time: $startDateTime")
                println("📱 Reminder hours: $reminderHours")
                
                // Create participant status map with all participants set to PENDING
                val participantStatusMap = participants.associateWith { ParticipantStatus.PENDING }
                
                val schedule = Schedule(
                    userId = currentUser.uid,
                    title = title,
                    description = description,
                    location = location,
                    startTime = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endTime = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    reminderHours = reminderHours,
                    allowReschedule = allowReschedule,
                    participants = participants,
                    participantStatus = participantStatusMap,
                    status = if (participants.isEmpty()) ScheduleStatus.ACTIVE else ScheduleStatus.PENDING,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Create the schedule
                scheduleRepositoryImpl.createSchedule(schedule)
                    .onSuccess { createdSchedule ->
                        println("📱 Schedule created successfully: ${createdSchedule.id}")
                        
                        // Send notifications to all participants
                        participants.forEach { participantId ->
                            val notificationRef = database.reference
                                .child("users")
                                .child(participantId)
                                .child("notifications")
                                .push()

                            val notification = mapOf(
                                "type" to "SCHEDULE_INVITE",
                                "timestamp" to System.currentTimeMillis(),
                                "senderId" to currentUser.uid,
                                "scheduleId" to createdSchedule.id,
                                "title" to title,
                                "startTime" to startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                "status" to NotificationStatus.PENDING.name
                            )

                            notificationRef.setValue(notification)
                        }
                        
                        // Schedule alarms only if schedule becomes ACTIVE
                        if (createdSchedule.status == ScheduleStatus.ACTIVE) {
                            println("📅 Scheduling alarms for new ACTIVE schedule: ${createdSchedule.title}")
                            // Clear any existing dismissed flags for this schedule (in case of ID reuse)
                            NotificationHelper.clearDismissedFlags(context, createdSchedule.id, listOf(currentUser.uid))
                            NotificationHelper.scheduleReminderForSchedule(context, createdSchedule)
                        } else {
                            println("📅 Not scheduling alarms - new schedule is not ACTIVE (status: ${createdSchedule.status})")
                        }
                        
                        // Real-time listeners will automatically refresh schedules
                    }
                    .onFailure { exception ->
                        println("📱 Error creating schedule: ${exception.message}")
                        exception.printStackTrace()
                    }
            } catch (e: Exception) {
                println("📱 Error in createSchedule: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun getParticipantDetails(userId: String) {
        viewModelScope.launch {
            try {
                database.reference
                    .child("users")
                    .child(userId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        val phoneNumber = snapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                        val profileImage = snapshot.child("profileImage").getValue(String::class.java)
                        
                        val user = User(
                            userId = userId,
                            phoneNumber = phoneNumber,
                            name = name,
                            status = "",
                            profileImage = profileImage,
                            friends = emptyMap()
                        )
                        _selectedParticipants.value = _selectedParticipants.value + (userId to user)
                    }
                    .addOnFailureListener {
                        // Handle error
                    }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun removeParticipant(userId: String) {
        _selectedParticipants.value = _selectedParticipants.value - userId
    }

    fun getScheduleDetails(scheduleId: String, onResult: (Schedule?) -> Unit) {
        viewModelScope.launch {
            try {
                scheduleRepositoryImpl.getScheduleById(scheduleId)
                    .onSuccess { schedule ->
                        onResult(schedule)
                    }
                    .onFailure {
                        onResult(null)
                    }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun loadAllFriends() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                
                database.reference
                    .child("users")
                    .child(currentUser.uid)
                    .child("friends")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val friendIds = snapshot.children.mapNotNull { it.key }
                        if (friendIds.isEmpty()) {
                            _allFriends.value = emptyList()
                            return@addOnSuccessListener
                        }

                        database.reference
                            .child("users")
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val friendsList = mutableListOf<User>()
                                
                                for (userSnapshot in usersSnapshot.children) {
                                    val userId = userSnapshot.key
                                    if (userId in friendIds) {
                                        val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                                        val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java)
                                        val profileImage = userSnapshot.child("profileImage").getValue(String::class.java)
                                        
                                        friendsList.add(User(
                                            userId = userId ?: "",
                                            name = name,
                                            phoneNumber = phoneNumber.toString(),
                                            profileImage = profileImage,
                                            friends = emptyMap()
                                        ))
                                    }
                                }
                                _allFriends.value = friendsList.sortedBy { it.name }
                            }
                    }
            } catch (e: Exception) {
                _allFriends.value = emptyList()
            }
        }
    }

    private fun listenToNotifications() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            
            println("📱 Setting up notification listener for user: ${currentUser.uid}")
            
            database.reference
                .child("users")
                .child(currentUser.uid)
                .child("notifications")
                .orderByChild("timestamp")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("📱 Notification data changed - processing ${snapshot.childrenCount} notifications")
                        val notificationsList = mutableListOf<Notification>()
                        for (notificationSnapshot in snapshot.children) {
                            try {
                                val type = notificationSnapshot.child("type").getValue(String::class.java)?.let {
                                    try { NotificationType.valueOf(it) } catch (e: Exception) { NotificationType.SCHEDULE_INVITE }
                                } ?: NotificationType.SCHEDULE_INVITE
                                
                                val status = notificationSnapshot.child("status").getValue(String::class.java)?.let {
                                    try { NotificationStatus.valueOf(it) } catch (e: Exception) { NotificationStatus.PENDING }
                                } ?: NotificationStatus.PENDING
                                
                                val notification = Notification(
                                    id = notificationSnapshot.key ?: "",
                                    type = type,
                                    timestamp = notificationSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis(),
                                    senderId = notificationSnapshot.child("senderId").getValue(String::class.java) ?: "",
                                    scheduleId = notificationSnapshot.child("scheduleId").getValue(String::class.java),
                                    title = notificationSnapshot.child("title").getValue(String::class.java) ?: "",
                                    startTime = notificationSnapshot.child("startTime").getValue(Long::class.java) ?: 0,
                                    status = status
                                )
                                notificationsList.add(notification)
                                println("📱 Parsed notification: ${type.name} - ${notification.title} (Status: ${status.name})")
                            } catch (e: Exception) {
                                println("Error parsing notification: ${e.message}")
                            }
                        }
                        val sorted = notificationsList.sortedByDescending { it.timestamp }
                        _notifications.value = sorted
                        println("📱 Updated notifications list with ${sorted.size} notifications")
                        
                        // Log pending notifications by type
                        val pendingByType = sorted.filter { it.status == NotificationStatus.PENDING }
                            .groupBy { it.type }
                            .mapValues { it.value.size }
                        println("📱 Pending notifications by type: $pendingByType")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        println("📱 Notification listener cancelled: ${error.message}")
                    }
                })
        }
    }

    fun handleScheduleInvite(notification: Notification, accept: Boolean) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            
            // Update notification status
            database.reference
                .child("users")
                .child(currentUser.uid)
                .child("notifications")
                .child(notification.id)
                .child("status")
                .setValue(if (accept) NotificationStatus.ACCEPTED.name else NotificationStatus.DECLINED.name)

            if (notification.scheduleId != null) {
                // Get the schedule
                scheduleRepositoryImpl.getScheduleById(notification.scheduleId)
                    .onSuccess { schedule ->
                        // Update participant status
                        val newStatus = if (accept) ParticipantStatus.ACCEPTED else ParticipantStatus.DECLINED
                        val updatedParticipantStatus = schedule.participantStatus + (currentUser.uid to newStatus)
                        
                        // Check participant responses
                        val allResponded = updatedParticipantStatus.values.none { it == ParticipantStatus.PENDING }
                        val allDeclined = updatedParticipantStatus.values.all { it == ParticipantStatus.DECLINED }
                        val anyAccepted = updatedParticipantStatus.values.any { it == ParticipantStatus.ACCEPTED }
                        
                        // Determine new schedule status with improved logic
                        val newScheduleStatus = when {
                            allDeclined -> ScheduleStatus.CANCELLED  // If everyone declined
                            anyAccepted -> ScheduleStatus.ACTIVE     // If at least one person accepted
                            allResponded -> ScheduleStatus.CANCELLED // If everyone responded but no one accepted
                            else -> ScheduleStatus.PENDING           // If still waiting for responses
                        }
                        
                        // Update the schedule with new statuses
                        val updatedSchedule = schedule.copy(
                            id = schedule.id,  // Ensure ID is preserved
                            userId = schedule.userId,  // Preserve creator
                            title = schedule.title,
                            description = schedule.description,
                            location = schedule.location,
                            startTime = schedule.startTime,
                            endTime = schedule.endTime,
                            reminderHours = schedule.reminderHours,
                            allowReschedule = schedule.allowReschedule,
                            participants = schedule.participants,  // Preserve participants list
                            participantStatus = updatedParticipantStatus,  // Update status
                            status = newScheduleStatus,  // Update schedule status
                            createdAt = schedule.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                        
                        // Update in Firestore and refresh views
                        scheduleRepositoryImpl.updateSchedule(updatedSchedule)
                            .onSuccess {
                                // Update notification status
                                val notificationRef = database.reference
                                    .child("users")
                                    .child(currentUser.uid)
                                    .child("notifications")
                                    .child(notification.id)
                                
                                notificationRef.child("status").setValue(NotificationStatus.READ.name)
                                
                                // Real-time listeners will automatically refresh schedules
                            }
                            .onFailure { error ->
                                println("Failed to update schedule: ${error.message}")
                            }
                    }
            }

            if (!accept && notification.scheduleId != null) {
                NotificationHelper.cancelReminderForUser(context, notification.scheduleId, currentUser.uid)
            }
        }
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    fun acceptScheduleInvite(scheduleId: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            
            // Find the notification for this schedule
            val notification = notifications.value.find { it.scheduleId == scheduleId }
            if (notification != null) {
                handleScheduleInvite(notification, true)
            }
        }
    }

    fun declineScheduleInvite(scheduleId: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            
            // Find the notification for this schedule
            val notification = notifications.value.find { it.scheduleId == scheduleId }
            if (notification != null) {
                handleScheduleInvite(notification, false)
            }
        }
    }

    fun updateScheduleAndNotify(schedule: Schedule) {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.currentUser.value ?: return@launch
                
                println("🔄 Attempting to update schedule: ${schedule.id} with status: ${schedule.status}")
                println("🔄 Schedule has ${schedule.participants.size} participants")
                println("🔄 Current user: ${currentUser.uid}")
                
                // Get the previous version of the schedule to compare changes
                var previousSchedule: Schedule? = null
                var shouldNotify = false
                var notificationType = NotificationType.SCHEDULE_UPDATE
                
                try {
                    scheduleRepositoryImpl.getScheduleById(schedule.id)
                        .onSuccess { prev ->
                            previousSchedule = prev
                            println("📋 Previous schedule loaded for comparison:")
                            println("   Previous title: ${prev.title}")
                            println("   Previous startTime: ${prev.startTime}")
                            println("   Previous status: ${prev.status}")
                            println("   Previous participants: ${prev.participants}")
                            println("   Previous participantStatus: ${prev.participantStatus}")
                            println("📋 New schedule details:")
                            println("   New title: ${schedule.title}")
                            println("   New startTime: ${schedule.startTime}")
                            println("   New status: ${schedule.status}")
                            println("   New participants: ${schedule.participants}")
                            println("   New participantStatus: ${schedule.participantStatus}")
                            
                            // Determine if we should send notifications
                            shouldNotify = when {
                                // Case 1: Explicit cancellation
                                schedule.status == ScheduleStatus.CANCELLED && 
                                prev.status != ScheduleStatus.CANCELLED -> {
                                    notificationType = NotificationType.SCHEDULE_CANCELLED
                                    println("✅ Notification: Schedule cancelled")
                                    true
                                }
                                
                                // Case 2: Creator is editing the schedule (most common case)
                                // This happens when creator edits and all participants are reset to PENDING
                                schedule.participants.isNotEmpty() && 
                                schedule.status == ScheduleStatus.PENDING &&
                                schedule.participantStatus.values.all { it == ParticipantStatus.PENDING } -> {
                                    notificationType = NotificationType.SCHEDULE_UPDATE
                                    println("✅ Notification: Creator edited schedule - all participants reset to PENDING")
                                    true
                                }
                                
                                // Case 3: Any core details changed
                                prev.title != schedule.title ||
                                prev.description != schedule.description ||
                                prev.location != schedule.location ||
                                prev.startTime != schedule.startTime ||
                                prev.endTime != schedule.endTime ||
                                prev.participants != schedule.participants -> {
                                    notificationType = NotificationType.SCHEDULE_UPDATE
                                    println("✅ Notification: Schedule details changed")
                                    true
                                }
                                
                                // Case 4: Status change to PENDING (indicates an edit) 
                                schedule.status == ScheduleStatus.PENDING && 
                                prev.status != ScheduleStatus.PENDING -> {
                                    notificationType = NotificationType.SCHEDULE_UPDATE
                                    println("✅ Notification: Status changed to PENDING")
                                    true
                                }
                                
                                else -> {
                                    println("❌ Notification: No notification needed - no significant changes detected")
                                    false
                            }
                            }
                        }
                        .onFailure { error ->
                            println("⚠️ Could not get previous schedule for comparison: ${error.message}")
                            // If we can't get previous version, assume it's an edit if it has participants
                            if (schedule.participants.isNotEmpty()) {
                                shouldNotify = true
                                notificationType = if (schedule.status == ScheduleStatus.CANCELLED) {
                                    NotificationType.SCHEDULE_CANCELLED
                                } else {
                                    NotificationType.SCHEDULE_UPDATE
                                }
                                println("✅ Notification: Fallback - assuming edit due to participants")
                            }
                        }
                } catch (e: Exception) {
                    println("❌ Error getting previous schedule: ${e.message}")
                    // Fallback - if there's an error getting previous version, send notification if there are participants
                    if (schedule.participants.isNotEmpty()) {
                        shouldNotify = true
                        notificationType = if (schedule.status == ScheduleStatus.CANCELLED) {
                            NotificationType.SCHEDULE_CANCELLED
                        } else {
                            NotificationType.SCHEDULE_UPDATE
                        }
                        println("✅ Notification: Exception fallback - assuming edit due to participants")
                    }
                }
                
                // ADDITIONAL SAFETY CHECK: If schedule has participants and is PENDING, always notify
                if (!shouldNotify && schedule.participants.isNotEmpty() && schedule.status == ScheduleStatus.PENDING) {
                    shouldNotify = true
                    notificationType = NotificationType.SCHEDULE_UPDATE
                    println("✅ Notification: Safety check - PENDING schedule with participants always gets notifications")
                }
                
                // FINAL SAFETY CHECK: If this is an edit (has participants, status is PENDING, and all participants are PENDING)
                // This is the most reliable indicator that the creator edited the schedule
                if (!shouldNotify && 
                    schedule.participants.isNotEmpty() && 
                    schedule.status == ScheduleStatus.PENDING &&
                    schedule.participantStatus.values.all { it == ParticipantStatus.PENDING }) {
                    shouldNotify = true
                    notificationType = NotificationType.SCHEDULE_UPDATE
                    println("✅ Notification: FINAL SAFETY CHECK - Creator edited schedule (all participants reset to PENDING)")
                }
                
                // Update the schedule first
                scheduleRepositoryImpl.updateSchedule(schedule)
                    .onSuccess { updatedSchedule ->
                        println("✅ Successfully updated schedule: ${schedule.id}")
                        
                        // Handle alarm scheduling based on schedule status
                        if (schedule.status == ScheduleStatus.CANCELLED) {
                            println("📅 Cancelling alarms for cancelled schedule: ${schedule.title}")
                            NotificationHelper.cancelReminderForSchedule(context, schedule.id, schedule.participants, schedule.userId)
                        } else if (schedule.status == ScheduleStatus.ACTIVE) {
                            println("📅 Rescheduling alarms for ACTIVE schedule: ${schedule.title}")
                            // Clear dismissed flags before rescheduling to ensure new alarms work
                            NotificationHelper.clearDismissedFlags(context, schedule.id, schedule.participants + schedule.userId)
                            NotificationHelper.rescheduleReminderForSchedule(context, schedule)
                        } else if (schedule.status == ScheduleStatus.PENDING) {
                            println("📅 Schedule is PENDING - clearing dismissed flags for potential future activation")
                            // Clear dismissed flags even for PENDING schedules in case they become ACTIVE later
                            NotificationHelper.clearDismissedFlags(context, schedule.id, schedule.participants + schedule.userId)
                        } else {
                            println("📅 Not scheduling alarms - schedule not ACTIVE (status: ${schedule.status})")
                        }
                        
                        if (shouldNotify) {
                            try {
                                // Send notifications to all participants immediately
                                println("📤 Sending ${notificationType.name} notifications to ${schedule.participants.size} participants")
                                println("📤 Participants list: ${schedule.participants}")
                                println("📤 Current user (will be excluded): ${currentUser.uid}")
                                
                                schedule.participants.forEach { participantId ->
                                    // Don't send notifications to the user who initiated the change
                                    if (participantId != currentUser.uid) {
                                        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
                                            try {
                                                println("📨 Processing notifications for participant: $participantId")
                                                
                                                // First, clean up ALL old notifications for this schedule
                                                val oldNotifications = database.reference
                                                    .child("users")
                                                    .child(participantId)
                                                    .child("notifications")
                                                    .orderByChild("scheduleId")
                                                    .equalTo(schedule.id)
                                                    .get()
                                                    .await()
                                                
                                                var removedCount = 0
                                                oldNotifications.children.forEach { notification ->
                                                    val status = notification.child("status").getValue(String::class.java)
                                                    if (status == NotificationStatus.PENDING.name) {
                                                        notification.ref.removeValue().await()
                                                        removedCount++
                                                    }
                                                }
                                                println("🗑️ Removed $removedCount old pending notifications for schedule ${schedule.id}")

                                                // Then create the new notification
                                                val notificationRef = database.reference
                                                    .child("users")
                                                    .child(participantId)
                                                    .child("notifications")
                                                    .push()

                                                val notificationDetails = mapOf(
                                                    "type" to notificationType.name,
                                                    "timestamp" to System.currentTimeMillis(),
                                                    "senderId" to currentUser.uid,
                                                    "scheduleId" to schedule.id,
                                                    "title" to schedule.title,
                                                    "startTime" to schedule.startTime,
                                                    "status" to NotificationStatus.PENDING.name
                                                )

                                                // Create the new notification
                                                notificationRef.setValue(notificationDetails).await()
                                                println("✅ Notification sent successfully to participant: $participantId")
                                                println("   📝 Details: type=${notificationType.name}, title=${schedule.title}")
                                            } catch (e: Exception) {
                                                println("❌ Error processing notification for $participantId: ${e.message}")
                                                e.printStackTrace()
                                            }
                                        }
                                    } else {
                                        println("⏭️ Skipping notification to current user: $participantId")
                                    }
                                }
                            } catch (e: Exception) {
                                println("❌ Error in notification system: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            println("❌ No notifications needed for this update")
                        }
                            
                        // Real-time listeners will automatically refresh schedules
                        println("🔄 Real-time listeners will handle schedule refresh...")
                        
                        println("✅ Schedule update completed successfully")
                        }
                        .onFailure { exception ->
                            println("❌ Error updating schedule: ${exception.message}")
                        exception.printStackTrace()
                }
            } catch (e: Exception) {
                println("❌ Error in updateScheduleAndNotify: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun getPreviousScheduleVersion(scheduleId: String, onResult: (Schedule?) -> Unit) {
        viewModelScope.launch {
            try {
                database.reference
                    .child("schedules")
                    .child(scheduleId)
                    .child("previousVersion")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val previousSchedule = Schedule(
                                id = scheduleId,
                                userId = snapshot.child("userId").getValue(String::class.java) ?: "",
                                title = snapshot.child("title").getValue(String::class.java) ?: "",
                                description = snapshot.child("description").getValue(String::class.java) ?: "",
                                location = snapshot.child("location").getValue(String::class.java) ?: "",
                                startTime = snapshot.child("startTime").getValue(Long::class.java) ?: 0L,
                                endTime = snapshot.child("endTime").getValue(Long::class.java) ?: 0L,
                                reminderHours = snapshot.child("reminderHours").getValue(Int::class.java) ?: 1,
                                allowReschedule = snapshot.child("allowReschedule").getValue(Boolean::class.java) ?: true,
                                participants = snapshot.child("participants").children.mapNotNull { it.getValue(String::class.java) },
                                participantStatus = snapshot.child("participantStatus").children.associate {
                                    it.key!! to ParticipantStatus.valueOf(it.getValue(String::class.java) ?: "PENDING")
                                },
                                status = ScheduleStatus.valueOf(snapshot.child("status").getValue(String::class.java) ?: "PENDING"),
                                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
                                updatedAt = snapshot.child("updatedAt").getValue(Long::class.java) ?: 0L
                            )
                            onResult(previousSchedule)
                        } else {
                            onResult(null)
                        }
                    }
                    .addOnFailureListener {
                        onResult(null)
                    }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            
            // Update notification status to READ
            database.reference
                .child("users")
                .child(currentUser.uid)
                .child("notifications")
                .child(notificationId)
                .child("status")
                .setValue(NotificationStatus.READ.name)
        }
    }

    // Force refresh of schedule data - can be called by screens
    fun forceRefreshSchedules() {
        println("Force refresh schedules triggered")
        // Real-time listeners will automatically refresh data
        // No manual refresh needed
    }

    // Clean up old pending notifications for cancelled/completed activities
    private fun cleanupOldNotifications() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val currentTime = System.currentTimeMillis()
                
                // Get current user's notifications
                database.reference
                    .child("users")
                    .child(currentUser.uid)
                    .child("notifications")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (notificationSnapshot in snapshot.children) {
                            val notificationId = notificationSnapshot.key ?: continue
                            val status = notificationSnapshot.child("status").getValue(String::class.java)
                            val scheduleId = notificationSnapshot.child("scheduleId").getValue(String::class.java)
                            val startTime = notificationSnapshot.child("startTime").getValue(Long::class.java) ?: 0L
                            val notificationType = notificationSnapshot.child("type").getValue(String::class.java)
                            
                            // Remove pending notifications for activities that have passed their start time
                            if (status == NotificationStatus.PENDING.name && 
                                scheduleId != null && 
                                currentTime > startTime &&
                                (notificationType == NotificationType.SCHEDULE_INVITE.name || 
                                 notificationType == NotificationType.SCHEDULE_UPDATE.name)) {
                                
                                println("Cleaning up expired pending notification: $notificationId")
                                database.reference
                                    .child("users")
                                    .child(currentUser.uid)
                                    .child("notifications")
                                    .child(notificationId)
                                    .removeValue()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        println("Error cleaning up notifications: ${e.message}")
                    }
            } catch (e: Exception) {
                println("Error in cleanupOldNotifications: ${e.message}")
            }
        }
    }

    private fun hasTimeOverlap(start1: Long, end1: Long, start2: Long, end2: Long): Boolean {
        return start1 < end2 && start2 < end1
    }

    private fun checkScheduleConflict(startTime: Long, endTime: Long, excludeScheduleId: String? = null): List<Schedule> {
        val currentUserId = auth.currentUser?.uid ?: return emptyList()
        
        return schedules.value.filter { schedule ->
            // Skip if it's the same schedule
            if (schedule.id == excludeScheduleId) return@filter false
            
            // Skip if it's cancelled
            if (schedule.status == ScheduleStatus.CANCELLED) return@filter false
            
            // Check if user is owner or has accepted the schedule
            val isUserInvolved = schedule.userId == currentUserId || 
                (schedule.participants.contains(currentUserId) && 
                schedule.participantStatus[currentUserId] == ParticipantStatus.ACCEPTED)
            
            if (!isUserInvolved) return@filter false
            
            // Check for time overlap
            hasTimeOverlap(startTime, endTime, schedule.startTime, schedule.endTime)
        }
    }

    fun getConflictingSchedules(scheduleId: String, onResult: (List<Schedule>) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("ConflictCheck", "Starting conflict check for schedule: $scheduleId")
                
                // Get the schedule we're trying to accept using Firestore
                val scheduleDoc = firestore.collection("schedules")
                    .document(scheduleId)
                    .get()
                    .await()

                if (!scheduleDoc.exists()) {
                    Log.d("ConflictCheck", "Schedule not found")
                    onResult(emptyList())
                    return@launch
                }

                val newStartTime = scheduleDoc.getLong("startTime") ?: return@launch
                val newEndTime = scheduleDoc.getLong("endTime") ?: return@launch
                
                Log.d("ConflictCheck", "New schedule time: $newStartTime - $newEndTime")

                // Get ALL user's schedules from Firestore, not just current day
                val currentUserId = auth.currentUser?.uid ?: return@launch
                Log.d("ConflictCheck", "Current user: $currentUserId")
                
                // Query all schedules where user is creator or participant
                val creatorSchedulesQuery = firestore.collection("schedules")
                    .whereEqualTo("userId", currentUserId)
                    .get()
                    .await()
                
                val participantSchedulesQuery = firestore.collection("schedules")
                    .whereArrayContains("participants", currentUserId)
                    .get()
                    .await()
                
                val allUserSchedules = mutableListOf<Schedule>()
                
                // Process creator schedules
                creatorSchedulesQuery.documents.forEach { doc ->
                    try {
                        val schedule = doc.toObject(Schedule::class.java)?.copy(id = doc.id)
                        if (schedule != null) {
                            allUserSchedules.add(schedule)
                        }
                    } catch (e: Exception) {
                        Log.e("ConflictCheck", "Error parsing creator schedule: ${e.message}")
                    }
                }
                
                // Process participant schedules
                participantSchedulesQuery.documents.forEach { doc ->
                    try {
                        val schedule = doc.toObject(Schedule::class.java)?.copy(id = doc.id)
                        if (schedule != null && !allUserSchedules.any { it.id == schedule.id }) {
                            allUserSchedules.add(schedule)
                        }
                    } catch (e: Exception) {
                        Log.e("ConflictCheck", "Error parsing participant schedule: ${e.message}")
                    }
                }

                Log.d("ConflictCheck", "Total user schedules to check: ${allUserSchedules.size}")

                val conflicts = allUserSchedules.filter { existingSchedule ->
                    Log.d("ConflictCheck", """
                        Checking schedule: ${existingSchedule.id}
                        Title: ${existingSchedule.title}
                        Time: ${existingSchedule.startTime} - ${existingSchedule.endTime}
                        Status: ${existingSchedule.status}
                        Owner: ${existingSchedule.userId}
                        Participants: ${existingSchedule.participants}
                        ParticipantStatus: ${existingSchedule.participantStatus}
                    """.trimIndent())

                    // Don't compare with the same schedule
                    if (existingSchedule.id == scheduleId) {
                        Log.d("ConflictCheck", "Skipping same schedule")
                        return@filter false
                    }
                    
                    // Don't check cancelled schedules
                    if (existingSchedule.status == ScheduleStatus.CANCELLED) {
                        Log.d("ConflictCheck", "Skipping cancelled schedule")
                        return@filter false
                    }
                    
                    // Only check schedules where user is owner or has accepted
                    val isUserInvolved = existingSchedule.userId == currentUserId || 
                        (existingSchedule.participants.contains(currentUserId) && 
                        existingSchedule.participantStatus[currentUserId] == ParticipantStatus.ACCEPTED)
                    
                    Log.d("ConflictCheck", "Is user involved: $isUserInvolved")
                    
                    if (!isUserInvolved) {
                        Log.d("ConflictCheck", "Skipping - user not involved")
                        return@filter false
                    }
                    
                    // Check for time overlap
                    val hasOverlap = newStartTime < existingSchedule.endTime && 
                        existingSchedule.startTime < newEndTime
                    
                    Log.d("ConflictCheck", """
                        Time overlap check:
                        newStart($newStartTime) < existingEnd(${existingSchedule.endTime}): ${newStartTime < existingSchedule.endTime}
                        existingStart(${existingSchedule.startTime}) < newEnd($newEndTime): ${existingSchedule.startTime < newEndTime}
                        Has overlap: $hasOverlap
                    """.trimIndent())
                    
                    hasOverlap
                }

                Log.d("ConflictCheck", "Found ${conflicts.size} conflicts")
                conflicts.forEach { conflict ->
                    Log.d("ConflictCheck", "Conflict with: ${conflict.title}")
                }

                onResult(conflicts)
            } catch (e: Exception) {
                Log.e("ConflictCheck", "Error checking conflicts", e)
                onResult(emptyList())
            }
        }
    }

    fun checkTimeConflicts(startTime: Long, endTime: Long, excludeScheduleId: String? = null, onResult: (List<Schedule>) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("ConflictCheck", "Checking conflicts for new schedule: $startTime - $endTime")
                
                val currentUserId = auth.currentUser?.uid ?: return@launch
                Log.d("ConflictCheck", "Current user: $currentUserId")
                
                // Get ALL user's schedules from Firestore, not just current day
                // Query all schedules where user is creator or participant
                val creatorSchedulesQuery = firestore.collection("schedules")
                    .whereEqualTo("userId", currentUserId)
                    .get()
                    .await()
                
                val participantSchedulesQuery = firestore.collection("schedules")
                    .whereArrayContains("participants", currentUserId)
                    .get()
                    .await()
                
                val allUserSchedules = mutableListOf<Schedule>()
                
                // Process creator schedules
                creatorSchedulesQuery.documents.forEach { doc ->
                    try {
                        val schedule = doc.toObject(Schedule::class.java)?.copy(id = doc.id)
                        if (schedule != null) {
                            allUserSchedules.add(schedule)
                        }
                    } catch (e: Exception) {
                        Log.e("ConflictCheck", "Error parsing creator schedule: ${e.message}")
                    }
                }
                
                // Process participant schedules
                participantSchedulesQuery.documents.forEach { doc ->
                    try {
                        val schedule = doc.toObject(Schedule::class.java)?.copy(id = doc.id)
                        if (schedule != null && !allUserSchedules.any { it.id == schedule.id }) {
                            allUserSchedules.add(schedule)
                        }
                    } catch (e: Exception) {
                        Log.e("ConflictCheck", "Error parsing participant schedule: ${e.message}")
                    }
                }

                Log.d("ConflictCheck", "Total user schedules to check: ${allUserSchedules.size}")

                val conflicts = allUserSchedules.filter { existingSchedule ->
                    Log.d("ConflictCheck", """
                        Checking schedule: ${existingSchedule.id}
                        Title: ${existingSchedule.title}
                        Time: ${existingSchedule.startTime} - ${existingSchedule.endTime}
                        Status: ${existingSchedule.status}
                        Owner: ${existingSchedule.userId}
                        Participants: ${existingSchedule.participants}
                        ParticipantStatus: ${existingSchedule.participantStatus}
                    """.trimIndent())
                    
                    // Skip if it's the same schedule being edited
                    if (existingSchedule.id == excludeScheduleId) {
                        Log.d("ConflictCheck", "Skipping same schedule")
                        return@filter false
                    }
                    
                    // Don't check cancelled schedules
                    if (existingSchedule.status == ScheduleStatus.CANCELLED) {
                        Log.d("ConflictCheck", "Skipping cancelled schedule")
                        return@filter false
                    }
                    
                    // Only check schedules where user is owner or has accepted
                    val isUserInvolved = existingSchedule.userId == currentUserId || 
                        (existingSchedule.participants.contains(currentUserId) && 
                        existingSchedule.participantStatus[currentUserId] == ParticipantStatus.ACCEPTED)
                    
                    Log.d("ConflictCheck", "Is user involved: $isUserInvolved")
                    
                    if (!isUserInvolved) {
                        Log.d("ConflictCheck", "Skipping - user not involved")
                        return@filter false
                    }
                    
                    // Check for time overlap
                    val hasOverlap = startTime < existingSchedule.endTime && 
                        existingSchedule.startTime < endTime
                    
                    Log.d("ConflictCheck", """
                        Time overlap check:
                        newStart($startTime) < existingEnd(${existingSchedule.endTime}): ${startTime < existingSchedule.endTime}
                        existingStart(${existingSchedule.startTime}) < newEnd($endTime): ${existingSchedule.startTime < endTime}
                        Has overlap: $hasOverlap
                    """.trimIndent())
                    
                    hasOverlap
                }

                Log.d("ConflictCheck", "Found ${conflicts.size} conflicts")
                conflicts.forEach { conflict ->
                    Log.d("ConflictCheck", "Conflict with: ${conflict.title}")
                }

                onResult(conflicts)
            } catch (e: Exception) {
                Log.e("ConflictCheck", "Error checking conflicts", e)
                onResult(emptyList())
            }
        }
    }
    
    // Debug function to test notifications manually
    fun debugSendTestNotification(scheduleId: String, participantId: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                println("🧪 DEBUG: Sending test notification")
                println("🧪 Schedule ID: $scheduleId")
                println("🧪 Participant ID: $participantId")
                println("🧪 Current User: ${currentUser.uid}")
                
                val notificationRef = database.reference
                    .child("users")
                    .child(participantId)
                    .child("notifications")
                    .push()

                val notificationDetails = mapOf(
                    "type" to NotificationType.SCHEDULE_UPDATE.name,
                    "timestamp" to System.currentTimeMillis(),
                    "senderId" to currentUser.uid,
                    "scheduleId" to scheduleId,
                    "title" to "Test Schedule Update",
                    "startTime" to System.currentTimeMillis() + (24 * 60 * 60 * 1000), // Tomorrow
                    "status" to NotificationStatus.PENDING.name
                )

                notificationRef.setValue(notificationDetails)
                    .addOnSuccessListener {
                        println("🧪 DEBUG: Test notification sent successfully!")
                    }
                    .addOnFailureListener { e ->
                        println("🧪 DEBUG: Failed to send test notification: ${e.message}")
                    }
            } catch (e: Exception) {
                println("🧪 DEBUG: Error in test notification: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // Debug function to check current notifications
    fun debugCheckNotifications() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                println("🧪 DEBUG: Checking current notifications for user: ${currentUser.uid}")
                
                database.reference
                    .child("users")
                    .child(currentUser.uid)
                    .child("notifications")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        println("🧪 DEBUG: Found ${snapshot.childrenCount} total notifications")
                        var pendingCount = 0
                        for (notificationSnapshot in snapshot.children) {
                            val type = notificationSnapshot.child("type").getValue(String::class.java)
                            val status = notificationSnapshot.child("status").getValue(String::class.java)
                            val title = notificationSnapshot.child("title").getValue(String::class.java)
                            val scheduleId = notificationSnapshot.child("scheduleId").getValue(String::class.java)
                            
                            if (status == NotificationStatus.PENDING.name) {
                                pendingCount++
                            }
                            
                            println("🧪 DEBUG: Notification - Type: $type, Status: $status, Title: $title, ScheduleId: $scheduleId")
                        }
                        println("🧪 DEBUG: Total pending notifications: $pendingCount")
                    }
                    .addOnFailureListener { e ->
                        println("🧪 DEBUG: Error checking notifications: ${e.message}")
                    }
            } catch (e: Exception) {
                println("🧪 DEBUG: Error in check notifications: ${e.message}")
                e.printStackTrace()
            }
        }
    }
} 