import { useState } from "react";
import { AsyncButton } from "../loading";
import type { Locale } from "../i18n";

export type AccountBusy = "password" | "export" | "delete" | null;

const COPY: Record<Locale, {
  aria: string; heading: string; exportTitle: string; exportDesc: string; exportBusy: string; exportBtn: string;
  pwTitle: string; pwOpen: string; pwCurrent: string; pwNew: string; pwNew2: string; cancel: string;
  pwBusy: string; pwConfirm: string; errFill: string; errLen: string; errMismatch: string;
  delTitle: string; delOpen: string; delWarn: string; delWarnStrong: string; delPassword: string;
  delErrFill: string; delBusy: string; delConfirm: string;
}> = {
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
    delBusy: "正在删除", delConfirm: "确认删除"
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
    delBusy: "Deleting", delConfirm: "Confirm deletion"
  }
};

export function AccountSettings({ busy, message, onChangePassword, onExportData, onDeleteAccount, locale = "zh-CN" }: {
  busy: AccountBusy; message: string | null;
  onChangePassword: (oldPassword: string, newPassword: string) => void;
  onExportData: () => void;
  onDeleteAccount: (password: string) => void;
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
    </div>
  </section>;
}
