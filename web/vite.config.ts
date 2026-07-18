import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import { buildPwaManifest } from "./src/pwaManifest";

export default defineConfig({
  // Relative assets work both at Spring's /app/aurora/ path and inside the
  // Capacitor local origin. An absolute base makes the bundled native app blank.
  base: "./",
  plugins: [
    react(),
    VitePWA({
      // registerType "prompt" (not "autoUpdate"): confirmed by reading vite-plugin-pwa's
      // generated client (node_modules/vite-plugin-pwa/dist/client/build/register.js) rather
      // than guessing from the option name. Under "autoUpdate" the generated register script
      // NEVER calls onNeedRefresh at all -- it silently reloads the page itself the instant a
      // new service worker activates, with zero user-visible warning, which could interrupt an
      // in-progress Aurora conversation. "prompt" mode surfaces needRefresh/offlineReady state
      // instead (see web/src/components/PwaUpdateNotice.tsx) and only applies the waiting
      // worker -- via the update-service-worker call the banner's "现在刷新" button
      // triggers -- once the user explicitly chooses to. autoUpdate + a visible refresh
      // button would otherwise race (the page could reload out from under the user before
      // they click anything), so this is a real behavior change, not just added UI.
      registerType: "prompt",
      manifest: buildPwaManifest(),
      // Only precache the built static app shell (JS/CSS/HTML/icons/manifest -- globPatterns
      // below already matches web/public/icons/*.png in the build output, so no separate
      // includeAssets entry is needed). Never add a runtimeCaching rule that could put a
      // /api/** response into a cache -- P0 payloads (conversation content, memory, personal
      // data) must never be cached client-side.
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,ico,webmanifest}"],
        // SPA navigation offline: serve the cached app shell for any navigation request
        // instead of a blank browser network-error page. The app's existing bootstrap
        // error state (web/src/loading.tsx's ConnectError, wired in AuroraApp.tsx) already
        // renders a branded, non-blank "没能连上你的内宇宙" message with a retry button once
        // the shell itself can load offline -- reused as-is, not reinvented here.
        navigateFallback: "index.html",
        navigateFallbackDenylist: [/^\/api\//],
        // Belt-and-suspenders: explicit NetworkOnly for /api/** so no future runtimeCaching
        // rule change can silently start caching sensitive API responses.
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith("/api/"),
            handler: "NetworkOnly",
          },
        ],
      },
    }),
  ],
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
    exclude: ["e2e/**", "scripts/**", "node_modules/**", "dist/**"]
  }
});
