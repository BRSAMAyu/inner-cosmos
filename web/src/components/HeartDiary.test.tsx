import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HeartDiary } from "./HeartDiary";

afterEach(cleanup);

function baseProps() {
  return {
    rawText: "", displayText: "", activeLevel: 0, polishBusy: false, submitBusy: false,
    onTextChange: vi.fn(), onSwitchLevel: vi.fn(), onTranscribeAudio: vi.fn().mockResolvedValue(undefined),
    onSubmit: vi.fn()
  };
}

describe("HeartDiary", () => {
  it("renders the writing textarea and does not yet show polish tabs for short/empty text", () => {
    render(<HeartDiary {...baseProps()} />);
    expect(screen.getByLabelText("心声日记正文")).toBeVisible();
    expect(screen.queryByRole("tablist", { name: "润色档位" })).not.toBeInTheDocument();
  });

  it("calls onTextChange as the user types", () => {
    const onTextChange = vi.fn();
    render(<HeartDiary {...baseProps()} onTextChange={onTextChange} />);
    fireEvent.change(screen.getByLabelText("心声日记正文"), { target: { value: "今天很累" } });
    expect(onTextChange).toHaveBeenCalledExactlyOnceWith("今天很累");
  });

  it("shows polish tabs once there is enough raw text, and switches levels", () => {
    const onSwitchLevel = vi.fn();
    render(<HeartDiary {...baseProps()} rawText="今天发生了很多事情" displayText="今天发生了很多事情" onSwitchLevel={onSwitchLevel} />);
    const tabs = screen.getByRole("tablist", { name: "润色档位" });
    expect(tabs).toBeVisible();
    fireEvent.click(screen.getByRole("tab", { name: "梳理" }));
    expect(onSwitchLevel).toHaveBeenCalledExactlyOnceWith(2);
  });

  it("shows the level description for the active level", () => {
    render(<HeartDiary {...baseProps()} rawText="今天发生了很多事情" displayText="整理后的内容" activeLevel={2} />);
    expect(screen.getByText(/修正乱序表达/)).toBeVisible();
  });

  it("shows a polishing indicator while polishBusy", () => {
    render(<HeartDiary {...baseProps()} rawText="今天发生了很多事情" displayText="今天发生了很多事情" polishBusy={true} />);
    expect(screen.getByText(/正在凝神/)).toBeVisible();
  });

  it("disables submit for content under 5 characters and enables it otherwise", () => {
    const { rerender } = render(<HeartDiary {...baseProps()} displayText="短" />);
    expect(screen.getByRole("button", { name: "将心声化作记忆星宿" })).toBeDisabled();
    rerender(<HeartDiary {...baseProps()} displayText="今天发生了很多事情" />);
    expect(screen.getByRole("button", { name: "将心声化作记忆星宿" })).not.toBeDisabled();
  });

  it("calls onSubmit when the submit button is clicked", () => {
    const onSubmit = vi.fn();
    render(<HeartDiary {...baseProps()} displayText="今天发生了很多事情" onSubmit={onSubmit} />);
    fireEvent.click(screen.getByRole("button", { name: "将心声化作记忆星宿" }));
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it("renders in English when locale is en-SG", () => {
    render(<HeartDiary {...baseProps()} locale="en-SG" />);
    expect(screen.getByLabelText("Heart diary entry")).toBeVisible();
  });

  // The audio-capture path (PcmWavRecorder start/stop/transcribe) is exercised by
  // AuroraConversation.test.tsx, which already stubs navigator.mediaDevices for that shared
  // recorder. In jsdom's default environment (no mediaDevices/AudioContext), voiceSupported is
  // false, so this asserts the same honest fallback that component already establishes.
  it("hides the recording button when voice capture is unsupported (jsdom default)", () => {
    render(<HeartDiary {...baseProps()} />);
    expect(screen.queryByRole("button", { name: /开始倾诉录音/ })).not.toBeInTheDocument();
  });
});
