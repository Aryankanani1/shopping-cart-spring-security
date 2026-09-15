/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// The Spring API runs on :8080. In dev we proxy /api through Vite so the browser
// talks to the SAME origin (localhost:5173) — no CORS config is needed on the
// backend, and no code change either. In production, set VITE_API_BASE_URL to the
// real API origin (and enable CORS there, or serve this build from the same host).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    environmentOptions: { jsdom: { url: 'http://localhost/' } },
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false,
  },
})
