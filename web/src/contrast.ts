/**
 * CP-12 contrast helpers shared by the token audit and the component-stack spot-check.
 * Pure functions over styles.css text — no DOM, no environment.
 */

export type Tokens = Record<string, string>;

export function parseBlock(css: string, selector: RegExp): Tokens {
  const tokens: Tokens = {};
  for (const match of css.matchAll(selector)) {
    for (const decl of match[1].matchAll(/--([a-z0-9-]+)\s*:\s*([^;}]+)/gi)) {
      tokens[`--${decl[1]}`] = decl[2].trim();
    }
  }
  return tokens;
}

export function hexToRgb(hex: string): [number, number, number] {
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
export function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map(channel => {
    const srgb = channel / 255;
    return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(foreground: string, background: string): number {
  const a = luminance(foreground);
  const b = luminance(background);
  const [light, dark] = a >= b ? [a, b] : [b, a];
  return (light + 0.05) / (dark + 0.05);
}

/** Alpha-blends an 8-digit hex (rrggbbaa) over an opaque background. */
export function composite(overlayHexWithAlpha: string, backgroundHex: string): string {
  const [r, g, b] = hexToRgb(overlayHexWithAlpha);
  const raw = overlayHexWithAlpha.replace("#", "").trim();
  const alpha = raw.length === 8 ? parseInt(raw.slice(6, 8), 16) / 255
    : raw.length === 4 ? parseInt(raw.slice(3, 4), 16) / 15 : 1;
  const [br, bg, bb] = hexToRgb(backgroundHex);
  const mix = (front: number, back: number) =>
    Math.round(front * alpha + back * (1 - alpha));
  const toHex = (value: number) => value.toString(16).padStart(2, "0");
  return `#${toHex(mix(r, br))}${toHex(mix(g, bg))}${toHex(mix(b, bb))}`;
}

/**
 * Resolves the simple `color-mix(in srgb, <hex|var> X%, <hex|var> Y%)` pattern the stylesheet
 * uses for tinted surfaces. Only the two-stop form is supported; anything else throws so the
 * audit fails loudly instead of silently skipping a surface it no longer understands.
 */
export function resolveColorMix(value: string, tokens: Tokens): string {
  const match = value.match(/color-mix\(\s*in\s+srgb\s*,\s*(.+?)\s+(\d+(?:\.\d+)?)%\s*,\s*(.+?)\s+(\d+(?:\.\d+)?)%\s*\)/);
  if (!match) throw new Error(`unsupported color-mix form: ${value}`);
  const resolve = (raw: string): [number, number, number] => {
    const hex = raw.startsWith("var(")
      ? tokens[raw.slice(4, -1).trim()]
      : raw;
    return hexToRgb(hex);
  };
  const a = resolve(match[1]);
  const b = resolve(match[3]);
  const weightA = Number(match[2]) / 100;
  const mix = (x: number, y: number) => Math.round(x * weightA + y * (1 - weightA));
  const toHex = (v: number) => v.toString(16).padStart(2, "0");
  return `#${toHex(mix(a[0], b[0]))}${toHex(mix(a[1], b[1]))}${toHex(mix(a[2], b[2]))}`;
}

/** First hex (8- or 6-digit) found in a declaration value. */
export function firstHex(value: string): string | null {
  return value.match(/#[0-9a-f]{3,8}\b/i)?.[0] ?? null;
}
