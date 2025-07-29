package com.temuin.temuin

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import com.temuin.temuin.navigation.MainNavigation
import com.temuin.temuin.navigation.NavigationData
import com.temuin.temuin.ui.theme.TemuinTheme
import com.temuin.temuin.data.repository.ChatRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var chatRepositoryImpl: ChatRepositoryImpl

    private var navigationData by mutableStateOf<NavigationData?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            // Show some UI to explain why permissions are needed
            println("⚠️ Some permissions were denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        println("📱 MainActivity onCreate")
        
        checkAndRequestPermissions()
        
        // Get initial navigation data from intent
        navigationData = getNavigationDataFromIntent(intent)
        println("📱 Initial navigation data: $navigationData")
        
        setContent {
            TemuinTheme {
                MainNavigation(
                    initialNavigationData = navigationData
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        println("📱 MainActivity onNewIntent: ${intent?.extras}")
        
        // Handle navigation when app is already running
        intent?.let { 
            val newNavigationData = getNavigationDataFromIntent(it)
            println("📱 New navigation data: $newNavigationData")
            
            if (newNavigationData != null) {
                navigationData = newNavigationData
                // The navigation will be handled by the LaunchedEffect in MainNavigation
            }
        }
    }

    private fun getNavigationDataFromIntent(intent: Intent?): NavigationData? {
        return intent?.let {
            val navigateTo = it.getStringExtra("navigate_to")
            println("📱 Navigate to: $navigateTo")
            println("📱 Intent extras: ${it.extras}")
            
            when (navigateTo) {
                "private_chat" -> {
                    val recipientId = it.getStringExtra("recipientId")
                    val recipientName = it.getStringExtra("recipientName")
                    val recipientPhone = it.getStringExtra("recipientPhone") ?: ""
                    
                    println("📱 Private chat navigation: id=$recipientId, name=$recipientName, phone=$recipientPhone")
                    
                    if (recipientId != null && recipientName != null) {
                        NavigationData.PrivateChat(recipientId, recipientName, recipientPhone)
                    } else {
                        println("📱 Missing required data for private chat navigation")
                        null
                    }
                }
                "group_chat" -> {
                    val groupId = it.getStringExtra("groupId")
                    println("📱 Group chat navigation: groupId=$groupId")
                    
                    if (groupId != null) {
                        NavigationData.GroupChat(groupId)
                    } else {
                        println("📱 Missing groupId for group chat navigation")
                        null
                    }
                }
                "notifications" -> {
                    val scheduleId = it.getStringExtra("scheduleId")
                    println("📱 Notifications navigation: scheduleId=$scheduleId")
                    NavigationData.Notifications(scheduleId)
                }
                else -> {
                    println("📱 Unknown navigation type: $navigateTo")
                    null
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        chatRepositoryImpl.updateLastSeen()
        
        // Check if we have a new intent to handle
        intent?.let { 
            val newNavigationData = getNavigationDataFromIntent(it)
            if (newNavigationData != null && newNavigationData != navigationData) {
                println("📱 Updating navigation data on resume: $newNavigationData")
                navigationData = newNavigationData
            }
        }
    }

    override fun onPause() {
        super.onPause()
        chatRepositoryImpl.updateLastSeen()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                println("📱 POST_NOTIFICATIONS permission needed")
            }
        }

        // Check alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasExactAlarmPermission()) {
                println("📱 Exact alarm permission needed - opening settings")
                // Open system settings for exact alarm permission
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    println("📱 Error opening exact alarm settings: ${e.message}")
                }
            }
        }

        // Check battery optimization
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            println("📱 Battery optimization permission needed")
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                println("📱 Error opening battery optimization settings: ${e.message}")
            }
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            println("📱 Requesting permissions: $permissionsToRequest")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Ensure notifications are enabled in system settings
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            println("📱 Notifications disabled in system settings - opening settings")
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            } catch (e: Exception) {
                println("📱 Error opening notification settings: ${e.message}")
            }
        }
    }

    private fun hasExactAlarmPermission(): Boolean {
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}