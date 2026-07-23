import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AccountSettings } from "./AccountSettings";
import type { TtsPreferences, UserProfileSettings } from "../api";

// A fully controllable fake Audio -- see InlineAudioPlayer.test.tsx for why jsdom's real
// HTMLMediaElement.play() (a stub returning undefined) cannot exercise these assertions.
class FakeAudio {
  static instances: FakeAudio[] = [];
  src: string;
  play: ReturnType<typeof vi.fn>;
  pause = vi.fn();
  constructor(src: string) { this.src = src; this.play = vi.fn(() => Promise.resolve()); FakeAudio.instances.push(this); }
  addEventListener() { /* no-op: these tests only assert on play() */ }
  removeEventListener() { /* no-op */ }
}

beforeEach(() => { FakeAudio.instances = []; vi.stubGlobal("Audio", FakeAudio); });
afterEach(cleanup);
afterEach(() => vi.unstubAllGlobals());

const profile: UserProfileSettings = {
  id: 1, username: "demo", nickname: "demo", role: "USER",
  auroraName: null, auroraTone: "温柔安静", preferredInputType: null,
  socialReachabilityStatus: "PRIVATE", bio: null, reflectionDepth: 3,
  allowMemoryRecall: true, quietHoursStart: "22:00", quietHoursEnd: "07:00",
  proactiveSensitivity: 2, allowMultiMessage: true, focusModeEnabled: false,
  focusWindowsJson: null, currentEnvironmentLabel: null,
  weatherAwarenessEnabled: true, timeAwarenessEnabled: true, timezone: "Asia/Shanghai"
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(res => { resolve = res; });
  return { promise, resolve };
}

describe("AccountSettings", () => {
  it("triggers data export directly, without a confirmation form", () => {
    const onExportData = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={onExportData} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "导出数据" }));
    expect(onExportData).toHaveBeenCalledOnce();
  });

  it("validates password length and confirmation match before calling onChangePassword", () => {
    const onChangePassword = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    fireEvent.change(screen.getByPlaceholderText("当前密码"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "short" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(screen.getByText("新密码至少 8 位")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "longenough1" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "different1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(screen.getByText("两次输入的新密码不一致")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();
  });

  // Gemini audit 4.10 (CONFIRMED/P1): the form used to call onChangePassword and then close/clear
  // ITSELF synchronously, before the async result was known -- a slow or failing request could
  // "appear to have succeeded" (form already closed) or leave the user unable to retry with their
  // original input. The form must now stay open (and disabled) until the promise resolves, and
  // must only close/clear on a CONFIRMED success.
  it("keeps the password form open and disabled while the change is in flight, and only closes/clears on confirmed success", async () => {
    const pending = deferred<string | null>();
    const onChangePassword = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    fireEvent.change(screen.getByPlaceholderText("当前密码"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "longenough1" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "longenough1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(onChangePassword).toHaveBeenCalledExactlyOnceWith("old-pass", "longenough1");

    // Still in flight: the form must NOT have closed yet, and the confirm button must be disabled
    // (guards against a double-submit while the first request is still outstanding).
    expect(screen.getByPlaceholderText("当前密码")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("当前密码")).toHaveValue("old-pass");
    expect(screen.getByRole("button", { name: "确认修改" })).toBeDisabled();

    // A second click while still in flight must not call onChangePassword again.
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(onChangePassword).toHaveBeenCalledTimes(1);

    await act(async () => { pending.resolve(null); await pending.promise; });

    // Confirmed success -- NOW the form closes and clears.
    expect(screen.queryByPlaceholderText("当前密码")).not.toBeInTheDocument();
  });

  it("keeps the user's input and shows an inline focused error when the password change fails, instead of closing the form", async () => {
    const pending = deferred<string | null>();
    const onChangePassword = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    fireEvent.change(screen.getByPlaceholderText("当前密码"), { target: { value: "wrong-old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "longenough1" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "longenough1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));

    await act(async () => { pending.resolve("当前密码不正确"); await pending.promise; });

    // The form is STILL open with the user's original input intact -- nothing was silently wiped.
    expect(screen.getByPlaceholderText("当前密码")).toHaveValue("wrong-old-pass");
    expect(screen.getByPlaceholderText("新密码（至少 8 位）")).toHaveValue("longenough1");
    // An inline, focused error tied to the form -- not just a generic top-of-page banner.
    expect(screen.getByRole("alert")).toHaveTextContent("当前密码不正确");
    expect(screen.getByRole("button", { name: "确认修改" })).not.toBeDisabled();
  });

  it("requires a password before confirming account deletion, and shows the irreversibility warning", () => {
    const onDeleteAccount = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    expect(screen.getByText(/此操作不可撤销/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(screen.getByText("请输入密码以确认")).toBeVisible();
    expect(onDeleteAccount).not.toHaveBeenCalled();
  });

  // Gemini audit 4.10: the same async-safety contract applies to the destructive delete-account
  // confirmation dialog -- it must not close itself before the deletion is confirmed to have
  // actually happened.
  it("keeps the delete-confirmation dialog open and disabled while deletion is in flight, and only closes it after confirmed success", async () => {
    const pending = deferred<string | null>();
    const onDeleteAccount = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "my-real-password" } });
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(onDeleteAccount).toHaveBeenCalledExactlyOnceWith("my-real-password");

    expect(screen.getByPlaceholderText("密码")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认删除" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(onDeleteAccount).toHaveBeenCalledTimes(1); // no double-submit while in flight

    await act(async () => { pending.resolve(null); await pending.promise; });
    expect(screen.queryByPlaceholderText("密码")).not.toBeInTheDocument();
  });

  it("keeps the delete dialog open with the password intact and shows an inline error when deletion fails", async () => {
    const pending = deferred<string | null>();
    const onDeleteAccount = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "wrong-password" } });
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));

    await act(async () => { pending.resolve("密码不正确"); await pending.promise; });

    expect(screen.getByPlaceholderText("密码")).toHaveValue("wrong-password");
    expect(screen.getByRole("alert")).toHaveTextContent("密码不正确");
    expect(screen.getByRole("button", { name: "确认删除" })).not.toBeDisabled();
  });

  it("disables the busy action and shows a status message", () => {
    render(<AccountSettings busy="export" message="数据已导出" onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    expect(screen.getByRole("button", { name: "导出数据" })).toBeDisabled();
    expect(screen.getByText("数据已导出")).toBeVisible();
  });

  it("seeds Aurora preferences from the loaded profile and saves a full patch on demand", () => {
    const onSaveProfile = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)}
      profile={profile} profileBusy={false} onSaveProfile={onSaveProfile} />);

    expect(screen.getByLabelText("对话风格")).toHaveValue("温柔安静");
    expect(screen.getByLabelText("反思深度")).toHaveValue("3");
    expect(screen.getByLabelText("允许记忆回溯")).toBeChecked();
    expect(screen.getByLabelText("允许多条消息")).toBeChecked();
    expect(screen.getByLabelText("主动关心频率")).toHaveValue("2");
    expect(screen.getByLabelText("谁可以找到你")).toHaveValue("PRIVATE");
    expect(screen.getByLabelText("安静时段开始")).toHaveValue("22:00");
    expect(screen.getByLabelText("安静时段结束")).toHaveValue("07:00");
    expect(screen.getByLabelText("专注模式")).not.toBeChecked();
    expect(screen.getByLabelText("感知天气")).toBeChecked();
    expect(screen.getByLabelText("感知时间")).toBeChecked();

    fireEvent.change(screen.getByLabelText("对话风格"), { target: { value: "理性清晰" } });
    fireEvent.click(screen.getByLabelText("专注模式"));
    fireEvent.click(screen.getByRole("button", { name: "保存偏好设置" }));

    expect(onSaveProfile).toHaveBeenCalledExactlyOnceWith({
      auroraTone: "理性清晰", reflectionDepth: 3, allowMemoryRecall: true, allowMultiMessage: true,
      proactiveSensitivity: 2, socialReachabilityStatus: "PRIVATE",
      quietHoursStart: "22:00", quietHoursEnd: "07:00",
      focusModeEnabled: true, weatherAwarenessEnabled: true, timeAwarenessEnabled: true
    });
  });

  it("does not render the preferences panel before the profile has loaded", () => {
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    expect(screen.queryByLabelText("对话风格")).not.toBeInTheDocument();
  });

  it("renders in English and validates in English when locale is en-SG", () => {
    const onChangePassword = vi.fn();
    render(<AccountSettings locale="en-SG" busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    expect(screen.getByRole("heading", { name: "Account & data" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Change password" }));
    fireEvent.change(screen.getByPlaceholderText("Current password"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("New password (at least 8 characters)"), { target: { value: "short" } });
    fireEvent.change(screen.getByPlaceholderText("Re-enter new password"), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirm change" }));
    expect(screen.getByText("New password must be at least 8 characters")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();
  });
});

describe("AccountSettings -- W2 voice preferences", () => {
  const ttsPreferences: TtsPreferences = {
    voices: [
      { id: "warm-a", label: "温和 A", language: "zh", previewText: "你好，我在这里。" },
      { id: "calm-b", label: "沉静 B", language: "zh", previewText: "别急，我陪着你。" }
    ],
    currentVoiceId: "warm-a", innerVoiceEnabled: true, innerVoiceMode: "AMBIENT"
  };

  function renderVoiceSettings(overrides: Partial<{
    onUpdateTtsPreferences: (patch: Record<string, unknown>) => Promise<string | null>;
    onPreviewVoice: (voiceId: string) => Promise<string>;
  }> = {}) {
    const onUpdateTtsPreferences = overrides.onUpdateTtsPreferences ?? vi.fn().mockResolvedValue(null);
    const onPreviewVoice = overrides.onPreviewVoice ?? vi.fn().mockResolvedValue("data:audio/mpeg;base64,AAA");
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)}
      ttsPreferences={ttsPreferences} ttsBusy={false}
      onUpdateTtsPreferences={onUpdateTtsPreferences} onPreviewVoice={onPreviewVoice} />);
    return { onUpdateTtsPreferences, onPreviewVoice };
  }

  it("does not render the voice section before tts preferences have loaded", () => {
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    expect(screen.queryByText("Aurora 的声音")).not.toBeInTheDocument();
  });

  it("seeds the voice picker and delivery-mode radios from the loaded preferences", () => {
    renderVoiceSettings();
    expect(screen.getByLabelText("开启内心独白")).toBeChecked();
    expect((screen.getByLabelText("自动播放 - 心声出现时自动轻声念出") as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText("点按播放 - 轻触后才展开并播放") as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText("温和 A") as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText("沉静 B") as HTMLInputElement).checked).toBe(false);
  });

  // (a) toggling delivery mode calls PATCH with the right body.
  it("calls PATCH with the right body when the delivery mode is toggled", () => {
    const { onUpdateTtsPreferences } = renderVoiceSettings();
    fireEvent.click(screen.getByLabelText("点按播放 - 轻触后才展开并播放"));
    expect(onUpdateTtsPreferences).toHaveBeenCalledExactlyOnceWith({ innerVoiceMode: "ON_DEMAND" });
  });

  it("calls PATCH with the right body when a different voice preset is picked", () => {
    const { onUpdateTtsPreferences } = renderVoiceSettings();
    fireEvent.click(screen.getByLabelText("沉静 B"));
    expect(onUpdateTtsPreferences).toHaveBeenCalledExactlyOnceWith({ voiceId: "calm-b" });
  });

  it("calls PATCH with the right body when the overall mute switch is toggled", () => {
    const { onUpdateTtsPreferences } = renderVoiceSettings();
    fireEvent.click(screen.getByLabelText("开启内心独白"));
    expect(onUpdateTtsPreferences).toHaveBeenCalledExactlyOnceWith({ innerVoiceEnabled: false });
  });

  // (b) a failed PATCH preserves the previous UI selection and shows an inline error, rather than
  // silently reverting or crashing.
  it("preserves the previous delivery-mode selection and shows an inline error when the PATCH fails", async () => {
    const pending = deferred<string | null>();
    renderVoiceSettings({ onUpdateTtsPreferences: vi.fn().mockReturnValue(pending.promise) });
    const ambient = screen.getByLabelText("自动播放 - 心声出现时自动轻声念出") as HTMLInputElement;
    const onDemand = screen.getByLabelText("点按播放 - 轻触后才展开并播放") as HTMLInputElement;
    fireEvent.click(onDemand);

    await act(async () => { pending.resolve("网络错误，暂时无法保存"); await pending.promise; });

    expect(screen.getByRole("alert")).toHaveTextContent("网络错误，暂时无法保存");
    // Rolled back to the last server-confirmed selection, with a visible reason -- not a silent,
    // unexplained snap-back and not a crash.
    expect(ambient.checked).toBe(true);
    expect(onDemand.checked).toBe(false);
  });

  it("keeps a successful selection applied without any error banner", async () => {
    const onUpdateTtsPreferences = vi.fn().mockResolvedValue(null);
    renderVoiceSettings({ onUpdateTtsPreferences });
    const onDemand = screen.getByLabelText("点按播放 - 轻触后才展开并播放") as HTMLInputElement;
    fireEvent.click(onDemand);
    await waitFor(() => expect(onUpdateTtsPreferences).toHaveBeenCalledOnce());
    expect(onDemand.checked).toBe(true);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  // (c) the preview button actually attempts to play audio -- asserted on the mocked Audio.play
  // call, not merely that a request was made.
  it("attempts to play audio through the shared InlineAudioPlayer when a preview succeeds", async () => {
    const { onPreviewVoice } = renderVoiceSettings();
    fireEvent.click(screen.getAllByRole("button", { name: "▶ 试听" })[0]);
    await waitFor(() => expect(onPreviewVoice).toHaveBeenCalledWith("warm-a"));
    await waitFor(() => expect(FakeAudio.instances.length).toBeGreaterThan(0));
    expect(FakeAudio.instances[0].play).toHaveBeenCalledOnce();
  });

  it("disables the preview button while a preview request is in flight, and re-enables it with an inline error on failure", async () => {
    let rejectPreview!: (error: Error) => void;
    const pendingPreview = new Promise<string>((_resolve, reject) => { rejectPreview = reject; });
    // A rejection is intentionally left unhandled until the assertions below run; suppress the
    // Node "unhandled rejection" warning this otherwise prints between attaching the promise here
    // and the component's own .catch() picking it up on the next microtask.
    pendingPreview.catch(() => undefined);
    renderVoiceSettings({ onPreviewVoice: vi.fn().mockReturnValue(pendingPreview) });
    const button = screen.getAllByRole("button", { name: "▶ 试听" })[0];
    fireEvent.click(button);
    expect(button).toBeDisabled();

    await act(async () => { rejectPreview(new Error("试听服务暂时不可用")); await pendingPreview.catch(() => undefined); });

    expect(screen.getByRole("alert")).toHaveTextContent("试听服务暂时不可用");
    expect(button).not.toBeDisabled();
  });
});
