// Seven local-time periods drive both palette and presentation rhythm.
// Classroom previews may pin an exact hour; null always restores live local time.
export type TimeOfDay =
  | "dawn"
  | "morning"
  | "noon"
  | "evening"
  | "dusk"
  | "night"
  | "deep-night";

export type ColorScheme = "day" | "night";

export type TimePresentation = {
  motion: "still" | "slow" | "flowing" | "awake";
  lightDirection: "east" | "overhead" | "west" | "diffuse";
  density: "sparse" | "balanced" | "rich";
  copyKey: TimeOfDay;
  motionRhythm: number;
  ambientDensity: number;
  lightX: string;
  lightY: string;
  driftX: number;
};

export const TIME_OF_DAY_ORDER: TimeOfDay[] = [
  "dawn",
  "morning",
  "noon",
  "evening",
  "dusk",
  "night",
  "deep-night",
];

export const TIME_OF_DAY_HOURS: Record<TimeOfDay, number> = {
  dawn: 6,
  morning: 8,
  noon: 12,
  evening: 16,
  dusk: 18,
  night: 21,
  "deep-night": 1,
};

export const TIME_PRESENTATION: Record<TimeOfDay, TimePresentation> = {
  dawn: {
    motion: "slow", lightDirection: "east", density: "sparse", copyKey: "dawn",
    motionRhythm: 0.72, ambientDensity: 0.72, lightX: "18%", lightY: "12%", driftX: 0.28,
  },
  morning: {
    motion: "awake", lightDirection: "east", density: "balanced", copyKey: "morning",
    motionRhythm: 1.08, ambientDensity: 0.9, lightX: "30%", lightY: "8%", driftX: 0.42,
  },
  noon: {
    motion: "still", lightDirection: "overhead", density: "sparse", copyKey: "noon",
    motionRhythm: 0.82, ambientDensity: 0.62, lightX: "52%", lightY: "0%", driftX: 0.06,
  },
  evening: {
    motion: "flowing", lightDirection: "west", density: "balanced", copyKey: "evening",
    motionRhythm: 0.94, ambientDensity: 0.86, lightX: "78%", lightY: "12%", driftX: -0.3,
  },
  dusk: {
    motion: "slow", lightDirection: "west", density: "rich", copyKey: "dusk",
    motionRhythm: 0.78, ambientDensity: 1.08, lightX: "88%", lightY: "22%", driftX: -0.44,
  },
  night: {
    motion: "flowing", lightDirection: "diffuse", density: "rich", copyKey: "night",
    motionRhythm: 0.66, ambientDensity: 1.2, lightX: "68%", lightY: "10%", driftX: -0.16,
  },
  "deep-night": {
    motion: "still", lightDirection: "diffuse", density: "sparse", copyKey: "deep-night",
    motionRhythm: 0.42, ambientDensity: 0.58, lightX: "50%", lightY: "18%", driftX: 0.04,
  },
};

export const ADAPTIVE_THEME_EVENT = "innercosmos:adaptive-theme-change";

const LOCK_KEY = "ic-theme-lock";
const PREVIEW_HOUR_KEY = "ic-theme-preview-hour";
const SCHEME_KEY = "ic-color-scheme";

const THEME_COLORS: Record<ColorScheme, Partial<Record<TimeOfDay, string>> & { default: string }> = {
  day: {
    default: "#F7F2EC",
    dawn: "#F7EEE6",
    morning: "#F3F0E8",
    noon: "#F6F4EE",
    evening: "#F4EEE4",
    dusk: "#F2E7DF",
  },
  night: {
    default: "#332B28",
    night: "#3A302B",
    "deep-night": "#332E32",
  },
};

export function isTimeOfDay(value: string | null | undefined): value is TimeOfDay {
  return Boolean(value && (TIME_OF_DAY_ORDER as string[]).includes(value));
}

/** Pure local-hour resolver. Values outside 0-23 wrap predictably. */
export function timeOfDayForHour(hour: number): TimeOfDay {
  const h = ((Math.floor(hour) % 24) + 24) % 24;
  if (h >= 5 && h < 7) return "dawn";
  if (h >= 7 && h < 11) return "morning";
  if (h >= 11 && h < 15) return "noon";
  if (h >= 15 && h < 17) return "evening";
  if (h >= 17 && h < 19) return "dusk";
  if (h >= 19 && h < 23) return "night";
  return "deep-night";
}

export function currentTimeOfDay(now: Date = new Date()): TimeOfDay {
  return timeOfDayForHour(now.getHours());
}

export function colorSchemeForTimeOfDay(tod: TimeOfDay): ColorScheme {
  return tod === "night" || tod === "deep-night" ? "night" : "day";
}

export function timePresentationFor(tod: TimeOfDay): TimePresentation {
  return TIME_PRESENTATION[tod];
}

export function themeColorFor(scheme: ColorScheme, tod: TimeOfDay): string {
  return THEME_COLORS[scheme][tod] ?? THEME_COLORS[scheme].default;
}

export function getPreviewHour(): number | null {
  try {
    const raw = localStorage.getItem(PREVIEW_HOUR_KEY);
    if (raw === null) return null;
    const hour = Number(raw);
    return Number.isInteger(hour) && hour >= 0 && hour <= 23 ? hour : null;
  } catch {
    return null;
  }
}

export function setPreviewHour(hour: number | null): void {
  try {
    if (hour === null) {
      localStorage.removeItem(PREVIEW_HOUR_KEY);
      localStorage.removeItem(LOCK_KEY);
      return;
    }
    const normalized = ((Math.round(hour) % 24) + 24) % 24;
    localStorage.setItem(PREVIEW_HOUR_KEY, String(normalized));
    localStorage.removeItem(LOCK_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted shells.
  }
}

/** Compatibility API for older period-level classroom controls. */
export function getLockedTimeOfDay(): TimeOfDay | null {
  const previewHour = getPreviewHour();
  if (previewHour !== null) return timeOfDayForHour(previewHour);
  try {
    const value = localStorage.getItem(LOCK_KEY);
    return isTimeOfDay(value) ? value : null;
  } catch {
    return null;
  }
}

export function setThemeLock(tod: TimeOfDay | null): void {
  try {
    localStorage.removeItem(PREVIEW_HOUR_KEY);
    if (tod) localStorage.setItem(LOCK_KEY, tod);
    else localStorage.removeItem(LOCK_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted shells.
  }
}

function resolvedTimeOfDay(now: Date): TimeOfDay {
  return getLockedTimeOfDay() ?? currentTimeOfDay(now);
}

function resolvedColorScheme(tod: TimeOfDay): ColorScheme {
  return getColorScheme() ?? colorSchemeForTimeOfDay(tod);
}

function applyPresentation(root: HTMLElement, tod: TimeOfDay): void {
  const presentation = timePresentationFor(tod);
  root.dataset.timeMotion = presentation.motion;
  root.dataset.lightDirection = presentation.lightDirection;
  root.dataset.ambientDensity = presentation.density;
  root.dataset.timeCopy = presentation.copyKey;
  root.style.setProperty("--time-motion-rhythm", String(presentation.motionRhythm));
  root.style.setProperty("--time-ambient-density", String(presentation.ambientDensity));
  root.style.setProperty("--time-light-x", presentation.lightX);
  root.style.setProperty("--time-light-y", presentation.lightY);
  root.style.setProperty("--time-drift-x", String(presentation.driftX));
}

function updateThemeColor(root: HTMLElement, scheme: ColorScheme, tod: TimeOfDay): void {
  const doc = root.ownerDocument;
  const meta = doc?.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  meta?.setAttribute("content", themeColorFor(scheme, tod));
}

function dispatchAdaptiveThemeChange(root: HTMLElement, tod: TimeOfDay, scheme: ColorScheme): void {
  const EventConstructor = root.ownerDocument?.defaultView?.CustomEvent ?? CustomEvent;
  root.dispatchEvent(new EventConstructor(ADAPTIVE_THEME_EVENT, {
    detail: { time: tod, theme: scheme, presentation: timePresentationFor(tod) },
  }));
}

export function applyTimeOfDayTheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): TimeOfDay {
  const tod = resolvedTimeOfDay(now);
  root.dataset.time = tod;
  applyPresentation(root, tod);
  return tod;
}

export function getColorScheme(): ColorScheme | null {
  try {
    const value = localStorage.getItem(SCHEME_KEY);
    return value === "day" || value === "night" ? value : null;
  } catch {
    return null;
  }
}

export function setColorScheme(scheme: ColorScheme | null): void {
  try {
    if (scheme) localStorage.setItem(SCHEME_KEY, scheme);
    else localStorage.removeItem(SCHEME_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted shells.
  }
}

/**
 * Applies period, scheme and semantic presentation values in one update.
 * Consumers such as the stardust canvas listen for ADAPTIVE_THEME_EVENT.
 */
export function applyAdaptiveTheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): TimeOfDay {
  const tod = resolvedTimeOfDay(now);
  const scheme = resolvedColorScheme(tod);
  root.dataset.time = tod;
  root.dataset.theme = scheme;
  root.style.colorScheme = scheme === "day" ? "light" : "dark";
  applyPresentation(root, tod);
  updateThemeColor(root, scheme, tod);
  dispatchAdaptiveThemeChange(root, tod, scheme);
  return tod;
}

/** Retained API: applies the effective scheme while preserving the return contract. */
export function applyColorScheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): ColorScheme | null {
  const tod = resolvedTimeOfDay(now);
  const chosen = getColorScheme();
  const effective = chosen ?? colorSchemeForTimeOfDay(tod);
  root.dataset.theme = effective;
  root.style.colorScheme = effective === "day" ? "light" : "dark";
  updateThemeColor(root, effective, tod);
  return chosen;
}

export function startTimeOfDayTheme(
  root: HTMLElement = document.documentElement
): () => void {
  applyAdaptiveTheme(root);
  const view = root.ownerDocument?.defaultView ?? window;
  const id = view.setInterval(() => applyAdaptiveTheme(root), 60_000);
  return () => view.clearInterval(id);
}
