import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const frontendPort = Number(process.env.ORBIT_FRONTEND_PORT || 5173)

export default defineConfig(({ command }) => ({
  plugins: [vue()],
  // Keep test servers and production builds away from the running dev server's optimizer cache.
  cacheDir: `.cache/vite/${command === 'serve' ? `dev-${frontendPort}` : 'build'}`,
  optimizeDeps: { include: ['pdfjs-dist', 'pdf-lib'] },
  server: { port: frontendPort, strictPort: true, proxy: { '/api': { target: process.env.ORBIT_BACKEND_URL||'http://127.0.0.1:8080', changeOrigin: false } } },
  build: { chunkSizeWarningLimit: 1100 },
}))
