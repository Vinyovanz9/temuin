package com.temuin.temuin.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.temuin.temuin.MainActivity
import com.temuin.temuin.R
import java.text.SimpleDateFormat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val CHANNEL_ID_ALARM = "activity_alarms"
        const val ACTION_REMINDER_ALARM = "REMINDER_ALARM"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_REPEAT_COUNT = "repeat_count"
        const val MAX_REPEAT_COUNT = 3
        const val REPEAT_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        const val EXTRA_TITLE = "title"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_MINUTES_UNTIL = "minutes_until"
        
        private var activeMediaPlayer: MediaPlayer? = null
        
        fun scheduleReminder(
            context: Context,
            scheduleId: String,
            userId: String,
            title: String,
            location: String,
            startTime: Long,
            reminderHours: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val reminderTime = startTime - (reminderHours * 60 * 60 * 1000)
            
            println("⏰ Scheduling reminder for schedule: $scheduleId")
            println("⏰ Current time: ${System.currentTimeMillis()}")
            println("⏰ Reminder time: $reminderTime")
            println("⏰ Start time: $startTime")
            println("⏰ Hours before: $reminderHours")
            println("⏰ Android API Level: ${Build.VERSION.SDK_INT}")
            println("⏰ Time until reminder: ${(reminderTime - System.currentTimeMillis()) / 1000} seconds")
            
            // Don't schedule if reminder time has passed
            if (reminderTime <= System.currentTimeMillis()) {
                println("⏰ Reminder time has passed, not scheduling")
                return
            }
            
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_REMINDER_ALARM
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LOCATION, location)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_REPEAT_COUNT, 0)
            }
            
            val requestCode = (scheduleId + userId).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        // Android 12+ (API 31+)
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setAlarmClock(
                                AlarmManager.AlarmClockInfo(reminderTime, pendingIntent),
                                pendingIntent
                            )
                            println("⏰ Scheduled exact alarm for API ${Build.VERSION.SDK_INT}")
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                reminderTime,
                                pendingIntent
                            )
                            println("⏰ Scheduled inexact alarm for API ${Build.VERSION.SDK_INT} (no exact alarm permission)")
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        // Android 6+ (API 23+) - Use setExactAndAllowWhileIdle for doze bypass
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                        println("⏰ Scheduled exact alarm with doze bypass for API ${Build.VERSION.SDK_INT}")
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                        // Android 4.4+ (API 19+) - Use setExact for precision
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                        println("⏰ Scheduled exact alarm for API ${Build.VERSION.SDK_INT}")
                    }
                    else -> {
                        // Below Android 4.4 (API < 19) - Use basic set
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                        println("⏰ Scheduled regular alarm for API ${Build.VERSION.SDK_INT}")
                    }
                }
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                println("⏰ Alarm scheduled successfully for ${dateFormat.format(Date(reminderTime))}")
                
            } catch (e: Exception) {
                println("⏰ Error scheduling alarm: ${e.message}")
                e.printStackTrace()
            }
        }
        
        fun cancelReminder(context: Context, scheduleId: String, userId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_REMINDER_ALARM
            }
            val requestCode = (scheduleId + userId).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            println("⏰ Cancelled alarm for schedule: $scheduleId user: $userId")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        println("⏰ AlarmReceiver.onReceive called with action: ${intent.action}")
        println("⏰ Current time: ${System.currentTimeMillis()}")
        println("⏰ Intent extras: ${intent.extras}")
        
        if (intent.action == ACTION_REMINDER_ALARM) {
            val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
            val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
            // Check if alarm was dismissed
            val prefs = context.getSharedPreferences("alarm_dismissed_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean(scheduleId + userId, false)) {
                println("⏰ Alarm for $scheduleId/$userId was dismissed, skipping notification and repeat.")
                return
            }
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Activity"
            val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
            val startTime = intent.getLongExtra(EXTRA_START_TIME, 0)
            val repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, 0)
            
            println("⏰ Processing alarm for schedule: $scheduleId, title: $title")
            println("⏰ Start time: $startTime")
            println("⏰ Time until start: ${(startTime - System.currentTimeMillis()) / 1000} seconds")
            
            showAlarmNotification(context, scheduleId, userId, title, location, startTime, repeatCount)
        } else {
            println("⏰ Unknown action received: ${intent.action}")
        }
    }
    
    private fun showAlarmNotification(
        context: Context,
        scheduleId: String,
        userId: String,
        title: String,
        location: String,
        startTime: Long,
        repeatCount: Int
    ) {
        // Check if alarm was dismissed before showing notification
        val prefs = context.getSharedPreferences("alarm_dismissed_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(scheduleId + userId, false)) {
            println("⏰ Alarm for $scheduleId/$userId was dismissed, skipping notification and repeat.")
            return
        }
        println("⏰ Showing alarm notification for: $title")
        
        // Wake the device
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "Temuin:AlarmWakeLock"
        )
        
        try {
            wakeLock.acquire(10 * 60 * 1000L) // 10 minutes
            
            // Play alarm sound
            playAlarmSound(context)
            
            // Vibrate
            startVibration(context)
            
            // Create notification channel
            createNotificationChannel(context)
            
            // Calculate time until start
            val minutesUntil = (startTime - System.currentTimeMillis()) / (1000 * 60)
            val timeText = when {
                minutesUntil <= 0 -> "now"
                minutesUntil <= 60 -> "in $minutesUntil minutes"
                else -> "in ${minutesUntil / 60} hours"
            }
            
            // Format start date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            val startDate = dateFormat.format(Date(startTime))
            val titleNotification = "$title is starting soon!"

            // Build notification content
            val contentText = buildString {
                if (location.isNotEmpty()) append("📍 $location \n")
                append("⌚ $startDate\n")
                append("Starts $timeText")
            }
            
            val requestCode = (scheduleId + userId).hashCode()
            
            // Create dismiss intent
            val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
                action = AlarmDismissReceiver.ACTION_DISMISS_ALARM
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_USER_ID, userId)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create snooze intent
            val snoozeIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
                action = AlarmDismissReceiver.ACTION_SNOOZE_ALARM
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LOCATION, location)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_REPEAT_COUNT, repeatCount)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 2000, // Different request code for snooze
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create full-screen intent for heads-up notification
            val fullScreenIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "SHOW_ALARM_ACTIVITY"
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LOCATION, location)
                putExtra(EXTRA_START_TIME, startTime)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 1000, // Different request code
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create notification builder
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
                .setContentTitle(titleNotification)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.temuin_logo)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)  // Force full-screen
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPendingIntent
                )
                .setOngoing(true)  // Makes notification persistent
                .setAutoCancel(false)  // Prevents auto-dismissal when clicked
                .setOnlyAlertOnce(false)  // Allow repeated alerts
                .setDeleteIntent(dismissPendingIntent)  // Use dismiss intent instead of null
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setLocalOnly(true)  // Prevents syncing to wearables where it might be dismissible
                .setShowWhen(true)  // Show timestamp
                .setUsesChronometer(false)  // Don't use chronometer
                .setTimeoutAfter(0)  // Never timeout
                .setGroupSummary(false)  // Don't group with other notifications
                .setContentIntent(dismissPendingIntent)  // Make entire notification clickable to dismiss
            
            // Add snooze button only if there are remaining repeats
            if (repeatCount + 1 < MAX_REPEAT_COUNT) {
                notificationBuilder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Snooze",
                    snoozePendingIntent
                )
            }
            
            val notification = notificationBuilder.build()
            
            // Add flags to make notification persistent (FIXED: removed dual posting)
                notification.flags = notification.flags or 
                    android.app.Notification.FLAG_NO_CLEAR or  // Cannot be cleared by "Clear All"
                    android.app.Notification.FLAG_ONGOING_EVENT or  // Persistent notification
                    android.app.Notification.FLAG_INSISTENT  // Keep alerting until dismissed
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Post notification with single ID (FIXED: removed dual posting)
                    notificationManager.notify(requestCode, notification)
            println("⏰ Posted notification with ID: $requestCode")
            
            println("⏰ Alarm notification displayed successfully")
            
            // Auto-dismiss after 5 minutes, then repeat if needed
            val handler = android.os.Handler(context.mainLooper)
            handler.postDelayed({
                // Check if alarm was dismissed before proceeding with repeat
                val prefs = context.getSharedPreferences("alarm_dismissed_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean(scheduleId + userId, false)) {
                    println("⏰ Alarm was dismissed, skipping repeat")
                    return@postDelayed
                }
                
                // Dismiss notification and stop alarm
                notificationManager.cancel(requestCode)
                stopAlarmSoundAndVibration()
                
                if (repeatCount + 1 < MAX_REPEAT_COUNT) {
                    // Check again if dismissed before scheduling repeat
                    if (prefs.getBoolean(scheduleId + userId, false)) {
                        println("⏰ Alarm was dismissed during auto-dismiss, not scheduling repeat")
                        return@postDelayed
                    }
                    
                    // Reschedule alarm for 5 minutes later with unique request code
                    val newIntent = Intent(context, AlarmReceiver::class.java).apply {
                        action = ACTION_REMINDER_ALARM
                        putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                        putExtra(EXTRA_USER_ID, userId)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_LOCATION, location)
                        putExtra(EXTRA_START_TIME, startTime)
                        putExtra(EXTRA_REPEAT_COUNT, repeatCount + 1)
                    }
                    val newRequestCode = requestCode + (repeatCount + 1) // Unique request code for each repeat
                    val newPendingIntent = PendingIntent.getBroadcast(
                        context,
                        newRequestCode,
                        newIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    
                    try {
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                                if (alarmManager.canScheduleExactAlarms()) {
                                    alarmManager.setAlarmClock(
                                        AlarmManager.AlarmClockInfo(System.currentTimeMillis() + REPEAT_INTERVAL_MS, newPendingIntent),
                                        newPendingIntent
                                    )
                                } else {
                                    alarmManager.setAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        System.currentTimeMillis() + REPEAT_INTERVAL_MS,
                                        newPendingIntent
                                    )
                                }
                            }
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    System.currentTimeMillis() + REPEAT_INTERVAL_MS,
                                    newPendingIntent
                                )
                            }
                            else -> {
                                alarmManager.setExact(
                                    AlarmManager.RTC_WAKEUP,
                                    System.currentTimeMillis() + REPEAT_INTERVAL_MS,
                                    newPendingIntent
                                )
                            }
                        }
                        println("⏰ Rescheduled alarm reminder for $title, repeat ${repeatCount + 1}")
                    } catch (e: Exception) {
                        println("⏰ Error rescheduling repeat alarm: ${e.message}")
                    }
                } else {
                    println("⏰ Maximum repeat count reached for $title")
                }
            }, 5 * 60 * 1000L) // 5 minutes
            
        } catch (e: Exception) {
            println("⏰ Error showing alarm notification: ${e.message}")
            e.printStackTrace()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    private fun playAlarmSound(context: Context) {
        try {
            activeMediaPlayer?.release()
            activeMediaPlayer = MediaPlayer().apply {
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                
                setDataSource(context, alarmSound)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }
                
                isLooping = true
                prepare()
                start()
                
                println("⏰ Alarm sound started")
            }
            
            // Set reference for dismiss receiver
            AlarmDismissReceiver.setActiveMediaPlayer(activeMediaPlayer)
        } catch (e: Exception) {
            println("⏰ Error playing alarm sound: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun startVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val vibrationPattern = longArrayOf(0, 1000, 1000) // Start immediately, vibrate 1s, pause 1s
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(vibrationPattern, 0) // Repeat indefinitely
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, 0)
            }
            
            println("⏰ Vibration started")
        } catch (e: Exception) {
            println("⏰ Error starting vibration: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "Activity Alarms",
                NotificationManager.IMPORTANCE_MAX  // Changed to MAX for strongest priority
            ).apply {
                description = "Alarm notifications for upcoming activities"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setShowBadge(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                // Additional settings to make notifications persistent and force heads-up
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(false)  // Prevent bubble notifications
                }
                // Force importance to MAX to ensure heads-up display
                importance = NotificationManager.IMPORTANCE_MAX
                
                // Additional channel settings for persistence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Ensure the channel allows heads-up notifications
                    setImportance(NotificationManager.IMPORTANCE_MAX)
                }
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            println("⏰ Alarm notification channel created for API ${Build.VERSION.SDK_INT} with MAX importance")
        } else {
            println("⏰ Notification channel not needed for API ${Build.VERSION.SDK_INT}")
        }
    }
    
    private fun stopAlarmSoundAndVibration() {
        try {
            activeMediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            activeMediaPlayer = null
        } catch (_: Exception) {}
        // Stop vibration
        // (Vibrator cancel logic can be added here if needed)
    }
} 