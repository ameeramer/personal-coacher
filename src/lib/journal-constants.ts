// Shared constants for journal-related components

export interface MoodOption {
  value: string
  emoji: string
  color: string
}

export const MOOD_OPTIONS: MoodOption[] = [
  { value: 'Great', emoji: '😊', color: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800' },
  { value: 'Good', emoji: '🙂', color: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border-blue-200 dark:border-blue-800' },
  { value: 'Okay', emoji: '😐', color: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-800' },
  { value: 'Struggling', emoji: '😔', color: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-200 dark:border-orange-800' },
  { value: 'Difficult', emoji: '😢', color: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 border-red-200 dark:border-red-800' },
]

// Mood config for entry cards with different styling structure
export const MOOD_CONFIG: Record<string, { emoji: string; color: string }> = {
  'Great': { emoji: '😊', color: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' },
  'Good': { emoji: '🙂', color: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' },
  'Okay': { emoji: '😐', color: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400' },
  'Struggling': { emoji: '😔', color: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400' },
  'Difficult': { emoji: '😢', color: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400' },
}

// Prose styling classes shared across components for consistency
export const PROSE_CLASSES = `prose prose-amber dark:prose-invert prose-sm max-w-none
  prose-headings:font-serif prose-headings:text-amber-900 dark:prose-headings:text-amber-100
  prose-p:text-gray-700 dark:prose-p:text-gray-300 prose-p:leading-7 prose-p:font-serif
  prose-strong:text-amber-800 dark:prose-strong:text-amber-200
  prose-em:text-amber-700 dark:prose-em:text-amber-300
  prose-a:text-amber-600 dark:prose-a:text-violet-400 prose-a:no-underline hover:prose-a:underline
  prose-ul:my-2 prose-ol:my-2 prose-li:my-0.5
  prose-blockquote:border-amber-300 dark:prose-blockquote:border-gray-600
  prose-blockquote:bg-amber-50/50 dark:prose-blockquote:bg-gray-800/50
  prose-blockquote:rounded-r-lg prose-blockquote:py-1 prose-blockquote:pr-4
  prose-blockquote:text-amber-800 dark:prose-blockquote:text-gray-300
  prose-code:text-amber-700 dark:prose-code:text-violet-400
  prose-code:bg-amber-100/50 dark:prose-code:bg-gray-800
  prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:font-mono prose-code:text-sm`

// Full page editor prose classes with larger text
export const PROSE_CLASSES_FULLPAGE = `prose prose-amber dark:prose-invert prose-lg max-w-none
  prose-headings:font-serif prose-headings:text-amber-900 dark:prose-headings:text-amber-100 prose-headings:my-3
  prose-p:text-gray-700 dark:prose-p:text-gray-300 prose-p:leading-8 prose-p:font-serif prose-p:my-2
  prose-strong:text-amber-800 dark:prose-strong:text-amber-200
  prose-em:text-amber-700 dark:prose-em:text-amber-300
  prose-a:text-amber-600 dark:prose-a:text-violet-400 prose-a:no-underline hover:prose-a:underline
  prose-ul:my-3 prose-ol:my-3 prose-li:my-1
  prose-blockquote:border-amber-300 dark:prose-blockquote:border-gray-600
  prose-blockquote:bg-amber-50/50 dark:prose-blockquote:bg-gray-800/50
  prose-blockquote:rounded-r-lg prose-blockquote:py-2 prose-blockquote:pr-4 prose-blockquote:my-3
  prose-blockquote:text-amber-800 dark:prose-blockquote:text-gray-300`

// Helper to validate URLs - blocks dangerous protocols
export function isValidUrl(url: string): boolean {
  if (!url || typeof url !== 'string') return false

  const trimmedUrl = url.trim()

  // Block dangerous protocols
  const dangerousProtocols = ['javascript:', 'data:', 'vbscript:', 'file:']
  const lowerUrl = trimmedUrl.toLowerCase()

  for (const protocol of dangerousProtocols) {
    if (lowerUrl.startsWith(protocol)) {
      return false
    }
  }

  // Allow http, https, mailto, tel, and relative URLs
  try {
    // For absolute URLs, validate with URL constructor
    if (trimmedUrl.startsWith('http://') || trimmedUrl.startsWith('https://') ||
        trimmedUrl.startsWith('mailto:') || trimmedUrl.startsWith('tel:')) {
      new URL(trimmedUrl)
      return true
    }

    // Allow relative URLs (starting with / or not having a protocol)
    if (trimmedUrl.startsWith('/') || trimmedUrl.startsWith('#') ||
        !trimmedUrl.includes(':')) {
      return true
    }

    return false
  } catch {
    return false
  }
}
