import { afterEach, describe, expect, it } from "vitest";
import {
  applyTimeOfDayTheme,
  currentTimeOfDay,
  getLockedTimeOfDay,
  setThemeLock,
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
  afterEach(() => setThemeLock(null));

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
});
