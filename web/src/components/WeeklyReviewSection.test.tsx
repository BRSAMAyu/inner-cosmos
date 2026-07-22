import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WeeklyReviewSection } from "./WeeklyReviewSection";
import type { WeeklyReviewV2 } from "../api";

afterEach(() => cleanup());

const review = (overrides: Partial<WeeklyReviewV2> = {}): WeeklyReviewV2 => ({
  id: 1, title: "第30周回顾", dateRange: "2026-07-13 ~ 2026-07-19", weekStartDate: "2026-07-13",
  weekEndDate: "2026-07-19", topThemes: "工作, 家庭", memoryCount: 5, dominantEmotion: "平静",
  emotionSpectrum: "[]", intensityAverage: 3.2, todoRatio: "2/5", recommendation: "多休息一下",
  auroraObservation: "这周你分享了很多", dailySnapshots: [
    { date: "2026-07-13", dayLabel: "周一", emotionWeather: "SUNNY", theme: "工作", memorySummary: "完成了项目", cognitiveSummary: "还不错", taskRatio: "1/2", auroraSummary: "继续保持" }
  ], legacy: false, ...overrides
});

describe("WeeklyReviewSection", () => {
  it("shows an empty state and lets the user generate a review", () => {
    const onGenerate = vi.fn();
    render(<WeeklyReviewSection review={null} busy={false} onGenerate={onGenerate} />);
    expect(screen.getByText(/还没有生成过周报/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /重新生成这一周/ }));
    expect(onGenerate).toHaveBeenCalledOnce();
  });

  it("renders a generated review with themes, stats and the daily trajectory", () => {
    render(<WeeklyReviewSection review={review()} busy={false} onGenerate={() => undefined} />);
    expect(screen.getByText("第30周回顾")).toBeVisible();
    expect(screen.getAllByText("工作").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("家庭")).toBeVisible();
    expect(screen.getByText("5")).toBeVisible();
    expect(screen.getByText("平静")).toBeVisible();
    expect(screen.getByText("完成了项目")).toBeVisible();
    expect(screen.getByText(/这周你分享了很多/)).toBeVisible();
    expect(screen.getByText("多休息一下")).toBeVisible();
  });

  it("disables the regenerate button while busy", () => {
    render(<WeeklyReviewSection review={review()} busy={true} onGenerate={() => undefined} />);
    expect(screen.getByRole("button", { name: /重新生成这一周/ })).toBeDisabled();
  });

  it("renders in English when locale is en-SG", () => {
    render(<WeeklyReviewSection locale="en-SG" review={null} busy={false} onGenerate={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Growth Weekly Review" })).toBeVisible();
  });
});
