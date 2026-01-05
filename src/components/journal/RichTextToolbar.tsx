'use client'

import { RefObject } from 'react'

interface RichTextToolbarProps {
  textareaRef: RefObject<HTMLTextAreaElement | null>
  onContentChange: (content: string) => void
  content: string
  isFullscreen?: boolean
  onToggleFullscreen?: () => void
}

interface FormatButton {
  icon: string
  label: string
  prefix: string
  suffix: string
  block?: boolean
}

const FORMAT_BUTTONS: FormatButton[] = [
  { icon: 'B', label: 'Bold', prefix: '**', suffix: '**' },
  { icon: 'I', label: 'Italic', prefix: '_', suffix: '_' },
  { icon: 'S', label: 'Strikethrough', prefix: '~~', suffix: '~~' },
  { icon: 'H1', label: 'Heading 1', prefix: '# ', suffix: '', block: true },
  { icon: 'H2', label: 'Heading 2', prefix: '## ', suffix: '', block: true },
  { icon: 'H3', label: 'Heading 3', prefix: '### ', suffix: '', block: true },
  { icon: '•', label: 'Bullet List', prefix: '- ', suffix: '', block: true },
  { icon: '1.', label: 'Numbered List', prefix: '1. ', suffix: '', block: true },
  { icon: '"', label: 'Quote', prefix: '> ', suffix: '', block: true },
  { icon: '🔗', label: 'Link', prefix: '[', suffix: '](url)' },
]

export function RichTextToolbar({ textareaRef, onContentChange, content, onToggleFullscreen }: RichTextToolbarProps) {
  const applyFormat = (format: FormatButton) => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = content.substring(start, end)

    let newContent: string
    let newCursorPos: number

    if (format.block) {
      // For block-level formatting, apply at the beginning of the line
      const beforeSelection = content.substring(0, start)
      const lastNewline = beforeSelection.lastIndexOf('\n')
      const lineStart = lastNewline + 1

      // Check if we're at the start of a line or need to add a newline
      if (lineStart === start) {
        // Already at line start
        newContent =
          content.substring(0, start) +
          format.prefix +
          selectedText +
          format.suffix +
          content.substring(end)
        newCursorPos = start + format.prefix.length + selectedText.length + format.suffix.length
      } else {
        // Need to create a new line
        newContent =
          content.substring(0, start) +
          '\n' + format.prefix +
          selectedText +
          format.suffix +
          content.substring(end)
        newCursorPos = start + 1 + format.prefix.length + selectedText.length + format.suffix.length
      }
    } else {
      // Inline formatting
      newContent =
        content.substring(0, start) +
        format.prefix +
        selectedText +
        format.suffix +
        content.substring(end)
      newCursorPos = start + format.prefix.length + selectedText.length + format.suffix.length
    }

    onContentChange(newContent)

    // Restore focus and cursor position after state update
    setTimeout(() => {
      textarea.focus()
      textarea.setSelectionRange(newCursorPos, newCursorPos)
    }, 0)
  }

  return (
    <div className="flex flex-wrap gap-1 p-2 bg-amber-50/50 dark:bg-gray-800/50 border-b border-amber-200/50 dark:border-gray-700/50 rounded-t-xl">
      {FORMAT_BUTTONS.map((format) => (
        <button
          key={format.label}
          type="button"
          onClick={() => applyFormat(format)}
          title={format.label}
          className={`
            px-2 py-1.5 text-sm rounded-lg transition-all duration-200
            hover:bg-amber-100 dark:hover:bg-gray-700
            active:scale-95 active:bg-amber-200 dark:active:bg-gray-600
            text-amber-900 dark:text-gray-300
            ${format.icon === 'B' ? 'font-bold' : ''}
            ${format.icon === 'I' ? 'italic' : ''}
            ${format.icon === 'S' ? 'line-through' : ''}
            ${format.icon.startsWith('H') ? 'font-serif font-semibold text-xs' : ''}
          `}
        >
          {format.icon}
        </button>
      ))}
      <div className="flex-1" />
      <span className="text-xs text-amber-600/60 dark:text-gray-500 self-center pr-2">
        Markdown supported
      </span>
      {onToggleFullscreen && (
        <button
          type="button"
          onClick={onToggleFullscreen}
          title="Open in full page editor"
          className="px-2 py-1.5 text-sm rounded-lg transition-all duration-200 hover:bg-amber-100 dark:hover:bg-gray-700 active:scale-95 active:bg-amber-200 dark:active:bg-gray-600 text-amber-900 dark:text-gray-300"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4" />
          </svg>
        </button>
      )}
    </div>
  )
}
