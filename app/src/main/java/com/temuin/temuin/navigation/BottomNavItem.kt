package com.temuin.temuin.navigation

sealed class BottomNavItem(
    val route: String,
    val title: String
) {
    object Schedules : BottomNavItem(
        route = "schedules",
        title = "Schedules"
    )
    
    object Chats : BottomNavItem(
        route = "chats",
        title = "Chats"
    )
    
    object Friends : BottomNavItem(
        route = "friends",
        title = "Friends"
    )
} 