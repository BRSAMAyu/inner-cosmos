import { describe, expect, it } from "vitest";
import { formatSlowLetterInstant, parseSlowLetterInstant, secondsUntilSlowLetterArrival, toLocalDateTimeInputValue } from "./slowLetterTime";

describe("slow-letter timestamps", () => {
  it("treats historical offset-less values as UTC and displays them in the device zone", () => {
    expect(parseSlowLetterInstant("2026-07-26T12:00:00")?.toISOString()).toBe("2026-07-26T12:00:00.000Z");
    expect(formatSlowLetterInstant("2026-07-26T12:00:00", {
      locale: "zh-CN", timeZone: "Asia/Shanghai"
    })).toContain("20:00");
  });

  it("preserves timestamps that already carry an offset and counts down from the absolute instant", () => {
    expect(parseSlowLetterInstant("2026-07-26T20:00:00+08:00")?.toISOString()).toBe("2026-07-26T12:00:00.000Z");
    expect(secondsUntilSlowLetterArrival("2026-07-26T12:00:30Z", Date.parse("2026-07-26T12:00:00Z"))).toBe(30);
  });

  it("formats datetime-local minimums as a local wall clock instead of a UTC ISO substring", () => {
    expect(toLocalDateTimeInputValue(new Date(2026, 6, 26, 20, 5))).toBe("2026-07-26T20:05");
  });
});
