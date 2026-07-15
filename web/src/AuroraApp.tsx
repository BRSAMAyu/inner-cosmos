import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { api, replayTurnEvents, streamAurora } from "./api";
import type { AuroraStreamEvent, DialogMessage, TurnStatus } from "./protocol";

type UiMessage = { key: string; speaker: "USER" | "AURORA"; text: string; partial?: boolean };
const terminal = new Set<TurnStatus>(["COMPLETED", "INTERRUPTED", "CANCELLED"]);
const modes = [
  ["DAILY_TALK", "倾诉"], ["THOUGHT_CLARIFY", "整理"], ["SOCRATIC", "追问"],
  ["ACTION_SPLIT", "行动"], ["RELATION_REVIEW", "关系"]
] as const;

function toUi(rows: DialogMessage[]): UiMessage[] {
  return rows.map(row => ({ key: `db-${row.id}`, speaker: row.speaker, text: row.textContent }));
}

export function AuroraApp() {
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [mode, setMode] = useState("DAILY_TALK");
  const [status, setStatus] = useState("正在连接你的内宇宙…");
  const [activeTurnId, setActiveTurnId] = useState<number | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const activeTurnRef = useRef<number | null>(null);
  const bubbleKeyRef = useRef<string | null>(null);
  const eventIdsRef = useRef(new Set<string>());
  const lastEventIdRef = useRef("");
  const reconnectingRef = useRef(false);
  const bootstrappedRef = useRef(false);
  const bootstrapCallRef = useRef(0);

  const replaceFromHistory = useCallback(async (sid: number) => {
    setMessages(toUi(await api.messages(sid)));
  }, []);

  const bootstrap = useCallback(async () => {
    const call = ++bootstrapCallRef.current;
    try {
      const created = await api.createSession();
      if (call !== bootstrapCallRef.current) return;
      setSessionId(created.id);
      await replaceFromHistory(created.id);
      if (call !== bootstrapCallRef.current) return;
      setAuthenticated(true);
      setStatus("Aurora 在这里。你可以随时打断，她会重新理解。 ");
    } catch (error) {
      if (call !== bootstrapCallRef.current) return;
      if (String(error).includes("Authentication") || String(error).includes("401")) {
        setAuthenticated(false);
        setStatus("请先登录");
      } else {
        setStatus(error instanceof Error ? error.message : "暂时无法连接");
      }
    }
  }, [replaceFromHistory]);

  useEffect(() => {
    if (bootstrappedRef.current) return;
    bootstrappedRef.current = true;
    void bootstrap();
  }, [bootstrap]);

  const finishTurn = useCallback(() => {
    abortRef.current = null;
    activeTurnRef.current = null;
    setActiveTurnId(null);
    bubbleKeyRef.current = null;
  }, []);

  const recover = useCallback(async (turnId: number, sid: number) => {
    if (reconnectingRef.current) return;
    reconnectingRef.current = true;
    setStatus("连接闪了一下，正在从持久化时间线恢复…");
    try {
      lastEventIdRef.current = await replayTurnEvents(turnId, lastEventIdRef.current, event => {
        if (event.type === "timeline.event") {
          setStatus(`正在恢复：${event.payload.eventType}`);
        }
      });
      for (let attempt = 0; attempt < 40; attempt++) {
        const timeline = await api.timeline(turnId);
        const recovered = timeline.bubbles
          .filter(b => b.status === "COMMITTED" || b.deliveredChars > 0)
          .map(b => ({
            key: `replay-${turnId}-${b.id}`,
            speaker: "AURORA" as const,
            text: b.status === "COMMITTED" ? b.content : b.content.slice(0, b.deliveredChars),
            partial: b.status !== "COMMITTED"
          }));
        setMessages(current => [
          ...current.filter(m => !m.key.startsWith(`live-${turnId}-`) && !m.key.startsWith(`replay-${turnId}-`)),
          ...recovered
        ]);
        if (terminal.has(timeline.turn.status)) {
          await replaceFromHistory(sid);
          setStatus(timeline.turn.status === "COMPLETED" ? "已从时间线恢复完整回应" : "已恢复到打断发生的位置");
          finishTurn();
          return;
        }
        await new Promise(resolve => setTimeout(resolve, 500));
      }
      setStatus("回应仍在后台生成，你可以继续说，Aurora 会重新规划。");
    } finally {
      reconnectingRef.current = false;
    }
  }, [finishTurn, replaceFromHistory]);

  const stop = useCallback(async () => {
    const turnId = activeTurnRef.current;
    abortRef.current?.abort();
    if (turnId) {
      try { await api.stop(turnId); } catch { /* stream may have completed first */ }
    }
    setMessages(current => current.map(message =>
      message.key.startsWith(`live-${turnId}-`) ? { ...message, partial: true } : message
    ));
    finishTurn();
    setStatus("已停在这里。直接继续说，Aurora 会带着已听见的部分重新理解。");
  }, [finishTurn]);

  const handleEvent = useCallback((event: AuroraStreamEvent) => {
    if (event.id && eventIdsRef.current.has(event.id)) return;
    if (event.id) eventIdsRef.current.add(event.id);
    if (event.id) lastEventIdRef.current = event.id;
    switch (event.type) {
      case "turn.started":
      case "turn.plan": {
        const turnId = event.payload.turnId;
        activeTurnRef.current = turnId;
        setActiveTurnId(turnId);
        setStatus(event.type === "turn.started" ? "Aurora 正在重新理解这一刻…" : "Aurora 已想好怎样回应");
        break;
      }
      case "bubble.started": {
        const turnId = activeTurnRef.current ?? 0;
        const key = `live-${turnId}-${event.payload.order}`;
        bubbleKeyRef.current = key;
        setMessages(current => current.some(m => m.key === key)
          ? current : [...current, { key, speaker: "AURORA", text: "", partial: true }]);
        break;
      }
      case "token": {
        const key = bubbleKeyRef.current;
        if (!key) break;
        setMessages(current => current.map(message =>
          message.key === key ? { ...message, text: message.text + event.payload.content } : message
        ));
        break;
      }
      case "bubble.completed": {
        const key = bubbleKeyRef.current;
        if (key) setMessages(current => current.map(message => message.key === key ? { ...message, partial: false } : message));
        break;
      }
      case "turn.interrupted":
        finishTurn();
        setStatus("Aurora 停下来了，正在听你接着说。");
        break;
      case "turn.completed":
      case "done":
        finishTurn();
        setStatus("Aurora 在听");
        break;
      case "safety":
        finishTurn();
        setStatus("这段内容需要把现实安全放在第一位，请先查看支持资源。");
        break;
      case "error":
        setStatus(event.payload.message || "流式回应发生错误");
        break;
    }
  }, [finishTurn]);

  const send = async (event: FormEvent) => {
    event.preventDefault();
    const text = draft.trim();
    if (!text || !sessionId) return;
    if (abortRef.current || activeTurnRef.current) await stop();
    setDraft("");
    setMessages(current => [...current, { key: `local-${crypto.randomUUID()}`, speaker: "USER", text }]);
    setStatus("Aurora 正在听…");
    eventIdsRef.current.clear();
    lastEventIdRef.current = "";
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await streamAurora({ sessionId, message: text, mode }, controller.signal, handleEvent);
    } catch (error) {
      if ((error as Error).name === "AbortError") return;
      const turnId = activeTurnRef.current;
      if (turnId) await recover(turnId, sessionId);
      else setStatus("还没建立回应时间线，请重试这句话。");
    }
  };

  if (authenticated === null) return <main className="login-shell"><div className="login" role="status">正在连接你的内宇宙…</div></main>;
  if (!authenticated) return <Login onSuccess={bootstrap} />;

  return (
    <main className="shell">
      <header className="hero">
        <div>
          <span className="eyebrow">INNER COSMOS · AURORA</span>
          <h1>可以被打断的陪伴，<br />才是真的在听。</h1>
          <p>你不需要等 Aurora 说完。新消息会成为新的理解输入，而不是错误。</p>
        </div>
        <div className="orb" aria-hidden="true"><span /></div>
      </header>

      <nav className="modes" aria-label="对话模式">
        {modes.map(([value, label]) => <button key={value} className={mode === value ? "active" : ""} onClick={() => setMode(value)}>{label}</button>)}
      </nav>

      <section className="conversation" aria-live="polite" aria-label="与 Aurora 的对话">
        {messages.length === 0 && <div className="empty"><span>✦</span><p>把现在最真实的一句话放在这里。</p></div>}
        {messages.map(message => (
          <article className={`message ${message.speaker.toLowerCase()} ${message.partial ? "partial" : ""}`} key={message.key}>
            <span className="speaker">{message.speaker === "AURORA" ? "Aurora" : "你"}</span>
            <p>{message.text || "…"}</p>
            {message.partial && message.text && <small>停在这里</small>}
          </article>
        ))}
      </section>

      <div className="state" role="status"><i className={activeTurnId ? "pulse" : ""} />{status}</div>
      <form className="composer" onSubmit={send}>
        <textarea value={draft} onChange={e => setDraft(e.target.value)} placeholder={activeTurnId ? "直接说出新的想法，Aurora 会停下并重新理解…" : "此刻，你想从哪里说起？"} aria-label="写给 Aurora" onKeyDown={e => {
          if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); e.currentTarget.form?.requestSubmit(); }
        }} />
        <div className="actions">
          {activeTurnId && <button type="button" className="stop" onClick={() => void stop()}>停止回应</button>}
          <button type="submit" className="send" disabled={!draft.trim() || !sessionId}>{activeTurnId ? "打断并发送" : "发送"}</button>
        </div>
      </form>
      <footer><a href="/pages/aurora-chat.html">返回经典界面</a><span>渐进迁移 · 原有能力完整保留</span></footer>
    </main>
  );
}

function Login({ onSuccess }: { onSuccess: () => Promise<void> }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  return <main className="login-shell"><form className="login" onSubmit={async e => {
    e.preventDefault();
    try { await api.login(username, password); await onSuccess(); } catch (reason) { setError(reason instanceof Error ? reason.message : "登录失败"); }
  }}>
    <span className="eyebrow">INNER COSMOS</span><h1>回到你的内宇宙</h1>
    <label>用户名<input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
    <label>密码<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="send" type="submit">登录</button>
  </form></main>;
}
