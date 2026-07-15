import { useState } from "react";

export type AccountBusy = "password" | "export" | "delete" | null;

export function AccountSettings({ busy, message, onChangePassword, onExportData, onDeleteAccount }: {
  busy: AccountBusy; message: string | null;
  onChangePassword: (oldPassword: string, newPassword: string) => void;
  onExportData: () => void;
  onDeleteAccount: (password: string) => void;
}) {
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
    if (!oldPassword || !newPassword) { setPasswordError("请填写当前密码和新密码"); return; }
    if (newPassword.length < 8) { setPasswordError("新密码至少 8 位"); return; }
    if (newPassword !== newPassword2) { setPasswordError("两次输入的新密码不一致"); return; }
    onChangePassword(oldPassword, newPassword);
    closePasswordForm();
  };

  const closeDeleteForm = () => { setDeleteOpen(false); setDeletePassword(""); setDeleteError(""); };
  const submitDelete = () => {
    if (!deletePassword) { setDeleteError("请输入密码以确认"); return; }
    onDeleteAccount(deletePassword);
    closeDeleteForm();
  };

  return <section className="account-settings" aria-label="账户与数据">
    <span className="eyebrow">ACCOUNT &amp; DATA</span>
    <h2>账户与数据</h2>
    {message && <p className="account-message">{message}</p>}
    <div className="account-actions-grid">
      <article>
        <strong>导出我的数据</strong>
        <p className="muted">把你的记忆、画像、共鸣体和慢信打包成一份 JSON 文件。</p>
        <button type="button" disabled={busy === "export"} onClick={onExportData}>导出数据</button>
      </article>

      <article>
        <strong>修改密码</strong>
        {!passwordOpen ? <button type="button" onClick={() => setPasswordOpen(true)}>修改密码</button> :
          <div className="account-form">
            <input type="password" placeholder="当前密码" autoComplete="current-password"
              value={oldPassword} onChange={event => setOldPassword(event.target.value)} />
            <input type="password" placeholder="新密码（至少 8 位）" autoComplete="new-password"
              value={newPassword} onChange={event => setNewPassword(event.target.value)} />
            <input type="password" placeholder="再次输入新密码" autoComplete="new-password"
              value={newPassword2} onChange={event => setNewPassword2(event.target.value)} />
            {passwordError && <p className="account-error" role="alert">{passwordError}</p>}
            <div className="account-form-actions">
              <button type="button" onClick={closePasswordForm}>取消</button>
              <button type="button" disabled={busy === "password"} onClick={submitPassword}>确认修改</button>
            </div>
          </div>}
      </article>

      <article className="danger">
        <strong>删除账户</strong>
        {!deleteOpen ? <button type="button" className="danger-quiet" onClick={() => setDeleteOpen(true)}>删除账户</button> :
          <div className="account-form">
            <p className="account-warning">这将永久删除你的账户和所有数据，包括记忆星体、每日记录、待办事项、慢信和共鸣体。<strong>此操作不可撤销。</strong></p>
            <input type="password" placeholder="密码" autoComplete="current-password"
              value={deletePassword} onChange={event => setDeletePassword(event.target.value)} />
            {deleteError && <p className="account-error" role="alert">{deleteError}</p>}
            <div className="account-form-actions">
              <button type="button" onClick={closeDeleteForm}>取消</button>
              <button type="button" className="danger-quiet" disabled={busy === "delete"} onClick={submitDelete}>确认删除</button>
            </div>
          </div>}
      </article>
    </div>
  </section>;
}
