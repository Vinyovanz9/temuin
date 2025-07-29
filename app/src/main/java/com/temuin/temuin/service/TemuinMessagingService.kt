package com.temuin.temuin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.temuin.temuin.MainActivity
import com.temuin.temuin.R
import java.text.SimpleDateFormat
import java.util.*

class TemuinMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID_CHAT = "chat_notifications"
        private const val CHANNEL_ID_SCHEDULE = "schedule_notifications"
        private const val NOTIFICATION_ID_CHAT_BASE = 1000
        private const val NOTIFICATION_ID_SCHEDULE_BASE = 2000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        getFCMToken()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        println("📱 FCM Message received: ${remoteMessage.data}")
        
        val data = remoteMessage.data
        when (data["type"]) {
            "chat" -> {
                val isGroupChat = data["isGroupChat"] == "true"
                if (isGroupChat) {
                    handleGroupChatNotification(data)
                } else {
                    handlePrivateChatNotification(data)
                }
            }
            "schedule_invite" -> handleScheduleInviteNotification(data)
            "schedule_update" -> handleScheduleUpdateNotification(data)
            "schedule_cancelled" -> handleScheduleCancelledNotification(data)
            "schedule_reminder" -> handleScheduleReminderNotification(data)
            else -> {
                println("📱 Unknown notification type: ${data["type"]}")
                // Handle fallback notification
                remoteMessage.notification?.let { notification ->
                    showFallbackNotification(notification.title ?: "Temuin", notification.body ?: "")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("📱 New FCM token received: $token")
        saveFCMToken(token)
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    println("📱 FCM token retrieved: $token")
                    saveFCMToken(token)
                } else {
                    println("📱 Failed to get FCM token: ${task.exception?.message}")
                }
            }
    }

    private fun saveFCMToken(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseDatabase.getInstance()
                .reference
                .child("users")
                .child(currentUser.uid)
                .child("fcmToken")
                .setValue(token)
                .addOnSuccessListener {
                    println("📱 FCM token saved successfully")
                }
                .addOnFailureListener { e ->
                    println("📱 Failed to save FCM token: ${e.message}")
                }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHAT,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setShowBadge(true)
                lightColor = Color.BLUE
                enableLights(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }

            val scheduleChannel = NotificationChannel(
                CHANNEL_ID_SCHEDULE,
                "Schedule Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for schedule invitations and updates"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setShowBadge(true)
                lightColor = Color.GREEN
                enableLights(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            
            notificationManager.createNotificationChannels(listOf(chatChannel, scheduleChannel))
            println("📱 Notification channels created for API ${Build.VERSION.SDK_INT}")
        } else {
            println("📱 Notification channels not needed for API ${Build.VERSION.SDK_INT}")
        }
    }

    private fun handlePrivateChatNotification(data: Map<String, String>) {
        println("📱 Handling private chat notification: $data")
        
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "Someone"
        val message = data["message"] ?: return
        val senderPhone = data["senderPhone"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "private_chat")
            putExtra("recipientId", senderId)
            putExtra("recipientName", senderName)
            putExtra("recipientPhone", senderPhone)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, senderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAT)
            .setContentTitle(senderName)
            .setContentText(message)
            .setSmallIcon(R.drawable.temuin_logo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_CHAT_BASE + senderId.hashCode(), notification)
        
        println("📱 Private chat notification displayed for $senderName")
    }

    private fun handleGroupChatNotification(data: Map<String, String>) {
        println("📱 Handling group chat notification: $data")
        
        val groupId = data["chatId"] ?: data["groupId"] ?: return
        val groupName = data["groupName"] ?: "Group Chat"
        val senderName = data["senderName"] ?: "Someone"
        val message = data["message"] ?: return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "group_chat")
            putExtra("groupId", groupId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, groupId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = "$senderName: $message"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAT)
            .setContentTitle(groupName)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.temuin_logo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_CHAT_BASE + groupId.hashCode(), notification)
        
        println("📱 Group chat notification displayed for $groupName")
    }

    private fun handleScheduleInviteNotification(data: Map<String, String>) {
        println("📱 Handling schedule invite notification: $data")
        
        val scheduleId = data["scheduleId"] ?: return
        val title = data["title"] ?: "Activity"
        val inviterName = data["creatorName"] ?: "Someone"
        val location = data["location"] ?: ""
        val startTime = data["startTime"] ?: ""

        val contentText = buildString {
            if (location.isNotEmpty()) append("📍 $location ")
            if (startTime.isNotEmpty()) {
                val formattedTime = formatDateTime(startTime)
                append("\n⌚ $formattedTime")
            }
        }

        showScheduleNotification(
            scheduleId,
            "$inviterName invited you to $title:",
            contentText
        )
    }

    private fun handleScheduleUpdateNotification(data: Map<String, String>) {
        println("📱 Handling schedule update notification: $data")
        
        val scheduleId = data["scheduleId"] ?: return
        val titleBefore = data["titleBefore"] ?: "Activity"
        val title = data["title"] ?: "Activity"
        val updaterName = data["updaterName"] ?: "Someone"
        val locationBefore = data["locationBefore"] ?: ""
        val location = data["location"] ?: ""
        val startTimeBefore = data["startTimeBefore"] ?: ""
        val startTime = data["startTime"] ?: ""
        val updateType = data["updateType"] ?: ""

        var contentText = buildString {}
        when (updateType){
            "location_changed" -> {
                contentText = buildString {
                    if (title == titleBefore) null
                    else append("🗓️ $titleBefore -> $title \n")

                    if (location.isNotEmpty()){
                        append("📍 $locationBefore -> $location")
                    }
                }
            }

            "rescheduled" -> {
                contentText = buildString {
                    if (title == titleBefore) null
                    else append("🗓️ $titleBefore -> $title \n")

                    if (startTime.isNotEmpty()) {
                        val formattedTimeBfr = formatDateTime(startTimeBefore)
                        val formattedTime = formatDateTime(startTime)
                        append("⌚ $formattedTimeBfr -> $formattedTime")
                    }
                }
            }

            "updated" -> {
                contentText = buildString {
                    if (title == titleBefore) null
                    else append("🗓️ $titleBefore -> $title \n")

                    if (location == locationBefore) append("📍 $location \n")
                    else append("📍 $locationBefore -> $location \n")

                    if (startTime == startTimeBefore) {
                        val formattedTime = formatDateTime(startTime)
                        append("⌚ $formattedTime")
                    } else {
                        val formattedTimeBfr = formatDateTime(startTimeBefore)
                        val formattedTime = formatDateTime(startTime)
                        append("From: $formattedTimeBfr \n")
                        append("To: $formattedTime")
                    }
                }
            }

            else -> {
                contentText = buildString {
                    if (location.isNotEmpty()) append("📍 $location \n")
                    if (location.isNotEmpty()){
                        val formattedTime = formatDateTime(startTime)
                        append("⌚ $formattedTime")
                    }
                }
            }
        }

        showScheduleNotification(
            scheduleId,
            when (updateType) {
                "active" -> "$title is active"
                "ongoing" -> "$title is ongoing"
                "completed" -> "$title is completed"
                "rescheduled" -> "$updaterName rescheduled $title:"
                "location_changed" -> "$updaterName changed location $title:"
                else -> "$updaterName updated $titleBefore:"
            },
            contentText
        )
    }

    private fun handleScheduleCancelledNotification(data: Map<String, String>) {
        println("📱 Handling schedule cancelled notification: $data")
        
        val scheduleId = data["scheduleId"] ?: return
        val title = data["title"] ?: "Activity"
        val cancellerName = data["updaterName"] ?: "Someone"
        val location = data["location"] ?: ""
        val startTime = data["startTime"] ?: ""
        val reason = data["reason"] ?: ""

        val contentText = buildString {
            if (location.isNotEmpty()) append("\n📍 $location")
            if (startTime.isNotEmpty()) {
                val formattedTime = formatDateTime(startTime)
                append("\n⌚ $formattedTime")
            }
            if (reason.isNotEmpty()) append("\nReason: $reason")
        }

        showScheduleNotification(
            scheduleId,
            "$cancellerName cancelled $title",
            contentText
        )
    }

    private fun handleScheduleReminderNotification(data: Map<String, String>) {
        println("📱 Handling schedule reminder notification: $data")
        
        val scheduleId = data["scheduleId"] ?: return
        val title = data["title"] ?: "Activity"
        val reminderText = data["reminderText"] ?: ""

        val contentText = buildString {
            append("Reminder: $title")
            if (reminderText.isNotEmpty()) append("\n$reminderText")
        }

        showScheduleNotification(
            scheduleId,
            "Activity Reminder",
            contentText
        )
    }

    private fun showScheduleNotification(
        scheduleId: String,
        notificationTitle: String,
        contentText: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (notificationTitle.contains("location_changed") || 
                notificationTitle.contains("rescheduled") || 
                notificationTitle.contains("invited") ||
                notificationTitle.contains("updated") ||
                notificationTitle.contains("cancelled")){
                putExtra("navigate_to", "notifications")
                putExtra("scheduleId", scheduleId)
            } else {
                putExtra("navigate_to", "main")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, scheduleId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SCHEDULE)
            .setContentTitle(notificationTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.temuin_logo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_SCHEDULE_BASE + scheduleId.hashCode(), notification)
        
        println("📱 Schedule notification displayed: $notificationTitle")
    }

    private fun showFallbackNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SCHEDULE)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.temuin_logo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        
        println("📱 Fallback notification displayed: $title")
    }

    private fun formatDateTime(timestamp: String): String {
        return try {
            val date = Date(timestamp.toLong())
            val formatter = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            formatter.format(date)
        } catch (e: Exception) {
            timestamp
        }
    }
} 