import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FormEvent } from "react";
import { AuroraConversation } from "./AuroraConversation";

const recorder = vi.hoisted(() => ({ start: vi.fn(), stop: vi.fn() }));
vi.mock("../audio-recorder", () => ({
  PcmWavRecorder: class {
    start = recorder.start;
    stop = recorder.stop;
  }
}));

afterEach(cleanup);
afterEach(() => {
  delete (navigator as unknown as Record<string, unknown>).mediaDevices;
  delete (globalThis as Record<string, unknown>).AudioContext;
  recorder.start.mockReset(); recorder.stop.mockReset();
});

describe("AuroraConversation", () => {
  it("uses compact invitation spacing only before the first message", () => {
    const props = { activeTurnId: null, draft: "", sessionReady: true,
      onDraftChange: () => undefined, onSubmit: (event: FormEvent<HTMLFormElement>) => event.preventDefault(),
      onStop: () => undefined };
    const { rerender } = render(<AuroraConversation {...props} messages={[]} />);
    expect(screen.getByRole("region", { name: "与 Aurora 的对话" })).toHaveClass("empty-state");

    rerender(<AuroraConversation {...props} messages={[{ key: "u1", speaker: "USER", text: "我想说一件事" }]} />);
    expect(screen.getByRole("region", { name: "与 Aurora 的对话" })).toHaveClass("has-messages");
    expect(screen.getByRole("region", { name: "与 Aurora 的对话" })).not.toHaveClass("empty-state");
  });

  it("scopes aria-live to only the actively streaming/partial bubble, not the whole conversation region", () => {
    const props = { activeTurnId: 9, draft: "", sessionReady: true,
      onDraftChange: () => undefined, onSubmit: (event: FormEvent<HTMLFormElement>) => event.preventDefault(),
      onStop: () => undefined };
    render(<AuroraConversation {...props} messages={[
      { key: "u1", speaker: "USER", text: "我想说一件事" },
      { key: "a1", speaker: "AURORA", text: "已经完成的一句", partial: false },
      { key: "a2", speaker: "AURORA", text: "还在生成中", partial: true }
    ]} />);
    // Screen readers must not re-announce every token across the whole region -- only the
    // one bubble that is actively streaming should be a live region.
    expect(screen.getByRole("region", { name: "与 Aurora 的对话" })).not.toHaveAttribute("aria-live");
    const streamingBubble = screen.getByText("还在生成中").closest("article");
    expect(streamingBubble).toHaveAttribute("aria-live", "polite");
    const completedBubble = screen.getByText("已经完成的一句").closest("article");
    expect(completedBubble).not.toHaveAttribute("aria-live");
  });

  it("preserves multi-message and partial interruption semantics", () => {
    render(<AuroraConversation messages={[
      { key: "u1", speaker: "USER", text: "先听我说" },
      { key: "a1", speaker: "AURORA", text: "我在这里", partial: true }
    ]} activeTurnId={7} draft="新的补充" sessionReady onDraftChange={() => undefined}
      onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.getByText("先听我说")).toBeVisible();
    expect(screen.getByText("我在这里")).toBeVisible();
    expect(screen.getByText("停在这里")).toBeVisible();
    expect(screen.getByRole("button", { name: "打断并发送" })).toBeEnabled();
  });

  it("auto-scrolls to the newest message as the conversation grows, so a long thread never leaves the reply hidden below the fold", () => {
    const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(() => {});
    const props = { activeTurnId: null, draft: "", sessionReady: true,
      onDraftChange: () => undefined, onSubmit: (event: FormEvent<HTMLFormElement>) => event.preventDefault(),
      onStop: () => undefined };
    const { rerender } = render(<AuroraConversation {...props} messages={[{ key: "u1", speaker: "USER", text: "第一句" }]} />);
    expect(scrollIntoView).toHaveBeenCalled();
    scrollIntoView.mockClear();

    rerender(<AuroraConversation {...props} messages={[
      { key: "u1", speaker: "USER", text: "第一句" },
      { key: "a1", speaker: "AURORA", text: "第二句", partial: true }
    ]} />);
    expect(scrollIntoView).toHaveBeenCalled();
    scrollIntoView.mockRestore();
  });

  it("offers a goodbye-ritual trigger only when idle and ready, never mid-stream", () => {
    const onGoodbye = vi.fn();
    const props = { draft: "", onDraftChange: () => undefined, onSubmit: (event: FormEvent<HTMLFormElement>) => event.preventDefault(),
      onStop: () => undefined, onGoodbye };
    const { rerender } = render(<AuroraConversation {...props} messages={[]} activeTurnId={null} sessionReady />);
    fireEvent.click(screen.getByRole("button", { name: "沉淀今天" }));
    expect(onGoodbye).toHaveBeenCalledOnce();

    rerender(<AuroraConversation {...props} messages={[]} activeTurnId={7} sessionReady />);
    expect(screen.queryByRole("button", { name: "沉淀今天" })).not.toBeInTheDocument();

    rerender(<AuroraConversation {...props} messages={[]} activeTurnId={null} sessionReady={false} />);
    expect(screen.queryByRole("button", { name: "沉淀今天" })).not.toBeInTheDocument();
  });

  it("renders composer, thinking beat and controls in English when locale is en-SG", () => {
    render(<AuroraConversation locale="en-SG" messages={[
      { key: "a1", speaker: "AURORA", text: "", partial: true }
    ]} activeTurnId={7} thinkingStage="composing" draft="a new thought" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.getByLabelText("Write to Aurora")).toBeVisible();
    expect(screen.getByText("Aurora is composing the next line…")).toBeVisible();
    expect(screen.getByRole("button", { name: "Stop responding" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Interrupt & send" })).toBeEnabled();
  });

  it("keeps stop and draft changes under caller control", () => {
    const onStop = vi.fn();
    const onDraftChange = vi.fn();
    render(<AuroraConversation messages={[]} activeTurnId={9} draft="" sessionReady
      onDraftChange={onDraftChange} onSubmit={event => event.preventDefault()} onStop={onStop} />);
    fireEvent.change(screen.getByLabelText("写给 Aurora"), { target: { value: "等等" } });
    fireEvent.click(screen.getByRole("button", { name: "停止回应" }));
    expect(onDraftChange).toHaveBeenCalledWith("等等");
    expect(onStop).toHaveBeenCalledOnce();
  });

  it("shows an inline thinking beat only while a turn is active and pre-speech", () => {
    const { rerender } = render(<AuroraConversation messages={[{ key: "u1", speaker: "USER", text: "在吗" }]}
      activeTurnId={7} thinkingStage="understanding" draft="" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.getByLabelText("Aurora 正在思考")).toBeVisible();
    expect(screen.getByText("Aurora 正在理解这一刻…")).toBeVisible();

    rerender(<AuroraConversation messages={[{ key: "u1", speaker: "USER", text: "在吗" }]}
      activeTurnId={7} thinkingStage="composing" draft="" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.getByText("Aurora 正在组织下一句…")).toBeVisible();

    // No beat once the turn ends, and none while actively streaming tokens (thinkingStage null).
    rerender(<AuroraConversation messages={[{ key: "u1", speaker: "USER", text: "在吗" }]}
      activeTurnId={null} thinkingStage="understanding" draft="" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.queryByLabelText("Aurora 正在思考")).not.toBeInTheDocument();

    rerender(<AuroraConversation messages={[{ key: "u1", speaker: "USER", text: "在吗" }]}
      activeTurnId={7} thinkingStage={null} draft="" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.queryByLabelText("Aurora 正在思考")).not.toBeInTheDocument();
  });

  it("hides the mic button when voice capture is unsupported", () => {
    render(<AuroraConversation messages={[]} activeTurnId={null} draft="" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined}
      onTranscribe={async () => "x"} />);
    expect(screen.queryByRole("button", { name: "用语音输入" })).not.toBeInTheDocument();
  });

  it("records voice, transcribes on stop, and appends the text to the draft", async () => {
    (globalThis as Record<string, unknown>).AudioContext = class {};
    const getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] });
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia };
    recorder.start.mockResolvedValue(undefined);
    recorder.stop.mockResolvedValue(new Blob(["wav"], { type: "audio/wav" }));
    const onDraftChange = vi.fn();
    const onTranscribe = vi.fn().mockResolvedValue("语音转出来的文字");
    render(<AuroraConversation messages={[]} activeTurnId={null} draft="" sessionReady
      onDraftChange={onDraftChange} onSubmit={event => event.preventDefault()} onStop={() => undefined}
      onTranscribe={onTranscribe} />);
    fireEvent.click(screen.getByRole("button", { name: "用语音输入" }));
    await waitFor(() => expect(recorder.start).toHaveBeenCalled());
    fireEvent.click(await screen.findByRole("button", { name: "停止录音并转写" }));
    await waitFor(() => expect(onTranscribe).toHaveBeenCalled());
    await waitFor(() => expect(onDraftChange).toHaveBeenCalledWith("语音转出来的文字"));
    expect(recorder.stop).toHaveBeenCalledWith(false);
  });

  // Gemini audit 4.6 (CONFIRMED/P2): a transcription promise that is still in flight when the
  // component unmounts must not be allowed to call onDraftChange (or otherwise touch state) once
  // it finally resolves -- there is no component left to own that draft anymore.
  it("does not apply a transcription result that resolves after the component has unmounted", async () => {
    (globalThis as Record<string, unknown>).AudioContext = class {};
    const getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] });
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia };
    recorder.start.mockResolvedValue(undefined);
    recorder.stop.mockResolvedValue(new Blob(["wav"], { type: "audio/wav" }));
    const onDraftChange = vi.fn();
    let resolveTranscribe!: (text: string) => void;
    const onTranscribe = vi.fn(() => new Promise<string>(resolve => { resolveTranscribe = resolve; }));

    const { unmount } = render(<AuroraConversation messages={[]} activeTurnId={null} draft="" sessionReady
      onDraftChange={onDraftChange} onSubmit={event => event.preventDefault()} onStop={() => undefined}
      onTranscribe={onTranscribe} />);
    fireEvent.click(screen.getByRole("button", { name: "用语音输入" }));
    await waitFor(() => expect(recorder.start).toHaveBeenCalled());
    fireEvent.click(await screen.findByRole("button", { name: "停止录音并转写" }));
    await waitFor(() => expect(onTranscribe).toHaveBeenCalled());

    unmount();
    resolveTranscribe("迟到的转写文字");
    // Let the resolved-promise continuation actually run before asserting.
    await Promise.resolve(); await Promise.resolve(); await Promise.resolve();

    expect(onDraftChange).not.toHaveBeenCalledWith("迟到的转写文字");
    expect(onDraftChange).not.toHaveBeenCalled();
  });

  // Same audit item: overlapping generations. finishRecording() nulls recorderRef and flips
  // `recording` back to false *synchronously*, before it awaits `recorder.stop()` -- so there is a
  // real window, while that stop() promise is still pending (and `transcribing` hasn't flipped on
  // yet), where the mic button is already re-enabled and labelled to start a brand new recording.
  // We hold recorder.stop()'s first call open to force that window, then run a whole second
  // start/stop cycle (generation #2) inside it, whose own recorder.stop() resolves immediately and
  // so reaches onTranscribe first. The stale (first) transcription must never be applied once it
  // finally resolves, no matter the resolution order.
  it("ignores a stale transcription result once a newer recording generation has started", async () => {
    (globalThis as Record<string, unknown>).AudioContext = class {};
    const getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] });
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia };
    recorder.start.mockResolvedValue(undefined);
    let resolveStopA!: (blob: Blob) => void;
    const stopAPromise = new Promise<Blob>(resolve => { resolveStopA = resolve; });
    recorder.stop
      .mockImplementationOnce(() => stopAPromise)
      .mockImplementationOnce(() => Promise.resolve(new Blob(["wav-b"], { type: "audio/wav" })));

    const onDraftChange = vi.fn();
    let resolveFirstTranscribe!: (text: string) => void;
    let resolveSecondTranscribe!: (text: string) => void;
    const onTranscribe = vi.fn()
      .mockImplementationOnce(() => new Promise<string>(resolve => { resolveFirstTranscribe = resolve; }))
      .mockImplementationOnce(() => new Promise<string>(resolve => { resolveSecondTranscribe = resolve; }));

    render(<AuroraConversation messages={[]} activeTurnId={null} draft="" sessionReady
      onDraftChange={onDraftChange} onSubmit={event => event.preventDefault()} onStop={() => undefined}
      onTranscribe={onTranscribe} />);

    // Start + stop recording A -- generation #1. Its recorder.stop() is held open by stopAPromise.
    fireEvent.click(screen.getByRole("button", { name: "用语音输入" }));
    await waitFor(() => expect(recorder.start).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole("button", { name: "停止录音并转写" }));

    // While A's recorder.stop() is still pending, the mic button is already back to its idle
    // "start" state -- use that window to run a whole second recording, B (generation #2), whose
    // own recorder.stop() resolves immediately.
    fireEvent.click(await screen.findByRole("button", { name: "用语音输入" }));
    await waitFor(() => expect(recorder.start).toHaveBeenCalledTimes(2));
    fireEvent.click(await screen.findByRole("button", { name: "停止录音并转写" }));

    // Generation #2 (B) reaches onTranscribe first, since generation #1 (A) is still blocked on
    // its own recorder.stop().
    await waitFor(() => expect(onTranscribe).toHaveBeenCalledTimes(1));

    // Now let generation #1 catch up: its recorder.stop() finally resolves, so it too proceeds to
    // call onTranscribe -- but generationRef has already moved on to #2, so its result is stale.
    resolveStopA(new Blob(["wav-a"], { type: "audio/wav" }));
    await waitFor(() => expect(onTranscribe).toHaveBeenCalledTimes(2));

    resolveSecondTranscribe("过期的转写A");
    await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    expect(onDraftChange).not.toHaveBeenCalledWith("过期的转写A");

    resolveFirstTranscribe("最新的转写B");
    await waitFor(() => expect(onDraftChange).toHaveBeenCalledWith("最新的转写B"));
    expect(onDraftChange).not.toHaveBeenCalledWith("过期的转写A");
  });
});
