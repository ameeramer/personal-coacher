'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'

export function ServiceWorkerRegistration() {
  const router = useRouter()

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

      return () => {
        navigator.serviceWorker.removeEventListener('message', handleServiceWorkerMessage)
      }
    }
  }, [router])

  return null
}
