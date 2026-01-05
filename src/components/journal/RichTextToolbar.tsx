'use client'

import { RefObject, useState, useEffect, useCallback } from 'react'

interface RichTextToolbarProps {
  editorRef: RefObject<HTMLDivElement | null>
  onContentChange: (content: string) => void
  minimal?: boolean
  showSourceView?: boolean
  onToggleSourceView?: () => void
  isSourceView?: boolean
  sourceRef?: RefObject<HTMLTextAreaElement | null>
}

const TEXT_COLORS = [
  { name: 'Default', value: '', colorClass: 'bg-gray-800 dark:bg-gray-200', textColor: 'text-gray-800 dark:text-gray-200' },
  { name: 'Red', value: '#ef4444', colorClass: 'bg-red-500', textColor: 'text-red-500' },
  { name: 'Orange', value: '#f97316', colorClass: 'bg-orange-500', textColor: 'text-orange-500' },
  { name: 'Green', value: '#22c55e', colorClass: 'bg-green-500', textColor: 'text-green-500' },
  { name: 'Blue', value: '#3b82f6', colorClass: 'bg-blue-500', textColor: 'text-blue-500' },
  { name: 'Purple', value: '#a855f7', colorClass: 'bg-purple-500', textColor: 'text-purple-500' },
]

export function RichTextToolbar({
  editorRef,
  onContentChange,
  minimal,
  showSourceView = false,
  onToggleSourceView,
  isSourceView = false,
  sourceRef
}: RichTextToolbarProps) {
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [activeFormats, setActiveFormats] = useState<Set<string>>(new Set())
  const [customColor, setCustomColor] = useState('#000000')

  // Helper function to insert HTML tags in source mode
  const insertSourceTag = useCallback((openTag: string, closeTag: string) => {
    const textarea = sourceRef?.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const text = textarea.value
    const selectedText = text.substring(start, end)

    const newText = text.substring(0, start) + openTag + selectedText + closeTag + text.substring(end)

    // Update the content
    onContentChange(newText)

    // Set cursor position between the tags
    setTimeout(() => {
      textarea.focus()
      const newCursorPos = selectedText ? start + openTag.length + selectedText.length + closeTag.length : start + openTag.length
      textarea.setSelectionRange(newCursorPos, newCursorPos)
    }, 0)
  }, [sourceRef, onContentChange])

  // Check current formatting state at cursor position
  const updateActiveFormats = useCallback(() => {
    const formats = new Set<string>()

    // Check inline formats using queryCommandState
    if (document.queryCommandState('bold')) formats.add('bold')
    if (document.queryCommandState('italic')) formats.add('italic')
    if (document.queryCommandState('strikeThrough')) formats.add('strikethrough')
    if (document.queryCommandState('insertUnorderedList')) formats.add('ul')
    if (document.queryCommandState('insertOrderedList')) formats.add('ol')

    // Check block format by looking at the current element
    const selection = window.getSelection()
    if (selection && selection.rangeCount > 0) {
      let node: Node | null = selection.anchorNode
      while (node && node !== editorRef.current) {
        if (node.nodeType === Node.ELEMENT_NODE) {
          const tagName = (node as Element).tagName.toLowerCase()
          if (tagName === 'h1') formats.add('h1')
          if (tagName === 'h2') formats.add('h2')
          if (tagName === 'h3') formats.add('h3')
          if (tagName === 'blockquote') formats.add('quote')
        }
        node = node.parentNode
      }
    }

    setActiveFormats(formats)
  }, [editorRef])

  // Update active formats on selection change
  useEffect(() => {
    const handleSelectionChange = () => {
      updateActiveFormats()
    }

    document.addEventListener('selectionchange', handleSelectionChange)
    return () => document.removeEventListener('selectionchange', handleSelectionChange)
  }, [updateActiveFormats])

  // Close color picker when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (showColorPicker && !(e.target as Element).closest('.color-picker-container')) {
        setShowColorPicker(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showColorPicker])


  const applyFormat = useCallback((tag: string) => {
    // If in source view mode, insert HTML tags as text
    if (isSourceView && sourceRef?.current) {
      switch (tag) {
        case 'bold':
          insertSourceTag('<b>', '</b>')
          break
        case 'italic':
          insertSourceTag('<i>', '</i>')
          break
        case 'strikethrough':
          insertSourceTag('<s>', '</s>')
          break
        case 'h1':
          insertSourceTag('<h1>', '</h1>')
          break
        case 'h2':
          insertSourceTag('<h2>', '</h2>')
          break
        case 'h3':
          insertSourceTag('<h3>', '</h3>')
          break
        case 'ul':
          insertSourceTag('<ul>\n  <li>', '</li>\n</ul>')
          break
        case 'ol':
          insertSourceTag('<ol>\n  <li>', '</li>\n</ol>')
          break
        case 'quote':
          insertSourceTag('<blockquote>', '</blockquote>')
          break
        case 'link':
          const sourceUrl = prompt('Enter URL:')
          if (sourceUrl) {
            insertSourceTag(`<a href="${sourceUrl}">`, '</a>')
          }
          break
      }
      return
    }

    const editor = editorRef.current
    if (!editor) return

    editor.focus()

    switch (tag) {
      case 'bold':
        document.execCommand('bold', false)
        break
      case 'italic':
        document.execCommand('italic', false)
        break
      case 'strikethrough':
        document.execCommand('strikeThrough', false)
        break
      case 'h1':
      case 'h2':
      case 'h3':
        // Check if we're already in this heading type - if so, convert to paragraph
        if (activeFormats.has(tag)) {
          document.execCommand('formatBlock', false, 'p')
        } else {
          document.execCommand('formatBlock', false, tag)
        }
        break
      case 'ul':
        document.execCommand('insertUnorderedList', false)
        break
      case 'ol':
        document.execCommand('insertOrderedList', false)
        break
      case 'quote':
        // Check if we're already in a blockquote - if so, convert to paragraph
        if (activeFormats.has('quote')) {
          document.execCommand('formatBlock', false, 'p')
        } else {
          document.execCommand('formatBlock', false, 'blockquote')
        }
        break
      case 'link':
        const url = prompt('Enter URL:')
        if (url) {
          document.execCommand('createLink', false, url)
        }
        break
    }

    // Update content and active formats
    onContentChange(editor.innerHTML)
    setTimeout(updateActiveFormats, 10)
  }, [editorRef, onContentChange, activeFormats, updateActiveFormats, isSourceView, sourceRef, insertSourceTag])

  const applyColor = useCallback((color: string) => {
    // If in source view mode, insert span tag with color
    if (isSourceView && sourceRef?.current) {
      if (color === '') {
        // Can't easily remove formatting in source mode, just close picker
        setShowColorPicker(false)
        return
      }
      insertSourceTag(`<span style="color: ${color}">`, '</span>')
      setShowColorPicker(false)
      return
    }

    const editor = editorRef.current
    if (!editor) return

    editor.focus()

    if (color === '') {
      document.execCommand('removeFormat', false)
    } else {
      document.execCommand('foreColor', false, color)
    }

    setShowColorPicker(false)
    onContentChange(editor.innerHTML)
  }, [editorRef, onContentChange, isSourceView, sourceRef, insertSourceTag])

  const applyCustomColor = useCallback(() => {
    applyColor(customColor)
  }, [customColor, applyColor])

  const formatButtons = [
    { id: 'bold', icon: 'B', label: 'Bold (Ctrl+B)', className: 'font-bold' },
    { id: 'italic', icon: 'I', label: 'Italic (Ctrl+I)', className: 'italic' },
    { id: 'strikethrough', icon: 'S', label: 'Strikethrough', className: 'line-through' },
    { id: 'h1', icon: 'H1', label: 'Heading 1', className: 'font-serif font-semibold text-xs' },
    { id: 'h2', icon: 'H2', label: 'Heading 2', className: 'font-serif font-semibold text-xs' },
    { id: 'h3', icon: 'H3', label: 'Heading 3', className: 'font-serif font-semibold text-xs' },
    { id: 'ul', icon: '•', label: 'Bullet List', className: '' },
    { id: 'ol', icon: '1.', label: 'Numbered List', className: '' },
    { id: 'quote', icon: '"', label: 'Quote', className: '' },
    { id: 'link', icon: '🔗', label: 'Link (Ctrl+K)', className: '' },
  ]

  const minimalButtons = formatButtons.filter(f =>
    ['bold', 'italic', 'h1', 'h2', 'ul', 'quote', 'link'].includes(f.id)
  )

  const buttonsToShow = minimal ? minimalButtons : formatButtons

  // Get button active style
  const getButtonActiveClass = (id: string) => {
    if (activeFormats.has(id)) {
      return 'bg-amber-200 dark:bg-gray-600 ring-2 ring-amber-400 dark:ring-violet-500'
    }
    return ''
  }

  if (minimal) {
    return (
      <div className="flex items-center gap-0.5 px-3 py-1.5">
        {buttonsToShow.map((format) => (
          <button
            key={format.id}
            type="button"
            onClick={() => applyFormat(format.id)}
            title={format.label}
            className={`
              px-2 py-1 text-sm rounded-full transition-all duration-200
              hover:bg-amber-100/50 dark:hover:bg-gray-700/50
              active:scale-95
              text-amber-800 dark:text-gray-300
              ${format.className}
              ${getButtonActiveClass(format.id)}
            `}
          >
            {format.icon}
          </button>
        ))}

        {/* Color picker - minimal */}
        <div className="relative color-picker-container">
          <button
            type="button"
            onClick={() => setShowColorPicker(!showColorPicker)}
            title="Text color"
            className="px-2 py-1 text-sm rounded-full transition-all duration-200 hover:bg-amber-100/50 dark:hover:bg-gray-700/50 active:scale-95 text-amber-800 dark:text-gray-300"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
            </svg>
          </button>

          {showColorPicker && (
            <div className="absolute top-full left-1/2 -translate-x-1/2 mt-2 p-3 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-amber-200 dark:border-gray-700 z-20 min-w-[180px]">
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-2 text-center">Select text first</p>
              <div className="flex flex-col gap-1">
                {TEXT_COLORS.map((color) => (
                  <button
                    key={color.name}
                    type="button"
                    onClick={() => applyColor(color.value)}
                    className={`flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-200 hover:bg-amber-50 dark:hover:bg-gray-700 ${color.textColor}`}
                  >
                    <span className={`w-5 h-5 rounded-full ${color.colorClass} flex-shrink-0 border-2 ${color.value === '' ? 'border-gray-300 dark:border-gray-500' : 'border-transparent'}`} />
                    <span className="text-sm font-medium">{color.name}</span>
                  </button>
                ))}
                {/* Custom color picker */}
                <div className="flex items-center gap-2 px-3 py-2 border-t border-gray-200 dark:border-gray-700 mt-1 pt-2">
                  <input
                    type="color"
                    value={customColor}
                    onChange={(e) => setCustomColor(e.target.value)}
                    className="w-8 h-8 rounded cursor-pointer border-0 p-0"
                    title="Pick custom color"
                  />
                  <button
                    type="button"
                    onClick={applyCustomColor}
                    className="flex-1 px-3 py-1.5 text-sm font-medium rounded-lg bg-amber-100 dark:bg-gray-700 text-amber-700 dark:text-gray-300 hover:bg-amber-200 dark:hover:bg-gray-600 transition-colors"
                  >
                    Apply
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Source view toggle - minimal */}
        {showSourceView && onToggleSourceView && (
          <>
            <div className="w-px h-4 bg-amber-200/50 dark:bg-gray-700/50 mx-1" />
            <button
              type="button"
              onClick={onToggleSourceView}
              title={isSourceView ? "Switch to WYSIWYG view" : "View source code"}
              className={`
                px-2 py-1 text-sm rounded-full transition-all duration-200
                hover:bg-amber-100/50 dark:hover:bg-gray-700/50
                active:scale-95
                ${isSourceView ? 'bg-amber-200 dark:bg-gray-600 text-amber-900 dark:text-gray-200' : 'text-amber-800 dark:text-gray-300'}
              `}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
              </svg>
            </button>
          </>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-wrap gap-1 p-2 bg-amber-50/50 dark:bg-gray-800/50 border-b border-amber-200/50 dark:border-gray-700/50 rounded-t-xl">
      {formatButtons.map((format) => (
        <button
          key={format.id}
          type="button"
          onClick={() => applyFormat(format.id)}
          title={format.label}
          className={`
            px-2 py-1.5 text-sm rounded-lg transition-all duration-200
            hover:bg-amber-100 dark:hover:bg-gray-700
            active:scale-95 active:bg-amber-200 dark:active:bg-gray-600
            text-amber-900 dark:text-gray-300
            ${format.className}
            ${getButtonActiveClass(format.id)}
          `}
        >
          {format.icon}
        </button>
      ))}

      {/* Color picker */}
      <div className="relative color-picker-container">
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
          <div className="absolute top-full left-0 mt-1 p-3 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-amber-200 dark:border-gray-700 z-10 min-w-[180px]">
            <p className="text-xs text-gray-500 dark:text-gray-400 mb-2 text-center">Select text first</p>
            <div className="flex flex-col gap-1">
              {TEXT_COLORS.map((color) => (
                <button
                  key={color.name}
                  type="button"
                  onClick={() => applyColor(color.value)}
                  className={`flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-200 hover:bg-amber-50 dark:hover:bg-gray-700 ${color.textColor}`}
                >
                  <span className={`w-5 h-5 rounded-full ${color.colorClass} flex-shrink-0 border-2 ${color.value === '' ? 'border-gray-300 dark:border-gray-500' : 'border-transparent'}`} />
                  <span className="text-sm font-medium">{color.name}</span>
                </button>
              ))}
              {/* Custom color picker */}
              <div className="flex items-center gap-2 px-3 py-2 border-t border-gray-200 dark:border-gray-700 mt-1 pt-2">
                <input
                  type="color"
                  value={customColor}
                  onChange={(e) => setCustomColor(e.target.value)}
                  className="w-8 h-8 rounded cursor-pointer border-0 p-0"
                  title="Pick custom color"
                />
                <button
                  type="button"
                  onClick={applyCustomColor}
                  className="flex-1 px-3 py-1.5 text-sm font-medium rounded-lg bg-amber-100 dark:bg-gray-700 text-amber-700 dark:text-gray-300 hover:bg-amber-200 dark:hover:bg-gray-600 transition-colors"
                >
                  Apply
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="w-px h-6 bg-amber-200 dark:bg-gray-700 mx-1 self-center" />

      {/* Source view toggle */}
      {showSourceView && onToggleSourceView && (
        <button
          type="button"
          onClick={onToggleSourceView}
          title={isSourceView ? "Switch to WYSIWYG view" : "View source code"}
          className={`
            px-2 py-1.5 text-sm rounded-lg transition-all duration-200
            hover:bg-amber-100 dark:hover:bg-gray-700
            active:scale-95
            flex items-center gap-1.5
            ${isSourceView ? 'bg-amber-200 dark:bg-gray-600 text-amber-900 dark:text-gray-200' : 'text-amber-900 dark:text-gray-300'}
          `}
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
          <span className="text-xs">{isSourceView ? 'WYSIWYG' : 'Source'}</span>
        </button>
      )}

      <div className="flex-1" />
      <span className="text-xs text-amber-600/60 dark:text-gray-500 self-center pr-2">
        {isSourceView ? 'Source view' : 'WYSIWYG editor'}
      </span>
    </div>
  )
}
