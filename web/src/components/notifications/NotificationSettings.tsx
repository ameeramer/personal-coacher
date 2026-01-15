'use client'

import { useState, useEffect, useCallback } from 'react'
import { useSession } from 'next-auth/react'

const BellIcon = () => (
  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0" />
  </svg>
)

const BellOffIcon = () => (
  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9.143 17.082a24.248 24.248 0 003.844.148m-3.844-.148a23.856 23.856 0 01-5.455-1.31 8.964 8.964 0 002.3-5.542m3.155 6.852a3 3 0 005.667 1.97m1.965-2.277L21 21m-4.225-4.225a23.81 23.81 0 003.536-1.003A8.967 8.967 0 0118 9.75V9A6 6 0 006.53 6.53m10.245 10.245L6.53 6.53M3 3l3.53 3.53" />
  </svg>
)

const CheckIcon = () => (
  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
  </svg>
)

type NotificationStatus = 'loading' | 'unsupported' | 'denied' | 'subscribed' | 'unsubscribed' | 'error'

export function NotificationSettings() {
  const { data: session } = useSession()
  const [status, setStatus] = useState<NotificationStatus>('loading')
  const [isProcessing, setIsProcessing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const checkSubscriptionStatus = useCallback(async () => {
    // Check if browser supports push notifications
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      setStatus('unsupported')
      return
    }

    // Check notification permission
    if (Notification.permission === 'denied') {
      setStatus('denied')
      return
    }

    try {
      const registration = await navigator.serviceWorker.ready
      const subscription = await registration.pushManager.getSubscription()

      if (subscription) {
        // Verify subscription exists on server
        const response = await fetch('/api/notifications/subscribe')
        if (response.ok) {
          const data = await response.json()
          setStatus(data.hasSubscription ? 'subscribed' : 'unsubscribed')
        } else {
          setStatus('unsubscribed')
        }
      } else {
        setStatus('unsubscribed')
      }
    } catch (err) {
      console.error('Error checking subscription:', err)
      setStatus('error')
      setError('Failed to check notification status')
    }
  }, [])

  useEffect(() => {
    if (session?.user) {
      checkSubscriptionStatus()
    }
  }, [session, checkSubscriptionStatus])

  const subscribe = async () => {
    setIsProcessing(true)
    setError(null)

    let browserSubscription: PushSubscription | null = null

    try {
      // Request notification permission
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') {
        setStatus('denied')
        setError('Notification permission denied')
        return
      }

      // Get VAPID public key from server
      const keyResponse = await fetch('/api/notifications/send')
      if (!keyResponse.ok) {
        throw new Error('Failed to get VAPID key')
      }
      const { vapidPublicKey } = await keyResponse.json()

      // Register service worker and subscribe to push
      const registration = await navigator.serviceWorker.ready

      browserSubscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey)
      })

      // Save subscription to server
      const response = await fetch('/api/notifications/subscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(browserSubscription.toJSON())
      })

      if (!response.ok) {
        throw new Error('Failed to save subscription')
      }

      setStatus('subscribed')
    } catch (err) {
      console.error('Error subscribing:', err)
      setError(err instanceof Error ? err.message : 'Failed to enable notifications')
      setStatus('error')

      // Clean up browser subscription if server save failed
      if (browserSubscription) {
        try {
          await browserSubscription.unsubscribe()
        } catch (cleanupErr) {
          console.error('Error cleaning up browser subscription:', cleanupErr)
        }
      }
    } finally {
      setIsProcessing(false)
    }
  }

  const unsubscribe = async () => {
    setIsProcessing(true)
    setError(null)

    try {
      const registration = await navigator.serviceWorker.ready
      const subscription = await registration.pushManager.getSubscription()

      if (subscription) {
        // Remove from server first
        await fetch('/api/notifications/subscribe', {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ endpoint: subscription.endpoint })
        })

        // Then unsubscribe locally
        await subscription.unsubscribe()
      }

      setStatus('unsubscribed')
    } catch (err) {
      console.error('Error unsubscribing:', err)
      setError(err instanceof Error ? err.message : 'Failed to disable notifications')
      setStatus('error')
    } finally {
      setIsProcessing(false)
    }
  }

  if (!session) {
    return null
  }

  return (
    <div className="bg-[var(--glass-bg)] backdrop-blur-[20px] rounded-2xl border border-[var(--glass-border)] shadow-[var(--glass-shadow)] p-7">
      <div className="flex items-center gap-4 mb-5">
        <div className="p-3 rounded-xl bg-[var(--accent-primary)]/10 text-[var(--accent-primary)]">
          <BellIcon />
        </div>
        <div>
          <h3 className="text-lg font-semibold text-[var(--foreground)]">Daily Reminders</h3>
          <p className="text-sm text-[var(--muted)] mt-0.5">Get notified at 22:15 to journal</p>
        </div>
      </div>

      {error && (
        <div className="mb-5 p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 text-sm">
          {error}
        </div>
      )}

      {status === 'loading' && (
        <div className="flex items-center gap-3 text-[var(--muted)]">
          <div className="w-4 h-4 border-2 border-[var(--accent-primary)] border-t-transparent rounded-full animate-spin" />
          <span className="text-sm">Checking notification status...</span>
        </div>
      )}

      {status === 'unsupported' && (
        <div className="p-4 rounded-xl bg-[var(--foreground)]/5 border border-[var(--glass-border)] text-[var(--muted)] text-sm">
          <p className="font-medium mb-1 text-[var(--foreground)]">Push notifications not supported</p>
          <p className="text-[var(--muted-foreground)]">Your browser doesn&apos;t support push notifications. Try using Chrome or Firefox on Android.</p>
        </div>
      )}

      {status === 'denied' && (
        <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-600 dark:text-amber-400 text-sm">
          <p className="font-medium mb-1">Notifications blocked</p>
          <p className="text-amber-600/80 dark:text-amber-400/80">You&apos;ve blocked notifications. Please enable them in your browser settings to receive daily reminders.</p>
        </div>
      )}

      {status === 'subscribed' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 p-4 rounded-xl bg-[var(--accent-primary)]/10 text-[var(--accent-primary)]">
            <CheckIcon />
            <span className="text-sm font-medium">Notifications enabled</span>
          </div>
          <button
            onClick={unsubscribe}
            disabled={isProcessing}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium text-[var(--muted)] bg-[var(--foreground)]/5 hover:bg-[var(--foreground)]/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isProcessing ? (
              <div className="w-4 h-4 border-2 border-[var(--muted)] border-t-transparent rounded-full animate-spin" />
            ) : (
              <BellOffIcon />
            )}
            Disable notifications
          </button>
        </div>
      )}

      {status === 'unsubscribed' && (
        <button
          onClick={subscribe}
          disabled={isProcessing}
          className="flex items-center gap-2 px-5 py-3 rounded-xl text-sm font-medium text-[var(--accent-primary)] bg-transparent border-2 border-[var(--accent-primary)]/50 hover:bg-[var(--accent-primary)]/10 hover:border-[var(--accent-primary)] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isProcessing ? (
            <div className="w-4 h-4 border-2 border-[var(--accent-primary)] border-t-transparent rounded-full animate-spin" />
          ) : (
            <BellIcon />
          )}
          Enable daily reminders
        </button>
      )}

      {status === 'error' && (
        <button
          onClick={subscribe}
          disabled={isProcessing}
          className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium text-[var(--muted)] bg-[var(--foreground)]/5 hover:bg-[var(--foreground)]/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isProcessing ? (
            <div className="w-4 h-4 border-2 border-[var(--muted)] border-t-transparent rounded-full animate-spin" />
          ) : (
            <BellIcon />
          )}
          Try again
        </button>
      )}
    </div>
  )
}

// Helper function to convert VAPID key
function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/')

  const rawData = window.atob(base64)
  const buffer = new ArrayBuffer(rawData.length)
  const outputArray = new Uint8Array(buffer)

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i)
  }
  return outputArray
}
