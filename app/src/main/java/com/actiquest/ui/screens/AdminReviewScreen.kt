package com.actiquest.ui.screens

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.actiquest.R
import com.actiquest.data.Repository
import com.actiquest.domain.Quest
import com.actiquest.domain.SeedQuests
import com.actiquest.domain.Submission
import com.actiquest.ui.components.*
import com.actiquest.ui.components.VideoPlayerView
import com.actiquest.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReviewScreen(
    navController: NavController,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = ThemeManager.isDarkMode

    val pendingSubmissions by remember { Repository.listenPendingSubmissions() }.collectAsState(initial = emptyList())
    val allQuests = remember { SeedQuests.QUEST_MAP.values.toList() }
    var selectedBottomTab by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf("Pending") }
    var subTypeFilter by remember { mutableStateOf("All") } // "All", "Videos", "Screenshots"
    var questCategoryFilter by remember { mutableStateOf("All") }
    var selectedSubmissionForReview by remember { mutableStateOf<Submission?>(null) }
    var isProcessingAction by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Double tap back button to exit on Admin Portal, or single back to return to Dashboard Tab
    androidx.activity.compose.BackHandler {
        if (selectedSubmissionForReview != null) {
            selectedSubmissionForReview = null
        } else if (selectedBottomTab != 0) {
            selectedBottomTab = 0
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000L) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit ActiQuest", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoCount = pendingSubmissions.count { it.submissionType != "screenshot" }
    val screenshotCount = pendingSubmissions.count { it.submissionType == "screenshot" }

    val filteredPendingSubmissions = remember(pendingSubmissions, subTypeFilter) {
        when (subTypeFilter) {
            "Videos" -> pendingSubmissions.filter { it.submissionType != "screenshot" }
            "Screenshots" -> pendingSubmissions.filter { it.submissionType == "screenshot" }
            else -> pendingSubmissions
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    "Sign Out of Admin Portal",
                    fontFamily = SfBoldFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else LightTextPrimary
                )
            },
            text = {
                Text(
                    "Are you sure you want to end your administrative verification session?",
                    fontFamily = SfSemiFontFamily,
                    color = if (isDark) SlateLight else LightTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        Repository.signOut()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRejected)
                ) {
                    Text("Sign Out", fontFamily = SfBoldFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        "Cancel",
                        fontFamily = SfSemiFontFamily,
                        color = if (isDark) SlateLight else LightTextSecondary
                    )
                }
            },
            containerColor = if (isDark) SurfaceCharcoal else Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        containerColor = if (isDark) DarkNavyBackground else LightBackground,
        bottomBar = {
            AppBottomNavBar(
                selectedTab = selectedBottomTab,
                onTabSelected = { selectedBottomTab = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedBottomTab) {
                0 -> {
                    // TAB 0: ADMIN DASHBOARD / OVERVIEW
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Admin Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isDark) SurfaceElevated else Color.White
                                            )
                                            .border(
                                                1.5.dp,
                                                CobaltBlue,
                                                CircleShape
                                            )
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_actiquest_logo),
                                            contentDescription = "ActiQuest Admin",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Fit
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Admin Portal",
                                            fontFamily = SfBoldFontFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.White else LightTextPrimary
                                        )
                                        Text(
                                            text = "Verification & Point Granting Desk",
                                            fontFamily = SfSemiFontFamily,
                                            fontSize = 12.sp,
                                            color = if (isDark) SlateLight else LightTextSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showLogoutDialog = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) SurfaceElevated else Color(0xFFFFEBEE))
                                        .border(1.dp, StatusRejected.copy(alpha = 0.3f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Logout,
                                        contentDescription = "Sign Out",
                                        tint = StatusRejected,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Hero Wellbeing / Queue Progress Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (isDark) SurfaceCharcoal else Color.White,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "VERIFICATION STATUS",
                                                fontFamily = SfBoldFontFamily,
                                                fontSize = 11.sp,
                                                color = CobaltBlue,
                                                letterSpacing = 0.8.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (pendingSubmissions.isNotEmpty()) "${pendingSubmissions.size} In Queue" else "Queue Up to Date",
                                                fontFamily = SfBoldFontFamily,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color.White else LightTextPrimary
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (pendingSubmissions.isNotEmpty()) StatusPending.copy(alpha = 0.15f) else MintEmerald.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (pendingSubmissions.isNotEmpty()) StatusPending.copy(alpha = 0.5f) else MintEmerald.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Text(
                                                text = if (pendingSubmissions.isNotEmpty()) "NEEDS ACTION" else "ALL CLEAR",
                                                fontFamily = SfBoldFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (pendingSubmissions.isNotEmpty()) StatusPending else MintEmerald,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Metric Stat Boxes
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MetricStatGridCard(
                                            label = "Total In Queue",
                                            value = "${pendingSubmissions.size}",
                                            icon = Icons.Default.PendingActions,
                                            accentColor = StatusPending,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricStatGridCard(
                                            label = "QR Videos",
                                            value = "$videoCount",
                                            icon = Icons.Default.Videocam,
                                            accentColor = ElectricBlue,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricStatGridCard(
                                            label = "Step Proofs",
                                            value = "$screenshotCount",
                                            icon = Icons.Default.PhotoLibrary,
                                            accentColor = MintEmerald,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { selectedBottomTab = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue, contentColor = Color.White),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "OPEN VERIFICATION QUEUE",
                                            fontFamily = SfBoldFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Daily Verification Activity Chart
                        item {
                            DailyActivityBarChart(
                                title = "Weekly Verification Activity",
                                days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                                values = listOf(14f, 22f, 18f, 35f, 28f, 15f, 30f)
                            )
                        }
                    }
                }

                1 -> {
                    // TAB 1: VERIFICATION QUEUE & MATRIX
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section title
                        item {
                            Text(
                                text = "Verification Desk",
                                fontFamily = SfBoldFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else LightTextPrimary
                            )
                        }

                        // Main Tab Selector
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterPill(
                                        label = "Queue (${pendingSubmissions.size})",
                                        isSelected = selectedTab == "Pending",
                                        activeColor = CobaltBlue,
                                        onClick = { selectedTab = "Pending" }
                                    )
                                }
                                item {
                                    FilterPill(
                                        label = "Quests & QR Codes (${allQuests.size})",
                                        isSelected = selectedTab == "Quests",
                                        activeColor = ElectricBlue,
                                        onClick = { selectedTab = "Quests" }
                                    )
                                }
                                item {
                                    FilterPill(
                                        label = "Rubric & Scoring",
                                        isSelected = selectedTab == "Guidelines",
                                        activeColor = IndigoPurple,
                                        onClick = { selectedTab = "Guidelines" }
                                    )
                                }
                            }
                        }

                        if (selectedTab == "Pending") {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (subTypeFilter == "All") CobaltBlue else (if (isDark) SurfaceElevated else Color.White),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (subTypeFilter == "All") CobaltBlue else (if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0))
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { subTypeFilter = "All" }
                                    ) {
                                        Text(
                                            "All (${pendingSubmissions.size})",
                                            fontFamily = SfBoldFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (subTypeFilter == "All") Color.White else (if (isDark) SlateLight else LightTextPrimary),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (subTypeFilter == "Videos") ElectricBlue else (if (isDark) SurfaceElevated else Color.White),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (subTypeFilter == "Videos") ElectricBlue else (if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0))
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { subTypeFilter = "Videos" }
                                    ) {
                                        Text(
                                            "📹 Videos ($videoCount)",
                                            fontFamily = SfBoldFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (subTypeFilter == "Videos") Color.White else (if (isDark) SlateLight else LightTextPrimary),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (subTypeFilter == "Screenshots") MintEmerald else (if (isDark) SurfaceElevated else Color.White),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (subTypeFilter == "Screenshots") MintEmerald else (if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0))
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { subTypeFilter = "Screenshots" }
                                    ) {
                                        Text(
                                            "📱 Proofs ($screenshotCount)",
                                            fontFamily = SfBoldFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (subTypeFilter == "Screenshots") Color.White else (if (isDark) SlateLight else LightTextPrimary),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            if (filteredPendingSubmissions.isEmpty()) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) SurfaceCharcoal else Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(36.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(MintEmerald.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MintEmerald,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                "Queue is Clear!",
                                                fontFamily = SfBoldFontFamily,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color.White else LightTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "All student submissions in this filter have been verified. New submissions will appear automatically.",
                                                fontFamily = SfSemiFontFamily,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) SlateLight else LightTextSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredPendingSubmissions) { submission ->
                                    AdminReviewTicketCard(
                                        submission = submission,
                                        onReview = { selectedSubmissionForReview = submission }
                                    )
                                }
                            }
                        } else if (selectedTab == "Quests") {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isDark) SurfaceCharcoal else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDark) CobaltBlue.copy(alpha = 0.4f) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Campus QR Checkpoint Matrix (Admin Only)",
                                                fontFamily = SfBoldFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isDark) Color.White else LightTextPrimary
                                            )
                                            Text(
                                                "The QR codes below are hidden from students. Place matching QR stickers at campus locations.",
                                                fontFamily = SfSemiFontFamily,
                                                fontSize = 11.sp,
                                                color = if (isDark) SlateLight else LightTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val cats = listOf("All", "Quick", "Explorer", "Boss")
                                    items(cats) { cat ->
                                        val isSel = questCategoryFilter.equals(cat, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSel) CobaltBlue else (if (isDark) SurfaceCharcoal else Color.White),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSel) CobaltBlue else (if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0))
                                            ),
                                            modifier = Modifier.clickable { questCategoryFilter = cat }
                                        ) {
                                            Text(
                                                cat,
                                                fontFamily = SfBoldFontFamily,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else (if (isDark) SlateLight else LightTextPrimary),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val filtered = allQuests.filter {
                                questCategoryFilter == "All" || it.category.equals(questCategoryFilter, ignoreCase = true)
                            }

                            items(filtered) { quest ->
                                AdminQuestMatrixCard(
                                    quest = quest,
                                    onCopyCode = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Quest QR Code", quest.questCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied code '${quest.questCode}' to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else if (selectedTab == "Guidelines") {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDark) SurfaceCharcoal else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDark) SurfaceElevatedHigh else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Tune, contentDescription = null, tint = ElectricBlue)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Partial Points & Verification Rubric",
                                                fontFamily = SfBoldFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (isDark) Color.White else LightTextPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = CobaltBlue.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    "1. Partial Points Flexibility",
                                                    fontFamily = SfBoldFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CobaltBlue,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Admins can award full points, custom partial points (e.g. 50%, 75%), or custom point counts for both QR video quests and manual pedometer/fitness screenshot submissions.",
                                                    fontFamily = SfSemiFontFamily,
                                                    color = if (isDark) SlateLight else LightTextPrimary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MintEmerald.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    "2. Pedometer & Fitness Screenshot Proof",
                                                    fontFamily = SfBoldFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MintEmerald,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Verify date, step count (e.g. 8,000 - 10,000 steps), active duration (mins), or distance. If student achieved partial goal (e.g. 5,000 out of 10,000), award partial points accordingly.",
                                                    fontFamily = SfSemiFontFamily,
                                                    color = if (isDark) SlateLight else LightTextPrimary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = IndigoPurple.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    "3. Real-Time Student & Class Sync",
                                                    fontFamily = SfBoldFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndigoPurple,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Upon approval, awarded points instantly update the student's personal score, their class leaderboard standing, and badge progression.",
                                                    fontFamily = SfSemiFontFamily,
                                                    color = if (isDark) SlateLight else LightTextPrimary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: STANDINGS & LEADERBOARDS
                    WeeklyClassStandingsScreen(navController)
                }

                3 -> {
                    // TAB 3: ADMIN PROFILE & SETTINGS (Upload avatar, Theme switch, Notifications, Logout)
                    ProfileSettingsContent(
                        navController = navController,
                        isAdmin = true,
                        onLogout = onLogout
                    )
                }
            }

            // High-Fidelity Review Dialog with Partial Points Controls
            selectedSubmissionForReview?.let { sub ->
                AdminSubmissionReviewDialog(
                    submission = sub,
                    isProcessing = isProcessingAction,
                    onDismiss = { if (!isProcessingAction) selectedSubmissionForReview = null },
                    onApproveWithPoints = { awardedPts, note ->
                        isProcessingAction = true
                        coroutineScope.launch {
                            try {
                                Repository.approveSubmission(sub, awardedPoints = awardedPts, reviewerNote = note)
                                Toast.makeText(
                                    context,
                                    "Approved! +$awardedPts points awarded to ${sub.studentName}.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Approval error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isProcessingAction = false
                                selectedSubmissionForReview = null
                            }
                        }
                    },
                    onRejectWithReason = { reason ->
                        isProcessingAction = true
                        coroutineScope.launch {
                            try {
                                Repository.rejectSubmission(sub.submissionId, reason)
                                Toast.makeText(context, "Retake feedback sent to student.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Rejection error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isProcessingAction = false
                                selectedSubmissionForReview = null
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AdminSubmissionReviewDialog(
    submission: Submission,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onApproveWithPoints: (Int, String?) -> Unit,
    onRejectWithReason: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val isScreenshot = submission.submissionType == "screenshot"
    val defaultMaxPoints = if (submission.pointValue > 0) submission.pointValue else 100

    var customPointsInput by remember { mutableStateOf(defaultMaxPoints.toString()) }
    var reviewerNote by remember { mutableStateOf("") }

    val currentAwardedPoints = customPointsInput.filter { it.isDigit() }.toIntOrNull() ?: defaultMaxPoints
    val isPartial = currentAwardedPoints < defaultMaxPoints

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavyBackground.copy(alpha = 0.95f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CobaltBlue.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isScreenshot) MintEmerald.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isScreenshot) MintEmerald else ElectricBlue
                                )
                            ) {
                                Text(
                                    if (isScreenshot) "FITNESS SCREENSHOT PROOF" else "QR QUEST VIDEO",
                                    color = if (isScreenshot) MintEmerald else ElectricBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                submission.questTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        IconButton(onClick = onDismiss, enabled = !isProcessing) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Student & Submission Details Strip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            submission.studentName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            if (isScreenshot) "Activity: ${submission.activityType}"
                                            else "Quest: ${submission.questCode}",
                                            fontSize = 11.sp,
                                            color = SlateLight
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CobaltBlue.copy(alpha = 0.25f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue)
                                ) {
                                    Text(
                                        "Requested: ${submission.pointValue} PTS",
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (submission.activityMetricValue.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MintEmerald.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsWalk,
                                            contentDescription = null,
                                            tint = MintEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Claimed Metric: ${submission.activityMetricValue}",
                                            fontWeight = FontWeight.Bold,
                                            color = MintEmerald,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            if (submission.studentNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Student note: \"${submission.studentNotes}\"",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Proof Display: Screenshot or Video
                    if (isScreenshot) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .border(1.dp, CobaltBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (submission.imagePath.isNotEmpty() && (submission.imagePath.startsWith("http") || submission.imagePath.startsWith("content"))) {
                                AsyncImage(
                                    model = submission.imagePath,
                                    contentDescription = "Pedometer proof screenshot",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                // High-fidelity simulated fitness screenshot card for demonstration
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                            )
                                        )
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = MintEmerald,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        submission.activityType.uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = ElectricBlue
                                    )
                                    Text(
                                        submission.activityMetricValue.ifEmpty { "10,480 Steps Logged" },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Verified Campus Fitness Activity Log",
                                        fontSize = 11.sp,
                                        color = SlateLight
                                    )
                                }
                            }
                        }
                    } else {
                        // Video Player
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            VideoPlayerView(
                                videoUriString = submission.videoPath,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // PARTIAL POINTS SECTION
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = if (isPartial) StatusPending else MintEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Points to Award",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (isPartial) StatusPending else MintEmerald).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isPartial) StatusPending else MintEmerald
                                    )
                                ) {
                                    Text(
                                        if (isPartial) "$currentAwardedPoints PTS (PARTIAL)" else "$currentAwardedPoints PTS (FULL)",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = if (isPartial) StatusPending else MintEmerald,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick preset chips: 100% Full, 75%, 50%, 25%
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val p100 = defaultMaxPoints
                                val p75 = (defaultMaxPoints * 0.75f).toInt()
                                val p50 = (defaultMaxPoints * 0.50f).toInt()
                                val p25 = (defaultMaxPoints * 0.25f).toInt()

                                listOf(
                                    Pair("100%", p100),
                                    Pair("75%", p75),
                                    Pair("50%", p50),
                                    Pair("25%", p25)
                                ).forEach { (lbl, pts) ->
                                    val isSelected = currentAwardedPoints == pts
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) CobaltBlue else SurfaceCharcoal,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) ElectricBlue else SlateSecondary.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { customPointsInput = pts.toString() }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                lbl,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else TextSecondary
                                            )
                                            Text(
                                                "$pts",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isSelected) ElectricBlue else Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom numeric input field
                            OutlinedTextField(
                                value = customPointsInput,
                                onValueChange = { customPointsInput = it },
                                label = { Text("Exact Points to Credit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CobaltBlue,
                                    unfocusedBorderColor = SlateSecondary.copy(alpha = 0.5f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reviewer Feedback Note
                    OutlinedTextField(
                        value = reviewerNote,
                        onValueChange = { reviewerNote = it },
                        label = { Text("Admin Feedback / Review Note (Optional)") },
                        placeholder = {
                            Text(
                                if (isPartial) "e.g., Awarded 50% partial points for 5,000 steps reached."
                                else "e.g., Verified! Great job completing your campus activity."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CobaltBlue,
                            unfocusedBorderColor = SlateSecondary.copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val note = reviewerNote.ifEmpty {
                                    if (isScreenshot) "Please attach a clear screenshot showing your daily steps / active minutes."
                                    else "Please re-record video showing complete form and reps."
                                }
                                onRejectWithReason(note)
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRejected),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusRejected),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Request Retake", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val finalNote = reviewerNote.ifEmpty {
                                    if (isPartial) "Partial points granted ($currentAwardedPoints pts)" else null
                                }
                                onApproveWithPoints(currentAwardedPoints, finalNote)
                            },
                            enabled = !isProcessing && currentAwardedPoints > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPartial) StatusPending else MintEmerald,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "APPROVE (+$currentAwardedPoints PTS)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReviewTicketCard(
    submission: Submission,
    onReview: () -> Unit
) {
    val isScreenshot = submission.submissionType == "screenshot"
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        submission.submittedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (isScreenshot) MintEmerald else CobaltBlue,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Ticket Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isScreenshot) MintEmerald.copy(alpha = 0.2f) else CobaltBlue.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isScreenshot) MintEmerald.copy(alpha = 0.6f) else CobaltBlue
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isScreenshot) Icons.Default.DirectionsWalk else Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (isScreenshot) MintEmerald else ElectricBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isScreenshot) "SCREENSHOT PROOF" else "QR VIDEO QUEST",
                                color = if (isScreenshot) MintEmerald else ElectricBlue,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusPending.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPending)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StatusPending))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("AWAITING REVIEW", color = StatusPending, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                submission.questTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Details Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("STUDENT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = SlateLight)
                    Text(
                        submission.studentName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ElectricBlue))
                    Text(
                        if (isScreenshot) " ── 📱 ── " else " ── 📹 ── ",
                        fontSize = 10.sp,
                        color = SlateLight,
                        fontWeight = FontWeight.Bold
                    )
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MintEmerald))
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (isScreenshot) "REQUESTED" else "QUEST PTS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = SlateLight
                    )
                    Text(
                        "+${submission.pointValue} PTS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MintEmerald
                    )
                }
            }

            if (submission.activityMetricValue.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Activity metric: ${submission.activityMetricValue}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time & Location Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceElevated.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = SlateLight, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Submitted $relativeTime",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceElevated.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isScreenshot) Icons.Default.PhotoCamera else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isScreenshot) "Fitness App" else "Campus Checkpoint",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Review CTA Button
            Button(
                onClick = onReview,
                colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    if (isScreenshot) Icons.Default.Image else Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isScreenshot) "VERIFY FITNESS SCREENSHOT & PTS" else "REVIEW VIDEO & PTS",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun AdminQuestMatrixCard(
    quest: Quest,
    onCopyCode: () -> Unit
) {
    val categoryColor = when (quest.category.lowercase()) {
        "boss" -> Color(0xFFEC4899)
        "explorer" -> ElectricBlue
        else -> MintEmerald
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal),
        border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = categoryColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor)
                ) {
                    Text(
                        "${quest.category.uppercase()} QUEST",
                        color = categoryColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MintEmerald.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald.copy(alpha = 0.5f))
                ) {
                    Text(
                        "+${quest.pointValue} PTS",
                        color = MintEmerald,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                quest.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                quest.instructions,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // QR Code Matrix Container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = CobaltBlue.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "QR MARKER CODE (ADMIN SECRET)",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = ElectricBlue,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                quest.questCode,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCopyCode,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ElectricBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Location & Duration Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceElevated.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            quest.location,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceElevated.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = SlateLight, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${quest.maxSeconds}s Max Video", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}
