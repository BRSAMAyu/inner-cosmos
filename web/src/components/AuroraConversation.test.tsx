import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuroraConversation } from "./AuroraConversation";

afterEach(cleanup);
afterEach(() => { delete (navigator as unknown as Record<string, unknown>).mediaDevices; delete (globalThis as Record<string, unknown>).MediaRecorder; });

describe("AuroraConversation", () => {
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

  it("hides the mic button when voice capture is unsupported", () => {
    render(<AuroraConversation messages={[]} activeTurnId={null} draft="" sessionReady
      onDraftChange={() => undefined} onSubmit={event => event.preventDefault()} onStop={() => undefined}
      onTranscribe={async () => "x"} />);
    expect(screen.queryByRole("button", { name: "用语音输入" })).not.toBeInTheDocument();
  });

  it("records voice, transcribes on stop, and appends the text to the draft", async () => {
    let rec: { ondataavailable: ((e: { data: Blob }) => void) | null; onstop: (() => void) | null } | null = null;
    class FakeRecorder {
      ondataavailable: ((e: { data: Blob }) => void) | null = null;
      onstop: (() => void) | null = null;
      mimeType = "audio/webm";
      constructor() { rec = this; }
      start() { /* no-op */ }
      stop() { this.ondataavailable?.({ data: new Blob(["x"], { type: "audio/webm" }) }); this.onstop?.(); }
    }
    (globalThis as Record<string, unknown>).MediaRecorder = FakeRecorder;
    const getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] });
    (navigator as unknown as Record<string, unknown>).mediaDevices = { getUserMedia };
    const onDraftChange = vi.fn();
    const onTranscribe = vi.fn().mockResolvedValue("语音转出来的文字");
    render(<AuroraConversation messages={[]} activeTurnId={null} draft="" sessionReady
      onDraftChange={onDraftChange} onSubmit={event => event.preventDefault()} onStop={() => undefined}
      onTranscribe={onTranscribe} />);
    fireEvent.click(screen.getByRole("button", { name: "用语音输入" }));
    await waitFor(() => expect(getUserMedia).toHaveBeenCalled());
    fireEvent.click(await screen.findByRole("button", { name: "停止录音并转写" }));
    await waitFor(() => expect(onTranscribe).toHaveBeenCalled());
    await waitFor(() => expect(onDraftChange).toHaveBeenCalledWith("语音转出来的文字"));
    expect(rec).not.toBeNull();
  });
});
