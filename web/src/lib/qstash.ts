import { Client } from '@upstash/qstash'

const globalForQStash = globalThis as unknown as {
  qstashClient: Client | undefined
}

// QStash client - only initialize if QSTASH_TOKEN is set
export const qstashClient = globalForQStash.qstashClient ?? (
  process.env.QSTASH_TOKEN
    ? new Client({ token: process.env.QSTASH_TOKEN })
    : null
)

if (process.env.NODE_ENV !== 'production' && qstashClient) {
  globalForQStash.qstashClient = qstashClient
}

/**
 * Get the base URL for QStash callbacks.
 * In production, this should be your Vercel deployment URL.
 * In development, you'll need to use a tunnel (e.g., ngrok) or deploy to Vercel.
 */
export function getQStashCallbackUrl(): string {
  // Use VERCEL_URL in production, or allow override via QSTASH_CALLBACK_URL
  const callbackUrl = process.env.QSTASH_CALLBACK_URL
    || (process.env.VERCEL_URL ? `https://${process.env.VERCEL_URL}` : null)
    || process.env.NEXTAUTH_URL

  if (!callbackUrl) {
    throw new Error('No callback URL configured. Set QSTASH_CALLBACK_URL, VERCEL_URL, or NEXTAUTH_URL.')
  }

  return callbackUrl
}
