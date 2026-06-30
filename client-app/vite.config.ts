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
    port: process.env.VITE_PORT ? parseInt(process.env.VITE_PORT) : 5173,
    proxy: {
      '/eureka': {
        // docker-compose dev default (svc-discovery-service exposes 4706)
        target: process.env.VITE_EUREKA_PROXY_TARGET || 'http://svc-discovery-service:4706',
        changeOrigin: true,
        secure: false,
      },
      '/fd-discovery': {
        target: process.env.VITE_FD_DISCOVERY_PROXY_TARGET || 'http://svc-fd-discovery:4705',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/fd-discovery/, ''),
      },
      '/coverage': {
        target: process.env.VITE_COVERAGE_PROXY_TARGET || 'http://svc-coverage:4703',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/coverage/, ''),
      },
      '/genuineness': {
        target: process.env.VITE_GENUINENESS_PROXY_TARGET || 'http://svc-genuineness:4702',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/genuineness/, ''),
      },
      '/entropy': {
        target: process.env.VITE_ENTROPY_PROXY_TARGET || 'http://svc-entropy:4701',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/entropy/, ''),
      },
      '/succinctness': {
        target: process.env.VITE_SUCCINCTNESS_PROXY_TARGET || 'http://svc-succinctness:4704',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/succinctness/, ''),
      },
      '/evaluation-patterns': {
        target: process.env.VITE_EVALUATION_PATTERNS_PROXY_TARGET || 'http://svc-evaluation-patterns:4699',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/evaluation-patterns/, ''),
      },
    },
  },
})
