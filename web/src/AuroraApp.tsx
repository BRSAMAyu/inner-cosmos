import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { api, apiConfigurationError, configureBearerAuth, hasConfiguredApiBase, replayTurnEvents, streamAurora, subscribeProactive, type ClaimCandidate, type CapsuleBoundary, type CapsuleFidelitySummary, type CapsuleGenomeVersion, type CapsuleMatch, type CapsulePreview, type CapsuleQuota, type CapsuleSandbox, type ConnectionRequests, type CorrectionCommand, type CorrectionImpact, type EchoCapsule, type MemoryCard, type MemoryOperation, type Notification, type DiscoverablePerson, type PersonaMessage, type PersonaSession, type PortraitDimension, type PublicCapsule, type PortraitHistoryEntry, type RelationMention, type RelationTimelinePoint, type RelationHealth, type PsychologyRetention, type PsychologySkillManifest, type PsychologySkillRun, type PsychologySkillSuggestion, type ResonanceStrategy, type SelfEvolution, type SlowLetter, type SocialConnection, type StarfieldDetail, type StarfieldScene, type StarfieldStar, type UnderstandingClaim, type UserCorrection, type WakeIntent } from "./api";
import { initialMobileState, mobileRuntime, type MobileRuntimeState } from "./mobile";
import { mobileOidc } from "./mobile-auth";
import type { AuroraStreamEvent, DialogMessage, TurnStatus } from "./protocol";
import { initialProductSpace, MeSpace, ProductShellNavigation, type ProductSpace } from "./components/ProductShell";
import { AuroraConversation, type AuroraUiMessage } from "./components/AuroraConversation";
import { AuroraSelfSpace } from "./components/AuroraSelfSpace";
import { UnderstandingCorrection, type CorrectionTarget } from "./components/UnderstandingCorrection";
import { ClaimCandidateReview } from "./components/ClaimCandidateReview";
import { MemoryStarfield } from "./components/MemoryStarfield";
import { CapsuleWorkbench } from "./components/CapsuleWorkbench";
import { ResonanceNetwork } from "./components/ResonanceNetwork";
import { PlazaDirectory } from "./components/PlazaDirectory";
import { PeopleDiscovery } from "./components/PeopleDiscovery";
import { RelationsView } from "./components/RelationsView";
import { LettersInbox } from "./components/LettersInbox";
import { PortraitView } from "./components/PortraitView";
import { AccountSettings, type AccountBusy } from "./components/AccountSettings";
import { PsychologySkillStudio, SkillSuggestionBanner, type SkillLocale } from "./components/PsychologySkillStudio";
import { ConnectError, LoadingText } from "./loading";

type RuntimeSignal = { stage: "idle" | "understanding" | "composing" | "speaking"; runtime: "single" | "dual"; relationshipMove?: string; repaired?: boolean };
const terminal = new Set<TurnStatus>(["COMPLETED", "INTERRUPTED", "CANCELLED"]);
const modes = [
  ["DAILY_TALK", "倾诉"], ["THOUGHT_CLARIFY", "整理"], ["SOCRATIC", "追问"],
  ["ACTION_SPLIT", "行动"], ["RELATION_REVIEW", "关系"]
] as const;
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
  const [bootstrapError, setBootstrapError] = useState<string | null>(null);
  const [activeTurnId, setActiveTurnId] = useState<number | null>(null);
  const [wakeIntents, setWakeIntents] = useState<WakeIntent[]>([]);
  const [wakeBusy, setWakeBusy] = useState(false);
  const [returnWhen, setReturnWhen] = useState("明天早上 8:30");
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [selfEvolution, setSelfEvolution] = useState<SelfEvolution | null>(null);
  const [selfBusy, setSelfBusy] = useState(false);
  const [correctionOld, setCorrectionOld] = useState("");
  const [correctionNew, setCorrectionNew] = useState("");
  const [correctionTarget, setCorrectionTarget] = useState<CorrectionTarget | null>(null);
  const [correctionImpact, setCorrectionImpact] = useState<CorrectionImpact | null>(null);
  const [correctionBusy, setCorrectionBusy] = useState(false);
  const [claims, setClaims] = useState<UnderstandingClaim[]>([]);
  const [corrections, setCorrections] = useState<UserCorrection[]>([]);
  const [retiringCorrectionId, setRetiringCorrectionId] = useState<number | null>(null);
  const [claimCandidates, setClaimCandidates] = useState<ClaimCandidate[]>([]);
  const [claimCandidateBusyId, setClaimCandidateBusyId] = useState<number | null>(null);
  const [portrait, setPortrait] = useState<PortraitDimension[]>([]);
  const [portraitHistory, setPortraitHistory] = useState<Record<string, PortraitHistoryEntry[]>>({});
  const [portraitCalibrated, setPortraitCalibrated] = useState<Record<string, boolean>>({});
  const [portraitBusy, setPortraitBusy] = useState<string | null>(null);
  const [accountBusy, setAccountBusy] = useState<AccountBusy>(null);
  const [accountMessage, setAccountMessage] = useState<string | null>(null);
  const [starfield, setStarfield] = useState<StarfieldScene | null>(null);
  const [starfieldBusy, setStarfieldBusy] = useState(false);
  const [memoryOperations, setMemoryOperations] = useState<MemoryOperation[]>([]);
  const [rollbackBusy, setRollbackBusy] = useState<number | null>(null);
  const [starfieldDetail, setStarfieldDetail] = useState<StarfieldDetail | null>(null);
  const [detailBusy, setDetailBusy] = useState<number | null>(null);
  const [importanceBusy, setImportanceBusy] = useState<number | null>(null);
  const [archiveBusy, setArchiveBusy] = useState<number | null>(null);
  const [memories, setMemories] = useState<MemoryCard[]>([]);
  const [capsules, setCapsules] = useState<EchoCapsule[]>([]);
  const [selectedCapsuleId, setSelectedCapsuleId] = useState<number | null>(null);
  const [genomeHistory, setGenomeHistory] = useState<CapsuleGenomeVersion[]>([]);
  const [fidelitySummary, setFidelitySummary] = useState<CapsuleFidelitySummary[]>([]);
  const [selectedMemoryIds, setSelectedMemoryIds] = useState<number[]>([]);
  const [capsuleName, setCapsuleName] = useState("");
  const [capsuleIntro, setCapsuleIntro] = useState("");
  const [capsulePreview, setCapsulePreview] = useState<CapsulePreview | null>(null);
  const [capsuleBusy, setCapsuleBusy] = useState(false);
  const [capsuleBoundary, setCapsuleBoundary] = useState<CapsuleBoundary | null>(null);
  const [boundaryBusy, setBoundaryBusy] = useState(false);
  const [sandboxQuestion, setSandboxQuestion] = useState("当你被误解时，通常会怎样表达自己的边界？");
  const [sandboxResult, setSandboxResult] = useState<CapsuleSandbox | null>(null);
  const [sandboxFeedback, setSandboxFeedback] = useState<string | null>(null);
  const [resonanceMatches, setResonanceMatches] = useState<CapsuleMatch[]>([]);
  const [publicCapsules, setPublicCapsules] = useState<PublicCapsule[]>([]);
  const [directoryPick, setDirectoryPick] = useState<PublicCapsule | null>(null);
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
  const [letterOutbox, setLetterOutbox] = useState<SlowLetter[]>([]);
  const [connectionRequests, setConnectionRequests] = useState<ConnectionRequests>({ incoming: [], outgoing: [] });
  const [friends, setFriends] = useState<SocialConnection[]>([]);
  const [people, setPeople] = useState<DiscoverablePerson[]>([]);
  const [peopleBusy, setPeopleBusy] = useState(false);
  const [relations, setRelations] = useState<RelationMention[]>([]);
  const [selectedRelation, setSelectedRelation] = useState<string | null>(null);
  const [relationTimeline, setRelationTimeline] = useState<RelationTimelinePoint[]>([]);
  const [relationHealth, setRelationHealth] = useState<RelationHealth | null>(null);
  const [relationBusy, setRelationBusy] = useState(false);
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
  const handleEventRef = useRef<(event: AuroraStreamEvent) => void>(() => undefined);
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
    setBootstrapError(null);
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
        api.psychologySkillRuns().then(rows => { setSkillRuns(rows); return rows; }),
        api.portrait().then(setPortrait),
        api.recentCorrections().then(setCorrections),
        api.plazaCapsules().then(setPublicCapsules).catch(() => undefined),
        api.letterOutbox().then(setLetterOutbox).catch(() => undefined),
        api.discoverPeople().then(setPeople).catch(() => undefined),
        api.claimCandidates().then(setClaimCandidates).catch(() => undefined),
        api.relations().then(setRelations).catch(() => undefined)
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
        // 非鉴权失败：过去只更新 status 却把 authenticated 停在 null，用户会永久卡在连接加载屏。
        // 现在进入明确的"错误态"，连接屏据此渲染错误 + 重试（恢复态）。
        const message = error instanceof Error ? error.message : "暂时无法连接你的内宇宙";
        setBootstrapError(message);
        setStatus(message);
      }
    }
  }, [replaceFromHistory]);

  const selectedCapsule = capsules.find(capsule => capsule.id === selectedCapsuleId) ?? null;
  // A capsule opened from the public plaza directory (not the curated match set) is wrapped in a
  // synthetic match so the existing visitor workbench (persona chat + slow letter) works unchanged.
  const directoryMatch: CapsuleMatch | null = directoryPick && !resonanceMatches.some(match => match.capsule.id === directoryPick.id)
    ? { capsule: directoryPick, matchScore: 0, matchReasons: [], matchSummary: "你在广场里主动找到了它，而不是被推荐的。",
        resonant: false, strategy: "SERENDIPITY", strategyLabel: "主动发现", strategyDescription: "你在共鸣广场里主动走近了它。" }
    : null;
  const visitorMatch = resonanceMatches.find(match => match.capsule.id === visitorMatchId)
    ?? (directoryMatch && directoryMatch.capsule.id === visitorMatchId ? directoryMatch : null)
    ?? resonanceMatches[0] ?? null;
  const selectedSkill = skills.find(skill => skill.id === selectedSkillId) ?? skills[0] ?? null;

  useEffect(() => {
    if (!selectedCapsule) { setGenomeHistory([]); setFidelitySummary([]); setCapsuleBoundary(null); return; }
    const ids = [...selectedCapsule.authorizedMemoryIds.matchAll(/\d+/g)].map(match => Number(match[0]));
    setSelectedMemoryIds(ids);
    setSandboxResult(null);
    setSandboxFeedback(null);
    void api.capsuleGenomeHistory(selectedCapsule.id).then(setGenomeHistory)
      .catch(error => setStatus(error instanceof Error ? error.message : "暂时无法读取共鸣体版本"));
    void api.capsuleFidelity(selectedCapsule.id).then(setFidelitySummary).catch(() => setFidelitySummary([]));
    void api.capsuleBoundary(selectedCapsule.id).then(setCapsuleBoundary).catch(() => setCapsuleBoundary(null));
  }, [selectedCapsuleId, selectedCapsule?.activeGenomeVersionId]);

  const saveCapsuleBoundary = async (boundary: Partial<CapsuleBoundary>) => {
    if (!selectedCapsule) return;
    setBoundaryBusy(true);
    try {
      await api.updateCapsuleBoundary(selectedCapsule.id, boundary);
      setCapsuleBoundary(await api.capsuleBoundary(selectedCapsule.id));
      setStatus("边界已更新。只有你能改动它，公开人格会按新的边界回应访客。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法保存这个共鸣体的边界"); }
    finally { setBoundaryBusy(false); }
  };

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
  handleEventRef.current = handleEvent;

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

  const correctionCommand = (): CorrectionCommand => correctionTarget ? {
    targetType: "MEMORY_CARD", targetId: correctionTarget.id, fieldName: "summary",
    oldValue: null, newValue: correctionNew.trim(), reason: "用户在记忆星空中直接纠正这条记忆"
  } : {
    targetType: "AURORA_UNDERSTANDING", targetId: 0, fieldName: "self_understanding",
    oldValue: correctionOld.trim() || null, newValue: correctionNew.trim(), reason: "用户在 Inner Cosmos 中主动校准"
  };

  const beginMemoryCorrection = (star: StarfieldStar) => {
    setCorrectionTarget({ id: star.id, label: star.title });
    setCorrectionOld(""); setCorrectionNew(""); setCorrectionImpact(null);
    navigateSpace("cosmos");
    window.setTimeout(() => document.querySelector(".understanding-space")?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
  };

  const clearCorrectionTarget = () => { setCorrectionTarget(null); setCorrectionImpact(null); };

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
      const affectedMemory = result.propagation.some(row => row.targetKind === "MEMORY");
      if (affectedMemory) {
        await Promise.all([
          api.starfield(starfield?.mode ?? "TIME").then(setStarfield),
          api.memoryCards().then(setMemories),
          api.myCapsules().then(setCapsules)
        ]);
      }
      void api.recentCorrections().then(setCorrections).catch(() => undefined);
      setCorrectionImpact(null); setCorrectionOld(""); setCorrectionNew(""); setCorrectionTarget(null);
      setStatus("已校准。旧理解仍可追溯，Aurora、星空与共鸣体上下文会按确认结果同步。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "这次纠正没有保存，任何下游都未改变"); }
    finally { setCorrectionBusy(false); }
  };

  const retireCorrection = async (id: number) => {
    setRetiringCorrectionId(id);
    try {
      await api.retireCorrection(id);
      // Retiring a correction reactivates the understanding it had superseded, so refetch both
      // the history list and the active claims to reflect the restored "current fact".
      const [freshCorrections, freshClaims] = await Promise.all([
        api.recentCorrections(), api.understandingClaims()
      ]);
      setCorrections(freshCorrections);
      setClaims(freshClaims);
      setStatus("这条更正已退休。Aurora 不再据此调整对你的理解，之前被它替代的理解会重新成为当前事实。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法让这条更正退休"); }
    finally { setRetiringCorrectionId(null); }
  };

  const confirmClaimCandidate = async (id: number) => {
    setClaimCandidateBusyId(id);
    try {
      const result = await api.confirmClaimCandidate(id);
      // Promotion goes through the correction path, so a new ACTIVE claim now exists and the
      // candidate leaves the pending list. Refresh claims so the confirmed understanding shows.
      setClaimCandidates(current => current.filter(candidate => candidate.id !== id));
      setClaims(current => [result.activeClaim, ...current]);
      void api.recentCorrections().then(setCorrections).catch(() => undefined);
      setStatus("已记住。这条理解现在是你确认的事实，会影响以后每次对话。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "这条理解没能保存"); }
    finally { setClaimCandidateBusyId(null); }
  };

  const dismissClaimCandidate = async (id: number) => {
    setClaimCandidateBusyId(id);
    try {
      await api.dismissClaimCandidate(id);
      setClaimCandidates(current => current.filter(candidate => candidate.id !== id));
      setStatus("好的，我不会把这条当作对你的理解。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法忽略这条理解"); }
    finally { setClaimCandidateBusyId(null); }
  };

  const loadPortraitHistory = async (dim: string) => {
    if (portraitHistory[dim]) return;
    try { setPortraitHistory(current => ({ ...current, [dim]: [] })); // mark as loading/loaded to avoid duplicate fetches
      const rows = await api.portraitHistory(dim);
      setPortraitHistory(current => ({ ...current, [dim]: rows }));
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法回看这一面的变化"); }
  };

  const submitPortraitCalibration = async (dim: string, oldValue: string, newValue: string) => {
    const trimmed = newValue.trim();
    if (!trimmed) return;
    setPortraitBusy(dim);
    try {
      await api.confirmCorrection({
        targetType: "PORTRAIT_DIM", targetId: 0, fieldName: dim,
        oldValue: oldValue || null, newValue: trimmed, reason: "用户在「Aurora 眼中的你」页面校准了这一维度"
      });
      // The correction coexists alongside Aurora's own observation rather than overwriting it
      // (RUN-006 semantics) — mark calibrated locally instead of refetching/replacing the value.
      setPortraitCalibrated(current => ({ ...current, [dim]: true }));
      setStatus("记下了。我会带着你这份看法继续理解你。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "没能存下，待会儿再试一次"); }
    finally { setPortraitBusy(null); }
  };

  const changeAccountPassword = async (oldPassword: string, newPassword: string) => {
    setAccountBusy("password");
    try { await api.changePassword(oldPassword, newPassword); setAccountMessage("密码已更新"); }
    catch (error) { setAccountMessage(error instanceof Error ? error.message : "密码修改失败"); }
    finally { setAccountBusy(null); }
  };

  const exportAccountData = async () => {
    setAccountBusy("export");
    try {
      const data = await api.exportData();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url; anchor.download = `inner-cosmos-export-${new Date().toISOString().slice(0, 10)}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
      setAccountMessage("数据已导出");
    } catch (error) { setAccountMessage(error instanceof Error ? error.message : "导出失败"); }
    finally { setAccountBusy(null); }
  };

  const deleteAccount = async (password: string) => {
    setAccountBusy("delete");
    try {
      await api.deleteAccount(password);
      setAuthenticated(false); setSessionId(null); setMessages([]); setPersonaSession(null); setPersonaMessages([]);
      setAccountMessage(null);
    } catch (error) { setAccountMessage(error instanceof Error ? error.message : "账户删除失败"); }
    finally { setAccountBusy(null); }
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

  const updateMemoryImportance = async (id: number, importance: number) => {
    setImportanceBusy(id);
    try {
      await api.updateMemoryImportance(id, importance);
      // Importance recomputes the card's gravity, so the star's size/position in the field shifts;
      // refetch the scene, the open detail, and the card list so all three stay consistent.
      const [scene, detail] = await Promise.all([
        api.starfield(starfield?.mode ?? "TIME"), api.starfieldDetail(id), api.memoryCards().then(setMemories)
      ]);
      setStarfield(scene);
      setStarfieldDetail(detail);
      setStatus("重要度已更新，这颗星的引力随之调整。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法调整这颗记忆的重要度"); }
    finally { setImportanceBusy(null); }
  };

  const archiveMemory = async (id: number) => {
    setArchiveBusy(id);
    try {
      await api.archiveMemory(id);
      // Archiving runs a versioned, rollbackable ARCHIVE operation: the star leaves the current
      // field, the operation appears in the recent-changes list, and the card list updates.
      const [scene, ops] = await Promise.all([
        api.starfield(starfield?.mode ?? "TIME"), api.memoryOperations(), api.memoryCards().then(setMemories)
      ]);
      setStarfield(scene);
      setMemoryOperations(ops);
      setStarfieldDetail(null);
      setStatus("这颗记忆已归档，不再出现在当前星空；你可以在“最近的记忆变更”里撤回。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法归档这颗记忆"); }
    finally { setArchiveBusy(null); }
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
      void api.capsuleFidelity(selectedCapsule.id).then(setFidelitySummary).catch(() => undefined);
      setStatus("反馈已保存为下一次 Genome 改进信号；当前公开版本没有暗中漂移。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "反馈暂时没有保存"); }
    finally { setCapsuleBusy(false); }
  };

  const chooseVisitorMatch = (capsuleId: number) => {
    setVisitorMatchId(capsuleId);
    setPersonaSession(null); setPersonaMessages([]); setPersonaQuota(null); setSentLetter(null); setLetterBody("");
  };

  const openDirectoryCapsule = (capsule: PublicCapsule) => {
    setDirectoryPick(capsule);
    chooseVisitorMatch(capsule.id);
    setStatus(`你从广场走近了「${capsule.pseudonym}」。它是授权 AI 共鸣体，不是真人实时在线。`);
    window.setTimeout(() => document.querySelector(".visitor-workbench, .resonance-network")?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
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
      void api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
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
      void api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setReplyDrafts(drafts => ({ ...drafts, [letter.id]: "" }));
      setStatus("回复慢信已启程。它仍会经过时间，而不是变成即时聊天。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "回复慢信没有启程"); }
  };

  const refreshConnections = async () => {
    const [requests, accepted, discoverable] = await Promise.all([
      api.connectionRequests(), api.friends(), api.discoverPeople().catch(() => people)
    ]);
    setConnectionRequests(requests); setFriends(accepted); setPeople(discoverable);
  };

  const requestPersonConnection = async (userId: number) => {
    setPeopleBusy(true);
    try {
      await api.requestFriend(userId); await refreshConnections();
      setStatus("邀请已发出。对方同意前不会开放任何私密内容，也不会变成即时聊天。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法发出这个邀请"); }
    finally { setPeopleBusy(false); }
  };

  const openRelation = async (label: string) => {
    setSelectedRelation(label); setRelationBusy(true);
    setRelationTimeline([]); setRelationHealth(null);
    try {
      const [timeline, health] = await Promise.all([
        api.relationTimeline(label),
        api.relationHealth(label).catch(() => null)
      ]);
      setRelationTimeline(timeline); setRelationHealth(health);
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时读不到这段关系的时间线"); }
    finally { setRelationBusy(false); }
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
  if (authenticated === null) return <main className="login-shell"><div className="login">
    {bootstrapError
      ? <ConnectError message={bootstrapError} onRetry={() => void bootstrap()} />
      : <LoadingText busy>正在连接你的内宇宙</LoadingText>}
  </div></main>;
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
      <ClaimCandidateReview candidates={claimCandidates} locale={skillLocale} busyId={claimCandidateBusyId}
        onConfirm={id => void confirmClaimCandidate(id)} onDismiss={id => void dismissClaimCandidate(id)} />
      <UnderstandingCorrection claims={claims} oldValue={correctionOld} newValue={correctionNew} impact={correctionImpact} busy={correctionBusy} target={correctionTarget}
        corrections={corrections} retiringId={retiringCorrectionId}
        onOldValue={value => { setCorrectionOld(value); setCorrectionImpact(null); }} onNewValue={value => { setCorrectionNew(value); setCorrectionImpact(null); }}
        onPreview={() => void previewCorrection()} onCancelPreview={() => setCorrectionImpact(null)} onConfirm={() => void confirmCorrection()} onClearTarget={clearCorrectionTarget}
        onRetire={id => void retireCorrection(id)} />

      {starfield && <MemoryStarfield starfield={starfield} starfieldBusy={starfieldBusy} onChangeMode={mode => void changeStarfieldMode(mode)}
        starfieldDetail={starfieldDetail} detailBusy={detailBusy} onRevealStar={id => void revealStar(id)} onCloseDetail={() => setStarfieldDetail(null)}
        memoryOperations={memoryOperations} rollbackBusy={rollbackBusy} onRollback={operation => void rollbackMemoryOperation(operation)} onCorrectMemory={beginMemoryCorrection}
        onUpdateImportance={(id, importance) => void updateMemoryImportance(id, importance)} onArchive={id => void archiveMemory(id)}
        importanceBusy={importanceBusy} archiveBusy={archiveBusy} />}

      <PsychologySkillStudio skills={skills} skillRuns={skillRuns} selectedSkill={selectedSkill} skillAnswers={skillAnswers}
        skillConsent={skillConsent} skillRetention={skillRetention} skillBusy={skillBusy} skillLocale={skillLocale}
        onLocaleChange={setSkillLocale} onSelectSkill={skillId => { setSelectedSkillId(skillId); setSkillAnswers({}); setSkillConsent(false); }}
        onAnswerChange={(key, value) => setSkillAnswers(current => ({ ...current, [key]: value }))}
        onRetentionChange={setSkillRetention} onConsentChange={setSkillConsent} onRun={() => void runPsychologySkill()}
        onContinueWithAurora={continueSkillWithAurora} onRevokeRun={id => void revokePsychologyRun(id)} />
      </div>

      <div className="product-space" hidden={productSpace !== "resonance"}>
      <CapsuleWorkbench capsules={capsules} selectedCapsuleId={selectedCapsuleId} selectedCapsule={selectedCapsule}
        selectableMemories={selectableMemories} selectedMemoryIds={selectedMemoryIds} capsuleName={capsuleName} capsuleIntro={capsuleIntro}
        capsulePreview={capsulePreview} capsuleBusy={capsuleBusy} genomeHistory={genomeHistory} fidelitySummary={fidelitySummary} sandboxQuestion={sandboxQuestion}
        sandboxResult={sandboxResult} sandboxFeedback={sandboxFeedback}
        onSelectCapsule={id => { setSelectedCapsuleId(id); if (id === null) { setSelectedMemoryIds([]); setCapsulePreview(null); } }}
        onToggleMemory={toggleCapsuleMemory} onCapsuleName={setCapsuleName} onCapsuleIntro={setCapsuleIntro}
        onPreviewNewCapsule={() => void previewNewCapsule()} onCancelPreview={() => setCapsulePreview(null)} onCreateCapsule={() => void createCapsule()}
        onRecompile={() => void recompileSelectedCapsule()} onSandboxQuestion={setSandboxQuestion} onRunSandbox={() => void runCapsuleSandbox()}
        onRateSandbox={rating => void rateCapsuleSandbox(rating)} onPublish={() => void publishSelectedCapsule()}
        onPause={() => void pauseSelectedCapsule()} onArchive={() => void archiveSelectedCapsule()}
        boundary={capsuleBoundary} boundaryBusy={boundaryBusy} onSaveBoundary={boundary => void saveCapsuleBoundary(boundary)} />

      <PlazaDirectory capsules={publicCapsules} activeCapsuleId={visitorMatch?.capsule.id ?? null} busy={visitorBusy}
        onOpenCapsule={openDirectoryCapsule} />

      <ResonanceNetwork resonanceMatches={resonanceMatches} resonanceStrategy={resonanceStrategy} visitorBusy={visitorBusy}
        visitorMatch={visitorMatch} personaSession={personaSession} personaMessages={personaMessages} personaDraft={personaDraft}
        personaQuota={personaQuota} letterTitle={letterTitle} letterBody={letterBody} sentLetter={sentLetter}
        onChooseStrategy={strategy => void chooseResonanceStrategy(strategy)} onChooseMatch={chooseVisitorMatch}
        onStartPersonaConversation={() => void startPersonaConversation()} onPersonaDraftChange={setPersonaDraft}
        onSendPersonaTurn={() => void sendPersonaTurn()} onLetterTitleChange={setLetterTitle} onLetterBodyChange={setLetterBody}
        onSendLetter={() => void sendLetterToMatch()} />
      </div>

      <div className="product-space" hidden={productSpace !== "letters"}>
      <PeopleDiscovery people={people} busy={peopleBusy} onRequest={userId => void requestPersonConnection(userId)} />
      <RelationsView relations={relations} selected={selectedRelation} timeline={relationTimeline} health={relationHealth} busy={relationBusy} onSelect={label => void openRelation(label)} />

      <LettersInbox letterInbox={letterInbox} letterOutbox={letterOutbox} replyDrafts={replyDrafts} connectionRequests={connectionRequests} friends={friends}
        onReplyDraftChange={(letterId, value) => setReplyDrafts(drafts => ({ ...drafts, [letterId]: value }))}
        onReply={letter => void replyWithLetter(letter)} onActOnLetter={(letter, action) => void actOnLetter(letter, action)}
        onReportLetter={letter => void reportLetter(letter)} onRequestConnection={letter => void requestConnection(letter)}
        onDecideConnection={(id, decision) => void decideConnection(id, decision)} onLeaveConnection={id => void leaveConnection(id)} />
      </div>

      <div className="product-space" hidden={productSpace !== "aurora"}>
      {skillSuggestion && <SkillSuggestionBanner suggestion={skillSuggestion} locale={skillLocale}
        onOpen={openSuggestedSkill} onDismiss={() => setSkillSuggestion(null)} />}

      <AuroraConversation messages={messages} activeTurnId={activeTurnId} draft={draft} sessionReady={Boolean(sessionId)}
        onDraftChange={setDraft} onSubmit={send} onStop={() => void stop()} />
      </div>

      <div className="product-space" hidden={productSpace !== "me"}>
        <MeSpace native={mobileState.native} connected={mobileState.connected} wakeIntentCount={wakeIntents.length}
          activeClaimCount={claims.filter(claim => claim.status === "ACTIVE").length}
          publicCapsuleCount={capsules.filter(capsule => capsule.visibilityStatus === "PUBLIC").length}
          friendCount={friends.length} onNavigate={navigateSpace} onRequestPush={() => void requestMobilePush()}
          onRequestMicrophone={() => void requestMobileMicrophone()} onLogout={() => void logout()} />
        <PortraitView dimensions={portrait} history={portraitHistory} calibrated={portraitCalibrated} busyDim={portraitBusy}
          onLoadHistory={dim => void loadPortraitHistory(dim)} onCalibrate={(dim, oldValue, newValue) => void submitPortraitCalibration(dim, oldValue, newValue)} />
        <AccountSettings busy={accountBusy} message={accountMessage} onChangePassword={(oldPassword, newPassword) => void changeAccountPassword(oldPassword, newPassword)}
          onExportData={() => void exportAccountData()} onDeleteAccount={password => void deleteAccount(password)} />
      </div>
      <div className="state global-state" role="status"><i className={activeTurnId ? "pulse" : ""} />{status}</div>
      <footer><a href="/pages/dashboard.html">尚未迁移的工具</a><span>五空间 AppShell · 数据与能力持续保留</span><button type="button" onClick={() => void logout()}>安全退出</button></footer>
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
