import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/concepts": {
        target: process.env.CLOSIE_API_URL || "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/demo": {
        target: process.env.CLOSIE_API_URL || "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/api": {
        target: process.env.CLOSIE_API_URL || "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});
