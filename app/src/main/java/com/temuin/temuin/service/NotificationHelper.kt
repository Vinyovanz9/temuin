package com.temuin.temuin.service

import android.content.Context
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.data.model.ScheduleStatus
import com.temuin.temuin.data.model.ParticipantStatus
import java.text.SimpleDateFormat
import java.util.*

object NotificationHelper {
    
    fun scheduleReminderForSchedule(context: Context, schedule: Schedule) {
        // Only schedule alarms for ACTIVE schedules
        if (schedule.status != ScheduleStatus.ACTIVE) {
            println("📅 Skipping alarm scheduling - schedule not ACTIVE (status: ${schedule.status})")
            return
        }
        
        // Clear any dismissed flags for this schedule first
        clearDismissedFlags(context, schedule.id, schedule.participants + schedule.userId)
        
        // Schedule for creator
        scheduleReminderForUser(context, schedule, schedule.userId)
        
        // Schedule for participants who have ACCEPTED
        schedule.participants.forEach { participantId ->
            val participantStatus = schedule.participantStatus[participantId]
            if (participantStatus == ParticipantStatus.ACCEPTED) {
                scheduleReminderForUser(context, schedule, participantId)
            } else {
                println("📅 Skipping alarm for participant $participantId - status: $participantStatus")
            }
        }
    }
    
    fun cancelReminderForSchedule(context: Context, scheduleId: String, participants: List<String>, creatorId: String) {
        val allUserIds = (participants + creatorId).distinct()
        allUserIds.forEach { userId ->
            cancelReminderForUser(context, scheduleId, userId)
        }
    }
    
    fun scheduleReminderForUser(context: Context, schedule: Schedule, userId: String) {
        // Only schedule if schedule is ACTIVE
        if (schedule.status != ScheduleStatus.ACTIVE) {
            println("📅 Skipping alarm for user $userId - schedule not ACTIVE (status: ${schedule.status})")
            return
        }
        
        // Check if user should get alarm
        val shouldSchedule = when {
            // User is the creator
            schedule.userId == userId -> true
            // User is a participant with ACCEPTED status
            schedule.participants.contains(userId) && 
            schedule.participantStatus[userId] == ParticipantStatus.ACCEPTED -> true
            else -> false
        }
        
        if (!shouldSchedule) {
            println("📅 Skipping alarm for user $userId - not creator or not accepted")
            return
        }
        
        // Clear dismissed flag for this specific user/schedule combination
        clearDismissedFlag(context, schedule.id, userId)
        
        val currentTime = System.currentTimeMillis()
        val reminderTime = schedule.startTime - (schedule.reminderHours * 60 * 60 * 1000)
        
        // Only schedule if reminder time hasn't passed
        if (reminderTime > currentTime) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            val startDate = dateFormat.format(Date(schedule.startTime))
            
            println("📅 Scheduling alarm for ACTIVE schedule: ${schedule.title} for user: $userId")
            println("📅 Cleared dismissed flag for: ${schedule.id + userId}")
            
            AlarmReceiver.scheduleReminder(
                context = context,
                scheduleId = schedule.id,
                userId = userId,
                title = schedule.title,
                location = schedule.location,
                startTime = schedule.startTime,
                reminderHours = schedule.reminderHours
            )
        } else {
            println("📅 Reminder time has passed for schedule: ${schedule.title}")
        }
    }
    
    fun cancelReminderForUser(context: Context, scheduleId: String, userId: String) {
        AlarmReceiver.cancelReminder(context, scheduleId, userId)
    }
    
    fun rescheduleReminderForSchedule(context: Context, schedule: Schedule) {
        // Cancel existing reminder first
        cancelReminderForSchedule(context, schedule.id, schedule.participants, schedule.userId)
        
        // Clear dismissed flags for all users involved in this schedule
        clearDismissedFlags(context, schedule.id, schedule.participants + schedule.userId)
        
        // Schedule new reminder only if ACTIVE
        if (schedule.status == ScheduleStatus.ACTIVE) {
            println("📅 Rescheduling alarms and clearing dismissed flags for: ${schedule.title}")
            scheduleReminderForSchedule(context, schedule)
        } else {
            println("📅 Not rescheduling - schedule not ACTIVE (status: ${schedule.status})")
        }
    }
    
    /**
     * Clear dismissed flag for a specific user and schedule combination
     */
    fun clearDismissedFlag(context: Context, scheduleId: String, userId: String) {
        try {
            val prefs = context.getSharedPreferences("alarm_dismissed_prefs", Context.MODE_PRIVATE)
            val key = scheduleId + userId
            if (prefs.contains(key)) {
                prefs.edit().remove(key).apply()
                println("📅 Cleared dismissed flag for: $key")
            }
        } catch (e: Exception) {
            println("📅 Error clearing dismissed flag: ${e.message}")
        }
    }
    
    /**
     * Clear dismissed flags for multiple users for a specific schedule
     */
    fun clearDismissedFlags(context: Context, scheduleId: String, userIds: List<String>) {
        try {
            val prefs = context.getSharedPreferences("alarm_dismissed_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var clearedCount = 0
            
            userIds.forEach { userId ->
                val key = scheduleId + userId
                if (prefs.contains(key)) {
                    editor.remove(key)
                    clearedCount++
                }
            }
            
            if (clearedCount > 0) {
                editor.apply()
                println("📅 Cleared $clearedCount dismissed flags for schedule: $scheduleId")
            }
        } catch (e: Exception) {
            println("📅 Error clearing dismissed flags: ${e.message}")
        }
    }
} 