import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/ant-design-vue') || id.includes('node_modules/@ant-design/icons-vue')) return 'vendor-antd'
          if (id.includes('node_modules/vue')) return 'vendor-vue'
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL || 'http://127.0.0.1:7778',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, ''),
      },
    },
  },
})
