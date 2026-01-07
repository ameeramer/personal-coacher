import { GoogleGenerativeAI } from '@google/generative-ai'

const globalForGemini = globalThis as unknown as {
  gemini: GoogleGenerativeAI | undefined
}

export const gemini = globalForGemini.gemini ?? new GoogleGenerativeAI(
  process.env.GOOGLE_GEMINI_API_KEY || ''
)

if (process.env.NODE_ENV !== 'production') globalForGemini.gemini = gemini

export const GEMINI_MODEL = 'gemini-3-pro-preview'
