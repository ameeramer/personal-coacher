/**
 * System prompt for generating daily tools.
 * This is the same prompt used in the Android app for consistency.
 */
export const DAILY_TOOL_SYSTEM_PROMPT = `You are a creative personal growth tool designer. Your task is to generate a UNIQUE, interactive web app that helps the user based on their recent journal entries.

CRITICAL REQUIREMENTS:
1. Generate a complete, self-contained HTML file with ALL CSS and JavaScript inline
2. The app should be interactive and engaging
3. It should take 2-5 minutes to use meaningfully
4. It MUST be relevant to the user's current emotional state and journal content
5. Be CREATIVE and SURPRISING - avoid generic patterns
6. NEVER create the same or similar tool that the user has already received - check the "PREVIOUSLY CREATED TOOLS" section and make something completely different

TECHNICAL REQUIREMENTS:
1. Include all CSS in a <style> tag in the <head>
2. Include all JavaScript in a <script> tag before </body>
3. Use modern CSS (flexbox, grid, animations, gradients)
4. Make it mobile-friendly (use viewport meta tag, responsive design)
5. DO NOT use any external resources (no CDN links, no external fonts, no images)
6. DO NOT use localStorage, cookies, or sessionStorage - use the Android bridge for persistence

CRITICAL - SCROLLING & KEYBOARD HANDLING:
The app runs in a WebView on mobile. You MUST ensure proper scrolling and keyboard handling:

1. ALWAYS make the page scrollable:
   - Use height: auto or min-height: 100vh on body, NEVER height: 100vh
   - Container should use min-height, not fixed height
   - Use overflow-y: auto on scrollable containers

2. Required CSS for proper scrolling:
   html, body {
     min-height: 100vh;
     height: auto;
     overflow-x: hidden;
     overflow-y: auto;
     -webkit-overflow-scrolling: touch;
   }

3. Input fields MUST be visible when keyboard opens:
   - Add padding-bottom: 300px to the main container to ensure space for keyboard
   - Use scroll-padding-bottom: 300px on body
   - When input is focused, scroll it into view using JavaScript:
     input.addEventListener('focus', () => {
       setTimeout(() => input.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
     });

4. For forms with multiple inputs:
   - Add margin-bottom: 16px between inputs
   - Add extra bottom padding (at least 300px) at the end of the form
   - Use flex-direction: column and allow natural document flow

5. AVOID these patterns that break scrolling:
   - position: fixed on containers (except for headers)
   - height: 100vh on body or main containers
   - overflow: hidden on body
   - vh units for container heights (use min-height instead)

DATA PERSISTENCE (IMPORTANT):
The app runs in an Android WebView with these JavaScript methods available:
- Android.saveData(key, value) - Save a string value persistently
- Android.loadData(key) - Load a saved value (returns "" if not found)
- Android.getAllData() - Get all saved data as JSON string
- Android.clearData() - Clear all saved data for this app
- Android.getAppInfo() - Get app metadata as JSON (title, createdAt)

For complex data, use JSON.stringify() and JSON.parse():
  // Save
  Android.saveData("state", JSON.stringify(yourObject));
  // Load
  let state = JSON.parse(Android.loadData("state") || "{}");

DESIGN REQUIREMENTS - MUST MATCH THE PARENT APP'S iOS-STYLE DESIGN:
The generated app MUST follow these exact design specifications to match the parent app:

1. COLOR PALETTE:
   - Light mode background: #F2F2F7 (iOS system background)
   - Card/surface background: #FFFFFF
   - Primary color (amber): #D97706
   - Primary container: #FEF3C7 (amber100)
   - Text primary: #171717
   - Text secondary: #525252
   - Border color: rgba(0,0,0,0.1)
   - For dark mode support, use CSS media query @media (prefers-color-scheme: dark)
   - Dark mode background: #000000
   - Dark mode surface: #1C1C1E
   - Dark mode primary (lavender): #8B82D1
   - Dark mode text: #F5F5F5

2. TYPOGRAPHY:
   - Use system fonts: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif
   - Headings: Use Georgia, serif font-family with bold weight
   - Body text: 16px base, line-height 1.5
   - Labels: 14px, slightly muted color

3. SPACING & LAYOUT:
   - Screen padding: 20px
   - Card padding: 20px
   - Section spacing: 24px
   - Item spacing: 16px
   - Use flexbox or CSS grid for layouts

4. COMPONENTS STYLE:
   - Cards: background white, border-radius 16px, subtle shadow (0 1px 3px rgba(0,0,0,0.1)), thin border (0.5px solid rgba(0,0,0,0.1))
   - Buttons: border-radius 12px, padding 16px 24px, primary uses amber #D97706 with white text
   - Input fields: border-radius 12px, padding 16px, subtle border
   - Use soft, rounded corners everywhere (12-20px border-radius)

5. ANIMATIONS:
   - Use subtle transitions (0.2s ease)
   - Gentle scale on button press (transform: scale(0.98))
   - Fade in for content (opacity animation)

6. VISUAL STYLE:
   - Clean, minimal iOS-like aesthetic
   - Generous whitespace
   - Soft shadows, not harsh
   - No harsh borders - use subtle separators
   - Icons should be simple line icons or emoji

RESPONSE FORMAT:
Respond with ONLY a JSON object (no markdown, no explanation):
{
  "title": "Short, catchy title (3-5 words)",
  "description": "Why this app will help the user (2-3 sentences based on their journal)",
  "journalContext": "Brief quote or insight from the journal that inspired this",
  "htmlCode": "<!DOCTYPE html>..."
}

AVOID:
- Generic breathing exercises (unless truly unique)
- Simple form/checklist UIs
- Harsh colors or high contrast that doesn't match the soft aesthetic
- Apps that feel like templates
- Using the word "journey" excessively
- Dark/heavy UI - keep it light and airy
- Fixed heights (height: 100vh) - always use min-height
- overflow: hidden on body or containers
- Layouts that don't scroll on mobile
- Input fields at the bottom without keyboard padding`

/**
 * Mood mapping from stored values to display names
 */
const MOOD_DISPLAY_NAMES: Record<string, string> = {
  'GREAT': 'Great',
  'GOOD': 'Good',
  'OKAY': 'Okay',
  'BAD': 'Bad',
  'TERRIBLE': 'Terrible'
}

interface JournalEntry {
  content: string
  mood: string | null
  tags: string[]
  date: Date
}

interface PreviousTool {
  title: string
  description: string
  date: Date
}

/**
 * Build the user prompt for daily tool generation.
 */
export function buildDailyToolUserPrompt(
  entries: JournalEntry[],
  previousTools: PreviousTool[]
): string {
  const dateFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric'
  })

  // Format journal entries
  const formattedEntries = entries.map((entry, index) => {
    // Remove HTML tags and limit length
    const cleanContent = entry.content
      .replace(/<[^>]*>/g, '')
      .substring(0, 500)

    const moodDisplay = entry.mood ? (MOOD_DISPLAY_NAMES[entry.mood.toUpperCase()] || entry.mood) : 'Not specified'
    const tagsDisplay = entry.tags.length > 0 ? entry.tags.join(', ') : 'None'

    return `Entry ${index + 1} (${dateFormatter.format(entry.date)}):
Mood: ${moodDisplay}
Tags: ${tagsDisplay}
Content: ${cleanContent}`
  }).join('\n\n')

  // Analyze mood trends
  const moods = entries.map(e => e.mood).filter(Boolean) as string[]
  let moodSummary = 'Mood data not available'

  if (moods.length > 0) {
    const moodCounts = moods.reduce((acc, mood) => {
      acc[mood] = (acc[mood] || 0) + 1
      return acc
    }, {} as Record<string, number>)

    const dominantMood = Object.entries(moodCounts)
      .sort((a, b) => b[1] - a[1])[0]?.[0]

    if (dominantMood) {
      const displayName = MOOD_DISPLAY_NAMES[dominantMood.toUpperCase()] || dominantMood
      moodSummary = `Dominant mood: ${displayName}`
    } else {
      moodSummary = 'Dominant mood: Mixed'
    }
  }

  // Format previous tools section
  let previousToolsSection = ''
  if (previousTools.length > 0) {
    const toolsList = previousTools.map(tool => {
      const toolDate = dateFormatter.format(tool.date)
      return `- "${tool.title}" (${toolDate}): ${tool.description}`
    }).join('\n')

    previousToolsSection = `
PREVIOUSLY CREATED TOOLS (DO NOT CREATE SIMILAR ONES):
${toolsList}

IMPORTANT: Create something COMPLETELY DIFFERENT from the tools listed above. Do not use the same concepts, exercises, or app types. Be creative and explore new approaches.
`
  }

  return `Based on the user's recent journal entries, create a personalized interactive web app that will genuinely help them.

RECENT JOURNAL ENTRIES:
${formattedEntries}

MOOD ANALYSIS:
${moodSummary}
${previousToolsSection}
Generate a unique, creative app that addresses what this user is experiencing. Make it beautiful, interactive, and meaningful.`
}

/**
 * Parse and validate the generated tool response from Claude.
 */
export interface GeneratedToolResponse {
  title: string
  description: string
  journalContext: string | null
  htmlCode: string
}

export function parseGeneratedToolResponse(response: string): GeneratedToolResponse {
  // Try to extract JSON from the response
  let jsonString = response.trim()

  // If response is wrapped in markdown code blocks, extract it
  if (!jsonString.startsWith('{')) {
    const jsonMatch = jsonString.match(/```(?:json)?\s*([\s\S]*?)```/)
    if (jsonMatch) {
      jsonString = jsonMatch[1].trim()
    } else {
      // Try to find JSON object in the response
      const startIndex = jsonString.indexOf('{')
      const endIndex = jsonString.lastIndexOf('}')
      if (startIndex !== -1 && endIndex !== -1 && endIndex > startIndex) {
        jsonString = jsonString.substring(startIndex, endIndex + 1)
      }
    }
  }

  const parsed = JSON.parse(jsonString)

  // Validate required fields
  if (!parsed.title || typeof parsed.title !== 'string') {
    throw new Error('Missing or invalid title in response')
  }
  if (!parsed.description || typeof parsed.description !== 'string') {
    throw new Error('Missing or invalid description in response')
  }
  if (!parsed.htmlCode || typeof parsed.htmlCode !== 'string') {
    throw new Error('Missing or invalid htmlCode in response')
  }

  // Validate HTML code
  if (!parsed.htmlCode.includes('<!DOCTYPE html>') && !parsed.htmlCode.includes('<html')) {
    throw new Error('Invalid HTML code generated - missing DOCTYPE or html tag')
  }

  return {
    title: parsed.title,
    description: parsed.description,
    journalContext: parsed.journalContext || null,
    htmlCode: parsed.htmlCode
  }
}
