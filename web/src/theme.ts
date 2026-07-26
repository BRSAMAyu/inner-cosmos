// 七时段时间感知主题（UIUX §2）。
// 默认跟随设备本地时间；课堂演示可以临时拨动小时，随时恢复实时。
export type TimeOfDay =
  | "dawn"
  | "morning"
  | "noon"
  | "evening"
  | "dusk"
  | "night"
  | "deep-night";

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

const LOCK_KEY = "ic-theme-lock";
const PREVIEW_HOUR_KEY = "ic-theme-preview-hour";
const SCHEME_KEY = "ic-color-scheme";

// 明暗轴与七时段正交。null 表示真正跟随时间；用户仍可强制白昼或夜色。
export type ColorScheme = "day" | "night";

/** 纯函数：给定本地小时(0-23)返回时段。 */
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

/** 晨曦到黄昏使用浅暖纸色；夜晚才进入柔和的暖夜色。 */
export function colorSchemeForTimeOfDay(tod: TimeOfDay): ColorScheme {
  return tod === "night" || tod === "deep-night" ? "night" : "day";
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

/** 设置课堂预览小时；null 恢复设备实时。 */
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
    /* localStorage 不可用时静默降级 */
  }
}

/** 兼容原有时段锁定 API；新的演示控件优先保存精确小时。 */
export function getLockedTimeOfDay(): TimeOfDay | null {
  const previewHour = getPreviewHour();
  if (previewHour !== null) return timeOfDayForHour(previewHour);
  try {
    const value = localStorage.getItem(LOCK_KEY);
    return value && (TIME_OF_DAY_ORDER as string[]).includes(value)
      ? (value as TimeOfDay)
      : null;
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
    /* localStorage 不可用时静默降级 */
  }
}

function resolvedTimeOfDay(now: Date): TimeOfDay {
  return getLockedTimeOfDay() ?? currentTimeOfDay(now);
}

export function applyTimeOfDayTheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): TimeOfDay {
  const tod = resolvedTimeOfDay(now);
  root.dataset.time = tod;
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
    /* localStorage 不可用时静默降级 */
  }
}

/** 应用明暗：未手动锁定时，根据真实/预览时段自动选择。 */
export function applyColorScheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): ColorScheme | null {
  const chosen = getColorScheme();
  const effective = chosen ?? colorSchemeForTimeOfDay(resolvedTimeOfDay(now));
  root.dataset.theme = effective;
  return chosen;
}

/** 原子更新时段与明暗，避免切换时出现一次暗色闪烁。 */
export function applyAdaptiveTheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): TimeOfDay {
  const tod = applyTimeOfDayTheme(root, now);
  applyColorScheme(root, now);
  return tod;
}

/** 启动：立即应用，并每分钟刷新实时主题。 */
export function startTimeOfDayTheme(
  root: HTMLElement = document.documentElement
): () => void {
  applyAdaptiveTheme(root);
  const id = window.setInterval(() => applyAdaptiveTheme(root), 60_000);
  return () => window.clearInterval(id);
}
