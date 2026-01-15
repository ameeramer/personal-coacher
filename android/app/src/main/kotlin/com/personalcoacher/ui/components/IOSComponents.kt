package com.personalcoacher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personalcoacher.ui.theme.IOSSpacing
import com.personalcoacher.ui.theme.PersonalCoachTheme

/**
 * iOS-style translucent card with blur effect and thin border.
 * Follows Apple Human Interface Guidelines for material design.
 */
@Composable
fun IOSCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(IOSSpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val shape = RoundedCornerShape(cornerRadius)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = extendedColors.translucentSurface
            ),
            border = BorderStroke(0.5.dp, extendedColors.thinBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = extendedColors.translucentSurface,
            border = BorderStroke(0.5.dp, extendedColors.thinBorder),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * iOS-style elevated card for prominent UI elements.
 * Uses elevated surface color with subtle shadow.
 */
@Composable
fun IOSElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(IOSSpacing.cardPaddingLarge),
    content: @Composable ColumnScope.() -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors
    val shape = RoundedCornerShape(cornerRadius)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = extendedColors.elevatedSurface
            ),
            border = BorderStroke(0.5.dp, extendedColors.thinBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = extendedColors.elevatedSurface
            ),
            border = BorderStroke(0.5.dp, extendedColors.thinBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * iOS-style section header with large, bold title.
 */
@Composable
fun IOSSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(
            horizontal = IOSSpacing.screenPadding,
            vertical = 8.dp
        )
    ) {
        androidx.compose.material3.Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            androidx.compose.material3.Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * iOS-style list item card for grouped content.
 */
@Composable
fun IOSListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    IOSCard(
        modifier = modifier,
        onClick = onClick,
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(IOSSpacing.cardPadding),
        content = content
    )
}

/**
 * iOS-style translucent surface for navigation bars and headers.
 */
@Composable
fun IOSTranslucentSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val extendedColors = PersonalCoachTheme.extendedColors

    Surface(
        modifier = modifier,
        color = extendedColors.translucentSurface,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Metadata text style for dates, timestamps, etc.
 * Smaller and lighter colored as per iOS guidelines.
 */
@Composable
fun IOSMetadataText(
    text: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    )
}
