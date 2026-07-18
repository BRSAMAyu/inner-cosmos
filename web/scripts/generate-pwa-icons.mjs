// One-time (re-runnable) PWA icon generator.
//
// Track B / B5-pwa-mobile: the web app manifest needs 192x192 and 512x512 PNG icons
// (plus a "maskable" 512 variant with safe-zone padding for Android adaptive icons).
// No new artwork is invented here -- every icon is derived from the existing native app
// icon at ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png (1024x1024).
//
// Why Playwright/Chromium instead of sharp/ImageMagick: neither is installed in this repo
// (no native image-resize dependency exists anywhere in package.json), and adding one just
// for a one-time icon export was judged not worth a new production dependency. Playwright
// is already a devDependency (used for e2e/observation scripts), so this reuses a real
// <canvas> in headless Chromium to decode + resize the source PNG deterministically --
// no new package added.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(here, "..");
const SOURCE = path.join(
  webRoot,
  "ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png"
);
const OUT_DIR = path.join(webRoot, "public/icons");

// Maskable icons need the visible logo to sit inside the "safe zone" (a centered circle
// covering ~80% of the canvas) so Android's adaptive-icon mask never clips it -- see
// https://web.dev/articles/maskable-icon. The background reuses the source icon's own
// white card background (not an invented color) so the padding is visually seamless with
// the icon art itself rather than introducing a new border color.
const MASKABLE_BG = "#FFFFFF";
const MASKABLE_SAFE_ZONE_RATIO = 0.8;

const targets = [
  { file: "icon-192.png", size: 192, maskable: false },
  { file: "icon-512.png", size: 512, maskable: false },
  { file: "icon-512-maskable.png", size: 512, maskable: true },
];

async function main() {
  if (!fs.existsSync(SOURCE)) {
    throw new Error(`source icon not found: ${SOURCE}`);
  }
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const sourceDataUrl = `data:image/png;base64,${fs.readFileSync(SOURCE).toString("base64")}`;

  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    for (const target of targets) {
      const dataUrl = await page.evaluate(
        async ({ src, size, maskable, bg, safeZoneRatio }) => {
          const img = await new Promise((resolve, reject) => {
            const el = new Image();
            el.onload = () => resolve(el);
            el.onerror = reject;
            el.src = src;
          });
          const canvas = document.createElement("canvas");
          canvas.width = size;
          canvas.height = size;
          const ctx = canvas.getContext("2d");
          if (maskable) {
            ctx.fillStyle = bg;
            ctx.fillRect(0, 0, size, size);
            const drawSize = Math.round(size * safeZoneRatio);
            const offset = Math.round((size - drawSize) / 2);
            ctx.drawImage(img, offset, offset, drawSize, drawSize);
          } else {
            ctx.drawImage(img, 0, 0, size, size);
          }
          return canvas.toDataURL("image/png");
        },
        { src: sourceDataUrl, size: target.size, maskable: target.maskable, bg: MASKABLE_BG, safeZoneRatio: MASKABLE_SAFE_ZONE_RATIO }
      );
      const base64 = dataUrl.replace(/^data:image\/png;base64,/, "");
      const outPath = path.join(OUT_DIR, target.file);
      fs.writeFileSync(outPath, Buffer.from(base64, "base64"));
      console.log(`wrote ${outPath} (${target.size}x${target.size}${target.maskable ? ", maskable" : ""})`);
    }
  } finally {
    await browser.close();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
