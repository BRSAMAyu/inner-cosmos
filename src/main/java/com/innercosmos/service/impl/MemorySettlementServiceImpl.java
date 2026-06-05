package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;
import com.innercosmos.entity.*;
import com.innercosmos.mapper.*;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.MemorySettlementService;
import com.innercosmos.service.ThemeAggregationService;
import com.innercosmos.vo.DailyRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MemorySettlementServiceImpl implements MemorySettlementService {
    private final MemoryCardMapper memoryCardMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final TodoItemMapper todoItemMapper;
    private final EventCardMapper eventCardMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final MemoryThemeMapper memoryThemeMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final DialogMessageMapper dialogMessageMapper;
    private final DialogSessionMapper dialogSessionMapper;
    private final GravityService gravityService;
    private final ThemeAggregationService themeAggregationService;
    private final StructuredAiService structuredAiService;

    public MemorySettlementServiceImpl(MemoryCardMapper memoryCardMapper,
                                       ThoughtFragmentMapper thoughtFragmentMapper,
                                       EmotionTraceMapper emotionTraceMapper,
                                       TodoItemMapper todoItemMapper,
                                       EventCardMapper eventCardMapper,
                                       RelationMentionMapper relationMentionMapper,
                                       MemoryThemeMapper memoryThemeMapper,
                                       DailyRecordMapper dailyRecordMapper,
                                       DialogMessageMapper dialogMessageMapper,
                                       DialogSessionMapper dialogSessionMapper,
                                       GravityService gravityService,
                                       ThemeAggregationService themeAggregationService,
                                       StructuredAiService structuredAiService) {
        this.memoryCardMapper = memoryCardMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.todoItemMapper = todoItemMapper;
        this.eventCardMapper = eventCardMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.memoryThemeMapper = memoryThemeMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.dialogMessageMapper = dialogMessageMapper;
        this.dialogSessionMapper = dialogSessionMapper;
        this.gravityService = gravityService;
        this.themeAggregationService = themeAggregationService;
        this.structuredAiService = structuredAiService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleSession(Long userId, Long sessionId) {
        // Verify session ownership
        DialogSession session = dialogSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此对话会话");
        }
        // Read user messages from session
        QueryWrapper<DialogMessage> msgQuery = new QueryWrapper<>();
        msgQuery.eq("session_id", sessionId).eq("speaker", "USER").orderByAsc("id");
        List<DialogMessage> messages = dialogMessageMapper.selectList(msgQuery);
        String raw = messages.stream().map(m -> m.textContent).reduce("", (a, b) -> a + "\n" + b);
        StructuredAiResults.SettlementResult ai = structuredAiService.call(userId, "MEMORY_SETTLEMENT",
                """
                Return JSON for a private memory settlement:
                memoryCard {title, summary, memoryType, emotionTags[], keywordTags[], peopleTags[], intensityScore, userImportance},
                fragments[] {type, rawExcerpt, analysis, reframe},
                emotionTrace {emotionName, emotionScore, weatherType, triggerScene},
                eventCards[], relationMentions[], todos[].
                Extract only from the user's messages. Keep it non-clinical and privacy-preserving.
                """,
                Map.of("sessionId", sessionId, "userMessages", raw),
                StructuredAiResults.SettlementResult.class,
                () -> fallbackSettlement(raw));

        // Extract MemoryCard
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.sourceSessionId = sessionId;
        card.title = blank(ai.memoryCard.title, "今日沉淀");
        card.summary = blank(ai.memoryCard.summary, firstSentence(raw));
        card.memoryType = blank(ai.memoryCard.memoryType, inferType(raw));
        card.emotionTags = jsonArray(ai.memoryCard.emotionTags, List.of("self-observation"));
        card.keywordTags = jsonArray(ai.memoryCard.keywordTags, fallbackKeywords(raw));
        card.peopleTags = jsonArray(ai.memoryCard.peopleTags, List.of());
        card.intensityScore = ai.memoryCard.intensityScore == null ? inferIntensity(raw) : ai.memoryCard.intensityScore;
        card.recurrenceCount = 1;
        card.userImportance = ai.memoryCard.userImportance == null ? 4.0 : ai.memoryCard.userImportance;
        card.triggerCount = 1;
        card.emotionalGravity = gravityService.calculateGravity(card.intensityScore, 1, card.userImportance, 1, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        memoryCardMapper.insert(card);

        // Create structured assets: fragments
        if (ai.fragments != null && !ai.fragments.isEmpty()) {
            for (StructuredAiResults.Fragment fragment : ai.fragments) {
                createFragment(userId, card.id, blank(fragment.type, "OBSERVATION"),
                        blank(fragment.rawExcerpt, firstSentence(raw)),
                        blank(fragment.analysis, "从用户表达中抽取出的片段."),
                        blank(fragment.reframe, "先把它放成一个可以看见的形状."));
            }
        } else {
            createFragment(userId, card.id, "FACT", firstSentence(raw), "从用户表达中抽取出的事实片段.", "先区分事实和解释.");
            createFragment(userId, card.id, "FEELING", inferEmotion(raw), "表达里出现的主要感受线索.", "允许感受存在,不急着证明它合理.");
            createFragment(userId, card.id, "BELIEF", inferBelief(raw), "可能影响用户自我评价的信念.", "把事件和自我价值暂时分开看.");
            createFragment(userId, card.id, "ACTION", inferAction(raw), "可以轻轻推进的一步.", "把下一步压缩到十分钟内能开始.");
        }

        if (raw.contains("需要") || raw.contains("想要")) {
            createFragment(userId, card.id, "NEED", inferNeed(raw), "表达中隐含的深层需要.", "把需要从期待中分出来看看.");
        }
        if (raw.contains("担心") || raw.contains("害怕") || raw.contains("焦虑")) {
            createFragment(userId, card.id, "WORRY", inferWorry(raw), "来自用户的担忧.", "先承认这个担心是合理的.");
        }

        // Create EmotionTrace
        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.sourceSessionId = sessionId;
        trace.emotionName = blank(ai.emotionTrace.emotionName, inferEmotionName(raw));
        trace.emotionScore = ai.emotionTrace.emotionScore == null ? card.intensityScore : ai.emotionTrace.emotionScore;
        trace.weatherType = blank(ai.emotionTrace.weatherType, inferWeather(card.intensityScore));
        trace.triggerScene = blank(ai.emotionTrace.triggerScene, firstSentence(raw));
        trace.recordDate = LocalDate.now();
        emotionTraceMapper.insert(trace);

        // Create TodoItems if task-related
        if (ai.todos != null && !ai.todos.isEmpty()) {
            for (StructuredAiResults.TodoSuggestion suggestion : ai.todos) {
                TodoItem todo = new TodoItem();
                todo.userId = userId;
                todo.sourceMemoryCardId = card.id;
                todo.taskName = blank(suggestion.taskName, "把今天提到的任务拆成第一步");
                todo.description = blank(suggestion.description, "由 Aurora 对话自动提取,建议从一个十分钟动作开始.");
                todo.priority = blank(suggestion.priority, "MEDIUM");
                todo.status = "TODO";
                todoItemMapper.insert(todo);
            }
        } else if (raw.contains("作业") || raw.contains("考试") || raw.contains("任务") || raw.contains("明天") || raw.contains("拖延")) {
            TodoItem todo = new TodoItem();
            todo.userId = userId;
            todo.sourceMemoryCardId = card.id;
            todo.taskName = raw.contains("作业") ? "推进 Java 作业的第一步" : "把今天提到的任务拆成第一步";
            todo.description = "由 Aurora 对话自动提取,建议从一个十分钟动作开始.";
            todo.priority = raw.contains("考试") ? "HIGH" : "MEDIUM";
            todo.status = "TODO";
            todoItemMapper.insert(todo);
        }

        // Create EventCards if event-related
        if (ai.eventCards != null && !ai.eventCards.isEmpty()) {
            for (StructuredAiResults.Event item : ai.eventCards) {
                EventCard event = new EventCard();
                event.userId = userId;
                event.sourceSessionId = sessionId;
                event.memoryCardId = card.id;
                event.eventTitle = blank(item.eventTitle, firstSentence(raw));
                event.eventSummary = blank(item.eventSummary, firstSentence(raw));
                event.eventTimeLabel = blank(item.eventTimeLabel, inferTimeLabel(raw));
                event.scene = blank(item.scene, "从对话中提取的事件场景");
                event.peopleTags = jsonArray(item.peopleTags, List.of());
                event.emotionTags = jsonArray(item.emotionTags, ai.memoryCard.emotionTags);
                eventCardMapper.insert(event);
            }
        } else if (raw.contains("今天") || raw.contains("昨天") || raw.contains("上周") || raw.contains("发生")) {
            EventCard event = new EventCard();
            event.userId = userId;
            event.sourceSessionId = sessionId;
            event.memoryCardId = card.id;
            event.eventTitle = firstSentence(raw);
            event.eventSummary = firstSentence(raw);
            event.eventTimeLabel = inferTimeLabel(raw);
            event.scene = "从对话中提取的事件场景";
            event.peopleTags = "[]";
            event.emotionTags = card.emotionTags;
            eventCardMapper.insert(event);
        }

        // Create RelationMentions if relation-related
        if (ai.relationMentions != null && !ai.relationMentions.isEmpty()) {
            for (StructuredAiResults.Relation item : ai.relationMentions) {
                RelationMention mention = new RelationMention();
                mention.userId = userId;
                mention.sourceSessionId = sessionId;
                mention.memoryCardId = card.id;
                mention.relationLabel = blank(item.relationLabel, inferRelationLabel(raw));
                mention.relationType = blank(item.relationType, inferRelationType(raw));
                mention.emotionTags = jsonArray(item.emotionTags, ai.memoryCard.emotionTags);
                mention.triggerSummary = blank(item.triggerSummary, firstSentence(raw));
                mention.boundaryHint = blank(item.boundaryHint, "注意关系的边界和自己的感受");
                relationMentionMapper.insert(mention);
            }
        } else if (raw.contains("朋友") || raw.contains("同学") || raw.contains("老师") || raw.contains("家人") || raw.contains("关系")) {
            RelationMention mention = new RelationMention();
            mention.userId = userId;
            mention.sourceSessionId = sessionId;
            mention.memoryCardId = card.id;
            mention.relationLabel = inferRelationLabel(raw);
            mention.relationType = inferRelationType(raw);
            mention.emotionTags = card.emotionTags;
            mention.triggerSummary = firstSentence(raw);
            mention.boundaryHint = "注意关系的边界和自己的感受";
            relationMentionMapper.insert(mention);
        }

        // Update themes
        themeAggregationService.aggregateThemes(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyRecordVO generateDailyRecord(Long userId, Long sessionId) {
        // Find the latest MemoryCard for this session
        QueryWrapper<MemoryCard> cardQuery = new QueryWrapper<>();
        cardQuery.eq("user_id", userId).eq("source_session_id", sessionId).orderByDesc("id").last("LIMIT 1");
        MemoryCard card = memoryCardMapper.selectOne(cardQuery);

        DailyRecordVO vo = new DailyRecordVO();
        if (card == null) {
            vo.theme = "还没有沉淀出的今日主题";
            vo.auroraSummary = "和 Aurora 完成一次对话后,这里会生成今日记录卡.";
            return vo;
        }

        vo.mainMemory = card;
        vo.theme = card.title;
        vo.auroraSummary = card.summary;
        vo.capsuleSuggested = card.emotionalGravity != null && card.emotionalGravity > 1.1;

        // Load fragments
        QueryWrapper<ThoughtFragment> fragmentQuery = new QueryWrapper<>();
        fragmentQuery.eq("memory_card_id", card.id).orderByAsc("id");
        vo.fragments = thoughtFragmentMapper.selectList(fragmentQuery);

        // Load emotions
        QueryWrapper<EmotionTrace> emotionQuery = new QueryWrapper<>();
        emotionQuery.eq("source_session_id", sessionId).orderByDesc("id");
        vo.emotions = emotionTraceMapper.selectList(emotionQuery);

        // Load todos
        QueryWrapper<TodoItem> todoQuery = new QueryWrapper<>();
        todoQuery.eq("source_memory_card_id", card.id).orderByAsc("id");
        vo.todos = todoItemMapper.selectList(todoQuery);

        // Save to tb_daily_record
        DailyRecord record = new DailyRecord();
        record.userId = userId;
        record.recordDate = LocalDate.now();
        record.sourceSessionId = sessionId;
        record.theme = vo.theme;
        record.eventSummary = card.summary;
        record.emotionWeather = inferWeather(card.intensityScore);
        record.cognitiveSummary = firstSentence(card.summary);
        record.todoSummary = vo.todos.isEmpty() ? "今日无待办" : "有 " + vo.todos.size() + " 项待推进";
        record.auroraSummary = vo.auroraSummary;
        record.capsuleSuggested = vo.capsuleSuggested;
        record.userAccepted = false;
        record.status = "ACTIVE";
        dailyRecordMapper.insert(record);

        return vo;
    }

    @Override
    public void updateThemeAggregation(Long userId) {
        themeAggregationService.aggregateThemes(userId);
    }

    private void createFragment(Long userId, Long cardId, String type, String rawExcerpt, String analysis, String reframe) {
        ThoughtFragment fragment = new ThoughtFragment();
        fragment.userId = userId;
        fragment.memoryCardId = cardId;
        fragment.fragmentType = type;
        fragment.rawExcerpt = rawExcerpt;
        fragment.aiAnalysis = analysis;
        fragment.reframeText = reframe;
        thoughtFragmentMapper.insert(fragment);
    }

    private StructuredAiResults.SettlementResult fallbackSettlement(String raw) {
        StructuredAiResults.SettlementResult result = new StructuredAiResults.SettlementResult();
        result.memoryCard.title = "今日沉淀";
        result.memoryCard.summary = firstSentence(raw);
        result.memoryCard.memoryType = inferType(raw);
        result.memoryCard.emotionTags = List.of("self-observation");
        result.memoryCard.keywordTags = fallbackKeywords(raw);
        result.memoryCard.peopleTags = List.of();
        result.memoryCard.intensityScore = inferIntensity(raw);
        result.memoryCard.userImportance = 4.0;
        result.emotionTrace.emotionName = inferEmotionName(raw);
        result.emotionTrace.emotionScore = inferIntensity(raw);
        result.emotionTrace.weatherType = inferWeather(result.emotionTrace.emotionScore);
        result.emotionTrace.triggerScene = firstSentence(raw);
        return result;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String jsonArray(List<String> values, List<String> fallback) {
        List<String> safe = values == null || values.isEmpty() ? fallback : values;
        return com.innercosmos.util.JsonUtils.toJson(safe == null ? List.of() : safe);
    }

    private List<String> fallbackKeywords(String raw) {
        List<String> keywords = new ArrayList<>();
        if (raw.contains("作业") || raw.contains("考试")) keywords.add("学业");
        if (raw.contains("朋友") || raw.contains("同学")) keywords.add("社交");
        if (raw.contains("家人")) keywords.add("家庭");
        if (raw.contains("累") || raw.contains("压力")) keywords.add("压力");
        if (raw.contains("开心") || raw.contains("高兴")) keywords.add("积极");
        if (keywords.isEmpty()) keywords.add("日常");
        return keywords;
    }

    private String firstSentence(String raw) {
        if (raw == null || raw.isBlank()) {
            return "用户完成了一次自我表达.";
        }
        String compact = raw.replaceAll("\\s+", " ").trim();
        return compact.length() > 64 ? compact.substring(0, 64) + "..." : compact;
    }

    /**
     * Get semantic analysis for the current input.
     * Uses PseudoSemanticAnalyzer for better inference.
     */
    private AnalysisResult analyze(String raw) {
        return PseudoSemanticAnalyzer.analyze(raw);
    }

    private String inferType(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Use primary intent from semantic analysis
        switch (analysis.primaryIntent) {
            case "TASK_STRESS":
                return "TODO";
            case "RELATION_ISSUE":
                return "RELATION";
            case "COGNITIVE_CLARITY":
                return "COGNITION";
            case "SELF_HARM":
            case "EXPRESS_EMOTION":
                return "EMOTION";
            default:
                return "EMOTION";
        }
    }

    private String inferKeywords(String raw) {
        return jsonArray(fallbackKeywords(raw), List.of("日常"));
    }

    private Double inferIntensity(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Use intensity score from semantic analysis (0-10 scale)
        return analysis.intensityScore;
    }

    private String inferEmotion(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Build emotion description from sentiment label and themes
        StringBuilder emotion = new StringBuilder();

        // Add sentiment base
        switch (analysis.sentimentLabel) {
            case "CRISIS":
                emotion.append("很重的情绪,需要温柔的支持");
                break;
            case "NEGATIVE":
                if (analysis.detectedThemes.contains("情绪承压")) {
                    emotion.append("疲惫和压力");
                } else if (analysis.detectedThemes.contains("关系牵动")) {
                    emotion.append("委屈和难过");
                } else {
                    emotion.append("不舒服的感受");
                }
                break;
            case "POSITIVE":
                emotion.append("明亮和积极");
                break;
            default:
                emotion.append("还没有被命名的复杂感受");
                break;
        }

        return emotion.toString();
    }

    private String inferBelief(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Use detected themes and intent to infer belief patterns
        if (analysis.detectedThemes.contains("自我评价")) {
            return "这件事也许让你再次怀疑自己,但一件事没做好不等于整个人不行.";
        }
        if (analysis.primaryIntent.equals("RELATION_ISSUE")) {
            return "这段关系也许让你想起一些旧的感受,我们先把现在和过去分开看.";
        }
        if (analysis.sentimentScore <= -3) {
            return "现在很难,但这不说明你不够好,只说明你现在需要一些支持.";
        }
        return "我正在尝试理解自己为什么会被这件事牵动.";
    }

    private String inferAction(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Use primary intent to suggest action
        switch (analysis.primaryIntent) {
            case "TASK_STRESS":
                return "明天先打开任务文件,只做十分钟.";
            case "RELATION_ISSUE":
                return "先写下对方说了什么,以及我实际感受到什么.";
            case "SELF_HARM":
                return "现在最小的一步,是先让自己活下来,其他的明天再说.";
            case "COGNITIVE_CLARITY":
                return "把现在最乱的一句话写下来,明天再看一次.";
            default:
                return "把今天最重的一句话保存下来,明天再看一次.";
        }
    }

    private String inferNeed(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Infer need from sentiment and themes
        if (analysis.sentimentScore <= -3) {
            return "需要温柔的支持和允许自己休息";
        }
        if (analysis.primaryIntent.equals("SEEK_SUPPORT")) {
            return "需要被理解和被看见";
        }
        if (analysis.detectedThemes.contains("任务压力")) {
            return "需要把压力拆成可开始的小步";
        }
        return "需要被看见和理解";
    }

    private String inferWorry(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Use detected themes to identify worry
        if (analysis.detectedThemes.contains("任务压力")) {
            return "担心任务完成不了,或者结果不如预期";
        }
        if (analysis.detectedThemes.contains("关系牵动")) {
            return "担心这段关系会变差,或者不被理解";
        }
        if (analysis.sentimentScore <= -3) {
            return "有一些深层的担心还没有被说出来";
        }
        return "有一个还没有被说清楚的担心";
    }

    private String inferEmotionName(String raw) {
        AnalysisResult analysis = analyze(raw);
        // Map sentiment label to emotion name
        switch (analysis.sentimentLabel) {
            case "CRISIS":
                return "危机";
            case "NEGATIVE":
                if (analysis.detectedThemes.contains("情绪承压")) return "焦虑";
                if (analysis.detectedThemes.contains("关系牵动")) return "难过";
                return "负面";
            case "POSITIVE":
                return "积极";
            default:
                return "平静";
        }
    }

    private String inferWeather(Double intensity) {
        double value = intensity == null ? 0 : intensity;
        if (value >= 7) return "STORM";
        if (value >= 5) return "RAINY";
        if (value >= 3) return "CLOUDY";
        return "SUNNY";
    }

    private String inferTimeLabel(String raw) {
        if (raw.contains("今天")) return "今天";
        if (raw.contains("昨天")) return "昨天";
        if (raw.contains("上周")) return "上周";
        return "近期";
    }

    private String inferRelationLabel(String raw) {
        if (raw.contains("朋友")) return "朋友";
        if (raw.contains("同学")) return "同学";
        if (raw.contains("老师")) return "老师";
        if (raw.contains("家人")) return "家人";
        return "关系";
    }

    private String inferRelationType(String raw) {
        if (raw.contains("冲突") || raw.contains("吵架")) return "CONFLICT";
        if (raw.contains("支持") || raw.contains("帮助")) return "SUPPORT";
        if (raw.contains("疏远") || raw.contains("不联系")) return "DISTANCE";
        return "GENERAL";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleDiary(Long userId, String diaryText) {
        if (diaryText == null || diaryText.isBlank()) {
            return;
        }

        StructuredAiResults.SettlementResult ai = structuredAiService.call(userId, "DIARY_SETTLEMENT",
                """
                Return JSON for a private diary memory settlement:
                memoryCard {title, summary, memoryType, emotionTags[], keywordTags[], peopleTags[], intensityScore, userImportance},
                fragments[] {type, rawExcerpt, analysis, reframe},
                emotionTrace {emotionName, emotionScore, weatherType, triggerScene},
                eventCards[], relationMentions[], todos[].
                Extract only from the diary text. Keep it non-clinical and privacy-preserving.
                """,
                Map.of("diaryText", diaryText),
                StructuredAiResults.SettlementResult.class,
                () -> fallbackSettlement(diaryText));

        // Extract MemoryCard
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.sourceSessionId = null; // No session associated with diary
        card.title = blank(ai.memoryCard.title, "今日心声日记");
        card.summary = blank(ai.memoryCard.summary, firstSentence(diaryText));
        card.memoryType = blank(ai.memoryCard.memoryType, inferType(diaryText));
        card.emotionTags = jsonArray(ai.memoryCard.emotionTags, List.of("diary-reflection"));
        card.keywordTags = jsonArray(ai.memoryCard.keywordTags, fallbackKeywords(diaryText));
        card.peopleTags = jsonArray(ai.memoryCard.peopleTags, List.of());
        card.intensityScore = ai.memoryCard.intensityScore == null ? inferIntensity(diaryText) : ai.memoryCard.intensityScore;
        card.recurrenceCount = 1;
        card.userImportance = ai.memoryCard.userImportance == null ? 4.0 : ai.memoryCard.userImportance;
        card.triggerCount = 1;
        card.emotionalGravity = gravityService.calculateGravity(card.intensityScore, 1, card.userImportance, 1, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        memoryCardMapper.insert(card);

        // Create structured assets: fragments
        if (ai.fragments != null && !ai.fragments.isEmpty()) {
            for (StructuredAiResults.Fragment fragment : ai.fragments) {
                createFragment(userId, card.id, blank(fragment.type, "OBSERVATION"),
                        blank(fragment.rawExcerpt, firstSentence(diaryText)),
                        blank(fragment.analysis, "从日记表达中抽取出的片段."),
                        blank(fragment.reframe, "先把它放成一个可以看见的形状."));
            }
        } else {
            createFragment(userId, card.id, "FACT", firstSentence(diaryText), "从日记表达中抽取出的事实片段.", "先区分事实和解释.");
            createFragment(userId, card.id, "FEELING", inferEmotion(diaryText), "日记里出现的主要感受线索.", "允许感受存在,不急着证明它合理.");
            createFragment(userId, card.id, "BELIEF", inferBelief(diaryText), "可能影响用户自我评价的信念.", "把事件和自我价值暂时分开看.");
            createFragment(userId, card.id, "ACTION", inferAction(diaryText), "可以轻轻推进的一步.", "把下一步压缩到十分钟内能开始.");
        }

        if (diaryText.contains("需要") || diaryText.contains("想要")) {
            createFragment(userId, card.id, "NEED", inferNeed(diaryText), "表达中隐含的深层需要.", "把需要从期待中分出来看看.");
        }
        if (diaryText.contains("担心") || diaryText.contains("害怕") || diaryText.contains("焦虑")) {
            createFragment(userId, card.id, "WORRY", inferWorry(diaryText), "来自日记的担忧.", "先承认这个担心是合理的.");
        }

        // Create EmotionTrace
        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.sourceSessionId = null;
        trace.emotionName = blank(ai.emotionTrace.emotionName, inferEmotionName(diaryText));
        trace.emotionScore = ai.emotionTrace.emotionScore == null ? card.intensityScore : ai.emotionTrace.emotionScore;
        trace.weatherType = blank(ai.emotionTrace.weatherType, inferWeather(card.intensityScore));
        trace.triggerScene = blank(ai.emotionTrace.triggerScene, firstSentence(diaryText));
        trace.recordDate = LocalDate.now();
        emotionTraceMapper.insert(trace);

        // Create TodoItems
        if (ai.todos != null && !ai.todos.isEmpty()) {
            for (StructuredAiResults.TodoSuggestion suggestion : ai.todos) {
                TodoItem todo = new TodoItem();
                todo.userId = userId;
                todo.sourceMemoryCardId = card.id;
                todo.taskName = blank(suggestion.taskName, "由日记自动提取的待办");
                todo.description = blank(suggestion.description, "由日记自动提取,建议从一个小动作开始.");
                todo.priority = blank(suggestion.priority, "MEDIUM");
                todo.status = "TODO";
                todoItemMapper.insert(todo);
            }
        }

        // Create EventCards
        if (ai.eventCards != null && !ai.eventCards.isEmpty()) {
            for (StructuredAiResults.Event item : ai.eventCards) {
                EventCard event = new EventCard();
                event.userId = userId;
                event.sourceSessionId = null;
                event.memoryCardId = card.id;
                event.eventTitle = blank(item.eventTitle, firstSentence(diaryText));
                event.eventSummary = blank(item.eventSummary, firstSentence(diaryText));
                event.eventTimeLabel = blank(item.eventTimeLabel, inferTimeLabel(diaryText));
                event.scene = blank(item.scene, "从日记中提取的事件场景");
                event.peopleTags = jsonArray(item.peopleTags, List.of());
                event.emotionTags = jsonArray(item.emotionTags, ai.memoryCard.emotionTags);
                eventCardMapper.insert(event);
            }
        }

        // Create RelationMentions
        if (ai.relationMentions != null && !ai.relationMentions.isEmpty()) {
            for (StructuredAiResults.Relation item : ai.relationMentions) {
                RelationMention mention = new RelationMention();
                mention.userId = userId;
                mention.sourceSessionId = null;
                mention.memoryCardId = card.id;
                mention.relationLabel = blank(item.relationLabel, inferRelationLabel(diaryText));
                mention.relationType = blank(item.relationType, inferRelationType(diaryText));
                mention.emotionTags = jsonArray(item.emotionTags, ai.memoryCard.emotionTags);
                mention.triggerSummary = blank(item.triggerSummary, firstSentence(diaryText));
                mention.boundaryHint = blank(item.boundaryHint, "注意关系的边界和自己的感受");
                relationMentionMapper.insert(mention);
            }
        }

        // Create a DailyRecord directly for the diary
        DailyRecord record = new DailyRecord();
        record.userId = userId;
        record.recordDate = LocalDate.now();
        record.sourceSessionId = null;
        record.theme = card.title;
        record.eventSummary = card.summary;
        record.emotionWeather = inferWeather(card.intensityScore);
        record.cognitiveSummary = firstSentence(card.summary);
        record.todoSummary = "来自日记提取";
        record.auroraSummary = "完成了一篇心声日记记叙。";
        record.capsuleSuggested = card.emotionalGravity != null && card.emotionalGravity > 1.1;
        record.userAccepted = true; // Auto accepted for diary
        record.status = "ACTIVE";
        dailyRecordMapper.insert(record);

        // Update themes
        themeAggregationService.aggregateThemes(userId);
    }
}
