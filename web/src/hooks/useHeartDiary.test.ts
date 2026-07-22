import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, diaryTranscribeAudio } from "../api";
import { useHeartDiary } from "./useHeartDiary";

vi.mock("../api", () => ({
  api: {
    diaryTranscribe: vi.fn(),
    diaryPolish: vi.fn(),
    diarySubmit: vi.fn()
  },
  diaryTranscribeAudio: vi.fn()
}));

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useHeartDiary({ setStatus }));
  return { result, setStatus };
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("useHeartDiary -- initial state", () => {
  it("starts with empty raw/display text at level 0", () => {
    const { result } = setup();
    expect(result.current.rawText).toBe("");
    expect(result.current.displayText).toBe("");
    expect(result.current.activeLevel).toBe(0);
    expect(result.current.transcriptionId).toBeNull();
    expect(result.current.polishBusy).toBe(false);
    expect(result.current.submitBusy).toBe(false);
  });
});

describe("useHeartDiary -- text editing", () => {
  it("onTextChange updates raw and display text and resets any prior transcription", async () => {
    vi.mocked(api.diaryTranscribe).mockResolvedValue({ id: 9, originalText: "x", editedText: null, status: "RAW" });
    const { result } = setup();
    act(() => { result.current.onTextChange("今天很累"); });
    expect(result.current.rawText).toBe("今天很累");
    expect(result.current.displayText).toBe("今天很累");
    expect(result.current.transcriptionId).toBeNull();
    expect(result.current.activeLevel).toBe(0);
  });
});

describe("useHeartDiary -- audio transcription", () => {
  it("applies a transcribed blob's text and id, and reports success", async () => {
    vi.mocked(diaryTranscribeAudio).mockResolvedValue({ id: 42, originalText: "语音转写的内容", editedText: null, status: "RAW" });
    const { result, setStatus } = setup();
    await act(async () => { await result.current.transcribeAudio(new Blob()); });
    expect(result.current.transcriptionId).toBe(42);
    expect(result.current.rawText).toBe("语音转写的内容");
    expect(result.current.displayText).toBe("语音转写的内容");
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("转成文字"));
  });

  it("reports a friendly error when transcription fails", async () => {
    vi.mocked(diaryTranscribeAudio).mockRejectedValue(new Error("没有识别到可用文字"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.transcribeAudio(new Blob()); });
    expect(setStatus).toHaveBeenCalledWith("没有识别到可用文字");
    expect(result.current.transcriptionId).toBeNull();
  });
});

describe("useHeartDiary -- switching polish levels", () => {
  it("switching to level 0 shows the raw text without calling the backend", async () => {
    const { result } = setup();
    act(() => { result.current.onTextChange("原始文字"); });
    await act(async () => { await result.current.switchLevel(0); });
    expect(result.current.displayText).toBe("原始文字");
    expect(api.diaryPolish).not.toHaveBeenCalled();
  });

  it("fetches a transcription id and the polished text on first visit to a level, then caches it", async () => {
    vi.mocked(api.diaryTranscribe).mockResolvedValue({ id: 11, originalText: "原始文字", editedText: null, status: "RAW" });
    vi.mocked(api.diaryPolish).mockResolvedValue({ polishedText: "整理后的文字" });
    const { result } = setup();
    act(() => { result.current.onTextChange("原始文字"); });
    await act(async () => { await result.current.switchLevel(2); });
    expect(api.diaryTranscribe).toHaveBeenCalledExactlyOnceWith("原始文字");
    expect(api.diaryPolish).toHaveBeenCalledExactlyOnceWith("原始文字", 2);
    expect(result.current.displayText).toBe("整理后的文字");
    expect(result.current.transcriptionId).toBe(11);

    // Revisiting the same level does not refetch.
    await act(async () => { await result.current.switchLevel(0); });
    await act(async () => { await result.current.switchLevel(2); });
    expect(api.diaryPolish).toHaveBeenCalledTimes(1);
    expect(result.current.displayText).toBe("整理后的文字");
  });

  it("does not refetch a transcription id it already has", async () => {
    vi.mocked(diaryTranscribeAudio).mockResolvedValue({ id: 77, originalText: "口述内容", editedText: null, status: "RAW" });
    vi.mocked(api.diaryPolish).mockResolvedValue({ polishedText: "润色内容" });
    const { result } = setup();
    await act(async () => { await result.current.transcribeAudio(new Blob()); });
    await act(async () => { await result.current.switchLevel(1); });
    expect(api.diaryTranscribe).not.toHaveBeenCalled();
    expect(api.diaryPolish).toHaveBeenCalledExactlyOnceWith("口述内容", 1);
  });
});

describe("useHeartDiary -- submit", () => {
  it("refuses to submit content under 5 characters", async () => {
    const { result, setStatus } = setup();
    act(() => { result.current.onTextChange("短"); });
    let ok = true;
    await act(async () => { ok = await result.current.submit(); });
    expect(ok).toBe(false);
    expect(api.diarySubmit).not.toHaveBeenCalled();
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("太短"));
  });

  it("ensures a transcription id exists, then submits the displayed content", async () => {
    vi.mocked(api.diaryTranscribe).mockResolvedValue({ id: 21, originalText: "今天发生了很多事", editedText: null, status: "RAW" });
    vi.mocked(api.diarySubmit).mockResolvedValue(true);
    const { result, setStatus } = setup();
    act(() => { result.current.onTextChange("今天发生了很多事"); });
    let ok = false;
    await act(async () => { ok = await result.current.submit(); });
    expect(ok).toBe(true);
    expect(api.diaryTranscribe).toHaveBeenCalledExactlyOnceWith("今天发生了很多事");
    expect(api.diarySubmit).toHaveBeenCalledExactlyOnceWith(21, "今天发生了很多事");
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("记忆宇宙"));
    expect(result.current.submitted).toBe(true);
  });

  it("submits with the existing transcription id without re-transcribing", async () => {
    vi.mocked(diaryTranscribeAudio).mockResolvedValue({ id: 55, originalText: "已经转写过的内容", editedText: null, status: "RAW" });
    vi.mocked(api.diarySubmit).mockResolvedValue(true);
    const { result } = setup();
    await act(async () => { await result.current.transcribeAudio(new Blob()); });
    await act(async () => { await result.current.submit(); });
    expect(api.diaryTranscribe).not.toHaveBeenCalled();
    expect(api.diarySubmit).toHaveBeenCalledExactlyOnceWith(55, "已经转写过的内容");
  });
});
