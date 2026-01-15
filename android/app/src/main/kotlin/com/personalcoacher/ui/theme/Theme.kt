package com.personalcoacher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// iOS-style spacing and dimensions
object IOSSpacing {
    val cardPadding = 20.dp // Increased from 16dp (+25%)
    val cardPaddingLarge = 24.dp // For main content cards
    val listItemSpacing = 16.dp // Increased from 12dp
    val sectionSpacing = 24.dp
    val screenPadding = 20.dp // Increased from 16dp
    val contentPadding = 20.dp
}

// Custom extended colors for the app
data class ExtendedColors(
    val userBubble: Color,
    val assistantBubble: Color,
    val onUserBubble: Color,
    val onAssistantBubble: Color,
    val journalBackground: Color,
    val journalLines: Color,
    val moodHappy: Color,
    val moodGrateful: Color,
    val moodCalm: Color,
    val moodNeutral: Color,
    val moodAnxious: Color,
    val moodSad: Color,
    val moodFrustrated: Color,
    val moodTired: Color,
    // iOS-style translucent surfaces
    val translucentSurface: Color,
    val translucentSurfaceVariant: Color,
    val thinBorder: Color,
    val elevatedSurface: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        userBubble = Teal600,
        assistantBubble = Gray100,
        onUserBubble = Color.White,
        onAssistantBubble = Gray800,
        journalBackground = Amber50,
        journalLines = Color(0xFFD4A574),
        moodHappy = Color(0xFFFBBF24),
        moodGrateful = Color(0xFF7DD3C0), // Softer teal
        moodCalm = Color(0xFF60A5FA), // Softer blue
        moodNeutral = Color(0xFF737373),
        moodAnxious = Color(0xFFFBBF24),
        moodSad = Color(0xFF9D8FE8), // Softer lavender
        moodFrustrated = Color(0xFFFF6B6B), // Softer red
        moodTired = Color(0xFFB4A7E8), // Softer violet
        translucentSurface = TranslucentLight,
        translucentSurfaceVariant = TranslucentUltraThinLight,
        thinBorder = BorderLight,
        elevatedSurface = SurfaceLight
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Amber600,
    onPrimary = Color.White,
    primaryContainer = Amber100,
    onPrimaryContainer = Amber900,
    secondary = Orange500,
    onSecondary = Color.White,
    secondaryContainer = Amber100,
    onSecondaryContainer = Amber900,
    tertiary = Emerald600,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = ErrorColor,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = BackgroundLight,
    onBackground = Gray900,
    surface = SurfaceLight,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    outline = Gray300,
    outlineVariant = Gray200,
)

private val DarkColorScheme = darkColorScheme(
    primary = Lavender500, // Softer lavender
    onPrimary = Color.White,
    primaryContainer = Lavender900,
    onPrimaryContainer = Lavender300,
    secondary = Lavender600, // Softer
    onSecondary = Color.White,
    secondaryContainer = Lavender800,
    onSecondaryContainer = Lavender300,
    tertiary = Teal500, // Softer teal
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    error = ErrorColor, // Softer red
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = BackgroundDark,
    onBackground = Gray100,
    surface = SurfaceDark,
    onSurface = Gray100,
    surfaceVariant = SurfaceElevatedDark, // iOS elevated surface
    onSurfaceVariant = Gray300,
    outline = Gray600,
    outlineVariant = Gray700,
)

private val LightExtendedColors = ExtendedColors(
    userBubble = Teal600, // Softer teal
    assistantBubble = Gray100,
    onUserBubble = Color.White,
    onAssistantBubble = Gray800,
    journalBackground = Color(0xFFF2F2F7), // iOS system background
    journalLines = Color(0xFFD4A574),
    moodHappy = Color(0xFFFBBF24),
    moodGrateful = Color(0xFF7DD3C0), // Softer
    moodCalm = Color(0xFF60A5FA), // Softer blue
    moodNeutral = Color(0xFF737373),
    moodAnxious = Color(0xFFFBBF24),
    moodSad = Color(0xFF9D8FE8), // Softer lavender
    moodFrustrated = Color(0xFFFF6B6B), // Softer red
    moodTired = Color(0xFFB4A7E8), // Softer violet
    translucentSurface = TranslucentLight,
    translucentSurfaceVariant = TranslucentUltraThinLight,
    thinBorder = BorderLight,
    elevatedSurface = SurfaceLight
)

private val DarkExtendedColors = ExtendedColors(
    userBubble = Lavender600, // Softer lavender
    assistantBubble = SurfaceElevatedDark, // iOS elevated
    onUserBubble = Color.White,
    onAssistantBubble = Gray200,
    journalBackground = BackgroundDark,
    journalLines = Gray700,
    moodHappy = Color(0xFFFBBF24),
    moodGrateful = Color(0xFF7DD3C0), // Softer
    moodCalm = Color(0xFF60A5FA), // Softer blue
    moodNeutral = Color(0xFF737373),
    moodAnxious = Color(0xFFFBBF24),
    moodSad = Color(0xFF9D8FE8), // Softer lavender
    moodFrustrated = Color(0xFFFF6B6B), // Softer red
    moodTired = Color(0xFFB4A7E8), // Softer violet
    translucentSurface = TranslucentDark,
    translucentSurfaceVariant = TranslucentUltraThinDark,
    thinBorder = BorderDark,
    elevatedSurface = SurfaceElevatedDark
)

@Composable
fun PersonalCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object PersonalCoachTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
