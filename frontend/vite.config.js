import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 프론트(5173)에서 /api로 보낸 요청을 백엔드(8080)로 그대로 전달
      // dev 단계에서 Spring 쪽 CORS 설정을 따로 안 해도 되게 하기 위함
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
