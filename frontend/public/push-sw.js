// Recepción de Web Push. Workbox lo carga dentro del service worker que genera vite-plugin-pwa
// (ver workbox.importScripts en vite.config.ts). El contenido llega ya descifrado por el navegador:
// { id, type, severity, title, body, url }.

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { title: 'Control Panel', body: event.data ? event.data.text() : '' }
  }

  event.waitUntil(
    self.registration.showNotification(data.title || 'Control Panel', {
      body: data.body || '',
      // Mismo aviso, misma notificación: si llega dos veces, la segunda sustituye a la primera
      tag: data.id,
      icon: '/pwa-192x192.png',
      badge: '/pwa-64x64.png',
      requireInteraction: data.severity === 'CRITICAL',
      data: { url: data.url || '/' },
    }),
  )
})

// Al tocar la notificación, abre la app (o la trae al frente si ya está abierta)
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      for (const client of windows) {
        if (client.url.startsWith(self.location.origin) && 'focus' in client) {
          return client.focus()
        }
      }
      return self.clients.openWindow(url)
    }),
  )
})
