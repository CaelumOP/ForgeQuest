import { defineConfig, type Plugin } from 'vite'
import preact from '@preact/preset-vite'
import { VitePWA } from 'vite-plugin-pwa'

// Which sites the app may load from or talk to. Build only: the dev server needs its own websocket.
const csp = [
  "default-src 'self'",
  "script-src 'self' https://accounts.google.com/gsi/client",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' blob: data: https://*.googleusercontent.com",
  "font-src 'self'",
  "connect-src 'self' https://generativelanguage.googleapis.com https://api.groq.com https://www.googleapis.com https://oauth2.googleapis.com https://accounts.google.com https://raw.githubusercontent.com",
  "frame-src https://accounts.google.com",
  "object-src 'none'",
  "base-uri 'self'",
].join('; ')

const cspMeta: Plugin = {
  name: 'csp-meta',
  apply: 'build',
  transformIndexHtml: (html) => html.replace('<head>', `<head>\n    <meta http-equiv="Content-Security-Policy" content="${csp}" />`),
}

export default defineConfig({
  // Served from caelumop.github.io/ForgeQuest/ (decision 6).
  base: '/ForgeQuest/',
  // One bundle: an old cached page can never ask for a chunk that a newer deploy removed.
  build: { rolldownOptions: { output: { codeSplitting: false } } },
  plugins: [
    preact(),
    cspMeta,
    VitePWA({
      // shortcut: updates install on next open until step 18 adds the "Update ready" prompt.
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg', 'icon-180.png'],
      workbox: { globPatterns: ['**/*.{js,css,html,svg,png,woff2,json}'] },
      manifest: {
        name: 'ForgeQuest',
        short_name: 'ForgeQuest',
        description: 'Workouts, food and a coach that keep you going.',
        start_url: './',
        scope: './',
        display: 'standalone',
        background_color: '#131314',
        theme_color: '#131314',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
})
