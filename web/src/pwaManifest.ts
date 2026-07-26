// B5-pwa-mobile: the web app manifest, as a plain, testable function rather than an inline
// object literal buried in vite.config.ts. Colors and icons are pulled straight from the
// existing product (web/src/styles.css tokens, the real native app icon) -- nothing here is
// invented artwork or an invented palette.
//
// start_url/scope are relative (".") on purpose: the built app is served at Spring's
// /app/aurora/ path (never the site root) and is also bundled at the Capacitor native
// origin, matching vite.config.ts's `base: "./"` comment. A relative scope resolves against
// wherever manifest.webmanifest itself is served from, so it works unmodified in both places.
export type PwaManifestIcon = {
  src: string;
  sizes: string;
  type: string;
  purpose?: "any" | "maskable";
};

export type PwaManifest = {
  name: string;
  short_name: string;
  description: string;
  start_url: string;
  scope: string;
  display: "standalone";
  background_color: string;
  theme_color: string;
  lang: string;
  icons: PwaManifestIcon[];
};

// Use the light canvas for the OS-controlled launch splash. The app applies the real local-time
// theme immediately after boot; a dark manifest background would otherwise flash a black panel
// even when the user explicitly chose day mode.
const SURFACE_CANVAS_LAUNCH = "#F5F4EF";
const ACCENT_AURORA = "#C79A68";

export function buildPwaManifest(): PwaManifest {
  return {
    name: "Inner Cosmos",
    short_name: "Inner Cosmos",
    description: "A continuous, interruptible conversation with Aurora",
    start_url: ".",
    scope: ".",
    display: "standalone",
    background_color: SURFACE_CANVAS_LAUNCH,
    theme_color: ACCENT_AURORA,
    lang: "en",
    icons: [
      { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "icons/icon-512-maskable.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
