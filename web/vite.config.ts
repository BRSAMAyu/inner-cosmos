import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import { buildPwaManifest } from "./src/pwaManifest";

export default defineConfig(({ mode }) => ({
  // Clean BrowserRouter deep links need an absolute web base; the Capacitor local
  // origin still needs relative assets. `npm run build:mobile` selects that branch.
  base: mode.startsWith("mobile") || mode.startsWith("tauri") ? "./" : "/app/aurora/",
  plugins: [
    react(),
    VitePWA({
      // Native shells ship immutable bundled assets. Registering a service worker there can
      // keep an older bundle alive across APK upgrades and produce a blank or stale native UI.
      disable: mode.startsWith("mobile") || mode.startsWith("tauri"),
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
            urlPattern: ({ url }: { url: URL }) => url.pathname.startsWith("/api/"),
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
    // Configurable so a local desktop/mobile shell can point at an operator-started dev backend on
    // any free loopback port (the default 8080 may be held by another instance). The app always
    // fetches same-origin `/api/**` (VITE_API_BASE_URL is empty in tauri/mobile-local modes), so this
    // proxy is the only hop between the shell and the backend — keeping it env-driven avoids forking
    // the config per machine without changing the committed default behavior.
    proxy: { "/api": process.env.INNER_COSMOS_API_PROXY ?? "http://localhost:8080" }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    exclude: ["e2e/**", "scripts/**", "node_modules/**", "dist/**"]
  }
}));
