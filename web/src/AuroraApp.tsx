import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { Capacitor } from "@capacitor/core";
import { api, apiConfigurationError, configureBearerAuth, demoModeBuild, hasConfiguredApiBase, transcribeAudio, type ClaimCandidate, type CapsuleBoundary, type CapsuleFidelitySummary, type CapsuleGenomeVersion, type CapsuleMatch, type CapsulePreview, type CapsuleQuota, type CapsuleSandbox, type CorrectionCommand, type CorrectionImpact, type EchoCapsule, type MemoryCard, type MemoryOperation, type PersonaMessage, type PersonaSession, type PortraitDimension, type PublicCapsule, type PortraitHistoryEntry, type PsychologyRetention, type PsychologySkillManifest, type PsychologySkillRun, type PsychologySkillSuggestion, type ResonanceStrategy, type SelfEvolution, type SlowLetter, type StarfieldDetail, type StarfieldScene, type StarfieldStar, type UnderstandingClaim, type UserCorrection } from "./api";
import { initialMobileState, mobileRuntime, type MobileRuntimeState } from "./mobile";
import { mobileOidc } from "./mobile-auth";
import { isTauriRuntime } from "./desktop-runtime";
import { reloadPersonaCandidates, resumeOrCreatePersonaConversation } from "./personaExperience";
import { AppearanceSettings, capsulePath, connectionTabFromSearch, connectionTabPath, ConnectionSubNav, letterThreadPath, MeSpace, meTabFromPath, meTabPath, MeSubNav, productSpaceFromPath, productSpaces, ProductShellNavigation, resourceFromPath, spacePath, type ConnectionTab, type ProductSpace, type CosmosTab, cosmosTabFromPath, cosmosTabPath, CosmosSubNav, type ResonanceTab, resonanceTabFromPath, resonanceTabPath, ResonanceSubNav } from "./components/ProductShell";
import { AuroraConversation } from "./components/AuroraConversation";
import { ConversationHistory } from "./components/ConversationHistory";
import { QuickHello } from "./components/QuickHello";
import { StartHereJourney, type JourneyStep } from "./components/StartHereJourney";
import { AuroraInnerVoiceAside } from "./components/AuroraInnerVoiceAside";
import { AuroraMemoryTrace } from "./components/AuroraMemoryTrace";
import { SafetyResourceCard } from "./components/SafetyResourceCard";
import { GoodbyeRitualCard } from "./components/GoodbyeRitualCard";
import { SocialGroupsView } from "./components/SocialGroupsView";
import { SafetyHarborPage } from "./components/SafetyHarborPage";
import { AdminConsole } from "./components/admin/AdminConsole";
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
import type { DataRetractionReceipt, TtsPreferences, TtsPreferencesPatch, UserProfileSettings } from "./api";
import { loadLocale, saveLocale, syncDocumentLocale, type Locale } from "./i18n";
import { APP_COPY, type DialogMode } from "./appCopy";
import { AuthGate } from "./components/AuthGate";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { PsychologySkillStudio, SkillSuggestionBanner, type SkillLocale } from "./components/PsychologySkillStudio";
import { ConnectError, LoadingText } from "./loading";
import { useAuroraSession } from "./hooks/useAuroraSession";
import { useConnectionsAndLetters } from "./hooks/useConnectionsAndLetters";
import { sendComposedLetter, type DraftedLetterState } from "./composeAndSend";
import { useDailyRecord } from "./hooks/useDailyRecord";
import { useWeeklyReview } from "./hooks/useWeeklyReview";
import { useThoughtShredder } from "./hooks/useThoughtShredder";
import { TimelineSection } from "./components/TimelineSection";
import { WeeklyReviewSection } from "./components/WeeklyReviewSection";
import { DailyRecordSection } from "./components/DailyRecordSection";
import { ThoughtShredderSection } from "./components/ThoughtShredderSection";
import type { MemoryThemeRow } from "./api";
import { useTodoBoard } from "./hooks/useTodoBoard";
import { useHeartDiary } from "./hooks/useHeartDiary";
import { useBeliefGallery } from "./hooks/useBeliefGallery";
import { TodoBoard } from "./components/TodoBoard";
import { HeartDiary } from "./components/HeartDiary";
import { BeliefGallery } from "./components/BeliefGallery";
import { userVisiblePublicCapsules, userVisibleResonanceMatches } from "./demoFixtureVisibility";
import { DemoPersonaChooser } from "./components/DemoPersonaChooser";
import { TodayOverview } from "./components/TodayOverview";
import { InnerCosmosOverview } from "./components/InnerCosmosOverview";
import { capsuleDraftDefaults, journeyStepsFromFacts, latestSettledMemory } from "./newUserJourney";

// The Aurora conversation/session domain (message list, streaming/turn status, interrupt/stop,
// mode picker, WakeIntent negotiate, session bootstrap/replay) has been extracted into
// ./hooks/useAuroraSession.ts (B1 domain-hook decomposition, first slice) -- see
// docs/goal/tracks/track-b-status.yml and evidence/track-b/README.md for what moved and why.
const modes = [
  ["DAILY_TALK", "倾诉"], ["THOUGHT_CLARIFY", "整理"], ["SOCRATIC", "追问"],
  ["ACTION_SPLIT", "行动"], ["RELATION_REVIEW", "关系"], ["CAPSULE_SHAPING", "塑造侧影"]
] as const;

const INITIAL_DRAFT_COPY: Record<Locale, {
  connecting: string;
  sandboxQuestion: string;
  personaDraft: string;
  letterTitle: string;
}> = {
  "zh-CN": {
    connecting: "正在连接你的内宇宙…",
    sandboxQuestion: "当你被误解时，通常会怎样表达自己的边界？",
    personaDraft: "最近有什么让你觉得自己被认真理解了？",
    letterTitle: "想把刚才的共鸣慢慢写下来"
  },
  "en-SG": {
    connecting: "Connecting to your inner cosmos…",
    sandboxQuestion: "When you feel misunderstood, how do you usually express a boundary?",
    personaDraft: "What has recently made you feel genuinely understood?",
    letterTitle: "I want to let this resonance arrive slowly"
  }
};

export function AuroraApp() {
  // Real client routing (react-router HashRouter, mounted in main.tsx): the active space is
  // derived from the current route on every render instead of being copied into state once
  // at mount. This is what makes an expired-auth deep link resume the right space after
  // re-login "for free" -- the route never changes underneath the AuthGate swap, so once
  // `authenticated` flips back to true, `productSpace` is still whatever the URL says.
  const location = useLocation();
  const navigate = useNavigate();
  const initialLocale = useMemo(() => loadLocale(), []);
  const initialDrafts = INITIAL_DRAFT_COPY[initialLocale];
  const productSpace = useMemo(() => productSpaceFromPath(location.pathname), [location.pathname]);
  const cosmosTab = useMemo(() => cosmosTabFromPath(location.pathname), [location.pathname]);
  const resonanceTab = useMemo(() => resonanceTabFromPath(location.pathname), [location.pathname]);
  const connectionTab = useMemo(() => connectionTabFromSearch(location.search), [location.search]);
  const meTab = useMemo(() => meTabFromPath(location.pathname), [location.pathname]);
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [status, setStatus] = useState(initialDrafts.connecting);
  const [statusVisible, setStatusVisible] = useState(true);
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
  const [capsulePersonaClaimIds, setCapsulePersonaClaimIds] = useState<number[]>([]);
  const [portrait, setPortrait] = useState<PortraitDimension[]>([]);
  const [portraitHistory, setPortraitHistory] = useState<Record<string, PortraitHistoryEntry[]>>({});
  const [portraitBusy, setPortraitBusy] = useState<string | null>(null);
  const [accountBusy, setAccountBusy] = useState<AccountBusy>(null);
  const [dataRightsReceipts, setDataRightsReceipts] = useState<DataRetractionReceipt[]>([]);
  const [dataRightsLoading, setDataRightsLoading] = useState(false);
  const [dataRightsLoaded, setDataRightsLoaded] = useState(false);
  const [accountMessage, setAccountMessage] = useState<string | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfileSettings | null>(null);
  const [profileBusy, setProfileBusy] = useState(false);
  // W2 voice feature: the user's TTS voice/inner-voice preferences. Both AccountSettings' voice
  // picker AND AuroraConversation's inner-voice bubble read from this single fetched-once source
  // (there is no dedicated shared preference context/store in this codebase yet -- see
  // AuroraApp.tsx's existing userProfile for the same one-fetch-many-consumers shape this mirrors).
  const [ttsPreferences, setTtsPreferences] = useState<TtsPreferences | null>(null);
  const [ttsBusy, setTtsBusy] = useState(false);
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
  const [genomeHistoryError, setGenomeHistoryError] = useState(false);
  const [fidelitySummary, setFidelitySummary] = useState<CapsuleFidelitySummary[]>([]);
  const [selectedMemoryIds, setSelectedMemoryIds] = useState<number[]>([]);
  const [capsuleName, setCapsuleName] = useState("");
  const [capsuleIntro, setCapsuleIntro] = useState("");
  const [capsuleOwnerNote, setCapsuleOwnerNote] = useState("");
  const [capsuleStandIn, setCapsuleStandIn] = useState(false);
  const [capsuleContactPolicy, setCapsuleContactPolicy] = useState("LETTER_ONLY");
  const [capsulePrivacy, setCapsulePrivacy] = useState<"STRICT" | "BALANCED" | "OPEN">("STRICT");
  const [personaTurnError, setPersonaTurnError] = useState<string | null>(null);
  const [capsulePreview, setCapsulePreview] = useState<CapsulePreview | null>(null);
  const [capsuleBusy, setCapsuleBusy] = useState(false);
  const [capsuleBoundary, setCapsuleBoundary] = useState<CapsuleBoundary | null>(null);
  const [boundaryBusy, setBoundaryBusy] = useState(false);
  const [boundaryLoadFailed, setBoundaryLoadFailed] = useState(false);
  const [sandboxQuestion, setSandboxQuestion] = useState(initialDrafts.sandboxQuestion);
  const [sandboxResult, setSandboxResult] = useState<CapsuleSandbox | null>(null);
  const [sandboxFeedback, setSandboxFeedback] = useState<string | null>(null);
  const [resonanceMatches, setResonanceMatches] = useState<CapsuleMatch[]>([]);
  const [publicCapsules, setPublicCapsules] = useState<PublicCapsule[]>([]);
  const [directoryPick, setDirectoryPick] = useState<PublicCapsule | null>(null);
  const [resonanceStrategy, setResonanceStrategy] = useState<ResonanceStrategy>("MIRROR");
  const [visitorMatchId, setVisitorMatchId] = useState<number | null>(null);
  const [personaSession, setPersonaSession] = useState<PersonaSession | null>(null);
  const [personaMessages, setPersonaMessages] = useState<PersonaMessage[]>([]);
  const [personaDraft, setPersonaDraft] = useState(initialDrafts.personaDraft);
  const [personaQuota, setPersonaQuota] = useState<CapsuleQuota | null>(null);
  // W1 capsule-voice reuse: on-demand synthesized audio of the latest capsule reply, fetched only
  // when the visitor taps play (opt-in/visible, never autoplay-surprising). Cleared on each new
  // turn / session so the play affordance reappears for the newest reply.
  const [personaVoiceAudio, setPersonaVoiceAudio] = useState<string | null>(null);
  const [personaVoiceBusy, setPersonaVoiceBusy] = useState(false);
  const [personaVoiceError, setPersonaVoiceError] = useState<string | null>(null);
  const [landedCapsuleIds, setLandedCapsuleIds] = useState<Set<number>>(() => new Set());
  const [landedBusyId, setLandedBusyId] = useState<number | null>(null);
  const [letterTitle, setLetterTitle] = useState(initialDrafts.letterTitle);
  const [letterBody, setLetterBody] = useState("");
  const [sentLetter, setSentLetter] = useState<SlowLetter | null>(null);
  // Gemini audit 4.5 (CONFIRMED/P1): persists the draft id + idempotency key across a failed
  // send-retry (see web/src/composeAndSend.ts) so retrying sendLetterToMatch after a failed send
  // reuses the SAME draft instead of creating a duplicate one. Reset to null whenever the compose
  // target changes (chooseVisitorMatch/chooseResonanceStrategy) or once a send finally succeeds.
  const letterDraftRef = useRef<DraftedLetterState>(null);
  const [skills, setSkills] = useState<PsychologySkillManifest[]>([]);
  const [skillRuns, setSkillRuns] = useState<PsychologySkillRun[]>([]);
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null);
  const [skillAnswers, setSkillAnswers] = useState<Record<string, string>>({});
  const [skillConsent, setSkillConsent] = useState(false);
  const [skillRetention, setSkillRetention] = useState<PsychologyRetention>("DISCARD_AFTER_SESSION");
  const [skillBusy, setSkillBusy] = useState(false);
  const [skillSuggestion, setSkillSuggestion] = useState<PsychologySkillSuggestion | null>(null);
  const [skillLocale, setSkillLocale] = useState<SkillLocale>(initialLocale);
  const [visitorBusy, setVisitorBusy] = useState(false);
  const [mobileState, setMobileState] = useState<MobileRuntimeState>(initialMobileState);
  const [memoryThemes, setMemoryThemes] = useState<MemoryThemeRow[]>([]);
  const bootstrappedRef = useRef(false);
  const bootstrapCallRef = useRef(0);
  const draftRestoredRef = useRef(false);
  const lastCandidatePollTurnRef = useRef("");

  useEffect(() => {
    syncDocumentLocale(skillLocale);
  }, [skillLocale]);

  useEffect(() => {
    setStatusVisible(true);
    const timer = window.setTimeout(() => setStatusVisible(false), 7000);
    return () => window.clearTimeout(timer);
  }, [status]);

  // Aurora conversation/session domain (message list, streaming/turn status, interrupt/stop, mode
  // picker, WakeIntent negotiate, session bootstrap/replay) -- extracted into its own hook; see
  // web/src/hooks/useAuroraSession.ts.
  const auroraSession = useAuroraSession({
    authenticated, skillLocale, onSkillSuggestion: setSkillSuggestion, setStatus,
    onNaturalActionExecuted: async featureTarget => {
      if (featureTarget === "memory-starfield") {
        const [scene, cards, operations] = await Promise.all([
          api.starfield("TIME"), api.memoryCards(), api.memoryOperations()
        ]);
        setStarfield(scene);
        setMemories(cards);
        setMemoryOperations(operations);
      } else if (featureTarget === "settings") {
        setUserProfile(await api.getProfile());
      }
    },
    onMemorySettled: async settledMode => {
      const [scene, cards, operations] = await Promise.all([
        api.starfield("TIME"), api.memoryCards(), api.memoryOperations()
      ]);
      setStarfield(scene);
      setMemories(cards);
      setMemoryOperations(operations);
      if (settledMode === "CAPSULE_SHAPING") {
        const latestMemory = latestSettledMemory(memories, cards);
        const defaults = capsuleDraftDefaults(latestMemory, skillLocale);
        setSelectedCapsuleId(null);
        setCapsulePreview(null);
        setSelectedMemoryIds(latestMemory ? [latestMemory.id] : []);
        setCapsuleName(defaults.name);
        setCapsuleIntro(skillLocale === "en-SG"
          ? "A private facet shaped from memories I chose to authorize."
          : "一个由我主动授权的记忆编织而成的私密侧影。");
        navigate(resonanceTabPath("mine"));
        window.scrollTo({ top: 0, behavior: "smooth" });
        setStatus(skillLocale === "en-SG"
          ? "Your newest memory is selected in a private capsule draft. Review the preview, then compile it when it feels right — nothing is published automatically."
          : "最新记忆已选入一个私密共鸣体草稿。确认授权预览后再由你决定是否编译；系统不会自动发布。");
        return;
      }
      navigate(cosmosTabPath("starfield"));
      window.scrollTo({ top: 0, behavior: "smooth" });
      setStatus(skillLocale === "en-SG"
        ? `This moment is now ${cards.length} traceable memory ${cards.length === 1 ? "star" : "stars"}. Open a star to see where it came from.`
        : `已经把这一刻沉淀成 ${cards.length} 颗可追溯的记忆星；点星体可以查看它来自哪里。`);
    }
  });

  // Candidate extraction is asynchronous after the complete turn is persisted. Poll only this
  // conversation on a bounded 0/500/1500ms schedule; GET is pure and never changes claim state.
  useEffect(() => {
    if (!auroraSession.sessionId || auroraSession.activeTurnId !== null) return;
    const last = auroraSession.messages.at(-1);
    if (!last || last.id == null || last.speaker !== "AURORA" || last.partial) return;
    const turnKey = `${auroraSession.sessionId}:${last.key}:${capsulePrivacy}`;
    if (lastCandidatePollTurnRef.current === turnKey) return;
    lastCandidatePollTurnRef.current = turnKey;
    let cancelled = false;
    const timers = [0, 500, 1500].map(delay => window.setTimeout(() => {
      if (!cancelled && auroraSession.sessionId) {
        void api.claimCandidates(auroraSession.sessionId, capsulePrivacy).then(rows => {
          if (!cancelled) setClaimCandidates(rows);
        }).catch(() => undefined);
      }
    }, delay));
    return () => {
      cancelled = true;
      timers.forEach(timer => window.clearTimeout(timer));
    };
  }, [auroraSession.activeTurnId, auroraSession.messages, auroraSession.sessionId, capsulePrivacy]);

  useEffect(() => {
    if (!authenticated) return;
    setStatus(skillLocale === "en-SG"
      ? "Aurora is here. Interrupt at any time; she will listen again."
      : "Aurora 在这里。你可以随时打断，她会重新理解。");
  }, [authenticated, skillLocale]);

  // Connections/letters domain (People Discovery, relation mentions/timeline, connection
  // requests/friends, slow-letter inbox/outbox/threads) -- extracted into its own hook; see
  // web/src/hooks/useConnectionsAndLetters.ts.
  const connectionsAndLetters = useConnectionsAndLetters({ setStatus, locale: skillLocale });

  // Phase 3 legacy-page port (timeline.html / weekly-review.html / daily-record.html /
  // thought-shredder.html) -- three small domain hooks for the "cosmos" space's growth-timeline,
  // weekly-review and thought-shredder sections; see web/src/hooks/useDailyRecord.ts,
  // useWeeklyReview.ts and useThoughtShredder.ts.
  const dailyRecord = useDailyRecord({ setStatus, locale: skillLocale });
  const weeklyReview = useWeeklyReview({ setStatus, locale: skillLocale });
  const thoughtShredder = useThoughtShredder({ setStatus, locale: skillLocale });
  // Legacy static-page ports (Phase 3, legacy batch B): todo.html, heart-diary.html, and the
  // belief-pattern-browsing half of beliefs.html. Each domain gets its own small hook, matching the
  // precedent set by useConnectionsAndLetters.ts.
  const todoBoard = useTodoBoard({ setStatus, locale: skillLocale });
  const heartDiary = useHeartDiary({ setStatus, locale: skillLocale });
  const beliefGallery = useBeliefGallery({ setStatus, locale: skillLocale });

  const navigateSpace = useCallback((space: ProductSpace) => {
    navigate(spacePath(space));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [navigate]);

  const navigateCosmosTab = useCallback((tab: CosmosTab) => {
    navigate(cosmosTabPath(tab));
  }, [navigate]);

  const navigateConnectionTab = useCallback((tab: ConnectionTab) => {
    navigate(connectionTabPath(tab));
  }, [navigate]);

  const navigateResonanceTab = useCallback((tab: ResonanceTab) => {
    navigate(resonanceTabPath(tab));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [navigate]);

  // Delivery is advanced by the backend scheduler, so keep the visible inbox/outbox honest while
  // the user stays on this tab. The immediate refresh covers returning from another space; the
  // bounded interval avoids requiring a full-page reload during the three-minute demo flight.
  useEffect(() => {
    if (!authenticated || productSpace !== "letters" || connectionTab !== "letters") return;
    void connectionsAndLetters.refreshLetters();
    const timer = window.setInterval(() => void connectionsAndLetters.refreshLetters(), 20_000);
    return () => window.clearInterval(timer);
  }, [authenticated, productSpace, connectionTab, connectionsAndLetters.refreshLetters]);

  // "此刻聊聊" is intentionally the one fast lane in Connect: short foreground polling makes
  // invitations and messages feel live in the classroom demo without turning every relationship
  // into an always-on chat channel. The poll disappears as soon as the user leaves this tab.
  useEffect(() => {
    if (!authenticated || productSpace !== "letters" || connectionTab !== "letters") return;
    void connectionsAndLetters.refreshLiveChats();
    const timer = window.setInterval(() => void connectionsAndLetters.refreshLiveChats(), 2_000);
    return () => window.clearInterval(timer);
  }, [authenticated, productSpace, connectionTab, connectionsAndLetters.refreshLiveChats]);

  // Friend requests and group invitations are created by another signed-in user, so local mutation
  // callbacks alone can never make them appear. Keep the Connect space fresh while it is visible;
  // this bounded foreground poll is intentionally scoped to the product space (and stops on unmount)
  // so accepting an invitation never requires a full website reload.
  useEffect(() => {
    if (!authenticated || productSpace !== "letters") return;
    void connectionsAndLetters.refreshConnections();
    const timer = window.setInterval(() => void connectionsAndLetters.refreshConnections(), 5_000);
    return () => window.clearInterval(timer);
  }, [authenticated, productSpace, connectionsAndLetters.refreshConnections]);

  useEffect(() => {
    if (!authenticated || productSpace !== "letters" || connectionTab !== "groups") return;
    const refresh = () => {
      void connectionsAndLetters.refreshGroups();
      void connectionsAndLetters.refreshSelectedGroupContext();
    };
    refresh();
    const timer = window.setInterval(refresh, 5_000);
    return () => window.clearInterval(timer);
  }, [authenticated, productSpace, connectionTab, connectionsAndLetters.refreshGroups,
    connectionsAndLetters.refreshSelectedGroupContext]);

  // Lazy per-tab fetch: each cosmos sub-tab's data loads only the first time it is actually
  // visited, not eagerly in the shared login bootstrap (doc 24 section 3.3 forbids adding
  // non-first-screen requests to that awaited Promise.all). Sections stay mounted (`hidden`,
  // matching the five-space precedent above) once loaded so switching tabs back and forth does
  // not re-fetch or lose scroll/edit state. memoryThemes + the daily-records list feed both the
  // "starfield" tab's TimelineSection and the "daily" tab's DailyRecordSection, so they get their
  // own once-only guards fired by whichever of those two tabs is visited first (e.g. a deep link
  // straight to /cosmos/daily still populates Timeline correctly if the user later switches to it).
  const cosmosTabLoadedRef = useRef<Partial<Record<CosmosTab, boolean>>>({});
  const themesLoadedRef = useRef(false);
  const dailyListLoadedRef = useRef(false);
  useEffect(() => {
    // Guarded on productSpace too, not just authenticated: cosmosTab defaults to "starfield" for
    // any path outside /cosmos/*, so without this the "cosmos" tab data would load as soon as the
    // user lands on Aurora, never actually deferred until they open the space.
    if (!authenticated || productSpace !== "cosmos" || cosmosTabLoadedRef.current[cosmosTab]) return;
    cosmosTabLoadedRef.current[cosmosTab] = true;
    if (cosmosTab === "starfield" || cosmosTab === "daily") {
      if (!themesLoadedRef.current) { themesLoadedRef.current = true; api.memoryThemes().then(setMemoryThemes).catch(() => undefined); }
      if (!dailyListLoadedRef.current) { dailyListLoadedRef.current = true; dailyRecord.loadDailyRecords().catch(() => undefined); }
    }
    if (cosmosTab === "daily") {
      void dailyRecord.loadLatestDailyRecord();
    } else if (cosmosTab === "weekly") {
      void weeklyReview.loadWeeklyReview();
    } else if (cosmosTab === "thoughts") {
      void thoughtShredder.loadShredderAiHealth();
      void thoughtShredder.loadShredderHistory();
      todoBoard.loadTodos().catch(() => undefined);
    } else if (cosmosTab === "beliefs") {
      beliefGallery.loadAll().catch(() => undefined);
      void beliefGallery.loadContradictions();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authenticated, productSpace, cosmosTab]);

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
        auroraSession.loadSessions(),
        auroraSession.loadWakeIntents(),
        auroraSession.loadNotifications(),
        auroraSession.loadSafetyResources(),
        api.selfEvolution().then(setSelfEvolution),
        api.understandingClaims().then(setClaims),
        api.starfield("TIME").then(setStarfield),
        api.memoryOperations().then(setMemoryOperations),
        api.memoryCards().then(setMemories),
        api.myCapsules().then(rows => { setCapsules(rows); loadedCapsules = rows; }),
        api.resonanceMatches().then(rows => {
          const visibleRows = userVisibleResonanceMatches(rows);
          setResonanceMatches(visibleRows);
          loadedMatches = visibleRows;
        }),
        connectionsAndLetters.loadLetterInbox(),
        connectionsAndLetters.loadConnectionRequests(),
        connectionsAndLetters.loadFriends(),
        api.psychologySkills().then(rows => { setSkills(rows); loadedSkills = rows; }),
        api.psychologySkillRuns().then(setSkillRuns),
        api.portrait().then(setPortrait),
        api.recentCorrections().then(setCorrections),
        api.plazaCapsules().then(rows => setPublicCapsules(userVisiblePublicCapsules(rows))).catch(() => undefined),
        connectionsAndLetters.loadLetterOutbox(),
        connectionsAndLetters.loadPeople(),
        api.claimCandidates().then(setClaimCandidates).catch(() => undefined),
        connectionsAndLetters.loadRelations(),
        connectionsAndLetters.loadLetterThreads(),
        api.getProfile().then(setUserProfile),
        // W2 voice: defensive like api.plazaCapsules() above -- a still-new, non-critical endpoint
        // failing (e.g. not yet deployed everywhere) must not fail the whole Aurora bootstrap.
        // AuroraConversation/AccountSettings both already treat a null ttsPreferences as "feature
        // not yet available" (render nothing / render nothing), so this degrades gracefully.
        api.ttsPreferences().then(setTtsPreferences).catch(() => undefined),
        connectionsAndLetters.loadGroups(),
        connectionsAndLetters.loadGroupInvites()
        // NOTE: timeline/daily-record/weekly-review/thought-shredder/todo/belief-gallery data is
        // deliberately NOT loaded here -- it is not first-screen (Aurora landing) data. It lazy-loads
        // the first time the user actually opens the relevant "cosmos" sub-tab; see the
        // cosmosTabLoadedRef effect below. Doc 24 section 3.3 forbids adding non-first-screen
        // requests to this awaited Promise.all.
      ]);
      if (call !== bootstrapCallRef.current) return;
      setAuthenticated(true);
      const firstVisibleCapsule = loadedCapsules.find(capsule => capsule.visibilityStatus !== "ARCHIVED");
      if (firstVisibleCapsule) setSelectedCapsuleId(current => current ?? firstVisibleCapsule.id);
      setVisitorMatchId(current => current ?? loadedMatches[0]?.capsule.id ?? null);
      setSelectedSkillId(current => current ?? loadedSkills[0]?.id ?? null);
      setStatus(resolved.returning
        ? (skillLocale === "en-SG"
          ? `Aurora returned as agreed: ${resolved.returning.purpose}`
          : `Aurora 按约定回来了：${resolved.returning.purpose}`)
        : (skillLocale === "en-SG"
          ? "Aurora is here. Interrupt at any time; she will listen again."
          : "Aurora 在这里。你可以随时打断，她会重新理解。 "));
    } catch (error) {
      if (call !== bootstrapCallRef.current) return;
      // Spring Security's CSRF filter can reject the first unauthenticated POST (createSession)
      // with 403 before the authentication entry point has a chance to return 401. That is still
      // an unauthenticated bootstrap, not a product connection failure; show AuthGate so a fresh
      // browser can actually register/sign in.
      if (/authentication|unauthori[sz]ed|\b40[13]\b/i.test(String(error))) {
        setAuthenticated(false);
        setStatus(skillLocale === "en-SG" ? "Please sign in first." : "请先登录");
      } else {
        // 非鉴权失败：过去只更新 status 却把 authenticated 停在 null，用户会永久卡在连接加载屏。
        // 现在进入明确的"错误态"，连接屏据此渲染错误 + 重试（恢复态）。
        const message = error instanceof Error ? error.message : skillLocale === "en-SG"
          ? "Inner Cosmos is temporarily unreachable."
          : "暂时无法连接你的内宇宙";
        setBootstrapError(message);
        setStatus(message);
      }
    }
  }, [auroraSession.resolveSession, auroraSession.replaceFromHistory, auroraSession.loadSessions, auroraSession.loadWakeIntents, auroraSession.loadNotifications,
      auroraSession.loadSafetyResources,
      connectionsAndLetters.loadLetterInbox, connectionsAndLetters.loadConnectionRequests, connectionsAndLetters.loadFriends,
      connectionsAndLetters.loadLetterOutbox, connectionsAndLetters.loadPeople, connectionsAndLetters.loadRelations, connectionsAndLetters.loadLetterThreads]);

  // Regression (remaining-work-handoff.md 2.2.6, "画像校准 reload"): derived from the real,
  // reloaded `corrections` list instead of a write-only local flag that reset to {} on every
  // refresh -- the correction itself was always genuinely persisted via api.confirmCorrection(),
  // only the "already calibrated" ribbon on the card silently vanished on reload.
  const portraitCalibrated = useMemo(() => {
    const calibrated: Record<string, boolean> = {};
    for (const correction of corrections) {
      if (correction.targetType === "PORTRAIT_DIM") calibrated[correction.fieldName] = true;
    }
    return calibrated;
  }, [corrections]);

  const selectedCapsule = capsules.find(capsule => capsule.id === selectedCapsuleId) ?? null;
  // A capsule opened from the public plaza directory (not the curated match set) is wrapped in a
  // synthetic match so the existing visitor workbench (persona chat + slow letter) works unchanged.
  const directoryMatch: CapsuleMatch | null = directoryPick && !resonanceMatches.some(match => match.capsule.id === directoryPick.id)
    ? { capsule: directoryPick, matchScore: 0, matchReasons: [],
        matchSummary: skillLocale === "en-SG"
          ? "You found this presence in the plaza rather than through a recommendation."
          : "你在广场里主动找到了它，而不是被推荐的。",
        resonant: false, strategy: "SERENDIPITY",
        strategyLabel: skillLocale === "en-SG" ? "Discovered by you" : "主动发现",
        strategyDescription: skillLocale === "en-SG"
          ? "You chose to move closer to this presence in the resonance plaza."
          : "你在共鸣广场里主动走近了它。" }
    : null;
  const visitorMatch = resonanceMatches.find(match => match.capsule.id === visitorMatchId)
    ?? (directoryMatch && directoryMatch.capsule.id === visitorMatchId ? directoryMatch : null)
    ?? resonanceMatches[0] ?? null;
  const selectedSkill = skills.find(skill => skill.id === selectedSkillId) ?? skills[0] ?? null;
  const completedJourneySteps = useMemo(() => journeyStepsFromFacts({
    hasUserMessage: auroraSession.messages.some(message => message.speaker === "USER" && Boolean(message.text.trim())),
    hasMemory: memories.length > 0,
    hasActiveCapsule: capsules.some(capsule => capsule.visibilityStatus !== "ARCHIVED"),
    hasVisitorSession: personaSession !== null,
    hasResonantMatch: resonanceMatches.some(match => match.resonant),
    hasSentLetter: sentLetter !== null || connectionsAndLetters.letterOutbox.length > 0
  }), [
    auroraSession.messages, capsules, connectionsAndLetters.letterOutbox.length, memories.length,
    personaSession, resonanceMatches, sentLetter
  ]);

  useEffect(() => {
    if (!selectedCapsule) {
      setGenomeHistory([]);
      setGenomeHistoryError(false);
      setFidelitySummary([]);
      setCapsuleBoundary(null);
      setBoundaryLoadFailed(false);
      return;
    }
    const ids = [...selectedCapsule.authorizedMemoryIds.matchAll(/\d+/g)].map(match => Number(match[0]));
    setSelectedMemoryIds(ids);
    setSandboxResult(null);
    setSandboxFeedback(null);
    setGenomeHistory([]);
    setGenomeHistoryError(false);
    void api.capsuleGenomeHistory(selectedCapsule.id).then(history => {
      setGenomeHistory(history);
      setGenomeHistoryError(false);
    }).catch(error => {
      setGenomeHistoryError(true);
      setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Capsule versions are temporarily unavailable."
        : "暂时无法读取共鸣体版本");
    });
    void api.capsuleFidelity(selectedCapsule.id).then(setFidelitySummary).catch(() => setFidelitySummary([]));
    setCapsuleBoundary(null);
    setBoundaryLoadFailed(false);
    void api.capsuleBoundary(selectedCapsule.id).then(value => {
      setCapsuleBoundary(value);
      setBoundaryLoadFailed(value === null);
    }).catch(() => { setCapsuleBoundary(null); setBoundaryLoadFailed(true); });
  }, [selectedCapsuleId, selectedCapsule?.activeGenomeVersionId]);

  const retryGenomeHistory = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      setGenomeHistory(await api.capsuleGenomeHistory(selectedCapsule.id));
      setGenomeHistoryError(false);
    } catch (error) {
      setGenomeHistoryError(true);
      setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Capsule versions are still unavailable. Check the connection and retry."
        : "仍然无法读取 Genome 版本，请检查连接后重试。");
    } finally {
      setCapsuleBusy(false);
    }
  };

  const saveCapsuleBoundary = async (boundary: Partial<CapsuleBoundary>) => {
    if (!selectedCapsule) return;
    setBoundaryBusy(true);
    try {
      const privacyChanged = boundary.privacyLevel != null && boundary.privacyLevel !== capsuleBoundary?.privacyLevel;
      await api.updateCapsuleBoundary(selectedCapsule.id, boundary);
      setCapsuleBoundary(await api.capsuleBoundary(selectedCapsule.id));
      setBoundaryLoadFailed(false);
      if (privacyChanged) await refreshSelectedCapsule(selectedCapsule.id);
      setStatus(privacyChanged
        ? (skillLocale === "en-SG"
          ? "Privacy changed. Publishing is paused until you recompile and review the new private version."
          : "隐私等级已改变。公开已暂停，请重新编译并复核新的私密版本。")
        : skillLocale === "en-SG"
        ? "Boundaries updated. Only you can change them; the public facet will use them with visitors."
        : "边界已更新。只有你能改动它，公开人格会按新的边界回应访客。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "These capsule boundaries could not be saved."
      : "暂时无法保存这个共鸣体的边界"); }
    finally { setBoundaryBusy(false); }
  };

  useEffect(() => {
    if (bootstrappedRef.current) return;
    bootstrappedRef.current = true;
    const native = Capacitor.isNativePlatform() || isTauriRuntime();
    // Demo-mode native builds (VITE_DEMO_MODE, see scripts/demo/build-demo-apk.sh) fall back to
    // the same session/password auth the web build uses, instead of hard-requiring OIDC/PKCE --
    // 2026-07-24 8-agent audit P0-1: a demo APK pointed at a plain-dev backend (OIDC disabled)
    // could never get past AuthGate's OIDC-only screen at all.
    const nativeAuthRequired = native && !demoModeBuild;
    configureBearerAuth(nativeAuthRequired ? () => mobileOidc.accessToken() : null, nativeAuthRequired,
      nativeAuthRequired ? () => mobileOidc.expireAccessToken() : null);
    let dispose: (() => Promise<void>) | undefined;
    void mobileOidc.initialize(bootstrap, error => {
      setAuthenticated(false);
      setStatus(error.message);
      setBootstrapError(error.message);
    }).then(cleanup => {
      dispose = cleanup;
      return bootstrap();
    }).catch(error => {
      setAuthenticated(false);
      setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Mobile authentication could not be initialized."
        : "移动认证初始化失败");
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
        if (!cancelled) setStatus(skillLocale === "en-SG"
          ? "This device is registered for notifications; remote delivery still depends on APNs / FCM being configured in this environment."
          : "设备已向系统通知服务注册；真实远程投递仍取决于当前环境的 APNs / FCM 配置。");
      }
    }).then(stopRuntime => {
      if (cancelled) void stopRuntime();
      else cleanup = stopRuntime;
    }).catch(error => {
      if (!cancelled) setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "The mobile runtime is temporarily unavailable."
        : "移动端运行时暂时不可用");
    });
    return () => {
      cancelled = true;
      window.removeEventListener("offline", browserOffline);
      window.removeEventListener("online", browserOnline);
      if (cleanup) void cleanup();
    };
  }, [auroraSession.openMobileWakeIntent, auroraSession.resumeConversation, auroraSession.refreshNotifications]);

  useEffect(() => {
    if (!authenticated || (!Capacitor.isNativePlatform() && !isTauriRuntime())) return;
    void mobileRuntime.deviceContext().then(context => api.registerDevice(context.installationId, {
      platform: context.platform === "ios" ? "IOS" : context.platform === "macos" ? "MACOS"
        : context.platform === "windows" ? "WINDOWS" : "ANDROID",
      transport: "LOCAL_EVIDENCE", appVersion: context.appVersion,
      locale: context.locale, timezone: context.timezone
    })).catch(error => setStatus(error instanceof Error ? error.message : "Unable to register this device"));
  }, [authenticated]);

  // Persist only the explicitly recoverable composer draft. It lives in Keystore-backed storage
  // on native platforms and IndexedDB on the web, expires after 24 hours, and is never auto-sent.
  useEffect(() => {
    if (draftRestoredRef.current) return;
    draftRestoredRef.current = true;
    void mobileRuntime.loadDraft("aurora").then(saved => {
      if (saved && !auroraSession.draft) {
        auroraSession.setDraft(saved.value);
        setStatus(skillLocale === "en-SG"
          ? "Your unsent draft was restored. Review it before choosing Send."
          : "未发送的草稿已恢复。请确认内容后再主动发送。");
      }
    });
  }, [auroraSession.draft, auroraSession.setDraft, skillLocale]);

  useEffect(() => {
    if (!draftRestoredRef.current) return;
    const timer = window.setTimeout(() => {
      void mobileRuntime.saveDraft("aurora", auroraSession.draft, auroraSession.sessionId?.toString() ?? null);
    }, 350);
    return () => window.clearTimeout(timer);
  }, [auroraSession.draft, auroraSession.sessionId]);

  const requestMobilePush = async () => {
    const permission = await mobileRuntime.requestPushRegistration();
    setStatus(skillLocale === "en-SG"
      ? (permission === "granted" ? "Notifications enabled. Aurora can return as arranged once remote delivery is configured."
        : permission === "denied" ? "Notifications remain off; return arrangements still stay inside the app."
          : permission === "unavailable" ? "This browser does not use system push; return arrangements still appear in the app."
            : "Notification registration could not be completed.")
      : (permission === "granted" ? "通知权限已开启，Aurora 可以在真实投递配置就绪后按约定回来。"
        : permission === "denied" ? "通知权限没有开启；回来约定仍会保留在应用内。"
          : permission === "unavailable" ? "当前浏览器不使用系统推送；回来约定仍会在应用内出现。"
            : "暂时无法完成通知注册。"));
  };

  const requestMobileMicrophone = async () => {
    const permission = await mobileRuntime.requestMicrophonePermission();
    setStatus(skillLocale === "en-SG"
      ? (permission === "granted" ? "Microphone ready; this permission check did not save any recording."
        : permission === "denied" ? "Microphone access is off; you can keep typing."
          : permission === "unavailable" ? "Microphone input is unavailable here; you can keep typing."
            : "Microphone permission could not be checked.")
      : (permission === "granted" ? "麦克风已准备好；本次授权检查没有保存任何录音。"
        : permission === "denied" ? "麦克风权限没有开启，你仍然可以继续打字。"
          : permission === "unavailable" ? "当前环境不支持麦克风输入，你仍然可以继续打字。"
            : "暂时无法检查麦克风权限。"));
  };

  const evolve = async (action: () => Promise<SelfEvolution>, success: string) => {
    setSelfBusy(true);
    try {
      setSelfEvolution(await action());
      setStatus(success);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "This change did not pass review."
        : "这次变化没有通过");
    } finally { setSelfBusy(false); }
  };

  const correctionCommand = (): CorrectionCommand => correctionTarget ? {
    targetType: "MEMORY_CARD", targetId: correctionTarget.id, fieldName: "summary",
    oldValue: null, newValue: correctionNew.trim(),
    reason: skillLocale === "en-SG"
      ? "The user corrected this memory directly in the memory starfield."
      : "用户在记忆星空中直接纠正这条记忆"
  } : {
    targetType: "AURORA_UNDERSTANDING", targetId: 0, fieldName: "self_understanding",
    oldValue: correctionOld.trim() || null, newValue: correctionNew.trim(),
    reason: skillLocale === "en-SG"
      ? "The user actively calibrated Aurora's understanding in Inner Cosmos."
      : "用户在 Inner Cosmos 中主动校准"
  };

  const beginMemoryCorrection = (star: StarfieldStar) => {
    setCorrectionTarget({ id: star.id, label: star.title });
    setCorrectionOld(""); setCorrectionNew(""); setCorrectionImpact(null);
    navigate(cosmosTabPath("beliefs"));
    window.setTimeout(() => document.querySelector(".understanding-space")?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
  };

  const clearCorrectionTarget = () => { setCorrectionTarget(null); setCorrectionImpact(null); };

  const previewCorrection = async () => {
    setCorrectionBusy(true);
    try {
      setCorrectionImpact(await api.previewCorrection(correctionCommand()));
      setStatus(skillLocale === "en-SG"
        ? "Review the impact first. Aurora's understanding changes only after you confirm."
        : "先看清影响范围；只有确认后，Aurora 的理解才会改变。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This correction preview is temporarily unavailable."
      : "暂时无法预览这次纠正"); }
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
      setStatus(skillLocale === "en-SG"
        ? "Calibrated. The earlier understanding remains traceable; Aurora, the starfield and capsule context will follow your confirmed correction."
        : "已校准。旧理解仍可追溯，Aurora、星空与共鸣体上下文会按确认结果同步。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The correction was not saved; nothing downstream changed."
      : "这次纠正没有保存，任何下游都未改变"); }
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
      setStatus(skillLocale === "en-SG"
        ? "Correction retired. Aurora no longer uses it; the understanding it replaced is current again."
        : "这条更正已退休。Aurora 不再据此调整对你的理解，之前被它替代的理解会重新成为当前事实。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This correction could not be retired."
      : "暂时无法让这条更正退休"); }
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
      setCapsulePersonaClaimIds(current => current.includes(result.activeClaim.id)
        ? current : [...current, result.activeClaim.id]);
      void api.recentCorrections().then(setCorrections).catch(() => undefined);
      setStatus(skillLocale === "en-SG"
        ? "Saved. This is now a fact you confirmed, and it can shape future conversations."
        : "已记住。这条理解现在是你确认的事实，会影响以后每次对话。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This understanding could not be saved."
      : "这条理解没能保存"); }
    finally { setClaimCandidateBusyId(null); }
  };

  const confirmAllSessionCandidates = async () => {
    if (!auroraSession.sessionId) return;
    setClaimCandidateBusyId(-1);
    try {
      const ids = await api.confirmSessionClaimCandidates(auroraSession.sessionId);
      setCapsulePersonaClaimIds(current => Array.from(new Set([...current, ...ids])));
      const [freshCandidates, freshClaims] = await Promise.all([
        api.claimCandidates(auroraSession.sessionId, capsulePrivacy), api.understandingClaims()
      ]);
      setClaimCandidates(freshCandidates);
      setClaims(freshClaims);
      setStatus(skillLocale === "en-SG"
        ? "Confirmed. These reviewed traits can be snapshotted into your next private capsule."
        : "已确认。这些经过你审核的特征可以进入下一个私密共鸣体快照。");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Could not confirm this session's candidates.");
    } finally {
      setClaimCandidateBusyId(null);
    }
  };

  const dismissClaimCandidate = async (id: number) => {
    setClaimCandidateBusyId(id);
    try {
      await api.dismissClaimCandidate(id);
      setClaimCandidates(current => current.filter(candidate => candidate.id !== id));
      setStatus(skillLocale === "en-SG"
        ? "Understood. Aurora will not treat this as an understanding of you."
        : "好的，我不会把这条当作对你的理解。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This understanding could not be dismissed."
      : "暂时无法忽略这条理解"); }
    finally { setClaimCandidateBusyId(null); }
  };

  const loadPortraitHistory = async (dim: string) => {
    if (portraitHistory[dim]) return;
    try { setPortraitHistory(current => ({ ...current, [dim]: [] })); // mark as loading/loaded to avoid duplicate fetches
      const rows = await api.portraitHistory(dim);
      setPortraitHistory(current => ({ ...current, [dim]: rows }));
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The history for this facet is temporarily unavailable."
      : "暂时无法回看这一面的变化"); }
  };

  const submitPortraitCalibration = async (dim: string, oldValue: string, newValue: string) => {
    const trimmed = newValue.trim();
    if (!trimmed) return;
    setPortraitBusy(dim);
    try {
      await api.confirmCorrection({
        targetType: "PORTRAIT_DIM", targetId: 0, fieldName: dim,
        oldValue: oldValue || null, newValue: trimmed,
        reason: skillLocale === "en-SG"
          ? "The user calibrated this facet in Aurora's view of them."
          : "用户在「Aurora 眼中的你」页面校准了这一维度"
      });
      // The correction coexists alongside Aurora's own observation rather than overwriting it
      // (RUN-006 semantics). Refresh the real corrections list so portraitCalibrated (derived
      // from it above) picks this up immediately and survives a reload, instead of a local-only
      // flag that used to vanish the moment the page refreshed.
      setCorrections(await api.recentCorrections());
      setStatus(skillLocale === "en-SG"
        ? "Noted. Aurora will carry your own view forward while continuing to understand you."
        : "记下了。我会带着你这份看法继续理解你。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This was not saved; try again in a moment."
      : "没能存下，待会儿再试一次"); }
    finally { setPortraitBusy(null); }
  };

  const loadDataRightsReceipts = async () => {
    setDataRightsLoading(true);
    try { setDataRightsReceipts(await api.dataRightsReceipts()); setDataRightsLoaded(true); }
    catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "Data-rights receipts are temporarily unavailable."
      : "暂时无法读取数据权利回执"); }
    finally { setDataRightsLoading(false); }
  };

  // App-wide language: initialized from detection (loadLocale), overridable + persisted here so the
  // choice survives reloads. skillLocale is the single shared locale state (see i18n.ts).
  const changeLocale = (locale: Locale) => {
    const previousDefaults = INITIAL_DRAFT_COPY[skillLocale];
    const nextDefaults = INITIAL_DRAFT_COPY[locale];
    setSandboxQuestion(value => value === previousDefaults.sandboxQuestion ? nextDefaults.sandboxQuestion : value);
    setPersonaDraft(value => value === previousDefaults.personaDraft ? nextDefaults.personaDraft : value);
    setLetterTitle(value => value === previousDefaults.letterTitle ? nextDefaults.letterTitle : value);
    setSkillLocale(locale);
    saveLocale(locale);
  };

  // Gemini audit 4.10 (CONFIRMED/P1): returns null on confirmed success or the error message on
  // failure -- AccountSettings.tsx awaits this before deciding whether to close/clear its own form
  // (only on success) or keep it open with the user's input and this message inline (on failure).
  const changeAccountPassword = async (oldPassword: string, newPassword: string): Promise<string | null> => {
    setAccountBusy("password");
    try {
      await api.changePassword(oldPassword, newPassword);
      setAccountMessage(skillLocale === "en-SG" ? "Password updated." : "密码已更新");
      return null;
    }
    catch (error) {
      const errorMessage = error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Password update failed."
        : "密码修改失败";
      setAccountMessage(errorMessage);
      return errorMessage;
    }
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
      setAccountMessage(skillLocale === "en-SG" ? "Data exported." : "数据已导出");
    } catch (error) { setAccountMessage(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "Export failed."
      : "导出失败"); }
    finally { setAccountBusy(null); }
  };

  // Gemini audit 4.10: same null-on-success / message-on-failure contract as changeAccountPassword.
  const deleteAccount = async (password: string): Promise<string | null> => {
    setAccountBusy("delete");
    try {
      await api.deleteAccount(password);
      await mobileRuntime.clearPrivateState();
      setAuthenticated(false); auroraSession.resetSession(); setPersonaSession(null); setPersonaMessages([]);
      setAccountMessage(null);
      return null;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Account deletion failed."
        : "账户删除失败";
      setAccountMessage(errorMessage);
      return errorMessage;
    }
    finally { setAccountBusy(null); }
  };

  const saveProfile = async (patch: Partial<UserProfileSettings>) => {
    setProfileBusy(true);
    try {
      const updated = await api.updateProfile(patch);
      setUserProfile(updated);
      setAccountMessage(skillLocale === "en-SG" ? "Preferences saved." : "偏好设置已保存");
      return true;
    } catch (error) {
      setAccountMessage(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Preferences could not be saved."
        : "偏好设置未能保存");
      return false;
    }
    finally { setProfileBusy(false); }
  };

  // W2 voice: same null-on-confirmed-success / error-message-on-failure contract as
  // changeAccountPassword/deleteAccount above -- AccountSettings' VoicePreferencesEditor awaits
  // this before deciding whether to keep an optimistic selection or roll it back with an inline
  // error (see that component's applyPatch).
  const updateTtsPreferences = async (patch: TtsPreferencesPatch): Promise<string | null> => {
    setTtsBusy(true);
    try {
      const updated = await api.updateTtsPreferences(patch);
      setTtsPreferences(updated);
      setAccountMessage(skillLocale === "en-SG" ? "Voice preferences saved." : "语音偏好已保存");
      return null;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Voice preferences could not be saved."
        : "语音偏好未能保存";
      setAccountMessage(errorMessage);
      return errorMessage;
    } finally { setTtsBusy(false); }
  };

  const previewTtsVoice = (voiceId: string) => api.previewTtsVoice(voiceId).then(result => result.audio);

  const changeStarfieldMode = async (nextMode: StarfieldScene["mode"]) => {
    if (starfield?.mode === nextMode) return;
    setStarfieldBusy(true);
    setStarfieldDetail(null);
    const viewLabel = skillLocale === "en-SG"
      ? (nextMode === "TIME" ? "time" : nextMode === "THEME" ? "theme" : "people")
      : (nextMode === "TIME" ? "时间" : nextMode === "THEME" ? "主题" : "人物");
    setStatus(skillLocale === "en-SG"
      ? `Switching to the ${viewLabel} view…`
      : `正在切换到${viewLabel}视角…`);
    navigate(`${cosmosTabPath("starfield")}?view=${nextMode.toLowerCase()}`, { replace: true });
    try {
      setStarfield(await api.starfield(nextMode));
      setStatus(skillLocale === "en-SG" ? `Now viewing by ${viewLabel}.` : `已进入${viewLabel}视角`);
    }
    catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The starfield view could not be changed."
      : "暂时无法切换星空视角"); }
    finally { setStarfieldBusy(false); }
  };

  const rollbackMemoryOperation = async (operation: MemoryOperation) => {
    setRollbackBusy(operation.id);
    try {
      const result = await api.rollbackMemoryOperation(operation.id);
      setMemoryOperations(await api.memoryOperations());
      setStarfield(await api.starfield(starfield?.mode ?? "TIME"));
      setStatus(skillLocale === "en-SG"
        ? `Reverted ${operation.operationType}; ${result.projectionReceipts.length} downstream projection receipts remain for rebuild or review.`
        : `已撤回这次${operation.operationType}；${result.projectionReceipts.length} 个下游投影留下了重建或复核回执。`);
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This memory change could not be reverted safely."
      : "这次记忆变更无法安全撤回"); }
    finally { setRollbackBusy(null); }
  };

  const revealStar = async (id: number) => {
    setDetailBusy(id);
    try { setStarfieldDetail(await api.starfieldDetail(id)); }
    catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This memory's source is temporarily unavailable."
      : "暂时无法打开这颗记忆的来源"); }
    finally { setDetailBusy(null); }
  };

  const openMemoryEvidence = (id: number) => {
    navigate(cosmosTabPath("starfield"));
    void revealStar(id);
    window.setTimeout(() => document.querySelector(".provenance-panel")
      ?.scrollIntoView({ behavior: "smooth", block: "center" }), 120);
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
      setStatus(skillLocale === "en-SG"
        ? "Importance updated; this star's gravity has shifted with it."
        : "重要度已更新，这颗星的引力随之调整。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This memory's importance could not be updated."
      : "暂时无法调整这颗记忆的重要度"); }
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
      setStatus(skillLocale === "en-SG"
        ? "Memory archived. It has left the current starfield; you can revert this under Recent memory changes."
        : "这颗记忆已归档，不再出现在当前星空；你可以在“最近的记忆变更”里撤回。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This memory could not be archived."
      : "暂时无法归档这颗记忆"); }
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
      const memoryIds = selectedMemoryIds.length > 0
        ? selectedMemoryIds
        : selectableMemories.filter(memory => !["LOCAL_ONLY", "NO_EXTERNAL_PROCESSING"].includes((memory.consentScope ?? "").toUpperCase()))
          .slice(0, 3).map(memory => memory.id);
      if (memoryIds.length > 0) setSelectedMemoryIds(memoryIds);
      const preview = await api.previewCapsule(memoryIds, capsulePrivacy);
      setCapsulePreview(preview);
      if (!capsuleName.trim()) setCapsuleName(preview.suggestedPseudonym);
      setStatus(skillLocale === "en-SG"
        ? "Aurora created a private draft from your latest eligible memories. Sensitive details were removed; nothing has been published."
        : "Aurora 已用最近的可授权记忆生成私密草稿；敏感项已移除，还没有公开任何内容。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The authorization preview could not be generated."
      : "暂时无法生成授权预览"); }
    finally { setCapsuleBusy(false); }
  };

  const createCapsule = async () => {
    if (!capsulePreview) return;
    setCapsuleBusy(true);
    try {
      const created = await api.createCapsule({
        pseudonym: capsuleName.trim() || capsulePreview.suggestedPseudonym,
        intro: capsuleIntro.trim() || (skillLocale === "en-SG"
          ? "A facet shaped from memories I chose to authorize."
          : "一个由我主动授权的记忆编织而成的侧影。"),
        memoryIds: selectedMemoryIds, publicTags: capsulePreview.publicTags,
        ownerContextNote: capsuleOwnerNote.trim() || undefined, standInEnabled: capsuleStandIn,
        realContactPolicy: capsuleContactPolicy, privacyLevel: capsulePrivacy,
        personaClaimIds: capsulePersonaClaimIds
      });
      setCapsules(current => [created, ...current]);
      setSelectedCapsuleId(created.id);
      setCapsulePreview(null); setCapsuleName(""); setCapsuleIntro("");
      setCapsuleOwnerNote(""); setCapsuleStandIn(false); setCapsuleContactPolicy("LETTER_ONLY");
      setCapsulePersonaClaimIds([]);
      setStatus(skillLocale === "en-SG"
        ? "The capsule was compiled as a private version. Test whether it feels like you before deciding to publish."
        : "共鸣体已作为私密版本编译。先在沙盒里判断像不像你，再决定是否公开。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The capsule was not created; your authorization is unchanged."
      : "共鸣体没有创建，授权未改变"); }
    finally { setCapsuleBusy(false); }
  };

  const saveCapsuleContext = async (patch: { ownerContextNote: string; standInEnabled: boolean; realContactPolicy: string; conversationLimitPerDay: number }) => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.updateCapsuleContext(selectedCapsule.id, patch);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus(skillLocale === "en-SG"
        ? "Background and contact preferences saved."
        : "背景说明与联系方式设置已保存。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "These settings could not be saved."
      : "暂时无法保存这些设置"); }
    finally { setCapsuleBusy(false); }
  };

  const refreshSelectedCapsule = async (id: number) => {
    const [rows, history] = await Promise.all([api.myCapsules(), api.capsuleGenomeHistory(id)]);
    setCapsules(rows); setGenomeHistory(history); setGenomeHistoryError(false);
  };

  const recompileSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.recompileCapsule(selectedCapsule.id, selectedMemoryIds);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus(skillLocale === "en-SG"
        ? "A new private Genome version is ready. The capsule has been taken off the plaza; review and test it, then publish it again."
        : "已生成新的私密 Genome 版本，共鸣体也已从广场下架。请复核并试聊后重新发布。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "Recompile failed; the previous version remains unchanged."
      : "重新编译失败，原版本仍保持不变"); }
    finally { setCapsuleBusy(false); }
  };

  const publishSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.setCapsuleVisibility(selectedCapsule.id, "PUBLIC", true);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus(skillLocale === "en-SG"
        ? "Published. Visitors will clearly see an authorized AI capsule, not a person replying live."
        : "已发布。访客会清楚看到这是授权 AI 共鸣体，不是真人实时在线。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This version cannot be published safely yet."
      : "当前版本还不能安全发布"); }
    finally { setCapsuleBusy(false); }
  };

  const pauseSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.setCapsuleVisibility(selectedCapsule.id, "PRIVATE", false);
      await refreshSelectedCapsule(selectedCapsule.id);
      setStatus(skillLocale === "en-SG"
        ? "Publishing paused. The Genome and feedback remain, but visitors cannot discover it."
        : "已暂停公开。Genome 和反馈仍保留，访客暂时不会再发现它。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "Publishing could not be paused."
      : "暂时无法暂停公开"); }
    finally { setCapsuleBusy(false); }
  };

  const archiveSelectedCapsule = async () => {
    if (!selectedCapsule) return;
    setCapsuleBusy(true);
    try {
      await api.archiveCapsule(selectedCapsule.id);
      const rows = await api.myCapsules(); setCapsules(rows);
      setSelectedCapsuleId(rows.find(row => row.visibilityStatus !== "ARCHIVED")?.id ?? null);
      setStatus(skillLocale === "en-SG"
        ? "Withdrawn. Discovery and existing conversations can no longer let this capsule respond for you."
        : "已撤回。公开发现和既有会话都不能再让这个共鸣体代表你回应。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The capsule could not be withdrawn."
      : "暂时无法撤回共鸣体"); }
    finally { setCapsuleBusy(false); }
  };

  const runCapsuleSandbox = async () => {
    if (!selectedCapsule || !sandboxQuestion.trim()) return;
    setCapsuleBusy(true); setSandboxFeedback(null);
    try {
      setSandboxResult(await api.sandboxCapsule(selectedCapsule.id, sandboxQuestion.trim()));
      setStatus(skillLocale === "en-SG"
        ? "This response exists only in your sandbox. It was not sent to anyone and cannot change the Genome automatically."
        : "这段回应只在你的沙盒里。它不会发送给其他人，也不会自动改变 Genome。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The sandbox cannot respond right now."
      : "沙盒暂时无法回应"); }
    finally { setCapsuleBusy(false); }
  };

  const rateCapsuleSandbox = async (rating: string, comment?: string) => {
    if (!selectedCapsule || !sandboxResult) return;
    setCapsuleBusy(true);
    try {
      await api.feedbackCapsuleSandbox(selectedCapsule.id, {
        genomeVersionId: sandboxResult.genomeVersionId, question: sandboxResult.question,
        response: sandboxResult.reply, rating, comment
      });
      setSandboxFeedback(rating);
      void api.capsuleFidelity(selectedCapsule.id).then(setFidelitySummary).catch(() => undefined);
      setStatus(skillLocale === "en-SG"
        ? "Feedback saved as a signal for your next Genome version; the public version has not drifted."
        : "反馈已保存为下一次 Genome 改进信号；当前公开版本没有暗中漂移。");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The feedback was not saved."
      : "反馈暂时没有保存"); }
    finally { setCapsuleBusy(false); }
  };

  const chooseVisitorMatch = (capsuleId: number) => {
    setVisitorMatchId(capsuleId);
    setPersonaSession(null); setPersonaMessages([]); setPersonaQuota(null); setSentLetter(null); setLetterBody(""); setPersonaTurnError(null);
    letterDraftRef.current = null; // 4.5: a different compose target invalidates any pending draft for the previous one.
  };

  const openDirectoryCapsule = (capsule: PublicCapsule) => {
    setDirectoryPick(capsule);
    chooseVisitorMatch(capsule.id);
    setStatus(skillLocale === "en-SG"
      ? `You found “${capsule.pseudonym}” in the plaza. It is an authorized AI capsule, not a person replying live.`
      : `你从广场走近了「${capsule.pseudonym}」。它是授权 AI 共鸣体，不是真人实时在线。`);
    navigate(resonanceTabPath("encounters"));
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const chooseResonanceStrategy = async (strategy: ResonanceStrategy) => {
    setVisitorBusy(true);
    try {
      const matches = userVisibleResonanceMatches(await api.resonanceMatches(strategy));
      setResonanceStrategy(strategy); setResonanceMatches(matches);
      setVisitorMatchId(matches[0]?.capsule.id ?? null);
      setPersonaSession(null); setPersonaMessages([]); setPersonaQuota(null); setSentLetter(null); setLetterBody(""); setPersonaTurnError(null);
      letterDraftRef.current = null; // 4.5: a different compose target invalidates any pending draft for the previous one.
      setStatus(skillLocale === "en-SG"
        ? "Meeting approach changed; the candidates have been refreshed."
        : (matches[0]?.strategyDescription ?? "已经切换相遇方式"));
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The meeting approach could not be changed."
      : "暂时无法切换相遇方式"); }
    finally { setVisitorBusy(false); }
  };

  const startPersonaConversation = async () => {
    if (!visitorMatch) return;
    setVisitorBusy(true);
    try {
      const capsuleId = visitorMatch.capsule.id;
      const { session, quota, history, resumed } = await resumeOrCreatePersonaConversation(capsuleId, {
        activeSession: api.activePersonaSession,
        createSession: api.createPersonaSession,
        messages: api.personaMessages,
        quota: api.capsuleQuota
      });
      setPersonaSession(session); setPersonaQuota(quota); setPersonaMessages(history); setPersonaTurnError(null);
      setPersonaVoiceAudio(null); setPersonaVoiceError(null);
      setStatus(skillLocale === "en-SG"
        ? resumed
          ? `Your recent conversation with “${visitorMatch.capsule.pseudonym}” has been restored. It is an authorized AI capsule, not a person replying live.`
          : `You are talking with the authorized AI capsule “${visitorMatch.capsule.pseudonym}”, not a person replying live.`
        : resumed
          ? `已恢复你和「${visitorMatch.capsule.pseudonym}」最近的对话。它是授权 AI 共鸣体，不是真人实时在线。`
          : `你正在和「${visitorMatch.capsule.pseudonym}」的授权 AI 共鸣体对话，不是真人实时在线。`);
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This capsule is temporarily unavailable."
      : "暂时无法进入这个共鸣体"); }
    finally { setVisitorBusy(false); }
  };

  const sendPersonaTurn = async () => {
    if (!personaSession || !visitorMatch || !personaDraft.trim()) return;
    setVisitorBusy(true); setPersonaTurnError(null);
    try {
      await api.sendPersonaMessage(personaSession.id, personaDraft.trim());
      const [history, quota] = await Promise.all([
        api.personaMessages(personaSession.id), api.capsuleQuota(visitorMatch.capsule.id)
      ]);
      setPersonaMessages(history); setPersonaQuota(quota); setPersonaDraft("");
      // Clear the previous reply's synthesized audio so the play affordance reappears for the new reply.
      setPersonaVoiceAudio(null); setPersonaVoiceError(null);
      setStatus(skillLocale === "en-SG"
        ? "The response came from an authorized Genome. Keep testing the resonance, or put what matters into a slow letter."
        : "回应来自授权 Genome；你可以继续验证共鸣，也可以把真正想说的内容写成慢信。 ");
    } catch (error) { setPersonaTurnError(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This turn was not delivered; your draft is still here."
      : "这轮对话没有送达，草稿内容仍在这里"); }
    finally { setVisitorBusy(false); }
  };

  const reportPersonaSession = async () => {
    if (!personaSession) return;
    try {
      await api.reportPersonaSession(personaSession.id, skillLocale === "en-SG"
        ? "A visitor reported this capsule during the conversation."
        : "访客在对话中举报了这个共鸣体");
      setStatus(skillLocale === "en-SG"
        ? "Report submitted. Conversation content is not published; access is restricted to review."
        : "已提交举报。举报不会自动公开对话内容，交由受限审核处理。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The report could not be submitted."
      : "暂时无法提交举报"); }
  };

  const blockPersonaSession = async () => {
    if (!personaSession) return;
    try {
      const blockedCapsuleId = personaSession.capsuleId;
      await api.blockPersonaSession(personaSession.id);
      setPersonaSession(null); setPersonaMessages([]); setPersonaQuota(null);
      setPersonaDraft(initialDrafts.personaDraft); setPersonaTurnError(null);
      setPersonaVoiceAudio(null); setPersonaVoiceError(null);
      setSentLetter(null); setLetterBody(""); letterDraftRef.current = null;
      // A block is owner-level, not capsule-level. Clear every visible candidate immediately so
      // another capsule from the same owner cannot remain actionable while the authoritative
      // viewer-filtered lists are refreshed.
      setResonanceMatches([]);
      setPublicCapsules([]);
      setDirectoryPick(current => current?.id === blockedCapsuleId ? null : current);
      setVisitorMatchId(null);
      try {
        const { matches, plaza } = await reloadPersonaCandidates(resonanceStrategy, {
          matches: api.resonanceMatches,
          plaza: api.plazaCapsules
        });
        setResonanceMatches(userVisibleResonanceMatches(matches));
        setPublicCapsules(userVisiblePublicCapsules(plaza));
        setStatus(skillLocale === "en-SG"
          ? "Capsule owner blocked. Their capsules were removed from meetings and the plaza."
          : "已屏蔽该共鸣体的主人；对方的共鸣体已从相遇和广场候选中移除。");
      } catch {
        setStatus(skillLocale === "en-SG"
          ? "The block succeeded. Candidate refresh failed, so meeting and plaza results stay hidden until they can be safely reloaded."
          : "屏蔽已成功，但候选刷新失败；为避免再次显示对方，相遇与广场会保持隐藏，直到能够安全重载。");
      }
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "The capsule could not be blocked."
      : "暂时无法屏蔽"); }
  };

  const markCurrentCapsuleLanded = async () => {
    if (!visitorMatch || landedCapsuleIds.has(visitorMatch.capsule.id) || landedBusyId !== null) return;
    const capsuleId = visitorMatch.capsule.id;
    setLandedBusyId(capsuleId);
    try {
      const { echoEnergy } = await api.markLanded(capsuleId);
      setLandedCapsuleIds(current => new Set(current).add(capsuleId));
      setResonanceMatches(rows => rows.map(match => match.capsule.id === capsuleId
        ? { ...match, capsule: { ...match.capsule, echoEnergy } }
        : match));
      setPublicCapsules(rows => rows.map(capsule => capsule.id === capsuleId ? { ...capsule, echoEnergy } : capsule));
      setDirectoryPick(current => current?.id === capsuleId ? { ...current, echoEnergy } : current);
      setStatus(skillLocale === "en-SG"
        ? "You left one meaningful echo. Repeated taps cannot add more."
        : "这条共鸣已被认真记下；重复点击不会继续增加能量。");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "The echo was not recorded. You can retry."
        : "这条回声还没有记下，可以重试。");
    } finally {
      setLandedBusyId(null);
    }
  };

  const playPersonaVoice = async () => {
    if (!personaSession) return;
    setPersonaVoiceBusy(true); setPersonaVoiceError(null);
    try {
      const { audio } = await api.personaVoice(personaSession.id);
      setPersonaVoiceAudio(audio);
    } catch (error) {
      setPersonaVoiceError(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "Capsule voice is temporarily unavailable."
        : "共鸣体语音暂时不可用");
    } finally { setPersonaVoiceBusy(false); }
  };

  const sendLetterToMatch = async () => {
    if (!visitorMatch || !letterTitle.trim() || !letterBody.trim()) return;
    setVisitorBusy(true);
    try {
      // Gemini audit 4.5: reuses letterDraftRef.current (set by a prior failed attempt for this
      // SAME compose) instead of unconditionally drafting again -- a retry after a failed send
      // must never produce a second, duplicate draft.
      const sent = await sendComposedLetter({
        pending: letterDraftRef.current,
        onDraftCreated: next => { letterDraftRef.current = next; },
        createDraft: idempotencyKey => api.draftSlowLetter(visitorMatch.capsule.id, letterTitle.trim(), letterBody.trim(), idempotencyKey),
        sendDraft: (draftId, idempotencyKey) => api.sendSlowLetter(draftId, idempotencyKey)
      });
      letterDraftRef.current = null; // sent successfully -- clear so the next compose starts fresh.
      setSentLetter(sent);
      void connectionsAndLetters.loadLetterOutbox();
      setStatus(skillLocale === "en-SG"
        ? "Your slow letter is on its way. The recipient sees your words and a safety preview, not a uniform AI-written template."
        : "慢信已经启程。收件人看到的是你的原话和安全预览，不是 AI 代写的统一模板。 ");
    } catch (error) {
      // letterDraftRef.current is intentionally left set (if a draft was created) so the user's
      // next click on "send" retries the send against the SAME draft rather than creating another.
      setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
        ? "The slow letter was not sent; your draft is still here."
        : "慢信没有发送，草稿内容仍在这里");
    }
    finally { setVisitorBusy(false); }
  };

  const logout = async () => {
    let remoteWarning: string | null = null;
    try {
      if ((Capacitor.isNativePlatform() || isTauriRuntime()) && !demoModeBuild) {
        try { await mobileOidc.logout(); }
        catch { remoteWarning = skillLocale === "en-SG"
          ? "Remote revocation was not confirmed."
          : "远程撤销未确认"; }
      }
      else await api.logout();
      await mobileRuntime.clearPrivateState();
      setAuthenticated(false); auroraSession.resetSession(); setPersonaSession(null); setPersonaMessages([]);
      setStatus(remoteWarning ?? (skillLocale === "en-SG" ? "Signed out safely." : "已安全退出"));
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "Could not sign out right now."
      : "暂时无法退出"); }
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
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This reflection could not be completed."
      : "这项反思暂时没有完成"); }
    finally { setSkillBusy(false); }
  };

  const revokePsychologyRun = async (runId: number) => {
    setSkillBusy(true);
    try {
      const revoked = await api.revokePsychologySkillRun(runId);
      setSkillRuns(current => current.map(run => run.id === runId ? revoked : run));
      setStatus(skillLocale === "en-SG" ? "This Skill result has been revoked and its saved content cleared." : "这次 Skill 结果已经撤回，保存的结果内容已清除。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : skillLocale === "en-SG"
      ? "This result could not be revoked."
      : "暂时无法撤回这次结果"); }
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

  const tt = APP_COPY[skillLocale];
  if (mobileState.native && (!hasConfiguredApiBase || apiConfigurationError)) return <main className="login-shell"><div className="login mobile-gate" role="alert">
    <span className="eyebrow">{skillLocale === "en-SG" ? "MOBILE ENVIRONMENT GATE" : "移动环境安全门"}</span>
    <h1>{skillLocale === "en-SG"
      ? "This device does not have a secure backend endpoint yet"
      : "这台设备还没有安全后端入口"}</h1>
    <p>{apiConfigurationError ?? (skillLocale === "en-SG"
      ? <>The app shell, deep links and recovery are ready, but this build has no <code>VITE_API_BASE_URL</code>.</>
      : <>应用壳、深链与恢复能力已经就绪，但本次构建没有注入 <code>VITE_API_BASE_URL</code>。</>)}
      {skillLocale === "en-SG"
        ? " Aurora will not attempt to sign in because that could send credentials and sessions to the wrong host."
        : " 为避免把凭据和会话发往错误地址，Aurora 不会尝试登录。"}</p>
    <small>{skillLocale === "en-SG"
      ? "Rebuild with a verified HTTPS API domain. Push credentials and store signing must also come from an authorised environment."
      : "请使用经过验证的 HTTPS API 域重新构建；推送凭据与商店签名也必须由授权环境提供。"}</small>
  </div></main>;
  if (authenticated === null) return <main className="login-shell"><div className="login">
    {bootstrapError
      ? <ConnectError message={bootstrapError} onRetry={() => void bootstrap()} locale={skillLocale} />
      : <LoadingText busy>{tt.connecting.replace(/…$/, "")}</LoadingText>}
  </div></main>;
  if (!authenticated) return <AuthGate native={mobileState.native && !demoModeBuild} onSuccess={bootstrap} locale={skillLocale}
    externalError={bootstrapError ?? ""} />;

  // A freely reachable support space (Phase 0, safety-critical) -- deliberately NOT one of the five
  // ProductSpace tabs (see ProductShell.tsx's five-space information architecture), so it renders as
  // its own standalone route instead of another `hidden`-toggled product-space div.
  if (location.pathname === "/safety-harbor" || location.pathname.startsWith("/safety-harbor/")) {
    return <SafetyHarborPage resources={auroraSession.safetyResources} locale={skillLocale}
      onBack={() => navigate(spacePath("aurora"))}
      onTalkToAurora={() => navigate(spacePath("aurora"))} />;
  }

  // Phase 3 port of the legacy static /pages/admin.html (8-tab moderation console). Like Safety
  // Harbor above, this is deliberately its own standalone route, not a 6th ProductShell space (see
  // ProductShell.tsx's five-space consumer information architecture). The backend `requireAdmin`-
  // gates every /api/admin/* and /api/abtest/* call (see AdminController etc.), and -- as of the
  // W0V 2.2.5 fix -- /api/ai-logs too (it has no other caller). /api/ai/health is deliberately NOT
  // admin-gated: ThoughtShredderSection also reads it as an ordinary user's own "is AI real or
  // mock" status, so it stays any-authenticated-user, with its own per-caller data scoped instead
  // (see AiHealthController). This frontend check is a UX gate on top of those, not the
  // authorization boundary itself: a non-admin session is redirected back to a normal space
  // instead of ever mounting AdminConsole (which would otherwise just show empty lists / 401s for
  // every admin-only tab). While userProfile hasn't loaded yet (a brief window right after
  // bootstrap), wait rather than guessing -- redirecting an actual admin away on a false negative
  // would be worse than a moment's delay.
  if (location.pathname === "/admin" || location.pathname.startsWith("/admin/")) {
    if (userProfile === null) return <main className="login-shell"><div className="login">
      <LoadingText busy>{tt.connecting.replace(/…$/, "")}</LoadingText>
    </div></main>;
    if (userProfile.role !== "ADMIN") return <Navigate to={spacePath("aurora")} replace />;
    return <AdminConsole locale={skillLocale} onBack={() => navigate(spacePath("aurora"))} />;
  }

  return (
    <main className={`shell space-${productSpace}`} data-product-space={productSpace} aria-label="Authenticated Inner Cosmos">
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
      <ProductShellNavigation active={productSpace} onNavigate={navigateSpace} locale={skillLocale} />
      <DemoPersonaChooser compact currentUsername={userProfile?.username ?? null} locale={skillLocale}
        onEntered={bootstrap} />

      {/* Gemini audit 4.7: each product space gets its own ErrorBoundary so a crash rendering one
          space (all five are always mounted, just `hidden`, to preserve scroll/edit state across
          tab switches) never takes the other four -- or the shared nav/footer below -- down too. */}
      <ErrorBoundary variant="space" locale={skillLocale}>
      <div className="product-space aurora-space" hidden={productSpace !== "aurora"}>
      <div className="aurora-stage">
      <section className="aurora-primary" aria-label={skillLocale === "en-SG" ? "Aurora conversation" : "Aurora 对话主舞台"}>
      <header className="hero">
        <div>
          <span className="eyebrow">{tt.heroEyebrow}</span>
          <h1>{tt.heroLine1}<br />{tt.heroLine2}</h1>
          <p>{tt.heroP}</p>
          <div className={`runtime-signal ${auroraSession.runtimeSignal.stage}`} aria-label={tt.runtimeAria}>
            <span>{auroraSession.runtimeSignal.stage === "understanding" ? tt.runtimeUnderstanding : auroraSession.runtimeSignal.stage === "composing" ? tt.runtimeComposing : auroraSession.runtimeSignal.stage === "speaking" ? tt.runtimeSpeaking : tt.runtimeHere}</span>
            {auroraSession.runtimeSignal.runtime === "dual" && <small>{tt.dualCore}</small>}
            {auroraSession.runtimeSignal.relationshipMove && <small>{tt.relationshipMovePrefix}{auroraSession.runtimeSignal.relationshipMove}</small>}
            {auroraSession.runtimeSignal.repaired && <small>{tt.repaired}</small>}
          </div>
        </div>
        <div className="orb" aria-hidden="true"><span /></div>
      </header>

      <nav className="modes" aria-label={tt.modesAria}>
        {modes.map(([value]) => <button key={value} className={auroraSession.mode === value ? "active" : ""} onClick={() => auroraSession.setMode(value)}>{tt.modeLabel[value as DialogMode]}</button>)}
      </nav>
      <ConversationHistory sessions={auroraSession.sessions} currentSessionId={auroraSession.sessionId}
        busy={auroraSession.sessionsBusy} locale={skillLocale}
        onOpen={session => void auroraSession.openSession(session)}
        onNew={() => void auroraSession.newConversation()}
        onRename={auroraSession.renameConversation}
        onPin={session => void auroraSession.pinConversation(session)}
        onArchive={session => void auroraSession.archiveConversation(session)}
        onReload={auroraSession.loadSessions} />
      {auroraSession.mode === "CAPSULE_SHAPING" && <section className="capsule-shaping-intro" aria-label={skillLocale === "en-SG" ? "Shape a capsule with Aurora" : "和 Aurora 一起塑造共鸣体"}>
        <div><span className="eyebrow">{skillLocale === "en-SG" ? "5-MINUTE LIVING PORTRAIT" : "五分钟 · 鲜活侧影"}</span>
          <strong>{skillLocale === "en-SG" ? "Tell stories, not personality labels." : "讲故事，不填人格问卷。"}</strong>
          <p>{skillLocale === "en-SG"
            ? "Aurora notices what is still thin—values, tensions, voice, boundaries and who you hope to meet—and asks one natural question at a time."
            : "Aurora 会感知价值、张力、表达方式、边界与期待遇见的人中哪里还单薄，每次只自然地聊一个最值得补充的部分。"}</p></div>
        <button type="button" className="quiet" onClick={() => navigate(resonanceTabPath("mine"))}>
          {skillLocale === "en-SG" ? "Preview the capsule workspace" : "先看看共鸣体工作台"}
        </button>
      </section>}

      <AuroraInnerVoiceAside voice={auroraSession.innerVoice}
        enabled={ttsPreferences?.innerVoiceEnabled ?? false}
        mode={ttsPreferences?.innerVoiceMode ?? "AMBIENT"}
        locale={skillLocale}
        onDismiss={auroraSession.dismissInnerVoice} />

      <AuroraMemoryTrace trace={auroraSession.memoryTrace} memories={memories}
        locale={skillLocale} onOpenMemory={openMemoryEvidence}
        onDismiss={auroraSession.dismissMemoryTrace} />

      {/* The composer sits directly after the hero/mode-picker, before the WakeIntent and
          Self/Emergence "capability display" panels below, so a first-time user (mobile
          especially) can reach it without scrolling past those panels first. Previously this
          lived in a second, disconnected `aurora` block far down in DOM order (after the
          cosmos/resonance/letters spaces) purely because of source-file history -- see
          golden-journeys.md J1 step 4 and 对齐文档/20 4.3's "旅程像能力陈列" finding. */}
      {skillSuggestion && <SkillSuggestionBanner suggestion={skillSuggestion} locale={skillLocale}
        onOpen={openSuggestedSkill} onDismiss={() => setSkillSuggestion(null)} />}

      <SafetyResourceCard alert={auroraSession.safetyAlert} resources={auroraSession.safetyResources}
        locale={skillLocale} onDismiss={auroraSession.dismissSafetyAlert}
        onOpenHarbor={() => navigate("/safety-harbor")} />

      <GoodbyeRitualCard result={auroraSession.goodbyeResult} locale={skillLocale} onDismiss={auroraSession.dismissGoodbye} />

      <AuroraConversation messages={auroraSession.messages} activeTurnId={auroraSession.activeTurnId}
        thinkingStage={auroraSession.activeTurnId !== null && (auroraSession.runtimeSignal.stage === "understanding" || auroraSession.runtimeSignal.stage === "composing") ? auroraSession.runtimeSignal.stage : null}
        draft={auroraSession.draft} sessionReady={Boolean(auroraSession.sessionId)}
        onDraftChange={auroraSession.setDraft} onSubmit={auroraSession.send} onStop={() => void auroraSession.stop()}
        onTranscribe={async blob => {
          try { const result = await transcribeAudio(blob); return result.text; }
          catch (error) { setStatus(error instanceof Error ? error.message : tt.transcribeUnavailable); return ""; }
        }} onGoodbye={() => void auroraSession.triggerGoodbye()} goodbyeBusy={auroraSession.goodbyeBusy}
        innerVoiceEnabled={ttsPreferences?.innerVoiceEnabled ?? false}
        innerVoiceMode={ttsPreferences?.innerVoiceMode ?? "AMBIENT"}
        claimCandidates={claimCandidates} claimCandidateBusyId={claimCandidateBusyId}
        onConfirmClaim={id => void confirmClaimCandidate(id)}
        onDismissClaim={id => void dismissClaimCandidate(id)}
        locale={skillLocale} />
      </section>

      <aside className="aurora-context-rail" aria-label={skillLocale === "en-SG" ? "Today and next steps" : "今日概览与下一步"}>
        {userProfile && <QuickHello profile={userProfile} locale={skillLocale} onSave={saveProfile}
          onBegin={() => void auroraSession.greet()} />}

        <TodayOverview memoryCount={memories.length} latestMemory={memories[0]?.title ?? null}
          arrivedLetters={connectionsAndLetters.letterInbox.length}
          latestLetter={connectionsAndLetters.letterInbox[0]?.title ?? null}
          publicCapsules={capsules.filter(capsule => capsule.visibilityStatus === "PUBLIC").length}
          wakeIntents={auroraSession.wakeIntents.length}
          onOpenCosmos={() => navigateSpace("cosmos")}
          onOpenLetters={() => navigate(connectionTabPath("letters"))}
          onOpenResonance={() => navigateSpace("resonance")}
          onWriteLetter={() => navigate(connectionTabPath("letters"))}
          onOpenReturns={() => document.querySelector(".returns")?.scrollIntoView({ behavior: "smooth", block: "start" })}
          locale={skillLocale} />

        <StartHereJourney
          locale={skillLocale}
          isDemoSandbox={Boolean(userProfile?.username?.startsWith("sandbox-"))}
          completedSteps={completedJourneySteps}
          onStep={(step: JourneyStep) => {
            if (step === "aurora") {
              document.querySelector(".composer")?.scrollIntoView({ behavior: "smooth", block: "center" });
              return;
            }
            if (step === "memory") {
              navigate(cosmosTabPath("starfield"));
              return;
            }
            if (step === "capsule") {
              auroraSession.setMode("CAPSULE_SHAPING");
              document.querySelector(".modes")?.scrollIntoView({ behavior: "smooth", block: "center" });
              return;
            }
            if (step === "letter") {
              navigate(connectionTabPath("letters"));
              return;
            }
            navigate(resonanceTabPath("encounters"));
            window.scrollTo({ top: 0, behavior: "smooth" });
          }}
        />
      </aside>
      </div>

      {(mobileState.native || !mobileState.connected) && <section className={`mobile-presence ${mobileState.connected ? "online" : "offline"}`} aria-label={tt.mobileAria}>
        <div>
          <span className="eyebrow">{tt.presenceEyebrow}</span>
          <strong>{mobileState.connected ? tt.mobileConnected : tt.mobileOffline}</strong>
          <p>{mobileState.connected
            ? tt.mobileConnectedP(mobileState.platform.toUpperCase(), mobileState.connectionType)
            : tt.mobileOfflineP}</p>
        </div>
        {mobileState.native && <div className="mobile-actions">
          <button type="button" onClick={() => void requestMobilePush()}>{tt.pushBtn}</button>
          <button type="button" onClick={() => void requestMobileMicrophone()}>{tt.micBtn}</button>
        </div>}
      </section>}

      <section className="returns" aria-label={tt.returnsAria}>
        <div className="returns-head"><div><span className="eyebrow">{tt.returnsEyebrow}</span><h2>{tt.returnsTitle}</h2></div>
          <div className="return-negotiate">
          <label>{skillLocale === "en-SG" ? "What should Aurora return for?" : "这次回来，想继续什么"}
            <select aria-label={skillLocale === "en-SG" ? "Return purpose" : "回来约定的目的"}
              value={auroraSession.returnPurpose} onChange={event => auroraSession.setReturnPurpose(event.target.value)}>
              {(skillLocale === "en-SG"
                ? ["Continue what we left unfinished", "Check whether the hardest thing has eased",
                  "Help me begin one small action", "Close the day together"]
                : ["继续这一刻未说完的话", "看看今天最难的事有没有松一点",
                  "陪我开始一个很小的行动", "在一天结束时一起收尾"])
                .map(purpose => <option key={purpose} value={purpose}>{purpose}</option>)}
            </select></label>
          <label>{tt.whenLabel}<input aria-label={tt.returnTimeAria} value={auroraSession.returnWhen} onChange={event => auroraSession.setReturnWhen(event.target.value)} /></label>
          <button type="button" disabled={auroraSession.wakeBusy || !auroraSession.returnWhen.trim()} onClick={() => void auroraSession.scheduleReturn()}>{tt.scheduleBtn}</button></div></div>
        <div className="return-presets" aria-label={skillLocale === "en-SG" ? "Common return times" : "常用回来时间"}>
          {(skillLocale === "en-SG"
            ? ["Tonight at 10", "Tomorrow morning at 8:30", "Friday after work", "Next Sunday afternoon"]
            : ["今晚 10 点", "明天早上 8:30", "周五下班后", "下周日下午"]).map(value =>
              <button type="button" key={value} onClick={() => auroraSession.setReturnWhen(value)}>{value}</button>)}
        </div>
        <p className="returns-autonomy">{skillLocale === "en-SG"
          ? "You can keep several different return plans. When your chats reveal a stable routine or something unfinished, Aurora may also gently propose one—never force it."
          : "不同目的的约定可以同时存在。对话里出现稳定作息或未完成的事时，Aurora 也可以主动提出一个合适的回来时间，但不会替你强制开启。"}</p>
        {auroraSession.wakeIntents.length === 0 ? <p className="returns-empty">{tt.returnsEmpty}</p> :
          <div className="return-list">{auroraSession.wakeIntents.map(intent => <article key={intent.id} className="return-card">
            <div><strong>{intent.reasonForUser}</strong><span>{new Date(intent.preferredAt).toLocaleString(skillLocale, { dateStyle: "short", timeStyle: "short" })}</span><small>{intent.purpose}</small></div>
            <div className="return-actions"><button type="button" disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.postponeReturn(intent)}>{tt.postpone}</button><button type="button" disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.cancelReturn(intent)}>{tt.cancel}</button></div>
          </article>)}</div>}
      </section>

      {auroraSession.notifications.filter(notice => notice.refType === "WAKE_INTENT").map(notice =>
        <section className="return-arrival" aria-label={tt.arrivalAria} key={notice.id}>
          <span className="eyebrow">{tt.returnedEyebrow}</span><h2>{notice.title}</h2><p>{notice.body}</p>
          <a href={`?wakeIntent=${notice.refId}`}>{tt.backToUnfinished}</a>
          <div className="return-actions"><button disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.respondToReturn(notice, "MATCHED")}>{tt.matched}</button>
            <button disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.respondToReturn(notice, "LATER")}>{tt.later}</button>
            <button disabled={auroraSession.wakeBusy} onClick={() => void auroraSession.respondToReturn(notice, "STOP_SIMILAR")}>{tt.stopSimilar}</button></div>
        </section>)}

      {selfEvolution && <AuroraSelfSpace evolution={selfEvolution} busy={selfBusy}
        onPropose={candidateId => void evolve(
          () => api.proposeSelfEvolution(candidateId, skillLocale === "en-SG"
            ? "Help Aurora stay continuous in similar moments and faithful to the way this relationship has actually developed."
            : "让 Aurora 在相似时刻更连续、更贴近双方已经形成的相处方式"),
          skillLocale === "en-SG"
            ? "This is still only a proposal. You can inspect how it would change Aurora first."
            : "这还只是一个提案。你可以先看它会怎样改变 Aurora。")}
        onEvaluate={proposalId => void evolve(() => api.evaluateSelfEvolution(proposalId), skillLocale === "en-SG"
          ? "Sandbox evaluation is complete. Nothing changes until you confirm it."
          : "沙盒评测完成。变化不会在你确认前生效。")}
        onActivate={proposalId => void evolve(() => api.activateSelfEvolution(proposalId), skillLocale === "en-SG"
          ? "This change is now part of Aurora's new version, and it can still be rolled back."
          : "这次变化已经成为新的 Aurora 版本，并且仍然可以回退。")}
        onRollback={(versionId, versionNo) => void evolve(() => api.rollbackSelfEvolution(versionId), skillLocale === "en-SG"
          ? `Returned to version ${versionNo}; the rollback itself remains traceable as a new version.`
          : `已回到第 ${versionNo} 版；回退本身也留下了可追溯的新版本。`)} locale={skillLocale} />}
      </div>
      </ErrorBoundary>

      <ErrorBoundary variant="space" locale={skillLocale}>
      <div className="product-space" hidden={productSpace !== "cosmos"}>
      {/* Cosmos-internal secondary navigation (doc 24 section 3.3): five sub-sections, each a
          real shareable /cosmos/<tab> URL, mounted-but-hidden like the five top-level spaces so
          switching back and forth keeps scroll/edit state. Each tab's data lazy-loads on first
          visit -- see the cosmosTabLoadedRef effect above -- instead of blocking login bootstrap. */}
      <CosmosSubNav active={cosmosTab} onNavigate={navigateCosmosTab} locale={skillLocale} />

      <div hidden={cosmosTab !== "starfield"}>
        {starfield && <InnerCosmosOverview starfield={starfield}
          dailyRecords={dailyRecord.dailyRecords} themes={memoryThemes} locale={skillLocale}
          onOpenMemory={id => void revealStar(id)}
          onOpenDaily={() => navigateCosmosTab("daily")}
          onOpenWeekly={() => navigateCosmosTab("weekly")}
          onOpenBeliefs={() => navigateCosmosTab("beliefs")} />}

        {starfield && <MemoryStarfield starfield={starfield} starfieldBusy={starfieldBusy} onChangeMode={mode => void changeStarfieldMode(mode)}
          starfieldDetail={starfieldDetail} detailBusy={detailBusy} onRevealStar={id => void revealStar(id)} onCloseDetail={() => setStarfieldDetail(null)}
          memoryOperations={memoryOperations} rollbackBusy={rollbackBusy} onRollback={operation => void rollbackMemoryOperation(operation)} onCorrectMemory={beginMemoryCorrection}
          onUpdateImportance={(id, importance) => void updateMemoryImportance(id, importance)} onArchive={id => void archiveMemory(id)}
          onStartMemory={() => navigateSpace("aurora")} importanceBusy={importanceBusy} archiveBusy={archiveBusy} locale={skillLocale} />}

        <TimelineSection dailyRecords={dailyRecord.dailyRecords} themes={memoryThemes} locale={skillLocale} />
      </div>

      <div hidden={cosmosTab !== "daily"}>
        <DailyRecordSection records={dailyRecord.dailyRecords} detail={dailyRecord.dailyRecordDetail}
          index={dailyRecord.dailyRecordIndex} acceptBusy={dailyRecord.dailyRecordAcceptBusy} editBusy={dailyRecord.dailyRecordEditBusy}
          onAccept={() => void dailyRecord.acceptDailyRecord()} onEditField={(field, value) => void dailyRecord.editDailyRecordField(field, value)}
          onSelectIndex={dailyRecord.selectDailyRecordIndex} locale={skillLocale} />
        <HeartDiary rawText={heartDiary.rawText} displayText={heartDiary.displayText} activeLevel={heartDiary.activeLevel}
          polishBusy={heartDiary.polishBusy} submitBusy={heartDiary.submitBusy} onTextChange={heartDiary.onTextChange}
          onSwitchLevel={level => void heartDiary.switchLevel(level)} onTranscribeAudio={blob => heartDiary.transcribeAudio(blob)}
          onSubmit={() => void heartDiary.submit()} locale={skillLocale} />
      </div>

      <div hidden={cosmosTab !== "weekly"}>
        <WeeklyReviewSection review={weeklyReview.weeklyReview} busy={weeklyReview.weeklyReviewBusy}
          onGenerate={() => void weeklyReview.generateWeeklyReview()} locale={skillLocale} />
      </div>

      <div hidden={cosmosTab !== "thoughts"}>
        <ThoughtShredderSection aiHealth={thoughtShredder.shredderAiHealth} history={thoughtShredder.shredderHistory}
          result={thoughtShredder.shredderResult} busy={thoughtShredder.shredderBusy}
          onShred={(text, saveMode) => void thoughtShredder.processShred(text, saveMode)}
          onSettle={id => void thoughtShredder.settleShred(id)} onDelete={id => void thoughtShredder.deleteShred(id)} locale={skillLocale} />
        <TodoBoard todos={todoBoard.todos} tab={todoBoard.tab} busy={todoBoard.busy} splitBusyId={todoBoard.splitBusyId}
          onSelectTab={todoBoard.setTab} onCreate={input => void todoBoard.createTodo(input)}
          onUpdateStatus={(id, status) => void todoBoard.updateStatus(id, status)} onSplit={id => void todoBoard.splitTodo(id)}
          onDelete={id => void todoBoard.deleteTodo(id)} onUpdate={(id, input) => void todoBoard.updateTodo(id, input)} locale={skillLocale} />
      </div>

      <div hidden={cosmosTab !== "beliefs"}>
        <ClaimCandidateReview candidates={claimCandidates} locale={skillLocale} busyId={claimCandidateBusyId}
          onConfirm={id => void confirmClaimCandidate(id)} onConfirmAll={() => void confirmAllSessionCandidates()}
          onDismiss={id => void dismissClaimCandidate(id)} />
        <UnderstandingCorrection claims={claims} oldValue={correctionOld} newValue={correctionNew} impact={correctionImpact} busy={correctionBusy} target={correctionTarget}
          corrections={corrections} retiringId={retiringCorrectionId}
          onOldValue={value => { setCorrectionOld(value); setCorrectionImpact(null); }} onNewValue={value => { setCorrectionNew(value); setCorrectionImpact(null); }}
          onPreview={() => void previewCorrection()} onCancelPreview={() => setCorrectionImpact(null)} onConfirm={() => void confirmCorrection()} onClearTarget={clearCorrectionTarget}
          onRetire={id => void retireCorrection(id)} locale={skillLocale} />
        <BeliefGallery beliefs={beliefGallery.beliefs} contradictions={beliefGallery.contradictions} filter={beliefGallery.filter}
          categories={beliefGallery.categories} selectedCategory={beliefGallery.selectedCategory} categoryBeliefs={beliefGallery.categoryBeliefs}
          busy={beliefGallery.busy} onSelectFilter={filter => void beliefGallery.selectFilter(filter)}
          onSelectCategory={category => void beliefGallery.selectCategory(category)} locale={skillLocale} />
        <PsychologySkillStudio skills={skills} skillRuns={skillRuns} selectedSkill={selectedSkill} skillAnswers={skillAnswers}
          skillConsent={skillConsent} skillRetention={skillRetention} skillBusy={skillBusy} skillLocale={skillLocale}
          onLocaleChange={setSkillLocale} onSelectSkill={skillId => { setSelectedSkillId(skillId); setSkillAnswers({}); setSkillConsent(false); }}
          onAnswerChange={(key, value) => setSkillAnswers(current => ({ ...current, [key]: value }))}
          onRetentionChange={setSkillRetention} onConsentChange={setSkillConsent} onRun={() => void runPsychologySkill()}
          onContinueWithAurora={continueSkillWithAurora} onRevokeRun={id => void revokePsychologyRun(id)} />
      </div>
      </div>
      </ErrorBoundary>

      <ErrorBoundary variant="space" locale={skillLocale}>
      <div className="product-space" hidden={productSpace !== "resonance"}>
      <ResonanceSubNav active={resonanceTab} onNavigate={navigateResonanceTab} locale={skillLocale} />
      <div hidden={resonanceTab !== "mine"}>
      <CapsuleWorkbench capsules={capsules} selectedCapsuleId={selectedCapsuleId} selectedCapsule={selectedCapsule}
        selectableMemories={selectableMemories} selectedMemoryIds={selectedMemoryIds} capsuleName={capsuleName} capsuleIntro={capsuleIntro}
        capsulePreview={capsulePreview} capsuleBusy={capsuleBusy} genomeHistory={genomeHistory} genomeHistoryError={genomeHistoryError}
        fidelitySummary={fidelitySummary} sandboxQuestion={sandboxQuestion}
        sandboxResult={sandboxResult} sandboxFeedback={sandboxFeedback}
        onSelectCapsule={id => {
          setSelectedCapsuleId(id);
          if (id === null) { setSelectedMemoryIds([]); setCapsulePreview(null); navigate(spacePath("resonance")); }
          else { navigate(capsulePath(id)); }
        }}
        onToggleMemory={toggleCapsuleMemory} onCapsuleName={setCapsuleName} onCapsuleIntro={setCapsuleIntro}
        onPreviewNewCapsule={() => void previewNewCapsule()} onCancelPreview={() => setCapsulePreview(null)} onCreateCapsule={() => void createCapsule()}
        onRecompile={() => void recompileSelectedCapsule()} onSandboxQuestion={setSandboxQuestion} onRunSandbox={() => void runCapsuleSandbox()}
        onRateSandbox={(rating, comment) => void rateCapsuleSandbox(rating, comment)} onPublish={() => void publishSelectedCapsule()}
        onPause={() => void pauseSelectedCapsule()} onArchive={() => void archiveSelectedCapsule()}
        boundary={capsuleBoundary} boundaryBusy={boundaryBusy} boundaryLoadFailed={boundaryLoadFailed}
        onRetryBoundary={() => {
          if (!selectedCapsule) return;
          setBoundaryLoadFailed(false);
          void api.capsuleBoundary(selectedCapsule.id).then(value => {
            setCapsuleBoundary(value); setBoundaryLoadFailed(value === null);
          }).catch(() => setBoundaryLoadFailed(true));
        }}
        onRetryGenomeHistory={() => void retryGenomeHistory()}
        onSaveBoundary={boundary => void saveCapsuleBoundary(boundary)}
        capsuleOwnerNote={capsuleOwnerNote} onCapsuleOwnerNote={setCapsuleOwnerNote}
        capsuleStandIn={capsuleStandIn} onCapsuleStandIn={setCapsuleStandIn}
        capsuleContactPolicy={capsuleContactPolicy} onCapsuleContactPolicy={setCapsuleContactPolicy}
        capsulePrivacy={capsulePrivacy} onCapsulePrivacy={value => {
          setCapsulePrivacy(value); setCapsulePreview(null);
        }}
        onSaveContext={patch => void saveCapsuleContext(patch)} locale={skillLocale} />
      </div>

      <div hidden={resonanceTab !== "plaza"}>
      <PlazaDirectory capsules={publicCapsules} activeCapsuleId={visitorMatch?.capsule.id ?? null} busy={visitorBusy}
        onOpenCapsule={openDirectoryCapsule} locale={skillLocale} />
      </div>

      <div hidden={resonanceTab !== "encounters"}>
      <ResonanceNetwork resonanceMatches={resonanceMatches} resonanceStrategy={resonanceStrategy} visitorBusy={visitorBusy}
        visitorMatch={visitorMatch} personaSession={personaSession} personaMessages={personaMessages} personaDraft={personaDraft}
        personaQuota={personaQuota} letterTitle={letterTitle} letterBody={letterBody} sentLetter={sentLetter}
        onChooseStrategy={strategy => void chooseResonanceStrategy(strategy)} onChooseMatch={chooseVisitorMatch}
        onStartPersonaConversation={() => void startPersonaConversation()} onPersonaDraftChange={setPersonaDraft}
        onSendPersonaTurn={() => void sendPersonaTurn()} onLetterTitleChange={setLetterTitle} onLetterBodyChange={setLetterBody}
        onSendLetter={() => void sendLetterToMatch()} onReportSession={() => void reportPersonaSession()}
        onBlockSession={() => void blockPersonaSession()} personaTurnError={personaTurnError}
        personaVoiceAudio={personaVoiceAudio} personaVoiceBusy={personaVoiceBusy} personaVoiceError={personaVoiceError}
        onPlayPersonaVoice={() => void playPersonaVoice()}
        landed={visitorMatch ? landedCapsuleIds.has(visitorMatch.capsule.id) : false}
        landedBusy={visitorMatch?.capsule.id === landedBusyId}
        onMarkLanded={() => void markCurrentCapsuleLanded()} locale={skillLocale} />
      </div>
      </div>
      </ErrorBoundary>

      <ErrorBoundary variant="space" locale={skillLocale}>
      <div className="product-space" hidden={productSpace !== "letters"}>
      <ConnectionSubNav active={connectionTab} onNavigate={navigateConnectionTab} locale={skillLocale} />

      <div hidden={connectionTab !== "people"}>
        <PeopleDiscovery people={connectionsAndLetters.people} isBusy={connectionsAndLetters.isPersonBusy} onRequest={userId => void connectionsAndLetters.requestPersonConnection(userId)} locale={skillLocale} />
      </div>

      <div hidden={connectionTab !== "relations"}>
        <RelationsView relations={connectionsAndLetters.relations} selected={connectionsAndLetters.selectedRelation} timeline={connectionsAndLetters.relationTimeline} health={connectionsAndLetters.relationHealth} busy={connectionsAndLetters.relationBusy} onSelect={label => void connectionsAndLetters.openRelation(label)} locale={skillLocale} />
      </div>

      <div hidden={connectionTab !== "groups"}>
        <SocialGroupsView groups={connectionsAndLetters.groups} invites={connectionsAndLetters.groupInvites} friends={connectionsAndLetters.friends}
        selectedGroupId={connectionsAndLetters.selectedGroupId} members={connectionsAndLetters.groupMembers} membersStatus={connectionsAndLetters.groupMembersStatus}
        messages={connectionsAndLetters.groupMessages} messagesStatus={connectionsAndLetters.groupMessagesStatus}
        createBusy={connectionsAndLetters.groupCreateBusy} isInviteBusy={connectionsAndLetters.isGroupInviteBusy}
        isInviteDecisionBusy={connectionsAndLetters.isGroupInviteDecisionBusy} isLeaveBusy={connectionsAndLetters.isGroupLeaveBusy}
        isMessageBusy={connectionsAndLetters.isGroupMessageBusy}
        currentUserId={userProfile?.id ?? null}
        onSelectGroup={id => void connectionsAndLetters.openGroup(id)} onCreateGroup={name => void connectionsAndLetters.createGroup(name)}
        onInvite={(groupId, userId) => void connectionsAndLetters.inviteToGroup(groupId, userId)}
        onRespondInvite={(memberId, decision) => void connectionsAndLetters.respondToGroupInvite(memberId, decision)}
        onLeaveGroup={id => void connectionsAndLetters.leaveGroup(id)}
        onSendMessage={(groupId, messageBody) => connectionsAndLetters.sendGroupMessage(groupId, messageBody)}
        locale={skillLocale} />
      </div>

      <div hidden={connectionTab !== "letters"}>
      <LettersInbox letterInbox={connectionsAndLetters.letterInbox} letterOutbox={connectionsAndLetters.letterOutbox} threads={connectionsAndLetters.letterThreads} threadLetters={connectionsAndLetters.threadLetters} threadLettersStatus={connectionsAndLetters.threadLettersStatus} selectedThreadId={connectionsAndLetters.selectedThreadId}
        isDraftBusy={connectionsAndLetters.isDraftBusy} replyBusyId={connectionsAndLetters.replyBusyId}
        isLetterActionBusy={connectionsAndLetters.isLetterActionBusy} isConnectionDecisionBusy={connectionsAndLetters.isConnectionDecisionBusy}
        isConnectionLeaveBusy={connectionsAndLetters.isConnectionLeaveBusy} isLetterConnectionBusy={connectionsAndLetters.isLetterConnectionBusy}
        onSendDraft={id => void connectionsAndLetters.sendDraft(id)} onOpenThread={id => { void connectionsAndLetters.openThread(id); navigate(letterThreadPath(id)); }} replyDrafts={connectionsAndLetters.replyDrafts} connectionRequests={connectionsAndLetters.connectionRequests} friends={connectionsAndLetters.friends}
        onReplyDraftChange={connectionsAndLetters.updateReplyDraft}
        onReply={letter => void connectionsAndLetters.replyWithLetter(letter)} onActOnLetter={(letter, action) => void connectionsAndLetters.actOnLetter(letter, action)}
        onReportLetter={letter => void connectionsAndLetters.reportLetter(letter)} onRequestConnection={letter => void connectionsAndLetters.requestConnection(letter)}
        onDecideConnection={(id, decision) => void connectionsAndLetters.decideConnection(id, decision)} onLeaveConnection={id => void connectionsAndLetters.leaveConnection(id)} locale={skillLocale}
        letterVoiceLetterId={connectionsAndLetters.letterVoiceLetterId} letterVoiceAudio={connectionsAndLetters.letterVoiceAudio} letterVoiceError={connectionsAndLetters.letterVoiceError}
        isLetterVoiceBusy={connectionsAndLetters.isLetterVoiceBusy} onPlayLetterVoice={letter => void connectionsAndLetters.playLetterVoice(letter)}
        refreshBusy={connectionsAndLetters.lettersRefreshing} onRefresh={() => void connectionsAndLetters.refreshLetters()}
        directLetterBusy={connectionsAndLetters.directLetterBusy}
        onSendDirectLetter={(receiverUserId, title, body, delivery) => connectionsAndLetters.sendDirectLetter(receiverUserId, title, body, delivery)}
        liveChatInvites={connectionsAndLetters.liveChatInvites} liveChatSessions={connectionsAndLetters.liveChatSessions}
        selectedLiveChatSessionId={connectionsAndLetters.selectedLiveChatSessionId}
        liveChatMessages={connectionsAndLetters.liveChatMessages} liveChatStatus={connectionsAndLetters.liveChatStatus}
        currentUserId={userProfile?.id ?? null}
        isLiveChatInviteBusy={connectionsAndLetters.isLiveChatInviteBusy}
        isLiveChatDecisionBusy={connectionsAndLetters.isLiveChatDecisionBusy}
        isLiveChatMessageBusy={connectionsAndLetters.isLiveChatMessageBusy}
        isLiveChatEndBusy={connectionsAndLetters.isLiveChatEndBusy}
        onInviteLiveChat={(userId, duration) => void connectionsAndLetters.inviteLiveChat(userId, duration)}
        onRespondLiveChatInvite={(inviteId, decision) => void connectionsAndLetters.respondLiveChatInvite(inviteId, decision)}
        onSelectLiveChatSession={sessionId => void connectionsAndLetters.selectLiveChatSession(sessionId)}
        onSendLiveChatMessage={(sessionId, body) => connectionsAndLetters.sendLiveChatMessage(sessionId, body)}
        onEndLiveChatSession={sessionId => void connectionsAndLetters.endLiveChatSession(sessionId)}
        onComposeNew={() => navigate("/resonance/encounters")} />
      </div>
      </div>
      </ErrorBoundary>

      <ErrorBoundary variant="space" locale={skillLocale}>
      <div className="product-space" hidden={productSpace !== "me"}>
        <MeSubNav active={meTab} onNavigate={tab => navigate(meTabPath(tab))} locale={skillLocale} />
        <div hidden={meTab !== "overview"}>
        <MeSpace native={mobileState.native} connected={mobileState.connected} wakeIntentCount={auroraSession.wakeIntents.length}
          activeClaimCount={claims.filter(claim => claim.status === "ACTIVE").length}
          publicCapsuleCount={capsules.filter(capsule => capsule.visibilityStatus === "PUBLIC").length}
          friendCount={connectionsAndLetters.friends.length} onNavigate={navigateSpace} onRequestPush={() => void requestMobilePush()}
          onRequestMicrophone={() => void requestMobileMicrophone()} onLogout={() => void logout()}
          onOpenSafetyHarbor={() => navigate("/safety-harbor")} locale={skillLocale} />
        </div>
        <div hidden={meTab !== "profile"}>
        <PortraitView dimensions={portrait} history={portraitHistory} calibrated={portraitCalibrated} busyDim={portraitBusy}
          onLoadHistory={dim => void loadPortraitHistory(dim)} onCalibrate={(dim, oldValue, newValue) => void submitPortraitCalibration(dim, oldValue, newValue)} locale={skillLocale} />
        </div>
        <div hidden={meTab !== "account"}>
        <AccountSettings busy={accountBusy} message={accountMessage} onChangePassword={(oldPassword, newPassword) => changeAccountPassword(oldPassword, newPassword)}
          onExportData={() => void exportAccountData()} onDeleteAccount={password => deleteAccount(password)}
          profile={userProfile} profileBusy={profileBusy} onSaveProfile={patch => void saveProfile(patch)}
          ttsPreferences={ttsPreferences} ttsBusy={ttsBusy}
          onUpdateTtsPreferences={patch => updateTtsPreferences(patch)} onPreviewVoice={voiceId => previewTtsVoice(voiceId)}
          locale={skillLocale} />
        </div>
        <div hidden={meTab !== "appearance"} className="me-appearance-panel">
        <AppearanceSettings locale={skillLocale} />
        <LocaleToggle locale={skillLocale} onChange={changeLocale} />
        </div>
        <div hidden={meTab !== "data"}>
        <DataRightsPanel receipts={dataRightsReceipts} loading={dataRightsLoading} loaded={dataRightsLoaded}
          onLoad={() => void loadDataRightsReceipts()} locale={skillLocale} />
        </div>
      </div>
      </ErrorBoundary>
      {statusVisible ? (
        <div className="state global-state visible" role="status"
          aria-live="polite" aria-atomic="true">
          <i className={auroraSession.activeTurnId ? "pulse" : ""} />
          <span>{status}</span>
          <button type="button" onClick={() => setStatusVisible(false)}
            aria-label={skillLocale === "en-SG" ? "Dismiss status" : "关闭状态提示"}>×</button>
        </div>
      ) : null}
    </main>
  );
}
