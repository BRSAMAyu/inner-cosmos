import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, streamAurora } from "../api";
import type { Notification, WakeIntent } from "../api";
import type { AuroraStreamEvent } from "../protocol";
import { mobileRuntime } from "../mobile";
import { useAuroraSession } from "./useAuroraSession";

vi.mock("../api", () => ({
  api: {
    createSession: vi.fn(),
    messages: vi.fn(),
    wakeIntent: vi.fn(),
    wakeIntents: vi.fn(),
    notifications: vi.fn(),
    safetyResources: vi.fn(),
    timeline: vi.fn(),
    stop: vi.fn(),
    negotiateWakeIntent: vi.fn(),
    wakeFeedback: vi.fn(),
    readNotification: vi.fn(),
    rescheduleWakeIntent: vi.fn(),
    cancelWakeIntent: vi.fn(),
    psychologySkillSuggestion: vi.fn(),
    triggerGoodbye: vi.fn()
  },
  streamAurora: vi.fn(),
  replayTurnEvents: vi.fn(),
  subscribeProactive: vi.fn(() => () => undefined)
}));

function setup(skillLocale: "zh-CN" | "en-SG" = "zh-CN") {
  const setStatus = vi.fn();
  const onSkillSuggestion = vi.fn();
  const { result } = renderHook(() => useAuroraSession({
    authenticated: true, skillLocale, onSkillSuggestion, setStatus
  }));
  return { result, setStatus, onSkillSuggestion };
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
  vi.mocked(api.messages).mockResolvedValue([]);
  vi.mocked(api.wakeIntents).mockResolvedValue([]);
  vi.mocked(api.notifications).mockResolvedValue([]);
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
      { key: "db-1", speaker: "USER", text: "你好" },
      { key: "db-2", speaker: "AURORA", text: "我在" }
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
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    const { result, setStatus, onSkillSuggestion } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("今天有点累"); });

    await act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });

    expect(result.current.draft).toBe("");
    expect(result.current.messages.some(m => m.speaker === "USER" && m.text === "今天有点累")).toBe(true);
    expect(onSkillSuggestion).toHaveBeenCalledWith(null);
    expect(streamAurora).toHaveBeenCalledOnce();

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

    act(() => { capturedOnEvent!({ id: "5", type: "turn.completed", payload: { message: "done" } }); });
    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.runtimeSignal.stage).toBe("idle");
    expect(setStatus).toHaveBeenLastCalledWith("Aurora 在这里，等你接着说");
  });

  it("a safety event sets a persistent safetyAlert that survives later status updates until dismissed", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
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

  it("an error event ends the turn like every other terminal event, instead of leaving the composer stuck", async () => {
    let capturedOnEvent: ((event: AuroraStreamEvent) => void) | undefined;
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => {
      capturedOnEvent = onEvent;
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
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(api.stop).mockResolvedValue({ turn: { id: 9, status: "INTERRUPTED" }, bubbles: [], events: [] });
    const { result } = setup();
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("接着说"); });
    const sendPromise = act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
    act(() => { capturedOnEvent!({ id: "1", type: "turn.started", payload: { turnId: 9 } }); });
    act(() => { capturedOnEvent!({ id: "2", type: "bubble.started", payload: { order: 0 } }); });
    act(() => { capturedOnEvent!({ id: "3", type: "token", payload: { content: "部" } }); });

    await act(async () => { await result.current.stop(); });
    await sendPromise;

    expect(api.stop).toHaveBeenCalledExactlyOnceWith(9);
    expect(result.current.activeTurnId).toBeNull();
    expect(result.current.messages.find(m => m.key === "live-9-0")?.partial).toBe(true);
  });
});

describe("useAuroraSession -- safety resources", () => {
  it("loadSafetyResources fetches and stores the real backend crisis-resource list", async () => {
    vi.mocked(api.safetyResources).mockResolvedValue([
      "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。"
    ]);
    const { result } = setup();
    expect(result.current.safetyResources).toEqual([]);
    await act(async () => { await result.current.loadSafetyResources(); });
    expect(result.current.safetyResources).toEqual([
      "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。"
    ]);
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
    vi.mocked(streamAurora).mockImplementation(async (_input, _signal, onEvent) => { capturedOnEvent = onEvent; });
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
    });
    vi.mocked(api.psychologySkillSuggestion).mockResolvedValue(null);
    vi.mocked(api.stop).mockResolvedValue({ turn: { id: 9, status: "INTERRUPTED" }, bubbles: [], events: [] });
    const { result, setStatus } = setup("en-SG");
    await act(async () => { await result.current.resolveSession(); });
    act(() => { result.current.setDraft("keep going"); });
    const sendPromise = act(async () => { await result.current.send({ preventDefault: () => undefined } as never); });
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
