import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import { buildPwaManifest } from "./src/pwaManifest";
import {
  pwaRegisterTypeForMode,
  shouldActivatePwaUpdateImmediately,
} from "./src/pwaUpdatePolicy";

export default defineConfig(({ mode }) => {
  const installedBundle = mode.startsWith("mobile") || mode.startsWith("tauri") || mode === "demo";
  const activatePwaUpdateImmediately = shouldActivatePwaUpdateImmediately(mode);
  return ({
  // Clean BrowserRouter deep links need an absolute web base; the Capacitor local
  // origin still needs relative assets. Demo mode is also an installed Capacitor
  // bundle, so an absolute /app/aurora base would produce a blank APK.
  base: installedBundle ? "./" : "/app/aurora/",
  plugins: [
    react(),
    VitePWA({
      // Native shells ship immutable bundled assets. Registering a service worker there can
      // keep an older bundle alive across APK upgrades and produce a blank or stale native UI.
      disable: installedBundle,
      // Normal web builds prompt before reloading so a conversation is not interrupted.
      // The classroom build auto-updates because consistency across tutor devices matters
      // more than preserving an ephemeral tab during a live demonstration.
      registerType: pwaRegisterTypeForMode(mode),
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
        // Classroom Demo updates take control immediately so a returning tutor never stays on
        // an old shell. API payloads remain NetworkOnly and are never cached.
        ...(activatePwaUpdateImmediately ? { skipWaiting: true, clientsClaim: true } : {}),
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
        // Every deployed shell must reference immutable, content-addressed assets. Fixed
        // app.js/index.css names were emitted into Workbox with `revision: null`, allowing a
        // returning browser to keep an old dark-theme stylesheet after a new build.
        entryFileNames: "assets/[name]-[hash].js",
        chunkFileNames: "assets/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]"
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
  });
});
