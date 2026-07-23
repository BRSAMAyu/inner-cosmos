import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { InlineAudioPlayer } from "./InlineAudioPlayer";

// A fully controllable fake Audio -- jsdom's real HTMLMediaElement.play() is a stub that logs a
// "not implemented" jsdom error and returns undefined, which cannot exercise the
// promise-resolve/promise-reject branches this component depends on.
class FakeAudio {
  static instances: FakeAudio[] = [];
  src: string;
  play: ReturnType<typeof vi.fn>;
  pause = vi.fn();
  private listeners: Record<string, Array<() => void>> = {};
  constructor(src: string) {
    this.src = src;
    this.play = vi.fn(() => Promise.resolve());
    FakeAudio.instances.push(this);
  }
  addEventListener(type: string, cb: () => void) { (this.listeners[type] ??= []).push(cb); }
  removeEventListener(type: string, cb: () => void) {
    this.listeners[type] = (this.listeners[type] ?? []).filter(listener => listener !== cb);
  }
  listenerCount(type: string) { return (this.listeners[type] ?? []).length; }
}

beforeEach(() => {
  FakeAudio.instances = [];
  vi.stubGlobal("Audio", FakeAudio);
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("InlineAudioPlayer", () => {
  it("renders a clickable button that plays on click when autoPlay is not set (ON_DEMAND / manual)", async () => {
    render(<InlineAudioPlayer audio="data:audio/mpeg;base64,AAA" />);
    const button = screen.getByRole("button");
    expect(FakeAudio.instances[0].play).not.toHaveBeenCalled();
    fireEvent.click(button);
    expect(FakeAudio.instances[0].play).toHaveBeenCalledOnce();
    await waitFor(() => expect(button).toHaveTextContent("播放中"));
  });

  it("auto-plays once on mount when autoPlay is set (AMBIENT)", async () => {
    render(<InlineAudioPlayer audio="data:audio/mpeg;base64,AAA" autoPlay />);
    expect(FakeAudio.instances[0].play).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.getByRole("button")).toHaveTextContent("播放中"));
  });

  it("falls back to a visible, retryable play affordance when the browser rejects an unsolicited play() (autoplay policy)", async () => {
    class RejectingAudio extends FakeAudio {
      constructor(src: string) { super(src); this.play = vi.fn(() => Promise.reject(new DOMException("blocked", "NotAllowedError"))); }
    }
    vi.stubGlobal("Audio", RejectingAudio);
    render(<InlineAudioPlayer audio="data:audio/mpeg;base64,AAA" autoPlay />);
    const button = await screen.findByRole("button");
    // Never silently do nothing: a visible, still-clickable affordance appears instead, and it is
    // distinguishable from the plain idle label (it says autoplay was blocked).
    await waitFor(() => expect(button).toHaveTextContent("拦截"));
    expect(button).not.toBeDisabled();
    // Retrying (a real click, i.e. an actual user gesture) should attempt play() again.
    fireEvent.click(button);
    expect(FakeAudio.instances[0].play).toHaveBeenCalledTimes(2);
  });

  it("cleans up its Audio element on unmount: pauses, drops its listeners, and clears its source", () => {
    const { unmount } = render(<InlineAudioPlayer audio="data:audio/mpeg;base64,AAA" autoPlay />);
    const instance = FakeAudio.instances[0];
    expect(instance.listenerCount("ended")).toBe(1);
    expect(instance.listenerCount("error")).toBe(1);
    unmount();
    expect(instance.pause).toHaveBeenCalledOnce();
    expect(instance.src).toBe("");
    expect(instance.listenerCount("ended")).toBe(0);
    expect(instance.listenerCount("error")).toBe(0);
  });

  it("fires onPlayAttempt the moment a play attempt starts, whether from autoplay or a click", () => {
    const onPlayAttempt = vi.fn();
    const { rerender } = render(<InlineAudioPlayer audio="data:audio/mpeg;base64,AAA" onPlayAttempt={onPlayAttempt} />);
    expect(onPlayAttempt).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button"));
    expect(onPlayAttempt).toHaveBeenCalledOnce();

    onPlayAttempt.mockClear();
    rerender(<InlineAudioPlayer audio="data:audio/mpeg;base64,BBB" autoPlay onPlayAttempt={onPlayAttempt} />);
    expect(onPlayAttempt).toHaveBeenCalledOnce();
  });

  it("renders in English when locale is en-SG", () => {
    render(<InlineAudioPlayer audio="data:audio/mpeg;base64,AAA" locale="en-SG" />);
    expect(screen.getByRole("button", { name: "▶ Play" })).toBeVisible();
  });
});
