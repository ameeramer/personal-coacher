package com.personalcoacher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personalcoacher.NotificationDeepLink
import com.personalcoacher.R
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.notification.NotificationHelper
import com.personalcoacher.ui.navigation.Screen
import com.personalcoacher.ui.screens.coach.CoachScreen
import com.personalcoacher.ui.screens.journal.JournalEditorScreen
import com.personalcoacher.ui.screens.journal.JournalScreen
import com.personalcoacher.ui.screens.login.LoginScreen
import com.personalcoacher.ui.screens.settings.SettingsScreen
import com.personalcoacher.ui.screens.summaries.SummariesScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class BottomNavItem(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Journal.route,
        labelResId = R.string.nav_journal,
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book
    ),
    BottomNavItem(
        route = Screen.Coach.route,
        labelResId = R.string.nav_coach,
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
    ),
    BottomNavItem(
        route = Screen.Summaries.route,
        labelResId = R.string.nav_summaries,
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        labelResId = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@HiltViewModel
class AppViewModel @Inject constructor(
    tokenManager: TokenManager
) : ViewModel() {
    // Get initial auth state synchronously to avoid flash
    val initialAuthState: Boolean = tokenManager.getTokenSync() != null

    // Observable auth state for reactive updates
    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn
}

@Composable
fun PersonalCoachApp(
    appViewModel: AppViewModel = hiltViewModel(),
    notificationDeepLink: NotificationDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Use the synchronously determined initial state to avoid login flash
    val isLoggedIn by appViewModel.isLoggedIn.collectAsState(initial = appViewModel.initialAuthState)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Track whether we've consumed the current deep link
    var deepLinkConsumed by remember { mutableStateOf(false) }

    // Check if we should show bottom nav
    val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

    // Determine if there's a valid coach deep link to process
    val hasCoachDeepLink = notificationDeepLink != null &&
        notificationDeepLink.navigateTo == NotificationHelper.NAVIGATE_TO_COACH &&
        !deepLinkConsumed

    // Get the coach message directly from the deep link (not via intermediate state)
    val coachMessageFromDeepLink = if (hasCoachDeepLink) notificationDeepLink?.coachMessage else null

    val startDestination = when {
        !isLoggedIn -> Screen.Login.route
        hasCoachDeepLink -> Screen.Coach.route
        else -> Screen.Journal.route
    }

    // Handle logout: navigate to login if user becomes logged out
    LaunchedEffect(isLoggedIn, currentDestination?.route) {
        if (!isLoggedIn && currentDestination?.route != null && currentDestination.route != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Handle navigation when receiving a deep link while the app is already open
    LaunchedEffect(notificationDeepLink?.timestamp, isLoggedIn, currentDestination?.route) {
        val deepLink = notificationDeepLink
        // Only navigate if:
        // 1. User is logged in
        // 2. There's a coach deep link
        // 3. We're not already on the coach screen
        // 4. Deep link hasn't been consumed yet
        if (isLoggedIn &&
            deepLink != null &&
            deepLink.navigateTo == NotificationHelper.NAVIGATE_TO_COACH &&
            !deepLinkConsumed &&
            currentDestination?.route != null &&
            currentDestination.route != Screen.Coach.route
        ) {
            navController.navigate(Screen.Coach.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(item.labelResId)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Journal.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Journal.route) {
                JournalScreen(
                    onEntryClick = { entry ->
                        navController.navigate(Screen.JournalEditor.createRoute(entry.id))
                    },
                    onNewEntry = {
                        navController.navigate(Screen.JournalEditor.createRoute())
                    }
                )
            }

            composable(
                route = Screen.JournalEditor.ROUTE_WITH_ARG,
                arguments = listOf(
                    androidx.navigation.navArgument("entryId") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getString("entryId")
                JournalEditorScreen(
                    onBack = { navController.popBackStack() },
                    entryId = entryId
                )
            }

            composable(Screen.Coach.route) {
                CoachScreen(
                    initialCoachMessage = coachMessageFromDeepLink,
                    onConsumeInitialMessage = {
                        deepLinkConsumed = true
                        onDeepLinkConsumed()
                    }
                )
            }

            composable(Screen.Summaries.route) {
                SummariesScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
