package com.personalcoacher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personalcoacher.ui.theme.PersonalCoachTheme
import com.personalcoacher.NotificationDeepLink
import com.personalcoacher.R
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.notification.NotificationHelper
import com.personalcoacher.ui.navigation.Screen
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.ui.screens.agenda.AgendaScreen
import com.personalcoacher.ui.screens.coach.CoachScreen
import com.personalcoacher.ui.screens.dailytools.DailyToolsScreen
import com.personalcoacher.ui.screens.dailytools.MyToolsScreen
import com.personalcoacher.ui.screens.home.HomeScreen
import com.personalcoacher.ui.screens.journal.JournalEditorScreen
import com.personalcoacher.ui.screens.journal.JournalScreen
import com.personalcoacher.ui.screens.login.LoginScreen
import com.personalcoacher.ui.screens.recorder.RecorderScreen
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
        route = Screen.Home.route,
        labelResId = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        route = Screen.Journal.route,
        labelResId = R.string.nav_journal,
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book
    ),
    BottomNavItem(
        route = Screen.Agenda.route,
        labelResId = R.string.nav_agenda,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    ),
    BottomNavItem(
        route = Screen.Coach.route,
        labelResId = R.string.nav_coach,
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
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
    tokenManager: TokenManager,
    val dailyAppRepository: DailyAppRepository
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

    // Track the deep link timestamp we've processed to avoid re-processing
    var processedDeepLinkTimestamp by remember { mutableStateOf<Long?>(null) }

    // Check if we should show bottom nav
    val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

    // Determine if there's an unprocessed coach deep link
    val hasUnprocessedCoachDeepLink = notificationDeepLink != null &&
        notificationDeepLink.navigateTo == NotificationHelper.NAVIGATE_TO_COACH &&
        notificationDeepLink.timestamp != processedDeepLinkTimestamp

    // Determine if there's an unprocessed conversation deep link (from chat response notification)
    val hasUnprocessedConversationDeepLink = notificationDeepLink != null &&
        notificationDeepLink.navigateTo == NotificationHelper.NAVIGATE_TO_CONVERSATION &&
        notificationDeepLink.timestamp != processedDeepLinkTimestamp

    // Get the coach message for passing to CoachScreen
    // Only pass it if the deep link hasn't been processed yet
    val coachMessageFromDeepLink = if (hasUnprocessedCoachDeepLink) notificationDeepLink?.coachMessage else null

    // Get the conversation ID for navigating to a specific conversation
    val conversationIdFromDeepLink = if (hasUnprocessedConversationDeepLink) notificationDeepLink?.conversationId else null

    // Start destination is always based on auth state only (not deep links)
    // Deep link navigation happens via LaunchedEffect after NavHost is set up
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

    // Handle logout: navigate to login if user becomes logged out
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Handle deep link navigation - runs once per deep link timestamp
    LaunchedEffect(notificationDeepLink?.timestamp) {
        val deepLink = notificationDeepLink ?: return@LaunchedEffect

        // Only process deep links that haven't been processed
        if (deepLink.timestamp != processedDeepLinkTimestamp && isLoggedIn) {
            when (deepLink.navigateTo) {
                NotificationHelper.NAVIGATE_TO_COACH -> {
                    // Navigate to coach screen (for dynamic AI notification)
                    navController.navigate(Screen.Coach.route) {
                        popUpTo(Screen.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                }
                NotificationHelper.NAVIGATE_TO_CONVERSATION -> {
                    // Navigate to coach screen (for chat response notification)
                    // The conversation ID will be handled by CoachScreen
                    navController.navigate(Screen.Coach.route) {
                        popUpTo(Screen.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val extendedColors = PersonalCoachTheme.extendedColors

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                // iOS-style translucent navigation bar with thin border
                Surface(
                    color = extendedColors.translucentSurface,
                    border = BorderStroke(0.5.dp, extendedColors.thinBorder),
                    shadowElevation = 0.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
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
                                label = {
                                    Text(
                                        text = stringResource(item.labelResId),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                        }
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
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToJournal = {
                        navController.navigate(Screen.Journal.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToCoach = {
                        navController.navigate(Screen.Coach.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSummaries = {
                        navController.navigate(Screen.Summaries.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAgenda = {
                        navController.navigate(Screen.Agenda.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToRecorder = {
                        navController.navigate(Screen.Recorder.route)
                    },
                    onNavigateToDailyTools = {
                        navController.navigate(Screen.DailyTools.route)
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
                    initialConversationId = conversationIdFromDeepLink,
                    onConsumeInitialMessage = {
                        // Mark this deep link as processed
                        processedDeepLinkTimestamp = notificationDeepLink?.timestamp
                        onDeepLinkConsumed()
                    }
                )
            }

            composable(Screen.Agenda.route) {
                AgendaScreen()
            }

            composable(Screen.Recorder.route) {
                RecorderScreen()
            }

            composable(Screen.Summaries.route) {
                SummariesScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToSummaries = {
                        navController.navigate(Screen.Summaries.route)
                    },
                    onNavigateToRecorder = {
                        navController.navigate(Screen.Recorder.route)
                    }
                )
            }

            composable(Screen.DailyTools.route) {
                DailyToolsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMyTools = {
                        navController.navigate(Screen.MyTools.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    repository = appViewModel.dailyAppRepository
                )
            }

            composable(Screen.MyTools.route) {
                MyToolsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    repository = appViewModel.dailyAppRepository
                )
            }
        }
    }
}
