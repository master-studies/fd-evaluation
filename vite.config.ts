import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/eureka': {
        target: process.env.VITE_EUREKA_PROXY_TARGET || 'http://localhost:8761',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
