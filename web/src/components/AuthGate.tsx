import { useState, type FormEvent } from "react";
import { api } from "../api";
import { mobileOidc } from "../mobile-auth";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// The single entry surface for /app/aurora/: it must offer both login and account
// creation, because it is the only route a new user is told to visit (see CLAUDE.md's
// own "run it locally" instructions). Historically registration only existed on the
// disconnected legacy page /pages/register.html; this component closes that gap by
// reusing the same backend contract (POST /api/v1/auth/register) inline, right next to
// login, with a small mode toggle -- no separate route, no separate theme
// implementation, so day/dusk/night rendering is automatically consistent between the
// two flows because they are now literally the same component.
export type AuthMode = "login" | "register";

const COPY: Record<Locale, {
  loginH1: string; registerH1: string; nativeP: string; nativeErr: string; nativeBtn: string;
  loginCopy: string; registerCopy: string; switchAria: string; tabLogin: string; tabRegister: string;
  username: string; nickname: string; password: string; confirm: string;
  errFill: string; errLen: string; errMismatch: string; loginFail: string; registerFail: string;
  loginBusy: string; registerBusy: string; loginBtn: string; registerBtn: string;
}> = {
  "zh-CN": {
    loginH1: "回到你的内宇宙", registerH1: "开始你的内宇宙",
    nativeP: "原生应用使用系统浏览器与 Authorization Code + PKCE 登录。密码不会进入 Aurora 应用。",
    nativeErr: "无法启动安全登录", nativeBtn: "使用身份提供方继续",
    loginCopy: "登录后继续和 Aurora 的对话，你的记忆与共鸣都还在。",
    registerCopy: "创建一个账号，几步之内就能开始和 Aurora 说话。",
    switchAria: "登录或注册", tabLogin: "登录", tabRegister: "注册",
    username: "用户名", nickname: "昵称（可选，默认使用用户名）", password: "密码", confirm: "确认密码",
    errFill: "请填写用户名和密码。", errLen: "密码至少 8 位。", errMismatch: "两次输入的密码不一致。",
    loginFail: "登录失败", registerFail: "注册失败，请换个用户名试试。",
    loginBusy: "正在登录", registerBusy: "正在创建", loginBtn: "登录", registerBtn: "创建账号"
  },
  "en-SG": {
    loginH1: "Back to your inner cosmos", registerH1: "Begin your inner cosmos",
    nativeP: "The native app signs in through the system browser with Authorization Code + PKCE. Your password never enters the Aurora app.",
    nativeErr: "Couldn't start secure sign-in", nativeBtn: "Continue with your identity provider",
    loginCopy: "Sign in to continue your conversation with Aurora — your memories and resonance are still here.",
    registerCopy: "Create an account and start talking with Aurora in just a few steps.",
    switchAria: "Log in or sign up", tabLogin: "Log in", tabRegister: "Sign up",
    username: "Username", nickname: "Nickname (optional, defaults to your username)", password: "Password", confirm: "Confirm password",
    errFill: "Enter your username and password.", errLen: "Password must be at least 8 characters.", errMismatch: "The two passwords do not match.",
    loginFail: "Sign-in failed", registerFail: "Sign-up failed — try a different username.",
    loginBusy: "Signing in", registerBusy: "Creating", loginBtn: "Log in", registerBtn: "Create account"
  }
};

export function AuthGate({ native, onSuccess, locale = "zh-CN" }: { native: boolean; onSuccess: () => Promise<void>; locale?: Locale }) {
  const t = COPY[locale];
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (native) return <main className="login-shell"><section className="login">
    <span className="eyebrow">INNER COSMOS</span><h1>{t.loginH1}</h1>
    <p>{t.nativeP}</p>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="send" type="button" onClick={() => void mobileOidc.beginLogin()
      .catch(reason => setError(reason instanceof Error ? reason.message : t.nativeErr))}>{t.nativeBtn}</button>
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
      setError(reason instanceof Error ? reason.message : t.loginFail);
    } finally {
      setBusy(false);
    }
  };

  const submitRegister = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (!username.trim() || !password) { setError(t.errFill); return; }
    if (password.length < 8) { setError(t.errLen); return; }
    if (password !== password2) { setError(t.errMismatch); return; }
    setBusy(true);
    try {
      await api.register(username.trim(), nickname.trim(), password);
      await onSuccess();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t.registerFail);
    } finally {
      setBusy(false);
    }
  };

  return <main className="login-shell">
    <form className="login" onSubmit={mode === "login" ? submitLogin : submitRegister}>
      <span className="eyebrow">INNER COSMOS</span>
      <h1>{mode === "login" ? t.loginH1 : t.registerH1}</h1>
      <p className="auth-copy">{mode === "login" ? t.loginCopy : t.registerCopy}</p>
      <div className="auth-mode-switch" role="tablist" aria-label={t.switchAria}>
        <button type="button" role="tab" aria-selected={mode === "login"} onClick={() => switchMode("login")}>{t.tabLogin}</button>
        <button type="button" role="tab" aria-selected={mode === "register"} onClick={() => switchMode("register")}>{t.tabRegister}</button>
      </div>
      <label>{t.username}<input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
      {mode === "register" && <label>{t.nickname}
        <input value={nickname} onChange={e => setNickname(e.target.value)} autoComplete="nickname" /></label>}
      <label>{t.password}<input type="password" value={password} onChange={e => setPassword(e.target.value)}
        autoComplete={mode === "login" ? "current-password" : "new-password"} /></label>
      {mode === "register" && <label>{t.confirm}
        <input type="password" value={password2} onChange={e => setPassword2(e.target.value)} autoComplete="new-password" /></label>}
      {error && <p className="error" role="alert">{error}</p>}
      <AsyncButton className="send" type="submit" busy={busy}
        busyText={mode === "login" ? t.loginBusy : t.registerBusy}>{mode === "login" ? t.loginBtn : t.registerBtn}</AsyncButton>
    </form>
  </main>;
}
