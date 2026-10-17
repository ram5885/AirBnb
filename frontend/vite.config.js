import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API is the API gateway of the Docker Compose stack on
// http://localhost:8080; override with VITE_API_PROXY_TARGET. Proxying /api means the React app can call
// fetch('/api/...') with no CORS headaches, regardless of what port Vite picks. In Docker, nginx does the
// same proxying (see nginx.conf), so the built app needs no API URL at all.
const apiTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
});
