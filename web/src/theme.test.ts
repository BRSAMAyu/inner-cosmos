import { afterEach, describe, expect, it } from "vitest";
import {
  ADAPTIVE_THEME_EVENT,
  applyColorScheme,
  applyAdaptiveTheme,
  applyTimeOfDayTheme,
  colorSchemeForTimeOfDay,
  currentTimeOfDay,
  getColorScheme,
  getLockedTimeOfDay,
  getPreviewHour,
  setColorScheme,
  setPreviewHour,
  setThemeLock,
  themeColorFor,
  timePresentationFor,
  timeOfDayForHour,
  type TimeOfDay,
} from "./theme";

describe("timeOfDayForHour", () => {
  const cases: Array<[number, TimeOfDay]> = [
    [0, "deep-night"],
    [4, "deep-night"],
    [5, "dawn"],
    [6, "dawn"],
    [7, "morning"],
    [10, "morning"],
    [11, "noon"],
    [14, "noon"],
    [15, "evening"],
    [16, "evening"],
    [17, "dusk"],
    [18, "dusk"],
    [19, "night"],
    [22, "night"],
    [23, "deep-night"],
  ];
  it.each(cases)("hour %i -> %s", (hour, expected) => {
    expect(timeOfDayForHour(hour)).toBe(expected);
  });

  it("wraps out-of-range hours", () => {
    expect(timeOfDayForHour(24)).toBe("deep-night");
    expect(timeOfDayForHour(-1)).toBe("deep-night"); // -1 -> 23:00, 属 deep-night
  });

  it("currentTimeOfDay uses the given date's local hour", () => {
    const noon = new Date(2026, 0, 1, 12, 0, 0);
    expect(currentTimeOfDay(noon)).toBe("noon");
  });
});

describe("theme lock", () => {
  afterEach(() => {
    setThemeLock(null);
    setPreviewHour(null);
  });

  it("round-trips a valid lock", () => {
    setThemeLock("dusk");
    expect(getLockedTimeOfDay()).toBe("dusk");
  });

  it("ignores an invalid stored value", () => {
    localStorage.setItem("ic-theme-lock", "bogus");
    expect(getLockedTimeOfDay()).toBeNull();
  });

  it("clears the lock with null", () => {
    setThemeLock("night");
    setThemeLock(null);
    expect(getLockedTimeOfDay()).toBeNull();
  });
});

describe("classroom time preview", () => {
  afterEach(() => setPreviewHour(null));

  it("stores an exact preview hour and resolves its period", () => {
    setPreviewHour(16);
    expect(getPreviewHour()).toBe(16);
    expect(getLockedTimeOfDay()).toBe("evening");
  });

  it("normalizes hours and restores live time with null", () => {
    setPreviewHour(25);
    expect(getPreviewHour()).toBe(1);
    setPreviewHour(null);
    expect(getPreviewHour()).toBeNull();
    expect(getLockedTimeOfDay()).toBeNull();
  });
});

describe("applyTimeOfDayTheme", () => {
  afterEach(() => setThemeLock(null));

  it("writes data-time from local time when unlocked", () => {
    const root = document.createElement("html");
    applyTimeOfDayTheme(root, new Date(2026, 0, 1, 13, 0, 0));
    expect(root.dataset.time).toBe("noon");
  });

  it("prefers a user lock over local time", () => {
    setThemeLock("dawn");
    const root = document.createElement("html");
    applyTimeOfDayTheme(root, new Date(2026, 0, 1, 13, 0, 0));
    expect(root.dataset.time).toBe("dawn");
  });

  it("exposes non-color time semantics as data attributes and CSS variables", () => {
    const root = document.createElement("html");
    applyTimeOfDayTheme(root, new Date(2026, 0, 1, 18, 0, 0));
    expect(root.dataset.timeMotion).toBe("slow");
    expect(root.dataset.lightDirection).toBe("west");
    expect(root.dataset.ambientDensity).toBe("rich");
    expect(root.dataset.timeCopy).toBe("dusk");
    expect(root.style.getPropertyValue("--time-light-x")).toBe("88%");
    expect(root.style.getPropertyValue("--time-motion-rhythm")).toBe("0.78");
  });
});

describe("color scheme (明暗轴)", () => {
  afterEach(() => {
    setColorScheme(null);
    setPreviewHour(null);
  });

  it("默认(跟随)返回 null", () => {
    expect(getColorScheme()).toBeNull();
  });

  it("round-trips day/night", () => {
    setColorScheme("day");
    expect(getColorScheme()).toBe("day");
    setColorScheme("night");
    expect(getColorScheme()).toBe("night");
  });

  it("忽略非法存储值", () => {
    localStorage.setItem("ic-color-scheme", "rainbow");
    expect(getColorScheme()).toBeNull();
  });

  it("maps the five daylight periods to day and the two late periods to night", () => {
    expect(colorSchemeForTimeOfDay("dawn")).toBe("day");
    expect(colorSchemeForTimeOfDay("dusk")).toBe("day");
    expect(colorSchemeForTimeOfDay("night")).toBe("night");
    expect(colorSchemeForTimeOfDay("deep-night")).toBe("night");
  });

  it("follow time is warm-light by day and soft-night after dark", () => {
    const root = document.createElement("html");
    applyColorScheme(root, new Date(2026, 0, 1, 13, 0, 0));
    expect(root.dataset.theme).toBe("day");
    applyColorScheme(root, new Date(2026, 0, 1, 21, 0, 0));
    expect(root.dataset.theme).toBe("night");
  });

  it("manual day/night overrides follow-time", () => {
    const root = document.createElement("html");
    setColorScheme("day");
    applyColorScheme(root);
    expect(root.dataset.theme).toBe("day");
    setColorScheme("night");
    applyColorScheme(root);
    expect(root.dataset.theme).toBe("night");
  });

  it("preview hour atomically updates period and follow-time light mode", () => {
    const root = document.createElement("html");
    setPreviewHour(8);
    applyAdaptiveTheme(root);
    expect(root.dataset.time).toBe("morning");
    expect(root.dataset.theme).toBe("day");
    setPreviewHour(1);
    applyAdaptiveTheme(root);
    expect(root.dataset.time).toBe("deep-night");
    expect(root.dataset.theme).toBe("night");
  });

  it("updates browser chrome color and emits a refresh event", () => {
    const root = document.documentElement;
    const meta = document.createElement("meta");
    meta.name = "theme-color";
    document.head.appendChild(meta);
    const events: Event[] = [];
    root.addEventListener(ADAPTIVE_THEME_EVENT, event => events.push(event), { once: true });

    setPreviewHour(8);
    applyAdaptiveTheme(root);

    expect(meta.content).toBe(themeColorFor("day", "morning"));
    expect(events).toHaveLength(1);
    expect(root.style.colorScheme).toBe("light");
    meta.remove();
  });
});

describe("semantic time presentation", () => {
  it("changes rhythm, light direction and density beyond palette", () => {
    const noon = timePresentationFor("noon");
    const night = timePresentationFor("night");
    expect(noon.lightDirection).toBe("overhead");
    expect(night.lightDirection).toBe("diffuse");
    expect(noon.ambientDensity).toBeLessThan(night.ambientDensity);
    expect(noon.motionRhythm).not.toBe(night.motionRhythm);
  });
});
