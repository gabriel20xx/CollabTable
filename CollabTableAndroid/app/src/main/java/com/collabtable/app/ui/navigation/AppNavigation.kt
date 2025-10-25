package com.collabtable.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.screens.ListDetailScreen
import com.collabtable.app.ui.screens.ListsScreen
import com.collabtable.app.ui.screens.LogsScreen
import com.collabtable.app.ui.screens.ServerSetupScreen
import com.collabtable.app.ui.screens.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }

    // Determine start destination based on first run
    val startDestination =
        if (preferencesManager.isFirstRun()) {
            "server_setup"
        } else {
            "lists"
        }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable("server_setup") {
            ServerSetupScreen(
                onSetupComplete = {
                    // Navigate to lists and clear back stack
                    navController.navigate("lists") {
                        popUpTo("server_setup") { inclusive = true }
                    }
                },
            )
        }

        composable("lists") {
            ListsScreen(
                onNavigateToList = { listId ->
                    navController.navigate("list/$listId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToLogs = {
                    navController.navigate("logs")
                },
            )
        }

        composable(
            route = "list/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
            ListDetailScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLeaveServer = {
                    // Navigate back to server setup and clear entire back stack
                    navController.navigate("server_setup") {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable("logs") {
            LogsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
