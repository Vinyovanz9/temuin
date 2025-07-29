package com.temuin.temuin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.temuin.temuin.data.model.Schedule
import com.temuin.temuin.data.model.ScheduleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BootReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == ACTION_QUICKBOOT_POWERON ||
            intent.action == ACTION_HTC_QUICKBOOT_POWERON) {
            
            restoreScheduleReminders(context)
        }
    }

    private fun restoreScheduleReminders(context: Context) {
        val currentUser = auth.currentUser ?: return
        
        scope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                
                // Get all active schedules for the current user
                val snapshot = firestore.collection("schedules")
                    .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
                    .whereGreaterThan("startTime", currentTime)
                    .get()
                    .await()
                
                for (document in snapshot.documents) {
                    try {
                        val schedule = document.toObject(Schedule::class.java)?.copy(id = document.id)
                        if (schedule != null && 
                            (schedule.userId == currentUser.uid || schedule.participants.contains(currentUser.uid))) {
                            
                            // Reschedule reminder for this schedule
                            NotificationHelper.scheduleReminderForSchedule(context, schedule)
                        }
                    } catch (e: Exception) {
                        // Skip this schedule if there's an error
                    }
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }
} 