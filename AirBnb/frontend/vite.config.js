import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend (Spring Boot) runs on http://localhost:8080.
// Proxying /api means the React app can call fetch('/api/...') with no CORS
// headaches during development, regardless of what port Vite picks.
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
});
