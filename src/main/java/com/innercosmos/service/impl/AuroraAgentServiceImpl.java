package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.goodbye.GoodbyeOrchestrator;
import com.innercosmos.ai.goodbye.GoodbyeTriggerDetector;
import com.innercosmos.ai.mode.ModeRegistry;
import com.innercosmos.ai.mode.ModeStrategy;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.prompt.PromptBuilder;
import com.innercosmos.ai.router.ResolvedModel;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.ai.portrait.PortraitReflectionService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.AuroraMemoryContextService;
import com.innercosmos.service.AuroraSelfContinuityService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.RhythmGuardService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.AuroraMemoryContextVO;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.SafetyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class AuroraAgentServiceImpl implements AuroraAgentService {
    private static final Logger log = LoggerFactory.getLogger(AuroraAgentServiceImpl.class);
    private static final List<String> MODES = List.of(
            "DAILY_TALK", "THOUGHT_CLARIFY", "SLEEP_REVIEW", "SOCRATIC", "ACTION_SPLIT", "RELATION_REVIEW"
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
    private com.innercosmos.service.EmotionBaselineService emotionBaselineService;
    private final Map<Long, Integer> turnCounter = new ConcurrentHashMap<>();
    private final Map<Long, Integer> goodbyeConfirmCount = new ConcurrentHashMap<>();
    /**
     * VS-003b — staging cache for rich SSE context. The browser opens the SSE
     * stream via a GET EventSource, which cannot carry a JSON body. The frontend
     * first POSTs the rich context (voice/weather/location/timezone) to
     * /stream-stage, then opens the GET stream with the returned token. The token
     * is consumed once and expires within {@link #STREAM_STAGE_TTL_MS}.
     */
    private final Map<String, ChatRequest> streamStage = new ConcurrentHashMap<>();
    private static final long STREAM_STAGE_TTL_MS = 60_000L;

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
        // SAFETY FIRST (VS-003 §1): synchronous safety gate before any model call.
        // recheckSync for distress-bearing messages also completes here, synchronously.
        SafetyResult safety = safetyService.check(request.message, userId, request.sessionId);
        dialogService.saveUserMessage(userId, request);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            return blockedReply(userId, request, safety);
        }
        return produceReply(userId, request, safety);
    }

    /**
     * Shared reply-production path used by both the POST (replyRich) and the SSE
     * (stream) entrypoints. The synchronous safety gate has ALREADY run by the time
     * this is called — do not re-run it here. Saves Aurora's messages and runs the
     * portrait/goodbye post-hooks exactly as before.
     */
    private AuroraReplyVO produceReply(Long userId, ChatRequest request, SafetyResult safety) {
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
            dialogService.saveAuroraMessage(userId, request.sessionId, boundaryRefusal);
            return vo;
        }

        UserProfile profile = loadProfile(userId);
        String mode = normalizeMode(request.mode);
        boolean allowMemory = allowMemory(profile);
        AgentContext agentContext = agentContextAssembler.assemble(
                userId, request.sessionId, request.message, allowMemory,
                request.latitude, request.longitude);
        List<String> gravityMemories = allowMemory ? memoryService.topGravitySummaries(userId, 5) : List.of();
        AuroraMemoryContextVO memoryContext = allowMemory
                ? memoryContextService.buildContext(userId, request.sessionId, request.message, 8, 6)
                : null;
        DialogSession session = request.sessionId == null ? null : sessionMapper.selectById(request.sessionId);
        String rhythm = rhythmGuardService.checkRhythm(userId, request.sessionId);
        ResolvedModel resolved = modelRouter.resolve(userId, request.sessionId);
        ModeStrategy modeStrategy = modeRegistry.get(mode);

        // VS-004 — feed Aurora's understanding of THIS user + the relationship + a
        // lightweight current-state read into the prompt, so the response style
        // emerges from them instead of being mode-driven.
        List<UserPortrait> portrait = safePortrait(userId);
        AgentUserRelationship relationship = safeRelationship(userId);
        String stateSignal = currentStateSignal(request.message);
        com.innercosmos.ai.semantic.EmotionBaseline baseline = safeBaseline(userId);

        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withConversationMode(mode)
                .withModeSegment(modeStrategy)
                .withUserProfile(profileBrief(profile))
                .withUserPortrait(portrait)
                .withUserCorrections(safeCorrections(userId))
                .withPortraitCalibrations(safePortraitCalibrations(userId))
                .withRelationship(relationship)
                .withCurrentStateSignal(stateSignal)
                .withMomentEmotion(agentContext.momentEmotionLabel)
                .withEmotionBaseline(baseline.baselineLabel, baseline.stabilityScore)
                .withSummaryAnchor(session == null ? null : session.summaryAnchor)
                .withRecentMessages(recentMessages(request.sessionId, 8))
                .withGravityMemories(gravityMemories)
                .withMemoryContext(memoryContext)
                .withRhythmAdvice(rhythm)
                .withVoiceMetadata(voiceMetadata(request))
                .withUserInput(request.message)
                .withOutputSchema()
                .build();

        Map<String, Object> turnContext = new LinkedHashMap<>();
        turnContext.put("auroraPrompt", prompt);
        turnContext.put("userMessage", request.message == null ? "" : request.message);
        turnContext.put("mode", mode);
        turnContext.put("modeGuide", modeGuide(mode));
        turnContext.put("userPortrait", portraitBriefForContext(portrait));
        turnContext.put("relationship", relationship == null ? "" : relationship.toPromptString());
        turnContext.put("relationshipStageLabel",
                relationship == null ? "" : AgentUserRelationshipService.stageLabel(relationship.relationshipStage));
        turnContext.put("currentStateSignal", stateSignal);
        turnContext.put("memoryRecallAllowed", allowMemory);
        turnContext.put("unifiedAgentContext", agentContext);
        turnContext.put("realWeatherLabel", agentContext.realWeatherLabel);
        turnContext.put("cityLabel", agentContext.cityLabel);
        turnContext.put("preferredProvider", resolved.provider());
        turnContext.put("recentAuroraMessages", recentAuroraMessages(request.sessionId, 6));
        turnContext.put("providerPolicy", providerPolicy(resolved));
        turnContext.put("agentLoopPolicy", Boolean.FALSE.equals(agentContext.multiMessageAllowed)
                ? "用户关闭了多条消息，本轮只能输出 1 条 segments。"
                : "你可以选择只说一条，也可以继续补充第二条或第三条。若某个后续想法不值得说，写 [[SILENCE]]，系统不会展示。不要固定数量。");

        AuroraReplyVO vo;
        try {
            StructuredAiResults.AuroraResult ai = callWithRetry(userId, mode, turnContext, resolved, request, gravityMemories, memoryContext, allowMemory, stateSignal);
            vo = toReply(profile, ai, request, mode, memoryContext, gravityMemories, allowMemory);
            vo = sanitizeLlmOutput(vo, userId);
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
            log.error("Aurora agent call failed after retries: {}", e.getMessage(), e);
            vo = differentiatedFallback(e, request.message, mode, stateSignal);
        }
        // Tag the response with the resolved provider/model for the UI
        vo.aiState = aiState(resolved);
        for (String msg : vo.messages) {
            dialogService.saveAuroraMessage(userId, request.sessionId, msg);
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

        return vo;
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
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> log.warn("Aurora stream client error: {}", String.valueOf(throwable.getMessage())));

        // VS-003 §1 — SAFETY FIRST, synchronously, before ANY chat token streams.
        // recheckSync for distress-bearing messages also completes here. Crisis must
        // never stream as free-form consolation (vision §8.5).
        SafetyResult safety;
        try {
            safety = safetyService.check(message, userId, sessionId);
        } catch (Exception e) {
            log.error("Aurora stream safety check failed: {}", e.getMessage(), e);
            sendOnce(emitter, "error", "{\"message\":\"safety check failed\"}");
            completeQuietly(emitter);
            return emitter;
        }
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            // Emit ONE safety/resource event asynchronously (uniform SSE contract).
            // Do NOT stream chat — crisis must never arrive as free-form consolation.
            aiExecutor.execute(() -> {
                saveUserAndBlockedAurora(userId, sessionId, message, mode, safety);
                sendOnce(emitter, "safety", jsonSafety(safety));
                sendOnce(emitter, "done", "{\"message\":\"done\"}");
                completeQuietly(emitter);
            });
            return emitter;
        }

        // Non-blocked: save the user turn, then build + persist the full reply exactly
        // as the POST path does, and finally drip the (already-persisted) segments
        // server-side over real SSE transport — no client-side fake typewriter.
        aiExecutor.execute(() -> {
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
                    request.timezone = richContext.timezone;
                    request.localTimeLabel = richContext.localTimeLabel;
                    request.weatherType = richContext.weatherType;
                    request.weatherDescription = richContext.weatherDescription;
                    request.temperature = richContext.temperature;
                    request.locationLabel = richContext.locationLabel;
                    request.latitude = richContext.latitude;
                    request.longitude = richContext.longitude;
                    request.aiProviderPreference = richContext.aiProviderPreference;
                }
                dialogService.saveUserMessage(userId, request);
                AuroraReplyVO reply = produceReply(userId, request, safety);

                for (int i = 0; i < reply.messages.size(); i++) {
                    if (i > 0) {
                        emitter.send(SseEmitter.event().name("segment").data("{\"break\":true}"));
                        Thread.sleep(220);
                    }
                    streamText(emitter, reply.messages.get(i));
                }
                // VS-003b — meta now carries the full perception payload (agentLoop,
                // aiState, voice/weather/location/timezone) so the frontend can render
                // the same panels on stream as on the POST fallback path.
                emitter.send(SseEmitter.event().name("meta").data(jsonMeta(reply, request, null)));
                emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                emitter.complete();
            } catch (Exception e) {
                log.error("Aurora stream failed: {}", e.getMessage(), e);
                // Best-effort error event so the client can fall back, then close.
                sendOnce(emitter, "error", "{\"message\":\"stream failed\"}");
                completeQuietly(emitter);
            }
        });
        return emitter;
    }

    /**
     * VS-003b — stage rich SSE context for a soon-to-open stream. The browser's
     * EventSource can only GET, so the frontend POSTs the rich body here, gets a
     * token, then opens GET /stream?token=…. Returns the token. Best-effort: a
     * self-expiring entry, consumed once by {@link #consumeStage(String)}.
     */
    @Override
    public String stageStreamContext(ChatRequest request) {
        if (request == null) return null;
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        streamStage.put(token, request);
        // TTL sweep — fire-and-forget; the map is bounded by active streams.
        aiExecutor.execute(() -> {
            try { Thread.sleep(STREAM_STAGE_TTL_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            streamStage.remove(token);
        });
        return token;
    }

    /** VS-003b — consume (once) the staged rich context for a stream token. */
    @Override
    public ChatRequest consumeStage(String token) {
        if (token == null || token.isBlank()) return null;
        return streamStage.remove(token);
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
                + ",\"featureTarget\":\"safety-harbor\""
                + ",\"safeMessage\":\"" + escape(safe) + "\"}";
    }

    @Override
    public AuroraReplyVO generateGreeting(Long userId, Long sessionId, String mode) {
        UserProfile profile = loadProfile(userId);
        String normalizedMode = normalizeMode(mode);
        boolean allowMemory = allowMemory(profile);
        AgentContext agentContext = agentContextAssembler.assemble(userId, sessionId, "", allowMemory);
        List<String> gravityMemories = allowMemory ? memoryService.topGravitySummaries(userId, 3) : List.of();
        AuroraMemoryContextVO memoryContext = allowMemory
                ? memoryContextService.buildContext(userId, sessionId, "", 6, 4)
                : null;
        String timeLabel = timeLabel();
        ModeStrategy modeStrategy = modeRegistry.get(normalizedMode);

        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withConversationMode(normalizedMode)
                .withModeSegment(modeStrategy)
                .withUserProfile(profileBrief(profile))
                .withUserPortrait(safePortrait(userId))
                .withUserCorrections(safeCorrections(userId))
                .withPortraitCalibrations(safePortraitCalibrations(userId))
                .withGravityMemories(gravityMemories)
                .withMemoryContext(memoryContext)
                .withOutputSchema()
                .build()
                + "\n\n现在是" + timeLabel + "。请 Aurora 主动发起对话，像朋友轻轻来找用户，而不是等待用户提问。";

        AuroraReplyVO vo;
        try {
            StructuredAiResults.AuroraResult ai = structuredAiService.call(userId, "AURORA_PROACTIVE_GREETING_" + normalizedMode,
                    auroraInstruction(true),
                    Map.of(
                            "auroraPrompt", prompt,
                            "mode", normalizedMode,
                            "timeLabel", timeLabel,
                            "memoryRecallAllowed", allowMemory,
                            "unifiedAgentContext", agentContext,
                            "providerPolicy", providerPolicy(null)
                    ),
                    StructuredAiResults.AuroraResult.class,
                    () -> fallbackGreeting(normalizedMode, timeLabel, gravityMemories, allowMemory));
            vo = toReply(profile, ai, null, normalizedMode, memoryContext, gravityMemories, allowMemory);
        } catch (Exception e) {
            log.error("Aurora greeting call failed, using emergency fallback: {}", e.getMessage(), e);
            vo = emergencyFallback("", normalizedMode);
        }
        vo.suggestSettle = false;
        if (sessionId != null) {
            for (String msg : vo.messages) {
                dialogService.saveAuroraMessage(userId, sessionId, msg);
            }
        }
        return vo;
    }

    private AuroraReplyVO blockedReply(Long userId, ChatRequest request, SafetyResult safety) {
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
        dialogService.saveAuroraMessage(userId, request.sessionId, blocked.messages.get(0));
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

    private AuroraReplyVO sanitizeLlmOutput(AuroraReplyVO vo, Long userId) {
        if (vo.messages == null || vo.messages.isEmpty()) return vo;
        List<String> sanitized = new ArrayList<>();
        for (String msg : vo.messages) {
            if (isLlmOutputBoundaryViolation(msg)) {
                log.warn("LLM output triggered hard boundary violation for user={}, replacing message", userId);
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
        return lower.contains("i am human") || lower.contains("i have consciousness") ||
               lower.contains("i feel real emotions") || lower.contains("biological life");
    }

    private AuroraReplyVO differentiatedFallback(Exception e, String message, String mode, String stateSignal) {
        String fallbackMsg;
        String flag;
        if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("timed out"))) {
            fallbackMsg = "I am still thinking about what you said, but it is taking a while. You can say it again, or we can talk about something else.";
            flag = "TIMEOUT";
        } else if (e.getMessage() != null && (e.getMessage().contains("429") || e.getMessage().contains("rate_limit"))) {
            fallbackMsg = "Things are a bit busy on my end. Please wait a minute and try again.";
            flag = "RATE_LIMITED";
        } else if (e.getMessage() != null && (e.getMessage().contains("parse") || e.getMessage().contains("JSON"))) {
            fallbackMsg = "I heard you but my thoughts did not come together clearly. Could you try saying it differently?";
            flag = "PARSE_ERROR";
        } else {
            // VS-004 mock-fallback coherence: when the LLM path failed, the
            // fallback still gently reflects the perceptual state signal so the
            // response stays coherent with the richer context. Perception only,
            // not a clinical label (vision §9/§13).
            fallbackMsg = fallbackAwareMessage(stateSignal);
            flag = "NETWORK_ERROR";
        }
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

    private StructuredAiResults.AuroraResult fallbackGreeting(String mode, String timeLabel, List<String> gravityMemories, boolean allowMemory) {
        StructuredAiResults.AuroraResult result = new StructuredAiResults.AuroraResult();
        List<String> segments = new ArrayList<>();
        segments.add(timeLabel + "好。我刚刚想起你，想先来问问：今天你心里最占地方的是什么？");
        if (allowMemory && gravityMemories != null && !gravityMemories.isEmpty()) {
            segments.add("如果你愿意，我们也可以从最近反复出现的那个主题继续；不愿意也没关系，今天可以只聊很轻的一点。");
        }
        result.segments = segments.stream().limit(2).toList();
        result.speakCount = result.segments.size();
        result.continueReason = "proactive-care";
        result.detectedTheme = "主动关心";
        result.nextQuestion = "今天你想从重一点的地方开始，还是从轻一点的地方开始？";
        result.smallStep = "";
        result.featureSuggestion = "也可以先写一段心声日记，我会帮你整理而不是评判。";
        result.featureTarget = "heart-diary";
        result.memoryReferenced = allowMemory && gravityMemories != null && !gravityMemories.isEmpty();
        result.referencedMemoryIds = List.of();
        return result;
    }

    private List<String> fallbackSegments(String message, String mode, boolean hasMemory, String stateSignal) {
        List<String> segments = new ArrayList<>();
        String text = message == null ? "" : message.trim();
        // VS-004 — the fallback gently reflects the perceptual state signal so the
        // deterministic path stays coherent with the richer prompt context. The
        // signal is a perception ("此刻偏疲惫/脆弱/平静/开放"), never a clinical label.
        String state = stateSignal == null ? "" : stateSignal.trim();
        if ("ACTION_SPLIT".equals(mode)) {
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
        return segments.stream().limit(3).toList();
    }

    private String auroraInstruction(boolean greeting) {
        String segmentCount = greeting ? "1-2" : "1-3";
        return ("You are the Aurora structured dialogue engine. Generate high-quality responses based on context.\n\n"
            + "[Absolute Rules]\n"
            + "1. Return only valid JSON. No Markdown wrapping, no code blocks, no thinking tags.\n"
            + "2. segments = Chinese chat bubbles, not article paragraphs. Each message should feel like a WeChat message.\n"
            + "3. referencedMemoryIds = number array only, e.g. [7, 12]. No strings, no #7 format.\n"
            + "4. No text outside the JSON.\n\n"
            + "[Message Count]\n"
            + "Max " + segmentCount + " segments. Count is determined by context, not fixed.\n"
            + "First message must respond to what the user just said.\n"
            + "Follow-up messages = Aurora's own judgment: supplement ideas / show care / connect memories (say 'I thought of a clue') / suggest features (only when natural).\n"
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
        return Map.of(
                "provider", provider,
                "model", model,
                "mode", llmConfig.getMode() == null ? "" : llmConfig.getMode(),
                "apiKeyConfigured", llmConfig.hasActiveApiKey(),
                "fallbackAllowed", llmConfig.isEffectiveFallbackAllowed()
        );
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
        text = text.replaceAll("^「|」$", "").trim();
        text = text.replaceAll("^(Aurora[:：]|我[:：])", "").trim();
        return text;
    }

    private boolean isTooSimilarInside(String text, Set<String> existing) {
        for (String old : existing) {
            if (similarity(text, old) >= 0.58 || sameLeadingClause(text, old)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTooSimilarToRecent(String text, List<String> recent) {
        if (recent == null) return false;
        return recent.stream().anyMatch(old -> similarity(text, old) >= 0.66 || sameLeadingClause(text, old));
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

    private void streamText(SseEmitter emitter, String response) throws Exception {
        StringBuilder token = new StringBuilder();
        for (char c : response.toCharArray()) {
            token.append(c);
            if (token.length() >= 2 || c == '。' || c == '，' || c == '\n') {
                emitter.send(SseEmitter.event().data("{\"content\":\"" + escape(token.toString()) + "\"}"));
                token.setLength(0);
                Thread.sleep(30);
            }
        }
        if (!token.isEmpty()) {
            emitter.send(SseEmitter.event().data("{\"content\":\"" + escape(token.toString()) + "\"}"));
        }
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
        sb.append(",\"replyTone\":\"").append(escape(reply.replyTone)).append("\"");
        sb.append(",\"nextQuestion\":\"").append(escape(reply.nextQuestion)).append("\"");
        sb.append(",\"smallStep\":\"").append(escape(reply.smallStep)).append("\"");
        sb.append(",\"featureSuggestion\":\"").append(escape(reply.featureSuggestion)).append("\"");
        sb.append(",\"suggestSettle\":").append(Boolean.TRUE.equals(reply.suggestSettle));
        sb.append(",\"memoryReferenced\":").append(Boolean.TRUE.equals(reply.memoryReferenced));
        sb.append(",\"riskFlags\":").append(jsonStringArray(reply.riskFlags));
        // agentLoop block: same shape as the POST path returns.
        Map<String, Object> loop = reply.agentLoop;
        int loopSpeak = loop != null && loop.get("speakCount") instanceof Number n ? n.intValue() : speakCount;
        String loopReason = loop != null && loop.get("continueReason") instanceof String s ? s : "";
        String loopMode = loop != null && loop.get("mode") instanceof String m ? m : "";
        String loopModeLabel = loop != null && loop.get("modeLabel") instanceof String ml ? ml : "";
        sb.append(",\"agentLoop\":{\"speakCount\":").append(loopSpeak)
                .append(",\"continueReason\":\"").append(escape(loopReason)).append("\"")
                .append(",\"mode\":\"").append(escape(loopMode)).append("\"")
                .append(",\"modeLabel\":\"").append(escape(loopModeLabel)).append("\"}");
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

    private String profileBrief(UserProfile profile) {
        if (profile == null) return "默认：温柔、具体、不过度分析；允许在相关时透明引用记忆。";
        String name = isBlank(profile.auroraName) ? "Aurora" : profile.auroraName;
        String tone = isBlank(profile.auroraTone) ? "温柔、具体、像朋友" : profile.auroraTone;
        String memory = Boolean.FALSE.equals(profile.allowMemoryRecall) ? "不要主动引用长期记忆" : "可以在相关时透明引用长期记忆";
        return "称呼：" + name + "；陪伴风格：" + tone + "；反思深度：" + profile.reflectionDepth + "；" + memory + "。";
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
        return "DAILY_TALK";
    }

    private String modeLabel(String mode) {
        return switch (mode) {
            case "THOUGHT_CLARIFY" -> "思维整理";
            case "SLEEP_REVIEW" -> "睡前复盘";
            case "SOCRATIC" -> "苏格拉底追问";
            case "ACTION_SPLIT" -> "行动拆解";
            case "RELATION_REVIEW" -> "关系复盘";
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

    private String timeLabel() {
        int hour = LocalTime.now().getHour();
        if (hour < 5) return "深夜";
        if (hour < 9) return "早晨";
        if (hour < 12) return "上午";
        if (hour < 14) return "中午";
        if (hour < 18) return "下午";
        if (hour < 22) return "晚上";
        return "夜里";
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

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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

