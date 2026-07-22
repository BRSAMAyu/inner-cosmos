import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { TimelineSection } from "./TimelineSection";
import type { DailyRecordEntry, MemoryThemeRow } from "../api";

afterEach(() => cleanup());

const record = (overrides: Partial<DailyRecordEntry> = {}): DailyRecordEntry => ({
  id: 1, recordDate: "2026-07-20", theme: "今天的主题", eventSummary: "写了一次日记",
  emotionWeather: "SUNNY", cognitiveSummary: "还不错", todoSummary: null,
  auroraSummary: "Aurora 观察到你今天很平静", capsuleSuggested: false, userAccepted: false,
  status: "ACTIVE", ...overrides
});

const theme = (overrides: Partial<MemoryThemeRow> = {}): MemoryThemeRow => ({
  id: 1, themeName: "自我理解", themeSummary: null, themeType: null, keywords: null,
  memoryCount: 4, averageGravity: 1.1, lastTouchedAt: null, status: "ACTIVE", ...overrides
});

describe("TimelineSection", () => {
  it("shows an empty state when there are no daily records", () => {
    render(<TimelineSection dailyRecords={[]} themes={[]} />);
    expect(screen.getByText(/完成一次 Aurora 对话或心声日记后/)).toBeVisible();
  });

  it("renders each daily record on the timeline", () => {
    render(<TimelineSection dailyRecords={[record()]} themes={[]} />);
    expect(screen.getByText("今天的主题")).toBeVisible();
    expect(screen.getByText("Aurora 观察到你今天很平静")).toBeVisible();
  });

  it("renders the theme evolution labels", () => {
    render(<TimelineSection dailyRecords={[record()]} themes={[theme({ themeName: "情绪节律" })]} />);
    expect(screen.getByText(/情绪节律/)).toBeVisible();
  });

  it("filters the timeline by the selected date", () => {
    render(<TimelineSection dailyRecords={[record({ id: 1, recordDate: "2026-07-20", theme: "A" }), record({ id: 2, recordDate: "2026-07-19", theme: "B" })]} themes={[]} />);
    expect(screen.getByText("A")).toBeVisible();
    expect(screen.getByText("B")).toBeVisible();
    const dateInput = screen.getByLabelText(/按日期查看|Filter by date/i);
    fireEvent.change(dateInput, { target: { value: "2026-07-19" } });
    expect(screen.queryByText("A")).not.toBeInTheDocument();
    expect(screen.getByText("B")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /全部|All/ }));
    expect(screen.getByText("A")).toBeVisible();
    expect(screen.getByText("B")).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<TimelineSection locale="en-SG" dailyRecords={[]} themes={[]} />);
    expect(screen.getByRole("heading", { name: "Growth Timeline" })).toBeVisible();
  });
});
