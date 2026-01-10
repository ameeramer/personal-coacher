package com.personalcoacher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.personalcoacher.notification.NotificationHelper
import com.personalcoacher.ui.PersonalCoachApp
import com.personalcoacher.ui.theme.PersonalCoachTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Data class to hold deep link information from notifications
 */
data class NotificationDeepLink(
    val navigateTo: String?,
    val coachMessage: String?,
    val coachTitle: String?,
    val timestamp: Long = System.currentTimeMillis() // Unique identifier for each deep link
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hold the deep link state at the Activity level so it survives recomposition
    private val deepLinkState = mutableStateOf<NotificationDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract deep link from initial intent
        deepLinkState.value = extractDeepLinkFromIntent(intent)

        setContent {
            PersonalCoachTheme {
                PersonalCoachApp(
                    notificationDeepLink = deepLinkState.value,
                    onDeepLinkConsumed = { deepLinkState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update the deep link state - compose will automatically recompose
        val newDeepLink = extractDeepLinkFromIntent(intent)
        if (newDeepLink.navigateTo != null) {
            deepLinkState.value = newDeepLink
        }
    }

    private fun extractDeepLinkFromIntent(intent: Intent?): NotificationDeepLink {
        return NotificationDeepLink(
            navigateTo = intent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO),
            coachMessage = intent?.getStringExtra(NotificationHelper.EXTRA_COACH_MESSAGE),
            coachTitle = intent?.getStringExtra(NotificationHelper.EXTRA_COACH_TITLE)
        )
    }
}
