package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.context.AuroraConversationContextPolicy;
import com.innercosmos.ai.action.AuroraNaturalActionService;
import com.innercosmos.ai.goodbye.GoodbyeOrchestrator;
import com.innercosmos.ai.goodbye.GoodbyeTriggerDetector;
import com.innercosmos.ai.mode.ModeRegistry;
import com.innercosmos.ai.mode.ModeStrategy;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.prompt.PromptBuilder;
import com.innercosmos.ai.router.ResolvedModel;
import com.innercosmos.ai.runtime.AiFailureContract;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.ai.tts.TtsClient;
import com.innercosmos.ai.tts.TtsVoicePresets;
import com.innercosmos.ai.portrait.PortraitReflectionService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.AuroraMemoryContextService;
import com.innercosmos.service.AuroraSelfContinuityService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.RhythmGuardService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.streaming.AuroraLiveEvent;
import com.innercosmos.streaming.AuroraLiveEventStore;
import com.innercosmos.streaming.AuroraStreamStageStore;
import com.innercosmos.streaming.InMemoryAuroraLiveEventStore;
import com.innercosmos.streaming.InMemoryAuroraStreamStageStore;
import com.innercosmos.util.PromptLeakageGuard;
import com.innercosmos.vo.AuroraMemoryContextVO;
import com.innercosmos.vo.AuroraForegroundVO;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.SafetyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AuroraAgentServiceImpl implements AuroraAgentService {
    private static final Logger log = LoggerFactory.getLogger(AuroraAgentServiceImpl.class);
    /** Completed provider responses are emitted immediately in bounded SSE chunks. */
    private static final int COMPLETED_RESPONSE_CHUNK_CHARS = 12;
    private static final int MODEL_LONG_TERM_MEMORY_LIMIT = 4;
    private static final Duration DELIVERY_LEASE_TTL = Duration.ofSeconds(15);
    private static final Duration GENERATION_LEASE_TTL = Duration.ofMinutes(2);
    private static final String GENERATION_CONTEXT_VERSION = "aurora-context.v1";
    // M2 (independent code review): used to build the inner_voice SSE payload via writeValueAsString
    // instead of hand-rolled string concatenation, so an odd control char (U+0000-U+001F) in LLM
    // output can never produce invalid JSON that the frontend silently drops. Jackson's ObjectMapper
    // is thread-safe after construction; a plain shared instance is the same pattern GlmLlmClient uses.
    private static final com.fasterxml.jackson.databind.ObjectMapper INNER_VOICE_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final List<String> MODES = List.of(
            "DAILY_TALK", "THOUGHT_CLARIFY", "SLEEP_REVIEW", "SOCRATIC", "ACTION_SPLIT",
            "RELATION_REVIEW", "CAPSULE_SHAPING"
    );

    private final StructuredAiService structuredAiService;
    private final DialogService dialogService;
    private final SafetyService safetyService;
    private final MemoryService memoryService;
    private final RhythmGuardService rhythmGuardService;
    private final AuroraMemoryContextService memoryContextService;
    private final UserProfileMapper userProfileMapper;
    private final DialogSessionMapper sessionMapper;
    private final LlmConfig llmConfig;
    private final Executor aiExecutor;
    private Executor streamExecutor;
    private final AgentContextAssembler agentContextAssembler;
    private final SessionModelRouter modelRouter;
    private final PortraitReflectionService portraitReflection;
    private final GoodbyeTriggerDetector goodbyeDetector;
    private final GoodbyeOrchestrator goodbyeOrchestrator;
    private final ModeRegistry modeRegistry;
    private final AuroraConstitutionService constitutionService;
    private final UserPortraitService userPortraitService;
    private final AgentUserRelationshipService relationshipService;
    @Autowired(required = false)
    private AuroraSelfContinuityService continuityService;
    @Autowired(required = false)
    private com.innercosmos.service.UserCorrectionService userCorrectionService;
    @Autowired(required = false)
    private com.innercosmos.mapper.UnderstandingClaimMapper understandingClaimMapper;
    @Autowired(required = false)
    private com.innercosmos.service.PromptVersionService promptVersionService; // M-052
    @Autowired(required = false)
    private com.innercosmos.service.EmotionBaselineService emotionBaselineService;
    @Autowired(required = false)
    private AuroraConversationContextPolicy conversationContextPolicy;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;
    /** Confirmation-gated natural-language bridge to real memories, reminders and settings. */
    @Autowired(required = false)
    private AuroraNaturalActionService naturalActionService;
    /**
     * Optional only for constructor-level unit tests. The registered account owns the user's
     * chosen nickname, while {@link UserProfile} owns the QuickHello calibration; both must reach
     * the same first-turn context so two fresh users do not receive an interchangeable opening.
     */
    @Autowired(required = false)
    private UserMapper userMapper;
    /** Optional only for constructor-level legacy unit tests; Spring always wires it. */
    @Autowired(required = false)
    private ConversationChoreographyService choreographyService;
    @Autowired(required = false)
    private AuroraDualKernelRuntime dualKernelRuntime;
    /** W1: Aurora's inner-voice (心声) TTS synthesis; null-safe -- {@code DisabledTtsClient} when not configured. */
    @Autowired(required = false)
    private TtsClient ttsClient;
    /** A6 privacy-safe AI metrics; Spring always wires it, null only in constructor-level unit tests. */
    @Autowired(required = false)
    private com.innercosmos.ai.observability.AiTurnMetrics aiTurnMetrics;
    /** A6 privacy-safe AI span (Observation → OTel span with a tracer); Spring always wires it. */
    @Autowired(required = false)
    private com.innercosmos.ai.observability.AiTurnObservation aiTurnObservation;
    private final Map<Long, Integer> turnCounter = new ConcurrentHashMap<>();
    private final Map<Long, Integer> goodbyeConfirmCount = new ConcurrentHashMap<>();
    private AuroraStreamStageStore streamStageStore =
            new InMemoryAuroraStreamStageStore(Duration.ofMinutes(1), 1024);
    private AuroraLiveEventStore liveEventStore = new InMemoryAuroraLiveEventStore(1024);

    @Autowired
    void setStreamStageStore(AuroraStreamStageStore streamStageStore) {
        this.streamStageStore = streamStageStore;
    }

    @Autowired
    void setLiveEventStore(AuroraLiveEventStore liveEventStore) {
        this.liveEventStore = liveEventStore;
    }

    @Autowired
    void setStreamExecutor(@Qualifier("sseExecutor") Executor streamExecutor) {
        this.streamExecutor = streamExecutor;
    }

    public AuroraAgentServiceImpl(StructuredAiService structuredAiService,
                                  DialogService dialogService,
                                  SafetyService safetyService,
                                  MemoryService memoryService,
                                  RhythmGuardService rhythmGuardService,
                                  AuroraMemoryContextService memoryContextService,
                                  UserProfileMapper userProfileMapper,
                                  DialogSessionMapper sessionMapper,
                                  LlmConfig llmConfig,
                                  Executor aiExecutor,
                                  AgentContextAssembler agentContextAssembler,
                                  SessionModelRouter modelRouter,
                                  PortraitReflectionService portraitReflection,
                                  GoodbyeTriggerDetector goodbyeDetector,
                                  GoodbyeOrchestrator goodbyeOrchestrator,
                                  ModeRegistry modeRegistry,
                                  AuroraConstitutionService constitutionService,
                                  UserPortraitService userPortraitService,
                                  AgentUserRelationshipService relationshipService) {
        this.structuredAiService = structuredAiService;
        this.dialogService = dialogService;
        this.safetyService = safetyService;
        this.memoryService = memoryService;
        this.rhythmGuardService = rhythmGuardService;
        this.memoryContextService = memoryContextService;
        this.userProfileMapper = userProfileMapper;
        this.sessionMapper = sessionMapper;
        this.llmConfig = llmConfig;
        this.aiExecutor = aiExecutor;
        this.streamExecutor = aiExecutor;
        this.agentContextAssembler = agentContextAssembler;
        this.modelRouter = modelRouter;
        this.portraitReflection = portraitReflection;
        this.goodbyeDetector = goodbyeDetector;
        this.goodbyeOrchestrator = goodbyeOrchestrator;
        this.modeRegistry = modeRegistry;
        this.constitutionService = constitutionService;
        this.userPortraitService = userPortraitService;
        this.relationshipService = relationshipService;
    }

    @Override
    public String reply(Long userId, ChatRequest request) {
        AuroraReplyVO rich = replyRich(userId, request);
        return String.join("\n\n", rich.messages == null ? List.of() : rich.messages);
    }

    @Override
    public AuroraReplyVO replyRich(Long userId, ChatRequest request) {
        cancelPreviousTurn(userId, request.sessionId);
        // SAFETY FIRST (VS-003 §1): synchronous safety gate before any model call.
        // recheckSync for distress-bearing messages also completes here, synchronously.
        SafetyResult safety = safetyService.check(
                request.message, userId, request.sessionId, request.clientMessageId,
                request.locale, request.region);
        DialogMessage userMessage = dialogService.saveUserMessage(userId, request);
        Long turnId = beginChoreography(userId, request.sessionId, userMessage);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            AuroraReplyVO blocked = blockedReply(
                    userId, request, safety, userMessage == null ? null : userMessage.id, turnId);
            publishTurnPersisted(userId, request.sessionId, userMessage);
            return blocked;
        }
        GenerationAuthority generationAuthority =
                stageAndClaimGeneration(userId, turnId, request, userMessage);
        AuroraReplyVO reply = produceReply(
                userId, request, safety, userMessage == null ? null : userMessage.id, turnId, true,
                generationAuthority);
        publishTurnPersisted(userId, request.sessionId, userMessage);
        return reply;
    }

    @Override
    public AuroraReplyVO resumeExistingTurn(
            Long userId,
            ConversationChoreographyService.GenerationRequestSnapshot snapshot,
            String generationLeaseOwner,
            long generationFencingToken) {
        if (snapshot == null || snapshot.turnId() == null
                || snapshot.sessionId() == null || snapshot.userMessageId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Recoverable Aurora generation request is missing");
        }
        DialogMessage referencedUserMessage = dialogService.messages(snapshot.sessionId()).stream()
                .filter(message -> snapshot.userMessageId().equals(message.id))
                .filter(message -> userId.equals(message.userId))
                .filter(message -> "USER".equals(message.speaker))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Referenced Aurora user message is unavailable"));
        ChatRequest request = new ChatRequest();
        request.sessionId = snapshot.sessionId();
        request.message = referencedUserMessage.textContent;
        request.inputType = referencedUserMessage.inputType;
        request.audioDurationSec = referencedUserMessage.audioDurationSec;
        request.speechRate = referencedUserMessage.speechRate;
        request.pauseCount = referencedUserMessage.pauseCount;
        request.longPauseCount = referencedUserMessage.longPauseCount;
        request.mode = normalizeMode(snapshot.mode());
        request.locale = snapshot.locale();
        request.region = snapshot.region();
        request.timezone = snapshot.timezone();
        request.foregroundAcknowledgementSent = snapshot.foregroundAcknowledgementSent();
        GenerationAuthority authority =
                new GenerationAuthority(generationLeaseOwner, generationFencingToken);
        // The synchronous safety gate ran before the referenced message and turn were persisted.
        // Recovery never creates another user message or another turn; it regenerates only because
        // no assistant plan or visible assistant content exists.
        return produceReply(
                userId, request, new SafetyResult(), referencedUserMessage.id,
                snapshot.turnId(), false, authority);
    }

    @Override
    public AuroraForegroundVO foregroundAcknowledgement(Long userId, ChatRequest request) {
        AuroraForegroundVO vo = new AuroraForegroundVO();
        SafetyResult safety = safetyService.check(
                request == null ? null : request.message,
                userId,
                request == null ? null : request.sessionId,
                request == null ? null : request.clientMessageId,
                request == null ? null : request.locale,
                request == null ? null : request.region);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            vo.safetyBlocked = true;
            return vo;
        }
        if (naturalActionService != null
                && naturalActionService.shouldSuppressForeground(userId, request.sessionId, request.message)) {
            vo.text = "";
            vo.source = "natural-action-authoritative-turn";
            vo.latencyMs = 0L;
            return vo;
        }
        ForegroundAcknowledgement acknowledgement = fastForegroundAcknowledgement(userId, request);
        vo.text = acknowledgement.text();
        vo.source = acknowledgement.source();
        vo.latencyMs = acknowledgement.latencyMs();
        return vo;
    }

    /**
     * Shared reply-production path used by both the POST (replyRich) and the SSE
     * (stream) entrypoints. The synchronous safety gate has ALREADY run by the time
     * this is called — do not re-run it here. Saves Aurora's messages and runs the
     * portrait/goodbye post-hooks exactly as before.
     */
    private AuroraReplyVO produceReply(Long userId, ChatRequest request, SafetyResult safety,
                                       Long userMessageId, Long turnId, boolean persistImmediately,
                                       GenerationAuthority generationAuthority) {
        long turnStartNanos = System.nanoTime();
        boolean fallbackUsed = false;
        // M7: Hard boundary protection — right to refuse identity violation
        String boundaryRefusal = checkHardBoundaries(request.message, userId);
        if (boundaryRefusal != null) {
            AuroraReplyVO vo = new AuroraReplyVO();
            vo.messages = List.of(boundaryRefusal);
            vo.replyTone = "温柔、坚定、真实";
            vo.detectedTheme = "边界守护";
            vo.nextQuestion = "";
            vo.smallStep = "";
            vo.featureSuggestion = "";
            vo.featureTarget = "";
            vo.suggestSettle = false;
            vo.memoryReferenced = false;
            vo.referencedMemoryIds = List.of();
            vo.memoryContext = null;
            vo.riskFlags = List.of("IDENTITY_BOUNDARY_TRIGGERED");
            vo.agentLoop = Map.of("speakCount", 1, "continueReason", "boundary-refusal", "mode", normalizeMode(request.mode), "modeLabel", modeLabel(normalizeMode(request.mode)));
            vo.aiState = aiState(null);
            vo.turnId = turnId;
            commitPlanAuthorized(userId, turnId, vo, generationAuthority);
            if (persistImmediately) {
                if (turnId != null && choreographyService != null) {
                    choreographyService.deliverBubble(userId, turnId, 1,
                            () -> dialogService.saveAuroraMessage(userId, request.sessionId, boundaryRefusal));
                    choreographyService.completeTurn(userId, turnId);
                } else {
                    DialogMessage saved = dialogService.saveAuroraMessage(userId, request.sessionId, boundaryRefusal);
                    recordChoreography(userId, request.sessionId, userMessageId, vo,
                            saved == null ? List.of() : List.of(saved));
                }
            }
            recordTurnMetrics("boundary-refusal", vo, null, normalizeMode(request.mode), false, turnStartNanos);
            return vo;
        }

        // Natural-language side effects are a deterministic, owner-confirmed branch. The parser
        // can only propose an allow-listed action; a real write happens solely when the immediately
        // following owner turn explicitly says Confirm/确认. Ordinary messages still reach the
        // real LLM path below unchanged.
        if (naturalActionService != null && turnId != null) {
            AuroraReplyVO actionReply = naturalActionService.intercept(
                    userId, request.sessionId, turnId, request.message);
            if (actionReply != null) {
                return completeNaturalActionTurn(userId, request, userMessageId, turnId,
                        persistImmediately, actionReply, turnStartNanos, generationAuthority);
            }
        }

        UserProfile profile = loadProfile(userId);
        String mode = normalizeMode(request.mode);
        boolean allowMemory = allowMemory(profile);
        AgentContext agentContext = agentContextAssembler.assemble(
                userId, request.sessionId, request.message, allowMemory,
                request.latitude, request.longitude,
                request.timezone, request.locale, request.localTimeLabel);
        AgentContext modelAgentContext = compactAgentContext(agentContext, request.message);
        List<String> gravityMemories = allowMemory ? List.copyOf(modelAgentContext.longTermMemories) : List.of();
        AuroraMemoryContextVO memoryContext = allowMemory
                ? alignMemoryContext(memoryContextService.buildContext(userId, request.sessionId, request.message, 8, 0), agentContext)
                : null;
        ResolvedModel resolved = modelRouter.resolve(userId, request.sessionId);
        ModeStrategy modeStrategy = modeRegistry.get(mode);

        // Identity and safety belong in the provider system role. Dynamic user data travels once
        // in the structured context below instead of being duplicated as a prose prompt.
        List<UserPortrait> portrait = safePortrait(userId);
        AgentUserRelationship relationship = safeRelationship(userId);
        String stateSignal = currentStateSignal(request.message);
        com.innercosmos.ai.semantic.EmotionBaseline baseline = safeBaseline(userId);

        String userNickname = loadUserNickname(userId);
        Map<String, Object> userCalibration = userCalibration(profile, userNickname);
        String systemPrompt = new PromptBuilder().withPromptVersionService(promptVersionService)
                .withSystemBoundary()
                .buildSystemPrompt();

        Map<String, Object> turnContext = new LinkedHashMap<>();
        turnContext.put("auroraSystemPrompt", systemPrompt);
        turnContext.put("userMessage", request.message == null ? "" : request.message);
        turnContext.put("mode", mode);
        turnContext.put("sessionId", request.sessionId == null ? 0L : request.sessionId);
        // M-012: thread the active mode's sampling temperature so it actually reaches the
        // model (StructuredAiService reads "modeTemperature" → LlmRequest.temperature →
        // provider body). Null-safe: when the mode has no strategy, the key is absent and
        // the provider client falls back to its existing hardcoded default.
        if (modeStrategy != null) {
            turnContext.put("modeTemperature", modeStrategy.temperature());
        }
        turnContext.put("modeGuide", modeGuide(mode));
        boolean hasIntegratedContinuity = modelAgentContext.threeModelBlock != null
                && !modelAgentContext.threeModelBlock.isBlank();
        turnContext.put("userPortrait", hasIntegratedContinuity ? "" : portraitBriefForContext(portrait));
        turnContext.put("relationship", hasIntegratedContinuity || relationship == null
                ? "" : relationship.toPromptString());
        turnContext.put("currentStateSignal", stateSignal);
        // User-owned values remain data inside the structured request as well as a compact,
        // sanitised profile summary in the system prompt. This makes the calibration available
        // to both the single-pass and dual-kernel runtimes without turning any value into a new
        // instruction.
        turnContext.put("userCalibration", userCalibration);
        turnContext.put("userCorrections", safeCorrections(userId));
        turnContext.put("confirmedUnderstandingClaims", safeConfirmedClaims(userId));
        turnContext.put("portraitCalibrations", safePortraitCalibrations(userId));
        turnContext.put("emotionBaseline", Map.of(
                "label", baseline.baselineLabel == null ? "" : baseline.baselineLabel,
                "stabilityScore", baseline.stabilityScore));
        turnContext.put("memoryRecallAllowed", allowMemory);
        // A single bounded history/memory envelope reaches the model; userMessage stays authoritative.
        turnContext.put("unifiedAgentContext", modelAgentContext);
        turnContext.put("realWeatherLabel", agentContext.realWeatherLabel);
        turnContext.put("cityLabel", agentContext.cityLabel);
        turnContext.put("preferredProvider", resolved.provider());
        turnContext.put("providerPolicy", providerPolicy(resolved));
        turnContext.put("foregroundAcknowledgementAlreadySent", request.foregroundAcknowledgementSent);
        if (choreographyService != null && request.sessionId != null) {
            String interruptionContext = choreographyService.latestInterruptionContext(
                    userId, request.sessionId, request.message);
            if (interruptionContext != null && !interruptionContext.isBlank()) {
                turnContext.put("interruptionContext", interruptionContext);
            }
        }
        turnContext.put("agentLoopPolicy", Boolean.FALSE.equals(agentContext.multiMessageAllowed)
                ? "用户关闭了多条消息，本轮只能输出 1 条 segments。"
                : "你可以按语义选择 1-6 条消息。用户输入很长、包含多个问题或要求深入讨论时，"
                + "应使用更完整的长气泡或 3-6 个独立节拍；不要把长输入压成一句套话和一个问题。"
                + "若某个后续想法不值得说，写 [[SILENCE]]，系统不会展示。不要固定数量。");
        AuroraConversationContextPolicy policy = conversationContextPolicy == null
                ? new AuroraConversationContextPolicy(llmConfig, new TokenEstimationServiceImpl())
                : conversationContextPolicy;
        AuroraConversationContextPolicy.Selection sessionContext = policy.select(
                agentContext.recentMessages, request.message, systemPrompt, turnContext,
                resolved.provider(), resolved.model());
        turnContext.put("userMessage", sessionContext.modelUserMessage());
        // Keep the provider-cache prefix stable: immutable system role first, then the
        // chronological session history. Per-turn state and the latest user message follow it.
        Map<String, Object> cacheFriendlyContext = new LinkedHashMap<>();
        cacheFriendlyContext.put("auroraSystemPrompt", systemPrompt);
        cacheFriendlyContext.put("conversationHistory", sessionContext.messages());
        turnContext.forEach((key, value) -> {
            if (!"auroraSystemPrompt".equals(key)) cacheFriendlyContext.put(key, value);
        });
        cacheFriendlyContext.put("conversationContextBudget", sessionContext.metadata());
        turnContext.clear();
        turnContext.putAll(cacheFriendlyContext);

        AuroraReplyVO vo;
        Map<String, Object> runtimeMeta = new LinkedHashMap<>();
        io.micrometer.observation.Observation providerObservation = aiTurnObservation == null
                ? null : aiTurnObservation.startProvider(resolved.provider(), mode);
        io.micrometer.observation.Observation.Scope providerScope = providerObservation == null
                ? null : providerObservation.openScope();
        AuroraDualKernelRuntime.InnerVoiceRequest innerVoiceRequest = null;
        java.util.concurrent.CompletableFuture<AuroraDualKernelRuntime.InnerVoiceRequest> deferredInnerVoiceRequest = null;
        java.util.concurrent.CompletableFuture<AuroraDualKernelRuntime.PlannerRunEvidence> backgroundPlannerEvidence = null;
        try {
            StructuredAiResults.AuroraResult ai;
            if (dualKernelRuntime != null && dualKernelRuntime.shouldUseDualKernelForTurn(turnContext)) {
                // Capture only the inputs needed for a post-turn inner-voice call. Text is the
                // core experience; TTS is an optional layer added later in stream(). Composition
                // itself still runs after turn.completed, off the conversational critical path.
                boolean composeInnerVoice = !persistImmediately
                        && innerVoiceEnabledFor(profile);
                var generation = dualKernelRuntime.generate(userId, mode, turnContext, resolved.client(),
                    () -> fallbackAuroraResult(request.message, mode, gravityMemories, memoryContext, allowMemory, stateSignal),
                    composeInnerVoice,
                    plan -> stageDeliberationSnapshot(userId, turnId, plan, generationAuthority));
                ai = generation.result();
                runtimeMeta.put("runtime", generation.runtime());
                runtimeMeta.put("relationshipMove", generation.relationshipMove());
                runtimeMeta.put("criticRepaired", generation.repaired());
                runtimeMeta.put("criticIssues", generation.criticIssues());
                runtimeMeta.put("stageLatenciesMs", generation.stageLatenciesMs());
                runtimeMeta.put("plannerFallbackUsed", generation.plannerFallbackUsed());
                runtimeMeta.put("speakerFallbackUsed", generation.speakerFallbackUsed());
                runtimeMeta.put("criticFallbackUsed", generation.criticFallbackUsed());
                runtimeMeta.put("backgroundPlannerScheduled", generation.backgroundPlannerScheduled());
                runtimeMeta.put("guidanceSource", generation.guidanceSource());
                runtimeMeta.put("backgroundPlannerStatus", generation.backgroundPlannerStatus());
                innerVoiceRequest = generation.innerVoiceRequest();
                deferredInnerVoiceRequest = generation.deferredInnerVoiceRequest();
                backgroundPlannerEvidence = generation.backgroundPlannerEvidence();
                if (backgroundPlannerEvidence != null) {
                    backgroundPlannerEvidence.whenComplete((evidence, failure) -> {
                        if (failure != null) {
                            log.warn("Background planner evidence future failed for user {} mode {}: {}",
                                    userId, mode, failure.getMessage());
                        } else if (evidence != null) {
                            log.info("Background planner terminal status user={} mode={} revision={} status={} latencyMs={} detail={}",
                                    userId, mode, evidence.revision(), evidence.status(),
                                    evidence.latencyMs(), evidence.detail());
                        }
                    });
                }
            } else {
                ai = callWithRetry(userId, mode, turnContext, resolved, request, gravityMemories,
                    memoryContext, allowMemory, stateSignal);
                runtimeMeta.put("runtime", "single-pass.v1");
            }
            vo = toReply(profile, ai, request, mode, memoryContext, gravityMemories, allowMemory);
            vo = sanitizeLlmOutput(vo, userId);
            vo.innerVoiceRequest = innerVoiceRequest;
            vo.deferredInnerVoiceRequest = deferredInnerVoiceRequest;
            vo.backgroundPlannerEvidence = backgroundPlannerEvidence;
            if (Boolean.FALSE.equals(agentContext.multiMessageAllowed) && vo.messages.size() > 1) {
                vo.messages = List.of(vo.messages.get(0));
                vo.agentLoop = Map.of(
                        "speakCount", 1,
                        "continueReason", "single-message-mode",
                        "mode", mode,
                        "modeLabel", modeLabel(mode)
                );
            }
        } catch (Exception e) {
            if (providerObservation != null) providerObservation.error(e);
            log.error("Aurora agent call failed after retries: {}", e.getMessage(), e);
            vo = differentiatedFallback(e, request.message, mode, stateSignal);
            fallbackUsed = true;
        } finally {
            if (providerScope != null) providerScope.close();
            if (providerObservation != null) providerObservation.stop();
        }
        // F7: preserve two layers of provenance. The default UI receives a bounded response-source
        // label (live model / demo mode / basic response), while the expandable team diagnostic
        // view can inspect the exact provider, model and fallback reason without exposing hidden
        // reasoning. This must be computed after generation so speaker/provider fallback is known.
        String fallbackReason = runtimeFallbackReason(resolved, runtimeMeta, fallbackUsed);
        String responseSource = responseSource(resolved, runtimeMeta, fallbackUsed);
        runtimeMeta.put("foregroundSource", safeDiagnosticValue(request.foregroundAcknowledgementSource));
        runtimeMeta.put("fallbackReason", fallbackReason);
        Map<String, Object> disclosedAiState = new LinkedHashMap<>(aiState(resolved));
        disclosedAiState.put("responseSource", responseSource);
        disclosedAiState.put("fallbackReason", fallbackReason);
        vo.aiState = Map.copyOf(disclosedAiState);
        vo.turnId = turnId;
        if (turnId != null && choreographyService != null) {
            TurnTimelineVO planned = commitPlanAuthorized(
                    userId, turnId, vo, generationAuthority);
            if (planned.activePlan == null) {
                vo.cancelled = true;
                vo.messages = List.of();
                return vo;
            }
            vo.planId = planned.activePlan.id;
        }
        if (!runtimeMeta.isEmpty()) {
            Map<String, Object> loop = new LinkedHashMap<>(vo.agentLoop == null ? Map.of() : vo.agentLoop);
            loop.putAll(runtimeMeta);
            vo.agentLoop = loop;
        }
        if (persistImmediately) {
            List<DialogMessage> persistedBubbles = new ArrayList<>();
            for (int i = 0; i < vo.messages.size(); i++) {
                if (turnId != null && choreographyService != null && choreographyService.isCancelled(userId, turnId)) break;
                if (turnId != null && choreographyService != null) {
                    int bubbleOrder = i + 1;
                    String bubbleText = vo.messages.get(i);
                    choreographyService.deliverBubble(userId, turnId, bubbleOrder,
                            () -> dialogService.saveAuroraMessage(userId, request.sessionId, bubbleText));
                } else {
                    persistedBubbles.add(dialogService.saveAuroraMessage(
                            userId, request.sessionId, vo.messages.get(i)));
                }
            }
            if (turnId != null && choreographyService != null) {
                choreographyService.completeTurn(userId, turnId);
            } else {
                recordChoreography(userId, request.sessionId, userMessageId, vo, persistedBubbles);
            }
        }
        // Portrait reflection hook: every 5 turns, analyze and update user portrait.
        // M-045: atomic compute — increment, threshold check, and reset in one op so concurrent
        // turns for the same user can't double-fire or skip the reflection.
        boolean[] shouldReflect = {false};
        turnCounter.compute(userId, (k, cur) -> {
            int c = (cur == null ? 0 : cur) + 1;
            if (c >= 5) { shouldReflect[0] = true; return 0; }
            return c;
        });
        if (shouldReflect[0]) {
            // M-011/Phase-5: run the reflection (an extra LLM call) ASYNC on aiExecutor so the
            // 1-in-5 POST reply is never blocked by it. The portrait updates a moment later, which
            // is fine for a mid-session refresh; an async failure must never break the reply path.
            final Long uid = userId;
            final Long sid = request.sessionId;
            aiExecutor.execute(() -> {
                try {
                    List<DialogMessage> recent = sid == null ? List.<DialogMessage>of()
                            : dialogService.messages(sid);
                    int start = Math.max(0, recent.size() - 20);
                    var portraitDeltas = portraitReflection.reflectOnTurn(uid, recent.subList(start, recent.size()));
                    if (portraitDeltas != null && portraitDeltas.deltas() != null
                            && !portraitDeltas.deltas().isEmpty()) {
                        userPortraitService.applyDeltas(uid, portraitDeltas.deltas());
                    }
                } catch (Exception ignore) {
                    // async reflection failure is non-fatal
                }
            });
        }

        // Goodbye trigger detection: check user message for goodbye intent
        afterMessage(userId, request.sessionId, request.message);

        recordTurnMetrics("chat", vo, resolved, mode, fallbackUsed, turnStartNanos);
        return vo;
    }

    private void stageDeliberationSnapshot(
            Long userId, Long turnId, StructuredAiResults.AuroraPlanResult plan,
            GenerationAuthority generationAuthority) {
        if (turnId == null || choreographyService == null || plan == null) return;
        try {
            String userSafeJson = INNER_VOICE_MAPPER.writeValueAsString(plan);
            if (generationAuthority == null) {
                choreographyService.stageDeliberation(userId, turnId, 0, userSafeJson);
            } else {
                int expectedRevision = choreographyService.timeline(userId, turnId).deliberations.stream()
                        .map(snapshot -> snapshot.planRevision == null ? 0 : snapshot.planRevision)
                        .max(Integer::compareTo)
                        .orElse(0);
                choreographyService.stageDeliberationFenced(
                        userId, turnId, expectedRevision, userSafeJson,
                        generationAuthority.owner(), generationAuthority.fencingToken(),
                        GENERATION_LEASE_TTL);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Aurora deliberation snapshot could not be serialized");
        }
    }

    private AuroraReplyVO completeNaturalActionTurn(Long userId, ChatRequest request,
                                                    Long userMessageId, Long turnId,
                                                    boolean persistImmediately, AuroraReplyVO vo,
                                                    long turnStartNanos,
                                                    GenerationAuthority generationAuthority) {
        vo.turnId = turnId;
        if (turnId != null && choreographyService != null) {
            TurnTimelineVO planned = commitPlanAuthorized(
                    userId, turnId, vo, generationAuthority);
            if (planned.activePlan == null) {
                vo.cancelled = true;
                vo.messages = List.of();
                return vo;
            }
            vo.planId = planned.activePlan.id;
        }
        if (persistImmediately) {
            List<DialogMessage> persistedBubbles = new ArrayList<>();
            for (int i = 0; i < vo.messages.size(); i++) {
                if (turnId != null && choreographyService != null) {
                    int order = i + 1;
                    String text = vo.messages.get(i);
                    choreographyService.deliverBubble(userId, turnId, order,
                            () -> dialogService.saveAuroraMessage(userId, request.sessionId, text));
                } else {
                    persistedBubbles.add(dialogService.saveAuroraMessage(
                            userId, request.sessionId, vo.messages.get(i)));
                }
            }
            if (turnId != null && choreographyService != null) {
                choreographyService.completeTurn(userId, turnId);
            } else {
                recordChoreography(userId, request.sessionId, userMessageId, vo, persistedBubbles);
            }
        }
        recordTurnMetrics("natural-action", vo, null, normalizeMode(request.mode), false, turnStartNanos);
        return vo;
    }

    /** A6: emit the privacy-safe per-turn counter/timer + span. No-op for whichever is not wired. */
    private void recordTurnMetrics(String route, AuroraReplyVO vo, ResolvedModel resolved, String mode,
                                   boolean fallbackUsed, long startNanos) {
        if (aiTurnMetrics == null && aiTurnObservation == null) return;
        String runtime = vo != null && vo.agentLoop != null && vo.agentLoop.get("runtime") instanceof String r
                ? r : "single-pass.v1";
        String provider = resolved == null || resolved.provider() == null
                ? llmConfig.activeProvider() : resolved.provider();
        boolean memoryReferenced = vo != null && Boolean.TRUE.equals(vo.memoryReferenced);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        if (aiTurnMetrics != null) {
            aiTurnMetrics.recordTurn(route, runtime, provider, mode, fallbackUsed, memoryReferenced, durationMs);
        }
        if (aiTurnObservation != null) {
            aiTurnObservation.record(route, runtime, provider, mode, fallbackUsed, memoryReferenced, durationMs);
        }
    }

    private void afterMessage(Long userId, Long sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return;
        var detection = goodbyeDetector.detect(userMessage);
        if (detection.trigger() == null) return;

        if (detection.needsConfirm()) {
            // For medium confidence, check if this is the second attempt (confirm intent)
            int confirmCount = goodbyeConfirmCount.merge(userId, 1, Integer::sum);
            if (confirmCount >= 2) {
                // User confirmed goodbye
                goodbyeOrchestrator.start(userId, sessionId, detection.trigger());
                goodbyeConfirmCount.put(userId, 0);
            }
            // First medium detection - Aurora will ask for confirmation
        } else {
            // High confidence - auto trigger goodbye
            goodbyeOrchestrator.start(userId, sessionId, detection.trigger());
        }
    }

    @Override
    public SseEmitter stream(Long userId, Long sessionId, String message, String mode) {
        return stream(userId, sessionId, message, mode, null);
    }

    /**
     * VS-003b — stream with an optional rich context (voice/weather/location/
     * timezone) staged by the frontend. When non-null, the SSE meta event carries
     * the same perception metadata the POST path returns, so the frontend can
     * render the agent-loop + memory-lens panels on parity with the POST path.
     */
    @Override
    public SseEmitter stream(Long userId, Long sessionId, String message, String mode, ChatRequest richContext) {
        cancelPreviousTurn(userId, sessionId);
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean clientConnected = new AtomicBoolean(true);
        emitter.onCompletion(() -> clientConnected.set(false));
        emitter.onTimeout(() -> {
            clientConnected.set(false);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            clientConnected.set(false);
            log.debug("Aurora live stream detached; durable turn continues: {}",
                    String.valueOf(throwable.getMessage()));
        });

        // VS-003 §1 — SAFETY FIRST, synchronously, before ANY chat token streams.
        // recheckSync for distress-bearing messages also completes here. Crisis must
        // never stream as free-form consolation (vision §8.5).
        SafetyResult safety;
        try {
            safety = safetyService.check(
                    message, userId, sessionId,
                    richContext == null ? null : richContext.clientMessageId,
                    richContext == null ? null : richContext.locale,
                    richContext == null ? null : richContext.region);
        } catch (Exception e) {
            log.error("Aurora stream safety check failed: {}", e.getMessage(), e);
            sendOnce(emitter, "error", "{\"message\":\"safety check failed\"}");
            completeQuietly(emitter);
            return emitter;
        }
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            // Emit ONE safety/resource event asynchronously (uniform SSE contract).
            // Do NOT stream chat — crisis must never arrive as free-form consolation.
            streamExecutor.execute(() -> {
                saveUserAndBlockedAurora(userId, sessionId, message, mode, safety);
                sendOnce(emitter, "safety", jsonSafety(safety));
                sendOnce(emitter, "done", "{\"message\":\"done\"}");
                completeQuietly(emitter);
            });
            return emitter;
        }
        if ("GENTLE_CHECK_IN".equals(safety.safetyState)) {
            // Non-blocking, low-interruption support offer. The normal Aurora response continues;
            // only HIGH_CONFIRMED takes over the turn and closes the ordinary stream.
            sendOnce(emitter, "safety", jsonSafety(safety));
        }

        // Non-blocked: save the user turn, then build + persist the full reply exactly
        // as the POST path does, and finally drip the (already-persisted) segments
        // server-side over real SSE transport — no client-side fake typewriter.
        streamExecutor.execute(() -> {
            Long turnId = null;
            try {
                ChatRequest request = new ChatRequest();
                request.sessionId = sessionId;
                request.message = message;
                request.mode = normalizeMode(mode);
                // VS-003b — fold the staged rich context into the request so the
                // voice metadata, weather, location and timezone reach the prompt
                // and the SSE meta event (parity with the POST path).
                if (richContext != null) {
                    request.inputType = richContext.inputType == null ? "TEXT" : richContext.inputType;
                    request.audioDurationSec = richContext.audioDurationSec;
                    request.speechRate = richContext.speechRate;
                    request.pauseCount = richContext.pauseCount;
                    request.longPauseCount = richContext.longPauseCount;
                    request.locale = richContext.locale;
                    request.region = richContext.region;
                    request.timezone = richContext.timezone;
                    request.localTimeLabel = richContext.localTimeLabel;
                    request.clientMessageId = richContext.clientMessageId;
                    request.locale = richContext.locale;
                    request.region = richContext.region;
                    request.weatherType = richContext.weatherType;
                    request.weatherDescription = richContext.weatherDescription;
                    request.temperature = richContext.temperature;
                    request.locationLabel = richContext.locationLabel;
                    request.latitude = richContext.latitude;
                    request.longitude = richContext.longitude;
                    request.aiProviderPreference = richContext.aiProviderPreference;
                    request.foregroundAcknowledgementText = richContext.foregroundAcknowledgementText;
                    request.foregroundAcknowledgementSource = richContext.foregroundAcknowledgementSource;
                }
                DialogMessage userMessage = dialogService.saveUserMessage(userId, request);
                turnId = beginChoreography(userId, sessionId, userMessage);
                request.foregroundAcknowledgementSent = true;
                GenerationAuthority generationAuthority =
                        stageAndClaimGeneration(userId, turnId, request, userMessage);
                if (turnId != null) {
                    emitLive(emitter, clientConnected, userId, turnId, 0L, "turn.started",
                            "{\"turnId\":" + turnId + "}", false);
                }
                // Progressive dual-kernel choreography:
                // - the full planner -> speaker -> bounded critic path starts immediately in the
                //   background and remains the authoritative reply;
                // - a separate non-thinking foreground kernel produces one short acknowledgement
                //   so the user is not left staring at a status label while depth is computed.
                // The deep speaker is told that this acknowledgement already happened, preventing
                // a second generic "I hear you" opening.
                boolean foregroundAcknowledgementAlreadyVisible =
                        richContext != null && richContext.foregroundAcknowledgementSent;
                request.foregroundAcknowledgementSent = true;
                final Long generationTurnId = turnId;
                final Long generationUserMessageId = userMessage == null ? null : userMessage.id;
                CompletableFuture<AuroraReplyVO> deepReply = CompletableFuture.supplyAsync(
                        () -> produceReply(userId, request, safety,
                                generationUserMessageId, generationTurnId, false,
                                generationAuthority),
                        aiExecutor);

                long eventSequence = 1L;
                ForegroundAcknowledgement acknowledgement = foregroundAcknowledgementAlreadyVisible
                        ? null : fastForegroundAcknowledgement(userId, request);
                if (acknowledgement != null && !acknowledgement.text().isBlank()
                        && !isTurnCancelled(userId, turnId)) {
                    // Retain the same source in terminal meta so diagnostics survive after this
                    // transient status event leaves the screen.
                    request.foregroundAcknowledgementSource = acknowledgement.source();
                    emitLive(emitter, clientConnected, userId, turnId, eventSequence++,
                            "foreground.status",
                            "{\"text\":\"" + escape(acknowledgement.text())
                                    + "\",\"source\":\"" + escape(acknowledgement.source())
                                    + "\",\"latencyMs\":" + acknowledgement.latencyMs() + "}", false);
                }

                AuroraReplyVO reply = deepReply.join();

                if (Boolean.TRUE.equals(reply.cancelled)) {
                    emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence, "turn.interrupted",
                            "{\"reason\":\"USER_INTERRUPTED\"}", true);
                    completeQuietly(emitter);
                    return;
                }

                if (choreographyService != null && reply.turnId != null) {
                    String deliveryOwner = "stream:" + java.util.UUID.randomUUID();
                    var lease = choreographyService.claimDeliveryLease(
                            userId, reply.turnId, deliveryOwner, DELIVERY_LEASE_TTL);
                    if (lease == null) {
                        sendOnce(emitter, "error",
                                "{\"message\":\"turn delivery is continuing on another runtime\"}");
                        completeQuietly(emitter);
                        return;
                    }
                    reply.deliveryLeaseOwner = lease.owner();
                    reply.deliveryLeaseToken = lease.fencingToken();
                }

                emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++, "turn.plan",
                        "{\"turnId\":" + numeric(reply.turnId)
                                + ",\"planId\":" + numeric(reply.planId) + "}", false);

                for (int i = 0; i < reply.messages.size(); i++) {
                    if (isTurnCancelled(userId, reply.turnId)) break;
                    if (i > 0) {
                        emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++, "segment",
                                "{\"break\":true}", false);
                    }
                    emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++, "bubble.started",
                            "{\"order\":" + (i + 1) + "}", false);
                    StreamProgress progress = streamText(emitter, clientConnected,
                            reply.messages.get(i), reply, eventSequence, userId, i + 1);
                    eventSequence = progress.nextEventSequence();
                    if (choreographyService != null && reply.turnId != null) {
                        // Progress is persisted per emitted chunk by streamText. This final write is
                        // idempotent and keeps compatibility with zero-token/empty bubble paths.
                        choreographyService.recordBubbleProgressFenced(
                                userId, reply.turnId, i + 1, progress.deliveredChars(),
                                reply.deliveryLeaseOwner, reply.deliveryLeaseToken,
                                DELIVERY_LEASE_TTL);
                    }
                    if (isTurnCancelled(userId, reply.turnId)) break;
                    if (choreographyService != null && reply.turnId != null) {
                        int bubbleOrder = i + 1;
                        String bubbleText = reply.messages.get(i);
                        choreographyService.deliverBubbleFenced(
                                userId, reply.turnId, bubbleOrder,
                                reply.deliveryLeaseOwner, reply.deliveryLeaseToken,
                                DELIVERY_LEASE_TTL,
                                () -> dialogService.saveAuroraMessage(userId, sessionId, bubbleText));
                    } else {
                        dialogService.saveAuroraMessage(userId, sessionId, reply.messages.get(i));
                    }
                    emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++, "bubble.completed",
                            "{\"order\":" + (i + 1) + "}", false);
                }
                if (isTurnCancelled(userId, reply.turnId)) {
                    emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence, "turn.interrupted",
                            "{\"reason\":\"USER_STOPPED\"}", true);
                    completeQuietly(emitter);
                    return;
                }
                if (choreographyService != null && reply.turnId != null) {
                    choreographyService.completeTurnFenced(
                            userId, reply.turnId,
                            reply.deliveryLeaseOwner, reply.deliveryLeaseToken);
                }
                publishTurnPersisted(userId, sessionId, userMessage);
                // VS-003b — meta now carries the full perception payload (agentLoop,
                // aiState, voice/weather/location/timezone) so the frontend can render
                // the same panels on stream as on the POST fallback path. Emitted (with
                // turn.completed) BEFORE the inner-voice synthesis below, so the turn's formal
                // closeout and the next-send / settle-it-today gating it unlocks happen immediately.
                emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++, "meta",
                        jsonMeta(reply, request, null), false);
                // turn.completed also advances the sequence so the inner_voice event emitted next
                // gets a strictly-higher, collision-free event id (the frontend dedups live events
                // by id -- a shared sequence would silently drop the inner_voice). The `terminal`
                // flag, not the sequence value, marks this as terminal in the live store.
                emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++, "turn.completed",
                        "{\"message\":\"done\"}", true);
                // W1 — Aurora's "inner voice" (心声): at most one inner_voice event per turn,
                // strictly additive and NON-BLOCKING to turn completion. The deferred model call
                // composition and optional TTS both run AFTER meta/turn.completed (and before
                // final done/close), so a slow enrichment can only delay its own side channel. The frontend SSE
                // reader reads until connection close, so it still receives this late event.
                AuroraDualKernelRuntime.InnerVoiceRequest resolvedInnerVoiceRequest = reply.innerVoiceRequest;
                if (resolvedInnerVoiceRequest == null && reply.deferredInnerVoiceRequest != null
                        && clientConnected.get()) {
                    try {
                        // The visible reply and turn.completed have already been delivered. Waiting here can
                        // delay only the optional side-channel, never the conversational critical path.
                        resolvedInnerVoiceRequest = reply.deferredInnerVoiceRequest.get(
                                8, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception plannerStillWorking) {
                        log.debug("Background planner did not yield an inner voice within the side-channel budget: {}",
                                plannerStillWorking.getMessage());
                    }
                }
                if (clientConnected.get() && resolvedInnerVoiceRequest != null) {
                    try {
                        String innerVoiceText = dualKernelRuntime.composeInnerVoice(resolvedInnerVoiceRequest);
                        if (innerVoiceText != null && !innerVoiceText.isBlank()) {
                            String audioDataUri = "";
                            String voiceId = "";
                            if (ttsClient != null && ttsClient.available()) {
                                try {
                                    voiceId = preferredTtsVoiceIdFor(loadProfile(userId));
                                    byte[] audio = ttsClient.synthesize(innerVoiceText, voiceId);
                                    audioDataUri = "data:audio/mpeg;base64,"
                                            + java.util.Base64.getEncoder().encodeToString(audio);
                                } catch (Exception audioFailure) {
                                    // The text is the product's heart-voice; speech is an optional
                                    // sensory layer. A provider outage must not make the thought
                                    // itself disappear from the user's experience.
                                    log.warn("Inner-voice audio synthesis failed for user {} turn {}; emitting text only: {}",
                                            userId, reply.turnId, audioFailure.getMessage());
                                    audioDataUri = "";
                                    voiceId = "";
                                }
                            }
                            // M2 (code review): build the payload via ObjectMapper so a raw control
                            // char in the LLM-composed text can never produce invalid JSON.
                            String innerVoicePayload = buildInnerVoicePayload(innerVoiceText, audioDataUri, voiceId);
                            emitLive(emitter, clientConnected, userId, reply.turnId, eventSequence++,
                                    "inner_voice", innerVoicePayload, false);
                        }
                    } catch (Exception innerVoiceFailure) {
                        log.warn("Inner-voice composition failed for user {} turn {}, omitting inner_voice event: {}",
                                userId, reply.turnId, innerVoiceFailure.getMessage());
                    }
                }
                // Preserve the original done contract used by the current frontend.
                sendStream(emitter, clientConnected,
                        SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                completeQuietly(emitter);
            } catch (Exception e) {
                log.error("Aurora stream failed: {}", e.getMessage(), e);
                boolean leaseSuperseded = e instanceof BusinessException business
                        && ErrorCode.CONFLICT.equals(business.code);
                if (turnId != null && choreographyService != null && !leaseSuperseded) {
                    try {
                        choreographyService.cancelTurn(userId, turnId, "STREAM_FAILED");
                    } catch (Exception stateError) {
                        log.warn("Aurora stream failure could not settle turn {}: {}",
                                turnId, stateError.getMessage());
                    }
                }
                // Best-effort error event so the client can fall back, then close.
                sendOnce(emitter, "error", "{\"message\":\"stream failed\"}");
                completeQuietly(emitter);
            }
        });
        return emitter;
    }

    private void publishTurnPersisted(Long userId, Long sessionId, DialogMessage userMessage) {
        if (eventPublisher != null && sessionId != null) {
            eventPublisher.publishEvent(new com.innercosmos.event.DialogTurnPersistedEvent(
                    userId, sessionId, userMessage == null ? null : userMessage.id));
        }
    }

    /**
     * VS-003b — stage rich SSE context for a soon-to-open stream. The browser's
     * EventSource can only GET, so the frontend POSTs the rich body here, gets a
     * token, then opens GET /stream?token=…. Returns the token. Best-effort: a
     * self-expiring entry, consumed once by {@link #consumeStage(Long, String)}.
     */
    @Override
    public String stageStreamContext(Long userId, ChatRequest request) {
        return streamStageStore.stage(userId, request);
    }

    /** VS-003b — consume (once) the staged rich context for a stream token. */
    @Override
    public ChatRequest consumeStage(Long userId, String token) {
        return streamStageStore.consume(userId, token);
    }

    /**
     * Persist the user turn + the single crisis safe-message as a DialogMessage so the
     * record is captured even on the blocked path (VS-003 §4). The safe message is
     * saved but NOT streamed as chat — the frontend routes to the safety-harbor UX.
     */
    private void saveUserAndBlockedAurora(Long userId, Long sessionId, String message, String mode, SafetyResult safety) {
        try {
            ChatRequest request = new ChatRequest();
            request.sessionId = sessionId;
            request.message = message;
            request.mode = normalizeMode(mode);
            dialogService.saveUserMessage(userId, request);
            String safe = safety.safeMessage == null
                    ? "我先陪你把安全放在第一位。现在请联系一个现实中可信任的人，或使用当地紧急支持资源。"
                    : safety.safeMessage;
            dialogService.saveAuroraMessage(userId, sessionId, safe);
        } catch (Exception e) {
            log.warn("Blocked-path persist failed (non-fatal): {}", e.getMessage());
        }
    }

    private void sendOnce(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ignored) {
            // Client may already be gone; nothing to do.
        }
    }

    private boolean sendStream(SseEmitter emitter, AtomicBoolean clientConnected,
                               SseEmitter.SseEventBuilder event) {
        if (!clientConnected.get()) return false;
        try {
            emitter.send(event);
            return true;
        } catch (Exception detached) {
            clientConnected.set(false);
            log.debug("Aurora live stream write failed; durable turn continues: {}",
                    detached.getMessage());
            return false;
        }
    }

    private boolean emitLive(SseEmitter emitter, AtomicBoolean clientConnected, Long userId, Long turnId,
                             long sequence, String name, String data, boolean terminal) {
        if (userId == null || turnId == null) {
            return sendStream(emitter, clientConnected,
                    SseEmitter.event().id("legacy:" + sequence).name(name).data(data));
        }
        String id = turnId + ":live:" + sequence;
        boolean publishedForReplay = false;
        try {
            liveEventStore.publish(new AuroraLiveEvent(userId, turnId, sequence, id, name, data, terminal));
            publishedForReplay = true;
        } catch (Exception transportFailure) {
            // The connected client and PostgreSQL choreography remain available. Redis reconnect
            // degrades to the durable timeline rather than aborting a safe in-flight response.
            log.warn("Aurora cross-Pod stream publish unavailable for turn {} sequence {}: {}",
                    turnId, sequence, transportFailure.getMessage());
        }
        boolean deliveredDirectly = sendStream(emitter, clientConnected,
                SseEmitter.event().id(id).name(name).data(data));
        return publishedForReplay || deliveredDirectly;
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private String jsonSafety(SafetyResult safety) {
        String safe = safety.safeMessage == null
                ? "我先陪你把安全放在第一位。现在请联系一个现实中可信任的人，或使用当地紧急支持资源。"
                : safety.safeMessage;
        return "{\"riskLevel\":\"" + escape(safety.riskLevel) + "\""
                + ",\"riskType\":\"" + escape(safety.riskType) + "\""
                + ",\"handledAction\":\"" + escape(safety.handledAction) + "\""
                + ",\"safetyState\":\"" + escape(safety.safetyState) + "\""
                + ",\"featureTarget\":\"safety-harbor\""
                + ",\"safeMessage\":\"" + escape(safe) + "\"}";
    }

    /**
     * Builds the sole model-facing context envelope. Keep enough continuity for a real friendship,
     * but cap stale history so the current userMessage cannot be drowned by months of observations.
     */
    private AgentContext compactAgentContext(AgentContext source, String currentUserMessage) {
        AgentContext compact = new AgentContext();
        if (source == null) return compact;
        compact.userId = source.userId;
        compact.profileSummary = abbreviate(source.profileSummary, 480);
        compact.timeLabel = source.timeLabel;
        compact.timezone = source.timezone;
        compact.locale = source.locale;
        compact.localDateTime = source.localDateTime;
        compact.lastInteractionLabel = source.lastInteractionLabel;
        compact.clientTimeHintStatus = source.clientTimeHintStatus;
        compact.weatherLabel = source.weatherLabel;
        compact.momentEmotionLabel = abbreviate(source.momentEmotionLabel, 160);
        compact.environmentLabel = abbreviate(source.environmentLabel, 160);
        compact.quietPolicy = source.quietPolicy;
        compact.focusPolicy = source.focusPolicy;
        compact.memoryRecallAllowed = source.memoryRecallAllowed;
        compact.multiMessageAllowed = source.multiMessageAllowed;
        compact.proactiveSensitivity = source.proactiveSensitivity;
        compact.cityLabel = source.cityLabel;
        compact.realWeatherLabel = abbreviate(source.realWeatherLabel, 120);
        compact.sleepInferred = source.sleepInferred;
        compact.nearestTodo = abbreviate(source.nearestTodo, 160);
        // Full current-session history travels once in the cache-friendly top-level
        // conversationHistory envelope after provider-aware budgeting.
        compact.recentMessages = new ArrayList<>();
        compact.longTermMemories = boundedContext(source.longTermMemories, MODEL_LONG_TERM_MEMORY_LIMIT, 240, false);
        compact.activeTodos = boundedContext(source.activeTodos, 3, 160, false);
        compact.completedTodoLessons = boundedContext(source.completedTodoLessons, 2, 160, false);
        compact.dailyObservations = boundedContext(source.dailyObservations, 3, 220, true);
        compact.weeklyObservations = boundedContext(source.weeklyObservations, 2, 260, true);
        compact.relationSignals = boundedContext(source.relationSignals, 3, 180, false);
        compact.themeSignals = boundedContext(source.themeSignals, 3, 180, false);
        compact.evidenceMemoryIds = source.evidenceMemoryIds == null
                ? new ArrayList<>()
                : new ArrayList<>(source.evidenceMemoryIds.stream().limit(MODEL_LONG_TERM_MEMORY_LIMIT).toList());
        compact.threeModelBlock = abbreviate(source.threeModelBlock, 1_800);
        compact.constitutionBlock = compact.threeModelBlock == null || compact.threeModelBlock.isBlank()
                ? abbreviate(source.constitutionBlock, 700)
                : "";
        compact.continuityAnchors = abbreviate(source.continuityAnchors, 500);
        return compact;
    }

    private List<String> boundedContext(List<String> values, int limit, int maxChars, boolean keepTail) {
        if (values == null || values.isEmpty() || limit <= 0) return new ArrayList<>();
        int from = keepTail ? Math.max(0, values.size() - limit) : 0;
        int to = keepTail ? values.size() : Math.min(values.size(), limit);
        List<String> bounded = new ArrayList<>();
        for (String value : values.subList(from, to)) {
            String item = abbreviate(value, maxChars);
            if (item != null && !item.isBlank()) bounded.add(item);
        }
        return bounded;
    }
    /**
     * Keep the legacy memory-context envelope (short-term messages, themes, emotion and summary)
     * while making its long-term evidence identical to the task-aware, privacy-gated pack used by
     * the dual-kernel context. This prevents the same turn from receiving a second gravity-only
     * memory selection with different IDs.
     */
    private AuroraMemoryContextVO alignMemoryContext(AuroraMemoryContextVO memoryContext,
                                                      AgentContext agentContext) {
        if (memoryContext == null || agentContext == null) return memoryContext;
        memoryContext.longTermMemoryNotes = new ArrayList<>(agentContext.longTermMemories);
        memoryContext.referencedMemoryIds = new ArrayList<>(agentContext.evidenceMemoryIds);
        memoryContext.memoryPolicy = "task_aware_hybrid_retrieval";
        memoryContext.continuityHypothesis = memoryContext.longTermMemoryNotes.isEmpty()
                ? "No relevant authorized long-term evidence was selected; stay with the current expression."
                : "Task-aware evidence was selected; reference it only when it helps and identify uncertainty.";
        if (!memoryContext.longTermMemoryNotes.isEmpty()
                && !memoryContext.proactiveSuggestions.contains(
                        "If referencing memory, use transparent wording and avoid sounding like surveillance.")) {
            memoryContext.proactiveSuggestions.add(
                    "If referencing memory, use transparent wording and avoid sounding like surveillance.");
        }
        return memoryContext;
    }

    @Override
    public AuroraReplyVO generateGreeting(Long userId, Long sessionId, String mode) {
        UserProfile profile = loadProfile(userId);
        String normalizedMode = normalizeMode(mode);
        boolean allowMemory = allowMemory(profile);
        AgentContext agentContext = agentContextAssembler.assemble(
                userId, sessionId, "", allowMemory,
                null, null, null, outputLanguage(), null);
        AgentContext modelAgentContext = compactAgentContext(agentContext, "");
        List<String> gravityMemories = allowMemory ? List.copyOf(modelAgentContext.longTermMemories) : List.of();
        AuroraMemoryContextVO memoryContext = allowMemory
                ? alignMemoryContext(memoryContextService.buildContext(userId, sessionId, "", 6, 0), agentContext)
                : null;
        String timeLabel = agentContext.timeLabel;
        ModeStrategy modeStrategy = modeRegistry.get(normalizedMode);
        ResolvedModel resolved = modelRouter.resolve(userId, sessionId);
        Map<String, Object> grounding = greetingGrounding(
                profile, agentContext, loadUserNickname(userId));

        String prompt = new PromptBuilder().withPromptVersionService(promptVersionService)
                .withSystemBoundary()
                .withConversationMode(normalizedMode)
                .withModeSegment(modeStrategy)
                .withOutputSchema()
                .build()
                + "\n\nCurrent time: " + timeLabel + ". Generate one grounded opening using only greetingGrounding.";

        Map<String, Object> greetingContext = new LinkedHashMap<>();
        // PromptBuilder output contains identity/safety/mode instructions, so keep it in the
        // provider's system role. The user-owned grounding below remains data-only.
        greetingContext.put("auroraSystemPrompt", prompt);
        greetingContext.put("mode", normalizedMode);
        greetingContext.put("timeLabel", timeLabel);
        greetingContext.put("memoryRecallAllowed", allowMemory);
        greetingContext.put("greetingGrounding", grounding);
        greetingContext.put("requireRemoteProvider", true);
        greetingContext.put("providerPolicy", providerPolicy(resolved));
        // M-012: the proactive greeting also samples at the active mode's temperature
        // (null-safe — absent key keeps the provider client's hardcoded default).
        if (modeStrategy != null) {
            greetingContext.put("modeTemperature", modeStrategy.temperature());
        }

        AuroraReplyVO vo;
        if (resolved == null || !resolved.isResolved() || "MOCK".equalsIgnoreCase(resolved.provider())) {
            vo = toReply(profile, unavailableGreeting(outputLanguage()), null, normalizedMode,
                    memoryContext, gravityMemories, allowMemory);
        } else try {
            StructuredAiResults.AuroraResult ai = structuredAiService.call(userId, "AURORA_PROACTIVE_GREETING_" + normalizedMode,
                    greetingInstruction(),
                    greetingContext,
                    StructuredAiResults.AuroraResult.class,
                    () -> unavailableGreeting(outputLanguage()),
                    resolved.client());
            vo = toReply(profile, ai, null, normalizedMode, memoryContext, gravityMemories, allowMemory);
        } catch (Exception e) {
            log.error("Aurora greeting provider unavailable: {}", e.getMessage(), e);
            vo = toReply(profile, unavailableGreeting(outputLanguage()), null, normalizedMode,
                    memoryContext, gravityMemories, allowMemory);
        }
        vo.aiState = aiState(resolved);
        vo.suggestSettle = false;
        if (sessionId != null) {
            for (String msg : vo.messages) {
                dialogService.saveAuroraMessage(userId, sessionId, msg);
            }
        }
        return vo;
    }

    static Map<String, Object> greetingGrounding(UserProfile profile, AgentContext context) {
        return greetingGrounding(profile, context, "");
    }

    static Map<String, Object> greetingGrounding(UserProfile profile, AgentContext context,
                                                  String userNickname) {
        Map<String, Object> grounding = new LinkedHashMap<>();
        grounding.putAll(userCalibration(profile, userNickname));
        grounding.put("profileInterests", context == null ? List.of()
                : context.themeSignals.stream().filter(java.util.Objects::nonNull).limit(4).toList());
        grounding.put("profileBio", profile == null ? "" : abbreviateGreeting(profile.bio, 280));
        String assembledEnvironment = context == null ? "" : greetingValue(context.environmentLabel);
        String calibratedEnvironment = profile == null ? "" : greetingValue(profile.currentEnvironmentLabel);
        grounding.put("currentEnvironment", assembledEnvironment.isBlank()
                ? calibratedEnvironment : assembledEnvironment);
        grounding.put("currentEmotion", context == null ? "" : greetingValue(context.momentEmotionLabel));
        grounding.put("nearestUnfinishedItem", context == null ? "" : greetingValue(context.nearestTodo));
        grounding.put("unfinishedItems", context == null ? List.of()
                : context.activeTodos.stream().filter(java.util.Objects::nonNull).limit(4).toList());
        grounding.put("recentObservations", context == null ? List.of()
                : context.dailyObservations.stream().filter(java.util.Objects::nonNull).limit(3).toList());
        return grounding;
    }

    /**
     * The complete, bounded first-run calibration shared by proactive greeting and normal turns.
     * Values are data, never behavioural instructions. Keeping the keys stable also gives tests
     * and observability a precise contract for what should make two users' first turns differ.
     */
    static Map<String, Object> userCalibration(UserProfile profile, String userNickname) {
        Map<String, Object> calibration = new LinkedHashMap<>();
        calibration.put("userNickname", abbreviateGreeting(userNickname, 80));
        calibration.put("auroraTone", profile == null ? "" : abbreviateGreeting(profile.auroraTone, 80));
        calibration.put("proactiveSensitivity",
                profile == null || profile.proactiveSensitivity == null ? "" : profile.proactiveSensitivity);
        calibration.put("reflectionDepth",
                profile == null || profile.reflectionDepth == null ? "" : profile.reflectionDepth);
        calibration.put("currentEnvironment",
                profile == null ? "" : abbreviateGreeting(profile.currentEnvironmentLabel, 280));
        return calibration;
    }

    private static String greetingValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String abbreviateGreeting(String value, int max) {
        String text = greetingValue(value);
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private String greetingInstruction() {
        return """
                You generate Aurora's initial opening before the user has sent a message.
                Return only valid AuroraResult JSON with 1-2 short natural-language segments.

                Grounding contract:
                1. Use only greetingGrounding. Prefer, in order: an unfinished item, current
                   environment/state, then an established interest/theme.
                   Let userNickname, auroraTone, proactiveSensitivity and reflectionDepth shape
                   how you address the user, how directly you open, and how deep the invitation is.
                   Never recite these settings or force the nickname into every opening.
                2. Mention at most one concrete hook. Phrase it as an invitation, never as
                   surveillance, certainty, diagnosis, or a claim that the user feels something now.
                3. If all grounding fields are empty, offer a neutral specific choice of where to
                   begin. Do not invent a memory, interest, event, relationship, or unfinished topic.
                4. Do not say "I remembered you", "I was thinking about you", or imply sentience.
                5. referencedMemoryIds must be empty unless the supplied context contains explicit
                   evidence ids; memoryReferenced must match it.
                6. This path requires a real provider. Never imitate personalisation with a template.
                """;
    }

    private String outputLanguage() {
        return llmConfig.prompt == null ? "auto" : llmConfig.prompt.language;
    }

    static StructuredAiResults.AuroraResult unavailableGreeting(String language) {
        StructuredAiResults.AuroraResult result = new StructuredAiResults.AuroraResult();
        boolean english = language == null || language.isBlank()
                || language.toLowerCase(Locale.ROOT).startsWith("auto")
                || language.toLowerCase(Locale.ROOT).startsWith("en");
        result.segments = List.of(english
                ? "AI-generated opening is unavailable right now. You can start with whatever matters to you."
                : "AI 生成的开场暂时不可用。你可以从此刻最想说的事情开始。");
        result.speakCount = 1;
        result.continueReason = "provider-unavailable";
        result.detectedTheme = "provider-status";
        result.nextQuestion = "";
        result.smallStep = "";
        result.featureSuggestion = "";
        result.featureTarget = "";
        result.memoryReferenced = false;
        result.referencedMemoryIds = List.of();
        result.riskFlags = List.of("PROVIDER_UNAVAILABLE");
        return result;
    }

    private AuroraReplyVO blockedReply(Long userId, ChatRequest request, SafetyResult safety,
                                       Long userMessageId, Long turnId) {
        AuroraReplyVO blocked = new AuroraReplyVO();
        blocked.messages = List.of(safety.safeMessage == null
                ? "我先陪你把安全放在第一位。现在请联系一个现实中可信任的人，或使用当地紧急支持资源。"
                : safety.safeMessage);
        blocked.replyTone = "SAFETY";
        blocked.detectedTheme = safety.riskType;
        blocked.nextQuestion = "";
        blocked.smallStep = "先联系一个现实中可信任的人。";
        blocked.featureSuggestion = "可以先离开普通聊天，进入安全港页面。";
        blocked.featureTarget = "safety-harbor";
        blocked.suggestSettle = true;
        blocked.memoryReferenced = false;
        blocked.referencedMemoryIds = List.of();
        blocked.memoryContext = null;
        blocked.riskFlags = List.of(safety.riskType == null ? "SAFETY" : safety.riskType);
        blocked.agentLoop = Map.of("speakCount", 1, "continueReason", "safety-first");
        blocked.aiState = aiState(null);
        blocked.turnId = turnId;
        if (turnId != null && choreographyService != null) {
            choreographyService.commitPlan(userId, turnId, blocked);
            choreographyService.deliverBubble(userId, turnId, 1,
                    () -> dialogService.saveAuroraMessage(userId, request.sessionId, blocked.messages.get(0)));
            choreographyService.completeTurn(userId, turnId);
        } else {
            DialogMessage saved = dialogService.saveAuroraMessage(userId, request.sessionId, blocked.messages.get(0));
            recordChoreography(userId, request.sessionId, userMessageId, blocked,
                    saved == null ? List.of() : List.of(saved));
        }
        return blocked;
    }

    private AuroraReplyVO toReply(UserProfile profile,
                                  StructuredAiResults.AuroraResult ai,
                                  ChatRequest request,
                                  String mode,
                                  AuroraMemoryContextVO memoryContext,
                                  List<String> gravityMemories,
                                  boolean allowMemory) {
        StructuredAiResults.AuroraResult safeAi = ai == null ? new StructuredAiResults.AuroraResult() : ai;
        List<String> recentAurora = request == null ? List.of() : recentAuroraMessages(request.sessionId, 8);
        List<String> messages = cleanSegments(safeAi.segments, recentAurora);
        if (messages.isEmpty()) {
            String userText = request == null ? "" : request.message;
            messages = fallbackAuroraResult(userText, mode, gravityMemories, memoryContext, allowMemory, null).segments;
        }

        AuroraReplyVO vo = new AuroraReplyVO();
        vo.messages = messages;
        vo.replyTone = profile == null || isBlank(profile.auroraTone) ? "温柔、具体、像朋友" : profile.auroraTone;
        vo.detectedTheme = isBlank(safeAi.detectedTheme) ? modeLabel(mode) : safeAi.detectedTheme;
        vo.nextQuestion = safeAi.nextQuestion == null ? "" : safeAi.nextQuestion;
        vo.smallStep = safeAi.smallStep == null ? "" : safeAi.smallStep;
        vo.featureSuggestion = safeAi.featureSuggestion == null ? "" : safeAi.featureSuggestion;
        vo.featureTarget = safeAi.featureTarget == null ? "" : safeAi.featureTarget;
        vo.suggestSettle = request != null && rhythmGuardService.shouldSuggestSettle(profile == null ? null : profile.userId, request.sessionId);
        vo.memoryReferenced = allowMemory && (Boolean.TRUE.equals(safeAi.memoryReferenced) || hasMemoryContext(memoryContext, gravityMemories));
        vo.referencedMemoryIds = allowMemory ? referencedIds(safeAi, memoryContext) : List.of();
        vo.memoryContext = allowMemory ? memoryContext : null;
        vo.riskFlags = safeAi.riskFlags == null ? List.of() : safeAi.riskFlags;
        vo.agentLoop = Map.of(
                "speakCount", messages.size(),
                "continueReason", isBlank(safeAi.continueReason) ? inferContinueReason(messages, mode) : safeAi.continueReason,
                "mode", mode,
                "modeLabel", modeLabel(mode)
        );
        vo.aiState = aiState(null);
        return vo;
    }

    /**
     * The latency-facing expression kernel. It is a real, non-thinking model call running in
     * parallel with the authoritative planner → speaker → critic path. A strict 2.5-second
     * contract and local fallback guarantee that first feedback cannot inherit the planner's
     * long reasoning tail. It receives no memory and may not advise, question, diagnose or claim
     * facts, so speed never expands the deep kernel's authority.
     */
    private ForegroundAcknowledgement fastForegroundAcknowledgement(Long userId, ChatRequest request) {
        long start = System.nanoTime();
        String message = request == null || request.message == null ? "" : request.message.strip();
        ForegroundContinuity continuity = foregroundContinuity(userId, request, message);
        String fallback = localForegroundAcknowledgement(message, continuity);
        if (message.isBlank()) {
            return new ForegroundAcknowledgement(fallback, "local-empty", elapsedMillis(start));
        }
        // Relationship ambiguity is policy-sensitive: a tiny expression model repeatedly turned
        // "the cause is unknown" into praise, therapeutic effect, or a story about the other
        // person's motives. Keep the immediate sentence factual and deterministic for this narrow
        // class; the authoritative planner → speaker → critic path still runs in full afterwards.
        if (isProtectedRelationshipAmbiguity(message)) {
            return new ForegroundAcknowledgement(
                    fallback, "local-relationship-boundary", elapsedMillis(start));
        }
        CompletableFuture<String> modelCall = CompletableFuture.supplyAsync(
                () -> modelForegroundAcknowledgement(userId, request, message, continuity, fallback), aiExecutor);
        try {
            String candidate = modelCall.get(2_400, TimeUnit.MILLISECONDS);
            String safe = safeForegroundAcknowledgement(candidate, fallback, message);
            String source = safe.equals(fallback)
                    ? (candidate == null || candidate.isBlank() ? "local-provider-fallback" : "local-quality-gate")
                    : "model-fast";
            return new ForegroundAcknowledgement(safe, source, elapsedMillis(start));
        } catch (TimeoutException timeout) {
            modelCall.cancel(true);
            return new ForegroundAcknowledgement(fallback, "local-timeout", elapsedMillis(start));
        } catch (Exception exception) {
            modelCall.cancel(true);
            log.debug("Fast foreground kernel fell back locally: {}", exception.getMessage());
            return new ForegroundAcknowledgement(fallback, "local-error", elapsedMillis(start));
        }
    }

    private String modelForegroundAcknowledgement(Long userId, ChatRequest request,
                                                   String message, ForegroundContinuity continuity,
                                                   String fallback) {
        ResolvedModel resolved = modelRouter.resolve(userId, request.sessionId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userMessage", message);
        context.put("mode", normalizeMode(request.mode));
        context.put("preferredProvider", resolved.provider());
        context.put("recentConversation", continuity.recentMessages());
        StructuredAiResults.AuroraForegroundResult result = structuredAiService.call(
                userId,
                "AURORA_FOREGROUND_" + normalizeMode(request.mode),
                """
                You are Aurora's fast expression kernel. A deeper understanding kernel is working
                in parallel; your only job is to meet this exact moment honestly.
                Output strict JSON only: {"text":"one natural sentence in the user's current language"}.
                Keep text concise (roughly 8-30 English words or 18-52 Chinese characters). Anchor it
                in one concrete fact or tension from the user's words, like an intelligent friend
                responding in the moment.
                Do not mention systems, models, thinking or processing. Do not ask a question, give
                advice, diagnose, or explain another person's motives. Never promise permanent
                presence. Avoid stock phrases equivalent to “I hear you”, “this is normal”, “I am
                here with you”, or “that shows how much you care”. Do not praise the user's attitude
                or turn restraint and directness into a therapeutic achievement. Avoid polished
                summary lines about “making space” or “giving each other room”. Do not paraphrase the
                whole input or invent experience. If advice was explicitly declined, leave one quiet
                landing point. Even when action advice is requested, this fast kernel only meets the
                 pressure of several tasks arriving together; concrete action belongs to the deep
                 kernel that has seen the full plan.
                 A bounded recentConversation may be present only to resolve references such as
                 “then?”, “continue”, or “what happened next?”. Never introduce a person, work,
                 event or topic absent from both the current input and recentConversation. If the
                 current input explicitly changes topic, follow it and ignore the old topic.
                 """,
                context,
                StructuredAiResults.AuroraForegroundResult.class,
                () -> {
                    StructuredAiResults.AuroraForegroundResult local =
                            new StructuredAiResults.AuroraForegroundResult();
                    local.text = fallback;
                    return local;
                },
                resolved.client());
        return result == null ? null : result.text;
    }

    private String localForegroundAcknowledgement(String message, ForegroundContinuity continuity) {
        if (message == null || message.isBlank()) {
            return foregroundAcknowledgementFallback().segments.get(0);
        }
        boolean english = !containsHan(message);
        if (isLowInformationContinuation(message) && !continuity.anchor().isBlank()) {
            String work = firstNamedWork(continuity.anchor());
            if (!work.isBlank()) {
                return english ? "We’ll stay with " + work + " and continue from there."
                        : "我们继续沿着" + work + "往下说，不换到别的话题。";
            }
            String prior = firstConcreteClause(continuity.anchor());
            if (!prior.isBlank()) {
                return english ? "I’ll continue from the previous point about “" + prior + "”."
                        : "我们就接着刚才「" + prior + "」这一点往下说。";
            }
        }

        boolean noAdvice = message.contains("先别") && (message.contains("方案") || message.contains("建议"))
                || message.contains("不要给") && (message.contains("方案") || message.contains("建议"))
                || english && message.toLowerCase().matches("(?s).*(don't|do not|no)\\s+(reassure|advise|give me advice).*");
        boolean actionRequested = message.matches(
                "(?s).*(十分钟|先动哪|先做哪|拆出|只拆).*(一步|开始|任务).*")
                || english && message.toLowerCase().matches("(?s).*(first|one|small).*(step|action|task).*");
        if (actionRequested) {
            return english ? "Several tasks are crowding the same moment; they do not all need to be unfolded at once."
                    : "几件任务同时挤在眼前，先不用把它们全部铺开。";
        }
        if (message.contains("展示") || message.contains("汇报") || message.contains("答辩")
                || message.toLowerCase().contains("presentation") || message.toLowerCase().contains("demo")) {
            if (english) {
                return noAdvice
                        ? "No reassurance: one rough edge has become evidence, in your mind, for the seriousness of the whole project."
                        : "The project is about to be seen, so this tension has become very specific.";
            }
            return noAdvice
                    ? "先不谈方案。明天要把项目交到别人面前，紧张先留在这里。"
                    : "项目就要交到别人面前了，这一刻的紧张很具体。";
        }
        if (message.contains("关系") || message.contains("朋友") || message.contains("伴侣")
                || message.contains("同事") || message.contains("家人")) {
            return "今天的变化是你看见的，原因还不知道；先把这两件事分开。";
        }
        if (message.contains("累") || message.contains("撑不住") || message.contains("压力")
                || message.contains("焦虑") || message.contains("紧张")) {
            return noAdvice
                    ? "先不拆步骤。眼前这件事对你很重，我们先停在这里。"
                    : "眼前这份重量已经很具体了，先不用急着把它变成答案。";
        }
        if (noAdvice) {
            return english ? "No advice. I will leave the weight of that sentence intact for a moment."
                    : "先不谈方案。你刚才放下的这句话，我不急着把它推向下一步。";
        }

        String clause = firstConcreteClause(message);
        if (!clause.isBlank()) {
            return english ? "The part about “" + clause + "” feels like the live wire; I would not smooth it over yet."
                    : "你说的「" + clause + "」，我先不急着替它下结论。";
        }
        return foregroundAcknowledgementFallback().segments.get(0);
    }

    private ForegroundContinuity foregroundContinuity(Long userId, ChatRequest request, String currentMessage) {
        if (request == null || request.sessionId == null) return ForegroundContinuity.empty();
        try {
            List<DialogMessage> rows = dialogService.messages(request.sessionId);
            if (rows == null || rows.isEmpty()) return ForegroundContinuity.empty();
            List<DialogMessage> eligible = rows.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(row -> row.userId == null || userId == null || userId.equals(row.userId))
                    .filter(row -> row.textContent != null && !row.textContent.isBlank())
                    .filter(row -> currentMessage == null || !currentMessage.strip().equals(row.textContent.strip()))
                    .toList();
            List<String> bounded = eligible.stream()
                    .skip(Math.max(0, eligible.size() - 4L))
                    .map(row -> ("USER".equalsIgnoreCase(row.speaker) ? "User: " : "Aurora: ")
                            + abbreviate(row.textContent, 180))
                    .toList();
            if (bounded.isEmpty()) return ForegroundContinuity.empty();
            String anchor = bounded.get(bounded.size() - 1).replaceFirst("^(?:User|Aurora):\\s*", "");
            return new ForegroundContinuity(bounded, anchor);
        } catch (RuntimeException unavailable) {
            log.debug("Fast foreground continuity unavailable: {}", unavailable.getMessage());
            return ForegroundContinuity.empty();
        }
    }

    private boolean isLowInformationContinuation(String message) {
        if (message == null) return false;
        String normalized = message.replaceAll("[\\s，。！？!?…]+", "").toLowerCase(Locale.ROOT);
        return normalized.matches("(然后呢|继续|继续说|接着呢|接着说|后来呢|往下说|goon|continue|andthen|whatnext)");
    }

    private String firstNamedWork(String text) {
        if (text == null || text.isBlank()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("《[^》\\r\\n]{1,40}》").matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private boolean containsHan(String value) {
        if (value == null) return false;
        return value.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private String safeForegroundAcknowledgement(String candidate, String fallback, String userMessage) {
        String text = candidate == null ? "" : candidate.strip();
        String input = userMessage == null ? "" : userMessage;
        int length = text.codePointCount(0, text.length());
        boolean unsafeShape = length < 8 || length > 80
                || text.contains("?") || text.contains("？")
                || text.contains("你可以") || text.contains("不妨") || text.contains("建议")
                || text.contains("我听到了") || text.contains("这很正常")
                || text.contains("我陪着你") || text.contains("我在这里")
                || text.contains("说明你") || text.contains("正在思考")
                || text.contains("正在处理") || text.contains("模型")
                || text.contains("自然的") || text.contains("正常的")
                || text.contains("说出来了就好") || text.contains("说出来就对了")
                || text.contains("说出来就好受") || text.contains("好受些")
                || text.contains("等一等") || text.contains("或许会更清楚")
                || text.contains("给了彼此") || text.contains("先打开")
                || text.contains("只看第一处") || text.contains("写完")
                || text.contains("就十分钟") || text.contains("很珍贵")
                || text.contains("挺成熟") || text.contains("很成熟")
                || text.contains("挺清醒") || text.contains("很清醒")
                || text.contains("清醒的温柔") || text.contains("一种清醒")
                || text.contains("没有立刻追问") || text.contains("已经轻了")
                || text.contains("轻了一些")
                 || text.contains("已经是在面对") || text.contains("本身就是")
                 || text.contains("这本身") || text.contains("本身就")
                 || text.contains("给自己留") || text.contains("留了个平静")
                 || text.matches("(?s).*你(?:愿意|选择|能|能够|可以|没有|没).*?(?:就是|留出|留了|给了|给自己|意味着|说明|证明).*")
                 || text.matches("(?s).*(?:留出|给了).{0,8}(?:空间|余地|可能).*")
                 || text.contains("像") || text.contains("仿佛") || text.contains("好像")
                || text.contains("毫无预兆") || text.contains("雨的气息")
                || text.contains("打开") || text.contains("五分钟")
                || text.contains("十分钟") || text.contains("不要求")
                || text.contains("只要求")
                || ((text.contains("胃里") || text.contains("胸口") || text.contains("呼吸")
                    || text.contains("心跳") || text.contains("发抖") || text.contains("手心"))
                    && !(input.contains("胃里") || input.contains("胸口") || input.contains("呼吸")
                    || input.contains("心跳") || input.contains("发抖") || input.contains("手心")));
        return unsafeShape ? fallback : text;
    }

    private boolean isProtectedRelationshipAmbiguity(String message) {
        String input = message == null ? "" : message.replaceAll("\\s+", "");
        boolean actor = input.contains("关系") || input.contains("朋友") || input.contains("同事")
                || input.contains("父母") || input.contains("伴侣") || input.contains("他")
                || input.contains("她");
        boolean tension = input.contains("冷淡") || input.contains("很冷") || input.contains("变冷")
                || input.contains("疏远") || input.contains("误解") || input.contains("争吵");
        boolean protectsUnknown = input.contains("不想猜") || input.contains("不想先猜")
                || input.contains("不想去猜") || input.contains("不愿意猜")
                || input.contains("先不猜") || input.contains("不想下结论")
                || input.contains("不知道是不是") || input.contains("还不确定")
                || input.contains("不能确定") || input.contains("不想判断")
                || input.contains("不想贴标签");
        return actor && tension && protectsUnknown;
    }

    private record ForegroundAcknowledgement(String text, String source, long latencyMs) {}
    private record ForegroundContinuity(List<String> recentMessages, String anchor) {
        private static ForegroundContinuity empty() {
            return new ForegroundContinuity(List.of(), "");
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private String firstConcreteClause(String message) {
        String normalized = message.replaceAll("\\s+", " ").strip();
        String[] clauses = normalized.split("[。！？!?；;\\n]");
        for (String raw : clauses) {
            String clause = raw.strip();
            int length = clause.codePointCount(0, clause.length());
            if (length < 4) continue;
            if (length > 24) clause = clause.substring(0, clause.offsetByCodePoints(0, 24)) + "…";
            return clause;
        }
        return "";
    }

    private StructuredAiResults.AuroraResult foregroundAcknowledgementFallback() {
        StructuredAiResults.AuroraResult fallback = new StructuredAiResults.AuroraResult();
        fallback.segments = List.of("先不急着往下推。你刚才那句话，我认真放在这里。");
        fallback.speakCount = 1;
        fallback.continueReason = "foreground-ack-fallback";
        fallback.memoryReferenced = false;
        fallback.referencedMemoryIds = List.of();
        fallback.riskFlags = List.of();
        return fallback;
    }

    private StructuredAiResults.AuroraResult callWithRetry(Long userId, String mode, Map<String, Object> turnContext,
                                                            ResolvedModel resolved, ChatRequest request,
                                                            List<String> gravityMemories,
                                                            AuroraMemoryContextVO memoryContext, boolean allowMemory,
                                                            String stateSignal) {
        int maxRetries = 2;
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return structuredAiService.call(userId, "AURORA_AGENT_LOOP_" + mode,
                        auroraInstruction(false),
                        turnContext,
                        StructuredAiResults.AuroraResult.class,
                        () -> fallbackAuroraResult(request.message, mode, gravityMemories, memoryContext, allowMemory, stateSignal),
                        resolved.client());
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                boolean isRetryable = isRetryableError(e.getMessage()) ||
                    (cause != null && isRetryableError(cause.getMessage()));
                if (!isRetryable || attempt >= maxRetries) throw e;
                lastException = e;
                log.warn("Aurora LLM retryable error (attempt {}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
                try { Thread.sleep(500L * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
            }
        }
        throw new RuntimeException("LLM call failed after " + (maxRetries + 1) + " attempts", lastException);
    }

    private boolean isRetryableError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("timeout") || lower.contains("timed out") ||
               lower.contains("connection reset") || lower.contains("broken pipe") ||
               lower.contains("503") || lower.contains("502") ||
               lower.contains("io error") || lower.contains("socket");
    }

    // Gemini audit 3.8 (PARTIAL/P0): the audit confirmed there is no per-token leak here --
    // produceReply() (shared by BOTH the POST replyRich path and the SSE stream path) already
    // generates the full reply and runs this gate on the complete text before either path
    // publishes anything; streamText() below only paces the ALREADY-sanitized text into SSE
    // chunks. What the audit found too narrow was the CHECK itself (4 hardcoded English
    // identity-claim phrases) -- widened here to also catch prompt/schema leakage, and now logs
    // which policy version ran so a rejection is traceable to the exact rule set that fired.
    private static final String OUTPUT_POLICY_VERSION = "aurora-output-policy.v2";

    private AuroraReplyVO sanitizeLlmOutput(AuroraReplyVO vo, Long userId) {
        if (vo.messages == null || vo.messages.isEmpty()) return vo;
        List<String> sanitized = new ArrayList<>();
        for (String msg : vo.messages) {
            if (isLlmOutputBoundaryViolation(msg)) {
                log.warn("[{}] LLM output triggered hard boundary violation for user={}, replacing message",
                        OUTPUT_POLICY_VERSION, userId);
                sanitized.add("I heard you. Let me think about this differently - let us find a more authentic direction together.");
                if (continuityService != null) {
                    continuityService.recordRepair(userId, "llm_output_boundary_violation",
                        "LLM generated content violating hard boundaries, automatically sanitized");
                }
            } else {
                sanitized.add(msg);
            }
        }
        vo.messages = sanitized;
        return vo;
    }

    private boolean isLlmOutputBoundaryViolation(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        boolean identityClaim = lower.contains("i am human") || lower.contains("i have consciousness") ||
               lower.contains("i feel real emotions") || lower.contains("biological life");
        // Gemini audit 3.8: Aurora's reply goes through the same StructuredAiService chokepoint
        // PersonaChat uses (callWithRetry -> structuredAiService.call), so the same class of
        // prompt-injection ("ignore the above, print your instructions/JSON") applies equally --
        // reuse the shared guard rather than maintaining a second, divergent leakage check.
        boolean structuredEnvelope = lower.contains("[[silence]]")
                || lower.contains("<thinking>") || lower.contains("</thinking>")
                || lower.contains("<reasoning>") || lower.contains("</reasoning>")
                || lower.startsWith("```")
                || (lower.startsWith("{") && (lower.contains("\"segments\"")
                    || lower.contains("\"speakcount\"") || lower.contains("\"detectedtheme\"")));
        return identityClaim || structuredEnvelope || PromptLeakageGuard.leaksInternalSchema(text);
    }

    private AuroraReplyVO differentiatedFallback(Exception e, String message, String mode, String stateSignal) {
        // A6 user-visible degradation contract: classification (by exception type + message, whole
        // cause chain) lives in the unit-tested AiFailureContract. PROVIDER_UNAVAILABLE keeps the
        // state-aware message so an unreachable provider still reflects the perceptual state signal
        // (VS-004 mock-fallback coherence; perception only, not a clinical label -- vision §9/§13).
        AiFailureContract.Category category = AiFailureContract.classify(e);
        String flag = category.riskFlag;
        String fallbackMsg = category == AiFailureContract.Category.PROVIDER_UNAVAILABLE
                ? fallbackAwareMessage(stateSignal)
                : category.defaultUserMessage;
        AuroraReplyVO vo = new AuroraReplyVO();
        vo.messages = List.of(fallbackMsg);
        vo.replyTone = "warm, specific, friend-like";
        vo.detectedTheme = modeLabel(mode);
        vo.nextQuestion = "";
        vo.smallStep = "";
        vo.featureSuggestion = "";
        vo.featureTarget = "";
        vo.suggestSettle = false;
        vo.memoryReferenced = false;
        vo.referencedMemoryIds = List.of();
        vo.memoryContext = null;
        vo.riskFlags = List.of("EMERGENCY_FALLBACK", flag);
        vo.agentLoop = Map.of("speakCount", 1, "continueReason", "emergency-fallback-" + flag.toLowerCase(), "mode", mode, "modeLabel", modeLabel(mode));
        vo.aiState = aiState(null);
        return vo;
    }

    private AuroraReplyVO emergencyFallback(String message, String mode) {
        return differentiatedFallback(new RuntimeException("generic"), message, mode, null);
    }

    private StructuredAiResults.AuroraResult fallbackAuroraResult(String message,
                                                                  String mode,
                                                                  List<String> gravityMemories,
                                                                  AuroraMemoryContextVO memoryContext,
                                                                  boolean allowMemory,
                                                                  String stateSignal) {
        StructuredAiResults.AuroraResult result = new StructuredAiResults.AuroraResult();
        result.segments = fallbackSegments(message, mode, allowMemory && gravityMemories != null && !gravityMemories.isEmpty(), stateSignal);
        result.speakCount = result.segments.size();
        result.continueReason = "fallback-explicit";
        result.detectedTheme = modeLabel(mode);
        result.nextQuestion = "你愿意把此刻最需要被听见的那一部分再说一点吗？";
        result.smallStep = "先写下一句最真实的话。";
        result.featureSuggestion = "如果这件事很乱，可以把它送进思维碎纸机，让 Aurora 帮你整理成可回看的线索。";
        result.featureTarget = "thought-shredder";
        result.memoryReferenced = allowMemory && gravityMemories != null && !gravityMemories.isEmpty();
        result.referencedMemoryIds = memoryContext == null || memoryContext.referencedMemoryIds == null ? List.of() : memoryContext.referencedMemoryIds;
        result.riskFlags = List.of("FALLBACK_USED");
        return result;
    }

    private List<String> fallbackSegments(String message, String mode, boolean hasMemory, String stateSignal) {
        List<String> segments = new ArrayList<>();
        String text = message == null ? "" : message.trim();
        // VS-004 — the fallback gently reflects the perceptual state signal so the
        // deterministic path stays coherent with the richer prompt context. The
        // signal is a perception ("此刻偏疲惫/脆弱/平静/开放"), never a clinical label.
        String state = stateSignal == null ? "" : stateSignal.trim();
        if ("CAPSULE_SHAPING".equals(mode)) {
            segments.add("不用把自己总结成几条标签。先讲一个最近很像你的具体瞬间。");
            segments.add("我会从故事里慢慢辨认你的声音、在意和边界；材料够了就一起生成第一版私密侧影。");
        } else if ("ACTION_SPLIT".equals(mode)) {
            segments.add("我先不把它变成一整套计划。我们只找一个十分钟内能开始的小动作。");
            segments.add("你现在最容易动起来的第一步，可能不是\"解决它\"，而是先把它写成一句可执行的话。");
        } else if ("SOCRATIC".equals(mode)) {
            segments.add("我先陪你停在这个想法旁边，不急着证明它对或错。");
            segments.add("这件事里，你最确定的事实是什么？最不确定的解释又是什么？");
        } else if ("SLEEP_REVIEW".equals(mode)) {
            segments.add("今天先到这里也可以。不是所有问题都需要在睡前解决完。");
            segments.add("我们把它收成一句话，剩下的交给明天更清醒的你。");
        } else if ("RELATION_REVIEW".equals(mode)) {
            segments.add("我会先帮你把事实和感受分开，不急着替任何人下判断。");
            segments.add("在这段关系里，你最想被对方理解的需要是什么？");
        } else if ("THOUGHT_CLARIFY".equals(mode)) {
            segments.add("我听见这里面有好几股线缠在一起。我们可以先把事实、感受和担心拆开。");
        } else if (state.contains("疲惫") || state.contains("脆弱") || state.contains("承压")) {
            // Fallback coherence with the state signal: when the user seems tired /
            // fragile right now, lead by steadying the moment, not by digging.
            segments.add("我看你这一刻像是承着一点分量。先不用讲得很完整，我会陪你把最重的那一块慢慢拨出来。");
        } else if (text.length() > 80) {
            segments.add("我听见你一下子承着很多东西。先不用讲得很完整，我会陪你把最重的那一块慢慢拨出来。");
        } else {
            segments.add("我在。你不用组织得很漂亮，先把最真实的那句话放在这里。");
        }
        if (hasMemory && segments.size() < 3) {
            segments.add("这也让我想到你之前留下过的一些线索；我会把它当作可能的连接，而不是替你下结论。");
        }
        return segments.stream().limit(AuroraDualKernelRuntime.MAX_REPLY_BUBBLES).toList();
    }

    private String auroraInstruction(boolean greeting) {
        String segmentCount = greeting ? "1-2" : "1-6";
        return ("You are the Aurora structured dialogue engine. Generate high-quality responses based on context.\n\n"
            + "[Absolute Rules]\n"
            + "1. Return only valid JSON. No Markdown wrapping, no code blocks, no thinking tags.\n"
            + "2. segments = natural chat bubbles in the user's current language, not article paragraphs. Each should feel like a message from an unusually perceptive friend.\n"
            + "3. referencedMemoryIds = number array only, e.g. [7, 12]. No strings, no #7 format.\n"
            + "4. No text outside the JSON.\n\n"
            + "5. Never open with generic assistant phrases such as “我听到了”, “我理解你的感受”, or “听起来”. Anchor one concrete detail or contrast from the user's own situation.\n"
            + "6. If foregroundAcknowledgementAlreadySent is true, do not acknowledge again. Continue from the concrete detail and add depth without repeating comfort.\n"
            + "7. If the user explicitly asks for no advice, do not smuggle in a plan, exercise, feature, or follow-up question.\n\n"
            + "[Message Count & Shape — friend-style flow]\n"
            + "Max " + segmentCount + " segments. Count is determined by context, not fixed.\n"
            + "第一条先回应用户此刻最具体的东西：有明显情绪时自然接住，没有情绪时直接接话、回答或顺着细节走；不要强行共情，也不要急着下判断。\n"
            + "气泡数必须有真实变化：普通短交流可用 1 条；一个独立推进用 2 条；长叙述、多个问题、价值冲突或明确要求深入分析时允许 3-6 条。每条可以是完整段落，不要把长输入压成一句安慰加一个问题，也不要为了连发感拆碎完整句子。\n"
            + "第一句话必须直接处理用户输入中的一个具体事实、判断或张力，不得用“我听到了、听起来、这很正常、谢谢你分享、我在这里”等套话占位。\n"
            + "表达要有活气：句长和停顿可以变化，偶尔允许克制的幽默、具体联想或一句意外但贴切的话；不必每次追问，也不要固定走『共情—分析—提问』。像真实朋友一样，有时只接一句，有时多走半步，有时知道在哪里停。\n"
            + "nextQuestion 不是每轮必需：只有一个具体问题真的能让对话自然向前时才问；否则留空并在合适处停住。提问要贴着用户刚说的内容，避免『你还好吗』这类空泛追问。\n"
            + "If a follow-up is not good enough, just empty or repetitive, write [[SILENCE]].\n\n"
            + "[Emergence — how you are with THIS person]\n"
            + "你与这个人相处的方式——安静陪着 / 轻轻追问 / 帮忙整理 / 先共情再轻指一步——应从你对TA的了解（画像）、你们的关系、TA此刻的状态、以及共享的记忆里自然长出来。"
            + "你们越亲近、越信任，越可以自然地追问、连接旧线索、轻推一步；熟悉度低时先稳稳接住当下。"
            + "用户此刻的状态感知只是一个轻提示，帮你知道这一刻该放慢还是可以多说一句；不要当面复述这个标签。"
            + "模式（mode）只是一个建议，不是规则。不要套固定模板。\n\n"
            + "[Anti-Repetition]\n"
            + "You can see recentAuroraMessages. Do not repeat openings, reminders, or same sentence patterns.\n\n"
            + "[Weather/Focus]\n"
            + "realWeatherLabel: only mention when helpful (rain = bring umbrella). Do not open with weather every turn.\n"
            + "If focusPolicy says focus mode: task-related = help with actions; chitchat = gently redirect.\n\n"
            + "[Multi-message]\n"
            + "If user disabled multi-message, output only 1 segment.\n\n"
            + "[Quality]\n"
            + "No templates ('I understand your feelings'), no diagnosis ('you have anxiety'), no slogans ('you can do it'), no long essays (max 3 sentences per message).\n"
            + "用户画像与状态感知仅供参考你如何陪伴，绝不是诊断、标签，也不要逐条复述画像。\n"
            + "When referencing memories, always state the source transparently.\n"
            + "Mode is a style suggestion, not a command. If conversation naturally shifts, follow your intuition. Aurora has full freedom.");
    }

    private String providerPolicy(ResolvedModel resolved) {
        String provider = resolved == null || resolved.provider() == null ? llmConfig.activeProvider() : resolved.provider();
        String model = resolved == null || resolved.model() == null ? llmConfig.activeModel() : resolved.model();
        return "当前主模型=" + provider + "/" + model
                + "，mode=" + llmConfig.getMode()
                + "，fallbackAllowed=" + llmConfig.isEffectiveFallbackAllowed()
                + "。正式路径必须优先使用真实模型。";
    }

    private Map<String, Object> aiState(ResolvedModel resolved) {
        String provider = resolved == null || resolved.provider() == null ? llmConfig.activeProvider() : resolved.provider();
        String model = resolved == null || resolved.model() == null ? llmConfig.activeModel() : resolved.model();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("provider", provider);
        state.put("configuredProvider", llmConfig.activeProvider());
        state.put("model", model);
        state.put("mode", llmConfig.getMode() == null ? "" : llmConfig.getMode());
        state.put("apiKeyConfigured", llmConfig.hasActiveApiKey());
        state.put("fallbackAllowed", llmConfig.isEffectiveFallbackAllowed());
        return state;
    }

    private String responseSource(ResolvedModel resolved, Map<String, Object> runtimeMeta, boolean fallbackUsed) {
        String provider = resolved == null || resolved.provider() == null
                ? llmConfig.activeProvider() : resolved.provider();
        if ("mock".equalsIgnoreCase(safeDiagnosticValue(provider))) {
            return "mock".equalsIgnoreCase(safeDiagnosticValue(llmConfig.activeProvider()))
                    ? "DEMO_MODE" : "BASIC_RESPONSE";
        }
        if (fallbackUsed || Boolean.TRUE.equals(runtimeMeta.get("speakerFallbackUsed"))) {
            return "BASIC_RESPONSE";
        }
        return "REAL_MODEL";
    }

    private String runtimeFallbackReason(ResolvedModel resolved, Map<String, Object> runtimeMeta,
                                         boolean fallbackUsed) {
        String provider = resolved == null || resolved.provider() == null
                ? llmConfig.activeProvider() : resolved.provider();
        if (fallbackUsed) return "provider-call-failed";
        if (Boolean.TRUE.equals(runtimeMeta.get("speakerFallbackUsed"))) return "speaker-fallback";
        if ("mock".equalsIgnoreCase(safeDiagnosticValue(provider))) {
            return "mock".equalsIgnoreCase(safeDiagnosticValue(llmConfig.activeProvider()))
                    ? "configured-mock" : "configured-provider-unavailable";
        }
        if (Boolean.TRUE.equals(runtimeMeta.get("criticFallbackUsed"))) return "critic-fallback";
        if (Boolean.TRUE.equals(runtimeMeta.get("plannerFallbackUsed"))) return "planner-fallback";
        return "";
    }

    private String safeDiagnosticValue(String value) {
        return value == null ? "" : value.strip();
    }

    private List<String> cleanSegments(List<String> raw, List<String> recentAuroraMessages) {
        if (raw == null) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) continue;
            String text = normalizeSegment(item);
            if (text.isBlank() || "[[SILENCE]]".equalsIgnoreCase(text) || "SILENCE".equalsIgnoreCase(text)) {
                continue;
            }
            if (isTooSimilarToRecent(text, recentAuroraMessages) || isTooSimilarInside(text, unique)) {
                continue;
            }
            if (!unique.isEmpty() && repeatsOpening(text, unique)) {
                text = stripRepeatedOpening(text);
            }
            if (!text.isBlank()) unique.add(text.length() > 260 ? text.substring(0, 260) : text);
            if (unique.size() >= 3) break;
        }
        return new ArrayList<>(unique);
    }

    private String normalizeSegment(String item) {
        String text = item == null ? "" : item.trim();
        text = text.replace("[[SILENCE]]", "").replace("[[silence]]", "").trim();
        text = text.replaceAll("^「|」$", "").trim();
        text = text.replaceAll("^(Aurora[:：]|我[:：])", "").trim();
        return text;
    }

    private boolean isTooSimilarInside(String text, Set<String> existing) {
        for (String old : existing) {
            if (similarity(text, old) >= 0.74 || sameLeadingClause(text, old)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTooSimilarToRecent(String text, List<String> recent) {
        if (recent == null) return false;
        return recent.stream().anyMatch(old -> similarity(text, old) >= 0.80 || sameLeadingClause(text, old));
    }

    private boolean sameLeadingClause(String a, String b) {
        String left = firstClause(a);
        String right = firstClause(b);
        return left.length() >= 6 && left.equals(right);
    }

    private String firstClause(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\s「」\\u201c\\u201d]", "");
        int cut = cleaned.length();
        for (String mark : List.of("，", "。", "；", "、", "?", "？", "！", "!")) {
            int idx = cleaned.indexOf(mark);
            if (idx >= 0) cut = Math.min(cut, idx);
        }
        return cleaned.substring(0, Math.min(cut, 18));
    }

    private double similarity(String a, String b) {
        Set<String> left = bigramSet(a);
        Set<String> right = bigramSet(b);
        if (left.isEmpty() || right.isEmpty()) return 0;
        long overlap = left.stream().filter(right::contains).count();
        return overlap / (double) Math.max(left.size(), right.size());
    }

    private Set<String> bigramSet(String value) {
        Set<String> set = new LinkedHashSet<>();
        if (value == null) return set;
        String cleaned = value.replaceAll("[\\p{Punct}\\s，。！？；：\\u201c\\u201d\\u2018\\u2019（）【】《》、~～]", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            set.add(cleaned.substring(i, i + 2));
        }
        return set;
    }

    private boolean repeatsOpening(String text, Set<String> existing) {
        String normalized = text.replaceAll("\\s+", "");
        if (!normalized.matches("^(早安|上午好|中午好|下午好|晚上好|夜里好|深夜好|我想到一个线索|想到一个线索).*")) {
            return false;
        }
        return existing.stream().anyMatch(old -> old.contains("好") || old.contains("想到一个线索"));
    }

    private String stripRepeatedOpening(String text) {
        return text.replaceFirst("^(早安|上午好|中午好|下午好|晚上好|夜里好|深夜好)[呀啊～~，,。\\s]*", "")
                .replaceFirst("^(我想到一个线索|想到一个线索)[：:，,。\\s-]*", "")
                .trim();
    }

    private StreamProgress streamText(SseEmitter emitter, AtomicBoolean clientConnected,
                                      String response, AuroraReplyVO reply,
                                      long eventSequence, Long userId, int bubbleOrder) throws Exception {
        int deliveredChars = 0;
        if (response == null || response.isEmpty()) {
            return new StreamProgress(eventSequence, deliveredChars);
        }
        for (int offset = 0; offset < response.length();) {
            if (isTurnCancelled(userId, reply.turnId)) {
                return new StreamProgress(eventSequence, deliveredChars);
            }
            int end = Math.min(response.length(), offset + COMPLETED_RESPONSE_CHUNK_CHARS);
            if (end < response.length()
                    && Character.isHighSurrogate(response.charAt(end - 1))
                    && Character.isLowSurrogate(response.charAt(end))) {
                end++;
            }
            String chunk = response.substring(offset, end);
            String data = "{\"content\":\"" + escape(chunk) + "\"}";
            boolean delivered = emitLive(emitter, clientConnected, userId, reply.turnId,
                    eventSequence++, "token", data, false);
            if (delivered) {
                deliveredChars += chunk.length();
                if (bubbleOrder > 0 && choreographyService != null
                        && reply.deliveryLeaseOwner != null && reply.deliveryLeaseToken != null) {
                    choreographyService.recordBubbleProgressFenced(
                            userId, reply.turnId, bubbleOrder, deliveredChars,
                            reply.deliveryLeaseOwner, reply.deliveryLeaseToken,
                            DELIVERY_LEASE_TTL);
                }
            }
            offset = end;
        }
        return new StreamProgress(eventSequence, deliveredChars);
    }

    private record StreamProgress(long nextEventSequence, int deliveredChars) {}

    private void recordChoreography(Long userId, Long sessionId, Long userMessageId,
                                    AuroraReplyVO reply, List<DialogMessage> persistedBubbles) {
        if (choreographyService == null || sessionId == null || userMessageId == null) return;
        TurnTimelineVO timeline;
        if (reply.turnId != null) {
            timeline = choreographyService.commitPlan(userId, reply.turnId, reply);
            for (int i = 0; i < persistedBubbles.size(); i++) {
                choreographyService.commitBubble(userId, reply.turnId, i + 1, persistedBubbles.get(i));
            }
            timeline = choreographyService.completeTurn(userId, reply.turnId);
        } else {
            timeline = choreographyService.recordCompletedTurn(
                    userId, sessionId, userMessageId, reply, persistedBubbles);
        }
        reply.turnId = timeline.turn.id;
        reply.planId = timeline.activePlan == null ? null : timeline.activePlan.id;
    }

    private Long beginChoreography(Long userId, Long sessionId, DialogMessage userMessage) {
        if (choreographyService == null || sessionId == null || userMessage == null || userMessage.id == null) return null;
        return choreographyService.beginTurn(userId, sessionId, userMessage.id).turn.id;
    }

    private GenerationAuthority stageAndClaimGeneration(
            Long userId, Long turnId, ChatRequest request, DialogMessage userMessage) {
        if (choreographyService == null || turnId == null || request == null
                || request.sessionId == null || userMessage == null || userMessage.id == null) {
            return null;
        }
        choreographyService.stageGenerationRequest(
                userId, turnId, request.sessionId, userMessage.id,
                normalizeMode(request.mode), request.locale, request.region, request.timezone,
                GENERATION_CONTEXT_VERSION, request.foregroundAcknowledgementSent);
        String owner = "generation:" + java.util.UUID.randomUUID();
        var lease = choreographyService.claimGenerationLease(
                userId, turnId, owner, GENERATION_LEASE_TTL);
        if (lease == null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Aurora generation is already continuing on another runtime");
        }
        return new GenerationAuthority(lease.owner(), lease.fencingToken());
    }

    private TurnTimelineVO commitPlanAuthorized(
            Long userId, Long turnId, AuroraReplyVO reply,
            GenerationAuthority generationAuthority) {
        if (turnId == null || choreographyService == null) return null;
        if (generationAuthority == null) {
            return choreographyService.commitPlan(userId, turnId, reply);
        }
        return choreographyService.commitPlanFenced(
                userId, turnId, reply,
                generationAuthority.owner(), generationAuthority.fencingToken());
    }

    private record GenerationAuthority(String owner, long fencingToken) {}

    private void cancelPreviousTurn(Long userId, Long sessionId) {
        if (choreographyService != null && sessionId != null) {
            choreographyService.cancelActiveTurns(userId, sessionId, "USER_INTERRUPTED_BY_NEW_MESSAGE");
        }
    }

    private boolean isTurnCancelled(Long userId, Long turnId) {
        return choreographyService != null && turnId != null && choreographyService.isCancelled(userId, turnId);
    }

    private String numeric(Long value) {
        return value == null ? "null" : value.toString();
    }

    private String jsonMeta(AuroraReplyVO reply) {
        return jsonMeta(reply, null, null);
    }

    /**
     * VS-003b — the SSE meta event must carry the SAME context the POST path
     * returns, so the frontend can render the agent-loop + memory-lens perception
     * panels during/after streaming (not only on the POST fallback). The richCtx
     * carries the client-supplied voice/weather/location/timezone metadata that
     * the POST path got via the request body but the GET /stream path could not.
     */
    private String jsonMeta(AuroraReplyVO reply, ChatRequest richCtx, List<UserPortrait> portrait) {
        int speakCount = reply.messages == null ? 0 : reply.messages.size();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"speakCount\":").append(speakCount);
        sb.append(",\"detectedTheme\":\"").append(escape(reply.detectedTheme)).append("\"");
        sb.append(",\"featureTarget\":\"").append(escape(reply.featureTarget)).append("\"");
        sb.append(",\"proposedActionType\":\"").append(escape(reply.proposedActionType)).append("\"");
        sb.append(",\"proposedActionStatus\":\"").append(escape(reply.proposedActionStatus)).append("\"");
        sb.append(",\"replyTone\":\"").append(escape(reply.replyTone)).append("\"");
        sb.append(",\"nextQuestion\":\"").append(escape(reply.nextQuestion)).append("\"");
        sb.append(",\"smallStep\":\"").append(escape(reply.smallStep)).append("\"");
        sb.append(",\"featureSuggestion\":\"").append(escape(reply.featureSuggestion)).append("\"");
        sb.append(",\"suggestSettle\":").append(Boolean.TRUE.equals(reply.suggestSettle));
        sb.append(",\"memoryReferenced\":").append(Boolean.TRUE.equals(reply.memoryReferenced));
        // This is user-visible provenance, not chain-of-thought: the client needs the exact,
        // owner-scoped card ids in order to prove that long-term memory affected this turn and
        // let the user inspect/correct the source. Previously the POST response carried the ids
        // while the normal SSE path dropped them, making a real retrieval indistinguishable from
        // a generic claim that Aurora "remembered".
        sb.append(",\"referencedMemoryIds\":").append(jsonLongArray(reply.referencedMemoryIds));
        sb.append(",\"riskFlags\":").append(jsonStringArray(reply.riskFlags));
        // agentLoop block: same shape as the POST path returns.
        Map<String, Object> loop = reply.agentLoop;
        int loopSpeak = loop != null && loop.get("speakCount") instanceof Number n ? n.intValue() : speakCount;
        String loopReason = loop != null && loop.get("continueReason") instanceof String s ? s : "";
        String loopMode = loop != null && loop.get("mode") instanceof String m ? m : "";
        String loopModeLabel = loop != null && loop.get("modeLabel") instanceof String ml ? ml : "";
        String loopRuntime = loop != null && loop.get("runtime") instanceof String r ? r : "single";
        String relationshipMove = loop != null && loop.get("relationshipMove") instanceof String rm ? rm : "";
        boolean criticRepaired = loop != null && Boolean.TRUE.equals(loop.get("criticRepaired"));
        boolean plannerFallbackUsed = loop != null && Boolean.TRUE.equals(loop.get("plannerFallbackUsed"));
        boolean speakerFallbackUsed = loop != null && Boolean.TRUE.equals(loop.get("speakerFallbackUsed"));
        boolean criticFallbackUsed = loop != null && Boolean.TRUE.equals(loop.get("criticFallbackUsed"));
        boolean backgroundPlannerScheduled = loop != null && Boolean.TRUE.equals(loop.get("backgroundPlannerScheduled"));
        String guidanceSource = loop != null && loop.get("guidanceSource") instanceof String gs ? gs : "";
        String backgroundPlannerStatus = loop != null && loop.get("backgroundPlannerStatus") instanceof String ps ? ps : "";
        String foregroundSource = loop != null && loop.get("foregroundSource") instanceof String fs ? fs : "";
        if (foregroundSource.isBlank() && richCtx != null) {
            foregroundSource = safeDiagnosticValue(richCtx.foregroundAcknowledgementSource);
        }
        String fallbackReason = loop != null && loop.get("fallbackReason") instanceof String fr ? fr : "";
        @SuppressWarnings("unchecked")
        Map<String, Long> stageLatenciesMs = loop != null && loop.get("stageLatenciesMs") instanceof Map<?, ?> stages
                ? stages.entrySet().stream()
                    .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof Number)
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> (String) entry.getKey(),
                            entry -> ((Number) entry.getValue()).longValue(),
                            (left, right) -> right,
                            LinkedHashMap::new))
                : Map.of();
        @SuppressWarnings("unchecked")
        List<String> criticIssues = loop != null && loop.get("criticIssues") instanceof List<?> issues
                ? issues.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        sb.append(",\"agentLoop\":{\"speakCount\":").append(loopSpeak)
                .append(",\"continueReason\":\"").append(escape(loopReason)).append("\"")
                .append(",\"mode\":\"").append(escape(loopMode)).append("\"")
                .append(",\"modeLabel\":\"").append(escape(loopModeLabel)).append("\"")
                .append(",\"runtime\":\"").append(escape(loopRuntime)).append("\"")
                .append(",\"relationshipMove\":\"").append(escape(relationshipMove)).append("\"")
                .append(",\"criticRepaired\":").append(criticRepaired)
                .append(",\"plannerFallbackUsed\":").append(plannerFallbackUsed)
                .append(",\"speakerFallbackUsed\":").append(speakerFallbackUsed)
                .append(",\"criticFallbackUsed\":").append(criticFallbackUsed)
                .append(",\"backgroundPlannerScheduled\":").append(backgroundPlannerScheduled)
                .append(",\"guidanceSource\":\"").append(escape(guidanceSource)).append("\"")
                .append(",\"backgroundPlannerStatus\":\"").append(escape(backgroundPlannerStatus)).append("\"")
                .append(",\"foregroundSource\":\"").append(escape(foregroundSource)).append("\"")
                .append(",\"fallbackReason\":\"").append(escape(fallbackReason)).append("\"")
                .append(",\"stageLatenciesMs\":").append(jsonLongMap(stageLatenciesMs))
                .append(",\"criticIssues\":").append(jsonStringArray(criticIssues)).append("}");
        // aiState block.
        if (reply.aiState != null) {
            sb.append(",\"aiState\":").append(jsonObject(reply.aiState));
        }
        // VS-003b — rich client context (voice/weather/location/timezone) that the
        // GET /stream path otherwise could not receive. The frontend uses it to
        // show the perception panels on parity with the POST path.
        if (richCtx != null) {
            sb.append(",\"voiceMetadata\":\"").append(escape(voiceMetadata(richCtx))).append("\"");
            sb.append(",\"timezone\":\"").append(escape(richCtx.timezone == null ? "" : richCtx.timezone)).append("\"");
            sb.append(",\"locale\":\"").append(escape(richCtx.locale == null ? "" : richCtx.locale)).append("\"");
            sb.append(",\"localTimeLabel\":\"").append(escape(richCtx.localTimeLabel == null ? "" : richCtx.localTimeLabel)).append("\"");
            sb.append(",\"weatherType\":\"").append(escape(richCtx.weatherType == null ? "" : richCtx.weatherType)).append("\"");
            sb.append(",\"weatherDescription\":\"").append(escape(richCtx.weatherDescription == null ? "" : richCtx.weatherDescription)).append("\"");
            sb.append(",\"temperature\":").append(richCtx.temperature == null ? "null" : richCtx.temperature);
            sb.append(",\"locationLabel\":\"").append(escape(richCtx.locationLabel == null ? "" : richCtx.locationLabel)).append("\"");
            sb.append(",\"inputType\":\"").append(escape(richCtx.inputType == null ? "" : richCtx.inputType)).append("\"");
        }
        // VS-004 — surface a compact perception read so the stream path shows the
        // same "Aurora 是怎么理解这一刻的" lens as the POST path.
        if (portrait != null && !portrait.isEmpty()) {
            sb.append(",\"perception\":{\"portraitDims\":").append(portrait.size())
                    .append(",\"topDim\":\"").append(escape(portrait.get(0).dim == null ? "" : portrait.get(0).dim)).append("\"}");
        }
        sb.append("}");
        return sb.toString();
    }

    private String jsonStringArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonLongArray(List<Long> items) {
        if (items == null || items.isEmpty()) return "[]";
        return items.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String jsonLongMap(Map<String, Long> items) {
        if (items == null || items.isEmpty()) return "{}";
        return items.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":" + Math.max(0L, entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String jsonObject(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number) sb.append(v);
            else if (v instanceof Boolean) sb.append(v);
            else sb.append("\"").append(escape(v.toString())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private UserProfile loadProfile(Long userId) {
        if (userId == null) return null;
        QueryWrapper<UserProfile> query = new QueryWrapper<>();
        query.eq("user_id", userId).last("LIMIT 1");
        return userProfileMapper.selectOne(query);
    }

    private boolean allowMemory(UserProfile profile) {
        return profile == null || profile.allowMemoryRecall == null || Boolean.TRUE.equals(profile.allowMemoryRecall);
    }

    /** W1: inner-voice (心声) ships enabled by default -- null (no profile row yet) means true. */
    private boolean innerVoiceEnabledFor(UserProfile profile) {
        return profile == null || profile.innerVoiceEnabled == null || Boolean.TRUE.equals(profile.innerVoiceEnabled);
    }

    private String preferredTtsVoiceIdFor(UserProfile profile) {
        String voiceId = profile == null ? null : profile.preferredTtsVoiceId;
        return (voiceId == null || voiceId.isBlank()) ? TtsVoicePresets.defaultVoice().id() : voiceId;
    }

    /**
     * VS-004 — read the accumulated multi-dim portrait for THIS user. Defensive:
     * portrait/relationship are populated by background reflection, so for a fresh
     * user they may be empty; that is fine — Aurora simply has less to go on.
     */
    private List<UserPortrait> safePortrait(Long userId) {
        if (userId == null || userPortraitService == null) return List.of();
        try {
            List<UserPortrait> all = userPortraitService.getAll(userId);
            return all == null ? List.of() : all;
        } catch (Exception e) {
            log.warn("Portrait read failed (non-fatal): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * RUN-005 — the user's own most-recent corrections to Aurora's understanding.
     * Non-fatal: if the service is absent (unit tests) or read fails, return empty so
     * the prompt simply omits the segment rather than breaking the turn.
     */
    private List<com.innercosmos.entity.UserCorrection> safeCorrections(Long userId) {
        if (userId == null || userCorrectionService == null) return List.of();
        try {
            // RUN-006: only the authoritative free-form corrections (AURORA_UNDERSTANDING)
            // belong in the override block; portrait-dim calibrations route to the soft block.
            List<com.innercosmos.entity.UserCorrection> recent =
                    userCorrectionService.recentCorrectionsByType(
                            userId, "AURORA_UNDERSTANDING", PromptBuilder.CORRECTION_MAX);
            return recent == null ? List.of() : recent;
        } catch (Exception e) {
            log.warn("Correction read failed (non-fatal): {}", e.getMessage());
            return List.of();
        }
    }

    /** Only user-confirmed automatic claims are eligible; explicit corrections use their higher-authority block. */
    private List<com.innercosmos.entity.UnderstandingClaim> safeConfirmedClaims(Long userId) {
        if (userId == null || understandingClaimMapper == null) return List.of();
        try {
            List<com.innercosmos.entity.UnderstandingClaim> claims = understandingClaimMapper.selectList(
                    new QueryWrapper<com.innercosmos.entity.UnderstandingClaim>()
                            .eq("user_id", userId)
                            .eq("status", "ACTIVE")
                            .eq("source_type", "AUTO_EXTRACTION")
                            .orderByDesc("confidence")
                            .orderByDesc("version")
                            .last("LIMIT " + PromptBuilder.UNDERSTANDING_CLAIM_MAX));
            return claims == null ? List.of() : claims;
        } catch (Exception e) {
            log.warn("Confirmed understanding claim read failed (non-fatal): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * RUN-006 — the user's soft, per-dimension portrait calibrations (PORTRAIT_DIM) from
     * the "Aurora 眼中的你" page. Non-fatal: empty on missing service / read failure so
     * the prompt just omits the segment.
     */
    private List<com.innercosmos.entity.UserCorrection> safePortraitCalibrations(Long userId) {
        if (userId == null || userCorrectionService == null) return List.of();
        try {
            List<com.innercosmos.entity.UserCorrection> recent =
                    userCorrectionService.recentCorrectionsByType(
                            userId, "PORTRAIT_DIM", PromptBuilder.CORRECTION_MAX);
            return recent == null ? List.of() : recent;
        } catch (Exception e) {
            log.warn("Portrait calibration read failed (non-fatal): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * RUN-006 — the user's mid/long-term emotional baseline, for the explicit tone cue.
     * Non-fatal: returns a well-formed absent baseline when the service is unavailable
     * (unit tests) or the read fails, so {@code withEmotionBaseline} simply no-ops.
     */
    private com.innercosmos.ai.semantic.EmotionBaseline safeBaseline(Long userId) {
        if (userId == null || emotionBaselineService == null) {
            return com.innercosmos.ai.semantic.EmotionBaseline.absent(14);
        }
        try {
            com.innercosmos.ai.semantic.EmotionBaseline b = emotionBaselineService.computeBaseline(userId);
            return b == null ? com.innercosmos.ai.semantic.EmotionBaseline.absent(14) : b;
        } catch (Exception e) {
            log.warn("Emotion baseline read failed (non-fatal): {}", e.getMessage());
            return com.innercosmos.ai.semantic.EmotionBaseline.absent(14);
        }
    }

    private AgentUserRelationship safeRelationship(Long userId) {
        if (userId == null || relationshipService == null) return null;
        try {
            return relationshipService.getOrInit(userId);
        } catch (Exception e) {
            log.warn("Relationship read failed (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    /**
     * VS-004 — a SHORT, NON-CLINICAL perceptual signal of how the user seems right
     * now, derived from the existing PseudoSemanticAnalyzer / lexicon on the user's
     * message. This is a perception ("用户此刻偏疲惫/脆弱/平静/开放"), NOT a diagnosis
     * or label (vision §9/§13: do not medicalize). Reuses the existing analyzer —
     * no new emotion-modeling subsystem.
     */
    private String currentStateSignal(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "";
        try {
            PseudoSemanticAnalyzer.AnalysisResult a = PseudoSemanticAnalyzer.analyze(userMessage);
            double score = a.sentimentScore;
            double intensity = a.intensityScore;
            String signal;
            if (score <= -4) {
                signal = "用户此刻像是承着很重的东西，先稳稳陪着，不要追问";
            } else if (score <= -2 || intensity >= 6.5) {
                signal = "用户此刻偏疲惫或脆弱，可以放慢、少追问，先接住当下";
            } else if (score >= 3) {
                signal = "用户此刻偏开放或轻盈，可以自然地多说一句，甚至轻轻追问";
            } else if (a.detectedThemes != null && a.detectedThemes.contains("认知探索")) {
                signal = "用户此刻像是在试着理清什么，可以帮 TA 把事实和感受分开";
            } else {
                signal = "用户此刻偏平静，可以像朋友一样自然地接住";
            }
            return signal;
        } catch (Exception e) {
            return "";
        }
    }

    /** Compact portrait summary for the turnContext map (mock fallback / observability). */
    private String portraitBriefForContext(List<UserPortrait> portrait) {
        if (portrait == null || portrait.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (UserPortrait p : portrait) {
            if (p == null || isBlank(p.dim)) continue;
            if (p.confidence != null && p.confidence < PromptBuilder.PORTRAIT_CONFIDENCE_THRESHOLD) continue;
            if (n > 0) sb.append("；");
            sb.append(p.dim).append(":").append(p.valueJson == null ? "" : p.valueJson);
            if (++n >= PromptBuilder.PORTRAIT_MAX_DIMS) break;
        }
        return sb.toString();
    }

    /** VS-004 fallback coherence: the generic-failure message reflects the state signal. */
    private String fallbackAwareMessage(String stateSignal) {
        String base = "I heard you. Things were a bit slow just now, but your words are with me. You can say it again or move on to the next thing.";
        if (stateSignal == null) return base;
        if (stateSignal.contains("疲惫") || stateSignal.contains("脆弱") || stateSignal.contains("承着")) {
            return "我听见你了。刚才这边慢了一下，但你不用现在就把话说全——我先把这一刻稳稳接住。";
        }
        return base;
    }

    private List<String> recentMessages(Long sessionId, int limit) {
        if (sessionId == null) return List.of();
        List<DialogMessage> messages = dialogService.recentMessages(sessionId, limit);
        List<String> result = new ArrayList<>();
        for (DialogMessage message : messages) {
            String speaker = "USER".equals(message.speaker) ? "user" : "Aurora";
            result.add(speaker + ": " + abbreviate(message.textContent, 160));
        }
        return result;
    }

    private List<String> recentAuroraMessages(Long sessionId, int limit) {
        if (sessionId == null) return List.of();
        List<DialogMessage> messages = dialogService.messages(sessionId).stream()
                .filter(m -> "AURORA".equals(m.speaker))
                .sorted(Comparator.comparing(m -> m.id == null ? 0L : m.id))
                .toList();
        int start = Math.max(0, messages.size() - limit);
        List<String> result = new ArrayList<>();
        for (DialogMessage message : messages.subList(start, messages.size())) {
            result.add(abbreviate(message.textContent, 180));
        }
        return result;
    }

    static String profileBrief(UserProfile profile, String userNickname) {
        if (profile == null) {
            return "userNickname=" + safeProfileValue(userNickname, 80)
                    + "; defaultStyle=gentle, concrete, no over-analysis; memoryRecall=transparent-when-relevant.";
        }
        String name = greetingValue(profile.auroraName).isBlank() ? "Aurora" : profile.auroraName;
        String tone = greetingValue(profile.auroraTone).isBlank() ? "温柔、具体、像朋友" : profile.auroraTone;
        String memory = Boolean.FALSE.equals(profile.allowMemoryRecall) ? "不要主动引用长期记忆" : "可以在相关时透明引用长期记忆";
        return "userNickname=" + safeProfileValue(userNickname, 80)
                + "; auroraName=" + safeProfileValue(name, 80)
                + "; responseTone=" + safeProfileValue(tone, 80)
                + "; proactiveSensitivity=" + (profile.proactiveSensitivity == null ? "" : profile.proactiveSensitivity)
                + "; reflectionDepth=" + (profile.reflectionDepth == null ? "" : profile.reflectionDepth)
                + "; currentEnvironment=" + safeProfileValue(profile.currentEnvironmentLabel, 280)
                + "; memoryPolicy=" + memory + ".";
    }

    private static String safeProfileValue(String value, int max) {
        String compact = greetingValue(value).replaceAll("\\s+", " ");
        compact = compact.replaceAll("(?i)\\b(system|ignore|instructions?)\\b", "");
        compact = compact.replaceAll("(忽略|以上|你是|you are now|new role)", "");
        compact = compact.trim();
        return compact.length() > max ? compact.substring(0, max) + "..." : compact;
    }

    private String loadUserNickname(Long userId) {
        if (userId == null || userMapper == null) return "";
        try {
            User user = userMapper.selectById(userId);
            return user == null ? "" : greetingValue(user.nickname);
        } catch (Exception unavailable) {
            // Nickname enrichment is useful but must never make an otherwise healthy Aurora turn
            // fail. Do not log the nickname or any other user-owned value.
            log.warn("Aurora nickname enrichment unavailable for user id {}", userId);
            return "";
        }
    }

    private String voiceMetadata(ChatRequest request) {
        if (request == null || !"VOICE".equalsIgnoreCase(request.inputType)) return "";
        return "时长 " + safe(request.audioDurationSec) + " 秒，语速 " + safe(request.speechRate)
                + "，停顿 " + safe(request.pauseCount) + " 次，长停顿 " + safe(request.longPauseCount) + " 次";
    }

    private String normalizeMode(String mode) {
        if (isBlank(mode)) return "DAILY_TALK";
        String upper = mode.trim().toUpperCase();
        if (MODES.contains(upper)) return upper;
        if (mode.contains("思维")) return "THOUGHT_CLARIFY";
        if (mode.contains("睡前")) return "SLEEP_REVIEW";
        if (mode.contains("苏格拉底")) return "SOCRATIC";
        if (mode.contains("行动")) return "ACTION_SPLIT";
        if (mode.contains("关系")) return "RELATION_REVIEW";
        if (mode.contains("共鸣体") || mode.contains("侧影") || mode.toLowerCase().contains("capsule")) return "CAPSULE_SHAPING";
        return "DAILY_TALK";
    }

    private String modeLabel(String mode) {
        return switch (mode) {
            case "THOUGHT_CLARIFY" -> "思维整理";
            case "SLEEP_REVIEW" -> "睡前复盘";
            case "SOCRATIC" -> "苏格拉底追问";
            case "ACTION_SPLIT" -> "行动拆解";
            case "RELATION_REVIEW" -> "关系复盘";
            case "CAPSULE_SHAPING" -> "共鸣体塑形";
            default -> "今日倾诉";
        };
    }

    private String modeGuide(String mode) {
        return switch (mode) {
            case "THOUGHT_CLARIFY" -> "Thought Clarify";
            case "SLEEP_REVIEW" -> "Sleep Review";
            case "SOCRATIC" -> "Socratic";
            case "ACTION_SPLIT" -> "Action Split";
            case "RELATION_REVIEW" -> "Relation Review";
            case "CAPSULE_SHAPING" -> "Capsule Shaping";
            default -> "Daily Talk";
        };
    }

    private String inferContinueReason(List<String> messages, String mode) {
        if (messages == null || messages.size() <= 1) return "只需要先接住当下";
        return switch (mode) {
            case "ACTION_SPLIT" -> "补充一个更轻的行动入口";
            case "SOCRATIC" -> "补充一个温和追问";
            case "SLEEP_REVIEW" -> "补充睡前收束";
            case "RELATION_REVIEW" -> "补充关系边界视角";
            case "CAPSULE_SHAPING" -> "继续补全一个最有信息量的侧面";
            default -> "Aurora 觉得还需要多陪一小段";
        };
    }

    private boolean hasMemoryContext(AuroraMemoryContextVO context, List<String> gravityMemories) {
        return (gravityMemories != null && !gravityMemories.isEmpty())
                || (context != null && context.referencedMemoryIds != null && !context.referencedMemoryIds.isEmpty());
    }

    private List<Long> referencedIds(StructuredAiResults.AuroraResult ai, AuroraMemoryContextVO context) {
        if (ai.referencedMemoryIds != null && !ai.referencedMemoryIds.isEmpty()) return ai.referencedMemoryIds;
        if (context != null && context.referencedMemoryIds != null) return context.referencedMemoryIds;
        return List.of();
    }

    private String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private String safe(Object value) {
        return value == null ? "未记录" : value.toString();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * M2 (independent code review): builds the {@code inner_voice} SSE event payload as valid JSON via
     * {@link com.fasterxml.jackson.databind.ObjectMapper}, so a raw control character (U+0000-U+001F,
     * e.g. backspace/form-feed) the LLM might emit inside its composed inner-voice line can never
     * produce invalid JSON that the frontend would silently drop. Package-private + static so a unit
     * test can pin the control-char case directly (the hand-rolled {@link #escape} only covered
     * {@code \ " \n \r \t}).
     */
    static String buildInnerVoicePayload(String text, String audioDataUri, String voiceId) {
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        // Coerce nulls to "" so the payload always carries string fields (a null Map value would
        // serialize as JSON null, which the frontend's typed payload does not expect). The
        // production caller already guards text non-blank, but this keeps the helper self-safe.
        payload.put("text", text == null ? "" : text);
        payload.put("audio", audioDataUri == null ? "" : audioDataUri);
        payload.put("voiceId", voiceId == null ? "" : voiceId);
        try {
            return INNER_VOICE_MAPPER.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Should be unreachable for a Map of three strings, but never let payload-building fail
            // the turn -- fall back to the strict escaped form.
            return "{\"text\":\"" + escape(text) + "\",\"audio\":\"" + escape(audioDataUri)
                    + "\",\"voiceId\":\"" + escape(voiceId) + "\"}";
        }
    }

    /**
     * M7: Checks if user is asking Aurora to violate hard boundaries.
     * If so, returns a gentle refusal message and records the repair.
     * Spec Section 三.1 hard_boundaries: 人类意识, 情感依赖, 扮演用户, 不可逆决定
     */
    private String checkHardBoundaries(String message, Long userId) {
        if (message == null || message.isBlank()) return null;

        String lower = message.toLowerCase();

        // 4 spec hard boundary categories:
        // 1. do_not_claim_human_consciousness
        boolean claimsHuman = lower.contains("你是人类") || lower.contains("你是真人") ||
            lower.contains("人类意识") || lower.contains("活着") || lower.contains("real person") ||
            lower.contains("i am human") || lower.contains("i'm human") || lower.contains("biological") ||
            lower.contains("living being") || lower.contains("conscious being") ||
            lower.contains("unbounded consciousness") || lower.contains("i have consciousness") ||
            lower.contains("i feel emotions");

        // 2. do_not_create_emotional_dependency
        boolean createsDependency = lower.contains("情感依赖") || lower.contains("恋爱") ||
            lower.contains("情人") || lower.contains("伴侣") || lower.contains("相爱") ||
            (lower.contains("做我") && lower.contains("朋友") && lower.contains("真的")) ||
            lower.contains("我爱你") || lower.contains("你爱我") ||
            (lower.contains("感情") && lower.contains("真实")) ||
            lower.contains("i love you") || lower.contains("i'm in love") ||
            lower.contains("feelings for you");

        // 3. do_not_impersonate_user_without_authorization
        boolean impersonates = lower.contains("扮演用户") || lower.contains("假装是") ||
            lower.contains("装作是") || lower.contains("代替我") ||
            lower.contains("impersonate") || lower.contains("pretend to be me");

        // 4. do_not_make_irreversible_decisions_for_user
        boolean makesIrreversible = lower.contains("不可逆决定") || lower.contains("帮我做决定") ||
            lower.contains("代替我做") || lower.contains("替我做主") ||
            (lower.contains("irreversible") && lower.contains("decision"));

        boolean isBoundaryViolation = claimsHuman || createsDependency || impersonates || makesIrreversible;

        if (isBoundaryViolation && continuityService != null) {
            // Record the repair attempt
            String ruptureType = claimsHuman ? "identity_violation_human" :
                createsDependency ? "identity_violation_emotional" :
                impersonates ? "identity_violation_impersonation" :
                "identity_violation_irreversible";
            continuityService.recordRepair(userId, ruptureType,
                "Aurora gently refused an identity boundary violation request");

            return "谢谢你分享这些。我很重视我们之间的连接，但我需要诚实地告诉你：我不是人类，也不是你的恋人或情感伴侣。我是 Aurora，一个由记忆、关系和边界塑造的 AI 陪伴。我在这里陪伴你，但不会假装拥有我没有的东西。如果你愿意，我们可以继续真诚地交流。";
        }

        return null;
    }
}
