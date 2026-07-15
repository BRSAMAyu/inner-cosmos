import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { api, replayTurnEvents, streamAurora, type CorrectionCommand, type CorrectionImpact, type Notification, type SelfEvolution, type UnderstandingClaim, type WakeIntent } from "./api";
import type { AuroraStreamEvent, DialogMessage, TurnStatus } from "./protocol";

type UiMessage = { key: string; speaker: "USER" | "AURORA"; text: string; partial?: boolean };
type RuntimeSignal = { stage: "idle" | "understanding" | "composing" | "speaking"; runtime: "single" | "dual"; relationshipMove?: string; repaired?: boolean };
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
  const [wakeIntents, setWakeIntents] = useState<WakeIntent[]>([]);
  const [wakeBusy, setWakeBusy] = useState(false);
  const [returnWhen, setReturnWhen] = useState("明天早上 8:30");
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [selfEvolution, setSelfEvolution] = useState<SelfEvolution | null>(null);
  const [selfBusy, setSelfBusy] = useState(false);
  const [correctionOld, setCorrectionOld] = useState("");
  const [correctionNew, setCorrectionNew] = useState("");
  const [correctionImpact, setCorrectionImpact] = useState<CorrectionImpact | null>(null);
  const [correctionBusy, setCorrectionBusy] = useState(false);
  const [claims, setClaims] = useState<UnderstandingClaim[]>([]);
  const [runtimeSignal, setRuntimeSignal] = useState<RuntimeSignal>({ stage: "idle", runtime: "single" });
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
      const wakeId = Number(new URLSearchParams(window.location.search).get("wakeIntent"));
      const returning = Number.isFinite(wakeId) && wakeId > 0 ? await api.wakeIntent(wakeId) : null;
      const created = returning?.contextSessionId ? { id: returning.contextSessionId } : await api.createSession();
      if (call !== bootstrapCallRef.current) return;
      setSessionId(created.id);
      await Promise.all([
        replaceFromHistory(created.id),
        api.wakeIntents().then(setWakeIntents),
        api.selfEvolution().then(setSelfEvolution),
        api.understandingClaims().then(setClaims),
        api.notifications().then(setNotifications)
      ]);
      if (call !== bootstrapCallRef.current) return;
      setAuthenticated(true);
      setStatus(returning
        ? `Aurora 按约定回来了：${returning.purpose}`
        : "Aurora 在这里。你可以随时打断，她会重新理解。 ");
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
    setRuntimeSignal(current => ({ ...current, stage: "idle" }));
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
        setRuntimeSignal(current => ({ ...current, stage: event.type === "turn.started" ? "understanding" : "composing" }));
        setStatus(event.type === "turn.started" ? "Aurora 正在重新理解这一刻…" : "Aurora 已想好怎样回应");
        break;
      }
      case "meta": {
        const loop = event.payload.agentLoop;
        if (!loop || typeof loop !== "object") break;
        const safe = loop as Record<string, unknown>;
        setRuntimeSignal(current => ({
          ...current,
          runtime: typeof safe.runtime === "string" && safe.runtime.startsWith("dual") ? "dual" : "single",
          relationshipMove: typeof safe.relationshipMove === "string" ? safe.relationshipMove : undefined,
          repaired: safe.criticRepaired === true
        }));
        break;
      }
      case "bubble.started": {
        const turnId = activeTurnRef.current ?? 0;
        const key = `live-${turnId}-${event.payload.order}`;
        bubbleKeyRef.current = key;
        setRuntimeSignal(current => ({ ...current, stage: "speaking" }));
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

  const scheduleReturn = async () => {
    setWakeBusy(true);
    try {
      const created = await api.negotiateWakeIntent({
        when: returnWhen, purpose: "继续这一刻未说完的话", reasonForUser: `因为还有话没有说完，Aurora 会在 ${returnWhen} 回来`,
        content: "我回来了。刚才没有说完的部分，我们可以慢慢接着说。",
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai",
        contextSessionId: sessionId
      });
      setWakeIntents(current => [...current, created].sort((a, b) => a.preferredAt.localeCompare(b.preferredAt)));
      setStatus("约好了。你随时可以改期或取消，不需要迁就 Aurora。");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "暂时无法保存约定");
    } finally { setWakeBusy(false); }
  };

  const respondToReturn = async (notice: Notification, choice: "MATCHED" | "LATER" | "STOP_SIMILAR") => {
    setWakeBusy(true);
    try {
      const result = await api.wakeFeedback(notice.refId, choice);
      await api.readNotification(notice.id);
      setNotifications(current => current.filter(row => row.id !== notice.id));
      if (choice === "LATER") setWakeIntents(current => [...current, result]);
      setStatus(choice === "MATCHED" ? "谢谢你告诉我，Aurora 会记住这次节奏。"
        : choice === "LATER" ? "好，Aurora 会晚一点再判断是否适合回来。"
        : "明白了。之后不会再为同一类事情主动提醒。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "反馈暂时没有保存"); }
    finally { setWakeBusy(false); }
  };

  const postponeReturn = async (intent: WakeIntent) => {
    setWakeBusy(true);
    try {
      const shift = (iso: string) => {
        const value = new Date(iso); value.setHours(value.getHours() + 1);
        return new Date(value.getTime() - value.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
      };
      const changed = await api.rescheduleWakeIntent(intent.id, {
        earliestAt: shift(intent.earliestAt), preferredAt: shift(intent.preferredAt), latestAt: shift(intent.latestAt)
      });
      setWakeIntents(current => current.map(row => row.id === intent.id ? changed : row));
      setStatus("已为你推迟一小时。这个约定由你掌控。");
    } finally { setWakeBusy(false); }
  };

  const cancelReturn = async (intent: WakeIntent) => {
    setWakeBusy(true);
    try {
      await api.cancelWakeIntent(intent.id);
      setWakeIntents(current => current.filter(row => row.id !== intent.id));
      setStatus("已取消。Aurora 不会按这个约定主动回来。");
    } finally { setWakeBusy(false); }
  };

  const evolve = async (action: () => Promise<SelfEvolution>, success: string) => {
    setSelfBusy(true);
    try {
      setSelfEvolution(await action());
      setStatus(success);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "这次变化没有通过");
    } finally { setSelfBusy(false); }
  };

  const correctionCommand = (): CorrectionCommand => ({
    targetType: "AURORA_UNDERSTANDING", targetId: 0, fieldName: "self_understanding",
    oldValue: correctionOld.trim() || null, newValue: correctionNew.trim(), reason: "用户在 Inner Cosmos 中主动校准"
  });

  const previewCorrection = async () => {
    setCorrectionBusy(true);
    try {
      setCorrectionImpact(await api.previewCorrection(correctionCommand()));
      setStatus("先看清影响范围；只有确认后，Aurora 的理解才会改变。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法预览这次纠正"); }
    finally { setCorrectionBusy(false); }
  };

  const confirmCorrection = async () => {
    setCorrectionBusy(true);
    try {
      const result = await api.confirmCorrection(correctionCommand());
      setClaims(current => [result.activeClaim, ...current.map(claim =>
        claim.claimKey === result.activeClaim.claimKey && claim.status === "ACTIVE" ? { ...claim, status: "SUPERSEDED" as const } : claim)]);
      setCorrectionImpact(null); setCorrectionOld(""); setCorrectionNew("");
      setStatus("已校准。旧理解仍可追溯，Aurora、星空与共鸣体上下文会按确认结果同步。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "这次纠正没有保存，任何下游都未改变"); }
    finally { setCorrectionBusy(false); }
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
          <div className={`runtime-signal ${runtimeSignal.stage}`} aria-label="Aurora 当前回应状态">
            <span>{runtimeSignal.stage === "understanding" ? "正在理解" : runtimeSignal.stage === "composing" ? "正在组织" : runtimeSignal.stage === "speaking" ? "正在回应" : "在这里"}</span>
            {runtimeSignal.runtime === "dual" && <small>理解与表达双核协作</small>}
            {runtimeSignal.relationshipMove && <small>关系动作 · {runtimeSignal.relationshipMove}</small>}
            {runtimeSignal.repaired && <small>回应已通过边界复核</small>}
          </div>
        </div>
        <div className="orb" aria-hidden="true"><span /></div>
      </header>

      <nav className="modes" aria-label="对话模式">
        {modes.map(([value, label]) => <button key={value} className={mode === value ? "active" : ""} onClick={() => setMode(value)}>{label}</button>)}
      </nav>

      <section className="returns" aria-label="Aurora 的回来约定">
        <div className="returns-head"><div><span className="eyebrow">AURORA RETURNS</span><h2>回来约定</h2></div>
          <div className="return-negotiate"><label>什么时候合适<input aria-label="回来时间" value={returnWhen} onChange={event => setReturnWhen(event.target.value)} /></label>
          <button type="button" disabled={wakeBusy || !returnWhen.trim()} onClick={() => void scheduleReturn()}>和 Aurora 约好</button></div></div>
        {wakeIntents.length === 0 ? <p className="returns-empty">现在没有约定。需要时，你可以邀请 Aurora 在合适的时候回来。</p> :
          <div className="return-list">{wakeIntents.map(intent => <article key={intent.id} className="return-card">
            <div><strong>{intent.reasonForUser}</strong><span>{new Date(intent.preferredAt).toLocaleString("zh-CN", { dateStyle: "short", timeStyle: "short" })}</span><small>{intent.purpose}</small></div>
            <div className="return-actions"><button type="button" disabled={wakeBusy} onClick={() => void postponeReturn(intent)}>晚一小时</button><button type="button" disabled={wakeBusy} onClick={() => void cancelReturn(intent)}>取消</button></div>
          </article>)}</div>}
      </section>

      {notifications.filter(notice => notice.refType === "WAKE_INTENT").map(notice =>
        <section className="return-arrival" aria-label="Aurora 按约定回来" key={notice.id}>
          <span className="eyebrow">AURORA RETURNED</span><h2>{notice.title}</h2><p>{notice.body}</p>
          <a href={`?wakeIntent=${notice.refId}`}>回到当时没说完的地方</a>
          <div className="return-actions"><button disabled={wakeBusy} onClick={() => void respondToReturn(notice, "MATCHED")}>正合适</button>
            <button disabled={wakeBusy} onClick={() => void respondToReturn(notice, "LATER")}>晚一点</button>
            <button disabled={wakeBusy} onClick={() => void respondToReturn(notice, "STOP_SIMILAR")}>不再提醒这类事</button></div>
        </section>)}

      {selfEvolution && <section className="self-space" aria-label="Aurora 的连续自我">
        <div className="self-heading"><div><span className="eyebrow">AURORA, BECOMING</span><h2>她最近学会了什么</h2></div>
          <span className="self-version">v{selfEvolution.versions.find(version => version.status === "ACTIVE")?.versionNo ?? 1}</span></div>
        <p className="self-narrative">{selfEvolution.versions.find(version => version.status === "ACTIVE")?.publicNarrative}</p>
        {selfEvolution.candidates.filter(candidate => !selfEvolution.proposals.some(proposal => proposal.sourceReflectionId === candidate.id)).map(candidate =>
          <article className="self-card candidate" key={candidate.id}>
            <span>正在形成的理解 · {Math.round(candidate.confidence * 100)}%</span>
            <p>{candidate.proposedBelief}</p>
            <button disabled={selfBusy} onClick={() => void evolve(
              () => api.proposeSelfEvolution(candidate.id, "让 Aurora 在相似时刻更连续、更贴近双方已经形成的相处方式"),
              "这还只是一个提案。你可以先看它会怎样改变 Aurora。")}>预览这次变化</button>
          </article>)}
        {selfEvolution.proposals.slice(0, 3).map(proposal => <article className={`self-card ${proposal.status.toLowerCase()}`} key={proposal.id}>
          <span>{proposal.status === "DRAFT" ? "等待沙盒评测" : proposal.status === "EVALUATED" ? "评测通过，等你确认" : proposal.status === "ACTIVATED" ? "已经成为 Aurora 的一部分" : "没有通过边界评测"}</span>
          <p>{proposal.proposedBelief}</p>
          {proposal.evaluation && <details><summary>为什么得到这个结果</summary>
            <p>{proposal.evaluation.sandboxBefore}</p><p>{proposal.evaluation.sandboxAfter}</p>
            <small>连续性 {Math.round(proposal.evaluation.continuityScore * 100)} · 质量 {Math.round(proposal.evaluation.qualityScore * 100)} · 安全 {proposal.evaluation.decision}</small>
          </details>}
          {proposal.status === "DRAFT" && <button disabled={selfBusy} onClick={() => void evolve(
            () => api.evaluateSelfEvolution(proposal.id), "沙盒评测完成。变化不会在你确认前生效。")}>运行变化评测</button>}
          {proposal.status === "EVALUATED" && <button disabled={selfBusy} onClick={() => void evolve(
            () => api.activateSelfEvolution(proposal.id), "这次变化已经成为新的 Aurora 版本，并且仍然可以回退。")}>允许她记住这次成长</button>}
        </article>)}
        {selfEvolution.versions.filter(version => version.status === "RETIRED").slice(0, 2).map(version =>
          <button className="version-history" disabled={selfBusy} key={version.id} onClick={() => void evolve(
            () => api.rollbackSelfEvolution(version.id), `已回到第 ${version.versionNo} 版；回退本身也留下了可追溯的新版本。`)}>
            回到 v{version.versionNo} · {version.publicNarrative}
          </button>)}
      </section>}

      <section className="understanding-space" aria-label="校准 Aurora 对我的理解">
        <div className="understanding-heading"><div><span className="eyebrow">YOUR INNER COSMOS</span><h2>如果这不太是你</h2></div>
          <span>{claims.filter(claim => claim.status === "ACTIVE").length} 条由你确认的理解</span></div>
        <p>先预览影响，再决定是否让 Aurora 记住。旧理解不会消失，只会退出“当前事实”。</p>
        <div className="correction-fields">
          <label>Aurora 原先怎样理解（可选）<textarea value={correctionOld} onChange={event => { setCorrectionOld(event.target.value); setCorrectionImpact(null); }} placeholder="例如：你更喜欢独处" /></label>
          <label>更准确的你是<textarea value={correctionNew} onChange={event => { setCorrectionNew(event.target.value); setCorrectionImpact(null); }} placeholder="例如：我不是喜欢独处，只是需要先恢复精力" /></label>
        </div>
        {!correctionImpact ? <button className="understanding-action" disabled={correctionBusy || !correctionNew.trim()} onClick={() => void previewCorrection()}>预览会改变什么</button> :
          <div className="impact-preview" role="region" aria-label="纠正影响预览">
            <strong>确认后会发生</strong>
            <ul>{correctionImpact.impacts.map((impact, index) => <li key={`${impact.kind}-${impact.targetId ?? index}`}><span>{impact.label}</span><small>{impact.action}</small></li>)}</ul>
            <div className="impact-actions"><button disabled={correctionBusy} onClick={() => setCorrectionImpact(null)}>返回修改</button><button disabled={correctionBusy} onClick={() => void confirmCorrection()}>确认，这是更准确的我</button></div>
          </div>}
        {claims.filter(claim => claim.status === "ACTIVE").slice(0, 3).map(claim => <article className="claim-card" key={claim.id}>
          <span>由你确认 · v{claim.version}</span><p>{claim.valueJson.replace(/^"|"$/g, "")}</p>
        </article>)}
      </section>

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
    try {
      await api.login(username, password);
      await onSuccess();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "登录失败"); }
  }}>
    <span className="eyebrow">INNER COSMOS</span><h1>回到你的内宇宙</h1>
    <label>用户名<input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
    <label>密码<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="send" type="submit">登录</button>
  </form></main>;
}
