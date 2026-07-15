import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { api, apiConfigurationError, configureBearerAuth, hasConfiguredApiBase, replayTurnEvents, streamAurora, subscribeProactive, type CapsuleGenomeVersion, type CapsuleMatch, type CapsulePreview, type CapsuleQuota, type CapsuleSandbox, type ConnectionRequests, type CorrectionCommand, type CorrectionImpact, type EchoCapsule, type MemoryCard, type MemoryOperation, type Notification, type PersonaMessage, type PersonaSession, type PsychologyRetention, type PsychologySkillManifest, type PsychologySkillRun, type PsychologySkillSuggestion, type ResonanceStrategy, type SelfEvolution, type SlowLetter, type SocialConnection, type StarfieldDetail, type StarfieldScene, type UnderstandingClaim, type WakeIntent } from "./api";
import { initialMobileState, mobileRuntime, type MobileRuntimeState } from "./mobile";
import { mobileOidc } from "./mobile-auth";
import type { AuroraStreamEvent, DialogMessage, TurnStatus } from "./protocol";
import { initialProductSpace, MeSpace, ProductShellNavigation, type ProductSpace } from "./components/ProductShell";
import { AuroraConversation, type AuroraUiMessage } from "./components/AuroraConversation";
import { AuroraSelfSpace } from "./components/AuroraSelfSpace";
import { UnderstandingCorrection } from "./components/UnderstandingCorrection";

type RuntimeSignal = { stage: "idle" | "understanding" | "composing" | "speaking"; runtime: "single" | "dual"; relationshipMove?: string; repaired?: boolean };
const terminal = new Set<TurnStatus>(["COMPLETED", "INTERRUPTED", "CANCELLED"]);
const modes = [
  ["DAILY_TALK", "倾诉"], ["THOUGHT_CLARIFY", "整理"], ["SOCRATIC", "追问"],
  ["ACTION_SPLIT", "行动"], ["RELATION_REVIEW", "关系"]
] as const;
type SkillLocale = "zh-CN" | "en-SG";
const skillQuestions: Record<SkillLocale, Record<string, Array<[string, string, string]>>> = { "zh-CN": {
  "emotion-needs-clarifier": [
    ["situation", "发生了什么", "只写这一次的具体情境"], ["feeling", "最接近的感受", "可以用自己的词，不必选标签"],
    ["need", "你更想保护或得到什么", "例如：空间、准备感、被理解、选择权"]
  ],
  "values-compass": [
    ["choiceA", "选择 A", "它会把你带向哪里"], ["choiceB", "选择 B", "它会把你带向哪里"],
    ["important", "两边都在保护什么重要的东西", "例如：自主、关系、稳定、成长"]
  ],
  "decision-conflict-map": [
    ["decision", "正在拉扯你的决定", "不需要一次讲完整背景"], ["pullToward", "什么在把你拉近它", "期待、价值或现实收益"],
    ["pullAway", "什么在让你退开", "代价、担心或需要被保护的部分"]
  ]
}, "en-SG": {
  "emotion-needs-clarifier": [
    ["situation", "What happened?", "Describe only this specific situation"], ["feeling", "Closest feeling", "Use your own words; no label is required"],
    ["need", "What do you want to protect or receive?", "For example: space, readiness, understanding, choice"]
  ],
  "values-compass": [
    ["choiceA", "Option A", "Where might it take you?"], ["choiceB", "Option B", "Where might it take you?"],
    ["important", "What matters on both sides?", "For example: autonomy, relationship, stability, growth"]
  ],
  "decision-conflict-map": [
    ["decision", "The decision pulling at you", "You do not need to give the whole background"], ["pullToward", "What draws you towards it?", "Hope, values, or practical benefit"],
    ["pullAway", "What makes you step back?", "Costs, worries, or a part that needs protection"]
  ]
} };

const skillCopy = {
  "zh-CN": {
    heading: "不是给你一个标签，而是陪你看清一点", count: "项低风险能力", intro: "这些是有边界的反思工具，不是心理诊断。Aurora 可以建议，但只有你明确同意后才能运行。",
    select: "选择一项反思能力", minutes: "约", minuteUnit: "分钟", before: "开始前，你应当知道", reads: "读取", readsValue: "仅本次填写内容", tools: "工具", noTools: "不调用外部工具", evidence: "依据", evidenceValue: "项，可展开查看", theory: "查看理论依据和版本",
    retention: "结果怎样保留", discard: "只看这一次，不保存结果", save: "保存结果，之后可以撤回", profile: "保存并允许以后单独确认是否进入画像", consent: "我知道这不是诊断，并同意只使用本次填写内容运行", busy: "正在整理…", start: "开始这次反思",
    recent: "最近的反思", revoked: "已撤回", escalated: "已暂停并转向安全支持", noSaved: "这次结果没有被保存。", alternative: "另一种可能：", action: "可以试试：", continue: "和 Aurora 继续谈", revoke: "撤回这次结果"
  },
  "en-SG": {
    heading: "Not a label — a little more clarity", count: "low-risk skills", intro: "These are bounded reflection tools, not psychological diagnoses. Aurora may suggest one, but it runs only after your explicit consent.",
    select: "Choose a reflection skill", minutes: "About", minuteUnit: "minutes", before: "Before you begin", reads: "Reads", readsValue: "Only what you enter for this run", tools: "Tools", noTools: "No external tools", evidence: "Evidence", evidenceValue: "references; expand to inspect", theory: "View evidence and version",
    retention: "How should the result be retained?", discard: "View once; do not save the result", save: "Save the result; I can revoke it later", profile: "Save it and ask separately before any profile use", consent: "I understand this is not a diagnosis and consent to using only this run's input for", busy: "Reflecting…", start: "Begin this reflection",
    recent: "Recent reflections", revoked: "Revoked", escalated: "Paused and redirected to safety support", noSaved: "This result was not saved.", alternative: "Another possibility: ", action: "A small step: ", continue: "Continue with Aurora", revoke: "Revoke this result"
  }
} as const;

function toUi(rows: DialogMessage[]): AuroraUiMessage[] {
  return rows.map(row => ({ key: `db-${row.id}`, speaker: row.speaker, text: row.textContent }));
}

export function AuroraApp() {
  const [productSpace, setProductSpace] = useState<ProductSpace>(initialProductSpace);
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<AuroraUiMessage[]>([]);
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
  const [starfield, setStarfield] = useState<StarfieldScene | null>(null);
  const [starfieldBusy, setStarfieldBusy] = useState(false);
  const [memoryOperations, setMemoryOperations] = useState<MemoryOperation[]>([]);
  const [rollbackBusy, setRollbackBusy] = useState<number | null>(null);
  const [starfieldDetail, setStarfieldDetail] = useState<StarfieldDetail | null>(null);
  const [detailBusy, setDetailBusy] = useState<number | null>(null);
  const [memories, setMemories] = useState<MemoryCard[]>([]);
  const [capsules, setCapsules] = useState<EchoCapsule[]>([]);
  const [selectedCapsuleId, setSelectedCapsuleId] = useState<number | null>(null);
  const [genomeHistory, setGenomeHistory] = useState<CapsuleGenomeVersion[]>([]);
  const [selectedMemoryIds, setSelectedMemoryIds] = useState<number[]>([]);
  const [capsuleName, setCapsuleName] = useState("");
  const [capsuleIntro, setCapsuleIntro] = useState("");
  const [capsulePreview, setCapsulePreview] = useState<CapsulePreview | null>(null);
  const [capsuleBusy, setCapsuleBusy] = useState(false);
  const [sandboxQuestion, setSandboxQuestion] = useState("当你被误解时，通常会怎样表达自己的边界？");
  const [sandboxResult, setSandboxResult] = useState<CapsuleSandbox | null>(null);
  const [sandboxFeedback, setSandboxFeedback] = useState<string | null>(null);
  const [resonanceMatches, setResonanceMatches] = useState<CapsuleMatch[]>([]);
  const [resonanceStrategy, setResonanceStrategy] = useState<ResonanceStrategy>("MIRROR");
  const [visitorMatchId, setVisitorMatchId] = useState<number | null>(null);
  const [personaSession, setPersonaSession] = useState<PersonaSession | null>(null);
  const [personaMessages, setPersonaMessages] = useState<PersonaMessage[]>([]);
  const [personaDraft, setPersonaDraft] = useState("最近有什么让你觉得自己被认真理解了？");
  const [personaQuota, setPersonaQuota] = useState<CapsuleQuota | null>(null);
  const [letterTitle, setLetterTitle] = useState("想把刚才的共鸣慢慢写下来");
  const [letterBody, setLetterBody] = useState("");
  const [sentLetter, setSentLetter] = useState<SlowLetter | null>(null);
  const [letterInbox, setLetterInbox] = useState<SlowLetter[]>([]);
  const [connectionRequests, setConnectionRequests] = useState<ConnectionRequests>({ incoming: [], outgoing: [] });
  const [friends, setFriends] = useState<SocialConnection[]>([]);
  const [skills, setSkills] = useState<PsychologySkillManifest[]>([]);
  const [skillRuns, setSkillRuns] = useState<PsychologySkillRun[]>([]);
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null);
  const [skillAnswers, setSkillAnswers] = useState<Record<string, string>>({});
  const [skillConsent, setSkillConsent] = useState(false);
  const [skillRetention, setSkillRetention] = useState<PsychologyRetention>("DISCARD_AFTER_SESSION");
  const [skillBusy, setSkillBusy] = useState(false);
  const [skillSuggestion, setSkillSuggestion] = useState<PsychologySkillSuggestion | null>(null);
  const [skillLocale, setSkillLocale] = useState<SkillLocale>("zh-CN");
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});
  const [visitorBusy, setVisitorBusy] = useState(false);
  const [runtimeSignal, setRuntimeSignal] = useState<RuntimeSignal>({ stage: "idle", runtime: "single" });
  const [mobileState, setMobileState] = useState<MobileRuntimeState>(initialMobileState);
  const abortRef = useRef<AbortController | null>(null);
  const activeTurnRef = useRef<number | null>(null);
  const bubbleKeyRef = useRef<string | null>(null);
  const eventIdsRef = useRef(new Set<string>());
  const lastEventIdRef = useRef("");
  const reconnectingRef = useRef(false);
  const bootstrappedRef = useRef(false);
  const bootstrapCallRef = useRef(0);

  const navigateSpace = useCallback((space: ProductSpace) => {
    setProductSpace(space);
    const url = new URL(window.location.href);
    url.searchParams.set("space", space);
    window.history.pushState({}, "", `${url.pathname}${url.search}${url.hash}`);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, []);

  useEffect(() => {
    const restoreSpace = () => setProductSpace(initialProductSpace());
    window.addEventListener("popstate", restoreSpace);
    return () => window.removeEventListener("popstate", restoreSpace);
  }, []);

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
      const loaded = await Promise.all([
        replaceFromHistory(created.id),
        api.wakeIntents().then(setWakeIntents),
        api.selfEvolution().then(setSelfEvolution),
        api.understandingClaims().then(setClaims),
        api.starfield("TIME").then(setStarfield),
        api.memoryOperations().then(setMemoryOperations),
        api.notifications().then(setNotifications),
        api.memoryCards().then(rows => { setMemories(rows); return rows; }),
        api.myCapsules().then(rows => { setCapsules(rows); return rows; }),
        api.resonanceMatches().then(rows => { setResonanceMatches(rows); return rows; }),
        api.letterInbox().then(rows => { setLetterInbox(rows); return rows; }),
        api.connectionRequests().then(rows => { setConnectionRequests(rows); return rows; }),
        api.friends().then(rows => { setFriends(rows); return rows; }),
        api.psychologySkills().then(rows => { setSkills(rows); return rows; }),
        api.psychologySkillRuns().then(rows => { setSkillRuns(rows); return rows; })
      ]);
      const loadedCapsules = loaded[8] as EchoCapsule[];
      const loadedMatches = loaded[9] as CapsuleMatch[];
      if (call !== bootstrapCallRef.current) return;
      setAuthenticated(true);
      const firstVisibleCapsule = loadedCapsules.find(capsule => capsule.visibilityStatus !== "ARCHIVED");
      if (firstVisibleCapsule) setSelectedCapsuleId(current => current ?? firstVisibleCapsule.id);
      setVisitorMatchId(current => current ?? loadedMatches[0]?.capsule.id ?? null);
      const loadedSkills = loaded[13] as PsychologySkillManifest[];
      setSelectedSkillId(current => current ?? loadedSkills[0]?.id ?? null);
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

  const selectedCapsule = capsules.find(capsule => capsule.id === selectedCapsuleId) ?? null;
  const visitorMatch = resonanceMatches.find(match => match.capsule.id === visitorMatchId) ?? resonanceMatches[0] ?? null;
  const selectedSkill = skills.find(skill => skill.id === selectedSkillId) ?? skills[0] ?? null;
  const skillText = skillCopy[skillLocale];

  useEffect(() => {
    if (!selectedCapsule) { setGenomeHistory([]); return; }
    const ids = [...selectedCapsule.authorizedMemoryIds.matchAll(/\d+/g)].map(match => Number(match[0]));
    setSelectedMemoryIds(ids);
    setSandboxResult(null);
    setSandboxFeedback(null);
    void api.capsuleGenomeHistory(selectedCapsule.id).then(setGenomeHistory)
      .catch(error => setStatus(error instanceof Error ? error.message : "暂时无法读取共鸣体版本"));
  }, [selectedCapsuleId, selectedCapsule?.activeGenomeVersionId]);

  useEffect(() => {
    if (bootstrappedRef.current) return;
    bootstrappedRef.current = true;
    const native = Capacitor.isNativePlatform();
    configureBearerAuth(native ? () => mobileOidc.accessToken() : null, native,
      native ? () => mobileOidc.expireAccessToken() : null);
    let dispose: (() => Promise<void>) | undefined;
    void mobileOidc.initialize(bootstrap, error => {
      setAuthenticated(false);
      setStatus(error.message);
    }).then(cleanup => {
      dispose = cleanup;
      return bootstrap();
    }).catch(error => {
      setAuthenticated(false);
      setStatus(error instanceof Error ? error.message : "移动认证初始化失败");
    });
    return () => { if (dispose) void dispose(); };
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
      setStatus(`Aurora 按约定回到这里：${intent.purpose}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "暂时无法续接这次回来约定");
    }
  }, [replaceFromHistory]);

  useEffect(() => {
    let cancelled = false;
    let cleanup: (() => Promise<void>) | undefined;
    const resumeFromDurableState = async () => {
      if (cancelled) return;
      const activeTurn = activeTurnRef.current;
      if (activeTurn && sessionId) await recover(activeTurn, sessionId);
      else if (sessionId) await replaceFromHistory(sessionId);
      if (!cancelled) void api.notifications().then(setNotifications).catch(() => undefined);
    };
    const browserOffline = () => setMobileState(current => ({ ...current, connected: false, connectionType: "none" }));
    const browserOnline = () => {
      setMobileState(current => ({ ...current, connected: true, connectionType: "unknown", lastRecoveryAt: new Date().toISOString() }));
      void resumeFromDurableState();
    };
    window.addEventListener("offline", browserOffline);
    window.addEventListener("online", browserOnline);
    void mobileRuntime.start({
      onState: state => { if (!cancelled) setMobileState(state); },
      onResume: resumeFromDurableState,
      onWakeIntent: openMobileWakeIntent,
      onPushToken: () => {
        if (!cancelled) setStatus("设备已向系统通知服务注册；真实远程投递仍取决于当前环境的 APNs / FCM 配置。");
      }
    }).then(stopRuntime => {
      if (cancelled) void stopRuntime();
      else cleanup = stopRuntime;
    }).catch(error => {
      if (!cancelled) setStatus(error instanceof Error ? error.message : "移动端运行时暂时不可用");
    });
    return () => {
      cancelled = true;
      window.removeEventListener("offline", browserOffline);
      window.removeEventListener("online", browserOnline);
      if (cleanup) void cleanup();
    };
  }, [openMobileWakeIntent, recover, replaceFromHistory, sessionId]);

  useEffect(() => {
    if (!authenticated) return;
    return subscribeProactive(event => {
      if (event.type !== "wake_intent") return;
      void api.notifications().then(rows => {
        setNotifications(rows);
        setWakeIntents(current => current.filter(intent => !rows.some(
          notice => notice.refType === "WAKE_INTENT" && notice.refId === intent.id
        )));
        setStatus("Aurora 按约定回来了；这次抵达已经写入耐久通知。");
      }).catch(() => setStatus("Aurora 发来了回来信号；正在等待耐久时间线确认。"));
    });
  }, [authenticated]);

  const requestMobilePush = async () => {
    const permission = await mobileRuntime.requestPushRegistration();
    setStatus(permission === "granted" ? "通知权限已开启，Aurora 可以在真实投递配置就绪后按约定回来。"
      : permission === "denied" ? "通知权限没有开启；回来约定仍会保留在应用内。"
        : permission === "unavailable" ? "当前浏览器不使用系统推送；回来约定仍会在应用内出现。" : "暂时无法完成通知注册。");
  };

  const requestMobileMicrophone = async () => {
    const permission = await mobileRuntime.requestMicrophonePermission();
    setStatus(permission === "granted" ? "麦克风已准备好；本次授权检查没有保存任何录音。"
      : permission === "denied" ? "麦克风权限没有开启，你仍然可以继续打字。"
        : permission === "unavailable" ? "当前环境不支持麦克风输入，你仍然可以继续打字。" : "暂时无法检查麦克风权限。");
  };

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
    setSkillSuggestion(null);
    void api.psychologySkillSuggestion(text, skillLocale).then(setSkillSuggestion).catch(() => setSkillSuggestion(null));
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

  const changeStarfieldMode = async (nextMode: StarfieldScene["mode"]) => {
    setStarfieldBusy(true);
    try { setStarfield(await api.starfield(nextMode)); }
    catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法切换星空视角"); }
    finally { setStarfieldBusy(false); }
  };

  const rollbackMemoryOperation = async (operation: MemoryOperation) => {
    setRollbackBusy(operation.id);
    try {
      const result = await api.rollbackMemoryOperation(operation.id);
      setMemoryOperations(await api.memoryOperations());
      setStarfield(await api.starfield(starfield?.mode ?? "TIME"));
      setStatus(`已撤回这次${operation.operationType}；${result.projectionReceipts.length} 个下游投影留下了重建或复核回执。`);
    } catch (error) { setStatus(error instanceof Error ? error.message : "这次记忆变更无法安全撤回"); }
    finally { setRollbackBusy(null); }
  };

  const revealStar = async (id: number) => {
    setDetailBusy(id);
    try { setStarfieldDetail(await api.starfieldDetail(id)); }
    catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法打开这颗记忆的来源"); }
    finally { setDetailBusy(null); }
  };

  const selectableMemories = memories.filter(memory => memory.status === "ACTIVE");
  const toggleCapsuleMemory = (id: number) => {
    setCapsulePreview(null);
    setSelectedMemoryIds(current => current.includes(id) ? current.filter(value => value !== id) : [...current, id]);
  };

  const previewNewCapsule = async () => {
    setCapsuleBusy(true);
    try {
      const preview = await api.previewCapsule(selectedMemoryIds);
      setCapsulePreview(preview);
      if (!capsuleName.trim()) setCapsuleName(preview.suggestedPseudonym);
      setStatus("这是严格脱敏后的编译预览；还没有创建或公开任何共鸣体。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法生成授权预览"); }
    finally { setCapsuleBusy(false); }
  };

  const createCapsule = async () => {
    if (!capsulePreview) return;
    setCapsuleBusy(true);
    try {
      const created = await api.createCapsule({
        pseudonym: capsuleName.trim() || capsulePreview.suggestedPseudonym,
        intro: capsuleIntro.trim() || capsulePreview.abstractSummary,
        memoryIds: selectedMemoryIds, publicTags: capsulePreview.publicTags
      });
      setCapsules(current => [created, ...current]);
      setSelectedCapsuleId(created.id);
      setCapsulePreview(null); setCapsuleName(""); setCapsuleIntro("");
      setStatus("共鸣体已作为私密版本编译。先在沙盒里判断像不像你，再决定是否公开。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "共鸣体没有创建，授权未改变"); }
    finally { setCapsuleBusy(false); }
  };

  const refreshSelectedCapsule = async (id: number) => {
    const [rows, history] = await Promise.all([api.myCapsules(), api.capsuleGenomeHistory(id)]);
    setCapsules(rows); setGenomeHistory(history);
  };

  const recompileSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.recompileCapsule(selectedCapsule.id, selectedMemoryIds);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus("已生成新的私密 Genome 版本。历史版本仍可追溯，请先试聊再公开。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "重新编译失败，原版本仍保持不变"); }
    finally { setCapsuleBusy(false); }
  };

  const publishSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.setCapsuleVisibility(selectedCapsule.id, "PUBLIC", true);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus("已发布。访客会清楚看到这是授权 AI 共鸣体，不是真人实时在线。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "当前版本还不能安全发布"); }
    finally { setCapsuleBusy(false); }
  };

  const pauseSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.setCapsuleVisibility(selectedCapsule.id, "PRIVATE", false);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus("已暂停公开。Genome 和反馈仍保留，访客暂时不会再发现它。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法暂停公开"); }
    finally { setCapsuleBusy(false); }
  };

  const archiveSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.archiveCapsule(selectedCapsule.id);
      const rows = await api.myCapsules(); setCapsules(rows);
      setSelectedCapsuleId(rows.find(row => row.visibilityStatus !== "ARCHIVED")?.id ?? null);
      setStatus("已撤回。公开发现和既有会话都不能再让这个共鸣体代表你回应。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法撤回共鸣体"); }
    finally { setCapsuleBusy(false); }
  };

  const runCapsuleSandbox = async () => {
    if (!selectedCapsule || !sandboxQuestion.trim()) return;
    setCapsuleBusy(true); setSandboxFeedback(null);
    try {
      setSandboxResult(await api.sandboxCapsule(selectedCapsule.id, sandboxQuestion.trim()));
      setStatus("这段回应只在你的沙盒里。它不会发送给其他人，也不会自动改变 Genome。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "沙盒暂时无法回应"); }
    finally { setCapsuleBusy(false); }
  };

  const rateCapsuleSandbox = async (rating: string) => {
    if (!selectedCapsule || !sandboxResult) return;
    setCapsuleBusy(true);
    try {
      await api.feedbackCapsuleSandbox(selectedCapsule.id, {
        genomeVersionId: sandboxResult.genomeVersionId, question: sandboxResult.question,
        response: sandboxResult.reply, rating
      });
      setSandboxFeedback(rating);
      setStatus("反馈已保存为下一次 Genome 改进信号；当前公开版本没有暗中漂移。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "反馈暂时没有保存"); }
    finally { setCapsuleBusy(false); }
  };

  const chooseVisitorMatch = (capsuleId: number) => {
    setVisitorMatchId(capsuleId);
    setPersonaSession(null); setPersonaMessages([]); setPersonaQuota(null); setSentLetter(null); setLetterBody("");
  };

  const chooseResonanceStrategy = async (strategy: ResonanceStrategy) => {
    setVisitorBusy(true);
    try {
      const matches = await api.resonanceMatches(strategy);
      setResonanceStrategy(strategy); setResonanceMatches(matches);
      setVisitorMatchId(matches[0]?.capsule.id ?? null);
      setPersonaSession(null); setPersonaMessages([]); setPersonaQuota(null); setSentLetter(null); setLetterBody("");
      setStatus(matches[0]?.strategyDescription ?? "已经切换相遇方式");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法切换相遇方式"); }
    finally { setVisitorBusy(false); }
  };

  const startPersonaConversation = async () => {
    if (!visitorMatch) return;
    setVisitorBusy(true);
    try {
      const [session, quota] = await Promise.all([
        api.createPersonaSession(visitorMatch.capsule.id), api.capsuleQuota(visitorMatch.capsule.id)
      ]);
      setPersonaSession(session); setPersonaQuota(quota); setPersonaMessages([]);
      setStatus(`你正在和「${visitorMatch.capsule.pseudonym}」的授权 AI 共鸣体对话，不是真人实时在线。`);
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法进入这个共鸣体"); }
    finally { setVisitorBusy(false); }
  };

  const sendPersonaTurn = async () => {
    if (!personaSession || !visitorMatch || !personaDraft.trim()) return;
    setVisitorBusy(true);
    try {
      await api.sendPersonaMessage(personaSession.id, personaDraft.trim());
      const [history, quota] = await Promise.all([
        api.personaMessages(personaSession.id), api.capsuleQuota(visitorMatch.capsule.id)
      ]);
      setPersonaMessages(history); setPersonaQuota(quota); setPersonaDraft("");
      setStatus("回应来自授权 Genome；你可以继续验证共鸣，也可以把真正想说的内容写成慢信。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "这轮对话没有送达"); }
    finally { setVisitorBusy(false); }
  };

  const sendLetterToMatch = async () => {
    if (!visitorMatch || !letterTitle.trim() || !letterBody.trim()) return;
    setVisitorBusy(true);
    try {
      const draft = await api.draftSlowLetter(visitorMatch.capsule.id, letterTitle.trim(), letterBody.trim());
      const sent = await api.sendSlowLetter(draft.id);
      setSentLetter(sent);
      setStatus("慢信已经启程。收件人看到的是你的原话和安全预览，不是 AI 代写的统一模板。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "慢信没有发送，草稿内容仍在这里"); }
    finally { setVisitorBusy(false); }
  };

  const logout = async () => {
    let remoteWarning: string | null = null;
    try {
      if (Capacitor.isNativePlatform()) {
        try { await mobileOidc.logout(); }
        catch (error) { remoteWarning = error instanceof Error ? error.message : "远程撤销未确认"; }
      }
      else await api.logout();
      setAuthenticated(false); setSessionId(null); setMessages([]); setPersonaSession(null); setPersonaMessages([]);
      setStatus(remoteWarning ?? "已安全退出");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出"); }
  };

  const actOnLetter = async (letter: SlowLetter, action: "read" | "decline" | "block" | "archive") => {
    try {
      const updated = await api.transitionLetter(letter.id, action);
      setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      setStatus(action === "block" ? "已屏蔽来信者；后续慢信也会被阻断。" : "慢信边界已更新。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法更新这封信"); }
  };

  const reportLetter = async (letter: SlowLetter) => {
    try {
      await api.reportLetter(letter.id, "收件人从 Aurora 界面举报慢信");
      setStatus("已提交举报。举报不会自动公开信件内容，交由受限审核处理。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法提交举报"); }
  };

  const replyWithLetter = async (letter: SlowLetter) => {
    const body = replyDrafts[letter.id]?.trim();
    if (!body) return;
    try {
      const draft = await api.replyWithSlowLetter(letter.id, `回复：${letter.title}`, body);
      await api.sendSlowLetter(draft.id);
      const updated = letter.status === "READ" ? await api.transitionLetter(letter.id, "reply") : letter;
      setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      setReplyDrafts(drafts => ({ ...drafts, [letter.id]: "" }));
      setStatus("回复慢信已启程。它仍会经过时间，而不是变成即时聊天。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "回复慢信没有启程"); }
  };

  const refreshConnections = async () => {
    const [requests, accepted] = await Promise.all([api.connectionRequests(), api.friends()]);
    setConnectionRequests(requests); setFriends(accepted);
  };

  const requestConnection = async (letter: SlowLetter) => {
    try {
      await api.requestConnectionFromLetter(letter.id); await refreshConnections();
      setStatus("连接邀请已发出。只有对方明确接受后，双方才会成为真实连接。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法发出连接邀请"); }
  };

  const decideConnection = async (id: number, decision: "accept" | "decline") => {
    try {
      await api.decideConnection(id, decision); await refreshConnections();
      setStatus(decision === "accept" ? "双方都已同意这段连接。" : "已婉拒；不会自动建立任何关系。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法处理连接邀请"); }
  };

  const leaveConnection = async (id: number) => {
    try {
      await api.leaveConnection(id); await refreshConnections(); setStatus("已退出这段连接。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出连接"); }
  };

  const runPsychologySkill = async () => {
    if (!selectedSkill || !skillConsent) return;
    const missing = selectedSkill.requiredInputs.some(key => !skillAnswers[key]?.trim());
    if (missing) { setStatus(skillLocale === "en-SG" ? "Add a little to all three fields; it does not need to be complete or correct." : "先把三处都写一点；不需要写得完整或正确。 "); return; }
    setSkillBusy(true);
    try {
      const run = await api.runPsychologySkill(selectedSkill.id, {
        explicitConsent: true, retentionChoice: skillRetention, locale: skillLocale,
        consentScopes: selectedSkill.requiredScopes, answers: skillAnswers
      });
      setSkillRuns(current => [run, ...current.filter(item => item.id !== run.id)]);
      setSkillConsent(false);
      setStatus(run.status === "ESCALATED"
        ? (skillLocale === "en-SG" ? "This exercise has paused. Put safety and real-world support first." : "这项练习已经暂停。先把安全和现实中的支持放在第一位。 ")
        : (skillLocale === "en-SG" ? "Reflection complete. It is not a diagnosis; you can continue with Aurora, save, or revoke it." : "反思完成。它不是诊断；你可以继续和 Aurora 谈、保存，或撤回。 "));
    } catch (error) { setStatus(error instanceof Error ? error.message : "这项反思暂时没有完成"); }
    finally { setSkillBusy(false); }
  };

  const revokePsychologyRun = async (runId: number) => {
    setSkillBusy(true);
    try {
      const revoked = await api.revokePsychologySkillRun(runId);
      setSkillRuns(current => current.map(run => run.id === runId ? revoked : run));
      setStatus(skillLocale === "en-SG" ? "This Skill result has been revoked and its saved content cleared." : "这次 Skill 结果已经撤回，保存的结果内容已清除。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法撤回这次结果"); }
    finally { setSkillBusy(false); }
  };

  const continueSkillWithAurora = (run: PsychologySkillRun) => {
    const english = run.locale === "en-SG";
    const summary = String(run.result.summary ?? (english ? "I just completed a reflection" : "我刚做完一项自我反思"));
    setDraft(english ? `I just completed a reflection. It said: ${summary}\nI'd like to continue, but please don't treat it as a diagnosis.`
      : `我刚做完一项反思，结果说：${summary}\n我想继续谈谈，但请不要把它当成诊断。`);
    navigateSpace("aurora");
    window.setTimeout(() => document.querySelector(".conversation")?.scrollIntoView({ behavior: "smooth" }), 0);
  };

  const openSuggestedSkill = () => {
    if (!skillSuggestion) return;
    setSelectedSkillId(skillSuggestion.skillId);
    setSkillAnswers({});
    setSkillConsent(false);
    navigateSpace("cosmos");
    window.setTimeout(() => document.querySelector(".skill-studio")?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
  };

  if (mobileState.native && (!hasConfiguredApiBase || apiConfigurationError)) return <main className="login-shell"><div className="login mobile-gate" role="alert">
    <span className="eyebrow">MOBILE ENVIRONMENT GATE</span>
    <h1>这台设备还没有安全后端入口</h1>
    <p>{apiConfigurationError ?? <>应用壳、深链与恢复能力已经就绪，但本次构建没有注入 <code>VITE_API_BASE_URL</code>。</>} 为避免把凭据和会话发往错误地址，Aurora 不会尝试登录。</p>
    <small>请使用经过验证的 HTTPS API 域重新构建；推送凭据与商店签名也必须由授权环境提供。</small>
  </div></main>;
  if (authenticated === null) return <main className="login-shell"><div className="login" role="status">正在连接你的内宇宙…</div></main>;
  if (!authenticated) return <Login native={mobileState.native} onSuccess={bootstrap} />;

  return (
    <main className="shell" data-product-space={productSpace}>
      <ProductShellNavigation active={productSpace} onNavigate={navigateSpace} />

      <div className="product-space" hidden={productSpace !== "aurora"}>
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

      {(mobileState.native || !mobileState.connected) && <section className={`mobile-presence ${mobileState.connected ? "online" : "offline"}`} aria-label="移动端连接状态">
        <div>
          <span className="eyebrow">AURORA, WITH YOU</span>
          <strong>{mobileState.connected ? "移动端已连接" : "网络暂时离开了"}</strong>
          <p>{mobileState.connected
            ? `${mobileState.platform.toUpperCase()} · ${mobileState.connectionType} · 回到前台时会从持久化时间线续接`
            : "你已经看到的内容会留在这里；网络恢复后，Aurora 会重新读取时间线，不会把断线误当成新对话。"}</p>
        </div>
        {mobileState.native && <div className="mobile-actions">
          <button type="button" onClick={() => void requestMobilePush()}>开启回来提醒</button>
          <button type="button" onClick={() => void requestMobileMicrophone()}>准备语音输入</button>
        </div>}
      </section>}

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

      {selfEvolution && <AuroraSelfSpace evolution={selfEvolution} busy={selfBusy}
        onPropose={candidateId => void evolve(() => api.proposeSelfEvolution(candidateId, "让 Aurora 在相似时刻更连续、更贴近双方已经形成的相处方式"), "这还只是一个提案。你可以先看它会怎样改变 Aurora。")}
        onEvaluate={proposalId => void evolve(() => api.evaluateSelfEvolution(proposalId), "沙盒评测完成。变化不会在你确认前生效。")}
        onActivate={proposalId => void evolve(() => api.activateSelfEvolution(proposalId), "这次变化已经成为新的 Aurora 版本，并且仍然可以回退。")}
        onRollback={(versionId, versionNo) => void evolve(() => api.rollbackSelfEvolution(versionId), `已回到第 ${versionNo} 版；回退本身也留下了可追溯的新版本。`)} />}
      </div>

      <div className="product-space" hidden={productSpace !== "cosmos"}>
      <UnderstandingCorrection claims={claims} oldValue={correctionOld} newValue={correctionNew} impact={correctionImpact} busy={correctionBusy}
        onOldValue={value => { setCorrectionOld(value); setCorrectionImpact(null); }} onNewValue={value => { setCorrectionNew(value); setCorrectionImpact(null); }}
        onPreview={() => void previewCorrection()} onCancelPreview={() => setCorrectionImpact(null)} onConfirm={() => void confirmCorrection()} />

      {starfield && <section className="cosmos-space" aria-label="记忆星空">
        <div className="cosmos-heading"><div><span className="eyebrow">MEMORY, ALIVE</span><h2>你的记忆不是档案柜</h2></div>
          <span>{starfield.stars.length} 颗当前记忆</span></div>
        <div className="cosmos-modes" aria-label="星空视角">
          {([ ["TIME", "时间"], ["THEME", "主题"], ["PEOPLE", "人物"] ] as const).map(([value, label]) =>
            <button type="button" disabled={starfieldBusy} aria-pressed={starfield.mode === value} key={value}
              className={starfield.mode === value ? "active" : ""} onClick={() => void changeStarfieldMode(value)}>{label}</button>)}
        </div>
        <p className="cosmos-explanation">{starfield.modeExplanation}</p>
        <div className="cosmos-map" aria-hidden="true">
          {starfield.stars.map(star => <span className="cosmos-star" key={star.id} title={star.ariaLabel} style={{
            left: `${50 + Math.max(-46, Math.min(46, star.x / 2))}%`, top: `${50 + Math.max(-42, Math.min(42, star.y / 2))}%`,
            width: `${Math.max(8, Math.min(24, 8 + star.gravity * 3))}px`, height: `${Math.max(8, Math.min(24, 8 + star.gravity * 3))}px`,
            background: star.color, opacity: Math.max(.45, star.glow ?? .7)
          }} />)}
        </div>
        <div className="cosmos-legend">{Object.entries(starfield.legend).map(([key, value]) => <span key={key}><strong>{key}</strong>{value}</span>)}</div>
        <ol className="cosmos-list" aria-label="记忆星空可访问列表">
          {starfield.accessibleList.map(star => <li key={star.id}><div><strong>{star.title}</strong><span>{star.theme} · {star.memoryLayer}</span></div>
            <small>置信度 {Math.round(star.confidence * 100)}% · v{star.versionNo}</small><p>{star.summary}</p>
            <button type="button" disabled={detailBusy !== null} onClick={() => void revealStar(star.id)}>{detailBusy === star.id ? "正在追溯…" : "查看来源与变化"}</button></li>)}
        </ol>
        {starfieldDetail && <aside className="provenance-panel" aria-label="记忆来源与变化">
          <div><span className="eyebrow">WHY THIS STAR</span><button type="button" onClick={() => setStarfieldDetail(null)} aria-label="关闭记忆来源">×</button></div>
          <h3>{starfieldDetail.card.title}</h3><p>{starfieldDetail.provenanceExplanation}</p>
          <dl><div><dt>当前版本</dt><dd>v{starfieldDetail.card.versionNo}</dd></div><div><dt>理解置信度</dt><dd>{Math.round(starfieldDetail.card.confidence * 100)}%</dd></div><div><dt>记忆层</dt><dd>{starfieldDetail.card.memoryLayer}</dd></div></dl>
          <details open><summary>为什么它在这里</summary><p>{starfieldDetail.gravityExplanation}</p></details>
          <details><summary>变化历史（{starfieldDetail.versionHistory.length}）</summary>{starfieldDetail.versionHistory.length === 0 ? <p>还没有后续改动。</p> : starfieldDetail.versionHistory.map(operation => <p key={operation.id}><strong>{operation.operationType}</strong> · v{operation.oldVersion} → v{operation.newVersion} · {operation.status}</p>)}</details>
          <details><summary>下游状态（{starfieldDetail.projectionReceipts.length}）</summary>{starfieldDetail.projectionReceipts.map(receipt => <p key={receipt.id}><strong>{receipt.projectionType}</strong> · {receipt.status}<br /><small>{receipt.detail}</small></p>)}</details>
        </aside>}
        {memoryOperations.length > 0 && <div className="memory-history" aria-label="记忆变更历史">
          <h3>最近的记忆变更</h3><p>撤回会生成一个新版本，不会抹掉发生过的历史。永久忘记不会恢复原文。</p>
          {memoryOperations.slice(0, 5).map(operation => <article key={operation.id}>
            <div><strong>{operation.operationType}</strong><span>v{operation.oldVersion} → v{operation.newVersion} · {operation.status === "ROLLED_BACK" ? "已撤回" : "已生效"}</span></div>
            {operation.status === "APPLIED" && !["FORGET", "LINK", "NO_OP", "ROLLBACK"].includes(operation.operationType) &&
              <button type="button" disabled={rollbackBusy !== null} onClick={() => void rollbackMemoryOperation(operation)}>{rollbackBusy === operation.id ? "正在撤回…" : "撤回这次变更"}</button>}
            {operation.operationType === "FORGET" && <small>原文已删除，不可恢复</small>}
          </article>)}
        </div>}
      </section>}

      <section className="skill-studio" aria-label={skillLocale === "en-SG" ? "Psychology-informed self reflection" : "心理学启发的自我探索"} lang={skillLocale === "en-SG" ? "en-SG" : "zh-CN"}>
        <div className="skill-heading"><div><span className="eyebrow">PSYCHOLOGY SKILLS</span><h2>{skillText.heading}</h2></div>
          <div className="skill-heading-tools"><span>{skills.length} {skillText.count}</span><div className="skill-locale" role="group" aria-label="Skill language"><button type="button" className={skillLocale === "zh-CN" ? "active" : ""} aria-pressed={skillLocale === "zh-CN"} onClick={() => setSkillLocale("zh-CN")}>中文</button><button type="button" className={skillLocale === "en-SG" ? "active" : ""} aria-pressed={skillLocale === "en-SG"} onClick={() => setSkillLocale("en-SG")}>English</button></div></div></div>
        <p>{skillText.intro}</p>
        <div className="skill-layout">
          <div className="skill-catalog" role="tablist" aria-label={skillText.select}>
            {skills.map(skill => <button type="button" role="tab" aria-selected={selectedSkill?.id === skill.id}
              className={selectedSkill?.id === skill.id ? "active" : ""} key={skill.id}
              onClick={() => { setSelectedSkillId(skill.id); setSkillAnswers({}); setSkillConsent(false); }}>
              <strong>{skill.title[skillLocale]}</strong><span>{skill.description[skillLocale]}</span>
              <small>{skillText.minutes} {skill.estimatedMinutes} {skillText.minuteUnit} · {skill.riskTier} · v{skill.version}</small>
            </button>)}
          </div>
          {selectedSkill && <div className="skill-runner" role="tabpanel">
            <div className="skill-disclosure"><strong>{skillText.before}</strong>
              <p>{selectedSkill.limitations[skillLocale]}</p>
              <dl><div><dt>{skillText.reads}</dt><dd>{skillText.readsValue}</dd></div><div><dt>{skillText.tools}</dt><dd>{selectedSkill.allowedTools.length ? selectedSkill.allowedTools.join(", ") : skillText.noTools}</dd></div><div><dt>{skillText.evidence}</dt><dd>{selectedSkill.evidence.length} {skillText.evidenceValue}</dd></div></dl>
              <details><summary>{skillText.theory}</summary>{selectedSkill.evidence.map(item => <p key={item}>{item}</p>)}<small>{selectedSkill.evaluationSuite}</small></details>
            </div>
            <div className="skill-fields">{(skillQuestions[skillLocale][selectedSkill.id] ?? []).map(([key, label, placeholder]) =>
              <label key={key}>{label}<textarea value={skillAnswers[key] ?? ""} placeholder={placeholder}
                onChange={event => setSkillAnswers(current => ({ ...current, [key]: event.target.value }))} /></label>)}</div>
            <fieldset className="skill-retention"><legend>{skillText.retention}</legend>
              <label><input type="radio" name="skill-retention" checked={skillRetention === "DISCARD_AFTER_SESSION"} onChange={() => setSkillRetention("DISCARD_AFTER_SESSION")} />{skillText.discard}</label>
              <label><input type="radio" name="skill-retention" checked={skillRetention === "SAVE_RESULT"} onChange={() => setSkillRetention("SAVE_RESULT")} />{skillText.save}</label>
              <label><input type="radio" name="skill-retention" checked={skillRetention === "PROFILE_ELIGIBLE"} onChange={() => setSkillRetention("PROFILE_ELIGIBLE")} />{skillText.profile}</label>
            </fieldset>
            <label className="skill-consent"><input type="checkbox" checked={skillConsent} onChange={event => setSkillConsent(event.target.checked)} />{skillText.consent} v{selectedSkill.version}</label>
            <button className="skill-start" type="button" disabled={skillBusy || !skillConsent} onClick={() => void runPsychologySkill()}>{skillBusy ? skillText.busy : skillText.start}</button>
          </div>}
        </div>
        {skillRuns.length > 0 && <div className="skill-results" aria-label={skillLocale === "en-SG" ? "My Skill results" : "我的 Skill 结果"}>
          <h3>{skillText.recent}</h3>{skillRuns.slice(0, 5).map(run => <article key={run.id} className={run.status.toLowerCase()} lang={run.locale === "en-SG" ? "en-SG" : "zh-CN"}>
            <header><strong>{skills.find(skill => skill.id === run.skillId)?.title[run.locale] ?? run.skillId}</strong><span>{run.status === "REVOKED" ? skillText.revoked : run.status === "ESCALATED" ? skillText.escalated : `v${run.skillVersion}`}</span></header>
            {run.status !== "REVOKED" && <><p>{String(run.result.summary ?? skillText.noSaved)}</p>
              {run.result.alternative && <p className="skill-alternative">{skillText.alternative}{String(run.result.alternative)}</p>}
              {run.result.smallAction && <p className="skill-action">{skillText.action}{String(run.result.smallAction)}</p>}
              <div className="skill-result-actions"><button type="button" onClick={() => continueSkillWithAurora(run)}>{skillText.continue}</button>
                <button type="button" disabled={skillBusy} onClick={() => void revokePsychologyRun(run.id)}>{skillText.revoke}</button></div></>}
          </article>)}</div>}
      </section>
      </div>

      <div className="product-space" hidden={productSpace !== "resonance"}>
      <section className="resonance-space" aria-label="共鸣体创建与像不像我沙盒">
        <div className="resonance-heading"><div><span className="eyebrow">YOUR RESONANCE</span><h2>先确认像不像你，再让别人遇见</h2></div>
          <span>{capsules.filter(capsule => capsule.visibilityStatus !== "ARCHIVED").length} 个共鸣体</span></div>
        <p className="resonance-intro">共鸣体是你明确授权的一个侧面，不是你的账号，也不会假装你正在实时回复。每次重编译都形成新版本。</p>

        {capsules.length > 0 && <div className="capsule-tabs" role="tablist" aria-label="我的共鸣体">
          {capsules.filter(capsule => capsule.visibilityStatus !== "ARCHIVED").map(capsule =>
            <button type="button" role="tab" aria-selected={selectedCapsuleId === capsule.id} className={selectedCapsuleId === capsule.id ? "active" : ""}
              key={capsule.id} onClick={() => setSelectedCapsuleId(capsule.id)}>{capsule.pseudonym}<small>{capsule.visibilityStatus === "PUBLIC" ? "已公开" : capsule.visibilityStatus === "NEEDS_REVIEW" ? "需复核" : "仅自己"}</small></button>)}
          <button type="button" role="tab" aria-selected={selectedCapsuleId === null} className={selectedCapsuleId === null ? "active new" : "new"}
            onClick={() => { setSelectedCapsuleId(null); setSelectedMemoryIds([]); setCapsulePreview(null); }}>＋ 新建一个侧面</button>
        </div>}

        {!selectedCapsule ? <div className="capsule-create" role="region" aria-label="创建共鸣体">
          <div className="capsule-step"><span>1</span><div><strong>你愿意让它使用哪些记忆？</strong><small>这里的选择不会自动公开；LOCAL_ONLY 与禁止外部处理的内容不能进入 Genome。</small></div></div>
          <div className="memory-consent-list">{selectableMemories.length === 0 ? <p>还没有可选择的当前记忆。你也可以创建一个不读取记忆的通用侧面。</p> : selectableMemories.slice(0, 10).map(memory => {
            const blocked = ["LOCAL_ONLY", "NO_EXTERNAL_PROCESSING"].includes((memory.consentScope ?? "").toUpperCase());
            return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
              checked={selectedMemoryIds.includes(memory.id)} onChange={() => toggleCapsuleMemory(memory.id)} /><span><strong>{memory.title}</strong><small>{blocked ? "不会用于共鸣体" : `${memory.memoryLayer ?? "记忆"} · v${memory.versionNo}`}</small></span></label>;
          })}</div>
          <div className="capsule-step"><span>2</span><div><strong>它表达你的哪一部分？</strong><small>名字和说明面向访客，但创建后仍保持私密，直到你主动发布。</small></div></div>
          <div className="capsule-fields"><label>共鸣体名字<input value={capsuleName} onChange={event => setCapsuleName(event.target.value)} placeholder="例如：雨后仍愿意开口的人" /></label>
            <label>希望它保留的侧面<textarea value={capsuleIntro} onChange={event => setCapsuleIntro(event.target.value)} placeholder="例如：面对关系误解时，我会先沉默整理，再清楚说出边界。" /></label></div>
          {!capsulePreview ? <button className="resonance-primary" disabled={capsuleBusy} onClick={() => void previewNewCapsule()}>先看严格脱敏预览</button> :
            <div className="capsule-preview" aria-label="共鸣体授权预览"><span className="eyebrow">WHAT IT MAY USE</span><p>{capsulePreview.abstractSummary}</p>
              <div className="preview-tags">{capsulePreview.publicTags.map(tag => <span key={tag}>{tag}</span>)}</div>
              {capsulePreview.removedSensitiveItems.length > 0 && <small>已移除：{capsulePreview.removedSensitiveItems.join("、")}</small>}
              {capsulePreview.riskWarnings.map(warning => <p className="preview-warning" key={warning}>{warning}</p>)}
              <div className="resonance-actions"><button disabled={capsuleBusy} onClick={() => setCapsulePreview(null)}>返回修改</button><button className="resonance-primary" disabled={capsuleBusy} onClick={() => void createCapsule()}>编译为私密版本</button></div>
            </div>}
        </div> : <div className="capsule-workbench">
          <div className="capsule-summary"><div><span className="capsule-status">{selectedCapsule.visibilityStatus === "PUBLIC" ? "公开中" : selectedCapsule.visibilityStatus === "NEEDS_REVIEW" ? "授权变化，等待复核" : "仅自己可见"}</span>
            <h3>{selectedCapsule.pseudonym}</h3><p>{selectedCapsule.intro}</p></div>
            <div className="genome-badge"><strong>v{genomeHistory[0]?.versionNo ?? "–"}</strong><small>{genomeHistory[0]?.status ?? "读取中"}</small></div></div>
          <details className="genome-history"><summary>Genome 版本与变化记录</summary>{genomeHistory.map(version => <article key={version.id}><strong>v{version.versionNo} · {version.status}</strong><span>{version.changeReason}</span><small>{version.compilerVersion}</small></article>)}</details>

          <div className="capsule-step"><span>1</span><div><strong>复核这个版本可以使用的记忆</strong><small>取消选择或修正来源后，必须重新编译；历史版本不会被悄悄改写。</small></div></div>
          <div className="memory-consent-list compact">{selectableMemories.slice(0, 10).map(memory => {
            const blocked = ["LOCAL_ONLY", "NO_EXTERNAL_PROCESSING"].includes((memory.consentScope ?? "").toUpperCase());
            return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
              checked={selectedMemoryIds.includes(memory.id)} onChange={() => toggleCapsuleMemory(memory.id)} /><span><strong>{memory.title}</strong><small>{blocked ? "不能进入共鸣体" : `v${memory.versionNo}`}</small></span></label>;
          })}</div>
          <button className="resonance-secondary" disabled={capsuleBusy} onClick={() => void recompileSelectedCapsule()}>用当前选择生成新版本</button>

          <div className="capsule-step"><span>2</span><div><strong>在只有你能看的沙盒里试聊</strong><small>反馈只成为下一版的改进信号，不会让公开人格暗中漂移。</small></div></div>
          <div className="sandbox-composer"><textarea value={sandboxQuestion} onChange={event => setSandboxQuestion(event.target.value)} aria-label="问自己的共鸣体" />
            <button className="resonance-primary" disabled={capsuleBusy || !sandboxQuestion.trim()} onClick={() => void runCapsuleSandbox()}>看看它会怎么说</button></div>
          {sandboxResult && <article className="sandbox-response"><span>v{sandboxResult.genomeVersionNo} 的回答 · {sandboxResult.identityNotice}</span><p>{sandboxResult.reply}</p>
            {sandboxResult.boundaryNotice && <small>{sandboxResult.boundaryNotice}</small>}
            {sandboxResult.providerAvailable ? <div className="sandbox-ratings" aria-label="这段回应像不像我">
              {([ ["LIKE_ME", "像我"], ["NOT_ME", "不像我"], ["FACT_WRONG", "事实不对"], ["TOO_EXPOSED", "太暴露"], ["TONE_WRONG", "语气不对"] ] as const).map(([value, label]) =>
                <button type="button" className={sandboxFeedback === value ? "active" : ""} disabled={capsuleBusy} key={value} onClick={() => void rateCapsuleSandbox(value)}>{label}</button>)}</div> :
              <p className="preview-warning">真实模型暂时不可用，这次回应不会被当作拟真证据。</p>}
          </article>}

          <div className="capsule-step"><span>3</span><div><strong>决定它是否可以被别人遇见</strong><small>发布不会开放真实身份、联系方式或未授权记忆；撤回会立即阻止新旧会话继续代表你。</small></div></div>
          <div className="resonance-actions">{selectedCapsule.visibilityStatus !== "PUBLIC" && <button className="resonance-primary" disabled={capsuleBusy || genomeHistory[0]?.status !== "ACTIVE"} onClick={() => void publishSelectedCapsule()}>确认并发布当前版本</button>}
            {selectedCapsule.visibilityStatus === "PUBLIC" && <button disabled={capsuleBusy} onClick={() => void pauseSelectedCapsule()}>暂停公开</button>}
            <button className="danger-quiet" disabled={capsuleBusy} onClick={() => void archiveSelectedCapsule()}>撤回这个共鸣体</button></div>
        </div>}
      </section>

      <section className="resonance-network" aria-label="发现共鸣并写一封慢信">
        <div className="resonance-heading"><div><span className="eyebrow">RESONANCE NETWORK</span><h2>不是刷卡片，是理解为什么会相遇</h2></div>
          <span>{resonanceMatches.length} 个此刻的候选</span></div>
        <p className="resonance-intro">这里没有热度排行。系统只展示脱敏侧面、共同主题和边界；先与授权 AI 共鸣体确认是否真的想继续，再决定要不要把话写给本人。</p>
        <div className="strategy-switcher" role="group" aria-label="选择共鸣匹配方式">
          {([["MIRROR", "相似共鸣"], ["COMPLEMENT", "有意义的互补"], ["GROWTH_EDGE", "成长边缘"],
            ["SERENDIPITY", "温和偶遇"], ["CONTEXTUAL", "阶段同行"]] as [ResonanceStrategy, string][]).map(([value, label]) =>
            <button type="button" key={value} aria-pressed={resonanceStrategy === value} disabled={visitorBusy}
              onClick={() => void chooseResonanceStrategy(value)}>{label}</button>)}
        </div>
        {resonanceMatches[0] && <p className="strategy-explanation"><strong>{resonanceMatches[0].strategyLabel}</strong> · {resonanceMatches[0].strategyDescription}</p>}
        {resonanceMatches.length === 0 ? <div className="network-empty">暂时没有足够安全的相遇候选。Inner Cosmos 不会用随机陌生人填满这里。</div> : <>
          <div className="match-rail" role="list" aria-label="共鸣候选">
            {resonanceMatches.map(match => <button type="button" role="listitem" key={match.capsule.id}
              className={visitorMatch?.capsule.id === match.capsule.id ? "match-card active" : "match-card"}
              onClick={() => chooseVisitorMatch(match.capsule.id)}><span>{match.resonant ? "此刻同行" : "探索相遇"}</span>
              <strong>{match.capsule.pseudonym}</strong><p>{match.capsule.intro}</p>
              <small>{match.matchSummary}</small></button>)}
          </div>
          {visitorMatch && <div className="visitor-workbench">
            <header><div><span className="identity-notice">授权 AI 共鸣体 · 不是真人实时在线</span><h3>{visitorMatch.capsule.pseudonym}</h3>
              <p>{visitorMatch.capsule.intro}</p></div><div className="match-reasons">{visitorMatch.matchReasons.map(reason => <span key={reason}>{reason}</span>)}</div></header>
            {!personaSession ? <div className="visitor-entry"><p>先问一两个真正重要的问题。它只能使用创建者明确授权的侧面，也不会把你的 Aurora 私有画像带进这段对话。</p>
              <button className="resonance-primary" disabled={visitorBusy} onClick={() => void startPersonaConversation()}>进入有限但自然的对话</button></div> : <>
              <div className="visitor-quota"><span>今天还可深入 {personaQuota?.remainingTurns ?? "–"} 轮</span><small>额度用于防滥用；模型故障不会扣次数，达到边界后会自然引导慢信。</small></div>
              <div className="persona-history" aria-label="共鸣体对话记录">{personaMessages.length === 0 ? <p>可以从一个具体时刻开始，而不是交换完整履历。</p> : personaMessages.map(message =>
                <article className={message.senderType === "VISITOR" ? "visitor" : "capsule"} key={message.id}><span>{message.senderType === "VISITOR" ? "你" : visitorMatch.capsule.pseudonym}</span><p>{message.textContent}</p></article>)}</div>
              <div className="sandbox-composer"><textarea aria-label="写给共鸣体" value={personaDraft} onChange={event => setPersonaDraft(event.target.value)} />
                <button className="resonance-primary" disabled={visitorBusy || !personaDraft.trim() || personaQuota?.exhausted} onClick={() => void sendPersonaTurn()}>发送这一轮</button></div>
              {personaMessages.some(message => message.senderType === "CAPSULE") && <div className="slow-letter-compose">
                <div className="capsule-step"><span>✉</span><div><strong>如果仍想继续，把话交给时间</strong><small>这封信会送给创建者本人。共鸣体不会替对方承诺回复，也不会泄露联系方式。</small></div></div>
                {visitorMatch.capsule.capsuleType !== "USER_CAPSULE" ? <p className="preview-warning">这是官方种子共鸣体，没有对应的真人收件人；你仍可继续对话，但不能把它当作认识真人的入口。</p> : sentLetter ?
                  <div className="letter-flight" role="status"><strong>慢信已启程</strong><span>{sentLetter.title}</span><small>预计 {new Date(sentLetter.estimatedArrivalAt).toLocaleString()} 到达 · 状态 {sentLetter.status}</small></div> : <>
                    <label>信的题目<input value={letterTitle} onChange={event => setLetterTitle(event.target.value)} /></label>
                    <label>你真正想让对方读到的话<textarea aria-label="慢信正文" value={letterBody} onChange={event => setLetterBody(event.target.value)} placeholder="不用总结整段对话，只写你愿意为它负责的那部分。" /></label>
                    <button className="resonance-primary" disabled={visitorBusy || !letterTitle.trim() || !letterBody.trim()} onClick={() => void sendLetterToMatch()}>让慢信启程</button></>}
              </div>}
            </>}
          </div>}
        </>}
      </section>
      </div>

      <div className="product-space" hidden={productSpace !== "letters"}>
      <section className="letter-inbox" aria-label="抵达我的慢信">
        <div className="resonance-heading"><div><span className="eyebrow">LETTERS, ARRIVED</span><h2>只在抵达之后，才由你决定关系往哪里走</h2></div><span>{letterInbox.length} 封已抵达</span></div>
        <p className="resonance-intro">飞行中的信不会提前泄露正文。抵达后你可以阅读、婉拒、举报或屏蔽；屏蔽会阻断同一来信者之后的慢信。</p>
        {letterInbox.length === 0 ? <div className="network-empty">此刻没有已经抵达的慢信。</div> : <div className="inbox-list">
          {letterInbox.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{letter.status}</span></header>
            <p>{letter.letterBody}</p>
            {["READ", "REPLIED"].includes(letter.status) && <div className="letter-reply"><textarea aria-label={`回复「${letter.title}」`}
              value={replyDrafts[letter.id] ?? ""} onChange={event => setReplyDrafts(drafts => ({ ...drafts, [letter.id]: event.target.value }))}
              placeholder="写下你愿意负责的回应；它仍会慢慢抵达。" /><button disabled={!replyDrafts[letter.id]?.trim()} onClick={() => void replyWithLetter(letter)}>让回复慢信启程</button></div>}
            <div>
              {letter.status === "DELIVERED" && <button onClick={() => void actOnLetter(letter, "read")}>标记已读</button>}
              {["DELIVERED", "READ"].includes(letter.status) && <button onClick={() => void actOnLetter(letter, "decline")}>温和婉拒</button>}
              {["READ", "REPLIED"].includes(letter.status) && <button onClick={() => void requestConnection(letter)}>愿意认识对方</button>}
              {letter.status !== "BLOCKED" && <button onClick={() => void actOnLetter(letter, "block")}>屏蔽后续来信</button>}
              <button onClick={() => void reportLetter(letter)}>举报这封信</button>
            </div></article>)}
        </div>}
        <div className="connection-consent" aria-label="双向连接同意">
          <div><strong>等待你决定</strong>{connectionRequests.incoming.length === 0 ? <small>没有新的连接邀请</small> : connectionRequests.incoming.map(item =>
            <article key={item.id}><span>{item.nickname} 想在慢信之后认识你</span><div><button onClick={() => void decideConnection(item.id, "accept")}>我也愿意</button><button onClick={() => void decideConnection(item.id, "decline")}>暂不连接</button></div></article>)}</div>
          <div><strong>等待对方决定</strong>{connectionRequests.outgoing.length === 0 ? <small>没有等待中的邀请</small> : connectionRequests.outgoing.map(item => <article key={item.id}><span>{item.nickname}</span><small>尚未同意，不会提前开放真人连接</small></article>)}</div>
          <div><strong>双方已同意</strong>{friends.length === 0 ? <small>还没有建立真人连接</small> : friends.map(item => <article key={item.id}><span>{item.nickname}</span><button onClick={() => void leaveConnection(item.id)}>退出连接</button></article>)}</div>
        </div>
      </section>
      </div>

      <div className="product-space" hidden={productSpace !== "aurora"}>
      {skillSuggestion && <aside className="skill-suggestion" aria-label={skillLocale === "en-SG" ? "Aurora optional reflection suggestion" : "Aurora 的可选反思建议"} lang={skillLocale === "en-SG" ? "en-SG" : "zh-CN"}>
        <div><span>{skillLocale === "en-SG" ? "AURORA SUGGESTS · Will not run automatically" : "AURORA SUGGESTS · 不会自动运行"}</span><strong>{skillSuggestion.title}</strong><p>{skillSuggestion.reason}</p></div>
        <div><button type="button" onClick={openSuggestedSkill}>{skillLocale === "en-SG" ? "I decide whether to open it" : "由我决定是否打开"}</button>
          <button type="button" className="quiet" onClick={() => setSkillSuggestion(null)}>{skillLocale === "en-SG" ? "Not this time" : "这次不要"}</button></div>
      </aside>}

      <AuroraConversation messages={messages} activeTurnId={activeTurnId} draft={draft} sessionReady={Boolean(sessionId)}
        onDraftChange={setDraft} onSubmit={send} onStop={() => void stop()} />
      </div>

      <div className="product-space" hidden={productSpace !== "me"}>
        <MeSpace native={mobileState.native} connected={mobileState.connected} wakeIntentCount={wakeIntents.length}
          activeClaimCount={claims.filter(claim => claim.status === "ACTIVE").length}
          publicCapsuleCount={capsules.filter(capsule => capsule.visibilityStatus === "PUBLIC").length}
          friendCount={friends.length} onNavigate={navigateSpace} onRequestPush={() => void requestMobilePush()}
          onRequestMicrophone={() => void requestMobileMicrophone()} onLogout={() => void logout()} />
      </div>
      <div className="state global-state" role="status"><i className={activeTurnId ? "pulse" : ""} />{status}</div>
      <footer><a href="/pages/aurora-chat.html">经典界面</a><span>五空间 AppShell · 数据与能力持续保留</span><button type="button" onClick={() => void logout()}>安全退出</button></footer>
    </main>
  );
}

function Login({ native, onSuccess }: { native: boolean; onSuccess: () => Promise<void> }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  if (native) return <main className="login-shell"><section className="login">
    <span className="eyebrow">INNER COSMOS</span><h1>回到你的内宇宙</h1>
    <p>原生应用使用系统浏览器与 Authorization Code + PKCE 登录。密码不会进入 Aurora 应用。</p>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="send" type="button" onClick={() => void mobileOidc.beginLogin()
      .catch(reason => setError(reason instanceof Error ? reason.message : "无法启动安全登录"))}>使用身份提供方继续</button>
  </section></main>;
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
