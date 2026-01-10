package com.personalcoacher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    val coachTitle: String?
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deepLink = extractDeepLinkFromIntent(intent)

        setContent {
            PersonalCoachTheme {
                PersonalCoachApp(notificationDeepLink = deepLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-compose with new deep link if app is already running
        val deepLink = extractDeepLinkFromIntent(intent)
        if (deepLink.navigateTo != null) {
            setContent {
                PersonalCoachTheme {
                    PersonalCoachApp(notificationDeepLink = deepLink)
                }
            }
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
