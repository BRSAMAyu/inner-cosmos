import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DailyRecordSection } from "./DailyRecordSection";
import type { DailyRecordDetail, DailyRecordEntry } from "../api";

afterEach(() => cleanup());

const entry = (overrides: Partial<DailyRecordEntry> = {}): DailyRecordEntry => ({
  id: 1, recordDate: "2026-07-20", theme: "今天的主题", eventSummary: "写了一次日记",
  emotionWeather: "SUNNY", cognitiveSummary: "还不错", todoSummary: null,
  auroraSummary: "Aurora 观察到你今天很平静", capsuleSuggested: false, userAccepted: false,
  status: "ACTIVE", ...overrides
});

const detail = (overrides: Partial<DailyRecordDetail> = {}): DailyRecordDetail => ({
  theme: "今天的主题", auroraSummary: "Aurora 观察到你今天很平静",
  mainMemory: { id: 9, title: "今天的主题", summary: "摘要", status: "ACTIVE", versionNo: 1, consentScope: null, memoryLayer: null, confidence: null },
  fragments: [{ id: 1, fragmentType: "EMOTION", rawExcerpt: "有点累", aiAnalysis: null, reframeText: "先允许自己休息" }],
  emotions: [{ id: 1, emotionName: "疲惫", emotionScore: 6, weatherType: "CLOUDY", triggerScene: "赶作业" }],
  todos: [{ id: 1, taskName: "推进作业", description: "十分钟", priority: "MEDIUM", status: "TODO" }],
  capsuleSuggested: false, ...overrides
});

describe("DailyRecordSection", () => {
  it("shows an empty state before any daily record exists", () => {
    render(<DailyRecordSection records={[]} detail={null} index={0} acceptBusy={false} editBusy={null}
      onAccept={() => undefined} onEditField={() => undefined} onSelectIndex={() => undefined} />);
    expect(screen.getByText(/今天还没有记录/)).toBeVisible();
  });

  it("renders today's rich detail: fragments, emotions and todos", () => {
    render(<DailyRecordSection records={[entry()]} detail={detail()} index={0} acceptBusy={false} editBusy={null}
      onAccept={() => undefined} onEditField={() => undefined} onSelectIndex={() => undefined} />);
    expect(screen.getByText("今天的主题")).toBeVisible();
    expect(screen.getByText("有点累")).toBeVisible();
    expect(screen.getByText("先允许自己休息")).toBeVisible();
    expect(screen.getByText(/疲惫/)).toBeVisible();
    expect(screen.getByText("推进作业")).toBeVisible();
  });

  it("accepts the current record", () => {
    const onAccept = vi.fn();
    render(<DailyRecordSection records={[entry()]} detail={detail()} index={0} acceptBusy={false} editBusy={null}
      onAccept={onAccept} onEditField={() => undefined} onSelectIndex={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: /接受并保存/ }));
    expect(onAccept).toHaveBeenCalledOnce();
  });

  it("edits the theme field inline and saves it", () => {
    const onEditField = vi.fn();
    render(<DailyRecordSection records={[entry()]} detail={detail()} index={0} acceptBusy={false} editBusy={null}
      onAccept={() => undefined} onEditField={onEditField} onSelectIndex={() => undefined} />);
    fireEvent.click(screen.getAllByRole("button", { name: /编辑/ })[0]);
    const input = screen.getByLabelText(/编辑主题/);
    fireEvent.change(input, { target: { value: "新的主题" } });
    fireEvent.click(screen.getByRole("button", { name: /^保存$/ }));
    expect(onEditField).toHaveBeenCalledExactlyOnceWith("theme", "新的主题");
  });

  it("navigates to a previous record and shows its plain fields", () => {
    const onSelectIndex = vi.fn();
    render(<DailyRecordSection
      records={[entry({ id: 1, recordDate: "2026-07-20", theme: "今天" }), entry({ id: 2, recordDate: "2026-07-19", theme: "昨天" })]}
      detail={detail()} index={0} acceptBusy={false} editBusy={null}
      onAccept={() => undefined} onEditField={() => undefined} onSelectIndex={onSelectIndex} />);
    fireEvent.click(screen.getByRole("button", { name: /前一天|Previous day/ }));
    expect(onSelectIndex).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("renders in English when locale is en-SG", () => {
    render(<DailyRecordSection locale="en-SG" records={[]} detail={null} index={0} acceptBusy={false} editBusy={null}
      onAccept={() => undefined} onEditField={() => undefined} onSelectIndex={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Today's Record Card" })).toBeVisible();
  });
});
