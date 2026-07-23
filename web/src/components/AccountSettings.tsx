import { useRef, useState } from "react";
import { AsyncButton } from "../loading";
import type { Locale } from "../i18n";
import type { TtsPreferences, TtsPreferencesPatch, UserProfileSettings } from "../api";
import { InlineAudioPlayer } from "./shared/InlineAudioPlayer";

export type AccountBusy = "password" | "export" | "delete" | null;

const toneOrder = ["", "温柔安静", "理性清晰", "朋友式直接", "哲学式追问", "行动导向"] as const;
const reachabilityOrder = ["PUBLIC", "PRIVATE"] as const;
const innerVoiceModeOrder = ["AMBIENT", "ON_DEMAND"] as const;

type AccountCopy = {
  aria: string; heading: string; exportTitle: string; exportDesc: string; exportBusy: string; exportBtn: string;
  pwTitle: string; pwOpen: string; pwCurrent: string; pwNew: string; pwNew2: string; cancel: string;
  pwBusy: string; pwConfirm: string; errFill: string; errLen: string; errMismatch: string;
  delTitle: string; delOpen: string; delWarn: string; delWarnStrong: string; delPassword: string;
  delErrFill: string; delBusy: string; delConfirm: string;
  prefsTitle: string; prefsNote: string; toneLabel: string; tone: Record<string, string>;
  depthLabel: string; memoryRecallLabel: string; memoryRecallHint: string;
  multiMessageLabel: string; multiMessageHint: string; proactiveLabel: string; proactiveHint: string;
  reachabilityLabel: string; reachability: Record<string, string>;
  quietStartLabel: string; quietEndLabel: string; focusModeLabel: string; weatherLabel: string; timeAwareLabel: string;
  prefsSaveBusy: string; prefsSave: string;
  voiceTitle: string; voiceNote: string; voiceEnabledLabel: string; voiceEnabledHint: string;
  voiceModeLabel: string; voiceMode: Record<string, string>; voicePickLabel: string;
  voicePreview: string; voicePreviewBusy: string; voicePreviewError: string; voiceSaveError: string;
};

const COPY: Record<Locale, AccountCopy> = {
  "zh-CN": {
    aria: "账户与数据", heading: "账户与数据",
    exportTitle: "导出我的数据", exportDesc: "把你的记忆、画像、共鸣体和慢信打包成一份 JSON 文件。",
    exportBusy: "正在导出", exportBtn: "导出数据",
    pwTitle: "修改密码", pwOpen: "修改密码", pwCurrent: "当前密码", pwNew: "新密码（至少 8 位）",
    pwNew2: "再次输入新密码", cancel: "取消", pwBusy: "正在修改", pwConfirm: "确认修改",
    errFill: "请填写当前密码和新密码", errLen: "新密码至少 8 位", errMismatch: "两次输入的新密码不一致",
    delTitle: "删除账户", delOpen: "删除账户",
    delWarn: "这将永久删除你的账户和所有数据，包括记忆星体、每日记录、待办事项、慢信和共鸣体。",
    delWarnStrong: "此操作不可撤销。", delPassword: "密码", delErrFill: "请输入密码以确认",
    delBusy: "正在删除", delConfirm: "确认删除",
    prefsTitle: "Aurora 偏好", prefsNote: "这些设置只影响 Aurora 如何回应和主动联系你，不会被其他人看到。",
    toneLabel: "对话风格",
    tone: { "": "默认", "温柔安静": "温柔安静", "理性清晰": "理性清晰", "朋友式直接": "朋友式直接", "哲学式追问": "哲学式追问", "行动导向": "行动导向" },
    depthLabel: "反思深度", memoryRecallLabel: "允许记忆回溯", memoryRecallHint: "Aurora 可以在对话中引用你之前的记忆",
    multiMessageLabel: "允许多条消息", multiMessageHint: "Aurora 可以像朋友一样在必要时连续补充 2-3 条短消息",
    proactiveLabel: "主动关心频率", proactiveHint: "1 表示尽量不打扰，5 表示更主动地来找你。",
    reachabilityLabel: "谁可以找到你", reachability: { PUBLIC: "公开 - 其他人可以通过慢信找到你", PRIVATE: "私密 - 只有你知道自己的存在" },
    quietStartLabel: "安静时段开始", quietEndLabel: "安静时段结束",
    focusModeLabel: "专注模式", weatherLabel: "感知天气", timeAwareLabel: "感知时间",
    prefsSaveBusy: "保存中…", prefsSave: "保存偏好设置",
    voiceTitle: "Aurora 的声音", voiceNote: "Aurora 会偶尔把她正在想的事情，用一句真正的心声念给你听——和正常回复不一样。",
    voiceEnabledLabel: "开启内心独白", voiceEnabledHint: "关闭后，Aurora 不会再念出心声（仍然只在她说话时才有）。",
    voiceModeLabel: "播放方式",
    voiceMode: { AMBIENT: "自动播放 - 心声出现时自动轻声念出", ON_DEMAND: "点按播放 - 轻触后才展开并播放" },
    voicePickLabel: "选择音色", voicePreview: "▶ 试听", voicePreviewBusy: "试听中…",
    voicePreviewError: "试听暂时失败，请再试一次", voiceSaveError: "语音偏好未能保存"
  },
  "en-SG": {
    aria: "Account & data", heading: "Account & data",
    exportTitle: "Export my data", exportDesc: "Package your memories, portrait, capsules and slow letters into one JSON file.",
    exportBusy: "Exporting", exportBtn: "Export data",
    pwTitle: "Change password", pwOpen: "Change password", pwCurrent: "Current password", pwNew: "New password (at least 8 characters)",
    pwNew2: "Re-enter new password", cancel: "Cancel", pwBusy: "Changing", pwConfirm: "Confirm change",
    errFill: "Enter your current and new password", errLen: "New password must be at least 8 characters", errMismatch: "The two new passwords do not match",
    delTitle: "Delete account", delOpen: "Delete account",
    delWarn: "This permanently deletes your account and all data — memory stars, daily records, to-dos, slow letters and capsules.",
    delWarnStrong: "This cannot be undone.", delPassword: "Password", delErrFill: "Enter your password to confirm",
    delBusy: "Deleting", delConfirm: "Confirm deletion",
    prefsTitle: "Aurora preferences", prefsNote: "These only affect how Aurora responds and reaches out to you; no one else can see them.",
    toneLabel: "Conversation style",
    tone: { "": "Default", "温柔安静": "Gentle & quiet", "理性清晰": "Rational & clear", "朋友式直接": "Friend-direct", "哲学式追问": "Philosophical", "行动导向": "Action-oriented" },
    depthLabel: "Reflection depth", memoryRecallLabel: "Allow memory recall", memoryRecallHint: "Aurora may reference your past memories in conversation",
    multiMessageLabel: "Allow multi-message replies", multiMessageHint: "Aurora may send 2-3 short follow-up messages like a friend would, when needed",
    proactiveLabel: "Proactive care frequency", proactiveHint: "1 means rarely reach out; 5 means reach out more actively.",
    reachabilityLabel: "Who can find you", reachability: { PUBLIC: "Public - others can find you via a slow letter", PRIVATE: "Private - only you know you exist" },
    quietStartLabel: "Quiet hours start", quietEndLabel: "Quiet hours end",
    focusModeLabel: "Focus mode", weatherLabel: "Weather awareness", timeAwareLabel: "Time awareness",
    prefsSaveBusy: "Saving…", prefsSave: "Save preferences",
    voiceTitle: "Aurora's voice", voiceNote: "Aurora will occasionally speak a genuine inner-monologue line about what she's thinking -- distinct from her normal reply.",
    voiceEnabledLabel: "Enable inner voice", voiceEnabledHint: "When off, Aurora never speaks an inner-voice line (it only ever plays alongside her own turns).",
    voiceModeLabel: "Delivery mode",
    voiceMode: { AMBIENT: "Ambient -- auto-plays quietly when an inner voice appears", ON_DEMAND: "On demand -- tap to reveal and play" },
    voicePickLabel: "Choose a voice", voicePreview: "▶ Preview", voicePreviewBusy: "Previewing…",
    voicePreviewError: "Preview failed -- please try again", voiceSaveError: "Voice preferences could not be saved"
  }
};

// Owner-private Aurora preference editor. Local state is seeded from the loaded profile and reset
// per profile via key={profile.id} (mirrors CapsuleWorkbench's CapsuleBoundaryEditor pattern).
function AuroraPreferencesEditor({ profile, profileBusy, onSaveProfile, t }: {
  profile: UserProfileSettings; profileBusy: boolean;
  onSaveProfile: (patch: Partial<UserProfileSettings>) => void; t: AccountCopy;
}) {
  const [tone, setTone] = useState(profile.auroraTone ?? "");
  const [depth, setDepth] = useState(profile.reflectionDepth ?? 3);
  const [memoryRecall, setMemoryRecall] = useState(profile.allowMemoryRecall ?? true);
  const [multiMessage, setMultiMessage] = useState(profile.allowMultiMessage ?? true);
  const [proactive, setProactive] = useState(profile.proactiveSensitivity ?? 3);
  const [reachability, setReachability] = useState(
    reachabilityOrder.includes(profile.socialReachabilityStatus as typeof reachabilityOrder[number])
      ? (profile.socialReachabilityStatus as string) : "PUBLIC");
  const [quietStart, setQuietStart] = useState(profile.quietHoursStart ?? "22:00");
  const [quietEnd, setQuietEnd] = useState(profile.quietHoursEnd ?? "07:00");
  const [focusMode, setFocusMode] = useState(profile.focusModeEnabled ?? false);
  const [weatherAware, setWeatherAware] = useState(profile.weatherAwarenessEnabled ?? true);
  const [timeAware, setTimeAware] = useState(profile.timeAwarenessEnabled ?? true);

  return <article className="account-preferences">
    <strong>{t.prefsTitle}</strong>
    <p className="muted">{t.prefsNote}</p>
    <label>{t.toneLabel}<select value={tone} onChange={event => setTone(event.target.value)}>
      {toneOrder.map(value => <option key={value} value={value}>{t.tone[value]}</option>)}</select></label>
    <label>{t.depthLabel}<input type="range" min={1} max={5} value={depth} onChange={event => setDepth(Number(event.target.value))} /></label>
    <div className="account-toggle"><label><input type="checkbox" checked={memoryRecall} onChange={event => setMemoryRecall(event.target.checked)} />
      {t.memoryRecallLabel}</label><small>{t.memoryRecallHint}</small></div>
    <div className="account-toggle"><label><input type="checkbox" checked={multiMessage} onChange={event => setMultiMessage(event.target.checked)} />
      {t.multiMessageLabel}</label><small>{t.multiMessageHint}</small></div>
    <label>{t.proactiveLabel}<input type="range" min={1} max={5} value={proactive} onChange={event => setProactive(Number(event.target.value))} /></label><small>{t.proactiveHint}</small>
    <label>{t.reachabilityLabel}<select value={reachability} onChange={event => setReachability(event.target.value)}>
      {reachabilityOrder.map(value => <option key={value} value={value}>{t.reachability[value]}</option>)}</select></label>
    <label>{t.quietStartLabel}<input type="time" value={quietStart} onChange={event => setQuietStart(event.target.value)} /></label>
    <label>{t.quietEndLabel}<input type="time" value={quietEnd} onChange={event => setQuietEnd(event.target.value)} /></label>
    <label className="account-toggle"><input type="checkbox" checked={focusMode} onChange={event => setFocusMode(event.target.checked)} />{t.focusModeLabel}</label>
    <label className="account-toggle"><input type="checkbox" checked={weatherAware} onChange={event => setWeatherAware(event.target.checked)} />{t.weatherLabel}</label>
    <label className="account-toggle"><input type="checkbox" checked={timeAware} onChange={event => setTimeAware(event.target.checked)} />{t.timeAwareLabel}</label>
    <AsyncButton busy={profileBusy} busyText={t.prefsSaveBusy} onClick={() => onSaveProfile({
      auroraTone: tone, reflectionDepth: depth, allowMemoryRecall: memoryRecall, allowMultiMessage: multiMessage,
      proactiveSensitivity: proactive, socialReachabilityStatus: reachability,
      quietHoursStart: quietStart, quietHoursEnd: quietEnd,
      focusModeEnabled: focusMode, weatherAwarenessEnabled: weatherAware, timeAwarenessEnabled: timeAware
    })}>{t.prefsSave}</AsyncButton>
  </article>;
}

// W2 voice feature: mirrors the password/delete-account async-lifecycle contract exactly --
// onUpdateTtsPreferences resolves to `null` on CONFIRMED success or an error-message string on
// failure. Every field (voice pick / delivery mode / mute) is its own instant-save control (no
// batched "Save" button, unlike AuroraPreferencesEditor above): each click optimistically updates
// the local selection so the control feels responsive, then AWAITS the PATCH before deciding
// whether to keep it (success) or roll back to the last server-confirmed value while showing an
// inline error (failure) -- never a silent, unexplained revert.
function VoicePreferencesEditor({ ttsPreferences, ttsBusy, onUpdateTtsPreferences, onPreviewVoice, locale, t }: {
  ttsPreferences: TtsPreferences; ttsBusy: boolean;
  onUpdateTtsPreferences: (patch: TtsPreferencesPatch) => Promise<string | null>;
  onPreviewVoice: (voiceId: string) => Promise<string>;
  locale: Locale; t: AccountCopy;
}) {
  const [voiceId, setVoiceId] = useState(ttsPreferences.currentVoiceId);
  const [mode, setMode] = useState<TtsPreferences["innerVoiceMode"]>(
    innerVoiceModeOrder.includes(ttsPreferences.innerVoiceMode) ? ttsPreferences.innerVoiceMode : "AMBIENT");
  const [enabled, setEnabled] = useState(ttsPreferences.innerVoiceEnabled);
  const [error, setError] = useState("");
  const [previewingId, setPreviewingId] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState("");
  const [previewAudio, setPreviewAudio] = useState<{ voiceId: string; audio: string; token: number } | null>(null);
  const previewTokenRef = useRef(0);

  // Applies a single-field patch: optimistic local update, awaited PATCH, roll back to the prior
  // confirmed value (`previous`) + inline error on failure -- the exact same "never silently
  // succeed or silently revert" contract as submitPassword/submitDelete above (null on confirmed
  // success, an error-message string on failure -- checked with === null, not truthiness, to match).
  const applyPatch = async <K extends keyof TtsPreferencesPatch>(
    field: K, value: NonNullable<TtsPreferencesPatch[K]>, previous: NonNullable<TtsPreferencesPatch[K]>,
    rollback: (value: NonNullable<TtsPreferencesPatch[K]>) => void
  ) => {
    setError("");
    const errorMessage = await onUpdateTtsPreferences({ [field]: value } as TtsPreferencesPatch);
    if (errorMessage !== null) { rollback(previous); setError(errorMessage || t.voiceSaveError); }
  };

  const changeVoice = (nextId: string) => {
    const previous = voiceId;
    setVoiceId(nextId);
    void applyPatch("voiceId", nextId, previous, setVoiceId);
  };
  const changeMode = (nextMode: TtsPreferences["innerVoiceMode"]) => {
    const previous = mode;
    setMode(nextMode);
    void applyPatch("innerVoiceMode", nextMode, previous, setMode);
  };
  const changeEnabled = (nextEnabled: boolean) => {
    const previous = enabled;
    setEnabled(nextEnabled);
    void applyPatch("innerVoiceEnabled", nextEnabled, previous, setEnabled);
  };

  const preview = async (id: string) => {
    if (previewingId) return; // one preview in flight at a time
    previewTokenRef.current += 1;
    const token = previewTokenRef.current;
    setPreviewingId(id);
    setPreviewError("");
    try {
      const audio = await onPreviewVoice(id);
      setPreviewAudio({ voiceId: id, audio, token });
    } catch (previewErrorCaught) {
      setPreviewError(previewErrorCaught instanceof Error ? previewErrorCaught.message : t.voicePreviewError);
    } finally {
      setPreviewingId(null);
    }
  };

  return <article className="account-preferences">
    <strong>{t.voiceTitle}</strong>
    <p className="muted">{t.voiceNote}</p>
    <div className="account-toggle"><label><input type="checkbox" checked={enabled}
      disabled={ttsBusy} onChange={event => changeEnabled(event.target.checked)} />
      {t.voiceEnabledLabel}</label><small>{t.voiceEnabledHint}</small></div>
    {error && <span className="voice-error" role="alert">{error}</span>}
    <fieldset disabled={!enabled}>
      <legend>{t.voiceModeLabel}</legend>
      {innerVoiceModeOrder.map(value => <label key={value} className="account-toggle">
        <input type="radio" name="inner-voice-mode" value={value} checked={mode === value}
          disabled={ttsBusy} onChange={() => changeMode(value)} />
        {t.voiceMode[value]}
      </label>)}
    </fieldset>
    <fieldset disabled={!enabled}>
      <legend>{t.voicePickLabel}</legend>
      <ul className="voice-preset-list">
        {ttsPreferences.voices.map(voice => <li key={voice.id} className="voice-preset-option">
          <label><input type="radio" name="inner-voice-preset" value={voice.id} checked={voiceId === voice.id}
            disabled={ttsBusy} onChange={() => changeVoice(voice.id)} />{voice.label}</label>
          <AsyncButton busy={previewingId === voice.id} busyText={t.voicePreviewBusy}
            disabled={previewingId !== null && previewingId !== voice.id}
            onClick={() => void preview(voice.id)}>{t.voicePreview}</AsyncButton>
          {previewAudio?.voiceId === voice.id && <InlineAudioPlayer key={previewAudio.token}
            audio={previewAudio.audio} autoPlay locale={locale} />}
        </li>)}
      </ul>
      {previewError && <span className="voice-error" role="alert">{previewError}</span>}
    </fieldset>
  </article>;
}

export function AccountSettings({ busy, message, onChangePassword, onExportData, onDeleteAccount,
  profile = null, profileBusy = false, onSaveProfile,
  ttsPreferences = null, ttsBusy = false, onUpdateTtsPreferences, onPreviewVoice, locale = "zh-CN" }: {
  busy: AccountBusy; message: string | null;
  // Gemini audit 4.10 (CONFIRMED/P1): both return a Promise resolving to `null` on confirmed
  // success or an error message string on failure -- the form below AWAITS this before deciding
  // whether to close/clear (success) or stay open with the error inline (failure). Returning void
  // synchronously (the old contract) was exactly what let the form close/clear before the async
  // result was known.
  onChangePassword: (oldPassword: string, newPassword: string) => Promise<string | null>;
  onExportData: () => void;
  onDeleteAccount: (password: string) => Promise<string | null>;
  profile?: UserProfileSettings | null; profileBusy?: boolean;
  onSaveProfile?: (patch: Partial<UserProfileSettings>) => void;
  // W2 voice feature: same null-on-success/message-on-failure contract as onChangePassword above.
  ttsPreferences?: TtsPreferences | null; ttsBusy?: boolean;
  onUpdateTtsPreferences?: (patch: TtsPreferencesPatch) => Promise<string | null>;
  onPreviewVoice?: (voiceId: string) => Promise<string>;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPassword2, setNewPassword2] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState("");
  const [deleteError, setDeleteError] = useState("");
  const [deleteSubmitting, setDeleteSubmitting] = useState(false);

  const closePasswordForm = () => { setPasswordOpen(false); setOldPassword(""); setNewPassword(""); setNewPassword2(""); setPasswordError(""); };
  const submitPassword = async () => {
    if (passwordSubmitting) return; // guard against a double-submit while one is already in flight
    if (!oldPassword || !newPassword) { setPasswordError(t.errFill); return; }
    if (newPassword.length < 8) { setPasswordError(t.errLen); return; }
    if (newPassword !== newPassword2) { setPasswordError(t.errMismatch); return; }
    setPasswordError("");
    setPasswordSubmitting(true);
    try {
      // Gemini audit 4.10: only close/clear the form on a CONFIRMED success (result === null).
      // A slow or failing request must never look like it succeeded, and a failure must keep the
      // user's already-typed input so they don't have to retype everything.
      const errorMessage = await onChangePassword(oldPassword, newPassword);
      if (errorMessage === null) closePasswordForm();
      else setPasswordError(errorMessage);
    } finally { setPasswordSubmitting(false); }
  };

  const closeDeleteForm = () => { setDeleteOpen(false); setDeletePassword(""); setDeleteError(""); };
  const submitDelete = async () => {
    if (deleteSubmitting) return; // guard against a double-submit while one is already in flight
    if (!deletePassword) { setDeleteError(t.delErrFill); return; }
    setDeleteError("");
    setDeleteSubmitting(true);
    try {
      // Gemini audit 4.10: same async-safe contract for the destructive delete-account dialog --
      // it must not close itself until deletion is confirmed to have actually happened.
      const errorMessage = await onDeleteAccount(deletePassword);
      if (errorMessage === null) closeDeleteForm();
      else setDeleteError(errorMessage);
    } finally { setDeleteSubmitting(false); }
  };

  return <section className="account-settings" aria-label={t.aria}>
    <span className="eyebrow">ACCOUNT &amp; DATA</span>
    <h2>{t.heading}</h2>
    {message && <p className="account-message">{message}</p>}
    <div className="account-actions-grid">
      <article>
        <strong>{t.exportTitle}</strong>
        <p className="muted">{t.exportDesc}</p>
        <AsyncButton busy={busy === "export"} busyText={t.exportBusy} onClick={onExportData}>{t.exportBtn}</AsyncButton>
      </article>

      <article>
        <strong>{t.pwTitle}</strong>
        {!passwordOpen ? <button type="button" onClick={() => setPasswordOpen(true)}>{t.pwOpen}</button> :
          <div className="account-form">
            <input type="password" placeholder={t.pwCurrent} autoComplete="current-password"
              value={oldPassword} onChange={event => setOldPassword(event.target.value)} />
            <input type="password" placeholder={t.pwNew} autoComplete="new-password"
              value={newPassword} onChange={event => setNewPassword(event.target.value)} />
            <input type="password" placeholder={t.pwNew2} autoComplete="new-password"
              value={newPassword2} onChange={event => setNewPassword2(event.target.value)} />
            {passwordError && <p className="account-error" role="alert">{passwordError}</p>}
            <div className="account-form-actions">
              <button type="button" onClick={closePasswordForm} disabled={passwordSubmitting}>{t.cancel}</button>
              <AsyncButton busy={busy === "password" || passwordSubmitting} busyText={t.pwBusy} onClick={() => void submitPassword()}>{t.pwConfirm}</AsyncButton>
            </div>
          </div>}
      </article>

      <article className="danger">
        <strong>{t.delTitle}</strong>
        {!deleteOpen ? <button type="button" className="danger-quiet" onClick={() => setDeleteOpen(true)}>{t.delOpen}</button> :
          <div className="account-form">
            <p className="account-warning">{t.delWarn}<strong>{t.delWarnStrong}</strong></p>
            <input type="password" placeholder={t.delPassword} autoComplete="current-password"
              value={deletePassword} onChange={event => setDeletePassword(event.target.value)} />
            {deleteError && <p className="account-error" role="alert">{deleteError}</p>}
            <div className="account-form-actions">
              <button type="button" onClick={closeDeleteForm} disabled={deleteSubmitting}>{t.cancel}</button>
              <AsyncButton busy={busy === "delete" || deleteSubmitting} busyText={t.delBusy} className="danger-quiet" onClick={() => void submitDelete()}>{t.delConfirm}</AsyncButton>
            </div>
          </div>}
      </article>

      {profile && onSaveProfile && <AuroraPreferencesEditor key={profile.id} profile={profile} profileBusy={profileBusy} onSaveProfile={onSaveProfile} t={t} />}
      {ttsPreferences && onUpdateTtsPreferences && onPreviewVoice &&
        <VoicePreferencesEditor ttsPreferences={ttsPreferences} ttsBusy={ttsBusy}
          onUpdateTtsPreferences={onUpdateTtsPreferences} onPreviewVoice={onPreviewVoice} locale={locale} t={t} />}
    </div>
  </section>;
}
