package com.actiquest.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.actiquest.data.Repository
import com.actiquest.domain.ClassModel
import com.actiquest.domain.CollegeCatalog
import com.actiquest.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyClassStandingsScreen(navController: NavController) {
    val isDark = ThemeManager.isDarkMode
    val currentUserId by Repository.currentUserId.collectAsState()
    val userState by remember(currentUserId) { Repository.listenUser(currentUserId) }.collectAsState(initial = null)
    val classesState by remember { Repository.listenClasses() }.collectAsState(initial = emptyList())

    val user = userState
    val userClassId = user?.classId ?: ""

    var selectedCollege by remember { mutableStateOf("All") }
    var selectedProgram by remember { mutableStateOf("All") }

    val collegesList = listOf("All") + CollegeCatalog.COLLEGES.map { it.code }
    val availablePrograms = remember(selectedCollege) {
        if (selectedCollege == "All") {
            listOf("All")
        } else {
            val college = CollegeCatalog.COLLEGES.find { it.code == selectedCollege }
            listOf("All") + (college?.programs ?: emptyList())
        }
    }

    val filteredClasses = remember(classesState, selectedCollege, selectedProgram) {
        classesState.filter { cls ->
            val matchCollege = (selectedCollege == "All" || cls.college.equals(selectedCollege, ignoreCase = true) || cls.classId.startsWith(selectedCollege))
            val matchProgram = (selectedProgram == "All" || cls.program.equals(selectedProgram, ignoreCase = true) || cls.name.contains(selectedProgram, ignoreCase = true))
            matchCollege && matchProgram
        }.sortedByDescending { it.totalPoints }
    }

    val topScore = classesState.maxOfOrNull { it.totalPoints } ?: 1

    Scaffold(
        containerColor = if (isDark) DarkNavyBackground else LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "WEEKLY CLASS STANDINGS",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SfBoldFontFamily,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color.White else LightTextPrimary,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else LightTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) SurfaceCharcoal else Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // College filter row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(collegesList) { col ->
                    val isSelected = selectedCollege == col
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCollege = col
                            selectedProgram = "All"
                        },
                        label = { Text(col, fontFamily = SfSemiFontFamily, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CobaltBlue,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDark) SurfaceCharcoal else Color.White,
                            labelColor = if (isDark) TextSecondary else LightTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CobaltBlue else (if (isDark) SlateSecondary.copy(alpha = 0.3f) else Color(0xFFE2E8F0))
                        )
                    )
                }
            }

            // Program dropdown filter if college selected - Full complete names
            if (availablePrograms.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availablePrograms) { prog ->
                        val isSelected = selectedProgram == prog
                        val displayProg = if (prog == "All") "All Programs" else prog
                        AssistChip(
                            onClick = { selectedProgram = prog },
                            label = {
                                Text(
                                    displayProg,
                                    fontFamily = SfSemiFontFamily,
                                    fontSize = 12.sp,
                                    color = if (isSelected) (if (isDark) ElectricBlue else CobaltBlue) else (if (isDark) TextSecondary else LightTextSecondary)
                                )
                            },
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) (if (isDark) ElectricBlue else CobaltBlue) else (if (isDark) SlateSecondary.copy(alpha = 0.3f) else Color(0xFFE2E8F0))
                            ),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) (if (isDark) ElectricBlue.copy(alpha = 0.15f) else CobaltBlue.copy(alpha = 0.10f)) else (if (isDark) SurfaceCharcoal else Color.White)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // User's own class summary banner
            val myClass = classesState.find { it.classId == userClassId }
            if (myClass != null) {
                val myRank = classesState.indexOfFirst { it.classId == userClassId } + 1
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceElevated else Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald.copy(alpha = 0.7f)),
                    elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MintEmerald.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "#$myRank",
                                        fontFamily = SfBoldFontFamily,
                                        fontWeight = FontWeight.Black,
                                        color = MintEmerald,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MintEmerald.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "YOUR CLASS",
                                        color = MintEmerald,
                                        fontFamily = SfBoldFontFamily,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    myClass.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = SfBoldFontFamily,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDark) Color.White else LightTextPrimary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${myClass.totalPoints} pts",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = SfBoldFontFamily,
                                fontWeight = FontWeight.Black,
                                color = MintEmerald
                            )
                            Text(
                                "${myClass.memberCount} Warriors",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = SfSemiFontFamily,
                                color = if (isDark) TextMuted else LightTextSecondary
                            )
                        }
                    }
                }
            }

            // Standings list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(filteredClasses) { index, classItem ->
                    val isMyClass = classItem.classId == userClassId
                    val rank = index + 1
                    ClassLeaderboardCard(
                        rank = rank,
                        classModel = classItem,
                        isMyClass = isMyClass,
                        topScore = topScore
                    )
                }

                if (filteredClasses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SportsScore,
                                    contentDescription = null,
                                    tint = SlateSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "No classes found for this filter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = SfSemiFontFamily,
                                    color = if (isDark) TextSecondary else LightTextSecondary
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
fun ClassLeaderboardCard(
    rank: Int,
    classModel: ClassModel,
    isMyClass: Boolean,
    topScore: Int
) {
    val isDark = ThemeManager.isDarkMode
    val rankBadge = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }

    val rankBorderColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> if (isMyClass) MintEmerald else (if (isDark) SlateSecondary.copy(alpha = 0.25f) else Color(0xFFE2E8F0))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                if (isMyClass) SurfaceElevated else SurfaceCharcoal
            } else {
                if (isMyClass) Color(0xFFF0FDF4) else Color.White
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (rank <= 3 || isMyClass) 1.5.dp else 1.dp,
            rankBorderColor
        ),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when (rank) {
                                    1 -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                    2 -> Color(0xFFC0C0C0).copy(alpha = 0.2f)
                                    3 -> Color(0xFFCD7F32).copy(alpha = 0.2f)
                                    else -> if (isDark) SurfaceElevated else Color(0xFFF1F5F9)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rank <= 3) {
                            Text(rankBadge, fontSize = 20.sp)
                        } else {
                            Text(
                                rankBadge,
                                fontFamily = SfBoldFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextSecondary else LightTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                classModel.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = SfBoldFontFamily,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else LightTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isMyClass) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MintEmerald.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "YOU",
                                        color = MintEmerald,
                                        fontSize = 9.sp,
                                        fontFamily = SfBoldFontFamily,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            "${classModel.college} • ${classModel.memberCount} members",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = SfSemiFontFamily,
                            color = if (isDark) TextMuted else LightTextSecondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${classModel.totalPoints} PTS",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SfBoldFontFamily,
                        fontWeight = FontWeight.Black,
                        color = if (rank == 1) Color(0xFFFFD700) else if (isMyClass) MintEmerald else CobaltBlue
                    )
                    val avg = if (classModel.memberCount > 0) classModel.totalPoints / classModel.memberCount else 0
                    Text(
                        "avg $avg pts/student",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = SfSemiFontFamily,
                        color = if (isDark) TextMuted else LightTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Score relative progress bar
            val progress = if (topScore > 0) (classModel.totalPoints.toFloat() / topScore).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (rank == 1) Color(0xFFFFD700) else if (isMyClass) MintEmerald else CobaltBlue,
                trackColor = if (isDark) SurfaceElevated else Color(0xFFE2E8F0)
            )
        }
    }
}
