import { GoogleGenerativeAI } from '@google/generative-ai'

const globalForGemini = globalThis as unknown as {
  gemini: GoogleGenerativeAI | undefined
}

export const gemini = globalForGemini.gemini ?? new GoogleGenerativeAI(
  process.env.GOOGLE_GEMINI_API_KEY || ''
)

if (process.env.NODE_ENV !== 'production') globalForGemini.gemini = gemini

export const DEFAULT_GEMINI_MODEL = 'gemini-3-pro-preview'

export const GEMINI_MODEL_OPTIONS = [
  { value: 'gemini-3-pro-preview', label: 'Gemini 3 Pro Preview' },
  { value: 'gemini-3-flash', label: 'Gemini 3 Flash' },
  { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
  { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' }
] as const

export type GeminiModelOption = typeof GEMINI_MODEL_OPTIONS[number]['value']

export function getGeminiModel(modelId: string = DEFAULT_GEMINI_MODEL) {
  return gemini.getGenerativeModel({ model: modelId })
}
