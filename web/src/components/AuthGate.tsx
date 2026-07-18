import { useState, type FormEvent } from "react";
import { api } from "../api";
import { mobileOidc } from "../mobile-auth";

// The single entry surface for /app/aurora/: it must offer both login and account
// creation, because it is the only route a new user is told to visit (see CLAUDE.md's
// own "run it locally" instructions). Historically registration only existed on the
// disconnected legacy page /pages/register.html; this component closes that gap by
// reusing the same backend contract (POST /api/v1/auth/register) inline, right next to
// login, with a small mode toggle -- no separate route, no separate theme
// implementation, so day/dusk/night rendering is automatically consistent between the
// two flows because they are now literally the same component.
export type AuthMode = "login" | "register";

export function AuthGate({ native, onSuccess }: { native: boolean; onSuccess: () => Promise<void> }) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (native) return <main className="login-shell"><section className="login">
    <span className="eyebrow">INNER COSMOS</span><h1>回到你的内宇宙</h1>
    <p>原生应用使用系统浏览器与 Authorization Code + PKCE 登录。密码不会进入 Aurora 应用。</p>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="send" type="button" onClick={() => void mobileOidc.beginLogin()
      .catch(reason => setError(reason instanceof Error ? reason.message : "无法启动安全登录"))}>使用身份提供方继续</button>
  </section></main>;

  const switchMode = (next: AuthMode) => {
    if (busy) return;
    setMode(next);
    setError("");
  };

  const submitLogin = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await api.login(username, password);
      await onSuccess();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "登录失败");
    } finally {
      setBusy(false);
    }
  };

  const submitRegister = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    // Mirrors /pages/register.html's client-side checks exactly (same copy) so the
    // in-app path feels identical to the one it is replacing as the primary entry.
    if (!username.trim() || !password) { setError("请填写用户名和密码。"); return; }
    if (password.length < 8) { setError("密码至少 8 位。"); return; }
    if (password !== password2) { setError("两次输入的密码不一致。"); return; }
    setBusy(true);
    try {
      await api.register(username.trim(), nickname.trim(), password);
      await onSuccess();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "注册失败，请换个用户名试试。");
    } finally {
      setBusy(false);
    }
  };

  return <main className="login-shell">
    <form className="login" onSubmit={mode === "login" ? submitLogin : submitRegister}>
      <span className="eyebrow">INNER COSMOS</span>
      <h1>{mode === "login" ? "回到你的内宇宙" : "开始你的内宇宙"}</h1>
      <p className="auth-copy">{mode === "login"
        ? "登录后继续和 Aurora 的对话，你的记忆与共鸣都还在。"
        : "创建一个账号，几步之内就能开始和 Aurora 说话。"}</p>
      <div className="auth-mode-switch" role="tablist" aria-label="登录或注册">
        <button type="button" role="tab" aria-selected={mode === "login"} onClick={() => switchMode("login")}>登录</button>
        <button type="button" role="tab" aria-selected={mode === "register"} onClick={() => switchMode("register")}>注册</button>
      </div>
      <label>用户名<input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
      {mode === "register" && <label>昵称（可选，默认使用用户名）
        <input value={nickname} onChange={e => setNickname(e.target.value)} autoComplete="nickname" /></label>}
      <label>密码<input type="password" value={password} onChange={e => setPassword(e.target.value)}
        autoComplete={mode === "login" ? "current-password" : "new-password"} /></label>
      {mode === "register" && <label>确认密码
        <input type="password" value={password2} onChange={e => setPassword2(e.target.value)} autoComplete="new-password" /></label>}
      {error && <p className="error" role="alert">{error}</p>}
      <button className="send" type="submit" disabled={busy}>{mode === "login" ? "登录" : "创建账号"}</button>
    </form>
  </main>;
}
