import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, streamAurora, replayTurnEvents } from "../api";
import type { Notification, WakeIntent } from "../api";
import type { AuroraStreamEvent } from "../protocol";
import { mobileRuntime } from "../mobile";
import { useAuroraSession } from "./useAuroraSession";

vi.mock("../api", () => ({
  api: {
    createSession: vi.fn(),
    dialogSessions: vi.fn(),
    dialogSession: vi.fn(),
    currentDialogSession: vi.fn(),
    updateDialogSession: vi.fn(),
    auroraForeground: vi.fn(),
    messages: vi.fn(),
    wakeIntent: vi.fn(),
    wakeIntents: vi.fn(),
    notifications: vi.fn(),
    safetyResources: vi.fn(),
    safetyResourceCatalog: vi.fn(),
    timeline: vi.fn(),
    stop: vi.fn(),
    negotiateWakeIntent: vi.fn(),
    wakeFeedback: vi.fn(),
    readNotification: vi.fn(),
    rescheduleWakeIntent: vi.fn(),
    cancelWakeIntent: vi.fn(),
    psychologySkillSuggestion: vi.fn(),
    triggerGoodbye: vi.fn(),
    settleAuroraSession: vi.fn()
  },
  streamAurora: vi.fn(),
  replayTurnEvents: vi.fn(),
  subscribeProactive: vi.fn(() => () => undefined)
}));

function setup(skillLocale: "zh-CN" | "en-SG" = "zh-CN", onNaturalActionExecuted = vi.fn()) {
  const setStatus = vi.fn();
  const onSkillSuggestion = vi.fn();
  const { result } = renderHook(() => useAuroraSession({
    authenticated: true, skillLocale, onSkillSuggestion, setStatus, onNaturalActionExecuted
  }));
  return { result, setStatus, onSkillSuggestion, onNaturalActionExecuted };
}

const wakeIntent = (overrides: Partial<WakeIntent> = {}): WakeIntent => ({
  id: 1, purpose: "继续这一刻未说完的话", reasonForUser: "因为还有话没有说完",
  content: "我回来了", earliestAt: "2026-07-19T00:00:00", preferredAt: "2026-07-19T00:30:00",
  latestAt: "2026-07-19T01:00:00", timezone: "Asia/Shanghai", status: "PLANNED",
  contextSessionId: null, supersedesIntentId: null, userFeedback: null, ...overrides
});

const notification = (overrides: Partial<Notification> = {}): Notification => ({
  id: 1, type: "WAKE_INTENT", title: "Aurora 回来了", body: "…", refId: 1, refType: "WAKE_INTENT", read: false, ...overrides
});

beforeEach(() => {
  vi.mocked(api.createSession).mockResolvedValue({ id: 100 });
  vi.mocked(api.currentDialogSession).mockResolvedValue(null);
  vi.mocked(api.dialogSessions).mockResolvedValue([]);
  vi.mocked(api.auroraForeground).mockResolvedValue({
    text: "眼前这份累已经很具体了。",
    source: "model-fast",
    latencyMs: 820,
    safetyBlocked: false
  });
  vi.mocked(api.messages).mockResolvedValue([]);
  vi.mocked(api.wakeIntents).mockResolvedValue([]);
  vi.mocked(api.notifications).mockResolvedValue([]);
  vi.mocked(api.safetyResourceCatalog).mockResolvedValue([]);
  window.history.pushState({}, "", "/");
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("useAuroraSession -- initial state", () => {
  it("starts with no session, empty conversation, idle runtime signal and the default mode/return-when copy", () => {
    const { result } = setup();
    expect(result.current.sessionId).toBeNull();
    expect(result.current.messages).toEqual([]);
    expect(result.current.draft).toBe("");
    expect(result.current.mode).toBe("DAILY_TALK");
    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.runtimeSignal).toEqual({ stage: "idle", runtime: "single" });
    expect(result.current.memoryTrace).toBeNull();
    expect(result.current.wakeIntents).toEqual([]);
    expect(result.current.wakeBusy).toBe(false);
    expect(result.current.returnWhen).toBe("明天早上 8:30");
    expect(result.current.notifications).toEqual([]);
  });
});

describe("useAuroraSession -- session bootstrap/replay", () => {
  it("resolveSession creates a fresh session and sets sessionId when there is no wakeIntent deep link", async () => {
    const { result } = setup();
    let resolved: Awaited<ReturnType<typeof result.current.resolveSession>>;
    await act(async () => { resolved = await result.current.resolveSession(); });
    expect(api.createSession).toHaveBeenCalledOnce();
    expect(api.wakeIntent).not.toHaveBeenCalled();
    expect(resolved!).toEqual({ sessionId: 100, returning: null, aborted: false });
    expect(result.current.sessionId).toBe(100);
  });

  it("resolveSession restores the latest durable active conversation after a normal refresh", async () => {
    vi.mocked(api.currentDialogSession).mockResolvedValue({
      id: 42, title: "昨天没说完的事", status: "ACTIVE", messageCount: 4,
      preview: "我们明天继续", activeTurnId: null, startedAt: "2026-07-26T10:00:00",
      lastActivityAt: "2026-07-26T10:10:00", archivedAt: null, pinnedAt: null,
      updatedAt: "2026-07-26T10:10:00"
    });
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    expect(result.current.sessionId).toBe(42);
    expect(api.createSession).not.toHaveBeenCalled();
  });

  it("resolveSession honors a conversation deep link so switching history survives refresh", async () => {
    vi.mocked(api.dialogSession).mockResolvedValue({
      id: 7, title: "Pinned context", status: "ACTIVE", messageCount: 2,
      preview: "continue here", activeTurnId: null, startedAt: "2026-07-26T10:00:00",
      lastActivityAt: "2026-07-26T10:10:00", archivedAt: null, pinnedAt: null,
      updatedAt: "2026-07-26T10:10:00"
    });
    window.history.pushState({}, "", "/?conversation=7");
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    expect(api.dialogSession).toHaveBeenCalledExactlyOnceWith(7);
    expect(api.currentDialogSession).not.toHaveBeenCalled();
    expect(result.current.sessionId).toBe(7);
  });

  it("resolveSession resumes the WakeIntent's own context session when ?wakeIntent= is present", async () => {
    vi.mocked(api.wakeIntent).mockResolvedValue(wakeIntent({ contextSessionId: 42 }));
    window.history.pushState({}, "", "/?wakeIntent=7");
    const { result } = setup();
    let resolved: Awaited<ReturnType<typeof result.current.resolveSession>>;
    await act(async () => { resolved = await result.current.resolveSession(); });
    expect(api.wakeIntent).toHaveBeenCalledExactlyOnceWith(7);
    expect(api.createSession).not.toHaveBeenCalled();
    expect(resolved!.sessionId).toBe(42);
    expect(resolved!.returning?.contextSessionId).toBe(42);
    expect(result.current.sessionId).toBe(42);
  });

  it("resolveSession aborts (does not set sessionId) when the caller reports staleness", async () => {
    const { result } = setup();
    let resolved: Awaited<ReturnType<typeof result.current.resolveSession>>;
    await act(async () => { resolved = await result.current.resolveSession(() => true); });
    expect(resolved!.aborted).toBe(true);
    expect(result.current.sessionId).toBeNull();
  });

  it("replaceFromHistory loads and converts the session's persisted messages", async () => {
    vi.mocked(api.messages).mockResolvedValue([
      { id: 1, speaker: "USER", textContent: "你好" },
      { id: 2, speaker: "AURORA", textContent: "我在" }
    ]);
    const { result } = setup();
    await act(async () => { await result.current.replaceFromHistory(100); });
    expect(result.current.messages).toEqual([
      { key: "db-1", id: 1, speaker: "USER", text: "你好" },
      { key: "db-2", id: 2, speaker: "AURORA", text: "我在" }
    ]);
  });

  it("loadWakeIntents and loadNotifications populate their own state", async () => {
    vi.mocked(api.wakeIntents).mockResolvedValue([wakeIntent()]);
    vi.mocked(api.notifications).mockResolvedValue([notification()]);
    const { result } = setup();
    await act(async () => { await Promise.all([result.current.loadWakeIntents(), result.current.loadNotifications()]); });
    expect(result.current.wakeIntents).toHaveLength(1);
    expect(result.current.notifications).toHaveLength(1);
  });
});

describe("useAuroraSession -- send / streaming / interrupt", () => {
  it("does nothing when the draft is blank or there is no session yet", async () => {
    const { result } = setup();
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
    expect(streamAurora).not.toHaveBeenCalled();
  });

  it("appends the user's message and streams a response, updating messages as bubble events arrive", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result, setStatus, onSkillSuggestion } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("今天有点累"); });

    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    expect(result.current.draft).toBe("");
    expect(result.current.messages.some(m => m.speaker === "USER" && m.text === "今天有点累")).toBe(true);
    expect(result.current.messages.some(m =>
      m.speaker === "AURORA" && m.text === "眼前这份累已经很具体了。")).toBe(false);
    expect(result.current.runtimeSignal).toMatchObject({
      stage: "understanding",
      foregroundText: "眼前这份累已经很具体了。",
      foregroundSource: "model-fast"
    });
    expect(onSkillSuggestion).toHaveBeenCalledWith(null);
    expect(streamAurora).toHaveBeenCalledOnce();
    expect(vi.mocked(streamAurora).mock.calls[0]?.[0]).toMatchObject({
      sessionId: 100,
      message: "今天有点累",
      mode: "DAILY_TALK",
      clientMessageId: expect.any(String),
      locale: "zh-CN",
      region: "CN",
      foregroundAcknowledgementSent: true,
      foregroundAcknowledgementText: "眼前这份累已经很具体了。",
      foregroundAcknowledgementSource: "model-fast"
    });
    expect(api.auroraForeground).toHaveBeenCalledWith(expect.objectContaining({
      clientMessageId: vi.mocked(streamAurora).mock.calls[0]?.[0].clientMessageId,
      locale: "zh-CN",
      region: "CN"
    }));

    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    expect(result.current.activeTurnId).toBe(9);
    expect(result.current.runtimeSignal.stage).toBe("understanding");

    act(() => { capturedOnEvent!({ id: "2", type: "bubble.started", payload: { order: 0 } }); });
    expect(result.current.runtimeSignal.stage).toBe("speaking");
    expect(result.current.messages.some(m => m.key === "live-9-0" && m.partial)).toBe(true);

    act(() => { capturedOnEvent!({ id: "3", type: "token", payload: { content: "先歇一下" } }); });
    expect(result.current.messages.find(m => m.key === "live-9-0")?.text).toBe("先歇一下");

    act(() => { capturedOnEvent!({ id: "4", type: "bubble.completed", payload: { order: 0 } }); });
    expect(result.current.messages.find(m => m.key === "live-9-0")?.partial).toBe(false);

    // A deliberate inter-bubble pacing break must read as "composing", not be dropped.
    act(() => { capturedOnEvent!({ id: "4b", type: "segment", payload: { break: true } }); });
    expect(result.current.runtimeSignal.stage).toBe("composing");
    act(() => { capturedOnEvent!({ id: "4c", type: "bubble.started", payload: { order: 1 } }); });
    expect(result.current.runtimeSignal.stage).toBe("speaking");

    act(() => { capturedOnEvent!({ id: "4d", type: "meta", payload: {
      memoryReferenced: true, referencedMemoryIds: [17, 23, "bad"],
      detectedTheme: "恢复",
      aiState: {
        provider: "mock", model: "aurora-demo", responseSource: "DEMO_MODE",
        fallbackReason: "configured-mock"
      },
      agentLoop: {
        runtime: "dual-kernel.v1", foregroundSource: "local-timeout",
        backgroundPlannerStatus: "SCHEDULED", guidanceSource: "bootstrap",
        fallbackReason: "configured-mock",
        stageLatenciesMs: { speaker: 18, criticalPathTotal: 24, invalid: "bad" }
      }
    } }); });
    expect(result.current.memoryTrace).toEqual({
      referencedMemoryIds: [17, 23], detectedTheme: "恢复"
    });
    expect(result.current.runtimeSignal.runtime).toBe("dual");
    expect(result.current.runtimeSignal).toMatchObject({
      responseSource: "DEMO_MODE",
      foregroundSource: "local-timeout",
      diagnostics: {
        provider: "mock",
        model: "aurora-demo",
        foregroundSource: "local-timeout",
        plannerStatus: "SCHEDULED",
        guidanceSource: "bootstrap",
        fallbackReason: "configured-mock",
        stageLatenciesMs: { speaker: 18, criticalPathTotal: 24 }
      }
    });

    act(() => { capturedOnEvent!({ id: "5", type: "turn.completed", payload: { message: "done" } }); });
    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.runtimeSignal.stage).toBe("idle");
    expect(result.current.runtimeSignal.responseSource).toBe("DEMO_MODE");
    expect(result.current.runtimeSignal.diagnostics?.fallbackReason).toBe("configured-mock");
    expect(result.current.memoryTrace).toEqual({
      referencedMemoryIds: [17, 23], detectedTheme: "恢复"
    });
    expect(setStatus).toHaveBeenLastCalledWith("Aurora 在这里，等你接着说");
  });

  it("shows an interruptible understanding state during foreground latency", async () => {
    let resolveForeground!: (value: Awaited<ReturnType<typeof api.auroraForeground>>) => void;
    vi.mocked(api.auroraForeground).mockImplementation(() => new Promise(resolve => {
      resolveForeground = resolve;
    }));
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(streamAurora).mockResolvedValue("TERMINAL_EVENT");
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("我现在有点乱"); });

    let sendPromise!: Promise<void>;
    await act(async () => {
      sendPromise = result.current.send({ preventDefault: () => undefined } as never);
      await Promise.resolve();
    });

    expect(result.current.activeTurnId).toBe(-1);
    expect(result.current.runtimeSignal.stage).toBe("understanding");

    await act(async () => {
      resolveForeground({
        text: "我先陪你把它放在这里。",
        source: "model-fast",
        latencyMs: 320,
        safetyBlocked: false
      });
      await sendPromise;
    });
  });

  it("refreshes the affected domain only after an executed natural-action receipt", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(api.wakeIntents).mockResolvedValue([wakeIntent({ id: 12 })]);
    const { result, onNaturalActionExecuted } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("确认"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    await act(async () => {
      capturedOnEvent!({ id: "1", type: "meta", payload: {
        proposedActionStatus: "EXECUTED", proposedActionType: "REMINDER",
        featureTarget: "aurora-returns", agentLoop: { runtime: "aurora-action.v1" }
      } });
      await Promise.resolve();
    });

    expect(api.wakeIntents).toHaveBeenCalled();
    expect(result.current.wakeIntents).toEqual([wakeIntent({ id: 12 })]);
    expect(onNaturalActionExecuted).toHaveBeenCalledExactlyOnceWith("aurora-returns");
  });

  it("a safety event sets a persistent safetyAlert that survives later status updates until dismissed", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("我撑不下去了"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    expect(result.current.safetyAlert).toBeNull();

    act(() => { capturedOnEvent!({
      id: "2", type: "safety",
      payload: { riskLevel: "HIGH", featureTarget: "AURORA_CHAT", safeMessage: "先看看这些资源" }
    }); });

    expect(result.current.safetyAlert).toEqual({
      riskLevel: "HIGH", featureTarget: "AURORA_CHAT", safeMessage: "先看看这些资源"
    });
    expect(result.current.activeTurnId).toBeNull();

    // A later, unrelated status-bearing event must not silently clear the alert.
    act(() => { capturedOnEvent!({ id: "3", type: "turn.completed", payload: { message: "done" } }); });
    expect(result.current.safetyAlert).not.toBeNull();

    act(() => { result.current.dismissSafetyAlert(); });
    expect(result.current.safetyAlert).toBeNull();
  });

  it("a GENTLE_CHECK_IN support offer does not terminate the active Aurora turn", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result } = setup("en-SG");
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("I still feel hopeless today"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 19 } }); });

    act(() => { capturedOnEvent!({
      id: "2", type: "safety", payload: {
        riskLevel: "MEDIUM", riskType: "GENTLE_CHECK_IN", handledAction: "SUPPORT_OFFER",
        safetyState: "GENTLE_CHECK_IN", featureTarget: "safety-harbor",
        safeMessage: "Can I gently check: are you safe right now?"
      }
    }); });

    expect(result.current.activeTurnId).toBe(19);
    expect(result.current.safetyAlert?.safetyState).toBe("GENTLE_CHECK_IN");
  });

  // W2 voice: the "inner_voice" SSE event is purely additive -- it must never block, delay, or
  // otherwise change normal turn completion, whether it arrives mid-turn or never at all.
  it("publishes inner_voice on a separate side channel without touching turn/runtime state", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("今天有点乱"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    act(() => { capturedOnEvent!({ id: "2", type: "bubble.started", payload: { order: 0 } }); });
    act(() => { capturedOnEvent!({ id: "3", type: "token", payload: { content: "我在" } }); });

    act(() => { capturedOnEvent!({
      id: "3b", type: "inner_voice",
      payload: { text: "其实我有点担心她今天的状态", audio: "data:audio/mpeg;base64,AAA", voiceId: "warm-a" }
    }); });

    const innerVoiceMessage = result.current.innerVoice;
    expect(innerVoiceMessage).toEqual({
      key: "inner-3b", text: "其实我有点担心她今天的状态",
      audio: "data:audio/mpeg;base64,AAA", voiceId: "warm-a"
    });
    // Purely additive: still mid-turn, runtime/activeTurnId untouched by the inner_voice event.
    expect(result.current.activeTurnId).toBe(9);
    expect(result.current.runtimeSignal.stage).toBe("speaking");

    act(() => { capturedOnEvent!({ id: "4", type: "bubble.completed", payload: { order: 0 } }); });
    act(() => { capturedOnEvent!({ id: "5", type: "turn.completed", payload: { message: "done" } }); });

    // Normal completion proceeds exactly as if the inner_voice event never happened.
    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.runtimeSignal.stage).toBe("idle");
    expect(setStatus).toHaveBeenLastCalledWith("Aurora 在这里，等你接着说");
    // The independent side channel is untouched by turn completion.
    expect(result.current.innerVoice).toEqual(innerVoiceMessage);
    expect(result.current.messages.some(m => m.speaker === "AURORA_INNER")).toBe(false);
  });

  it("completes a turn normally when no inner_voice event ever arrives -- its absence never blocks completion", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("没什么特别的"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    act(() => { capturedOnEvent!({ id: "2", type: "bubble.started", payload: { order: 0 } }); });
    act(() => { capturedOnEvent!({ id: "3", type: "bubble.completed", payload: { order: 0 } }); });
    act(() => { capturedOnEvent!({ id: "4", type: "turn.completed", payload: { message: "done" } }); });

    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.runtimeSignal.stage).toBe("idle");
    expect(setStatus).toHaveBeenLastCalledWith("Aurora 在这里，等你接着说");
    expect(result.current.innerVoice).toBeNull();
  });

  // Self-review regression guard: the backend now emits inner_voice AFTER turn.completed (so a slow
  // TTS synthesis never delays turn closeout), at which point activeTurnRef.current is already null.
  // The side channel intentionally keeps only the latest meaningful heart-voice; it is not a second
  // transcript. A later turn must replace the previous observation even when both arrive after
  // turn.completed.
  it("replaces the side channel with a SECOND turn inner_voice after turn.completed", async () => {
    const onEvents: Array<(event: AuroraStreamEvent) => void> = [];
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      onEvents.push(onEvent);
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });

    // Turn 1 -- inner_voice arrives AFTER turn.completed (activeTurnRef now null): the reorder case.
    act(() => { result.current.setDraft("第一回合"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
    act(() => { onEvents[0]!({ id: "t1-1", type: "turn.started", payload: { turnId: 9 } }); });
    act(() => { onEvents[0]!({ id: "t1-2", type: "turn.completed", payload: { message: "done" } }); });
    act(() => { onEvents[0]!({ id: "t1-3", type: "inner_voice", payload: { text: "回合一的心声", audio: "data:audio/mpeg;base64,A", voiceId: "warm-a" } }); });
    expect(result.current.innerVoice?.text).toBe("回合一的心声");
    expect(result.current.messages.some(m => m.speaker === "AURORA_INNER")).toBe(false);

    // Turn 2 (distinct turnId) -- before the fix this inner_voice was silently dropped.
    act(() => { result.current.setDraft("第二回合"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
    act(() => { onEvents[1]!({ id: "t2-1", type: "turn.started", payload: { turnId: 10 } }); });
    act(() => { onEvents[1]!({ id: "t2-2", type: "turn.completed", payload: { message: "done" } }); });
    act(() => { onEvents[1]!({ id: "t2-3", type: "inner_voice", payload: { text: "回合二的心声", audio: "data:audio/mpeg;base64,B", voiceId: "warm-a" } }); });

    expect(result.current.innerVoice).toEqual({
      key: "inner-t2-3", text: "回合二的心声",
      audio: "data:audio/mpeg;base64,B", voiceId: "warm-a"
    });
    expect(result.current.messages.some(m => m.speaker === "AURORA_INNER")).toBe(false);
  });

  it("an error event ends the turn like every other terminal event, instead of leaving the composer stuck", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("接着说"); });
    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    expect(result.current.activeTurnId).toBe(9);

    act(() => { capturedOnEvent!({ id: "2", type: "error", payload: { message: "流式回应发生错误" } }); });

    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.runtimeSignal.stage).toBe("idle");
    expect(setStatus).toHaveBeenLastCalledWith("流式回应发生错误");
  });

  it("stop aborts the in-flight turn, marks the live bubble partial and resets activeTurnId", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, signal, onEvent) => {
      capturedOnEvent = onEvent;
      await new Promise<void>((_resolve, reject) => {
        signal.addEventListener("abort", () => reject(Object.assign(new Error("aborted"), { name: "AbortError" })));
      });
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(api.stop).mockResolvedValue({ turn: { id: 9, status: "INTERRUPTED" }, bubbles: [], events: [] });
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("接着说"); });
    let sendPromise!: Promise<void>;
    await act(async () => {
      sendPromise = result.current.send({ preventDefault: () => undefined } as never);
      await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    });
    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    act(() => { capturedOnEvent!({ id: "2", type: "bubble.started", payload: { order: 0 } }); });
    act(() => { capturedOnEvent!({ id: "3", type: "token", payload: { content: "部" } }); });

    await act(async () => { await result.current.stop(); });
    await sendPromise;

    expect(api.stop).toHaveBeenCalledExactlyOnceWith(9);
    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.messages.find(m => m.key === "live-9-0")?.partial).toBe(true);
  });

  // ── Gemini audit 4.2 (CONFIRMED/P0): clean EOF without a terminal event must trigger bounded
  //    recovery, never be silently treated as success ──────────────────────────────────────────

  it("4.2: streamAurora resolving EOF_WITHOUT_TERMINAL (no terminal event ever seen) triggers recover(), not silent success", async () => {
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      onEvent({ id: "1", type: "turn.started", payload: { turnId: 9 } });
      return "EOF_WITHOUT_TERMINAL";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(replayTurnEvents).mockResolvedValue("");
    vi.mocked(api.timeline).mockResolvedValue({ turn: { id: 9, status: "COMPLETED" }, bubbles: [], events: [] });
    vi.mocked(api.messages).mockResolvedValue([]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("接着说"); });

    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    // A clean EOF with no terminal event must invoke recover(), not fall through as if the turn
    // had completed normally. (lastEventIdRef is already "1" by this point -- handleEvent updated
    // it from the turn.started event the mock fired before returning EOF_WITHOUT_TERMINAL.)
    expect(replayTurnEvents).toHaveBeenCalledWith(9, "1", expect.any(Function));
    expect(api.timeline).toHaveBeenCalledWith(9);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("恢复"));
    // recover() found the turn already COMPLETED via the timeline poll, so the turn genuinely
    // ends here -- not stuck showing "still generating" forever.
    expect(result.current.activeTurnId).toBeNull();
  });

  // ── Gemini audit 4.1 (CONFIRMED/P0): a superseded turn's in-flight recovery must never
  //    clobber a newer turn's live state ──────────────────────────────────────────────────────

  it("4.1: a stale recovery for an already-stopped turn cannot resurrect state after a newer action superseded it", async () => {
    let releaseTimeline!: () => void;
    const timelineHang = new Promise<void>(resolve => { releaseTimeline = resolve; });
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      onEvent({ id: "1", type: "turn.started", payload: { turnId: 9 } });
      return "EOF_WITHOUT_TERMINAL";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(replayTurnEvents).mockResolvedValue("");
    vi.mocked(api.timeline).mockImplementation(async () => {
      await timelineHang; // simulates a slow recovery poll for the now-superseded turn
      return { turn: { id: 9, status: "COMPLETED" as const }, bubbles: [], events: [] };
    });
    vi.mocked(api.messages).mockResolvedValue([]);
    vi.mocked(api.stop).mockResolvedValue({ turn: { id: 9, status: "INTERRUPTED" }, bubbles: [], events: [] });
    const { result, setStatus } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("接着说"); });

    // send() triggers EOF_WITHOUT_TERMINAL -> recover() -> api.timeline(), which hangs. Started
    // and pumped inside ONE act() so its synchronous state updates (turn.started) are flushed,
    // then this act() call itself resolves while the underlying send() promise keeps running in
    // the background, blocked on the hung timeline call -- it is picked back up (still the SAME
    // promise) by a later, separate act() below, never left un-awaited.
    let sendPromise!: Promise<void>;
    await act(async () => {
      sendPromise = result.current.send({ preventDefault: () => undefined } as never);
      await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    });
    expect(result.current.activeTurnId).toBe(9);

    // The user stops the turn WHILE its recovery is still hung on the timeline poll -- this is
    // exactly the "a newer action supersedes an in-flight recovery" scenario 4.1 protects.
    await act(async () => { await result.current.stop(); });
    expect(result.current.activeTurnId).toBeNull();
    setStatus.mockClear();

    // Now let the stale recovery's timeline poll finally resolve, and await the ORIGINAL send()
    // promise (started above) all the way through to completion.
    await act(async () => {
      releaseTimeline();
      await sendPromise;
      await Promise.resolve();
    });

    // The stale recovery must NOT resurrect activeTurnId or overwrite stop()'s own status with
    // its own "recovered" status message -- its generation was superseded by stop().
    expect(result.current.activeTurnId).toBeNull();
    expect(setStatus).not.toHaveBeenCalledWith(expect.stringContaining("已从时间线恢复"));
    expect(setStatus).not.toHaveBeenCalledWith(expect.stringContaining("已恢复到打断发生的位置"));
  });
});

describe("useAuroraSession -- safety resources", () => {
  it("loadSafetyResources fetches and stores the real backend crisis-resource list", async () => {
    const resource = {
      id: "cn-emergency", label: "紧急报警", phone: "110",
      authorityUrl: "https://www.gov.cn/", verifiedAt: "2026-07-27",
      region: "CN", audience: "所有人", hours: "24/7",
      channel: "PHONE", category: "EMERGENCY"
    } as const;
    vi.mocked(api.safetyResourceCatalog).mockResolvedValue([resource]);
    const { result } = setup();
    expect(result.current.safetyResources).toEqual([]);
    await act(async () => { await result.current.loadSafetyResources(); });
    expect(result.current.safetyResources).toEqual([resource]);
    expect(api.safetyResourceCatalog).toHaveBeenCalledWith("zh-CN", "CN");
  });

  it("requests Singapore English resources for en-SG", async () => {
    vi.mocked(api.safetyResourceCatalog).mockResolvedValue([]);
    const { result } = setup("en-SG");
    await act(async () => { await result.current.loadSafetyResources(); });
    expect(api.safetyResourceCatalog).toHaveBeenCalledWith("en-SG", "SG");
  });
});

describe("useAuroraSession -- goodbye ritual", () => {
  it("triggerGoodbye posts to the goodbye endpoint for the current session and stores the farewell line", async () => {
    vi.mocked(api.triggerGoodbye).mockResolvedValue({
      success: true, line: "今天先到这里，我会把重要的部分留住。", stepsCompleted: [],
      confirmed: false, reverted: false, confidence: 0.95, goodbyeStrength: "HIGH"
    });
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    expect(result.current.goodbyeResult).toBeNull();

    await act(async () => { await result.current.triggerGoodbye(); });

    expect(api.triggerGoodbye).toHaveBeenCalledExactlyOnceWith(100, "BUTTON");
    expect(result.current.goodbyeResult?.line).toBe("今天先到这里，我会把重要的部分留住。");
  });

  it("settles a real conversation and reports completion after the goodbye", async () => {
    vi.mocked(api.triggerGoodbye).mockResolvedValue({
      success: true, line: "先到这里。", stepsCompleted: [], confirmed: false, reverted: false,
      confidence: 0.9, goodbyeStrength: "HIGH"
    });
    vi.mocked(api.settleAuroraSession).mockResolvedValue({} as never);
    const onMemorySettled = vi.fn();
    const setStatus = vi.fn();
    const { result } = renderHook(() => useAuroraSession({
      authenticated: true, skillLocale: "zh-CN", onSkillSuggestion: vi.fn(), setStatus, onMemorySettled
    }));
    await act(async () => { await result.current.resolveSession(); });
    vi.mocked(api.messages).mockResolvedValue([{ id: 1, sessionId: 100, speaker: "USER", textContent: "我很在乎明天的展示" } as never]);
    await act(async () => { await result.current.replaceFromHistory(100); });
    act(() => { result.current.setMode("CAPSULE_SHAPING"); });

    await act(async () => { await result.current.triggerGoodbye(); });

    expect(api.settleAuroraSession).toHaveBeenCalledExactlyOnceWith(100);
    expect(onMemorySettled).toHaveBeenCalledExactlyOnceWith("CAPSULE_SHAPING");
  });

  it("does nothing without an active session", async () => {
    const { result } = setup();
    await act(async () => { await result.current.triggerGoodbye(); });
    expect(api.triggerGoodbye).not.toHaveBeenCalled();
  });

  it("dismissGoodbye clears the stored farewell result", async () => {
    vi.mocked(api.triggerGoodbye).mockResolvedValue({
      success: true, line: "先到这里。", stepsCompleted: [], confirmed: false, reverted: false,
      confidence: 0.9, goodbyeStrength: "MEDIUM"
    });
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    await act(async () => { await result.current.triggerGoodbye(); });
    expect(result.current.goodbyeResult).not.toBeNull();

    act(() => { result.current.dismissGoodbye(); });
    expect(result.current.goodbyeResult).toBeNull();
  });

  it("surfaces a status message when the goodbye request fails, without crashing", async () => {
    vi.mocked(api.triggerGoodbye).mockRejectedValue(new Error("暂时无法完成这次告别"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.resolveSession(); });
    await act(async () => { await result.current.triggerGoodbye(); });
    expect(setStatus).toHaveBeenCalledWith("暂时无法完成这次告别");
    expect(result.current.goodbyeResult).toBeNull();
  });
});

describe("useAuroraSession -- status copy is locale-aware, not hardcoded Chinese", () => {
  it("send() and terminal turn events use English status text when skillLocale is en-SG", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => { capturedOnEvent = onEvent; return "TERMINAL_EVENT"; });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result, setStatus } = setup("en-SG");
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("a bit tired today"); });

    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
    expect(setStatus).toHaveBeenCalledWith(expect.stringMatching(/^Aurora is listening/));

    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    expect(setStatus).toHaveBeenLastCalledWith(expect.stringMatching(/Aurora is/));
    act(() => { capturedOnEvent!({ id: "2", type: "turn.completed", payload: { message: "done" } }); });
    expect(setStatus).toHaveBeenLastCalledWith(expect.not.stringMatching(/[一-鿿]/));
  });

  it("stop() and the streaming-error fallback use English status text when skillLocale is en-SG", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, signal, onEvent) => {
      capturedOnEvent = onEvent;
      await new Promise<void>((_resolve, reject) => {
        signal.addEventListener("abort", () => reject(Object.assign(new Error("aborted"), { name: "AbortError" })));
      });
      return "TERMINAL_EVENT";
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(api.stop).mockResolvedValue({ turn: { id: 9, status: "INTERRUPTED" }, bubbles: [], events: [] });
    const { result, setStatus } = setup("en-SG");
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("keep going"); });
    let sendPromise!: Promise<void>;
    await act(async () => {
      sendPromise = result.current.send({ preventDefault: () => undefined } as never);
      await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    });
    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });

    await act(async () => { await result.current.stop(); });
    await sendPromise;
    expect(setStatus).toHaveBeenLastCalledWith(expect.not.stringMatching(/[一-鿿]/));

    act(() => { capturedOnEvent!({ id: "2", type: "error", payload: { message: "" } }); });
    expect(setStatus).toHaveBeenLastCalledWith(expect.not.stringMatching(/[一-鿿]/));
  });
});

describe("useAuroraSession -- mode picker", () => {
  it("setMode changes the active conversation mode", () => {
    const { result } = setup();
    act(() => { result.current.setMode("SOCRATIC"); });
    expect(result.current.mode).toBe("SOCRATIC");
  });
});

describe("useAuroraSession -- WakeIntent negotiate", () => {
  it("scheduleReturn negotiates a new WakeIntent, inserts it sorted by preferredAt and reports success", async () => {
    vi.mocked(api.negotiateWakeIntent).mockResolvedValue(wakeIntent({ id: 5, preferredAt: "2026-07-19T09:00:00" }));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.scheduleReturn(); });
    expect(api.negotiateWakeIntent).toHaveBeenCalledOnce();
    expect(result.current.wakeIntents).toHaveLength(1);
    expect(result.current.wakeBusy).toBe(false);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("约好了"));
  });

  it("writes and later displays an English WakeIntent receipt when the interface is English", async () => {
    vi.mocked(api.negotiateWakeIntent).mockImplementation(async request => wakeIntent({
      id: 8,
      purpose: request.purpose,
      reasonForUser: request.reasonForUser,
      content: request.content,
      preferredAt: "2026-07-19T09:00:00"
    }));
    const { result, setStatus } = setup("en-SG");

    expect(result.current.returnPurpose).toBe("Continue what we left unfinished");
    await act(async () => { await result.current.scheduleReturn(); });

    expect(api.negotiateWakeIntent).toHaveBeenCalledWith(expect.objectContaining({
      when: "Tomorrow at 8:30 AM",
      purpose: "Continue what we left unfinished",
      reasonForUser: "Aurora will return as agreed (Tomorrow at 8:30 AM) to “Continue what we left unfinished”.",
      content: "I’m back. We can continue the part we left unfinished, at your pace."
    }));
    expect(result.current.wakeIntents[0].reasonForUser).not.toMatch(/[\u4e00-\u9fff]/);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("arranged"));
  });

  it("postponeReturn shifts the intent's window by one hour", async () => {
    const original = wakeIntent({ id: 6, earliestAt: "2026-07-19T08:00:00", preferredAt: "2026-07-19T08:30:00", latestAt: "2026-07-19T09:00:00" });
    vi.mocked(api.wakeIntents).mockResolvedValue([original]);
    const shifted = wakeIntent({ id: 6, earliestAt: "2099-07-19T09:00:00", preferredAt: "2099-07-19T09:30:00", latestAt: "2099-07-19T10:00:00" });
    vi.mocked(api.rescheduleWakeIntent).mockResolvedValue(shifted);
    const cancelNative = vi.spyOn(mobileRuntime, "cancelWakeIntentNotification").mockResolvedValue();
    const scheduleNative = vi.spyOn(mobileRuntime, "scheduleWakeIntentNotification").mockResolvedValue();
    const { result } = setup();
    await act(async () => { await result.current.loadWakeIntents(); });
    await act(async () => { await result.current.postponeReturn(original); });
    expect(api.rescheduleWakeIntent).toHaveBeenCalledWith(6, {
      earliestAt: "2026-07-19T09:00:00", preferredAt: "2026-07-19T09:30:00", latestAt: "2026-07-19T10:00:00"
    });
    expect(result.current.wakeIntents[0]).toEqual(shifted);
    expect(cancelNative).toHaveBeenCalledExactlyOnceWith(6);
    expect(scheduleNative).toHaveBeenCalledOnce();
    cancelNative.mockRestore(); scheduleNative.mockRestore();
  });

  it("cancelReturn removes the intent from the list", async () => {
    vi.mocked(api.wakeIntents).mockResolvedValue([wakeIntent({ id: 6 })]);
    vi.mocked(api.cancelWakeIntent).mockResolvedValue(wakeIntent({ id: 6, status: "CANCELLED" }));
    const cancelNative = vi.spyOn(mobileRuntime, "cancelWakeIntentNotification").mockResolvedValue();
    const { result } = setup();
    await act(async () => { await result.current.loadWakeIntents(); });
    await act(async () => { await result.current.cancelReturn(wakeIntent({ id: 6 })); });
    expect(result.current.wakeIntents).toHaveLength(0);
    expect(cancelNative).toHaveBeenCalledExactlyOnceWith(6);
    cancelNative.mockRestore();
  });

  it("respondToReturn ('MATCHED') marks the arrival notification read and removes it", async () => {
    vi.mocked(api.notifications).mockResolvedValue([notification({ id: 2 })]);
    vi.mocked(api.wakeFeedback).mockResolvedValue(wakeIntent());
    const { result } = setup();
    await act(async () => { await result.current.loadNotifications(); });
    await act(async () => { await result.current.respondToReturn(notification({ id: 2 }), "MATCHED"); });
    expect(api.readNotification).toHaveBeenCalledExactlyOnceWith(2);
    expect(result.current.notifications).toHaveLength(0);
  });
});
