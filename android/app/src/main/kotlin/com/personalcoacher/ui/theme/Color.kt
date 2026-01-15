package com.personalcoacher.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Colors - Amber/Orange (Light mode)
val Amber50 = Color(0xFFFFFBEB)
val Amber100 = Color(0xFFFEF3C7)
val Amber200 = Color(0xFFFDE68A)
val Amber300 = Color(0xFFFCD34D)
val Amber400 = Color(0xFFFBBF24)
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFD97706)
val Amber700 = Color(0xFFB45309)
val Amber800 = Color(0xFF92400E)
val Amber900 = Color(0xFF78350F)

// Orange
val Orange500 = Color(0xFFF97316)
val Orange600 = Color(0xFFEA580C)

// iOS-Style Softer Lavender/Indigo Palette (Dark mode primary)
// These are desaturated and calmer than the original violet
val Lavender50 = Color(0xFFF5F3FF)
val Lavender100 = Color(0xFFEDE9FE)
val Lavender200 = Color(0xFFDDD6FE)
val Lavender300 = Color(0xFFC4B5FD)
val Lavender400 = Color(0xFFA78BFA)
val Lavender500 = Color(0xFF9D8FE8) // Softer, more desaturated
val Lavender600 = Color(0xFF8B82D1) // Primary accent - calmer lavender
val Lavender700 = Color(0xFF7673BA)
val Lavender800 = Color(0xFF635FA3)
val Lavender900 = Color(0xFF514D8C)

// Secondary Colors - Softer Indigo (replacing intense violet)
val Violet400 = Color(0xFFB4A7E8) // Softer
val Violet500 = Color(0xFF9D8FE8) // Softer, desaturated
val Violet600 = Color(0xFF8B82D1) // Softer
val Violet700 = Color(0xFF7673BA)
val Violet800 = Color(0xFF635FA3)
val Violet900 = Color(0xFF514D8C)

val Purple600 = Color(0xFF9D8FE8) // Softer purple
val Purple900 = Color(0xFF514D8C)

// Softer Teal (replacing intense emerald)
val Teal400 = Color(0xFF5EEAD4)
val Teal500 = Color(0xFF7DD3C0) // Softer, calmer
val Teal600 = Color(0xFF6BC4B3) // Primary teal - softer
val Teal700 = Color(0xFF5AB4A3)

// Legacy Emerald (kept for compatibility, now softer)
val Emerald500 = Color(0xFF7DD3C0) // Softer teal-green
val Emerald600 = Color(0xFF6BC4B3) // Softer
val Emerald700 = Color(0xFF5AB4A3)

// Neutrals - Slightly warmer for iOS feel
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFE5E5E5)
val Gray300 = Color(0xFFD4D4D4)
val Gray400 = Color(0xFFA3A3A3)
val Gray500 = Color(0xFF737373)
val Gray600 = Color(0xFF525252)
val Gray700 = Color(0xFF404040)
val Gray800 = Color(0xFF262626)
val Gray900 = Color(0xFF171717)

// iOS-style translucent backgrounds
val BackgroundLight = Color(0xFFF2F2F7) // iOS system background
val BackgroundDark = Color(0xFF000000) // True black for OLED
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1C1C1E) // iOS secondary system background
val SurfaceElevatedDark = Color(0xFF2C2C2E) // iOS tertiary system background

// iOS-style translucent colors (for blur backgrounds)
val TranslucentLight = Color(0xE6FFFFFF) // 90% white
val TranslucentDark = Color(0xCC1C1C1E) // 80% dark
val TranslucentUltraThinLight = Color(0x99FFFFFF) // 60% white
val TranslucentUltraThinDark = Color(0x801C1C1E) // 50% dark

// Thin border colors for iOS-style cards
val BorderLight = Color(0x1A000000) // 10% black
val BorderDark = Color(0x1AFFFFFF) // 10% white

// Status colors - Slightly softer
val ErrorColor = Color(0xFFFF6B6B) // Softer red
val SuccessColor = Color(0xFF51CF66) // Softer green
val WarningColor = Color(0xFFFFD43B) // Softer yellow

// Chat bubble colors - Using softer palette
val UserBubbleLight = Teal600
val UserBubbleDark = Lavender600
val AssistantBubbleLight = Gray100
val AssistantBubbleDark = Color(0xFF2C2C2E) // iOS elevated surface
