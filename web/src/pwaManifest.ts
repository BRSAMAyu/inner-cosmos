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

// --surface-canvas (night, default theme) and --accent-aurora from web/src/styles.css.
const SURFACE_CANVAS_NIGHT = "#211A18";
const ACCENT_AURORA = "#C79A68";

export function buildPwaManifest(): PwaManifest {
  return {
    name: "Inner Cosmos · 内宇宙",
    short_name: "内宇宙",
    description: "Inner Cosmos — 与 Aurora 保持连续、可打断的真实对话",
    start_url: ".",
    scope: ".",
    display: "standalone",
    background_color: SURFACE_CANVAS_NIGHT,
    theme_color: ACCENT_AURORA,
    lang: "zh-CN",
    icons: [
      { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "icons/icon-512-maskable.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
