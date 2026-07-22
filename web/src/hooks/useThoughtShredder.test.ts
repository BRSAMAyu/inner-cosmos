import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { ShredderResult } from "../api";
import { useThoughtShredder } from "./useThoughtShredder";

vi.mock("../api", () => ({
  api: {
    aiHealth: vi.fn(),
    shredderHistory: vi.fn(),
    shredderProcess: vi.fn(),
    shredderSettle: vi.fn(),
    shredderDelete: vi.fn()
  }
}));

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useThoughtShredder({ setStatus }));
  return { result, setStatus };
}

const shredResult = (overrides: Partial<ShredderResult> = {}): ShredderResult => ({
  originalHandlingMode: "KEEP_ONLY_RESULT", coreFeeling: "疲惫", hiddenNeed: "被理解", noiseToDrop: ["噪音一"],
  sentenceToKeep: "我需要休息一下。", memoryCard: { id: 5, title: "从混乱里留下的一句话", summary: "我需要休息一下。", status: "ACTIVE", versionNo: 1, consentScope: null, memoryLayer: null, confidence: null },
  fragments: [], suggestedTodo: null, ...overrides
});

describe("useThoughtShredder", () => {
  it("does nothing and reports a status when the input is empty", async () => {
    const { result, setStatus } = setup();
    await act(async () => { await result.current.processShred("   ", "KEEP_ONLY_RESULT"); });
    expect(api.shredderProcess).not.toHaveBeenCalled();
    expect(setStatus).toHaveBeenCalledWith("请先写下你的想法");
  });

  it("processes real text and stores the result plus refreshed history", async () => {
    vi.mocked(api.shredderProcess).mockResolvedValue(shredResult());
    vi.mocked(api.shredderHistory).mockResolvedValue([{ id: 5, title: "从混乱里留下的一句话", summary: "我需要休息一下。", memoryType: "SHREDDER", emotionalGravity: 0.4 }]);
    const { result } = setup();
    await act(async () => { await result.current.processShred("今天很累", "KEEP_ONLY_RESULT"); });
    expect(api.shredderProcess).toHaveBeenCalledExactlyOnceWith("今天很累", "KEEP_ONLY_RESULT");
    expect(result.current.shredderResult).toEqual(shredResult());
    expect(result.current.shredderHistory).toHaveLength(1);
    expect(result.current.shredderBusy).toBe(false);
  });

  it("settles a memory card and refreshes history", async () => {
    vi.mocked(api.shredderSettle).mockResolvedValue(undefined);
    vi.mocked(api.shredderHistory).mockResolvedValue([]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.settleShred(5); });
    expect(api.shredderSettle).toHaveBeenCalledExactlyOnceWith(5);
    expect(setStatus).toHaveBeenCalledWith("已沉淀到记忆");
  });

  it("deletes a memory card and refreshes history", async () => {
    vi.mocked(api.shredderDelete).mockResolvedValue(undefined);
    vi.mocked(api.shredderHistory).mockResolvedValue([]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.deleteShred(5); });
    expect(api.shredderDelete).toHaveBeenCalledExactlyOnceWith(5);
    expect(setStatus).toHaveBeenCalledWith("记录已删除");
  });
});
