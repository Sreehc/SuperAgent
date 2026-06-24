/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('/recharts/')) return 'charts-vendor'
          if (id.includes('/marked/') || id.includes('/highlight.js/') || id.includes('/dompurify/')) {
            return 'content-vendor'
          }
          if (id.includes('/@radix-ui/') || id.includes('/lucide-react/') || id.includes('/@phosphor-icons/')) {
            return 'ui-vendor'
          }
          if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/react-router-dom/')) {
            return 'react-vendor'
          }
          if (id.includes('/@tanstack/react-query/') || id.includes('/@tanstack/react-table/') || id.includes('/zustand/')) {
            return 'data-vendor'
          }
        },
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
