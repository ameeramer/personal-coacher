package com.personalcoacher.ui

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
import com.personalcoacher.domain.repository.AuthRepository
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
    authRepository: AuthRepository
) : ViewModel() {
    val isLoggedIn: Flow<Boolean> = authRepository.isLoggedIn
}

@Composable
fun PersonalCoachApp(
    appViewModel: AppViewModel = hiltViewModel(),
    notificationDeepLink: NotificationDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val isLoggedIn by appViewModel.isLoggedIn.collectAsState(initial = false)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Track the timestamp of the last processed deep link to detect new ones
    var lastProcessedTimestamp by remember { mutableStateOf(0L) }

    // State to hold pending coach message from notification - use a wrapper to ensure recomposition
    var pendingCoachMessage by remember { mutableStateOf<String?>(null) }

    // Flag to trigger navigation after message is set
    var shouldNavigateToCoach by remember { mutableStateOf(false) }

    // Check if we should show bottom nav
    val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

    // Navigate based on auth state
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn && currentDestination?.route != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
            }
        } else if (isLoggedIn && currentDestination?.route == Screen.Login.route) {
            navController.navigate(Screen.Journal.route) {
                popUpTo(Screen.Login.route) {
                    inclusive = true
                }
            }
        }
    }

    // Process deep link when it arrives and user is logged in
    LaunchedEffect(notificationDeepLink?.timestamp, isLoggedIn) {
        val deepLink = notificationDeepLink
        if (isLoggedIn &&
            deepLink != null &&
            deepLink.navigateTo == NotificationHelper.NAVIGATE_TO_COACH &&
            deepLink.timestamp > lastProcessedTimestamp
        ) {
            // Mark this deep link as processed
            lastProcessedTimestamp = deepLink.timestamp
            // Store the coach message
            pendingCoachMessage = deepLink.coachMessage
            // Set flag to trigger navigation
            shouldNavigateToCoach = true
            // Notify that we've consumed the deep link
            onDeepLinkConsumed()
        }
    }

    // Handle navigation separately to ensure state is set first
    LaunchedEffect(shouldNavigateToCoach) {
        if (shouldNavigateToCoach) {
            shouldNavigateToCoach = false
            // Navigate to coach screen
            navController.navigate(Screen.Coach.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                    inclusive = false
                }
                launchSingleTop = true
                restoreState = false
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
            startDestination = if (isLoggedIn) Screen.Journal.route else Screen.Login.route,
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
                    initialCoachMessage = pendingCoachMessage,
                    onConsumeInitialMessage = { pendingCoachMessage = null }
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
