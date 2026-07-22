import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { DailyRecordDetail, DailyRecordEntry } from "../api";
import { useDailyRecord } from "./useDailyRecord";

vi.mock("../api", () => ({
  api: {
    dailyRecords: vi.fn(),
    latestDailyRecord: vi.fn(),
    acceptDailyRecordEntry: vi.fn(),
    editDailyRecord: vi.fn()
  }
}));

afterEach(() => vi.clearAllMocks());

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useDailyRecord({ setStatus }));
  return { result, setStatus };
}

const entry = (overrides: Partial<DailyRecordEntry> = {}): DailyRecordEntry => ({
  id: 1, recordDate: "2026-07-20", theme: "今天", eventSummary: "写了作业", emotionWeather: "SUNNY",
  cognitiveSummary: "还好", todoSummary: null, auroraSummary: "继续加油", capsuleSuggested: false,
  userAccepted: false, status: "ACTIVE", ...overrides
});

const detail = (overrides: Partial<DailyRecordDetail> = {}): DailyRecordDetail => ({
  theme: "今天", auroraSummary: "继续加油", mainMemory: null, fragments: [], emotions: [], todos: [],
  capsuleSuggested: false, ...overrides
});

describe("useDailyRecord", () => {
  it("loads the daily-record list and the richer latest detail", async () => {
    vi.mocked(api.dailyRecords).mockResolvedValue([entry()]);
    vi.mocked(api.latestDailyRecord).mockResolvedValue(detail());
    const { result } = setup();
    await act(async () => { await result.current.loadDailyRecords(); });
    await act(async () => { await result.current.loadLatestDailyRecord(); });
    expect(result.current.dailyRecords).toEqual([entry()]);
    expect(result.current.dailyRecordDetail).toEqual(detail());
  });

  it("accepts the record at the current index using its real DailyRecord id", async () => {
    vi.mocked(api.dailyRecords).mockResolvedValue([entry({ id: 42 })]);
    vi.mocked(api.acceptDailyRecordEntry).mockResolvedValue(undefined);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.loadDailyRecords(); });
    await act(async () => { await result.current.acceptDailyRecord(); });
    expect(api.acceptDailyRecordEntry).toHaveBeenCalledExactlyOnceWith(42);
    expect(result.current.dailyRecords[0].userAccepted).toBe(true);
    expect(setStatus).toHaveBeenCalledWith("记录已接受并保存");
  });

  it("reports a friendly status when there is no record to accept", async () => {
    const { result, setStatus } = setup();
    await act(async () => { await result.current.acceptDailyRecord(); });
    expect(api.acceptDailyRecordEntry).not.toHaveBeenCalled();
    expect(setStatus).toHaveBeenCalledWith("没有可保存的记录");
  });

  it("can navigate to the previous day, then back to today, across multiple entries", async () => {
    // Regression for the Gemini audit's 2.2.1 finding: the old clamp was
    // `Math.max(0, Math.min(index, current))`, which can never increase past `current`, so the
    // "前一天" (previous day) button -- which calls onSelectIndex(index + 1) -- was permanently
    // inert. records[] is ordered newest-first (index 0 = today); spans a month boundary to also
    // cover the "跨月" (cross-month) requirement, since index navigation must not assume any
    // particular date arithmetic, only list bounds.
    vi.mocked(api.dailyRecords).mockResolvedValue([
      entry({ id: 3, recordDate: "2026-08-01" }),
      entry({ id: 2, recordDate: "2026-07-31" }),
      entry({ id: 1, recordDate: "2026-07-30" })
    ]);
    const { result } = setup();
    await act(async () => { await result.current.loadDailyRecords(); });
    expect(result.current.dailyRecordIndex).toBe(0);

    act(() => result.current.selectDailyRecordIndex(1)); // "前一天" from today
    expect(result.current.dailyRecordIndex).toBe(1);

    act(() => result.current.selectDailyRecordIndex(2)); // "前一天" again, crossing into July
    expect(result.current.dailyRecordIndex).toBe(2);

    act(() => result.current.selectDailyRecordIndex(1)); // "后一天" back towards today
    expect(result.current.dailyRecordIndex).toBe(1);

    act(() => result.current.selectDailyRecordIndex(0)); // "后一天" all the way back
    expect(result.current.dailyRecordIndex).toBe(0);
  });

  it("clamps navigation to the loaded record list's bounds", async () => {
    vi.mocked(api.dailyRecords).mockResolvedValue([entry({ id: 1 }), entry({ id: 2 })]);
    const { result } = setup();
    await act(async () => { await result.current.loadDailyRecords(); });

    act(() => result.current.selectDailyRecordIndex(-5));
    expect(result.current.dailyRecordIndex).toBe(0);

    act(() => result.current.selectDailyRecordIndex(50));
    expect(result.current.dailyRecordIndex).toBe(1); // clamped to records.length - 1, not left inert

    act(() => result.current.selectDailyRecordIndex(50));
    expect(result.current.dailyRecordIndex).toBe(1); // repeat past-bound selection stays clamped, not stuck below it
  });

  it("clamps to index 0 when there are no records to page through (no-data day)", async () => {
    vi.mocked(api.dailyRecords).mockResolvedValue([]);
    const { result } = setup();
    await act(async () => { await result.current.loadDailyRecords(); });
    act(() => result.current.selectDailyRecordIndex(3));
    expect(result.current.dailyRecordIndex).toBe(0);
  });

  it("edits the theme field of the current record and refreshes the latest detail when it is index 0", async () => {
    vi.mocked(api.dailyRecords).mockResolvedValue([entry({ id: 9, theme: "旧主题" })]);
    vi.mocked(api.editDailyRecord).mockResolvedValue(entry({ id: 9, theme: "新主题" }));
    vi.mocked(api.latestDailyRecord).mockResolvedValue(detail({ theme: "新主题" }));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.loadDailyRecords(); });
    await act(async () => { await result.current.editDailyRecordField("theme", "新主题"); });
    expect(api.editDailyRecord).toHaveBeenCalledExactlyOnceWith(9, { theme: "新主题" });
    await waitFor(() => expect(result.current.dailyRecords[0].theme).toBe("新主题"));
    expect(setStatus).toHaveBeenCalledWith("已保存");
  });
});
