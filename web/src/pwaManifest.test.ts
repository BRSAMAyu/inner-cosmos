import { describe, expect, it } from "vitest";
import { buildPwaManifest } from "./pwaManifest";

// B5-pwa-mobile: pins the shape of the generated web app manifest before wiring it into
// vite.config.ts's VitePWA plugin. jsdom cannot run a real service worker or install a
// manifest, so this is the honest boundary of what's unit-testable here -- the rest
// (installability, offline caching) is verified live against a real Chromium instance
// (see evidence/track-b/scripts/observe-b1-pwa-offline.mjs).
describe("buildPwaManifest", () => {
  const manifest = buildPwaManifest();

  it("names the product consistently with index.html's <title>", () => {
    expect(manifest.name).toBe("Inner Cosmos · 内宇宙");
    expect(manifest.short_name).toBe("内宇宙");
  });

  it("is scoped under /app/aurora/, not the site root", () => {
    // Relative paths so the same manifest resolves correctly whether served by Spring at
    // /app/aurora/manifest.webmanifest or bundled at the Capacitor native origin.
    expect(manifest.start_url).toBe(".");
    expect(manifest.scope).toBe(".");
  });

  it("opens as a standalone app, not a browser tab", () => {
    expect(manifest.display).toBe("standalone");
  });

  it("uses the existing warm night-theme tokens, not invented colors", () => {
    // --surface-canvas (night default) and --accent-aurora from web/src/styles.css.
    expect(manifest.background_color).toBe("#211A18");
    expect(manifest.theme_color).toBe("#C79A68");
  });

  it("declares 192 and 512 icons plus a maskable 512 variant, all derived from the real app icon", () => {
    const sizes = manifest.icons?.map(icon => icon.sizes);
    expect(sizes).toEqual(["192x192", "512x512", "512x512"]);
    const purposes = manifest.icons?.map(icon => icon.purpose ?? "any");
    expect(purposes).toEqual(["any", "any", "maskable"]);
    for (const icon of manifest.icons ?? []) {
      expect(icon.src.startsWith("icons/")).toBe(true);
      expect(icon.type).toBe("image/png");
    }
  });

  it("returns a fresh object each call (no shared-mutation risk between test/build)", () => {
    const other = buildPwaManifest();
    expect(other).not.toBe(manifest);
    expect(other).toEqual(manifest);
  });
});
