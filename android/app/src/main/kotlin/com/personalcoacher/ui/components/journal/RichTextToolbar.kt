package com.personalcoacher.ui.components.journal

import android.webkit.WebView
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
                        modifier = Modifier.padding(8.dp)
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

@Composable
private fun LinkDialog(
    webView: WebView?,
    isSourceMode: Boolean,
    onDismiss: () -> Unit
) {
    var linkUrl by remember { mutableStateOf("") }

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
                        if (isSourceMode) {
                            insertSourceHtml(webView, "<a href=\"$linkUrl\">", "</a>")
                        } else {
                            executeCommand(webView, "link", linkUrl)
                        }
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
