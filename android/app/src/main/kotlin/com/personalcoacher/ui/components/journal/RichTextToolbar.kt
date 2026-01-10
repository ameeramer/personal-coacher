package com.personalcoacher.ui.components.journal

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TextColor(
    val name: String,
    val color: Color,
    val htmlColor: String
)

val textColors = listOf(
    TextColor("Default", Color.Unspecified, ""),
    TextColor("Red", Color(0xFFEF4444), "#ef4444"),
    TextColor("Orange", Color(0xFFF97316), "#f97316"),
    TextColor("Green", Color(0xFF22C55E), "#22c55e"),
    TextColor("Blue", Color(0xFF3B82F6), "#3b82f6"),
    TextColor("Purple", Color(0xFFA855F7), "#a855f7"),
    TextColor("Pink", Color(0xFFDB2777), "#db2777")
)

/**
 * Rich text toolbar that communicates with the WebView-based WYSIWYG editor.
 * Uses HTML formatting commands like the web app.
 *
 * @param webView Reference to the WebView for executing commands
 * @param activeFormats Set of currently active formats at cursor (e.g., "bold", "italic", "h1")
 * @param isSourceMode Whether the editor is in source (HTML) mode
 * @param onSourceModeChange Called to toggle source mode
 */
@Composable
fun RichTextToolbar(
    webView: WebView?,
    activeFormats: Set<String>,
    isSourceMode: Boolean,
    onSourceModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showHeadingPicker by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#000000") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bold
            ToolbarButton(
                icon = Icons.Default.FormatBold,
                contentDescription = "Bold",
                isSelected = activeFormats.contains("bold"),
                onClick = {
                    if (isSourceMode) {
                        insertSourceHtml(webView, "<b>", "</b>")
                    } else {
                        executeCommand(webView, "bold")
                    }
                }
            )

            // Italic
            ToolbarButton(
                icon = Icons.Default.FormatItalic,
                contentDescription = "Italic",
                isSelected = activeFormats.contains("italic"),
                onClick = {
                    if (isSourceMode) {
                        insertSourceHtml(webView, "<i>", "</i>")
                    } else {
                        executeCommand(webView, "italic")
                    }
                }
            )

            // Strikethrough
            ToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                contentDescription = "Strikethrough",
                isSelected = activeFormats.contains("strikethrough"),
                onClick = {
                    if (isSourceMode) {
                        insertSourceHtml(webView, "<s>", "</s>")
                    } else {
                        executeCommand(webView, "strikethrough")
                    }
                }
            )

            ToolbarDivider()

            // Heading picker
            Box {
                ToolbarButton(
                    icon = Icons.Default.Title,
                    contentDescription = "Heading",
                    isSelected = activeFormats.any { it in listOf("h1", "h2", "h3") },
                    onClick = { showHeadingPicker = true }
                )

                DropdownMenu(
                    expanded = showHeadingPicker,
                    onDismissRequest = { showHeadingPicker = false }
                ) {
                    listOf(
                        Triple("h1", "Heading 1", "<h1>"),
                        Triple("h2", "Heading 2", "<h2>"),
                        Triple("h3", "Heading 3", "<h3>")
                    ).forEach { (command, label, tag) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label)
                                    if (activeFormats.contains(command)) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                if (isSourceMode) {
                                    insertSourceHtml(webView, tag, "</${command}>")
                                } else {
                                    executeCommand(webView, command)
                                }
                                showHeadingPicker = false
                            }
                        )
                    }
                }
            }

            ToolbarDivider()

            // Bullet list
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Bullet list",
                isSelected = activeFormats.contains("ul"),
                onClick = {
                    if (isSourceMode) {
                        insertSourceHtml(webView, "<ul>\n  <li>", "</li>\n</ul>")
                    } else {
                        executeCommand(webView, "ul")
                    }
                }
            )

            // Numbered list
            ToolbarButton(
                icon = Icons.Default.FormatListNumbered,
                contentDescription = "Numbered list",
                isSelected = activeFormats.contains("ol"),
                onClick = {
                    if (isSourceMode) {
                        insertSourceHtml(webView, "<ol>\n  <li>", "</li>\n</ol>")
                    } else {
                        executeCommand(webView, "ol")
                    }
                }
            )

            // Quote
            ToolbarButton(
                icon = Icons.Default.FormatQuote,
                contentDescription = "Quote",
                isSelected = activeFormats.contains("quote"),
                onClick = {
                    if (isSourceMode) {
                        insertSourceHtml(webView, "<blockquote>", "</blockquote>")
                    } else {
                        executeCommand(webView, "quote")
                    }
                }
            )

            ToolbarDivider()

            // Link
            ToolbarButton(
                icon = Icons.Default.Link,
                contentDescription = "Link",
                onClick = { showLinkDialog = true }
            )

            // Color picker
            Box {
                ToolbarButton(
                    icon = Icons.Default.Palette,
                    contentDescription = "Text color",
                    onClick = { showColorPicker = true }
                )

                DropdownMenu(
                    expanded = showColorPicker,
                    onDismissRequest = { showColorPicker = false }
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Text Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "Select text first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            textColors.forEach { textColor ->
                                ColorButton(
                                    color = textColor,
                                    onClick = {
                                        if (textColor.htmlColor.isEmpty()) {
                                            if (isSourceMode) {
                                                // Can't easily remove formatting in source mode
                                            } else {
                                                executeCommand(webView, "removeFormat")
                                            }
                                        } else {
                                            if (isSourceMode) {
                                                insertSourceHtml(
                                                    webView,
                                                    "<span style=\"color: ${textColor.htmlColor}\">",
                                                    "</span>"
                                                )
                                            } else {
                                                executeCommand(webView, "foreColor", textColor.htmlColor)
                                            }
                                        }
                                        showColorPicker = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom color picker section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Custom Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Full HSV color picker
                        HSVColorPicker(
                            selectedHex = customColorHex,
                            onColorChange = { newHex ->
                                customColorHex = newHex
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Hex input and apply button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Color preview
                            val previewColor = try {
                                Color(android.graphics.Color.parseColor(customColorHex))
                            } catch (e: Exception) {
                                Color.Black
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(previewColor)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )

                            // Hex input
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                BasicTextField(
                                    value = customColorHex,
                                    onValueChange = { newValue ->
                                        // Allow editing and validate format
                                        val filtered = newValue.uppercase().filter { it.isDigit() || it in 'A'..'F' || it == '#' }
                                        if (filtered.length <= 7) {
                                            customColorHex = if (filtered.startsWith("#")) filtered else "#$filtered"
                                        }
                                    },
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                                )
                            }

                            // Apply button
                            TextButton(
                                onClick = {
                                    if (customColorHex.length == 7 && customColorHex.startsWith("#")) {
                                        if (isSourceMode) {
                                            insertSourceHtml(
                                                webView,
                                                "<span style=\"color: $customColorHex\">",
                                                "</span>"
                                            )
                                        } else {
                                            executeCommand(webView, "foreColor", customColorHex)
                                        }
                                        showColorPicker = false
                                    }
                                },
                                enabled = customColorHex.length == 7 && customColorHex.startsWith("#")
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Source/WYSIWYG mode toggle
            ToolbarButton(
                icon = if (isSourceMode) Icons.Default.RemoveRedEye else Icons.Default.Code,
                contentDescription = if (isSourceMode) "WYSIWYG mode" else "Source mode",
                isSelected = isSourceMode,
                onClick = { onSourceModeChange(!isSourceMode) }
            )
        }
    }

    // Link dialog
    if (showLinkDialog) {
        LinkDialog(
            webView = webView,
            isSourceMode = isSourceMode,
            onDismiss = { showLinkDialog = false }
        )
    }
}

/**
 * Validates a URL to ensure it's safe for insertion.
 * Blocks javascript: and data: URLs to prevent XSS attacks.
 */
private fun isValidUrl(url: String): Boolean {
    val trimmed = url.trim().lowercase()
    // Block potentially dangerous URL schemes
    if (trimmed.startsWith("javascript:") ||
        trimmed.startsWith("data:") ||
        trimmed.startsWith("vbscript:")) {
        return false
    }
    // Must be a valid URL format (http, https, mailto, tel, or relative path)
    return trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("mailto:") ||
            trimmed.startsWith("tel:") ||
            trimmed.startsWith("/") ||
            trimmed.startsWith("#") ||
            (!trimmed.contains(":") && trimmed.isNotEmpty())
}

@Composable
private fun LinkDialog(
    webView: WebView?,
    isSourceMode: Boolean,
    onDismiss: () -> Unit
) {
    var linkUrl by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val isValid = linkUrl.isBlank() || isValidUrl(linkUrl)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Link") },
        text = {
            Column {
                Text(
                    "Select text in the editor first, then enter URL:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = {
                        linkUrl = it
                        showError = false
                    },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    isError = showError || (!isValid && linkUrl.isNotBlank()),
                    supportingText = if (!isValid && linkUrl.isNotBlank()) {
                        { Text("Invalid URL. Use http://, https://, mailto:, or tel:") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (linkUrl.isNotBlank() && isValidUrl(linkUrl)) {
                        if (isSourceMode) {
                            insertSourceHtml(webView, "<a href=\"$linkUrl\">", "</a>")
                        } else {
                            executeCommand(webView, "link", linkUrl)
                        }
                        onDismiss()
                    } else {
                        showError = true
                    }
                },
                enabled = linkUrl.isNotBlank() && isValid
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ColorButton(
    color: TextColor,
    onClick: () -> Unit
) {
    val displayColor = if (color.color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color.color
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(displayColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = color.name }
    )
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}

/**
 * Full HSV (Hue, Saturation, Value) color picker with three sliders.
 * Uses BoxWithConstraints to properly calculate slider thumb positions.
 */
@Composable
private fun HSVColorPicker(
    selectedHex: String,
    onColorChange: (String) -> Unit
) {
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var value by remember { mutableStateOf(1f) }

    // Initialize from hex color
    androidx.compose.runtime.LaunchedEffect(selectedHex) {
        try {
            if (selectedHex.length == 7 && selectedHex.startsWith("#")) {
                val color = android.graphics.Color.parseColor(selectedHex)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(color, hsv)
                hue = hsv[0]
                saturation = hsv[1]
                value = hsv[2]
            }
        } catch (e: Exception) {
            // Invalid color, keep current values
        }
    }

    // Helper function to update color and notify
    fun updateColor(newHue: Float = hue, newSat: Float = saturation, newVal: Float = value) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(newHue, newSat, newVal))
        val hex = String.format("#%06X", 0xFFFFFF and rgb)
        onColorChange(hex)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hue slider
        Column {
            Text(
                text = "Hue",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                            hue = newHue
                            updateColor(newHue = newHue)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newHue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            hue = newHue
                            updateColor(newHue = newHue)
                        }
                    }
            ) {
                // Guard against invalid maxWidth values
                if (maxWidth.value > 0 && maxWidth.value < Float.MAX_VALUE) {
                    val thumbOffset = ((hue / 360f) * maxWidth.value).dp - 2.dp
                    // Slider thumb indicator
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset.coerceIn(0.dp, maxWidth - 4.dp))
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // Saturation slider
        Column {
            Text(
                text = "Saturation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newSat = (offset.x / size.width).coerceIn(0f, 1f)
                            saturation = newSat
                            updateColor(newSat = newSat)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newSat = (change.position.x / size.width).coerceIn(0f, 1f)
                            saturation = newSat
                            updateColor(newSat = newSat)
                        }
                    }
            ) {
                // Guard against invalid maxWidth values
                if (maxWidth.value > 0 && maxWidth.value < Float.MAX_VALUE) {
                    val thumbOffset = (saturation * maxWidth.value).dp - 2.dp
                    // Slider thumb indicator
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset.coerceIn(0.dp, maxWidth - 4.dp))
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // Value (brightness) slider
        Column {
            Text(
                text = "Brightness",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black,
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newVal = (offset.x / size.width).coerceIn(0f, 1f)
                            value = newVal
                            updateColor(newVal = newVal)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newVal = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = newVal
                            updateColor(newVal = newVal)
                        }
                    }
            ) {
                // Guard against invalid maxWidth values
                if (maxWidth.value > 0 && maxWidth.value < Float.MAX_VALUE) {
                    val thumbOffset = (value * maxWidth.value).dp - 2.dp
                    // Slider thumb indicator
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset.coerceIn(0.dp, maxWidth - 4.dp))
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
