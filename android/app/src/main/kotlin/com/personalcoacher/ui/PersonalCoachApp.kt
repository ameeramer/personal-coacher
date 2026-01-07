package com.personalcoacher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.personalcoacher.R
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.ui.navigation.Screen
import com.personalcoacher.ui.screens.coach.CoachScreen
import com.personalcoacher.ui.screens.journal.JournalEditorScreen
import com.personalcoacher.ui.screens.journal.JournalScreen
import com.personalcoacher.ui.screens.login.LoginScreen
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
    appViewModel: AppViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isLoggedIn by appViewModel.isLoggedIn.collectAsState(initial = false)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
                route = Screen.JournalEditor.route,
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
                CoachScreen()
            }

            composable(Screen.Summaries.route) {
                SummariesScreen()
            }
        }
    }
}
