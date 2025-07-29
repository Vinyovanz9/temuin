package com.temuin.temuin.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.temuin.temuin.MainActivity
import com.temuin.temuin.R
import com.temuin.temuin.data.model.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.*

/*
class LocationTrackingService : Service() {
    
    companion object {
        const val ACTION_START_TRACKING = "START_TRACKING"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 seconds
        private const val LOCATION_FASTEST_INTERVAL = 15000L // 15 seconds
        private const val PROXIMITY_THRESHOLD_METERS = 100.0 // 100 meters
        private const val FAR_THRESHOLD_METERS = 1000.0 // 1 km
    }
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var activeSchedules = mutableListOf<Schedule>()
    private var lastKnownLocation: Location? = null
    private var isTracking = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
//        initializeLocationServices()
    }
    
    */
/*
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startLocationTracking()
            ACTION_STOP_TRACKING -> stopLocationTracking()
        }
        return START_STICKY
    }
    *//*

    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        serviceScope.launch { 
            // Clean up any resources
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your location for activity proximity notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    */
/*
    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastKnownLocation = location
                    checkProximityToSchedules(location)
                }
            }
        }
    }
    *//*

    
    */
/*
    private fun startLocationTracking() {
        if (isTracking) return
        
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
            loadActiveSchedules()
        } catch (e: SecurityException) {
            stopSelf()
        }
    }
    *//*

    
    private fun stopLocationTracking() {
        if (!isTracking) return
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
    
    private fun createForegroundNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Activity Location Tracking")
        .setContentText("Monitoring proximity to your scheduled activities")
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .setSilent(true)
        .build()
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    */
/*
    private fun loadActiveSchedules() {
        val currentUser = auth.currentUser ?: return
        val currentTime = System.currentTimeMillis()
        
        database.reference
            .child("schedules")
            .orderByChild("startTime")
            .startAt(currentTime.toDouble())
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    activeSchedules.clear()
                    for (scheduleSnapshot in snapshot.children) {
                        val schedule = scheduleSnapshot.getValue(Schedule::class.java)
                        if (schedule != null && 
                            (schedule.userId == currentUser.uid || schedule.participants.contains(currentUser.uid)) &&
                            schedule.locationLatitude != null && schedule.locationLongitude != null) {
                            activeSchedules.add(schedule)
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }
    *//*

    
    */
/*
    private fun checkProximityToSchedules(currentLocation: Location) {
        val currentUser = auth.currentUser ?: return
        
        for (schedule in activeSchedules) {
            val scheduleLatitude = schedule.locationLatitude
            val scheduleLongitude = schedule.locationLongitude
            
            if (scheduleLatitude == null || scheduleLongitude == null) {
                continue
            }
            
            val scheduleLocation = Location("schedule").apply {
                latitude = scheduleLatitude
                longitude = scheduleLongitude
            }
            
            val distance = currentLocation.distanceTo(scheduleLocation).toDouble()
            
            when {
                distance <= PROXIMITY_THRESHOLD_METERS -> {
                    handleUserArrivedAtLocation(schedule, currentUser.uid)
                }
                distance >= FAR_THRESHOLD_METERS -> {
                    handleUserMovingAwayFromLocation(schedule, currentUser.uid, distance)
                }
                distance <= FAR_THRESHOLD_METERS && distance > PROXIMITY_THRESHOLD_METERS -> {
                    handleUserApproachingLocation(schedule, currentUser.uid, distance)
                }
            }
        }
    }
    *//*

    
    private fun handleUserArrivedAtLocation(schedule: Schedule, userId: String) {
        serviceScope.launch {
            try {
                val userName = getUserName(userId)
                val message = if (userId == schedule.userId) {
                    "$userName has arrived at ${schedule.location}"
                } else {
                    "$userName has arrived at the activity location"
                }
                
                sendNotificationToParticipants(
                    schedule = schedule,
                    excludeUserId = userId,
                    title = "Participant Arrived",
                    message = message,
                    type = "USER_ARRIVED"
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun handleUserMovingAwayFromLocation(schedule: Schedule, userId: String, distance: Double) {
        serviceScope.launch {
            try {
                val userName = getUserName(userId)
                val distanceKm = distance / 1000.0
                
                // Send notification to the user who is moving away
                sendNotificationToUser(
                    userId = userId,
                    title = "Activity Location Alert",
                    message = "You are ${String.format("%.1f", distanceKm)} km away from ${schedule.title}. You might be lost!",
                    type = "USER_FAR_FROM_LOCATION"
                )
                
                // Notify other participants
                val message = "$userName seems to be moving away from the activity location"
                sendNotificationToParticipants(
                    schedule = schedule,
                    excludeUserId = userId,
                    title = "Participant Alert",
                    message = message,
                    type = "USER_MOVING_AWAY"
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun handleUserApproachingLocation(schedule: Schedule, userId: String, distance: Double) {
        serviceScope.launch {
            try {
                val userName = getUserName(userId)
                val distanceMeters = distance.toInt()
                val message = "$userName is on their way (${distanceMeters}m away from ${schedule.location})"
                
                sendNotificationToParticipants(
                    schedule = schedule,
                    excludeUserId = userId,
                    title = "Participant Update",
                    message = message,
                    type = "USER_APPROACHING"
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private suspend fun getUserName(userId: String): String {
        return try {
            val snapshot = database.reference
                .child("users")
                .child(userId)
                .child("name")
                .get()
                .await()
            snapshot.getValue(String::class.java) ?: "Unknown User"
        } catch (e: Exception) {
            "Unknown User"
        }
    }
    
    private fun sendNotificationToUser(
        userId: String,
        title: String,
        message: String,
        type: String
    ) {
        serviceScope.launch {
            try {
                val userTokenSnapshot = database.reference
                    .child("users")
                    .child(userId)
                    .child("fcmToken")
                    .get()
                    .await()
                
                val fcmToken = userTokenSnapshot.getValue(String::class.java)
                if (fcmToken != null) {
                    // Send FCM message
                    val data = mapOf(
                        "type" to type,
                        "title" to title,
                        "message" to message,
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                    
                    // In a real implementation, you would use Firebase Admin SDK or your backend
                    // to send the FCM message. For now, we'll just save it as a notification.
                    saveNotificationToDatabase(userId, title, message, type)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun sendNotificationToParticipants(
        schedule: Schedule,
        excludeUserId: String,
        title: String,
        message: String,
        type: String
    ) {
        val allParticipants = (schedule.participants + schedule.userId).filter { it != excludeUserId }
        
        for (participantId in allParticipants) {
            sendNotificationToUser(participantId, title, message, type)
        }
    }
    
    private suspend fun saveNotificationToDatabase(
        userId: String,
        title: String,
        message: String,
        type: String
    ) {
        try {
            val notificationRef = database.reference
                .child("users")
                .child(userId)
                .child("notifications")
                .push()
            
            val notification = mapOf(
                "type" to type,
                "title" to title,
                "message" to message,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )
            
            notificationRef.setValue(notification).await()
        } catch (e: Exception) {
            // Handle error
        }
    }
}
*/
