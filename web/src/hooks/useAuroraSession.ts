import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import {
  api, replayTurnEvents, streamAurora, subscribeProactive,
  type GoodbyeResult, type Notification, type PsychologySkillSuggestion, type WakeIntent
} from "../api";
import type { AuroraStreamEvent, DialogMessage, TurnStatus } from "../protocol";
import type { AuroraUiMessage } from "../components/AuroraConversation";
import type { SkillLocale } from "../components/PsychologySkillStudio";
import { mobileRuntime } from "../mobile";

// Extracted from AuroraApp.tsx (B1 domain-hook decomposition, first slice): everything behind the
// Aurora space's chat experience -- message list, streaming/turn status, interrupt/stop, mode
// picker, WakeIntent negotiate, and session bootstrap/replay. See
// docs/goal/tracks/track-b-status.yml and evidence/track-b/README.md for what stayed behind in
// AuroraApp.tsx and why (the app-wide `status` banner, `authenticated`/bootstrapError, mobile
// runtime state, and every other domain's own data/handlers).

export type AuroraRuntimeSignal = {
  stage: "idle" | "understanding" | "composing" | "speaking";
  runtime: "single" | "dual";
  relationshipMove?: string;
  repaired?: boolean;
};

export type AuroraSafetyAlert = { riskLevel: string; featureTarget: string; safeMessage?: string };

const terminal = new Set<TurnStatus>(["COMPLETED", "INTERRUPTED", "CANCELLED"]);

const STATUS_COPY: Record<SkillLocale, {
  reconnecting: string; restoringEvent: (eventType: string) => string;
  recoveredCompleted: string; recoveredInterrupted: string; stillGenerating: string;
  wakeIntentReturn: (purpose: string) => string; wakeIntentResumeFailed: string;
  wakeIntentArrived: string; wakeIntentArrivedPendingConfirm: string;
  stoppedHere: string; turnStarted: string; turnPlanned: string;
  interrupted: string; completed: string; safetyStatus: string; streamErrorFallback: string;
  listening: string; noTimelineRetry: string; goodbyeFailed: string;
  returnScheduled: string; returnSaveFailed: string;
  returnFeedbackMatched: string; returnFeedbackLater: string; returnFeedbackStop: string; returnFeedbackSaveFailed: string;
  returnPostponed: string; returnCancelled: string;
}> = {
  "zh-CN": {
    reconnecting: "连接闪了一下，正在从持久化时间线恢复…", restoringEvent: eventType => `正在恢复：${eventType}`,
    recoveredCompleted: "已从时间线恢复完整回应", recoveredInterrupted: "已恢复到打断发生的位置",
    stillGenerating: "回应仍在后台生成，你可以继续说，Aurora 会重新规划。",
    wakeIntentReturn: purpose => `Aurora 按约定回到这里：${purpose}`, wakeIntentResumeFailed: "暂时无法续接这次回来约定",
    wakeIntentArrived: "Aurora 按约定回来了；这次抵达已经写入耐久通知。",
    wakeIntentArrivedPendingConfirm: "Aurora 发来了回来信号；正在等待耐久时间线确认。",
    stoppedHere: "已停在这里。直接继续说，Aurora 会带着已听见的部分重新理解。",
    turnStarted: "Aurora 正在重新理解这一刻…", turnPlanned: "Aurora 已想好怎样回应",
    interrupted: "Aurora 停下来了，正在听你接着说。", completed: "Aurora 在这里，等你接着说",
    safetyStatus: "这段内容需要把现实安全放在第一位，请先查看支持资源。", streamErrorFallback: "流式回应发生错误",
    listening: "Aurora 正在听…", noTimelineRetry: "还没建立回应时间线，请重试这句话。",
    goodbyeFailed: "暂时无法完成这次告别",
    returnScheduled: "约好了。你随时可以改期或取消，不需要迁就 Aurora。", returnSaveFailed: "暂时无法保存约定",
    returnFeedbackMatched: "谢谢你告诉我，Aurora 会记住这次节奏。", returnFeedbackLater: "好，Aurora 会晚一点再判断是否适合回来。",
    returnFeedbackStop: "明白了。之后不会再为同一类事情主动提醒。 ", returnFeedbackSaveFailed: "反馈暂时没有保存",
    returnPostponed: "已为你推迟一小时。这个约定由你掌控。", returnCancelled: "已取消。Aurora 不会按这个约定主动回来。"
  },
  "en-SG": {
    reconnecting: "The connection flickered; recovering from the durable timeline…",
    restoringEvent: eventType => `Restoring: ${eventType}`,
    recoveredCompleted: "Recovered the full response from the timeline", recoveredInterrupted: "Recovered up to where it was interrupted",
    stillGenerating: "The response is still generating in the background — you can keep talking, Aurora will replan.",
    wakeIntentReturn: purpose => `Aurora came back as arranged: ${purpose}`, wakeIntentResumeFailed: "Couldn't resume this return right now",
    wakeIntentArrived: "Aurora came back as arranged; this arrival is already recorded in durable notifications.",
    wakeIntentArrivedPendingConfirm: "Aurora sent a return signal; waiting for durable timeline confirmation.",
    stoppedHere: "Stopped here. Keep talking directly — Aurora will re-understand with what it already heard.",
    turnStarted: "Aurora is re-understanding this moment…", turnPlanned: "Aurora has decided how to respond",
    interrupted: "Aurora paused, listening for you to continue.", completed: "Aurora is here, waiting for you to go on",
    safetyStatus: "This needs real-world safety put first — please check the support resources first.", streamErrorFallback: "The streaming response hit an error",
    listening: "Aurora is listening…", noTimelineRetry: "No response timeline exists yet — please retry this message.",
    goodbyeFailed: "Couldn't complete this goodbye right now",
    returnScheduled: "It's arranged. You can reschedule or cancel anytime — Aurora won't hold you to it.", returnSaveFailed: "Couldn't save this arrangement right now",
    returnFeedbackMatched: "Thanks for telling me — Aurora will remember this rhythm.", returnFeedbackLater: "Okay, Aurora will judge later whether it's a good time to come back.",
    returnFeedbackStop: "Understood. Aurora won't proactively remind you about this kind of thing again. ", returnFeedbackSaveFailed: "The feedback wasn't saved this time",
    returnPostponed: "Postponed by an hour for you. You're in control of this arrangement.", returnCancelled: "Cancelled. Aurora won't proactively come back for this arrangement."
  }
};

function toUi(rows: DialogMessage[]): AuroraUiMessage[] {
  return rows.map(row => ({ key: `db-${row.id}`, speaker: row.speaker, text: row.textContent }));
}

export type ResolvedSession = { sessionId: number; returning: WakeIntent | null; aborted: boolean };

export type UseAuroraSessionOptions = {
  /** Gates the WakeIntent-arrival SSE subscription; mirrors AuroraApp.tsx's own `authenticated` gate. */
  authenticated: boolean | null;
  /** The psychology-skill-suggestion side effect of sending a message belongs to the (not yet
   * extracted) Skill domain, so it is injected rather than owned here. */
  skillLocale: SkillLocale;
  onSkillSuggestion: (suggestion: PsychologySkillSuggestion | null) => void;
  /** The app-wide status banner is a cross-cutting concern shared by every domain (see
   * web/src/loading.tsx's B1 loading-audit checkpoint); this hook only ever writes to it. */
  setStatus: (status: string) => void;
};

export function useAuroraSession({ authenticated, skillLocale, onSkillSuggestion, setStatus }: UseAuroraSessionOptions) {
  const t = STATUS_COPY[skillLocale];
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<AuroraUiMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [mode, setMode] = useState("DAILY_TALK");
  const [activeTurnId, setActiveTurnId] = useState<number | null>(null);
  const [runtimeSignal, setRuntimeSignal] = useState<AuroraRuntimeSignal>({ stage: "idle", runtime: "single" });
  const [wakeIntents, setWakeIntents] = useState<WakeIntent[]>([]);
  const [wakeBusy, setWakeBusy] = useState(false);
  const [returnWhen, setReturnWhen] = useState("明天早上 8:30");
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [safetyAlert, setSafetyAlert] = useState<AuroraSafetyAlert | null>(null);
  const dismissSafetyAlert = useCallback(() => setSafetyAlert(null), []);
  const [safetyResources, setSafetyResources] = useState<string[]>([]);
  const loadSafetyResources = useCallback(() => api.safetyResources().then(setSafetyResources), []);
  const [goodbyeResult, setGoodbyeResult] = useState<GoodbyeResult | null>(null);
  const dismissGoodbye = useCallback(() => setGoodbyeResult(null), []);

  const abortRef = useRef<AbortController | null>(null);
  const activeTurnRef = useRef<number | null>(null);
  const bubbleKeyRef = useRef<string | null>(null);
  const eventIdsRef = useRef(new Set<string>());
  const lastEventIdRef = useRef("");
  const reconnectingRef = useRef(false);
  const handleEventRef = useRef<(event: AuroraStreamEvent) => void>(() => undefined);

  const replaceFromHistory = useCallback(async (sid: number) => {
    setMessages(toUi(await api.messages(sid)));
  }, []);

  // Resolves (or creates) the session to resume -- a WakeIntent return if `?wakeIntent=` is
  // present, otherwise a fresh session -- and sets `sessionId` unless a newer bootstrap call has
  // superseded this one (`isStale`). Deliberately does NOT also load messages/wakeIntents/
  // notifications here: AuroraApp.tsx's bootstrap fires those alongside every other domain's
  // initial load in one Promise.all, exactly as it did before this extraction, so the original
  // concurrency (and the "abort before the mega-fetch starts" race guard) is preserved.
  const resolveSession = useCallback(async (isStale?: () => boolean): Promise<ResolvedSession> => {
    const wakeId = Number(new URLSearchParams(window.location.search).get("wakeIntent"));
    const returning = Number.isFinite(wakeId) && wakeId > 0 ? await api.wakeIntent(wakeId) : null;
    const created = returning?.contextSessionId ? { id: returning.contextSessionId } : await api.createSession();
    if (isStale?.()) return { sessionId: created.id, returning, aborted: true };
    setSessionId(created.id);
    return { sessionId: created.id, returning, aborted: false };
  }, []);

  const loadWakeIntents = useCallback(() => api.wakeIntents().then(setWakeIntents), []);
  // No .catch() here to match the original bootstrap Promise.all entry: a failure here should
  // fail the whole bootstrap (surfaced via AuroraApp.tsx's bootstrapError), not be swallowed.
  const loadNotifications = useCallback(() => api.notifications().then(setNotifications), []);
  // Used for background refreshes (mobile resume, WakeIntent arrival) where a failure should be
  // silent rather than surface as a connection error.
  const refreshNotifications = useCallback(() => api.notifications().then(setNotifications).catch(() => undefined), []);

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
    setStatus(t.reconnecting);
    try {
      lastEventIdRef.current = await replayTurnEvents(turnId, lastEventIdRef.current, event => {
        if (event.type === "timeline.event") {
          setStatus(t.restoringEvent(event.payload.eventType));
        } else {
          handleEventRef.current(event);
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
          setStatus(timeline.turn.status === "COMPLETED" ? t.recoveredCompleted : t.recoveredInterrupted);
          finishTurn();
          return;
        }
        await new Promise(resolve => setTimeout(resolve, 500));
      }
      setStatus(t.stillGenerating);
    } finally {
      reconnectingRef.current = false;
    }
  }, [finishTurn, replaceFromHistory, setStatus, t]);

  const openMobileWakeIntent = useCallback(async (wakeIntentId: number) => {
    try {
      const intent = await api.wakeIntent(wakeIntentId);
      const sid = intent.contextSessionId;
      if (sid) {
        setSessionId(sid);
        await replaceFromHistory(sid);
      }
      const url = new URL(window.location.href);
      url.searchParams.set("wakeIntent", String(wakeIntentId));
      window.history.replaceState({}, "", `${url.pathname}${url.search}${url.hash}`);
      setStatus(t.wakeIntentReturn(intent.purpose));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t.wakeIntentResumeFailed);
    }
  }, [replaceFromHistory, setStatus, t]);

  // Encapsulates the "recover an in-flight turn, or just reload history" branch the mobile-runtime
  // resume effect needs -- kept here (rather than exposing activeTurnRef itself) so the ref stays
  // private to this hook.
  const resumeConversation = useCallback(async () => {
    const activeTurn = activeTurnRef.current;
    if (activeTurn && sessionId) await recover(activeTurn, sessionId);
    else if (sessionId) await replaceFromHistory(sessionId);
  }, [recover, replaceFromHistory, sessionId]);

  useEffect(() => {
    if (!authenticated) return;
    return subscribeProactive(event => {
      if (event.type !== "wake_intent") return;
      void api.notifications().then(rows => {
        setNotifications(rows);
        setWakeIntents(current => current.filter(intent => !rows.some(
          notice => notice.refType === "WAKE_INTENT" && notice.refId === intent.id
        )));
        setStatus(t.wakeIntentArrived);
      }).catch(() => setStatus(t.wakeIntentArrivedPendingConfirm));
    });
  }, [authenticated, setStatus, t]);

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
    setStatus(t.stoppedHere);
  }, [finishTurn, setStatus, t]);

  const triggerGoodbye = useCallback(async () => {
    if (!sessionId) return;
    try {
      const result = await api.triggerGoodbye(sessionId, "BUTTON");
      setGoodbyeResult(result);
    } catch (error) { setStatus(error instanceof Error ? error.message : t.goodbyeFailed); }
  }, [sessionId, setStatus, t]);

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
        setStatus(event.type === "turn.started" ? t.turnStarted : t.turnPlanned);
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
      case "segment": {
        // The server places a deliberate pacing break (with its own short pause) between bubbles;
        // it used to be dropped here, collapsing multi-message replies into an undifferentiated
        // wrap. Reflecting it as a brief "composing the next message" beat is what makes Aurora's
        // multi-message rhythm read as authored rather than accidental. The very next
        // bubble.started flips the stage back to "speaking".
        setRuntimeSignal(current => ({ ...current, stage: "composing" }));
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
        setStatus(t.interrupted);
        break;
      case "turn.completed":
      case "done":
        finishTurn();
        setStatus(t.completed);
        break;
      case "safety":
        finishTurn();
        setSafetyAlert({
          riskLevel: event.payload.riskLevel,
          featureTarget: event.payload.featureTarget,
          safeMessage: event.payload.safeMessage
        });
        setStatus(t.safetyStatus);
        break;
      case "error":
        finishTurn();
        setStatus(event.payload.message || t.streamErrorFallback);
        break;
    }
  }, [finishTurn, setStatus, t]);
  handleEventRef.current = handleEvent;

  // Deliberately NOT wrapped in useCallback -- matches the original AuroraApp.tsx, where `send`
  // was a plain function recreated every render (never used as another effect's dependency).
  const send = async (event: FormEvent) => {
    event.preventDefault();
    const text = draft.trim();
    if (!text || !sessionId) return;
    if (abortRef.current || activeTurnRef.current) await stop();
    setDraft("");
    setMessages(current => [...current, { key: `local-${crypto.randomUUID()}`, speaker: "USER", text }]);
    setStatus(t.listening);
    onSkillSuggestion(null);
    void api.psychologySkillSuggestion(text, skillLocale).then(onSkillSuggestion).catch(() => onSkillSuggestion(null));
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
      else setStatus(t.noTimelineRetry);
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
      const notificationAt = new Date(created.preferredAt);
      if (Number.isFinite(notificationAt.getTime()) && notificationAt.getTime() > Date.now()) {
        await mobileRuntime.scheduleWakeIntentNotification({
          wakeIntentId: created.id, title: "Aurora", body: created.reasonForUser, at: notificationAt
        }).catch(() => undefined);
      }
      setStatus(t.returnScheduled);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t.returnSaveFailed);
    } finally { setWakeBusy(false); }
  };

  const respondToReturn = async (notice: Notification, choice: "MATCHED" | "LATER" | "STOP_SIMILAR") => {
    setWakeBusy(true);
    try {
      const result = await api.wakeFeedback(notice.refId, choice);
      await api.readNotification(notice.id);
      setNotifications(current => current.filter(row => row.id !== notice.id));
      if (choice === "LATER") {
        setWakeIntents(current => [...current, result]);
        const at = new Date(result.preferredAt);
        if (Number.isFinite(at.getTime()) && at.getTime() > Date.now()) {
          await mobileRuntime.scheduleWakeIntentNotification({ wakeIntentId: result.id, title: "Aurora", body: result.reasonForUser, at }).catch(() => undefined);
        }
      }
      setStatus(choice === "MATCHED" ? t.returnFeedbackMatched
        : choice === "LATER" ? t.returnFeedbackLater
        : t.returnFeedbackStop);
    } catch (error) { setStatus(error instanceof Error ? error.message : t.returnFeedbackSaveFailed); }
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
      await mobileRuntime.cancelWakeIntentNotification(intent.id);
      const at = new Date(changed.preferredAt);
      if (Number.isFinite(at.getTime()) && at.getTime() > Date.now()) {
        await mobileRuntime.scheduleWakeIntentNotification({ wakeIntentId: changed.id, title: "Aurora", body: changed.reasonForUser, at }).catch(() => undefined);
      }
      setWakeIntents(current => current.map(row => row.id === intent.id ? changed : row));
      setStatus(t.returnPostponed);
    } finally { setWakeBusy(false); }
  };

  const cancelReturn = async (intent: WakeIntent) => {
    setWakeBusy(true);
    try {
      await api.cancelWakeIntent(intent.id);
      await mobileRuntime.cancelWakeIntentNotification(intent.id);
      setWakeIntents(current => current.filter(row => row.id !== intent.id));
      setStatus(t.returnCancelled);
    } finally { setWakeBusy(false); }
  };

  // Used by logout/deleteAccount (still owned by AuroraApp.tsx) to clear the conversation when the
  // account session itself ends.
  const resetSession = useCallback(() => {
    setSessionId(null);
    setMessages([]);
  }, []);

  return {
    sessionId, messages, draft, setDraft, mode, setMode, activeTurnId, runtimeSignal,
    wakeIntents, wakeBusy, returnWhen, setReturnWhen, notifications, safetyAlert, dismissSafetyAlert,
    safetyResources, loadSafetyResources, goodbyeResult, dismissGoodbye, triggerGoodbye,
    send, stop, scheduleReturn, respondToReturn, postponeReturn, cancelReturn,
    resolveSession, replaceFromHistory, loadWakeIntents, loadNotifications, refreshNotifications,
    resumeConversation, openMobileWakeIntent, resetSession
  };
}
