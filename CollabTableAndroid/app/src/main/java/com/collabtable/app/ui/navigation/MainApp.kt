package com.collabtable.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.screens.ListDetailScreen
import com.collabtable.app.ui.screens.ListsScreen
import com.collabtable.app.ui.screens.LogsScreen
import com.collabtable.app.ui.screens.ServerSetupScreen
import com.collabtable.app.ui.screens.SettingsScreen

private object Routes {
    const val SETUP = "server_setup"
    const val MAIN_ROOT = "main_root"
    const val TABLES = "tables"
    const val SETTINGS = "settings"
    const val LIST_DETAIL = "list/{listId}"
    const val LOGS = "logs"
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    val isFirstRun by prefs.isFirstRunFlow.collectAsState()

    // Show setup flow until completed; then show main app with bottom nav
    if (isFirstRun) {
        val navController = rememberNavController()
        NavHost(navController, startDestination = Routes.SETUP) {
            composable(Routes.SETUP) {
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
                    selected = currentRoute == Routes.TABLES || currentRoute?.startsWith("list/") == true,
                    onClick = {
                        if (currentRoute?.startsWith("list/") == true) {
                            // If we're in a list detail, pop back to the tables root instead of creating a new entry
                            navController.popBackStack(Routes.TABLES, inclusive = false)
                        } else if (currentRoute != Routes.TABLES) {
                            navController.navigate(Routes.TABLES) {
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
                    // Highlight settings tab for both Settings screen and Logs screen
                    selected = currentRoute == Routes.SETTINGS || currentRoute == Routes.LOGS,
                    onClick = {
                        if (currentRoute == Routes.LOGS) {
                            // Return to Settings without rebuilding entire graph
                            navController.popBackStack(Routes.SETTINGS, inclusive = false)
                        } else if (currentRoute != Routes.SETTINGS) {
                            navController.navigate(Routes.SETTINGS) {
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
        // Apply the innerPadding from the Scaffold so content is not obscured by the bottom navigation bar.
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            val tabRoutes = setOf(Routes.TABLES, Routes.SETTINGS)
            NavHost(
                navController = navController,
                startDestination = Routes.TABLES,
            ) {
                // Fade between bottom navigation tabs
                composable(
                    route = Routes.TABLES,
                    enterTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                            fadeIn(tween(200))
                        } else {
                            fadeIn(tween(150))
                        }
                    },
                    exitTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                            fadeOut(tween(200))
                        } else {
                            fadeOut(tween(150))
                        }
                    },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(180)) },
                ) {
                    ListsScreen(
                        onNavigateToList = { listId -> navController.navigate("list/$listId") },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToLogs = { navController.navigate(Routes.LOGS) },
                    )
                }
                composable(
                    route = Routes.SETTINGS,
                    enterTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) fadeIn(tween(200)) else fadeIn(tween(150))
                    },
                    exitTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) fadeOut(tween(200)) else fadeOut(tween(150))
                    },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(180)) },
                ) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLogs = { navController.navigate(Routes.LOGS) },
                        onLeaveServer = {
                            // Reset to setup flow by toggling the flag and rebuilding graph via recomposition
                            prefs.setIsFirstRun(true)
                        },
                    )
                }
                // Horizontal slide for Logs screen (container-aware for predictive back)
                composable(
                    route = Routes.LOGS,
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(240),
                        ) + fadeIn(tween(200))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(200),
                        ) + fadeOut(tween(180))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(240),
                        ) + fadeIn(tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(200),
                        ) + fadeOut(tween(180))
                    },
                ) {
                    LogsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.LIST_DETAIL,
                    arguments = listOf(navArgument("listId") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        ) + fadeIn(tween(220))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(200),
                        ) + fadeOut(tween(180))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(260),
                        ) + fadeIn(tween(220))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(200),
                        ) + fadeOut(tween(180))
                    },
                ) { backStackEntry ->
                    val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
                    ListDetailScreen(listId = listId, onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}
