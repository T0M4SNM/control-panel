import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// URL del backend en desarrollo. En producción Nginx sirve frontend y API desde el mismo origen.
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'icon.svg', 'apple-touch-icon-180x180.png'],
      manifest: {
        name: 'Control Panel',
        short_name: 'Control Panel',
        description: 'Proyectos, infraestructura, vencimientos y costes',
        lang: 'es',
        start_url: '/',
        display: 'standalone',
        background_color: '#0f172a',
        theme_color: '#0f172a',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // La API nunca pasa por la caché del service worker
        navigateFallbackDenylist: [/^\/api\//, /^\/actuator\//],
        // Recepción de notificaciones Web Push (public/push-sw.js)
        importScripts: ['push-sw.js'],
      },
    }),
  ],
  server: {
    // Mismo origen en desarrollo, para que la cookie de sesión y la de CSRF funcionen igual que en producción
    proxy: {
      '/api': backendUrl,
      '/actuator': backendUrl,
    },
  },
})
