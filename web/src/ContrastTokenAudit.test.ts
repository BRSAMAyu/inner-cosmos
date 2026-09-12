import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * CP-12 token-level contrast audit (WCAG 2.2 AA). Parses the design tokens out of
 * styles.css for BOTH effective themes — the default warm-night `:root` and the merged
 * `:root[data-theme="day"]` blocks (later blocks win per CSS cascade) — and asserts every
 * text-bearing token pairing reaches 4.5:1. Token-level (not rendered-DOM-level) on
 * purpose: the tokens are the contract every future component inherits; a regression here
 * fails before any component ships with it.
 */

type Tokens = Record<string, string>;

function parseBlock(css: string, selector: RegExp): Tokens {
  const tokens: Tokens = {};
  for (const match of css.matchAll(selector)) {
    const body = match[1];
    for (const decl of body.matchAll(/--([a-z0-9-]+)\s*:\s*([^;}]+)/gi)) {
      tokens[`--${decl[1]}`] = decl[2].trim();
    }
  }
  return tokens;
}

function merge(base: Tokens, override: Tokens): Tokens {
  return { ...base, ...override };
}

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace("#", "").trim();
  const full = clean.length === 3
    ? clean.split("").map(ch => ch + ch).join("")
    : clean.slice(0, 6);
  if (!/^[0-9a-f]{6}$/i.test(full)) throw new Error(`non-hex token value: ${hex}`);
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16)
  ];
}

/** WCAG 2.x relative luminance. */
function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map(channel => {
    const srgb = channel / 255;
    return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(foreground: string, background: string): number {
  const a = luminance(foreground);
  const b = luminance(background);
  const [light, dark] = a >= b ? [a, b] : [b, a];
  return (light + 0.05) / (dark + 0.05);
}

const css = readFileSync(resolve(__dirname, "styles.css"), "utf-8");
const night = parseBlock(css, /:root\s*\{([^}]*)\}/g);
const dayBlocks = parseBlock(css, /:root\[data-theme="day"\]\s*\{([^}]*)\}/g);
const day = merge(night, dayBlocks);

/** The text-bearing token pairs the design contract promises. */
const PAIRS: Array<[string, string, string]> = [
  ["text-primary", "--text-primary", "--surface-canvas"],
  ["text-primary on raised", "--text-primary", "--surface-raised"],
  ["text-muted", "--text-muted", "--surface-canvas"],
  ["text-muted on raised", "--text-muted", "--surface-raised"],
  ["text-faint (meta)", "--text-faint", "--surface-canvas"],
  ["danger text", "--danger", "--surface-canvas"],
  ["danger text on raised", "--danger", "--surface-raised"],
  ["on-accent-strong / aurora", "--on-accent-strong", "--accent-aurora"],
  ["on-accent-strong / aurora-soft", "--on-accent-strong", "--accent-aurora-soft"],
  ["on-accent-strong / sage-soft", "--on-accent-strong", "--accent-sage-soft"],
  ["on-plum-strong / plum", "--on-plum-strong", "--accent-plum"]
];

describe("CP-12 contrast token audit (WCAG 2.2 AA, 4.5:1)", () => {
  it("the default warm-night theme keeps every text-bearing pairing at 4.5:1+", () => {
    const failures: string[] = [];
    for (const [label, fgToken, bgToken] of PAIRS) {
      const fg = night[fgToken];
      const bg = night[bgToken];
      const ratio = contrast(fg, bg);
      if (ratio < 4.5) failures.push(`${label}: ${fg} on ${bg} = ${ratio.toFixed(2)}:1`);
    }
    expect(failures, failures.join("; ")).toEqual([]);
  });

  it("the effective day theme (merged blocks) keeps every text-bearing pairing at 4.5:1+", () => {
    const failures: string[] = [];
    for (const [label, fgToken, bgToken] of PAIRS) {
      const fg = day[fgToken];
      const bg = day[bgToken];
      const ratio = contrast(fg, bg);
      if (ratio < 4.5) failures.push(`${label}: ${fg} on ${bg} = ${ratio.toFixed(2)}:1`);
    }
    expect(failures, failures.join("; ")).toEqual([]);
  });

  it("audits real hex tokens only — a var()/gradient token in a text pair must fail loudly", () => {
    expect(() => contrast("var(--text-primary)", "#000000")).toThrow(/non-hex/);
  });
});
