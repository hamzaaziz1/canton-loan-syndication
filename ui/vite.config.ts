import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Each pane talks only to its own organisation's backend instance (ADR 0003).
// The backends listen on localhost, and the dev server proxies to them, so the
// browser needs no CORS. In production each organisation would serve only its
// own pane, from its own backend; this dev server shows all three side by side.
const backend = (port: number, prefix: string) => ({
  target: `http://127.0.0.1:${port}`,
  rewrite: (path: string) => path.replace(new RegExp(`^/${prefix}`), ''),
})

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/agent': backend(8081, 'agent'),
      '/lendera': backend(8082, 'lendera'),
      '/lenderb': backend(8083, 'lenderb'),
    },
  },
})
