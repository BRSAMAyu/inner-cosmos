import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { Capacitor } from "@capacitor/core";
import { api, apiConfigurationError, configureBearerAuth, hasConfiguredApiBase, transcribeAudio, type ClaimCandidate, type CapsuleBoundary, type CapsuleFidelitySummary, type CapsuleGenomeVersion, type CapsuleMatch, type CapsulePreview, type CapsuleQuota, type CapsuleSandbox, type CorrectionCommand, type CorrectionImpact, type EchoCapsule, type MemoryCard, type MemoryOperation, type PersonaMessage, type PersonaSession, type PortraitDimension, type PublicCapsule, type PortraitHistoryEntry, type PsychologyRetention, type PsychologySkillManifest, type PsychologySkillRun, type PsychologySkillSuggestion, type ResonanceStrategy, type SelfEvolution, type SlowLetter, type StarfieldDetail, type StarfieldScene, type StarfieldStar, type UnderstandingClaim, type UserCorrection } from "./api";
import { initialMobileState, mobileRuntime, type MobileRuntimeState } from "./mobile";
import { mobileOidc } from "./mobile-auth";
import { capsulePath, letterThreadPath, MeSpace, productSpaceFromPath, productSpaces, ProductShellNavigation, resourceFromPath, spacePath, type ProductSpace } from "./components/ProductShell";
import { AuroraConversation } from "./components/AuroraConversation";
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
import { DataRightsPanel } from "./components/DataRightsPanel";
import { LocaleToggle } from "./components/LocaleToggle";
import type { DataRetractionReceipt } from "./api";
import { loadLocale, saveLocale, type Locale } from "./i18n";
import { AuthGate } from "./components/AuthGate";
import { PsychologySkillStudio, SkillSuggestionBanner, type SkillLocale } from "./components/PsychologySkillStudio";
import { ConnectError, LoadingText } from "./loading";
import { useAuroraSession } from "./hooks/useAuroraSession";
import { useConnectionsAndLetters } from "./hooks/useConnectionsAndLetters";

// The Aurora conversation/session domain (message list, streaming/turn status, interrupt/stop,
// mode picker, WakeIntent negotiate, session bootstrap/replay) has been extracted into
// ./hooks/useAuroraSession.ts (B1 domain-hook decomposition, first slice) -- see
// docs/goal/tracks/track-b-status.yml and evidence/track-b/README.md for what moved and why.
const modes = [
  ["DAILY_TALK", "倾诉"], ["THOUGHT_CLARIFY", "整理"], ["SOCRATIC", "追问"],
  ["ACTION_SPLIT", "行动"], ["RELATION_REVIEW", "关系"]
] as const;

export function AuroraApp() {
  // Real client routing (react-router HashRouter, mounted in main.tsx): the active space is
  // derived from the current route on every render instead of being copied into state once
  // at mount. This is what makes an expired-auth deep link resume the right space after
  // re-login "for free" -- the route never changes underneath the AuthGate swap, so once
  // `authenticated` flips back to true, `productSpace` is still whatever the URL says.
  const location = useLocation();
  const navigate = useNavigate();
  const productSpace = useMemo(() => productSpaceFromPath(location.pathname), [location.pathname]);
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [status, setStatus] = useState("正在连接你的内宇宙…");
  const [bootstrapError, setBootstrapError] = useState<string | null>(null);
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
  const [dataRightsReceipts, setDataRightsReceipts] = useState<DataRetractionReceipt[]>([]);
  const [dataRightsLoading, setDataRightsLoading] = useState(false);
  const [dataRightsLoaded, setDataRightsLoaded] = useState(false);
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
  const [skills, setSkills] = useState<PsychologySkillManifest[]>([]);
  const [skillRuns, setSkillRuns] = useState<PsychologySkillRun[]>([]);
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null);
  const [skillAnswers, setSkillAnswers] = useState<Record<string, string>>({});
  const [skillConsent, setSkillConsent] = useState(false);
  const [skillRetention, setSkillRetention] = useState<PsychologyRetention>("DISCARD_AFTER_SESSION");
  const [skillBusy, setSkillBusy] = useState(false);
  const [skillSuggestion, setSkillSuggestion] = useState<PsychologySkillSuggestion | null>(null);
  const [skillLocale, setSkillLocale] = useState<SkillLocale>(() => loadLocale());
  const [visitorBusy, setVisitorBusy] = useState(false);
  const [mobileState, setMobileState] = useState<MobileRuntimeState>(initialMobileState);
  const bootstrappedRef = useRef(false);
  const bootstrapCallRef = useRef(0);

  // Aurora conversation/session domain (message list, streaming/turn status, interrupt/stop, mode
  // picker, WakeIntent negotiate, session bootstrap/replay) -- extracted into its own hook; see
  // web/src/hooks/useAuroraSession.ts.
  const auroraSession = useAuroraSession({
    authenticated, skillLocale, onSkillSuggestion: setSkillSuggestion, setStatus
  });

  // Connections/letters domain (People Discovery, relation mentions/timeline, connection
  // requests/friends, slow-letter inbox/outbox/threads) -- extracted into its own hook; see
  // web/src/hooks/useConnectionsAndLetters.ts.
  const connectionsAndLetters = useConnectionsAndLetters({ setStatus });

  const navigateSpace = useCallback((space: ProductSpace) => {
    navigate(spacePath(space));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [navigate]);

  // Nested resource deep links: opening /resonance/capsule/:id selects that capsule, so a shared
  // link or a back/forward step lands on the exact capsule rather than the space default. The
  // capsule domain still owns loading/rendering; this only maps the URL id onto the existing
  // selection. If the id is not among the loaded capsules, selectedCapsule resolves to null and the
  // workbench shows its normal empty state -- no crash on a stale/foreign link.
  useEffect(() => {
    const resource = resourceFromPath(location.pathname);
    if (resource.space === "resonance" && resource.resource === "capsule" && resource.id != null) {
      setSelectedCapsuleId(current => current === resource.id ? current : resource.id);
    }
    if (resource.space === "letters" && resource.resource === "thread" && resource.id != null
        && connectionsAndLetters.selectedThreadId !== resource.id) {
      void connectionsAndLetters.openThread(resource.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  // One-time redirect for bookmarks/links made before this checkpoint, which used
  // `?space=<x>` on the app's single path instead of a real route. Cheap and low-risk: a
  // stale link should not 404 or silently ignore the requested space just because routing
  // moved on. Only fires when there is no hash-route yet (i.e. the link predates routing);
  // once any real route is present it always wins.
  useEffect(() => {
    if (window.location.hash) return;
    const legacySpace = new URLSearchParams(window.location.search).get("space");
    if (legacySpace && productSpaces.some(([space]) => space === legacySpace)) {
      navigate(spacePath(legacySpace as ProductSpace), { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const bootstrap = useCallback(async () => {
    const call = ++bootstrapCallRef.current;
    setBootstrapError(null);
    try {
      // The session/WakeIntent-return resolution stays sequential (not part of the Promise.all
      // below) so a superseded bootstrap call (e.g. mobile OIDC re-init racing a previous
      // bootstrap) can abort before firing every other domain's initial fetch -- exactly the
      // guard this function had before the Aurora session domain moved into its own hook.
      const resolved = await auroraSession.resolveSession(() => call !== bootstrapCallRef.current);
      if (resolved.aborted) return;
      let loadedCapsules: EchoCapsule[] = [];
      let loadedMatches: CapsuleMatch[] = [];
      let loadedSkills: PsychologySkillManifest[] = [];
      await Promise.all([
        auroraSession.replaceFromHistory(resolved.sessionId),
        auroraSession.loadWakeIntents(),
        auroraSession.loadNotifications(),
        api.selfEvolution().then(setSelfEvolution),
        api.understandingClaims().then(setClaims),
        api.starfield("TIME").then(setStarfield),
        api.memoryOperations().then(setMemoryOperations),
        api.memoryCards().then(setMemories),
        api.myCapsules().then(rows => { setCapsules(rows); loadedCapsules = rows; }),
        api.resonanceMatches().then(rows => { setResonanceMatches(rows); loadedMatches = rows; }),
        connectionsAndLetters.loadLetterInbox(),
        connectionsAndLetters.loadConnectionRequests(),
        connectionsAndLetters.loadFriends(),
        api.psychologySkills().then(rows => { setSkills(rows); loadedSkills = rows; }),
        api.psychologySkillRuns().then(setSkillRuns),
        api.portrait().then(setPortrait),
        api.recentCorrections().then(setCorrections),
        api.plazaCapsules().then(setPublicCapsules).catch(() => undefined),
        connectionsAndLetters.loadLetterOutbox(),
        connectionsAndLetters.loadPeople(),
        api.claimCandidates().then(setClaimCandidates).catch(() => undefined),
        connectionsAndLetters.loadRelations(),
        connectionsAndLetters.loadLetterThreads()
      ]);
      if (call !== bootstrapCallRef.current) return;
      setAuthenticated(true);
      const firstVisibleCapsule = loadedCapsules.find(capsule => capsule.visibilityStatus !== "ARCHIVED");
      if (firstVisibleCapsule) setSelectedCapsuleId(current => current ?? firstVisibleCapsule.id);
      setVisitorMatchId(current => current ?? loadedMatches[0]?.capsule.id ?? null);
      setSelectedSkillId(current => current ?? loadedSkills[0]?.id ?? null);
      setStatus(resolved.returning
        ? `Aurora 按约定回来了：${resolved.returning.purpose}`
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
  }, [auroraSession.resolveSession, auroraSession.replaceFromHistory, auroraSession.loadWakeIntents, auroraSession.loadNotifications,
      connectionsAndLetters.loadLetterInbox, connectionsAndLetters.loadConnectionRequests, connectionsAndLetters.loadFriends,
      connectionsAndLetters.loadLetterOutbox, connectionsAndLetters.loadPeople, connectionsAndLetters.loadRelations, connectionsAndLetters.loadLetterThreads]);

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

  // The mobile-runtime bridge stays here (mobileState is a cross-cutting concern used by the
  // top-level gate, the "me" space and the mobile-presence banner, not Aurora-conversation-only),
  // but its resume/wake-intent callbacks now delegate the actual recover-or-replay/session logic
  // to the Aurora session hook rather than reaching into its (now private) refs directly.
  useEffect(() => {
    let cancelled = false;
    let cleanup: (() => Promise<void>) | undefined;
    const resumeFromDurableState = async () => {
      if (cancelled) return;
      await auroraSession.resumeConversation();
      if (!cancelled) void auroraSession.refreshNotifications();
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
      onWakeIntent: auroraSession.openMobileWakeIntent,
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
  }, [auroraSession.openMobileWakeIntent, auroraSession.resumeConversation, auroraSession.refreshNotifications]);

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

  const loadDataRightsReceipts = async () => {
    setDataRightsLoading(true);
    try { setDataRightsReceipts(await api.dataRightsReceipts()); setDataRightsLoaded(true); }
    catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法读取数据权利回执"); }
    finally { setDataRightsLoading(false); }
  };

  // App-wide language: initialized from detection (loadLocale), overridable + persisted here so the
  // choice survives reloads. skillLocale is the single shared locale state (see i18n.ts).
  const changeLocale = (locale: Locale) => { setSkillLocale(locale); saveLocale(locale); };

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
      setAuthenticated(false); auroraSession.resetSession(); setPersonaSession(null); setPersonaMessages([]);
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
      void connectionsAndLetters.loadLetterOutbox();
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
      setAuthenticated(false); auroraSession.resetSession(); setPersonaSession(null); setPersonaMessages([]);
      setStatus(remoteWarning ?? "已安全退出");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出"); }
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
    auroraSession.setDraft(english ? `I just completed a reflection. It said: ${summary}\nI'd like to continue, but please don't treat it as a diagnosis.`
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
  if (!authenticated) return <AuthGate native={mobileState.native} onSuccess={bootstrap} />;

  return (
    <main className="shell" data-product-space={productSpace}>
      {/* Real routes for the five spaces. Each space's content below still mounts
          unconditionally and toggles via `hidden` (not <Route element>) so switching
          spaces never remounts/loses in-progress state (draft text, scroll position,
          sandbox results, etc.) -- exactly how it worked before routing, just now driven
          by a real, shareable, back/forward-correct path instead of a `space` state
          variable. <Routes> here only canonicalizes the URL itself: root and any
          unrecognized path redirect to /aurora, and every known (sub-)path is left alone. */}
      <Routes>
        <Route path="/" element={<Navigate to={spacePath("aurora")} replace />} />
        <Route path="/aurora/*" element={null} />
        <Route path="/cosmos/*" element={null} />
        <Route path="/resonance/*" element={null} />
        <Route path="/connections/letters/*" element={null} />
        <Route path="/me/*" element={null} />
        <Route path="*" element={<Navigate to={spacePath("aurora")} replace />} />
      </Routes>
      <ProductShellNavigation active={productSpace} onNavigate={navigateSpace} />

      <div className="product-space" hidden={productSpace !== "aurora"}>
      <header className="hero">
        <div>
          <span className="eyebrow">INNER COSMOS · AURORA</span>
          <h1>可以被打断的陪伴，<br />才是真的在听。</h1>
          <p>你不需要等 Aurora 说完。新消息会成为新的理解输入，而不是错误。</p>
          <div className={`runtime-signal ${auroraSession.runtimeSignal.stage}`} aria-label="Aurora 当前回应状态">
            <span>{auroraSession.runtimeSignal.stage === "understanding" ? "正在理解" : auroraSession.runtimeSignal.stage === "composing" ? "正在组织" : auroraSession.runtimeSignal.stage === "speaking" ? "正在回应" : "在这里"}</span>
            {auroraSession.runtimeSignal.runtime === "dual" && <small>理解与表达双核协作</small>}
            {auroraSession.runtimeSignal.relationshipMove && <small>关系动作 · {auroraSession.runtimeSignal.relationshipMove}</small>}
            {auroraSession.runtimeSignal.repaired && <small>回应已通过边界复核</small>}
          </div>
        </div>
        <div className="orb" aria-hidden="true"><span /></div>
      </header>

      <nav className="modes" aria-label="对话模式">
        {modes.map(([value, label]) => <button key={value} className={auroraSession.mode === value ? "active" : ""} onClick={() => auroraSession.setMode(value)}>{label}</button>)}
      </nav>

      {/* The composer sits directly after the hero/mode-picker, before the WakeIntent and
          Self/Emergence "capability display" panels below, so a first-time user (mobile
          especially) can reach it without scrolling past those panels first. Previously this
          lived in a second, disconnected `aurora` block far down in DOM order (after the
          cosmos/resonance/letters spaces) purely because of source-file history -- see
          golden-journeys.md J1 step 4 and 对齐文档/20 4.3's "旅程像能力陈列" finding. */}
      {skillSuggestion && <SkillSuggestionBanner suggestion={skillSuggestion} locale={skillLocale}
        onOpen={openSuggestedSkill} onDismiss={() => setSkillSuggestion(null)} />}

      <AuroraConversation messages={auroraSession.messages} activeTurnId={auroraSession.activeTurnId}
        thinkingStage={auroraSession.activeTurnId !== null && (auroraSession.runtimeSignal.stage === "understanding" || auroraSession.runtimeSignal.stage === "composing") ? auroraSession.runtimeSignal.stage : null}
        draft={auroraSession.draft} sessionReady={Boolean(auroraSession.sessionId)}
        onDraftChange={auroraSession.setDraft} onSubmit={auroraSession.send} onStop={() => void auroraSession.stop()}
        onTranscribe={async blob => {
          try { const result = await transcribeAudio(blob); return result.text; }
          catch (error) { setStatus(error instanceof Error ? error.message : "语音转写暂时不可用"); return ""; }
        }} locale={skillLocale} />

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
          <div className="return-negotiate"><label>什么时候合适<input aria-label="回来时间" value={auroraSession.returnWhen} onChange={event => auroraSession.setReturnWhen(event.target.value)} /></label>
          <button type="button" disabled={auroraSession.wakeBusy || !auroraSession.returnWhen.trim()} onClick={() => void auroraSession.scheduleReturn()}>和 Aurora 约好</button></div></div>
        {auroraSession.wakeIntents.length === 0 ? <p className="returns-empty">现在没有约定。需要时，你可以邀请 Aurora 在合适的时候回来。</p> :
          <div className="return-list">{auroraSession.wakeIntents.map(intent => <article key={intent.id} className="return-card">
            <div><strong>{intent.reasonForUser}</strong><span>{new Date(intent.preferredAt).toLocaleString("zh-CN", { dateStyle: "short", timeStyle: "short" })}</span><small>{intent.purpose}</small></div>
            <div className="return-actions"><button type="button" disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.postponeReturn(intent)}>晚一小时</button><button type="button" disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.cancelReturn(intent)}>取消</button></div>
          </article>)}</div>}
      </section>

      {auroraSession.notifications.filter(notice => notice.refType === "WAKE_INTENT").map(notice =>
        <section className="return-arrival" aria-label="Aurora 按约定回来" key={notice.id}>
          <span className="eyebrow">AURORA RETURNED</span><h2>{notice.title}</h2><p>{notice.body}</p>
          <a href={`?wakeIntent=${notice.refId}`}>回到当时没说完的地方</a>
          <div className="return-actions"><button disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.respondToReturn(notice, "MATCHED")}>正合适</button>
            <button disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.respondToReturn(notice, "LATER")}>晚一点</button>
            <button disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.respondToReturn(notice, "STOP_SIMILAR")}>不再提醒这类事</button></div>
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
        onSelectCapsule={id => {
          setSelectedCapsuleId(id);
          if (id === null) { setSelectedMemoryIds([]); setCapsulePreview(null); navigate(spacePath("resonance")); }
          else { navigate(capsulePath(id)); }
        }}
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
      <PeopleDiscovery people={connectionsAndLetters.people} busy={connectionsAndLetters.peopleBusy} onRequest={userId => void connectionsAndLetters.requestPersonConnection(userId)} />
      <RelationsView relations={connectionsAndLetters.relations} selected={connectionsAndLetters.selectedRelation} timeline={connectionsAndLetters.relationTimeline} health={connectionsAndLetters.relationHealth} busy={connectionsAndLetters.relationBusy} onSelect={label => void connectionsAndLetters.openRelation(label)} />

      <LettersInbox letterInbox={connectionsAndLetters.letterInbox} letterOutbox={connectionsAndLetters.letterOutbox} threads={connectionsAndLetters.letterThreads} threadLetters={connectionsAndLetters.threadLetters} selectedThreadId={connectionsAndLetters.selectedThreadId} draftBusy={connectionsAndLetters.draftBusy} replyBusyId={connectionsAndLetters.replyBusyId} onSendDraft={id => void connectionsAndLetters.sendDraft(id)} onOpenThread={id => { void connectionsAndLetters.openThread(id); navigate(letterThreadPath(id)); }} replyDrafts={connectionsAndLetters.replyDrafts} connectionRequests={connectionsAndLetters.connectionRequests} friends={connectionsAndLetters.friends}
        onReplyDraftChange={connectionsAndLetters.updateReplyDraft}
        onReply={letter => void connectionsAndLetters.replyWithLetter(letter)} onActOnLetter={(letter, action) => void connectionsAndLetters.actOnLetter(letter, action)}
        onReportLetter={letter => void connectionsAndLetters.reportLetter(letter)} onRequestConnection={letter => void connectionsAndLetters.requestConnection(letter)}
        onDecideConnection={(id, decision) => void connectionsAndLetters.decideConnection(id, decision)} onLeaveConnection={id => void connectionsAndLetters.leaveConnection(id)} />
      </div>

      <div className="product-space" hidden={productSpace !== "me"}>
        <MeSpace native={mobileState.native} connected={mobileState.connected} wakeIntentCount={auroraSession.wakeIntents.length}
          activeClaimCount={claims.filter(claim => claim.status === "ACTIVE").length}
          publicCapsuleCount={capsules.filter(capsule => capsule.visibilityStatus === "PUBLIC").length}
          friendCount={connectionsAndLetters.friends.length} onNavigate={navigateSpace} onRequestPush={() => void requestMobilePush()}
          onRequestMicrophone={() => void requestMobileMicrophone()} onLogout={() => void logout()} locale={skillLocale} />
        <PortraitView dimensions={portrait} history={portraitHistory} calibrated={portraitCalibrated} busyDim={portraitBusy}
          onLoadHistory={dim => void loadPortraitHistory(dim)} onCalibrate={(dim, oldValue, newValue) => void submitPortraitCalibration(dim, oldValue, newValue)} />
        <AccountSettings busy={accountBusy} message={accountMessage} onChangePassword={(oldPassword, newPassword) => void changeAccountPassword(oldPassword, newPassword)}
          onExportData={() => void exportAccountData()} onDeleteAccount={password => void deleteAccount(password)} locale={skillLocale} />
        <LocaleToggle locale={skillLocale} onChange={changeLocale} />
        <DataRightsPanel receipts={dataRightsReceipts} loading={dataRightsLoading} loaded={dataRightsLoaded}
          onLoad={() => void loadDataRightsReceipts()} locale={skillLocale} />
      </div>
      <div className="state global-state" role="status"><i className={auroraSession.activeTurnId ? "pulse" : ""} />{status}</div>
      <footer><a href="/pages/dashboard.html">尚未迁移的工具</a><span>五空间 AppShell · 数据与能力持续保留</span><button type="button" onClick={() => void logout()}>安全退出</button></footer>
    </main>
  );
}
