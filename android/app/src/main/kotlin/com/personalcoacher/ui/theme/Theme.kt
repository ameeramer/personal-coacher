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
    val moodTired: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        userBubble = Emerald600,
        assistantBubble = Gray100,
        onUserBubble = Color.White,
        onAssistantBubble = Gray800,
        journalBackground = Amber50,
        journalLines = Color(0xFFD4A574),
        moodHappy = Color(0xFFFBBF24),
        moodGrateful = Color(0xFF10B981),
        moodCalm = Color(0xFF3B82F6),
        moodNeutral = Color(0xFF6B7280),
        moodAnxious = Color(0xFFF59E0B),
        moodSad = Color(0xFF6366F1),
        moodFrustrated = Color(0xFFEF4444),
        moodTired = Color(0xFF8B5CF6)
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
    primary = Violet500,
    onPrimary = Color.White,
    primaryContainer = Violet900,
    onPrimaryContainer = Violet400,
    secondary = Purple600,
    onSecondary = Color.White,
    secondaryContainer = Purple900,
    onSecondaryContainer = Violet400,
    tertiary = Emerald500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFF87171),
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = BackgroundDark,
    onBackground = Gray100,
    surface = SurfaceDark,
    onSurface = Gray100,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray300,
    outline = Gray600,
    outlineVariant = Gray700,
)

private val LightExtendedColors = ExtendedColors(
    userBubble = Emerald600,
    assistantBubble = Gray100,
    onUserBubble = Color.White,
    onAssistantBubble = Gray800,
    journalBackground = Amber50,
    journalLines = Color(0xFFD4A574),
    moodHappy = Color(0xFFFBBF24),
    moodGrateful = Color(0xFF10B981),
    moodCalm = Color(0xFF3B82F6),
    moodNeutral = Color(0xFF6B7280),
    moodAnxious = Color(0xFFF59E0B),
    moodSad = Color(0xFF6366F1),
    moodFrustrated = Color(0xFFEF4444),
    moodTired = Color(0xFF8B5CF6)
)

private val DarkExtendedColors = ExtendedColors(
    userBubble = Violet600,
    assistantBubble = Gray800,
    onUserBubble = Color.White,
    onAssistantBubble = Gray200,
    journalBackground = Gray900,
    journalLines = Gray700,
    moodHappy = Color(0xFFFBBF24),
    moodGrateful = Color(0xFF10B981),
    moodCalm = Color(0xFF3B82F6),
    moodNeutral = Color(0xFF6B7280),
    moodAnxious = Color(0xFFF59E0B),
    moodSad = Color(0xFF6366F1),
    moodFrustrated = Color(0xFFEF4444),
    moodTired = Color(0xFF8B5CF6)
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
