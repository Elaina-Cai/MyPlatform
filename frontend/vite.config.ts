import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 生产构建产物输出到本仓库自带的 nginx 的 html 目录，由 nginx 提供静态资源。
// 开发：Vite 5173 + proxy；访问生产效果：先 npm run build，再启动 nginx（见 nginx-1.30.1/conf/nginx.conf）。
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../nginx-1.30.1/html",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws": {
        target: "http://localhost:8080",
        ws: true,
      },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws": {
        target: "http://localhost:8080",
        ws: true,
      },
    },
  },
});