package com.collabtable.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.screens.ListDetailScreen
import com.collabtable.app.ui.screens.ListsScreen
import com.collabtable.app.ui.screens.LogsScreen
import com.collabtable.app.ui.screens.ServerSetupScreen
import com.collabtable.app.ui.screens.SettingsScreen

private object Routes {
    const val Setup = "server_setup"
    const val MainRoot = "main_root"
    const val Tables = "tables"
    const val Settings = "settings"
    const val ListDetail = "list/{listId}"
    const val Logs = "logs"
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    // Show setup flow until completed; then show main app with bottom nav
    if (prefs.isFirstRun()) {
        val navController = rememberNavController()
        NavHost(navController, startDestination = Routes.Setup) {
            composable(Routes.Setup) {
                ServerSetupScreen(
                    onSetupComplete = {
                        // Mark not first run and trigger recomposition to show main UI
                        prefs.setIsFirstRun(false)
                    },
                )
            }
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.Tables || currentRoute?.startsWith("list/") == true,
                    onClick = {
                        if (currentRoute?.startsWith("list/") == true) {
                            // Behave like back from details to tables overview
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.Tables) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Tables") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.Settings || currentRoute == Routes.Logs,
                    onClick = {
                        if (currentRoute == Routes.Logs) {
                            // Behave like back from logs to settings screen
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.Settings) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(
                navController = navController,
                startDestination = Routes.Tables,
            ) {
                composable(
                    route = Routes.Tables,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    ListsScreen(
                        onNavigateToList = { listId -> navController.navigate("list/$listId") },
                        onNavigateToSettings = { navController.navigate(Routes.Settings) },
                        onNavigateToLogs = { navController.navigate(Routes.Logs) },
                    )
                }
                composable(
                    route = Routes.Settings,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLogs = { navController.navigate(Routes.Logs) },
                        onLeaveServer = {
                            // Reset to setup flow by toggling the flag and rebuilding graph via recomposition
                            prefs.setIsFirstRun(true)
                        },
                    )
                }
                composable(
                    route = Routes.Logs,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    LogsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.ListDetail,
                    arguments = listOf(navArgument("listId") { type = NavType.StringType }),
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) { backStackEntry ->
                    val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
                    ListDetailScreen(listId = listId, onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}
