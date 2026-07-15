import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/app/aurora/",
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static/app/aurora",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: "assets/app.js",
        chunkFileNames: "assets/[name].js",
        assetFileNames: "assets/[name][extname]"
      }
    }
  },
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8080" }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    exclude: ["e2e/**", "node_modules/**", "dist/**"]
  }
});
