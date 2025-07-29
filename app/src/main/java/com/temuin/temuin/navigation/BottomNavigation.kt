package com.temuin.temuin.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.temuin.temuin.R

@Composable
fun BottomNavigation(
    onClick:(index: Int) -> Unit,
    selectedItem: Int,
    totalUnreadCount: Int = 0,
    pendingScheduleNotifications: Int = 0
){
    val items = listOf(
        NavigationItem("Schedules", R.drawable.baseline_calendar_month_24, R.drawable.outline_calendar_today_24),
        NavigationItem("Chats", R.drawable.chat_icon, R.drawable.outline_chat_24),
        NavigationItem("Friends", R.drawable.baseline_friends_24, R.drawable.outline_friends_24)
    )

    Column {
        Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), thickness = (0.5).dp)

        NavigationBar(
            containerColor = Color.Transparent
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedItem == index,
                    onClick = { onClick(index) },
                    label = {
                        if (index == selectedItem) {
                            Text(
                                text = item.name,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        } else {
                            Text(
                                text = item.name,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    },
                    icon = {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = if (index == selectedItem) {
                                    painterResource(item.selectedIcon)
                                } else {
                                    painterResource(item.unSelectedIcon)
                                },
                                contentDescription = null,
                                tint = if (index == selectedItem) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )

                            // Show badge for Chats tab (index 1) when there are unread messages
                            if (index == 1 && totalUnreadCount > 0) {
                                Badge(
                                    modifier = Modifier.offset(x = 10.dp, y = (-8).dp),
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (totalUnreadCount > 99) "99+" else totalUnreadCount.toString(),
                                        color = MaterialTheme.colorScheme.background
                                    )
                                }
                            }
                            
                            // Show badge for Schedules tab (index 0) when there are pending notifications
                            if (index == 0 && pendingScheduleNotifications > 0) {
                                Badge(
                                    modifier = Modifier.offset(x = 10.dp, y = (-8).dp),
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (pendingScheduleNotifications > 9) "9+" else pendingScheduleNotifications.toString(),
                                        color = MaterialTheme.colorScheme.background
                                    )
                                }
                            }
                        }
                    },

                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = Color.DarkGray,
                        unselectedTextColor = Color.DarkGray
                    )
                )
            }
        }
    }
}

data class NavigationItem(
    val name: String,
    @DrawableRes val selectedIcon: Int,
    @DrawableRes val unSelectedIcon: Int
)