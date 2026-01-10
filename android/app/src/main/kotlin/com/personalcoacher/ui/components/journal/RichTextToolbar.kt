package com.personalcoacher.ui.components.journal

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

data class TextColor(
    val name: String,
    val color: Color,
    val htmlColor: String
)

val textColors = listOf(
    TextColor("Default", Color.Unspecified, ""),
    TextColor("Red", Color(0xFFDC2626), "#DC2626"),
    TextColor("Orange", Color(0xFFF97316), "#F97316"),
    TextColor("Amber", Color(0xFFD97706), "#D97706"),
    TextColor("Green", Color(0xFF16A34A), "#16A34A"),
    TextColor("Blue", Color(0xFF2563EB), "#2563EB"),
    TextColor("Purple", Color(0xFF9333EA), "#9333EA"),
    TextColor("Pink", Color(0xFFDB2777), "#DB2777")
)

/**
 * Format types for detecting active formatting in text
 */
enum class FormatType {
    BOLD, ITALIC, STRIKETHROUGH, H1, H2, H3, BULLET_LIST, NUMBERED_LIST, QUOTE, CODE, LINK
}

@Composable
fun RichTextToolbar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isSourceMode: Boolean,
    onSourceModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showHeadingPicker by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }

    // Detect active formats at cursor position
    val activeFormats = remember(textFieldValue) {
        detectActiveFormats(textFieldValue)
    }

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
            // Bold - toggle behavior
            ToolbarButton(
                icon = Icons.Default.FormatBold,
                contentDescription = "Bold",
                isSelected = activeFormats.contains(FormatType.BOLD),
                onClick = {
                    toggleMarkdownFormat(textFieldValue, onValueChange, "**", "**", FormatType.BOLD, activeFormats, isSourceMode)
                }
            )

            // Italic - toggle behavior
            ToolbarButton(
                icon = Icons.Default.FormatItalic,
                contentDescription = "Italic",
                isSelected = activeFormats.contains(FormatType.ITALIC),
                onClick = {
                    toggleMarkdownFormat(textFieldValue, onValueChange, "*", "*", FormatType.ITALIC, activeFormats, isSourceMode)
                }
            )

            // Strikethrough - toggle behavior
            ToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                contentDescription = "Strikethrough",
                isSelected = activeFormats.contains(FormatType.STRIKETHROUGH),
                onClick = {
                    toggleMarkdownFormat(textFieldValue, onValueChange, "~~", "~~", FormatType.STRIKETHROUGH, activeFormats, isSourceMode)
                }
            )

            ToolbarDivider()

            // Heading picker
            Box {
                ToolbarButton(
                    icon = Icons.Default.Title,
                    contentDescription = "Heading",
                    isSelected = activeFormats.any { it in listOf(FormatType.H1, FormatType.H2, FormatType.H3) },
                    onClick = { showHeadingPicker = true }
                )

                DropdownMenu(
                    expanded = showHeadingPicker,
                    onDismissRequest = { showHeadingPicker = false }
                ) {
                    listOf(
                        Triple("# ", "Heading 1", FormatType.H1),
                        Triple("## ", "Heading 2", FormatType.H2),
                        Triple("### ", "Heading 3", FormatType.H3)
                    ).forEach { (prefix, label, formatType) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label)
                                    if (activeFormats.contains(formatType)) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                toggleLinePrefix(textFieldValue, onValueChange, prefix, formatType, activeFormats, isSourceMode)
                                showHeadingPicker = false
                            }
                        )
                    }
                }
            }

            ToolbarDivider()

            // Bullet list - toggle behavior
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Bullet list",
                isSelected = activeFormats.contains(FormatType.BULLET_LIST),
                onClick = {
                    toggleLinePrefix(textFieldValue, onValueChange, "- ", FormatType.BULLET_LIST, activeFormats, isSourceMode)
                }
            )

            // Numbered list - toggle behavior
            ToolbarButton(
                icon = Icons.Default.FormatListNumbered,
                contentDescription = "Numbered list",
                isSelected = activeFormats.contains(FormatType.NUMBERED_LIST),
                onClick = {
                    toggleLinePrefix(textFieldValue, onValueChange, "1. ", FormatType.NUMBERED_LIST, activeFormats, isSourceMode)
                }
            )

            // Quote - toggle behavior
            ToolbarButton(
                icon = Icons.Default.FormatQuote,
                contentDescription = "Quote",
                isSelected = activeFormats.contains(FormatType.QUOTE),
                onClick = {
                    toggleLinePrefix(textFieldValue, onValueChange, "> ", FormatType.QUOTE, activeFormats, isSourceMode)
                }
            )

            // Code
            ToolbarButton(
                icon = Icons.Default.Code,
                contentDescription = "Code",
                isSelected = activeFormats.contains(FormatType.CODE),
                onClick = {
                    toggleMarkdownFormat(textFieldValue, onValueChange, "`", "`", FormatType.CODE, activeFormats, isSourceMode)
                }
            )

            ToolbarDivider()

            // Link
            ToolbarButton(
                icon = Icons.Default.Link,
                contentDescription = "Link",
                isSelected = activeFormats.contains(FormatType.LINK),
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
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Text Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            textColors.forEach { textColor ->
                                ColorButton(
                                    color = textColor,
                                    onClick = {
                                        if (textColor.htmlColor.isNotEmpty()) {
                                            insertColoredText(textFieldValue, onValueChange, textColor.htmlColor, isSourceMode)
                                        }
                                        showColorPicker = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom color picker button
                        TextButton(
                            onClick = {
                                showColorPicker = false
                                showCustomColorPicker = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Custom color...")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Source/WYSIWYG mode toggle
            ToolbarButton(
                icon = if (isSourceMode) Icons.Default.RemoveRedEye else Icons.Default.Edit,
                contentDescription = if (isSourceMode) "WYSIWYG mode" else "Source mode",
                isSelected = isSourceMode,
                onClick = { onSourceModeChange(!isSourceMode) }
            )
        }
    }

    // Link dialog
    if (showLinkDialog) {
        LinkDialog(
            textFieldValue = textFieldValue,
            onValueChange = onValueChange,
            onDismiss = { showLinkDialog = false },
            isSourceMode = isSourceMode
        )
    }

    // Custom color picker dialog
    if (showCustomColorPicker) {
        CustomColorPickerDialog(
            onColorSelected = { color ->
                insertColoredText(textFieldValue, onValueChange, color, isSourceMode)
                showCustomColorPicker = false
            },
            onDismiss = { showCustomColorPicker = false }
        )
    }
}

@Composable
private fun LinkDialog(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    isSourceMode: Boolean
) {
    var linkUrl by remember { mutableStateOf("") }
    val selection = textFieldValue.selection
    val selectedText = if (selection.collapsed) "" else {
        textFieldValue.text.substring(selection.min, selection.max)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Link") },
        text = {
            Column {
                if (selectedText.isNotEmpty()) {
                    Text(
                        "Selected text: \"$selectedText\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (linkUrl.isNotBlank()) {
                        insertLink(textFieldValue, onValueChange, linkUrl, isSourceMode)
                    }
                    onDismiss()
                }
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
private fun CustomColorPickerDialog(
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }

    val selectedColor = remember(hue, saturation, lightness) {
        Color.hsl(hue, saturation, lightness)
    }

    val hexColor = remember(selectedColor) {
        val argb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f - (1f - lightness) * saturation))
        String.format("#%06X", 0xFFFFFF and argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Custom Color") },
        text = {
            Column {
                // Color preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(selectedColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hue slider
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    )
                )

                // Saturation slider
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    )
                )

                // Lightness slider
                Text("Lightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = lightness,
                    onValueChange = { lightness = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    )
                )

                Text(
                    "Hex: $hexColor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(hexColor) }) {
                Text("Apply")
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
            .width(1.dp)
            .size(width = 1.dp, height = 24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}

/**
 * Detect active markdown formats at cursor position
 */
private fun detectActiveFormats(textFieldValue: TextFieldValue): Set<FormatType> {
    val formats = mutableSetOf<FormatType>()
    val text = textFieldValue.text
    val cursorPos = textFieldValue.selection.start

    if (text.isEmpty()) return formats

    // Find the current line
    val lineStart = text.lastIndexOf('\n', (cursorPos - 1).coerceAtLeast(0)) + 1
    val lineEnd = text.indexOf('\n', cursorPos).let { if (it == -1) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)

    // Check line prefixes
    when {
        currentLine.startsWith("# ") -> formats.add(FormatType.H1)
        currentLine.startsWith("## ") -> formats.add(FormatType.H2)
        currentLine.startsWith("### ") -> formats.add(FormatType.H3)
        currentLine.startsWith("- ") || currentLine.startsWith("* ") -> formats.add(FormatType.BULLET_LIST)
        currentLine.matches(Regex("^\\d+\\. .*")) -> formats.add(FormatType.NUMBERED_LIST)
        currentLine.startsWith("> ") -> formats.add(FormatType.QUOTE)
    }

    // Check inline formats by looking for surrounding markers
    // This is a simplified check - looks for patterns around cursor
    val textBeforeCursor = text.substring(0, cursorPos)
    val textAfterCursor = text.substring(cursorPos)

    // Bold check: **text** or __text__
    if ((textBeforeCursor.contains("**") && textAfterCursor.contains("**")) ||
        (textBeforeCursor.endsWith("**") && !textAfterCursor.startsWith("**"))) {
        val lastOpen = textBeforeCursor.lastIndexOf("**")
        val nextClose = textAfterCursor.indexOf("**")
        if (lastOpen != -1 && nextClose != -1) {
            // Check if we're inside the markers
            val textBetween = textBeforeCursor.substring(lastOpen + 2)
            if (!textBetween.contains("**")) {
                formats.add(FormatType.BOLD)
            }
        }
    }

    // Italic check: *text* (but not **)
    val italicPattern = Regex("(?<!\\*)\\*(?!\\*)")
    val beforeMatches = italicPattern.findAll(textBeforeCursor).count()
    val afterMatches = italicPattern.findAll(textAfterCursor).count()
    if (beforeMatches % 2 == 1 && afterMatches >= 1) {
        formats.add(FormatType.ITALIC)
    }

    // Strikethrough check: ~~text~~
    if (textBeforeCursor.contains("~~") && textAfterCursor.contains("~~")) {
        val lastOpen = textBeforeCursor.lastIndexOf("~~")
        val nextClose = textAfterCursor.indexOf("~~")
        if (lastOpen != -1 && nextClose != -1) {
            val textBetween = textBeforeCursor.substring(lastOpen + 2)
            if (!textBetween.contains("~~")) {
                formats.add(FormatType.STRIKETHROUGH)
            }
        }
    }

    // Code check: `text`
    val codeMatches = textBeforeCursor.count { it == '`' }
    val codeAfterMatches = textAfterCursor.count { it == '`' }
    if (codeMatches % 2 == 1 && codeAfterMatches >= 1) {
        formats.add(FormatType.CODE)
    }

    // Link check: [text](url)
    if (textBeforeCursor.contains("[") && textAfterCursor.contains("](")) {
        formats.add(FormatType.LINK)
    }

    return formats
}

/**
 * Toggle inline markdown format (like **bold**, *italic*, etc.)
 */
private fun toggleMarkdownFormat(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    prefix: String,
    suffix: String,
    formatType: FormatType,
    activeFormats: Set<FormatType>,
    isSourceMode: Boolean
) {
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    if (activeFormats.contains(formatType)) {
        // Remove formatting - find and remove the markers
        val textBeforeCursor = text.substring(0, selection.start)
        val textAfterCursor = text.substring(selection.end)

        val lastPrefixIndex = textBeforeCursor.lastIndexOf(prefix)
        val nextSuffixIndex = textAfterCursor.indexOf(suffix)

        if (lastPrefixIndex != -1 && nextSuffixIndex != -1) {
            val newText = text.substring(0, lastPrefixIndex) +
                    text.substring(lastPrefixIndex + prefix.length, selection.end) +
                    text.substring(selection.end + suffix.length)

            val newCursorPos = (selection.start - prefix.length).coerceAtLeast(0)
            onValueChange(
                textFieldValue.copy(
                    text = newText,
                    selection = TextRange(newCursorPos)
                )
            )
        }
    } else {
        // Add formatting
        if (selection.collapsed) {
            // No selection - insert markers and place cursor between them
            val newText = text.substring(0, selection.start) + prefix + suffix + text.substring(selection.end)
            onValueChange(
                textFieldValue.copy(
                    text = newText,
                    selection = TextRange(selection.start + prefix.length)
                )
            )
        } else {
            // Wrap selection with markers
            val selectedText = text.substring(selection.min, selection.max)
            val newText = text.replaceRange(selection.min, selection.max, "$prefix$selectedText$suffix")
            onValueChange(
                textFieldValue.copy(
                    text = newText,
                    selection = TextRange(selection.min + prefix.length, selection.max + prefix.length)
                )
            )
        }
    }
}

/**
 * Toggle line prefix format (like headings, lists, quotes)
 */
private fun toggleLinePrefix(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    prefix: String,
    formatType: FormatType,
    activeFormats: Set<FormatType>,
    isSourceMode: Boolean
) {
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    // Find the start of the current line
    val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)) + 1
    val lineEnd = text.indexOf('\n', selection.start).let { if (it == -1) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)

    if (activeFormats.contains(formatType)) {
        // Remove the prefix
        val prefixToRemove = when (formatType) {
            FormatType.H1 -> "# "
            FormatType.H2 -> "## "
            FormatType.H3 -> "### "
            FormatType.BULLET_LIST -> if (currentLine.startsWith("- ")) "- " else "* "
            FormatType.NUMBERED_LIST -> currentLine.takeWhile { it.isDigit() || it == '.' || it == ' ' }.let {
                if (it.endsWith(" ")) it else ""
            }
            FormatType.QUOTE -> "> "
            else -> prefix
        }

        if (currentLine.startsWith(prefixToRemove)) {
            val newText = text.substring(0, lineStart) +
                    currentLine.removePrefix(prefixToRemove) +
                    text.substring(lineEnd)

            val newCursorPos = (selection.start - prefixToRemove.length).coerceAtLeast(lineStart)
            onValueChange(
                textFieldValue.copy(
                    text = newText,
                    selection = TextRange(newCursorPos)
                )
            )
        }
    } else {
        // Remove any existing line prefix first, then add the new one
        val existingPrefix = when {
            currentLine.startsWith("### ") -> "### "
            currentLine.startsWith("## ") -> "## "
            currentLine.startsWith("# ") -> "# "
            currentLine.startsWith("- ") -> "- "
            currentLine.startsWith("* ") -> "* "
            currentLine.startsWith("> ") -> "> "
            currentLine.matches(Regex("^\\d+\\. .*")) -> currentLine.takeWhile { it.isDigit() || it == '.' || it == ' ' }
            else -> ""
        }

        val lineWithoutPrefix = currentLine.removePrefix(existingPrefix)
        val newText = text.substring(0, lineStart) + prefix + lineWithoutPrefix + text.substring(lineEnd)

        val prefixDiff = prefix.length - existingPrefix.length
        onValueChange(
            textFieldValue.copy(
                text = newText,
                selection = TextRange(selection.start + prefixDiff, selection.end + prefixDiff)
            )
        )
    }
}

private fun insertLink(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    url: String,
    isSourceMode: Boolean
) {
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    val selectedText = if (selection.collapsed) "link text" else {
        text.substring(selection.min, selection.max)
    }

    val linkMarkdown = "[$selectedText]($url)"
    val newText = text.replaceRange(selection.min, selection.max, linkMarkdown)

    // Position cursor at end of link
    val newCursorPos = selection.min + linkMarkdown.length
    onValueChange(
        textFieldValue.copy(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    )
}

private fun insertColoredText(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    htmlColor: String,
    isSourceMode: Boolean
) {
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    val selectedText = if (selection.collapsed) "colored text" else {
        text.substring(selection.min, selection.max)
    }

    val colorSpan = "<span style=\"color:$htmlColor\">$selectedText</span>"
    val newText = text.replaceRange(selection.min, selection.max, colorSpan)

    onValueChange(
        textFieldValue.copy(
            text = newText,
            selection = TextRange(selection.min + colorSpan.length)
        )
    )
}
