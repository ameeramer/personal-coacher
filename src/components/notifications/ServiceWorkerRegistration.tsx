'use client'

import { useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'

// Key for storing pending navigation from notification clicks
const NOTIFICATION_NAV_KEY = 'notification_pending_nav'

export function ServiceWorkerRegistration() {
  const router = useRouter()

  // Check for pending navigation (set by service worker via BroadcastChannel or localStorage)
  const checkPendingNavigation = useCallback(() => {
    try {
      const pendingNav = localStorage.getItem(NOTIFICATION_NAV_KEY)
      if (pendingNav) {
        localStorage.removeItem(NOTIFICATION_NAV_KEY)
        const data = JSON.parse(pendingNav)
        // Only navigate if the timestamp is recent (within last 30 seconds)
        if (data.url && data.timestamp && Date.now() - data.timestamp < 30000) {
          const url = new URL(data.url)
          router.push(url.pathname)
        }
      }
    } catch (e) {
      console.error('Error checking pending navigation:', e)
    }
  }, [router])

  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('/sw.js')
        .then((registration) => {
          console.log('Service Worker registered:', registration.scope)

          // Check for updates
          registration.addEventListener('updatefound', () => {
            const newWorker = registration.installing
            if (newWorker) {
              newWorker.addEventListener('statechange', () => {
                if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                  // New service worker available
                  console.log('New Service Worker available')
                }
              })
            }
          })
        })
        .catch((error) => {
          console.error('Service Worker registration failed:', error)
        })

      // Listen for messages from service worker (used for notification click navigation on Android PWA)
      const handleServiceWorkerMessage = (event: MessageEvent) => {
        if (event.data?.type === 'NOTIFICATION_CLICK' && event.data?.url) {
          // Navigate to the URL from the notification
          const url = new URL(event.data.url)
          router.push(url.pathname)
        }
      }

      navigator.serviceWorker.addEventListener('message', handleServiceWorkerMessage)

      // Check for pending navigation on mount (handles case where app was not active when notification was clicked)
      checkPendingNavigation()

      // Also check when the page becomes visible (handles Android PWA resume from background)
      const handleVisibilityChange = () => {
        if (document.visibilityState === 'visible') {
          checkPendingNavigation()
        }
      }
      document.addEventListener('visibilitychange', handleVisibilityChange)

      // Listen for storage events (in case another tab/context sets the pending nav)
      const handleStorage = (event: StorageEvent) => {
        if (event.key === NOTIFICATION_NAV_KEY && event.newValue) {
          checkPendingNavigation()
        }
      }
      window.addEventListener('storage', handleStorage)

      return () => {
        navigator.serviceWorker.removeEventListener('message', handleServiceWorkerMessage)
        document.removeEventListener('visibilitychange', handleVisibilityChange)
        window.removeEventListener('storage', handleStorage)
      }
    }
  }, [router, checkPendingNavigation])

  return null
}
