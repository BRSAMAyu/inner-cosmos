import { useState } from "react";
import { AsyncButton } from "../loading";
import type { Locale } from "../i18n";
import type { UserProfileSettings } from "../api";

export type AccountBusy = "password" | "export" | "delete" | null;

const toneOrder = ["", "温柔安静", "理性清晰", "朋友式直接", "哲学式追问", "行动导向"] as const;
const reachabilityOrder = ["PUBLIC", "PRIVATE"] as const;

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
    prefsSaveBusy: "保存中…", prefsSave: "保存偏好设置"
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
    prefsSaveBusy: "Saving…", prefsSave: "Save preferences"
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

export function AccountSettings({ busy, message, onChangePassword, onExportData, onDeleteAccount,
  profile = null, profileBusy = false, onSaveProfile, locale = "zh-CN" }: {
  busy: AccountBusy; message: string | null;
  onChangePassword: (oldPassword: string, newPassword: string) => void;
  onExportData: () => void;
  onDeleteAccount: (password: string) => void;
  profile?: UserProfileSettings | null; profileBusy?: boolean;
  onSaveProfile?: (patch: Partial<UserProfileSettings>) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPassword2, setNewPassword2] = useState("");
  const [passwordError, setPasswordError] = useState("");

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState("");
  const [deleteError, setDeleteError] = useState("");

  const closePasswordForm = () => { setPasswordOpen(false); setOldPassword(""); setNewPassword(""); setNewPassword2(""); setPasswordError(""); };
  const submitPassword = () => {
    if (!oldPassword || !newPassword) { setPasswordError(t.errFill); return; }
    if (newPassword.length < 8) { setPasswordError(t.errLen); return; }
    if (newPassword !== newPassword2) { setPasswordError(t.errMismatch); return; }
    onChangePassword(oldPassword, newPassword);
    closePasswordForm();
  };

  const closeDeleteForm = () => { setDeleteOpen(false); setDeletePassword(""); setDeleteError(""); };
  const submitDelete = () => {
    if (!deletePassword) { setDeleteError(t.delErrFill); return; }
    onDeleteAccount(deletePassword);
    closeDeleteForm();
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
              <button type="button" onClick={closePasswordForm}>{t.cancel}</button>
              <AsyncButton busy={busy === "password"} busyText={t.pwBusy} onClick={submitPassword}>{t.pwConfirm}</AsyncButton>
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
              <button type="button" onClick={closeDeleteForm}>{t.cancel}</button>
              <AsyncButton busy={busy === "delete"} busyText={t.delBusy} className="danger-quiet" onClick={submitDelete}>{t.delConfirm}</AsyncButton>
            </div>
          </div>}
      </article>

      {profile && onSaveProfile && <AuroraPreferencesEditor key={profile.id} profile={profile} profileBusy={profileBusy} onSaveProfile={onSaveProfile} t={t} />}
    </div>
  </section>;
}
