import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  composite, contrast, firstHex, parseBlock, resolveColorMix, type Tokens
} from "./contrast";

/**
 * CP-12 rendered-surface contrast spot-check: the token audit checks tokens against flat
 * surfaces, but real components stack semi-transparent backgrounds (cards over panels over
 * the page). This audit composites the ACTUAL background chains declared in styles.css and
 * asserts the text tokens that sit on them still reach 4.5:1 in both themes.
 */

const css = readFileSync(resolve(__dirname, "styles.css"), "utf-8");
const night = parseBlock(css, /:root\s*\{([^}]*)\}/g);
const dayBlocks = parseBlock(css, /:root\[data-theme="day"\]\s*\{([^}]*)\}/g);
const day: Tokens = { ...night, ...dayBlocks };

function ruleOf(selector: string, theme: "night" | "day" = "night"): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  // Theme-aware with the CSS cascade's own fallback: a day-scoped override wins when
  // present; otherwise the base rule is exactly what the day theme renders too.
  const themeMatch = theme === "day"
    ? css.match(new RegExp(`:root\\[data-theme="day"\\]\\s+${escaped}\\s*\\{([^}]*)\\}`))
    : null;
  const match = themeMatch
    ?? css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`));
  if (!match) throw new Error(`rule not found: ${selector}`);
  return match[1];
}

function backgroundOf(selector: string, theme: "night" | "day" = "night"): string {
  const body = ruleOf(selector, theme);
  const background = body.match(/background(?:-color)?\s*:\s*([^;}]+)/)?.[1];
  if (!background) throw new Error(`no background in ${selector}`);
  return background;
}

/** Composites a declared background (flat hex, or the first gradient stop) over a base. */
function compositeBackground(selector: string, base: string, tokens: Tokens,
  theme: "night" | "day" = "night"): string {
  const background = backgroundOf(selector, theme);
  const firstStop = background.match(/#[0-9a-f]{3,8}\b/i)?.[0]
    // color-mix(...) contains var() whose ")" would truncate a [^)]* match — capture to the
    // declaration end instead.
    ?? background.match(/color-mix\([^{;}]*/)?.[0];
  if (!firstStop) throw new Error(`no parseable stop in ${selector}: ${background}`);
  const layer = firstStop.startsWith("color-mix")
    ? resolveColorMix(firstStop, tokens)
    : firstStop;
  return composite(layer, base);
}

type Stack = {
  label: string;
  selector: string;
  textTokens: Array<[string, string]>;
};

/** The audited surfaces and the text that actually renders on them. */
const STACKS: Array<{ base: string; layers: string[]; surfaces: Stack[] }> = [
  {
    base: "--surface-canvas",
    layers: [".portrait-claims-panel"],
    surfaces: [
      { label: "claims panel body text", selector: ".portrait-claims-panel", textTokens: [["text-primary", "--text-primary"]] },
      { label: "claims row card", selector: ".portrait-claim-row", textTokens: [["row text", "--text-primary"]] }
    ]
  },
  {
    base: "--surface-canvas",
    layers: [".opening-continuity"],
    surfaces: [
      { label: "opening continuity card", selector: ".opening-continuity", textTokens: [["card text", "--text-primary"]] }
    ]
  },
  {
    base: "--surface-canvas",
    layers: [".portrait-calibrate textarea"],
    surfaces: [
      { label: "calibration textarea", selector: ".portrait-calibrate textarea", textTokens: [["input text", "--text-primary"]] }
    ]
  }
];

describe("CP-12 rendered-surface contrast spot-check (composited stacks, 4.5:1)", () => {
  for (const [themeName, tokens] of [["night", night], ["day", day]] as const) {
    it(`${themeName}: every audited composited surface keeps its text at 4.5:1+`, () => {
      const failures: string[] = [];
      for (const stack of STACKS) {
        const base = tokens[stack.base];
        let surface = base;
        for (const layer of stack.layers) {
          surface = compositeBackground(layer, surface, tokens, themeName);
        }
        for (const entry of stack.surfaces) {
          // The surface the text sits on includes the entry's own selector when it differs.
          const textSurface = entry.selector === stack.layers[stack.layers.length - 1]
            ? surface
            : compositeBackground(entry.selector, surface, tokens, themeName);
          for (const [label, textToken] of entry.textTokens) {
            const ratio = contrast(tokens[textToken], textSurface);
            if (ratio < 4.5) {
              failures.push(`${themeName} ${label}: ${tokens[textToken]} on ${textSurface} = ${ratio.toFixed(2)}:1`);
            }
          }
        }
      }
      expect(failures, failures.join("; ")).toEqual([]);
    });
  }

  it("the audit understands every audited rule — an unparseable background fails loudly", () => {
    for (const stack of STACKS) {
      for (const layer of stack.layers) expect(() => backgroundOf(layer)).not.toThrow();
    }
    expect(firstHex("#ffffff06 over nothing")).toBe("#ffffff06");
    expect(() => resolveColorMix("color-mix(in srgb, nonsense)", night)).toThrow();
  });
});
