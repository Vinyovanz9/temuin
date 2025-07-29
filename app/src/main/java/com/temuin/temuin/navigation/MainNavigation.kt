package com.temuin.temuin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.temuin.temuin.ui.screens.splash.SplashScreen
import com.temuin.temuin.ui.screens.auth.PhoneAuthScreen
import com.temuin.temuin.ui.screens.auth.ProfileSetupScreen
import com.temuin.temuin.ui.screens.profile.EditProfileScreen
import com.temuin.temuin.ui.screens.friends.FriendsScreen
import com.temuin.temuin.ui.screens.schedules.SchedulesScreen
import com.temuin.temuin.ui.screens.profile.ProfilePhotoScreen
import android.graphics.Bitmap
import androidx.compose.foundation.background
import com.temuin.temuin.ui.screens.chat.ChatScreen
import com.temuin.temuin.ui.screens.chats.ChatListScreen
import com.temuin.temuin.ui.screens.schedules.ScheduleDetailScreen
import com.temuin.temuin.ui.screens.schedules.EditScheduleScreen
import com.temuin.temuin.ui.screens.profile.ViewProfileScreen
import com.temuin.temuin.ui.screens.groupchat.GroupChatScreen
import com.temuin.temuin.ui.screens.groupchat.ViewGroupProfileScreen
import com.temuin.temuin.ui.screens.notifications.NotificationsScreen
import com.temuin.temuin.ui.screens.schedules.SchedulesViewModel
import com.temuin.temuin.ui.screens.chats.ChatListViewModel
import com.temuin.temuin.ui.screens.profile.StatusScreen
import com.temuin.temuin.data.model.NotificationStatus
import com.temuin.temuin.ui.screens.auth.TermsScreen
import com.temuin.temuin.ui.screens.auth.PrivacyPolicyScreen
import com.temuin.temuin.ui.screens.auth.TermsOfServiceScreen
import com.temuin.temuin.ui.screens.settings.SettingsScreen
import com.temuin.temuin.ui.screens.settings.SecurityNotificationScreen
import com.temuin.temuin.ui.screens.settings.DeleteAccountScreen

// Navigation data for handling notification navigation
sealed class NavigationData {
    data class PrivateChat(val recipientId: String, val recipientName: String, val recipientPhone: String) : NavigationData()
    data class GroupChat(val groupId: String) : NavigationData()
    data class Notifications(val scheduleId: String?) : NavigationData()
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Terms : Screen("terms")
    object PrivacyPolicy : Screen("privacy_policy")
    object TermsOfService : Screen("terms_of_service")
    object Auth : Screen("auth")
    object ProfileSetup : Screen("profile_setup")
    object Main : Screen("main")
    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")
    object SecurityNotification : Screen("security_notification")
    object DeleteAccount : Screen("delete_account")
    object ProfilePhoto : Screen("profile_photo")
    object Notifications : Screen("notifications")
    object ViewProfile : Screen("view_profile/{userId}") {
        fun createRoute(userId: String) = "view_profile/$userId"
    }
    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }
    object ViewGroupProfile : Screen("view_group_profile/{groupId}") {
        fun createRoute(groupId: String) = "view_group_profile/$groupId"
    }
    object Status : Screen("status")
    object Chat : Screen("chat/{userId}/{name}/{phoneNumber}") {
        fun createRoute(userId: String, name: String, phoneNumber: String) = "chat/$userId/$name/$phoneNumber"
    }
    object EditSchedule : Screen("schedule_edit?scheduleId={scheduleId}") {
        fun createRoute(scheduleId: String? = null) = if (scheduleId != null) "schedule_edit?scheduleId=$scheduleId" else "schedule_edit?scheduleId="
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    initialNavigationData: NavigationData? = null
) {
    val navController = rememberNavController()
    var currentProfileImage by remember { mutableStateOf<Bitmap?>(null) }
    
    // Handle initial navigation from notification
    LaunchedEffect(initialNavigationData) {
        initialNavigationData?.let { navigationData ->
            println("📱 MainNavigation handling navigation data: $navigationData")
            
            when (navigationData) {
                is NavigationData.PrivateChat -> {
                    val route = Screen.Chat.createRoute(
                        navigationData.recipientId,
                        navigationData.recipientName,
                        navigationData.recipientPhone
                    )
                    println("📱 Navigating to private chat: $route")
                    
                    navController.navigate(route) {
                        popUpTo(Screen.Main.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is NavigationData.GroupChat -> {
                    println("📱 Navigating to main screen first, then group chat will be handled by MainContent")
                    
                    // Navigate to main screen first, the group chat navigation will be handled by MainContent
                    navController.navigate(Screen.Main.route) {
                        popUpTo(navController.graph.id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is NavigationData.Notifications -> {
                    println("📱 Navigating to main screen first, then notifications will be handled by MainContent")
                    
                    // Navigate to main screen first, the notifications navigation will be handled by MainContent
                    navController.navigate(Screen.Main.route) {
                        popUpTo(navController.graph.id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToProfileSetup = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToTerms = {
                    navController.navigate(Screen.Terms.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Terms.route) {
            TermsScreen(
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Terms.route) { inclusive = true }
                    }
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(Screen.PrivacyPolicy.route)
                },
                onNavigateToTermsOfService = {
                    navController.navigate(Screen.TermsOfService.route)
                }
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TermsOfService.route) {
            TermsOfServiceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Auth.route) {
            PhoneAuthScreen(
                onNavigateToProfile = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(
                onNavigateToSchedule = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                },
                onViewFullPhoto = { bitmap ->
                    currentProfileImage = bitmap
                    navController.navigate(Screen.ProfilePhoto.route)
                }
            )
        }

        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewFullPhoto = { bitmap ->
                    currentProfileImage = bitmap
                    navController.navigate(Screen.ProfilePhoto.route)
                },
                onNavigateToStatus = {
                    navController.navigate(Screen.Status.route)
                }
            )
        }

        composable(Screen.ProfilePhoto.route) {
            currentProfileImage?.let { bitmap ->
                ProfilePhotoScreen(
                    bitmap = bitmap,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTerms = {
                    navController.navigate(Screen.TermsOfService.route)
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(Screen.PrivacyPolicy.route)
                },
                onNavigateToSecurity = {
                    navController.navigate(Screen.SecurityNotification.route)
                },
                onNavigateToDeleteAccount = {
                    navController.navigate(Screen.DeleteAccount.route)
                }
            )
        }

        composable(Screen.SecurityNotification.route) {
            SecurityNotificationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DeleteAccount.route) {
            DeleteAccountScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainContent(
                onNavigateToProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onViewProfile = { userId ->
                    navController.navigate(Screen.ViewProfile.createRoute(userId))
                },
                onNavigateToChat = { userId, name, phoneNumber ->
                    navController.navigate(Screen.Chat.createRoute(userId, name, phoneNumber))
                },
                onViewFullPhoto = { bitmap ->
                    currentProfileImage = bitmap
                    navController.navigate(Screen.ProfilePhoto.route)
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                },
                initialNavigationData = initialNavigationData
            )
        }

        composable(
            route = "schedule_detail/{scheduleId}",
            arguments = listOf(
                navArgument("scheduleId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: return@composable
            ScheduleDetailScreen(
                scheduleId = scheduleId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditSchedule.createRoute(id))
                },
                onNavigateToProfile = { userId ->
                    navController.navigate(Screen.ViewProfile.createRoute(userId))
                }
            )
        }

        composable(
            route = Screen.EditSchedule.route,
            arguments = listOf(
                navArgument("scheduleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getString("scheduleId")?.takeIf { it.isNotEmpty() }
            EditScheduleScreen(
                scheduleId = scheduleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ViewProfile.route,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            ViewProfileScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onViewFullPhoto = { bitmap ->
                    currentProfileImage = bitmap
                    navController.navigate(Screen.ProfilePhoto.route)
                },
                onNavigateToChat = { userId, name, phoneNumber ->
                    navController.navigate(Screen.Chat.createRoute(userId, name, phoneNumber))
                }
            )
        }

        composable(Screen.Status.route) {
            StatusScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
                navArgument("phoneNumber") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            val name = backStackEntry.arguments?.getString("name") ?: return@composable

            ChatScreen(
                recipientId = userId,
                recipientName = name,
                onNavigateBack = { navController.popBackStack() },
                onViewProfile = { userId ->
                    navController.navigate(Screen.ViewProfile.createRoute(userId))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onViewProfile: (String) -> Unit,
    onNavigateToChat: (String, String, String) -> Unit,
    onViewFullPhoto: (Bitmap) -> Unit,
    onLogout: () -> Unit,
    initialNavigationData: NavigationData? = null,
    chatListViewModel: ChatListViewModel = hiltViewModel(),
    schedulesViewModel: SchedulesViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val totalUnreadCount by chatListViewModel.totalUnreadCount.collectAsState()
    val notifications by schedulesViewModel.notifications.collectAsState()
    val pendingScheduleNotifications = notifications.count { it.status == NotificationStatus.PENDING }
    
    // Handle navigation from notifications within the nested navigation
    LaunchedEffect(initialNavigationData) {
        initialNavigationData?.let { navigationData ->
            println("📱 MainContent handling navigation data: $navigationData")
            
            when (navigationData) {
                is NavigationData.GroupChat -> {
                    val route = Screen.GroupChat.createRoute(navigationData.groupId)
                    println("📱 Navigating to group chat in nested nav: $route")
                    
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is NavigationData.Notifications -> {
                    println("📱 Navigating to notifications in nested nav")
                    
                    navController.navigate(Screen.Notifications.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                else -> {
                    // Private chat is handled at the main navigation level
                    println("📱 Navigation data not handled in MainContent: $navigationData")
                }
            }
        }
    }
    
    val showBottomBar = when (currentRoute) {
        BottomNavItem.Schedules.route,
        BottomNavItem.Chats.route,
        BottomNavItem.Friends.route -> true
        else -> false
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigation(
                    onClick = { index ->
                        val route = when (index) {
                            0 -> BottomNavItem.Schedules.route
                            1 -> BottomNavItem.Chats.route
                            2 -> BottomNavItem.Friends.route
                            else -> return@BottomNavigation
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    selectedItem = when (currentRoute) {
                        BottomNavItem.Schedules.route -> 0
                        BottomNavItem.Chats.route -> 1
                        BottomNavItem.Friends.route -> 2
                        else -> 0
                    },
                    totalUnreadCount = totalUnreadCount,
                    pendingScheduleNotifications = pendingScheduleNotifications
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Schedules.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(BottomNavItem.Schedules.route) {
                SchedulesScreen(
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onNavigateToEditSchedule = { scheduleId ->
                        navController.navigate(Screen.EditSchedule.createRoute(scheduleId))
                    },
                    onLogout = onLogout,
                    onScheduleClick = { scheduleId ->
                        navController.navigate("schedule_detail/$scheduleId?source=upcoming")
                    }
                )
            }

            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onScheduleClick = { scheduleId ->
                        navController.navigate("schedule_detail/$scheduleId?source=notifications")
                    }
                )
            }

            composable(
                route = "schedule_detail/{scheduleId}?source={source}",
                arguments = listOf(
                    navArgument("scheduleId") { type = NavType.StringType },
                    navArgument("source") { 
                        type = NavType.StringType
                        defaultValue = "upcoming"
                    }
                )
            ) { backStackEntry ->
                val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: return@composable
                val source = backStackEntry.arguments?.getString("source") ?: "upcoming"
                val viewModel: SchedulesViewModel = hiltViewModel()
                val currentUserId = viewModel.getCurrentUserId()
                
                ScheduleDetailScreen(
                    scheduleId = scheduleId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id ->
                        navController.navigate(Screen.EditSchedule.createRoute(id))
                    },
                    onNavigateToProfile = onViewProfile,
                    source = source,
                    currentUserId = currentUserId,
                    onAcceptInvite = { id ->
                        viewModel.acceptScheduleInvite(id)
                        navController.popBackStack()
                    },
                    onDeclineInvite = { id ->
                        viewModel.declineScheduleInvite(id)
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.EditSchedule.route,
                arguments = listOf(
                    navArgument("scheduleId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val scheduleId = backStackEntry.arguments?.getString("scheduleId")?.takeIf { it.isNotEmpty() }
                EditScheduleScreen(
                    scheduleId = scheduleId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(BottomNavItem.Chats.route) {
                ChatListScreen(
                    onChatClick = onNavigateToChat,
                    onViewProfile = onViewProfile,
                    onGroupChatClick = { groupId ->
                        navController.navigate(Screen.GroupChat.createRoute(groupId))
                    },
                    onViewGroupProfile = { groupId ->
                        navController.navigate(Screen.ViewGroupProfile.createRoute(groupId))
                    }
                )
            }

            composable(BottomNavItem.Friends.route) {
                FriendsScreen(
                    onNavigateToChat = onNavigateToChat,
                    onViewProfile = onViewProfile
                )
            }

            composable(
                route = Screen.GroupChat.route,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                GroupChatScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onViewGroupProfile = { id ->
                        navController.navigate(Screen.ViewGroupProfile.createRoute(id))
                    },
                    onViewProfile = onViewProfile
                )
            }

            composable(
                route = Screen.ViewGroupProfile.route,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                ViewGroupProfileScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onMemberClick = onViewProfile,
                    onMessageMember = onNavigateToChat,
                    onNavigateToChats = {
                        navController.navigate(BottomNavItem.Chats.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onViewFullPhoto = onViewFullPhoto
                )
            }

            composable(Screen.ViewProfile.route,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                ViewProfileScreen(
                    userId = userId,
                    onNavigateBack = { navController.popBackStack() },
                    onViewFullPhoto = onViewFullPhoto,
                    onNavigateToChat = onNavigateToChat
                )
            }
        }
    }
} 