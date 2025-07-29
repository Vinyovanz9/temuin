package com.temuin.temuin.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Vibrator

class AlarmDismissReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_DISMISS_ALARM = "DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "SNOOZE_ALARM"
        private var activeMediaPlayer: MediaPlayer? = null
        
        fun setActiveMediaPlayer(mediaPlayer: MediaPlayer?) {
            activeMediaPlayer = mediaPlayer
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS_ALARM -> handleDismissAlarm(context, intent)
            ACTION_SNOOZE_ALARM -> handleSnoozeAlarm(context, intent)
        }
    }
    
    private fun handleDismissAlarm(context: Context, intent: Intent) {
            val scheduleId = intent.getStringExtra(AlarmReceiver.EXTRA_SCHEDULE_ID)
            val userId = intent.getStringExtra(AlarmReceiver.EXTRA_USER_ID)
            
            println("⏰ Dismissing alarm for schedule: $scheduleId, user: $userId")
            
            // Stop vibration
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
            println("⏰ Vibration stopped")
            
            // Stop alarm sound
            activeMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                    println("⏰ Alarm sound stopped")
                }
                release()
                activeMediaPlayer = null
                println("⏰ MediaPlayer released")
            }
            
            // Cancel notification
            if (scheduleId != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val requestCode = (scheduleId + userId).hashCode()
                
            // Cancel notification
                try {
                    notificationManager.cancel(requestCode)
                    println("⏰ Cancelled notification with ID: $requestCode")
                    
                    // For extra safety, try cancelling a range of possible IDs
                    for (i in 0..2) {
                        notificationManager.cancel(requestCode + i)
                    }
                    
                    // Force clear all notifications from our app (nuclear option for API 29 issues)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            // Get all active notifications and cancel any that match our pattern
                            val activeNotifications = notificationManager.activeNotifications
                            for (notification in activeNotifications) {
                                val notificationId = notification.id
                                // Cancel if it's in our range of possible IDs
                                if (notificationId == requestCode || 
                                (notificationId >= requestCode && notificationId <= requestCode + 10)) {
                                    notificationManager.cancel(notificationId)
                                    println("⏰ Force cancelled active notification with ID: $notificationId")
                                }
                            }
                        } catch (e: Exception) {
                            println("⏰ Could not access active notifications: ${e.message}")
                        }
                    }
                    
                    println("⏰ All notification cancellation attempts completed")
                    
                    // Double-check by trying to cancel again after a short delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        notificationManager.cancel(requestCode)
                        println("⏰ Double-check notification cancellation completed")
                    }, 100)
                } catch (e: Exception) {
                    println("⏰ Error cancelling notification: ${e.message}")
                }
            }
            
            // Set dismissed flag in SharedPreferences to prevent repeats
            if (scheduleId != null && userId != null) {
                val prefs = context.getSharedPreferences("alarm_dismissed_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean(scheduleId + userId, true).apply()
                println("⏰ Dismissed flag set in SharedPreferences")
                
                // Cancel all pending repeat alarms for this schedule/user combination
                cancelAllPendingRepeats(context, scheduleId, userId)
            }
    }
    
    private fun handleSnoozeAlarm(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(AlarmReceiver.EXTRA_SCHEDULE_ID) ?: return
        val userId = intent.getStringExtra(AlarmReceiver.EXTRA_USER_ID) ?: return
        val title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "Activity"
        val location = intent.getStringExtra(AlarmReceiver.EXTRA_LOCATION) ?: ""
        val startTime = intent.getLongExtra(AlarmReceiver.EXTRA_START_TIME, 0)
        val currentRepeatCount = intent.getIntExtra(AlarmReceiver.EXTRA_REPEAT_COUNT, 0)
        
        println("⏰ Snoozing alarm for schedule: $scheduleId, user: $userId, current repeat: $currentRepeatCount")
        
        // Stop current alarm (vibration, sound, notification)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
        println("⏰ Vibration stopped for snooze")
        
        // Stop alarm sound
        activeMediaPlayer?.apply {
            if (isPlaying) {
                stop()
                println("⏰ Alarm sound stopped for snooze")
            }
            release()
            activeMediaPlayer = null
            println("⏰ MediaPlayer released for snooze")
        }
        
        // Cancel current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val requestCode = (scheduleId + userId).hashCode()
        notificationManager.cancel(requestCode)
        println("⏰ Current notification cancelled for snooze")
        
        // Check if we can schedule another repeat
        if (currentRepeatCount + 1 < AlarmReceiver.MAX_REPEAT_COUNT) {
            // Schedule the next repeat immediately (snooze means skip the 5-minute wait)
            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_REMINDER_ALARM
                putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(AlarmReceiver.EXTRA_USER_ID, userId)
                putExtra(AlarmReceiver.EXTRA_TITLE, title)
                putExtra(AlarmReceiver.EXTRA_LOCATION, location)
                putExtra(AlarmReceiver.EXTRA_START_TIME, startTime)
                putExtra(AlarmReceiver.EXTRA_REPEAT_COUNT, currentRepeatCount + 1)
            }
            
            val snoozeRequestCode = requestCode + (currentRepeatCount + 1) // Unique request code for snooze repeat
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                snoozeRequestCode,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            try {
                // Schedule the snooze alarm for 5 minutes from now
                val snoozeTime = System.currentTimeMillis() + AlarmReceiver.REPEAT_INTERVAL_MS
                
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setAlarmClock(
                                AlarmManager.AlarmClockInfo(snoozeTime, snoozePendingIntent),
                                snoozePendingIntent
                            )
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                snoozeTime,
                                snoozePendingIntent
                            )
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            snoozeTime,
                            snoozePendingIntent
                        )
                    }
                    else -> {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            snoozeTime,
                            snoozePendingIntent
                        )
                    }
                }
                
                println("⏰ Snoozed alarm scheduled for $title, repeat ${currentRepeatCount + 1} in 5 minutes")
            } catch (e: Exception) {
                println("⏰ Error scheduling snooze alarm: ${e.message}")
            }
        } else {
            println("⏰ Cannot snooze - maximum repeat count would be exceeded")
        }
    }
    
    private fun cancelAllPendingRepeats(context: Context, scheduleId: String, userId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val requestCode = (scheduleId + userId).hashCode()
            
            println("⏰ Cancelling all pending repeats for schedule: $scheduleId, user: $userId")
            println("⏰ Base request code: $requestCode")
            
            // Cancel all possible repeat alarms (up to MAX_REPEAT_COUNT)
            for (repeatCount in 0 until AlarmReceiver.MAX_REPEAT_COUNT) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_REMINDER_ALARM
                    putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(AlarmReceiver.EXTRA_USER_ID, userId)
                    putExtra(AlarmReceiver.EXTRA_REPEAT_COUNT, repeatCount)
                }
                
                // Cancel with original request code pattern
                val originalPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + repeatCount, // Different request code for each repeat
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(originalPendingIntent)
                
                // Also cancel with persistent request code pattern (for API 29+)
                val persistentPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (requestCode + 10000) + repeatCount, // Persistent pattern
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(persistentPendingIntent)
                
                println("⏰ Cancelled repeat alarm $repeatCount for schedule: $scheduleId (both original and persistent)")
            }
            
            // Also cancel the original alarm with both patterns
            AlarmReceiver.cancelReminder(context, scheduleId, userId)
            
            // Additional cleanup - cancel any alarms that might have been created with different patterns
            for (i in 0..10) {
                val cleanupIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_REMINDER_ALARM
                    putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(AlarmReceiver.EXTRA_USER_ID, userId)
                }
                
                val cleanupPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + i,
                    cleanupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(cleanupPendingIntent)
                
                val persistentCleanupPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (requestCode + 10000) + i,
                    cleanupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(persistentCleanupPendingIntent)
            }
            
            println("⏰ All pending alarms cancelled for schedule: $scheduleId, user: $userId")
            
        } catch (e: Exception) {
            println("⏰ Error cancelling pending repeats: ${e.message}")
            e.printStackTrace()
        }
    }
} 