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
