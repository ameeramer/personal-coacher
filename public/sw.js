// Service Worker for Personal Coach PWA
// Handles push notifications for daily journal reminders

const CACHE_NAME = 'personal-coach-v6';

// Install event - cache essential files
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll([
        '/',
        '/journal',
        '/coach',
        '/manifest.json',
      ]);
    })
  );
  // Activate immediately
  self.skipWaiting();
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => caches.delete(name))
      );
    })
  );
  // Take control of all pages immediately
  self.clients.claim();
});

// Push event - handle incoming push notifications
self.addEventListener('push', (event) => {
  let data = {
    title: 'Journal Reminder',
    body: "Time to reflect on your day! Take a moment to journal your thoughts.",
    icon: '/icons/icon-192.svg',
    badge: '/icons/icon-192.svg',
    tag: 'journal-reminder',
    data: {
      url: '/journal'
    }
  };

  // If push event has data, use it
  if (event.data) {
    try {
      const pushData = event.data.json();
      data = { ...data, ...pushData };
    } catch {
      // If not JSON, use as body text
      data.body = event.data.text();
    }
  }

  const options = {
    body: data.body,
    icon: data.icon,
    badge: data.badge,
    tag: data.tag,
    vibrate: [200, 100, 200],
    requireInteraction: true,
    data: data.data
    // Note: Action buttons removed due to Android PWA Chrome bug where action button
    // clicks don't properly bring the app to foreground. Body click works reliably.
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

// Notification click event - handle user interaction
self.addEventListener('notificationclick', (event) => {
  // Close notification immediately - this is safe outside waitUntil for body clicks
  event.notification.close();

  // Build absolute URL to ensure it works on all platforms including mobile
  const path = event.notification.data?.url || '/journal';
  let urlToOpen;
  try {
    urlToOpen = new URL(path, self.location.origin).href;
  } catch {
    // Fallback if URL constructor fails
    urlToOpen = self.location.origin + '/journal';
  }

  event.waitUntil(
    (async () => {
      // Try to find an existing window to navigate/focus
      const windowClients = await clients.matchAll({ type: 'window', includeUncontrolled: true });

      for (const client of windowClients) {
        if (client.url.startsWith(self.location.origin) && 'focus' in client) {
          // Try to navigate the existing window
          try {
            if ('navigate' in client) {
              await client.navigate(urlToOpen);
            }
            // Send postMessage as backup for navigation
            client.postMessage({ type: 'NOTIFICATION_CLICK', url: urlToOpen });
            return client.focus();
          } catch {
            // Navigation or focus failed, fall through to openWindow
          }
        }
      }

      // No existing window found or navigation failed - open a new window
      if (clients.openWindow) {
        return clients.openWindow(urlToOpen);
      }
    })()
  );
});

// Notification close event
self.addEventListener('notificationclose', (event) => {
  // Could track dismissed notifications here if needed
  console.log('Notification dismissed:', event.notification.tag);
});

// Fetch event - serve from cache, fall back to network
self.addEventListener('fetch', (event) => {
  // Only handle GET requests
  if (event.request.method !== 'GET') {
    return;
  }

  // Skip API calls and external resources
  if (event.request.url.includes('/api/') || !event.request.url.startsWith(self.location.origin)) {
    return;
  }

  event.respondWith(
    caches.match(event.request).then((response) => {
      // Return cached version or fetch from network
      return response || fetch(event.request).then((fetchResponse) => {
        // Cache successful responses
        if (fetchResponse.ok) {
          const responseClone = fetchResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseClone);
          });
        }
        return fetchResponse;
      });
    }).catch(() => {
      // Offline fallback
      if (event.request.destination === 'document') {
        return caches.match('/');
      }
    })
  );
});
