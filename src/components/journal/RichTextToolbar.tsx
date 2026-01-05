'use client'

import { RefObject, useState } from 'react'

interface RichTextToolbarProps {
  textareaRef: RefObject<HTMLTextAreaElement | null>
  onContentChange: (content: string) => void
  content: string
  isFullscreen?: boolean
  onToggleFullscreen?: () => void
  showPreview?: boolean
  onTogglePreview?: () => void
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

const TEXT_COLORS = [
  { name: 'Default', value: '', colorClass: 'bg-gray-800 dark:bg-gray-200' },
  { name: 'Red', value: '#ef4444', colorClass: 'bg-red-500' },
  { name: 'Orange', value: '#f97316', colorClass: 'bg-orange-500' },
  { name: 'Amber', value: '#f59e0b', colorClass: 'bg-amber-500' },
  { name: 'Green', value: '#22c55e', colorClass: 'bg-green-500' },
  { name: 'Blue', value: '#3b82f6', colorClass: 'bg-blue-500' },
  { name: 'Purple', value: '#a855f7', colorClass: 'bg-purple-500' },
  { name: 'Pink', value: '#ec4899', colorClass: 'bg-pink-500' },
]

export function RichTextToolbar({ textareaRef, onContentChange, content, onToggleFullscreen, showPreview, onTogglePreview }: RichTextToolbarProps) {
  const [showColorPicker, setShowColorPicker] = useState(false)
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

  const applyColor = (color: string) => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = content.substring(start, end)

    let newContent: string
    let newCursorPos: number

    if (color === '') {
      // Default color - just use the text as is
      newContent = content
      newCursorPos = end
    } else {
      // Wrap with colored span
      const coloredText = `<span style="color:${color}">${selectedText || 'text'}</span>`
      newContent =
        content.substring(0, start) +
        coloredText +
        content.substring(end)
      newCursorPos = start + coloredText.length
    }

    onContentChange(newContent)
    setShowColorPicker(false)

    // Restore focus and cursor position after state update
    setTimeout(() => {
      textarea.focus()
      if (selectedText) {
        textarea.setSelectionRange(newCursorPos, newCursorPos)
      } else {
        // Position cursor inside the span tag before the text
        const insertPos = start + `<span style="color:${color}">`.length
        textarea.setSelectionRange(insertPos, insertPos + 4) // Select 'text'
      }
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

      {/* Color picker */}
      <div className="relative">
        <button
          type="button"
          onClick={() => setShowColorPicker(!showColorPicker)}
          title="Text color"
          className="px-2 py-1.5 text-sm rounded-lg transition-all duration-200 hover:bg-amber-100 dark:hover:bg-gray-700 active:scale-95 active:bg-amber-200 dark:active:bg-gray-600 text-amber-900 dark:text-gray-300"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
          </svg>
        </button>

        {showColorPicker && (
          <div className="absolute top-full left-0 mt-1 p-2 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-amber-200 dark:border-gray-700 z-10">
            <div className="grid grid-cols-4 gap-1.5">
              {TEXT_COLORS.map((color) => (
                <button
                  key={color.name}
                  type="button"
                  onClick={() => applyColor(color.value)}
                  title={color.name}
                  className={`w-6 h-6 rounded-full ${color.colorClass} hover:scale-110 transition-transform border-2 ${color.value === '' ? 'border-gray-400 dark:border-gray-500' : 'border-transparent'} hover:border-amber-500 dark:hover:border-violet-400`}
                />
              ))}
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-2 text-center">
              Select text first
            </p>
          </div>
        )}
      </div>

      <div className="w-px h-6 bg-amber-200 dark:bg-gray-700 mx-1 self-center" />

      <div className="flex-1" />
      <span className="text-xs text-amber-600/60 dark:text-gray-500 self-center pr-2">
        Markdown supported
      </span>

      {/* Preview toggle */}
      {onTogglePreview && (
        <button
          type="button"
          onClick={onTogglePreview}
          title={showPreview ? "Edit mode" : "Preview mode"}
          className={`px-2 py-1.5 text-sm rounded-lg transition-all duration-200 active:scale-95 ${
            showPreview
              ? 'bg-amber-200 dark:bg-violet-600 text-amber-900 dark:text-white'
              : 'hover:bg-amber-100 dark:hover:bg-gray-700 text-amber-900 dark:text-gray-300'
          }`}
        >
          {showPreview ? (
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          ) : (
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
          )}
        </button>
      )}

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
