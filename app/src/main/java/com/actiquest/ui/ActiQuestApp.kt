package com.actiquest.ui

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.actiquest.data.Repository
import com.actiquest.ui.screens.*

@Composable
fun ActiQuestApp() {
    val navController = rememberNavController()
    var isAuthenticated by remember { mutableStateOf(false) }
    var startRoute by remember { mutableStateOf("home") }

    val handleLogout = {
        Repository.signOut()
        isAuthenticated = false
        startRoute = "home"
    }

    if (!isAuthenticated) {
        LoginScreen(
            onLoginSuccess = { isAdmin ->
                startRoute = if (isAdmin) "admin_review" else "home"
                isAuthenticated = true
            }
        )
    } else {
        ActiQuestNavHost(
            navController = navController,
            startDestination = startRoute,
            onLogout = handleLogout
        )
    }
}

@Composable
fun ActiQuestNavHost(
    navController: NavHostController,
    startDestination: String = "home",
    onLogout: () -> Unit = {}
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("home") { 
            HomeScreen(navController, onLogout = onLogout) 
        }
        composable("my_submissions") { 
            StudentSubmissionsScreen(navController, onLogout = onLogout) 
        }
        composable("admin_review") { 
            AdminReviewScreen(navController, onLogout = onLogout) 
        }
        composable("badges") { 
            BadgesScreen(navController) 
        }
        composable("standings") { 
            WeeklyClassStandingsScreen(navController) 
        }
        composable("activity_journey") { 
            ActivityJourneyScreen(navController) 
        }
        composable("notifications") { 
            NotificationHistoryScreen(navController) 
        }
        composable("submit_proof") {
            SubmitFitnessProofScreen(navController)
        }
        composable("scan") { 
            ScanScreen(navController) 
        }
        composable("quest_detail/{questCode}") { backStackEntry ->
            val questCode = backStackEntry.arguments?.getString("questCode") ?: ""
            QuestDetailScreen(navController, questCode)
        }
        composable("quest_briefing/{questCode}") { backStackEntry ->
            val questCode = backStackEntry.arguments?.getString("questCode") ?: ""
            QuestBriefingScreen(navController, questCode)
        }
        composable("record/{questCode}/{maxSeconds}") { backStackEntry ->
            val questCode = backStackEntry.arguments?.getString("questCode") ?: ""
            val maxSeconds = backStackEntry.arguments?.getString("maxSeconds")?.toIntOrNull() ?: 30
            RecordScreen(navController, questCode, maxSeconds)
        }
    }
}
