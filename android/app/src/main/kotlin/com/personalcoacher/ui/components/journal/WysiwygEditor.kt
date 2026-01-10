package com.personalcoacher.ui.components.journal

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Interface for communicating between WebView and Compose
 */
class WysiwygJsInterface(
    private val onContentChange: (String) -> Unit,
    private val onActiveFormatsChange: (Set<String>) -> Unit
) {
    @JavascriptInterface
    fun onContentChanged(html: String) {
        onContentChange(html)
    }

    @JavascriptInterface
    fun onFormatsChanged(formats: String) {
        // formats is comma-separated list like "bold,italic"
        val formatSet = if (formats.isBlank()) emptySet() else formats.split(",").toSet()
        onActiveFormatsChange(formatSet)
    }
}

/**
 * A WYSIWYG editor using WebView with contentEditable, matching web app behavior.
 *
 * @param content The HTML content to display/edit
 * @param onContentChange Called when content changes
 * @param isSourceMode Whether to show HTML source or WYSIWYG view
 * @param onActiveFormatsChange Called when active formats at cursor position change
 * @param onWebViewReady Called when WebView is ready, provides reference for toolbar commands
 * @param placeholder Placeholder text when empty
 * @param enabled Whether editing is enabled
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WysiwygEditor(
    content: String,
    onContentChange: (String) -> Unit,
    isSourceMode: Boolean,
    onActiveFormatsChange: (Set<String>) -> Unit,
    onWebViewReady: (WebView) -> Unit = {},
    placeholder: String = "What's on your mind today? Start writing...",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }
    // Track content that we've set TO the WebView (to avoid setting it back)
    var lastSetContent by remember { mutableStateOf("") }
    // Track content that came FROM the WebView (to avoid circular updates)
    var lastReceivedContent by remember { mutableStateOf("") }

    val jsInterface = remember(onContentChange, onActiveFormatsChange) {
        WysiwygJsInterface(
            onContentChange = { html ->
                // Track what we received from WebView to prevent circular updates
                lastReceivedContent = html
                lastSetContent = html // Also update lastSetContent to prevent setContent being called
                onContentChange(html)
            },
            onActiveFormatsChange = onActiveFormatsChange
        )
    }

    // Update content when it changes externally (e.g., loading existing entry)
    // Only set content if it didn't come from the WebView itself
    LaunchedEffect(content, isWebViewReady) {
        if (isWebViewReady && webView != null && content != lastSetContent && content != lastReceivedContent) {
            lastSetContent = content
            val escapedContent = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
            webView?.evaluateJavascript("setContent(\"$escapedContent\")", null)
        }
    }

    // Toggle source mode
    LaunchedEffect(isSourceMode, isWebViewReady) {
        if (isWebViewReady && webView != null) {
            webView?.evaluateJavascript("setSourceMode($isSourceMode)", null)
        }
    }

    // Toggle enabled state
    LaunchedEffect(enabled, isWebViewReady) {
        if (isWebViewReady && webView != null) {
            webView?.evaluateJavascript("setEnabled($enabled)", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Force LTR layout direction to fix reverse typing on RTL devices
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(Color.TRANSPARENT)

                addJavascriptInterface(jsInterface, "Android")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isWebViewReady = true
                        // Notify parent that WebView is ready
                        view?.let { onWebViewReady(it) }
                        // Set initial content
                        if (content.isNotEmpty()) {
                            val escapedContent = content
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "")
                            view?.evaluateJavascript("setContent(\"$escapedContent\")", null)
                            lastSetContent = content
                        }
                        // Set initial source mode
                        view?.evaluateJavascript("setSourceMode($isSourceMode)", null)
                    }
                }

                val html = buildEditorHtml(isDarkTheme, placeholder)
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

                webView = this
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            webView = view
        }
    )
}

/**
 * Execute a formatting command on the WebView editor
 */
fun executeCommand(webView: WebView?, command: String, value: String? = null) {
    val jsCommand = if (value != null) {
        "execFormatCommand('$command', '$value')"
    } else {
        "execFormatCommand('$command')"
    }
    webView?.evaluateJavascript(jsCommand, null)
}

/**
 * Insert HTML at cursor position in source mode
 */
fun insertSourceHtml(webView: WebView?, openTag: String, closeTag: String) {
    val escapedOpen = openTag.replace("'", "\\'")
    val escapedClose = closeTag.replace("'", "\\'")
    webView?.evaluateJavascript("insertSourceTag('$escapedOpen', '$escapedClose')", null)
}

/**
 * Build the HTML for the editor WebView
 */
private fun buildEditorHtml(isDarkTheme: Boolean, placeholder: String): String {
    val bgColor = if (isDarkTheme) "#1a1a1a" else "#fffbf5"
    val textColor = if (isDarkTheme) "#e5e5e5" else "#1f2937"
    val placeholderColor = if (isDarkTheme) "#6b7280" else "#9ca3af"
    val lineColor = if (isDarkTheme) "rgba(255,255,255,0.05)" else "rgba(212,165,116,0.3)"
    val headingColor = if (isDarkTheme) "#fbbf24" else "#92400e"
    val linkColor = if (isDarkTheme) "#a78bfa" else "#d97706"
    val quoteBackground = if (isDarkTheme) "rgba(255,255,255,0.05)" else "rgba(251,191,36,0.1)"
    val quoteBorder = if (isDarkTheme) "#4b5563" else "#d97706"

    return """
<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta http-equiv="Content-Language" content="en">
    <style>
        * {
            box-sizing: border-box;
            -webkit-tap-highlight-color: transparent;
        }

        html, body {
            margin: 0;
            padding: 0;
            height: 100%;
            background-color: $bgColor;
            font-family: Georgia, 'Times New Roman', serif;
            direction: ltr !important;
            text-align: left !important;
            unicode-bidi: bidi-override !important;
        }

        #editor-container {
            min-height: 100%;
            padding: 20px;
            background-image: repeating-linear-gradient(
                transparent,
                transparent 27px,
                $lineColor 28px
            );
            background-position: 0 12px;
        }

        #editor {
            min-height: 200px;
            outline: none;
            color: $textColor;
            font-size: 18px;
            line-height: 28px;
            word-wrap: break-word;
            direction: ltr !important;
            text-align: left !important;
            unicode-bidi: bidi-override !important;
            -webkit-writing-mode: horizontal-tb !important;
            writing-mode: horizontal-tb !important;
            -webkit-user-modify: read-write !important;
        }

        /* Force all paragraphs and divs inside editor to be LTR */
        #editor p, #editor div, #editor span, #editor li, #editor blockquote {
            direction: ltr !important;
            text-align: left !important;
            unicode-bidi: bidi-override !important;
        }

        #editor:empty:before {
            content: attr(data-placeholder);
            color: $placeholderColor;
            font-style: italic;
            pointer-events: none;
        }

        #editor h1, #editor h2, #editor h3 {
            color: $headingColor;
            font-family: Georgia, 'Times New Roman', serif;
            margin: 8px 0;
        }

        #editor h1 { font-size: 28px; line-height: 36px; }
        #editor h2 { font-size: 24px; line-height: 32px; }
        #editor h3 { font-size: 20px; line-height: 28px; }

        #editor a {
            color: $linkColor;
            text-decoration: none;
        }

        #editor a:hover {
            text-decoration: underline;
        }

        #editor blockquote {
            margin: 8px 0;
            padding: 8px 16px;
            border-left: 4px solid $quoteBorder;
            background: $quoteBackground;
            border-radius: 0 8px 8px 0;
        }

        #editor ul, #editor ol {
            margin: 8px 0;
            padding-left: 24px;
        }

        #editor li {
            margin: 4px 0;
        }

        #source {
            display: none;
            min-height: 200px;
            width: 100%;
            border: none;
            outline: none;
            background: transparent;
            color: $textColor;
            font-family: 'Courier New', monospace;
            font-size: 14px;
            line-height: 20px;
            resize: none;
            padding: 0;
            direction: ltr;
            text-align: left;
        }

        #source::placeholder {
            color: $placeholderColor;
        }

        .source-mode #editor { display: none; }
        .source-mode #source { display: block; }
    </style>
</head>
<body dir="ltr" style="direction: ltr; text-align: left;">
    <div id="editor-container" dir="ltr" style="direction: ltr; text-align: left;">
        <div id="editor" contenteditable="true" dir="ltr" style="direction: ltr !important; text-align: left !important; unicode-bidi: bidi-override !important;" spellcheck="false" autocapitalize="sentences" autocomplete="off" autocorrect="off" data-placeholder="$placeholder"></div>
        <textarea id="source" dir="ltr" style="direction: ltr; text-align: left;" spellcheck="false" autocapitalize="sentences" autocomplete="off" autocorrect="off" placeholder="HTML source code..."></textarea>
    </div>

    <script>
        const editor = document.getElementById('editor');
        const source = document.getElementById('source');
        const container = document.getElementById('editor-container');
        let isSourceMode = false;
        let debounceTimer = null;
        let isComposing = false;
        let savedSelection = null;
        let isUserEditing = false; // Track if user is actively editing
        let editingTimeout = null;

        // Save and restore selection to fix cursor position issues on Android
        function saveSelection() {
            const sel = window.getSelection();
            if (sel.rangeCount > 0) {
                savedSelection = sel.getRangeAt(0).cloneRange();
            }
        }

        function restoreSelection() {
            if (savedSelection) {
                const sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(savedSelection);
            }
        }

        // Mark user as actively editing (prevents setContent from being called)
        function markEditing() {
            isUserEditing = true;
            clearTimeout(editingTimeout);
            // Reset after 500ms of no input - long enough to cover the round-trip to Android
            editingTimeout = setTimeout(() => {
                isUserEditing = false;
            }, 500);
        }

        // Notify Android of content changes (debounced)
        function notifyContentChange() {
            markEditing(); // Mark that user is editing
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                const content = isSourceMode ? source.value : editor.innerHTML;
                Android.onContentChanged(content);
            }, 100);
        }

        // Notify Android of format changes
        function notifyFormatChange() {
            const formats = [];

            if (document.queryCommandState('bold')) formats.push('bold');
            if (document.queryCommandState('italic')) formats.push('italic');
            if (document.queryCommandState('strikeThrough')) formats.push('strikethrough');
            if (document.queryCommandState('insertUnorderedList')) formats.push('ul');
            if (document.queryCommandState('insertOrderedList')) formats.push('ol');

            // Check block format
            const selection = window.getSelection();
            if (selection && selection.rangeCount > 0) {
                let node = selection.anchorNode;
                while (node && node !== editor) {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        const tagName = node.tagName.toLowerCase();
                        if (tagName === 'h1') formats.push('h1');
                        if (tagName === 'h2') formats.push('h2');
                        if (tagName === 'h3') formats.push('h3');
                        if (tagName === 'blockquote') formats.push('quote');
                    }
                    node = node.parentNode;
                }
            }

            Android.onFormatsChanged(formats.join(','));
        }

        // Set content from Android (only used for loading existing entries)
        // This should NOT be called during normal typing - only for initial load
        function setContent(html) {
            // CRITICAL: Skip if user is actively editing to prevent cursor reset
            if (isUserEditing) {
                return;
            }
            // Skip if content is the same to avoid cursor reset
            if (isSourceMode) {
                if (source.value === html) return;
                source.value = html;
            } else {
                if (editor.innerHTML === html) return;
                editor.innerHTML = html;
                // Put cursor at end for initial load
                const sel = window.getSelection();
                if (editor.childNodes.length > 0) {
                    const range = document.createRange();
                    range.selectNodeContents(editor);
                    range.collapse(false);
                    sel.removeAllRanges();
                    sel.addRange(range);
                }
            }
        }

        // Toggle source mode
        function setSourceMode(sourceMode) {
            if (sourceMode === isSourceMode) return;

            if (sourceMode) {
                // Switching to source mode
                source.value = editor.innerHTML;
                container.classList.add('source-mode');
            } else {
                // Switching to WYSIWYG mode
                editor.innerHTML = source.value;
                container.classList.remove('source-mode');
            }
            isSourceMode = sourceMode;
        }

        // Set enabled state
        function setEnabled(enabled) {
            editor.contentEditable = enabled ? 'true' : 'false';
            source.disabled = !enabled;
        }

        // Execute formatting command (called from Android)
        function execFormatCommand(command, value) {
            if (isSourceMode) return; // Don't execute in source mode

            editor.focus();

            switch (command) {
                case 'bold':
                    document.execCommand('bold', false);
                    break;
                case 'italic':
                    document.execCommand('italic', false);
                    break;
                case 'strikethrough':
                    document.execCommand('strikeThrough', false);
                    break;
                case 'h1':
                case 'h2':
                case 'h3':
                    // Check if already in this heading - if so, convert to paragraph
                    const selection = window.getSelection();
                    if (selection && selection.rangeCount > 0) {
                        let node = selection.anchorNode;
                        let currentTag = null;
                        while (node && node !== editor) {
                            if (node.nodeType === Node.ELEMENT_NODE) {
                                const tagName = node.tagName.toLowerCase();
                                if (['h1', 'h2', 'h3'].includes(tagName)) {
                                    currentTag = tagName;
                                    break;
                                }
                            }
                            node = node.parentNode;
                        }
                        if (currentTag === command) {
                            document.execCommand('formatBlock', false, 'p');
                        } else {
                            document.execCommand('formatBlock', false, command);
                        }
                    } else {
                        document.execCommand('formatBlock', false, command);
                    }
                    break;
                case 'ul':
                    document.execCommand('insertUnorderedList', false);
                    break;
                case 'ol':
                    document.execCommand('insertOrderedList', false);
                    break;
                case 'quote':
                    // Check if already in blockquote
                    const sel = window.getSelection();
                    if (sel && sel.rangeCount > 0) {
                        let n = sel.anchorNode;
                        let inQuote = false;
                        while (n && n !== editor) {
                            if (n.nodeType === Node.ELEMENT_NODE && n.tagName.toLowerCase() === 'blockquote') {
                                inQuote = true;
                                break;
                            }
                            n = n.parentNode;
                        }
                        if (inQuote) {
                            document.execCommand('formatBlock', false, 'p');
                        } else {
                            document.execCommand('formatBlock', false, 'blockquote');
                        }
                    } else {
                        document.execCommand('formatBlock', false, 'blockquote');
                    }
                    break;
                case 'link':
                    if (value) {
                        document.execCommand('createLink', false, value);
                    }
                    break;
                case 'foreColor':
                    if (value) {
                        document.execCommand('foreColor', false, value);
                    }
                    break;
                case 'removeFormat':
                    document.execCommand('removeFormat', false);
                    break;
            }

            notifyContentChange();
            setTimeout(notifyFormatChange, 10);
        }

        // Insert HTML tags in source mode (called from Android)
        // Supports toggle behavior - if cursor is inside the same tags, removes them instead
        function insertSourceTag(openTag, closeTag) {
            if (!isSourceMode) return;

            const start = source.selectionStart;
            const end = source.selectionEnd;
            const text = source.value;
            const selectedText = text.substring(start, end);

            // Check if we're inside existing tags of the same type
            // Look for the opening tag before cursor and closing tag after cursor
            const beforeCursor = text.substring(0, start);
            const afterCursor = text.substring(end);

            // Extract tag name from openTag (e.g., "<b>" -> "b", "<h1>" -> "h1")
            const tagMatch = openTag.match(/<([a-z0-9]+)/i);
            if (!tagMatch) {
                // Fallback to simple insert if can't parse tag
                const newText = text.substring(0, start) + openTag + selectedText + closeTag + text.substring(end);
                source.value = newText;
                const newPos = selectedText ? start + openTag.length + selectedText.length + closeTag.length : start + openTag.length;
                source.setSelectionRange(newPos, newPos);
                source.focus();
                notifyContentChange();
                return;
            }

            const tagName = tagMatch[1].toLowerCase();

            // Build regex to find the opening tag (with optional attributes)
            const openTagRegex = new RegExp('<' + tagName + '(\\s[^>]*)?' + '>', 'gi');
            const closeTagRegex = new RegExp('</' + tagName + '>', 'gi');

            // Find the last opening tag before cursor
            let lastOpenTagPos = -1;
            let lastOpenTagEndPos = -1;
            let match;
            openTagRegex.lastIndex = 0;
            while ((match = openTagRegex.exec(beforeCursor)) !== null) {
                lastOpenTagPos = match.index;
                lastOpenTagEndPos = match.index + match[0].length;
            }

            // Find the first closing tag after cursor (or after selection end)
            closeTagRegex.lastIndex = 0;
            const closeMatch = closeTagRegex.exec(afterCursor);

            // Check if there's an intervening close tag before cursor (which would mean we're not inside)
            let hasInterveningClose = false;
            if (lastOpenTagPos >= 0) {
                const betweenOpenAndCursor = text.substring(lastOpenTagEndPos, start);
                closeTagRegex.lastIndex = 0;
                if (closeTagRegex.exec(betweenOpenAndCursor)) {
                    hasInterveningClose = true;
                }
            }

            // If we found matching open and close tags around the cursor, toggle off (remove them)
            if (lastOpenTagPos >= 0 && closeMatch && !hasInterveningClose) {
                const closeTagPos = end + closeMatch.index;
                const closeTagEndPos = closeTagPos + closeMatch[0].length;

                // Calculate the opening tag length
                openTagRegex.lastIndex = lastOpenTagPos;
                const openMatchAtPos = openTagRegex.exec(text);
                const openTagLength = openMatchAtPos ? openMatchAtPos[0].length : openTag.length;

                // Remove both tags
                const newText = text.substring(0, lastOpenTagPos) +
                               text.substring(lastOpenTagEndPos, closeTagPos) +
                               text.substring(closeTagEndPos);
                source.value = newText;

                // Adjust cursor position (account for removed opening tag)
                const newStart = start - openTagLength;
                const newEnd = end - openTagLength;
                source.setSelectionRange(newStart >= 0 ? newStart : 0, newEnd >= 0 ? newEnd : 0);
                source.focus();
                notifyContentChange();
                return;
            }

            // Not inside existing tags, so insert new ones
            const newText = text.substring(0, start) + openTag + selectedText + closeTag + text.substring(end);
            source.value = newText;

            // Position cursor
            const newPos = selectedText ? start + openTag.length + selectedText.length + closeTag.length : start + openTag.length;
            source.setSelectionRange(newPos, newPos);
            source.focus();

            notifyContentChange();
        }

        // Force LTR direction on any inserted content
        function forceLTRDirection() {
            // Apply LTR direction to all child elements
            const elements = editor.querySelectorAll('*');
            elements.forEach(el => {
                el.setAttribute('dir', 'ltr');
                el.style.direction = 'ltr';
                el.style.textAlign = 'left';
                el.style.unicodeBidi = 'bidi-override';
            });

            // Also ensure the editor itself maintains LTR
            editor.setAttribute('dir', 'ltr');
            editor.style.direction = 'ltr';
        }

        // Wrap text in LTR span to prevent RTL reordering
        function wrapInLTR(text) {
            // Use Left-to-Right Mark (LRM) Unicode characters to force LTR
            return '\u200E' + text + '\u200E';
        }

        // Event listeners
        editor.addEventListener('input', function(e) {
            // Don't notify during composition to prevent cursor jumping
            if (!isComposing) {
                // Force LTR on any new content
                forceLTRDirection();
                notifyContentChange();
            }
        });
        editor.addEventListener('keyup', notifyFormatChange);
        editor.addEventListener('mouseup', notifyFormatChange);
        editor.addEventListener('focus', function() {
            // Ensure LTR is enforced when focusing
            forceLTRDirection();
            notifyFormatChange();
        });

        // Composition event handling for Android IME
        editor.addEventListener('compositionstart', function(e) {
            isComposing = true;
            saveSelection();
        });

        editor.addEventListener('compositionupdate', function(e) {
            // Don't interfere with composition
        });

        editor.addEventListener('compositionend', function(e) {
            isComposing = false;
            // Force LTR direction after composition ends
            forceLTRDirection();
            // Notify content change after composition ends
            notifyContentChange();
            notifyFormatChange();
        });

        // Handle beforeinput to insert LTR marks with text
        editor.addEventListener('beforeinput', function(e) {
            // For space insertion, ensure we maintain LTR order
            if (e.inputType === 'insertText' && e.data === ' ' && !isComposing) {
                // Insert a space with LTR mark to prevent word reordering
                e.preventDefault();
                const sel = window.getSelection();
                if (sel.rangeCount > 0) {
                    const range = sel.getRangeAt(0);
                    range.deleteContents();
                    // Insert space with LTR marks around it
                    const textNode = document.createTextNode('\u200E \u200E');
                    range.insertNode(textNode);
                    // Move cursor after the space
                    range.setStartAfter(textNode);
                    range.setEndAfter(textNode);
                    sel.removeAllRanges();
                    sel.addRange(range);
                    notifyContentChange();
                }
            }
        });

        // MutationObserver to catch any DOM changes and enforce LTR
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                    forceLTRDirection();
                }
            });
        });

        observer.observe(editor, {
            childList: true,
            subtree: true,
            characterData: true
        });

        source.addEventListener('input', notifyContentChange);
    </script>
</body>
</html>
    """.trimIndent()
}
