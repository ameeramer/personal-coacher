// Service Worker for Personal Coach PWA
// Handles push notifications for daily journal reminders

const CACHE_NAME = 'personal-coach-v10';

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
    data: data.data,
    actions: [
      {
        action: 'open',
        title: 'Write Entry'
      },
      {
        action: 'dismiss',
        title: 'Later'
      }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

// Notification click event - handle user interaction
self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  if (event.action === 'dismiss') {
    return;
  }

  // Default action or 'open' action - open the journal page
  // Build absolute URL to ensure it works on all platforms including mobile
  const path = event.notification.data?.url || '/journal';
  const urlToOpen = new URL(path, self.location.origin).href;

  // Always use clients.openWindow() - this is the only reliable way to bring
  // the app to the foreground on Android PWA. client.navigate() + client.focus()
  // works for navigation but doesn't bring the app to foreground on Android.
  event.waitUntil(
    clients.openWindow ? clients.openWindow(urlToOpen) : Promise.resolve()
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
