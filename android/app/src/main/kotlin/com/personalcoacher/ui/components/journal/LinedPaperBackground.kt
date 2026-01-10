package com.personalcoacher.ui.components.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personalcoacher.ui.theme.PersonalCoachTheme

@Composable
fun LinedPaperBackground(
    modifier: Modifier = Modifier,
    lineSpacing: Dp = 28.dp,
    showMargin: Boolean = false,
    marginOffset: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    val backgroundColor = PersonalCoachTheme.extendedColors.journalBackground
    val lineColor = PersonalCoachTheme.extendedColors.journalLines

    Box(modifier = modifier.background(backgroundColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineSpacingPx = lineSpacing.toPx()
            val marginOffsetPx = marginOffset.toPx()

            // Draw horizontal lines
            var y = lineSpacingPx
            while (y < size.height) {
                drawLine(
                    color = lineColor.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += lineSpacingPx
            }

            // Draw margin line (optional, like notebook paper)
            if (showMargin) {
                drawLine(
                    color = Color(0xFFE57373).copy(alpha = 0.5f),
                    start = Offset(marginOffsetPx, 0f),
                    end = Offset(marginOffsetPx, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        content()
    }
}

/**
 * Paper texture background with optimized rendering.
 * Uses a sparse dot pattern instead of dense circles to avoid excessive draw calls.
 * The pattern is drawn with larger spacing to maintain visual effect while being performant.
 */
@Composable
fun PaperTextureBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundColor = PersonalCoachTheme.extendedColors.journalBackground

    Box(
        modifier = modifier.background(backgroundColor)
    ) {
        // Optimized paper texture effect - use larger spacing for performance
        // This reduces draw calls from ~130K to ~2K on a typical screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            val noiseIntensity = 0.04f // Slightly higher intensity to compensate for fewer dots
            val spacing = 16 // Larger spacing = fewer draw calls

            for (x in 0 until size.width.toInt() step spacing) {
                for (y in 0 until size.height.toInt() step spacing) {
                    val alpha = random.nextFloat() * noiseIntensity
                    drawCircle(
                        color = Color.Black.copy(alpha = alpha),
                        radius = 1.5f, // Slightly larger radius to maintain visual density
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }

        content()
    }
}

@Composable
fun PaperCardBackground(
    modifier: Modifier = Modifier,
    showCornerFold: Boolean = true,
    content: @Composable () -> Unit
) {
    val backgroundColor = PersonalCoachTheme.extendedColors.journalBackground
    val lineColor = PersonalCoachTheme.extendedColors.journalLines

    Box(modifier = modifier.background(backgroundColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineSpacingPx = 24.dp.toPx()

            // Draw horizontal lines
            var y = lineSpacingPx
            while (y < size.height) {
                drawLine(
                    color = lineColor.copy(alpha = 0.2f),
                    start = Offset(16.dp.toPx(), y),
                    end = Offset(size.width - 16.dp.toPx(), y),
                    strokeWidth = 0.5.dp.toPx()
                )
                y += lineSpacingPx
            }

            // Draw corner fold effect
            if (showCornerFold) {
                val foldSize = 16.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width - foldSize, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, foldSize)
                    close()
                }
                drawPath(
                    path = path,
                    color = lineColor.copy(alpha = 0.2f)
                )
            }
        }

        content()
    }
}
