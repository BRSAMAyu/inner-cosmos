import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { WeeklyReviewV2 } from "../api";
import { useWeeklyReview } from "./useWeeklyReview";

vi.mock("../api", () => ({
  api: {
    weeklyReviewV2Latest: vi.fn(),
    generateWeeklyReviewV2: vi.fn()
  }
}));

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useWeeklyReview({ setStatus }));
  return { result, setStatus };
}

const review = (overrides: Partial<WeeklyReviewV2> = {}): WeeklyReviewV2 => ({
  id: 1, title: "第30周回顾", dateRange: "2026-07-13 ~ 2026-07-19", weekStartDate: "2026-07-13",
  weekEndDate: "2026-07-19", topThemes: "工作, 家庭", memoryCount: 5, dominantEmotion: "平静",
  emotionSpectrum: "[]", intensityAverage: 3.2, todoRatio: "2/5", recommendation: "多休息",
  auroraObservation: "这周你分享了很多", dailySnapshots: [], legacy: false, ...overrides
});

describe("useWeeklyReview", () => {
  it("loads the latest V2 review, tolerating null when none exists", async () => {
    vi.mocked(api.weeklyReviewV2Latest).mockResolvedValue(null);
    const { result } = setup();
    await act(async () => { await result.current.loadWeeklyReview(); });
    expect(result.current.weeklyReview).toBeNull();
  });

  it("generates a fresh review and surfaces a status message", async () => {
    vi.mocked(api.generateWeeklyReviewV2).mockResolvedValue(review());
    const { result, setStatus } = setup();
    await act(async () => { await result.current.generateWeeklyReview(); });
    expect(result.current.weeklyReview).toEqual(review());
    expect(result.current.weeklyReviewBusy).toBe(false);
    expect(setStatus).toHaveBeenCalledWith("这周的成长周报已经生成。");
  });

  it("surfaces an error status when generation fails", async () => {
    vi.mocked(api.generateWeeklyReviewV2).mockRejectedValue(new Error("网络错误"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.generateWeeklyReview(); });
    expect(setStatus).toHaveBeenCalledWith("网络错误");
  });
});
