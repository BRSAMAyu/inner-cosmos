// 七时段时间感知主题（§1.1 时间感知系统 / UIUXdesign §3）。
// 本版：按本地钟点把 <html data-time> 设为七时段之一，CSS 据此平滑切换暖色色温。
// 日落算法（NOAA）为后置增强（UIUXdesign §3.2 允许）；用户可锁定某一时段。
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

const LOCK_KEY = "ic-theme-lock";
const SCHEME_KEY = "ic-color-scheme";

// 明暗轴：与七时段(data-time)正交。默认 null=跟随（当前=暖夜暗色系）；
// "day"=白昼莫兰迪浅色；"night"=强制暖夜暗色。仅 "day" 时写 <html data-theme="day">，
// 让 styles.css 的浅色 token 覆盖生效；默认不写属性，暗色行为 100% 不变。
export type ColorScheme = "day" | "night";

/** 纯函数：给定本地小时(0-23)返回时段。可测试。 */
export function timeOfDayForHour(hour: number): TimeOfDay {
  const h = ((Math.floor(hour) % 24) + 24) % 24;
  if (h >= 5 && h < 7) return "dawn";
  if (h >= 7 && h < 11) return "morning";
  if (h >= 11 && h < 15) return "noon";
  if (h >= 15 && h < 17) return "evening";
  if (h >= 17 && h < 19) return "dusk";
  if (h >= 19 && h < 23) return "night";
  return "deep-night"; // 23:00–05:00
}

export function currentTimeOfDay(now: Date = new Date()): TimeOfDay {
  return timeOfDayForHour(now.getHours());
}

/** 读取用户锁定的时段（无锁定或非法值返回 null）。 */
export function getLockedTimeOfDay(): TimeOfDay | null {
  try {
    const v = localStorage.getItem(LOCK_KEY);
    return v && (TIME_OF_DAY_ORDER as string[]).includes(v)
      ? (v as TimeOfDay)
      : null;
  } catch {
    return null;
  }
}

/** 锁定到某时段；传 null 解除锁定，恢复跟随本地时间。 */
export function setThemeLock(tod: TimeOfDay | null): void {
  try {
    if (tod) localStorage.setItem(LOCK_KEY, tod);
    else localStorage.removeItem(LOCK_KEY);
  } catch {
    /* localStorage 不可用时静默降级 */
  }
}

/** 把当前应生效的时段写到 <html data-time>，返回该时段。 */
export function applyTimeOfDayTheme(
  root: HTMLElement = document.documentElement,
  now: Date = new Date()
): TimeOfDay {
  const tod = getLockedTimeOfDay() ?? currentTimeOfDay(now);
  root.dataset.time = tod;
  return tod;
}

/** 读取用户选择的明暗方案（无选择或非法值返回 null=跟随）。 */
export function getColorScheme(): ColorScheme | null {
  try {
    const v = localStorage.getItem(SCHEME_KEY);
    return v === "day" || v === "night" ? v : null;
  } catch {
    return null;
  }
}

/** 设置明暗方案；传 null 恢复跟随（暖夜暗色系）。 */
export function setColorScheme(scheme: ColorScheme | null): void {
  try {
    if (scheme) localStorage.setItem(SCHEME_KEY, scheme);
    else localStorage.removeItem(SCHEME_KEY);
  } catch {
    /* localStorage 不可用时静默降级 */
  }
}

/** 把明暗方案写到 <html data-theme>：仅 "day" 写 data-theme="day"，否则移除(暗色默认)。 */
export function applyColorScheme(root: HTMLElement = document.documentElement): ColorScheme | null {
  const scheme = getColorScheme();
  if (scheme === "day") root.dataset.theme = "day";
  else delete root.dataset.theme;
  return scheme;
}

/** 启动：立即应用时段主题 + 明暗方案，并每分钟刷新时段；返回停止函数。 */
export function startTimeOfDayTheme(
  root: HTMLElement = document.documentElement
): () => void {
  applyTimeOfDayTheme(root);
  applyColorScheme(root);
  const id = window.setInterval(() => applyTimeOfDayTheme(root), 60_000);
  return () => window.clearInterval(id);
}
