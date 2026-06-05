package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.prompt.PromptBuilder;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public AuroraAgentServiceImpl(StructuredAiService structuredAiService,
                                  DialogService dialogService,
                                  SafetyService safetyService,
                                  MemoryService memoryService,
                                  RhythmGuardService rhythmGuardService,
                                  AuroraMemoryContextService memoryContextService,
                                  UserProfileMapper userProfileMapper,
                                  DialogSessionMapper sessionMapper,
                                  LlmConfig llmConfig,
                                  Executor aiExecutor) {
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
            return blockedReply(userId, request, safety);
        }

        UserProfile profile = loadProfile(userId);
        String mode = normalizeMode(request.mode);
        boolean allowMemory = allowMemory(profile);
        List<String> gravityMemories = allowMemory ? memoryService.topGravitySummaries(userId, 5) : List.of();
        AuroraMemoryContextVO memoryContext = allowMemory
                ? memoryContextService.buildContext(userId, request.sessionId, request.message, 8, 6)
                : null;
        DialogSession session = request.sessionId == null ? null : sessionMapper.selectById(request.sessionId);
        String rhythm = rhythmGuardService.checkRhythm(userId, request.sessionId);

        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withConversationMode(mode)
                .withUserProfile(profileBrief(profile))
                .withSummaryAnchor(session == null ? null : session.summaryAnchor)
                .withRecentMessages(recentMessages(request.sessionId, 8))
                .withGravityMemories(gravityMemories)
                .withMemoryContext(memoryContext)
                .withRhythmAdvice(rhythm)
                .withVoiceMetadata(voiceMetadata(request))
                .withUserInput(request.message)
                .withOutputSchema()
                .build();

        StructuredAiResults.AuroraResult ai = structuredAiService.call(userId, "AURORA_AGENT_LOOP_" + mode,
                auroraInstruction(false),
                Map.of(
                        "auroraPrompt", prompt,
                        "userMessage", request.message == null ? "" : request.message,
                        "mode", mode,
                        "modeGuide", modeGuide(mode),
                        "memoryRecallAllowed", allowMemory,
                        "providerPolicy", providerPolicy(),
                        "agentLoopPolicy", "你可以选择只说一条，也可以继续补充第二条或第三条。不要固定数量。"
                ),
                StructuredAiResults.AuroraResult.class,
                () -> fallbackAuroraResult(request.message, mode, gravityMemories, memoryContext, allowMemory));

        AuroraReplyVO vo = toReply(profile, ai, request, mode, memoryContext, gravityMemories, allowMemory);
        for (String msg : vo.messages) {
            dialogService.saveAuroraMessage(userId, request.sessionId, msg);
        }
        return vo;
    }

    @Override
    public SseEmitter stream(Long userId, Long sessionId, String message, String mode) {
        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.onTimeout(emitter::complete);
        aiExecutor.execute(() -> {
            try {
                ChatRequest request = new ChatRequest();
                request.sessionId = sessionId;
                request.message = message;
                request.mode = normalizeMode(mode);
                AuroraReplyVO reply = replyRich(userId, request);
                for (int i = 0; i < reply.messages.size(); i++) {
                    if (i > 0) {
                        emitter.send(SseEmitter.event().name("segment").data("{\"break\":true}"));
                        Thread.sleep(260);
                    }
                    streamText(emitter, reply.messages.get(i));
                }
                emitter.send(SseEmitter.event().name("meta").data(jsonMeta(reply)));
                emitter.complete();
            } catch (Exception e) {
                log.error("Aurora stream failed: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @Override
    public AuroraReplyVO generateGreeting(Long userId, Long sessionId, String mode) {
        UserProfile profile = loadProfile(userId);
        String normalizedMode = normalizeMode(mode);
        boolean allowMemory = allowMemory(profile);
        List<String> gravityMemories = allowMemory ? memoryService.topGravitySummaries(userId, 3) : List.of();
        AuroraMemoryContextVO memoryContext = allowMemory
                ? memoryContextService.buildContext(userId, sessionId, "", 6, 4)
                : null;
        String timeLabel = timeLabel();

        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withConversationMode(normalizedMode)
                .withUserProfile(profileBrief(profile))
                .withGravityMemories(gravityMemories)
                .withMemoryContext(memoryContext)
                .withOutputSchema()
                .build()
                + "\n\n现在是" + timeLabel + "。请 Aurora 主动发起对话，像朋友轻轻来找用户，而不是等待用户提问。";

        StructuredAiResults.AuroraResult ai = structuredAiService.call(userId, "AURORA_PROACTIVE_GREETING_" + normalizedMode,
                auroraInstruction(true),
                Map.of(
                        "auroraPrompt", prompt,
                        "mode", normalizedMode,
                        "timeLabel", timeLabel,
                        "memoryRecallAllowed", allowMemory,
                        "providerPolicy", providerPolicy()
                ),
                StructuredAiResults.AuroraResult.class,
                () -> fallbackGreeting(normalizedMode, timeLabel, gravityMemories, allowMemory));

        AuroraReplyVO vo = toReply(profile, ai, null, normalizedMode, memoryContext, gravityMemories, allowMemory);
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
        blocked.aiState = aiState();
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
        List<String> messages = cleanSegments(safeAi.segments);
        if (messages.isEmpty()) {
            String userText = request == null ? "" : request.message;
            messages = fallbackAuroraResult(userText, mode, gravityMemories, memoryContext, allowMemory).segments;
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
        vo.aiState = aiState();
        return vo;
    }

    private StructuredAiResults.AuroraResult fallbackAuroraResult(String message,
                                                                  String mode,
                                                                  List<String> gravityMemories,
                                                                  AuroraMemoryContextVO memoryContext,
                                                                  boolean allowMemory) {
        StructuredAiResults.AuroraResult result = new StructuredAiResults.AuroraResult();
        result.segments = fallbackSegments(message, mode, allowMemory && gravityMemories != null && !gravityMemories.isEmpty());
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

    private List<String> fallbackSegments(String message, String mode, boolean hasMemory) {
        List<String> segments = new ArrayList<>();
        String text = message == null ? "" : message.trim();
        if ("ACTION_SPLIT".equals(mode)) {
            segments.add("我先不把它变成一整套计划。我们只找一个十分钟内能开始的小动作。");
            segments.add("你现在最容易动起来的第一步，可能不是“解决它”，而是先把它写成一句可执行的话。");
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
        return """
                只返回合法 JSON，不要 Markdown：
                {
                  "segments": ["短消息 1", "可选短消息 2"],
                  "speakCount": 1,
                  "continueReason": "为什么选择继续说或停住",
                  "detectedTheme": "具体主题",
                  "nextQuestion": "最多一个问题，可留空",
                  "smallStep": "很小的下一步，可留空",
                  "featureSuggestion": "自然时才推荐项目功能，可留空",
                  "featureTarget": "heart-diary|thought-shredder|todo|memory-starfield|echo-plaza|slow-letter|",
                  "memoryReferenced": false,
                  "referencedMemoryIds": [],
                  "riskFlags": []
                }
                segments 必须是 %s 条中文聊天气泡，具体条数由上下文决定，不要固定。
                第一条必须贴着用户刚刚说的话；后续消息是 Aurora 的 agent loop：补充想法、关心、记忆连接或温和功能邀请。
                不要模板化，不要诊断，不要口号，不要长文。
                """.formatted(segmentCount);
    }

    private String providerPolicy() {
        return "当前主模型=" + llmConfig.activeProvider() + "/" + llmConfig.activeModel()
                + "，mode=" + llmConfig.getMode()
                + "，fallbackAllowed=" + llmConfig.isEffectiveFallbackAllowed()
                + "。正式路径必须优先使用真实模型。";
    }

    private Map<String, Object> aiState() {
        return Map.of(
                "provider", llmConfig.activeProvider(),
                "model", llmConfig.activeModel(),
                "mode", llmConfig.getMode() == null ? "" : llmConfig.getMode(),
                "apiKeyConfigured", llmConfig.hasActiveApiKey(),
                "fallbackAllowed", llmConfig.isEffectiveFallbackAllowed()
        );
    }

    private List<String> cleanSegments(List<String> raw) {
        if (raw == null) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) continue;
            String text = item.trim();
            if (!text.isBlank()) unique.add(text.length() > 260 ? text.substring(0, 260) : text);
            if (unique.size() >= 3) break;
        }
        return new ArrayList<>(unique);
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
        return "{\"speakCount\":" + (reply.messages == null ? 0 : reply.messages.size())
                + ",\"detectedTheme\":\"" + escape(reply.detectedTheme) + "\""
                + ",\"featureTarget\":\"" + escape(reply.featureTarget) + "\"}";
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

    private List<String> recentMessages(Long sessionId, int limit) {
        if (sessionId == null) return List.of();
        List<DialogMessage> messages = dialogService.messages(sessionId);
        int start = Math.max(0, messages.size() - limit);
        List<String> result = new ArrayList<>();
        for (DialogMessage message : messages.subList(start, messages.size())) {
            String speaker = "USER".equals(message.speaker) ? "用户" : "Aurora";
            result.add(speaker + ": " + abbreviate(message.textContent, 160));
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
            case "THOUGHT_CLARIFY" -> "拆出事实、感受、担心、需要和下一步。";
            case "SLEEP_REVIEW" -> "收束、安顿、减少追问，帮助用户睡前放下。";
            case "SOCRATIC" -> "温和追问一个关键假设，不直接给答案。";
            case "ACTION_SPLIT" -> "给十分钟内能开始的具体第一步。";
            case "RELATION_REVIEW" -> "区分事实、感受、需要和边界，不评判人格。";
            default -> "先陪伴，再根据需要轻轻引导。";
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
}
