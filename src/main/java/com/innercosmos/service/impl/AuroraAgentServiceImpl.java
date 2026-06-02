package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.prompt.PromptBuilder;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.AuroraMemoryContextService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.RhythmGuardService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.AuroraMemoryContextVO;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.SafetyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class AuroraAgentServiceImpl implements AuroraAgentService {

    private static final Logger log = LoggerFactory.getLogger(AuroraAgentServiceImpl.class);

    private final StructuredAiService structuredAiService;
    private final DialogService dialogService;
    private final SafetyService safetyService;
    private final MemoryService memoryService;
    private final RhythmGuardService rhythmGuardService;
    private final AuroraMemoryContextService memoryContextService;
    private final UserProfileMapper userProfileMapper;
    private final DialogSessionMapper sessionMapper;
    private final Executor aiExecutor;

    public AuroraAgentServiceImpl(StructuredAiService structuredAiService, DialogService dialogService,
                                  SafetyService safetyService,
                                  MemoryService memoryService,
                                  RhythmGuardService rhythmGuardService,
                                  AuroraMemoryContextService memoryContextService,
                                  UserProfileMapper userProfileMapper,
                                  DialogSessionMapper sessionMapper,
                                  Executor aiExecutor) {
        this.structuredAiService = structuredAiService;
        this.dialogService = dialogService;
        this.safetyService = safetyService;
        this.memoryService = memoryService;
        this.rhythmGuardService = rhythmGuardService;
        this.memoryContextService = memoryContextService;
        this.userProfileMapper = userProfileMapper;
        this.sessionMapper = sessionMapper;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public String reply(Long userId, ChatRequest request) {
        AuroraReplyVO rich = replyRich(userId, request);
        return String.join("\n\n", rich.messages == null ? List.of() : rich.messages);
    }

    @Override
    public AuroraReplyVO replyRich(Long userId, ChatRequest request) {
        SafetyResult safety = safetyService.check(request.message, userId, request.sessionId);
        dialogService.saveUserMessage(userId, request);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            AuroraReplyVO blocked = new AuroraReplyVO();
            blocked.messages = List.of(safety.safeMessage);
            blocked.replyTone = "SAFETY";
            blocked.detectedTheme = safety.riskType;
            blocked.nextQuestion = "请优先联系现实中的可信任的人或当地紧急支持。";
            blocked.suggestSettle = true;
            blocked.memoryReferenced = false;
            blocked.referencedMemoryIds = List.of();
            dialogService.saveAuroraMessage(userId, request.sessionId, safety.safeMessage);
            return blocked;
        }

        UserProfile profile = loadProfile(userId);
        String rhythm = rhythmGuardService.checkRhythm(userId, request.sessionId);
        boolean allowMemory = profile == null || profile.allowMemoryRecall == null || Boolean.TRUE.equals(profile.allowMemoryRecall);
        List<String> gravityMemories = userId != null && allowMemory ? memoryService.topGravitySummaries(userId, 5) : List.of();
        AuroraMemoryContextVO memoryContext = memoryContextService.buildContext(userId, request.sessionId, request.message, 8, 6);
        DialogSession session = sessionMapper.selectById(request.sessionId);
        List<String> recentMessages = recentMessages(request.sessionId, 6);
        String mode = normalizeMode(request.mode);
        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withConversationMode(mode)
                .withUserProfile(profileBrief(profile))
                .withSummaryAnchor(session == null ? null : session.summaryAnchor)
                .withRecentMessages(recentMessages)
                .withGravityMemories(gravityMemories)
                .withMemoryContext(memoryContext)
                .withRhythmAdvice(rhythm)
                .withUserInput(request.message)
                .withVoiceMetadata(voiceMetadata(request))
                .withOutputSchema()
                .build();
        StructuredAiResults.AuroraResult ai = structuredAiService.call(userId, "AURORA_CHAT_" + mode,
                """
                Return JSON with:
                segments: 2-4 short natural message segments in the user's language;
                detectedTheme: concrete theme label;
                nextQuestion: at most one gentle next question;
                smallStep: one small real-world next step, or empty string;
                memoryReferenced: true only if you explicitly used memory context;
                referencedMemoryIds: IDs from memory context only.
                The reply must feel specific to the user's input and must not diagnose.
                """,
                java.util.Map.of("prompt", prompt, "mode", mode, "memoryRecallAllowed", allowMemory),
                StructuredAiResults.AuroraResult.class,
                () -> fallbackAuroraResult(request.message, mode, gravityMemories, memoryContext));
        String response = String.join("\n\n", ai.segments == null || ai.segments.isEmpty()
                ? fallbackAuroraResult(request.message, mode, gravityMemories, memoryContext).segments
                : ai.segments);
        dialogService.saveAuroraMessage(userId, request.sessionId, response);

        AuroraReplyVO vo = new AuroraReplyVO();
        vo.messages = splitMessages(response);
        vo.replyTone = profile == null || profile.auroraTone == null ? "温柔安静" : profile.auroraTone;
        vo.detectedTheme = ai.detectedTheme == null || ai.detectedTheme.isBlank() ? detectTheme(request.message, mode) : ai.detectedTheme;
        vo.nextQuestion = ai.nextQuestion == null || ai.nextQuestion.isBlank() ? extractQuestion(vo.messages) : ai.nextQuestion;
        vo.smallStep = ai.smallStep;
        vo.suggestSettle = rhythmGuardService.shouldSuggestSettle(userId, request.sessionId)
                || "SETTLE".equals(rhythm)
                || "REST".equals(rhythm);
        vo.memoryReferenced = Boolean.TRUE.equals(ai.memoryReferenced)
                || (!gravityMemories.isEmpty() && allowMemory)
                || (memoryContext.referencedMemoryIds != null && !memoryContext.referencedMemoryIds.isEmpty());
        vo.referencedMemoryIds = ai.referencedMemoryIds == null || ai.referencedMemoryIds.isEmpty()
                ? (memoryContext.referencedMemoryIds == null ? List.of() : memoryContext.referencedMemoryIds)
                : ai.referencedMemoryIds;
        vo.memoryContext = memoryContext;
        return vo;
    }

    @Override
    public SseEmitter stream(Long userId, Long sessionId, String message) {
        SseEmitter emitter = new SseEmitter(60_000L);

        emitter.onCompletion(() -> log.debug("SSE stream completed for userId={}, sessionId={}", userId, sessionId));
        emitter.onTimeout(() -> {
            log.warn("SSE stream timed out for userId={}, sessionId={}", userId, sessionId);
            emitter.complete();
        });

        aiExecutor.execute(() -> {
            try {
                ChatRequest request = new ChatRequest();
                request.sessionId = sessionId;
                request.message = message;
                AuroraReplyVO reply = replyRich(userId, request);
                String response = String.join("\n\n", reply.messages == null ? List.of() : reply.messages);
                StringBuilder token = new StringBuilder();
                for (char c : response.toCharArray()) {
                    token.append(c);
                    if (token.length() >= 2 || c == '。' || c == '，' || c == '\n') {
                        emitter.send(SseEmitter.event().data("{\"content\":\"" + escape(token.toString()) + "\"}"));
                        token.setLength(0);
                        Thread.sleep(30);
                    }
                }
                if (token.length() > 0) {
                    emitter.send(SseEmitter.event().data("{\"content\":\"" + escape(token.toString()) + "\"}"));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream failed for userId={}, sessionId={}: {}", userId, sessionId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private StructuredAiResults.AuroraResult fallbackAuroraResult(String message, String mode,
                                                                  List<String> gravityMemories,
                                                                  AuroraMemoryContextVO memoryContext) {
        StructuredAiResults.AuroraResult result = new StructuredAiResults.AuroraResult();
        String theme = detectTheme(message, mode);
        result.detectedTheme = theme;
        result.segments = List.of(
                "我听见这件事现在对你有重量，尤其是「" + theme + "」这一层。",
                "我们先不急着把它解释成你哪里不够好，可以先把事实、感受和下一步分开。",
                "此刻最小的一步，是把最重的一句话写下来，再决定要不要处理它。"
        );
        result.nextQuestion = "如果只选一个最需要被看见的部分，会是哪一个？";
        result.smallStep = "写下一句最重的话。";
        result.memoryReferenced = memoryContext != null && memoryContext.referencedMemoryIds != null
                && !memoryContext.referencedMemoryIds.isEmpty() && gravityMemories != null && !gravityMemories.isEmpty();
        result.referencedMemoryIds = result.memoryReferenced && memoryContext.referencedMemoryIds != null
                ? memoryContext.referencedMemoryIds : List.of();
        return result;
    }

    private String voiceMetadata(ChatRequest request) {
        if (!"VOICE".equalsIgnoreCase(request.inputType)) {
            return "";
        }
        return "语音时长 " + safe(request.audioDurationSec) + " 秒，语速 " + safe(request.speechRate)
                + "，停顿 " + safe(request.pauseCount) + " 次，长停顿 " + safe(request.longPauseCount) + " 次";
    }

    private UserProfile loadProfile(Long userId) {
        if (userId == null) return null;
        QueryWrapper<UserProfile> query = new QueryWrapper<>();
        query.eq("user_id", userId).last("LIMIT 1");
        return userProfileMapper.selectOne(query);
    }

    private List<String> recentMessages(Long sessionId, int limit) {
        if (sessionId == null) return List.of();
        List<DialogMessage> messages = dialogService.messages(sessionId);
        int start = Math.max(0, messages.size() - limit);
        List<String> result = new ArrayList<>();
        for (DialogMessage message : messages.subList(start, messages.size())) {
            String speaker = "USER".equals(message.speaker) ? "用户" : "Aurora";
            result.add(speaker + "：" + abbreviate(message.textContent, 120));
        }
        return result;
    }

    private String profileBrief(UserProfile profile) {
        if (profile == null) {
            return "默认温柔安静风格，允许适度记忆引用。";
        }
        String tone = profile.auroraTone == null || profile.auroraTone.isBlank() ? "温柔安静" : profile.auroraTone;
        String depth = profile.reflectionDepth == null ? "3" : profile.reflectionDepth.toString();
        String memory = Boolean.FALSE.equals(profile.allowMemoryRecall) ? "不要主动引用长期记忆" : "可以透明引用相关长期记忆";
        return "称呼 " + (profile.auroraName == null ? "Aurora" : profile.auroraName)
                + "；陪伴风格 " + tone + "；反思深度 " + depth + "；" + memory + "。";
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "DAILY_TALK";
        String upper = mode.trim().toUpperCase();
        if (List.of("DAILY_TALK", "THOUGHT_CLARIFY", "SLEEP_REVIEW", "SOCRATIC", "ACTION_SPLIT", "RELATION_REVIEW").contains(upper)) {
            return upper;
        }
        if (mode.contains("思维")) return "THOUGHT_CLARIFY";
        if (mode.contains("睡前")) return "SLEEP_REVIEW";
        if (mode.contains("苏格拉底")) return "SOCRATIC";
        if (mode.contains("行动")) return "ACTION_SPLIT";
        if (mode.contains("关系")) return "RELATION_REVIEW";
        return "DAILY_TALK";
    }

    private List<String> splitMessages(String response) {
        if (response == null || response.isBlank()) return List.of("我在。我们可以先从一句最真实的话开始。");
        List<String> parts = new ArrayList<>();
        for (String part : response.split("\\n\\s*\\n|(?<=[。！？?])")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) parts.add(trimmed);
        }
        return parts.isEmpty() ? List.of(response) : parts;
    }

    private String detectTheme(String message, String mode) {
        if (message == null || message.isBlank()) return mode;

        // Use semantic analysis for better theme detection
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze(message);

        // Return primary detected theme if any, otherwise fall back to mode
        if (!analysis.detectedThemes.isEmpty() && !analysis.detectedThemes.contains("日常分享")) {
            return analysis.detectedThemes.get(0);
        }

        // Fall back to mode if no clear theme detected
        return mode;
    }

    private String extractQuestion(List<String> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            String msg = messages.get(i);
            if (msg.contains("？") || msg.contains("?")) return msg;
        }
        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(keyword -> text != null && text.contains(keyword));
    }

    private String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private String safe(Object value) {
        return value == null ? "未记录" : value.toString();
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
