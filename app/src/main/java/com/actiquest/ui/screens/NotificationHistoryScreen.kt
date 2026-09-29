package com.actiquest.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.actiquest.data.Repository
import com.actiquest.domain.AppNotification
import com.actiquest.domain.DateTimeUtils
import com.actiquest.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val isDark = ThemeManager.isDarkMode
    val notifications by Repository.listenNotifications().collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("All") }
    var showClearDialog by remember { mutableStateOf(false) }

    // Safe Back handling
    BackHandler {
        if (!navController.popBackStack()) {
            navController.navigate("home") { popUpTo(0) }
        }
    }

    val unreadCount = notifications.count { !it.isRead }

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "Unread" -> notifications.filter { !it.isRead }
            "Approvals" -> notifications.filter { it.category == "Review" || it.title.contains("Approved", ignoreCase = true) }
            "Quests" -> notifications.filter { it.category == "Quest" }
            "Streaks" -> notifications.filter { it.category == "Streak" || it.category == "Leaderboard" }
            else -> notifications
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Clear Notification History",
                    fontFamily = SfBoldFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else LightTextPrimary
                )
            },
            text = {
                Text(
                    "Are you sure you want to clear all notification history?",
                    fontFamily = SfSemiFontFamily,
                    color = if (isDark) SlateLight else LightTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        Repository.clearNotifications()
                        showClearDialog = false
                        Toast.makeText(context, "Notifications cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRejected)
                ) {
                    Text("Clear All", fontFamily = SfBoldFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", fontFamily = SfSemiFontFamily, color = if (isDark) SlateLight else LightTextSecondary)
                }
            },
            containerColor = if (isDark) SurfaceCharcoal else Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        containerColor = if (isDark) DarkNavyBackground else LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = SfBoldFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else LightTextPrimary
                            )
                        )
                        if (unreadCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = StatusRejected
                            ) {
                                Text(
                                    text = "$unreadCount",
                                    fontFamily = SfBoldFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate("home") { popUpTo(0) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else LightTextPrimary
                        )
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                Repository.markAllNotificationsRead()
                                Toast.makeText(context, "All marked as read", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                "Mark Read",
                                fontFamily = SfBoldFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = CobaltBlue
                            )
                        }
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Clear All",
                                tint = if (isDark) SlateLight else LightTextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color.Transparent else LightBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Chips
            val filters = listOf("All", "Unread", "Approvals", "Quests", "Streaks")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val isSel = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) CobaltBlue else (if (isDark) SurfaceElevated else Color.White),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSel) CobaltBlue else (if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0))
                        ),
                        modifier = Modifier.clickable { selectedFilter = filter }
                    ) {
                        Text(
                            text = filter,
                            fontFamily = if (isSel) SfBoldFontFamily else SfSemiFontFamily,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (isSel) Color.White else (if (isDark) SlateLight else LightTextSecondary),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) SurfaceElevated else Color(0xFFEEF2FF),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = CobaltBlue,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            text = "No Notifications",
                            fontFamily = SfBoldFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isDark) Color.White else LightTextPrimary
                        )

                        Text(
                            text = if (selectedFilter == "Unread") "You are all caught up! No unread alerts." else "Updates on quest submissions, leaderboard ranks, and streaks will appear here.",
                            fontFamily = SfSemiFontFamily,
                            fontSize = 13.sp,
                            color = if (isDark) SlateLight else LightTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredNotifications, key = { it.id }) { notif ->
                        NotificationItemCard(
                            notification = notif,
                            isDark = isDark,
                            onClick = {
                                Repository.markNotificationRead(notif.id)
                                if (notif.category == "Review") {
                                    navController.navigate("my_submissions")
                                } else if (notif.category == "Leaderboard") {
                                    navController.navigate("standings")
                                } else if (notif.category == "Streak") {
                                    navController.navigate("activity_journey")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notification: AppNotification,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val relativeTime = DateTimeUtils.formatRelativeTime(notification.timestamp)

    val iconVector = when (notification.category) {
        "Review" -> Icons.Default.CheckCircle
        "Quest" -> Icons.Default.Explore
        "Streak" -> Icons.Default.LocalFireDepartment
        "Leaderboard" -> Icons.Default.EmojiEvents
        else -> Icons.Default.Notifications
    }

    val iconColor = when (notification.category) {
        "Review" -> MintEmerald
        "Quest" -> ElectricBlue
        "Streak" -> Color(0xFFF59E0B)
        "Leaderboard" -> AccentGold
        else -> CobaltBlue
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = when {
            !notification.isRead && isDark -> SurfaceElevated.copy(alpha = 0.95f)
            !notification.isRead && !isDark -> Color(0xFFF8FAFC)
            isDark -> SurfaceCharcoal.copy(alpha = 0.85f)
            else -> Color.White
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                !notification.isRead -> iconColor.copy(alpha = 0.5f)
                isDark -> SurfaceElevatedHigh.copy(alpha = 0.7f)
                else -> Color(0xFFE2E8F0)
            }
        ),
        shadowElevation = if (!notification.isRead && !isDark) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Text info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontFamily = SfBoldFontFamily,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = if (isDark) Color.White else LightTextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    if (!notification.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(iconColor)
                        )
                    }
                }

                Text(
                    text = notification.message,
                    fontFamily = SfSemiFontFamily,
                    fontSize = 12.5.sp,
                    color = if (isDark) SlateLight else LightTextSecondary,
                    lineHeight = 17.sp
                )

                Text(
                    text = relativeTime,
                    fontFamily = SfSemiFontFamily,
                    fontSize = 11.sp,
                    color = if (isDark) SlateSecondary else Color(0xFF94A3B8)
                )
            }
        }
    }
}
