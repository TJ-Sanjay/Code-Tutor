import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The React app talks to the Spring Boot API through this proxy, so there is no CORS to configure in development.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 4000
  }
})
