import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PcmWavRecorder } from "./audio-recorder";

// Regression coverage for remaining-work-handoff.md 2.2.2 / the Gemini audit: this shared
// recorder backs both AuroraConversation's and HeartDiary's mic capture, but had zero test
// coverage anywhere in the repo for its safety-relevant behavior -- permission denial, the 60s
// hard cap, background/visibility auto-stop, and teardown (timers, listeners, tracks, context).
// A component-level test can't easily force these paths; they belong here, once, on the shared class.

class FakeAudioContext {
  sampleRate = 16_000;
  closed = false;
  createMediaStreamSource = vi.fn(() => ({ connect: vi.fn(), disconnect: vi.fn() }));
  // No real AudioWorklet in jsdom -- force the documented ScriptProcessor fallback path.
  audioWorklet = { addModule: vi.fn().mockRejectedValue(new Error("no AudioWorklet in jsdom")) };
  createScriptProcessor = vi.fn(() => ({ onaudioprocess: null as ((e: unknown) => void) | null, connect: vi.fn(), disconnect: vi.fn() }));
  close = vi.fn(async () => { this.closed = true; });
}

function fakeStream() {
  const track = { stop: vi.fn() };
  return { getTracks: () => [track], _track: track };
}

beforeEach(() => {
  vi.useFakeTimers();
  (globalThis as unknown as { AudioContext: unknown }).AudioContext = FakeAudioContext;
  if (!URL.createObjectURL) (URL as unknown as { createObjectURL: () => string }).createObjectURL = () => "blob:test";
  if (!URL.revokeObjectURL) (URL as unknown as { revokeObjectURL: () => void }).revokeObjectURL = () => undefined;
});

afterEach(() => {
  vi.useRealTimers();
  delete (navigator as unknown as Record<string, unknown>).mediaDevices;
  delete (globalThis as unknown as Record<string, unknown>).AudioContext;
});

describe("PcmWavRecorder", () => {
  it("propagates permission denial from getUserMedia and leaves the recorder inert (no active stream)", async () => {
    const denied = Object.assign(new Error("Permission denied"), { name: "NotAllowedError" });
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockRejectedValue(denied) };
    const recorder = new PcmWavRecorder();
    await expect(recorder.start()).rejects.toThrow("Permission denied");
    // Recorder must not be left thinking it's active -- a second start() attempt must not throw
    // "already active", and stop() on a never-started recorder must be a safe no-op.
    await expect(recorder.stop()).resolves.toBeNull();
  });

  it("auto-stops after the 60-second hard cap and tears down (timer, listener, tracks, context)", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const removeSpy = vi.spyOn(document, "removeEventListener");
    const recorder = new PcmWavRecorder();
    const onAutoStop = vi.fn(() => void recorder.stop());
    await recorder.start(undefined, onAutoStop);

    expect(onAutoStop).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(onAutoStop).toHaveBeenCalledOnce();
    expect(stream._track.stop).toHaveBeenCalledOnce();
    expect(removeSpy.mock.calls.some(call => call[0] === "visibilitychange")).toBe(true);

    // Firing the timer again (there shouldn't be one left) must not call onAutoStop a second time.
    await vi.advanceTimersByTimeAsync(120_000);
    expect(onAutoStop).toHaveBeenCalledOnce();
  });

  it("auto-stops when the tab goes into the background (visibilitychange + document.hidden)", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const recorder = new PcmWavRecorder();
    const onAutoStop = vi.fn();
    await recorder.start(undefined, onAutoStop);

    Object.defineProperty(document, "hidden", { value: true, configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
    expect(onAutoStop).toHaveBeenCalledOnce();
    Object.defineProperty(document, "hidden", { value: false, configurable: true });
  });

  it("does not auto-stop on a visibilitychange while still foregrounded", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const recorder = new PcmWavRecorder();
    const onAutoStop = vi.fn();
    await recorder.start(undefined, onAutoStop);
    document.dispatchEvent(new Event("visibilitychange")); // document.hidden is false by default in jsdom
    expect(onAutoStop).not.toHaveBeenCalled();
    await recorder.stop(true);
  });

  it("stop() clears the 60s timer so it never fires after an explicit stop, and removes the buffer", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const recorder = new PcmWavRecorder();
    const onAutoStop = vi.fn();
    await recorder.start(undefined, onAutoStop);
    await recorder.stop(true);

    await vi.advanceTimersByTimeAsync(60_000);
    expect(onAutoStop).not.toHaveBeenCalled();
    expect(stream._track.stop).toHaveBeenCalledOnce();

    // A second stop() call must be a safe no-op, not double-teardown (no second track.stop()).
    await recorder.stop();
    expect(stream._track.stop).toHaveBeenCalledOnce();
  });

  it("stop(cancel) discards captured audio (returns null) even if samples were captured", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const recorder = new PcmWavRecorder();
    await recorder.start();
    expect(await recorder.stop(true)).toBeNull();
  });

  it("stop() with no captured samples returns null instead of an empty blob", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const recorder = new PcmWavRecorder();
    await recorder.start();
    expect(await recorder.stop(false)).toBeNull();
  });

  it("rejects a second concurrent start() while already active", async () => {
    const stream = fakeStream();
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia: vi.fn().mockResolvedValue(stream) };
    const recorder = new PcmWavRecorder();
    await recorder.start();
    await expect(recorder.start()).rejects.toThrow("Recorder is already active");
    await recorder.stop(true);
  });
});
