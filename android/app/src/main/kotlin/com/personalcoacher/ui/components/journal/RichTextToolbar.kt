package com.personalcoacher.ui.components.journal

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun RichTextToolbar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isPreviewMode: Boolean,
    onPreviewModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showHeadingPicker by remember { mutableStateOf(false) }

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
                onClick = {
                    insertMarkdown(textFieldValue, onValueChange, "**", "**")
                }
            )

            // Italic
            ToolbarButton(
                icon = Icons.Default.FormatItalic,
                contentDescription = "Italic",
                onClick = {
                    insertMarkdown(textFieldValue, onValueChange, "*", "*")
                }
            )

            // Strikethrough
            ToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                contentDescription = "Strikethrough",
                onClick = {
                    insertMarkdown(textFieldValue, onValueChange, "~~", "~~")
                }
            )

            ToolbarDivider()

            // Heading picker
            Box {
                ToolbarButton(
                    icon = Icons.Default.Title,
                    contentDescription = "Heading",
                    onClick = { showHeadingPicker = true }
                )

                DropdownMenu(
                    expanded = showHeadingPicker,
                    onDismissRequest = { showHeadingPicker = false }
                ) {
                    listOf("# " to "Heading 1", "## " to "Heading 2", "### " to "Heading 3").forEach { (prefix, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                insertLinePrefix(textFieldValue, onValueChange, prefix)
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
                onClick = {
                    insertLinePrefix(textFieldValue, onValueChange, "- ")
                }
            )

            // Numbered list
            ToolbarButton(
                icon = Icons.Default.FormatListNumbered,
                contentDescription = "Numbered list",
                onClick = {
                    insertLinePrefix(textFieldValue, onValueChange, "1. ")
                }
            )

            // Quote
            ToolbarButton(
                icon = Icons.Default.FormatQuote,
                contentDescription = "Quote",
                onClick = {
                    insertLinePrefix(textFieldValue, onValueChange, "> ")
                }
            )

            // Code
            ToolbarButton(
                icon = Icons.Default.Code,
                contentDescription = "Code",
                onClick = {
                    insertMarkdown(textFieldValue, onValueChange, "`", "`")
                }
            )

            ToolbarDivider()

            // Link
            ToolbarButton(
                icon = Icons.Default.Link,
                contentDescription = "Link",
                onClick = {
                    val selection = textFieldValue.selection
                    val selectedText = if (selection.collapsed) "link text" else {
                        textFieldValue.text.substring(selection.min, selection.max)
                    }
                    val newText = textFieldValue.text.replaceRange(
                        selection.min,
                        selection.max,
                        "[$selectedText](url)"
                    )
                    onValueChange(
                        textFieldValue.copy(
                            text = newText,
                            selection = TextRange(selection.min + selectedText.length + 3, selection.min + selectedText.length + 6)
                        )
                    )
                }
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
                                        if (textColor.htmlColor.isEmpty()) {
                                            // Default - just use the selected text without color
                                        } else {
                                            insertColoredText(textFieldValue, onValueChange, textColor.htmlColor)
                                        }
                                        showColorPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Preview toggle
            ToolbarButton(
                icon = Icons.Default.RemoveRedEye,
                contentDescription = if (isPreviewMode) "Edit mode" else "Preview mode",
                isSelected = isPreviewMode,
                onClick = { onPreviewModeChange(!isPreviewMode) }
            )
        }
    }
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
        modifier = Modifier.size(36.dp),
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

private fun insertMarkdown(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    prefix: String,
    suffix: String
) {
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    if (selection.collapsed) {
        // No selection - insert placeholder
        val newText = text.substring(0, selection.start) + prefix + suffix + text.substring(selection.end)
        onValueChange(
            textFieldValue.copy(
                text = newText,
                selection = TextRange(selection.start + prefix.length)
            )
        )
    } else {
        // Wrap selection
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

private fun insertLinePrefix(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    prefix: String
) {
    val selection = textFieldValue.selection
    val text = textFieldValue.text

    // Find the start of the current line
    val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)) + 1

    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    onValueChange(
        textFieldValue.copy(
            text = newText,
            selection = TextRange(selection.start + prefix.length, selection.end + prefix.length)
        )
    )
}

private fun insertColoredText(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    htmlColor: String
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
