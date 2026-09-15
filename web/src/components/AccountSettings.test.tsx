import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AccountSettings } from "./AccountSettings";
import type { AgeVerificationStatusView, TtsPreferences, UserProfileSettings } from "../api";

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
    expect(screen.getByText("设置", { selector: ".eyebrow" })).toBeVisible();
    expect(screen.queryByText("ACCOUNT & DATA")).not.toBeInTheDocument();
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
    expect(screen.getByText("Aurora 偏好").closest("details")).not.toHaveAttribute("open");

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
    expect(screen.getByRole("heading", { name: "Aurora & account settings" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Change password" }));
    fireEvent.change(screen.getByPlaceholderText("Current password"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("New password (at least 8 characters)"), { target: { value: "short" } });
    fireEvent.change(screen.getByPlaceholderText("Re-enter new password"), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirm change" }));
    expect(screen.getByText("New password must be at least 8 characters")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();
  });

  it("localizes native quiet-hour controls in English mode", () => {
    render(<AccountSettings locale="en-SG" busy={null} message={null}
      onChangePassword={() => Promise.resolve(null)} onExportData={() => undefined}
      onDeleteAccount={() => Promise.resolve(null)} profile={profile} onSaveProfile={() => undefined} />);
    expect(screen.getByLabelText("Quiet hours start")).toHaveAttribute("lang", "en-SG");
    expect(screen.getByLabelText("Quiet hours end")).toHaveAttribute("lang", "en-SG");
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

  it("seeds the compact voice and delivery-mode selects from the loaded preferences", () => {
    renderVoiceSettings();
    expect(screen.getByText("Aurora 的声音").closest("details")).toHaveAttribute("open");
    expect(screen.getByLabelText("允许对话外的心声")).toBeChecked();
    expect(screen.getByLabelText("浮现方式")).toHaveValue("AMBIENT");
    expect(screen.getByLabelText("选择音色")).toHaveValue("warm-a");
    expect(screen.getAllByRole("button", { name: "▶ 试听" })).toHaveLength(1);
  });

  it("derives natural English voice names from stable ids instead of backend Chinese labels", () => {
    const productionVoices: TtsPreferences = {
      ...ttsPreferences,
      voices: [
        { id: "warm_gentle_female", label: "温柔女声 · 小春", language: "zh", previewText: "你好。" },
        { id: "deep_soothing_male", label: "低沉男声 · 成然", language: "zh", previewText: "我在。" },
        { id: "future_voice_c", label: "未来中文标签", language: "zh", previewText: "你好。" }
      ],
      currentVoiceId: "warm_gentle_female"
    };
    render(<AccountSettings locale="en-SG" busy={null} message={null}
      onChangePassword={() => Promise.resolve(null)} onExportData={() => undefined}
      onDeleteAccount={() => Promise.resolve(null)} ttsPreferences={productionVoices}
      onUpdateTtsPreferences={() => Promise.resolve(null)}
      onPreviewVoice={() => Promise.resolve("data:audio/mpeg;base64,AAA")} />);

    expect(screen.getByLabelText("Choose a voice")).toHaveValue("warm_gentle_female");
    expect(screen.getByRole("option", { name: "Warm & gentle · Xiaochun" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Deep & soothing · Chengran" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Future Voice C" })).toBeInTheDocument();
    expect(screen.queryByText(/温柔女声|低沉男声|未来中文标签/)).not.toBeInTheDocument();
  });

  // (a) toggling delivery mode calls PATCH with the right body.
  it("calls PATCH with the right body when the delivery mode is toggled", () => {
    const { onUpdateTtsPreferences } = renderVoiceSettings();
    fireEvent.change(screen.getByLabelText("浮现方式"), { target: { value: "ON_DEMAND" } });
    expect(onUpdateTtsPreferences).toHaveBeenCalledExactlyOnceWith({ innerVoiceMode: "ON_DEMAND" });
  });

  it("calls PATCH with the right body when a different voice preset is picked", () => {
    const { onUpdateTtsPreferences } = renderVoiceSettings();
    fireEvent.change(screen.getByLabelText("选择音色"), { target: { value: "calm-b" } });
    expect(onUpdateTtsPreferences).toHaveBeenCalledExactlyOnceWith({ voiceId: "calm-b" });
  });

  it("rolls a failed voice change back to the last saved voice and explains why", async () => {
    const pending = deferred<string | null>();
    renderVoiceSettings({ onUpdateTtsPreferences: vi.fn().mockReturnValue(pending.promise) });
    const voiceSelect = screen.getByLabelText("选择音色");
    fireEvent.change(voiceSelect, { target: { value: "calm-b" } });
    expect(voiceSelect).toHaveValue("calm-b");
    await act(async () => { pending.resolve("声线未能保存"); await pending.promise; });
    expect(voiceSelect).toHaveValue("warm-a");
    expect(screen.getByRole("alert")).toHaveTextContent("声线未能保存");
  });

  it("calls PATCH with the right body when the overall mute switch is toggled", () => {
    const { onUpdateTtsPreferences } = renderVoiceSettings();
    fireEvent.click(screen.getByLabelText("允许对话外的心声"));
    expect(onUpdateTtsPreferences).toHaveBeenCalledExactlyOnceWith({ innerVoiceEnabled: false });
    expect(screen.getByLabelText("选择音色")).not.toBeDisabled();
  });

  // (b) a failed PATCH preserves the previous UI selection and shows an inline error, rather than
  // silently reverting or crashing.
  it("preserves the previous delivery-mode selection and shows an inline error when the PATCH fails", async () => {
    const pending = deferred<string | null>();
    renderVoiceSettings({ onUpdateTtsPreferences: vi.fn().mockReturnValue(pending.promise) });
    const modeSelect = screen.getByLabelText("浮现方式");
    fireEvent.change(modeSelect, { target: { value: "ON_DEMAND" } });

    await act(async () => { pending.resolve("网络错误，暂时无法保存"); await pending.promise; });

    expect(screen.getByRole("alert")).toHaveTextContent("网络错误，暂时无法保存");
    // Rolled back to the last server-confirmed selection, with a visible reason -- not a silent,
    // unexplained snap-back and not a crash.
    expect(modeSelect).toHaveValue("AMBIENT");
  });

  it("keeps a successful selection applied without any error banner", async () => {
    const onUpdateTtsPreferences = vi.fn().mockResolvedValue(null);
    renderVoiceSettings({ onUpdateTtsPreferences });
    const modeSelect = screen.getByLabelText("浮现方式");
    fireEvent.change(modeSelect, { target: { value: "ON_DEMAND" } });
    await waitFor(() => expect(onUpdateTtsPreferences).toHaveBeenCalledOnce());
    expect(modeSelect).toHaveValue("ON_DEMAND");
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

describe("AccountSettings -- CP-13 VERIFIED_ID badge", () => {
  const status = (overrides: Partial<AgeVerificationStatusView> = {}): AgeVerificationStatusView => ({
    ageGateMethod: "SELF_DECLARED", birthDate: null, latestStatus: null,
    latestFailureReason: null, history: [], ...overrides
  });
  const base = {
    busy: null, message: null,
    onChangePassword: () => Promise.resolve(null),
    onExportData: () => undefined,
    onDeleteAccount: () => Promise.resolve(null)
  } as const;

  afterEach(cleanup);

  it("shows the verified badge only from the backend's own VERIFIED_ID fact", async () => {
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.resolve(status({
      ageGateMethod: "VERIFIED_ID", birthDate: "1995-06-01", latestStatus: "VERIFIED"
    }))} />);
    expect(await screen.findByText("实名已验证（VERIFIED_ID）")).toBeVisible();
    expect(screen.queryByText("未完成实名")).not.toBeInTheDocument();
    const wrap = document.querySelector(".identity-badge-wrap")!;
    expect(wrap).toHaveAttribute("data-verified", "true");
  });

  it("a VERIFIED history row on a non-upgraded account (minor-intercept path) never shows verified", async () => {
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.resolve(status({
      latestStatus: "VERIFIED",
      history: [{ method: "OPERATOR_SMS", provider: "sandbox", status: "VERIFIED",
        verifiedBirthDate: "2012-01-01", createdAt: "2026-09-01T10:00:00" }]
    }))} />);
    expect(await screen.findByText("未完成实名")).toBeVisible();
    expect(screen.queryByText(/实名已验证/)).not.toBeInTheDocument();
    expect(document.querySelector(".identity-badge-wrap")).toHaveAttribute("data-verified", "false");
  });

  it("unverified stays neutral with a hint, and surfaces the backend's PENDING fact", async () => {
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.resolve(status({
      latestStatus: "PENDING"
    }))} />);
    expect(await screen.findByText("未完成实名")).toBeVisible();
    expect(screen.getByText(/完成实名年龄核验后，这里会显示已验证徽章/)).toBeVisible();
    expect(screen.getByText("有一次核验正在进行中，完成后这里会更新。")).toBeVisible();
  });

  it("shows the backend's own failure reason after a rejected attempt, still without shaming", async () => {
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.resolve(status({
      latestStatus: "REJECTED", latestFailureReason: "验证码不匹配"
    }))} />);
    expect(await screen.findByText("最近一次核验未通过：验证码不匹配")).toBeVisible();
    expect(screen.queryByText(/实名已验证/)).not.toBeInTheDocument();
  });

  it("renders nothing before the status resolves — no premature unverified flash", async () => {
    let resolveStatus!: (value: AgeVerificationStatusView) => void;
    const pending = new Promise<AgeVerificationStatusView>(resolve => { resolveStatus = resolve; });
    render(<AccountSettings {...base} identityStatusLoader={() => pending} />);
    expect(document.querySelector(".identity-badge-wrap")).toBeNull();
    expect(screen.queryByText("未完成实名")).not.toBeInTheDocument();
    // After resolution the badge appears from the real fact.
    await act(async () => { resolveStatus(status({ ageGateMethod: "VERIFIED_ID" })); await pending; });
    expect(screen.getByText("实名已验证（VERIFIED_ID）")).toBeVisible();
  });

  it("a failed load shows a neutral unavailable line with a working retry, never a guessed state", async () => {
    const loader = vi.fn().mockRejectedValue(new Error("offline"));
    render(<AccountSettings {...base} identityStatusLoader={loader} />);
    expect(await screen.findByText("实名状态暂时无法获取。")).toBeVisible();
    expect(screen.queryByText("未完成实名")).not.toBeInTheDocument();
    expect(document.querySelector(".identity-badge-wrap")).toHaveAttribute("data-verified", "unknown");
    loader.mockResolvedValue(status({ ageGateMethod: "VERIFIED_ID" }));
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    expect(await screen.findByText("实名已验证（VERIFIED_ID）")).toBeVisible();
    expect(loader).toHaveBeenCalledTimes(2);
  });

  it("renders the badge states in English", async () => {
    render(<AccountSettings {...base} locale="en-SG"
      identityStatusLoader={() => Promise.resolve(status())} />);
    expect(await screen.findByText("Identity verification not completed")).toBeVisible();
    expect(screen.getByText(/Once you complete identity \(age\) verification/)).toBeVisible();
  });
});

describe("AccountSettings -- CP-18 opening-recap re-entry", () => {
  const base = {
    busy: null, message: null,
    onChangePassword: () => Promise.resolve(null),
    onExportData: () => undefined,
    onDeleteAccount: () => Promise.resolve(null)
  } as const;

  afterEach(cleanup);

  // The one durable UI path back after the opening card's own withdraw button disappears with
  // the card: the settings row must reflect the owner's REAL switch value, never a guessed one.
  it("seeds the checkbox from the real GET value and states what 'off' honestly means", async () => {
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={() => Promise.resolve({ openingVisible: false })} />);
    expect(await screen.findByLabelText("显示开场回顾")).not.toBeChecked();
    expect(screen.getByText("不再显示开场回顾，连续性记录不受影响。")).toBeVisible();
  });

  it("renders nothing for the switch before the value resolves — no premature on/off flash", async () => {
    let resolveVisibility!: (value: { openingVisible: boolean }) => void;
    const pending = new Promise<{ openingVisible: boolean }>(resolve => { resolveVisibility = resolve; });
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={() => pending} />);
    expect(screen.queryByLabelText("显示开场回顾")).not.toBeInTheDocument();
    expect(screen.queryByText("开场回顾")).not.toBeInTheDocument();
    await act(async () => { resolveVisibility({ openingVisible: true }); await pending; });
    expect(screen.getByLabelText("显示开场回顾")).toBeChecked();
  });

  it("re-opens the recap via PUT(true) and keeps it on after the server confirms", async () => {
    const setContinuityVisibility = vi.fn().mockResolvedValue({ openingVisible: true });
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={() => Promise.resolve({ openingVisible: false })}
      setContinuityVisibility={setContinuityVisibility} />);
    const toggle = await screen.findByLabelText("显示开场回顾");
    fireEvent.click(toggle);
    expect(setContinuityVisibility).toHaveBeenCalledExactlyOnceWith(true);
    await waitFor(() => expect(screen.getByLabelText("显示开场回顾")).toBeChecked());
    expect(screen.getByText("开始新对话时，Aurora 会带来上次对话留下的开场回顾。")).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("withdraws again via PUT(false), showing the honest off-state copy", async () => {
    const setContinuityVisibility = vi.fn().mockResolvedValue({ openingVisible: false });
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={() => Promise.resolve({ openingVisible: true })}
      setContinuityVisibility={setContinuityVisibility} />);
    fireEvent.click(await screen.findByLabelText("显示开场回顾"));
    expect(setContinuityVisibility).toHaveBeenCalledExactlyOnceWith(false);
    await waitFor(() => expect(screen.getByText("不再显示开场回顾，连续性记录不受影响。")).toBeVisible());
  });

  it("rolls a failed toggle back to the last server-confirmed value and says why", async () => {
    let rejectSave!: (error: Error) => void;
    const pending = new Promise<{ openingVisible: boolean }>((_resolve, reject) => { rejectSave = reject; });
    // Rejection is consumed by the component's .catch; pre-attach a no-op to keep it from
    // surfacing as an unhandled rejection before that happens.
    pending.catch(() => undefined);
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={() => Promise.resolve({ openingVisible: false })}
      setContinuityVisibility={vi.fn().mockReturnValue(pending)} />);
    const toggle = await screen.findByLabelText("显示开场回顾");
    fireEvent.click(toggle);
    expect(toggle).toBeChecked(); // optimistic while in flight
    await act(async () => { rejectSave(new Error("network down")); await pending.catch(() => undefined); });
    expect(screen.getByLabelText("显示开场回顾")).not.toBeChecked(); // rolled back
    expect(screen.getByRole("alert")).toHaveTextContent("暂时没能保存，稍后再试。");
  });

  it("a failed load shows a neutral unavailable line with a working retry, never a guessed state", async () => {
    const loader = vi.fn().mockRejectedValue(new Error("offline"));
    render(<AccountSettings {...base} identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={loader} />);
    expect(await screen.findByText("开场回顾设置暂时无法获取。")).toBeVisible();
    expect(screen.queryByLabelText("显示开场回顾")).not.toBeInTheDocument();
    loader.mockResolvedValue({ openingVisible: true });
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByLabelText("显示开场回顾")).toBeChecked();
    expect(loader).toHaveBeenCalledTimes(2);
  });

  it("renders the switch in English when locale is en-SG", async () => {
    render(<AccountSettings {...base} locale="en-SG"
      identityStatusLoader={() => Promise.reject(new Error("offline"))}
      continuityVisibilityLoader={() => Promise.resolve({ openingVisible: false })} />);
    expect(await screen.findByLabelText("Show the opening recap")).not.toBeChecked();
    expect(screen.getByText("The opening recap is off; your continuity records are unaffected.")).toBeVisible();
  });
});
