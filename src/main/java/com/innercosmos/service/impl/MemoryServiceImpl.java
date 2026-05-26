package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.MemoryExtractAgent;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.ThemeAggregationService;
import com.innercosmos.vo.DailyRecordVO;
import com.innercosmos.vo.StarfieldDetailVO;
import com.innercosmos.vo.StarfieldVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryServiceImpl implements MemoryService {
    private final MemoryCardMapper memoryCardMapper;
    private final DialogMessageMapper messageMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final TodoItemMapper todoItemMapper;
    private final GravityService gravityService;
    private final MemoryExtractAgent extractAgent;
    private final RelationMentionMapper relationMentionMapper;
    private final ThemeAggregationService themeAggregationService;
    private final DailyRecordMapper dailyRecordMapper;

    public MemoryServiceImpl(MemoryCardMapper memoryCardMapper,
                             DialogMessageMapper messageMapper,
                             ThoughtFragmentMapper thoughtFragmentMapper,
                             EmotionTraceMapper emotionTraceMapper,
                             TodoItemMapper todoItemMapper,
                             GravityService gravityService,
                             MemoryExtractAgent extractAgent,
                             RelationMentionMapper relationMentionMapper,
                             ThemeAggregationService themeAggregationService,
                             DailyRecordMapper dailyRecordMapper) {
        this.memoryCardMapper = memoryCardMapper;
        this.messageMapper = messageMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.todoItemMapper = todoItemMapper;
        this.gravityService = gravityService;
        this.extractAgent = extractAgent;
        this.relationMentionMapper = relationMentionMapper;
        this.themeAggregationService = themeAggregationService;
        this.dailyRecordMapper = dailyRecordMapper;
    }

    @Override
    public MemoryCard extractFromSession(Long userId, Long sessionId) {
        QueryWrapper<DialogMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).eq("speaker", "USER").orderByAsc("id");
        List<DialogMessage> messages = messageMapper.selectList(query);
        String raw = messages.stream().map(m -> m.textContent).reduce("", (a, b) -> a + "\n" + b);
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.sourceSessionId = sessionId;
        card.title = "今日沉淀";
        card.summary = extractAgent.summarize(raw);
        card.memoryType = inferType(raw);
        card.emotionTags = "[\"self-observation\"]";
        card.keywordTags = "[\"aurora\",\"daily\"]";
        card.peopleTags = "[]";
        card.intensityScore = raw.contains("很") || raw.contains("特别") ? 7.0 : 4.5;
        card.recurrenceCount = 1;
        card.userImportance = 4.0;
        card.triggerCount = 1;
        card.emotionalGravity = gravityService.calculateGravity(card.intensityScore, 1, card.userImportance, 1, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        memoryCardMapper.insert(card);
        createStructuredAssets(userId, sessionId, card, raw);
        return card;
    }

    @Override
    public List<MemoryCard> listCards(Long userId) {
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("emotional_gravity");
        return memoryCardMapper.selectList(query);
    }

    @Override
    public List<StarfieldVO> starfield(Long userId) {
        List<MemoryCard> cards = listCards(userId);
        List<StarfieldVO> result = new ArrayList<>();
        int i = 0;
        for (MemoryCard card : cards) {
            StarfieldVO vo = new StarfieldVO();
            vo.id = card.id;
            vo.title = card.title;
            vo.memoryType = card.memoryType;
            vo.gravity = card.emotionalGravity == null ? 0 : card.emotionalGravity;
            vo.x = Math.cos(i * 1.7) * (40 + i * 6);
            vo.y = Math.sin(i * 1.7) * (40 + i * 6);
            vo.summary = card.summary;
            vo.theme = starTheme(card.memoryType);
            vo.color = starColor(card.memoryType);
            vo.glow = Math.min(1.0, 0.34 + vo.gravity * 0.22);
            vo.freshness = freshness(card.lastTouchedAt);
            vo.suggestedForCapsule = vo.gravity > 1.1;
            vo.detail = buildStarDetail(card, vo);
            result.add(vo);
            i++;
        }
        return result;
    }

    @Override
    public DailyRecordVO latestDailyRecord(Long userId) {
        QueryWrapper<MemoryCard> memoryQuery = new QueryWrapper<>();
        memoryQuery.eq("user_id", userId).orderByDesc("id").last("LIMIT 1");
        MemoryCard card = memoryCardMapper.selectOne(memoryQuery);
        DailyRecordVO vo = new DailyRecordVO();
        if (card == null) {
            vo.theme = "还没有沉淀出的今日主题";
            vo.auroraSummary = "和 Aurora 完成一次对话后，这里会生成今日记录卡。";
            return vo;
        }
        vo.mainMemory = card;
        vo.theme = card.title;
        vo.auroraSummary = card.summary;
        vo.capsuleSuggested = card.emotionalGravity != null && card.emotionalGravity > 1.1;

        QueryWrapper<ThoughtFragment> fragmentQuery = new QueryWrapper<>();
        fragmentQuery.eq("memory_card_id", card.id).orderByAsc("id");
        vo.fragments = thoughtFragmentMapper.selectList(fragmentQuery);

        QueryWrapper<EmotionTrace> emotionQuery = new QueryWrapper<>();
        emotionQuery.eq("source_session_id", card.sourceSessionId).orderByDesc("id");
        vo.emotions = emotionTraceMapper.selectList(emotionQuery);

        QueryWrapper<TodoItem> todoQuery = new QueryWrapper<>();
        todoQuery.eq("source_memory_card_id", card.id).orderByAsc("id");
        vo.todos = todoItemMapper.selectList(todoQuery);
        return vo;
    }

    @Override
    public List<String> topGravitySummaries(Long userId, int limit) {
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("status", "ACTIVE")
                .gt("emotional_gravity", 0)
                .orderByDesc("emotional_gravity")
                .last("LIMIT " + limit);
        List<MemoryCard> cards = memoryCardMapper.selectList(query);
        List<String> summaries = new ArrayList<>();
        for (MemoryCard card : cards) {
            summaries.add(card.title + "（gravity=" + String.format("%.2f", card.emotionalGravity) + "）：" + card.summary);
        }
        return summaries;
    }

    private String inferType(String raw) {
        if (raw.contains("作业") || raw.contains("考试") || raw.contains("任务")) {
            return "TODO";
        }
        if (raw.contains("朋友") || raw.contains("同学") || raw.contains("关系")) {
            return "RELATION";
        }
        if (raw.contains("想") || raw.contains("觉得")) {
            return "COGNITION";
        }
        return "EMOTION";
    }

    private void createStructuredAssets(Long userId, Long sessionId, MemoryCard card, String raw) {
        createFragment(userId, card.id, "FACT", firstSentence(raw), "从用户表达中抽取出的事实片段。", "先区分事实和解释。");
        createFragment(userId, card.id, "FEELING", inferEmotion(raw), "表达里出现的主要感受线索。", "允许感受存在，不急着证明它合理。");
        createFragment(userId, card.id, "BELIEF", inferBelief(raw), "可能影响用户自我评价的信念。", "把事件和自我价值暂时分开看。");
        createFragment(userId, card.id, "ACTION", inferAction(raw), "可以轻轻推进的一步。", "把下一步压缩到十分钟内能开始。");

        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.sourceSessionId = sessionId;
        trace.emotionName = inferEmotionName(raw);
        trace.emotionScore = card.intensityScore;
        trace.weatherType = inferWeather(card.intensityScore);
        trace.triggerScene = firstSentence(raw);
        trace.recordDate = LocalDate.now();
        emotionTraceMapper.insert(trace);

        if (raw.contains("作业") || raw.contains("考试") || raw.contains("任务") || raw.contains("明天") || raw.contains("拖延")) {
            TodoItem todo = new TodoItem();
            todo.userId = userId;
            todo.sourceMemoryCardId = card.id;
            todo.taskName = raw.contains("作业") ? "推进 Java 作业的第一步" : "把今天提到的任务拆成第一步";
            todo.description = "由 Aurora 对话自动提取，建议从一个十分钟动作开始。";
            todo.priority = raw.contains("考试") ? "HIGH" : "MEDIUM";
            todo.status = "TODO";
            todoItemMapper.insert(todo);
        }
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

    private String firstSentence(String raw) {
        if (raw == null || raw.isBlank()) {
            return "用户完成了一次自我表达。";
        }
        String compact = raw.replaceAll("\\s+", " ").trim();
        return compact.length() > 64 ? compact.substring(0, 64) + "..." : compact;
    }

    private String inferEmotion(String raw) {
        if (raw.contains("累") || raw.contains("压力")) return "疲惫和压力";
        if (raw.contains("烦") || raw.contains("不舒服")) return "烦躁和不舒服";
        if (raw.contains("开心") || raw.contains("高兴")) return "明亮和开心";
        if (raw.contains("孤独") || raw.contains("没人懂")) return "孤独和未被理解";
        return "还没有被命名的复杂感受";
    }

    private String inferEmotionName(String raw) {
        if (raw.contains("开心") || raw.contains("高兴")) return "开心";
        if (raw.contains("孤独")) return "孤独";
        if (raw.contains("烦")) return "烦躁";
        if (raw.contains("累")) return "疲惫";
        return "复杂";
    }

    private String inferBelief(String raw) {
        if (raw.contains("没做好") || raw.contains("不行")) {
            return "如果一件事没做好，就说明我这个人不行。";
        }
        if (raw.contains("没人懂")) {
            return "如果别人没有立刻理解我，也许我就只能一个人承受。";
        }
        return "我正在尝试理解自己为什么会被这件事牵动。";
    }

    private String inferAction(String raw) {
        if (raw.contains("作业") || raw.contains("任务")) {
            return "明天先打开任务文件，只做十分钟。";
        }
        if (raw.contains("关系") || raw.contains("朋友") || raw.contains("同学")) {
            return "先写下对方说了什么，以及我实际感受到什么。";
        }
        return "把今天最重的一句话保存下来，明天再看一次。";
    }

    private String inferWeather(Double intensity) {
        double value = intensity == null ? 0 : intensity;
        if (value >= 7) return "STORM";
        if (value >= 5) return "RAINY";
        if (value >= 3) return "CLOUDY";
        return "SUNNY";
    }

    private String starTheme(String memoryType) {
        if ("TODO".equals(memoryType)) return "需要被轻轻推进的事";
        if ("RELATION".equals(memoryType)) return "关系里的回声";
        if ("COGNITION".equals(memoryType)) return "正在成形的理解";
        return "被命名的感受";
    }

    private String starColor(String memoryType) {
        if ("TODO".equals(memoryType)) return "#b8afc6";
        if ("RELATION".equals(memoryType)) return "#c9a7a6";
        if ("COGNITION".equals(memoryType)) return "#a9b7c8";
        return "#9caf9f";
    }

    private Double freshness(LocalDateTime touchedAt) {
        if (touchedAt == null) {
            return 0.48;
        }
        long days = java.time.Duration.between(touchedAt, LocalDateTime.now()).toDays();
        if (days <= 0) return 0.96;
        if (days <= 3) return 0.78;
        if (days <= 7) return 0.58;
        return 0.36;
    }

    private String buildStarDetail(MemoryCard card, StarfieldVO vo) {
        String typeText = starTheme(card.memoryType);
        String suggestion = Boolean.TRUE.equals(vo.suggestedForCapsule)
                ? "这颗星的情感重力较高，适合进入脱敏预览后再决定是否编织为共鸣体。"
                : "这颗星可以先留在私人星图里，等它更清晰时再被分享。";
        return typeText + "。情感重力 " + String.format("%.2f", vo.gravity) + "。 " + suggestion;
    }

    @Override
    public StarfieldDetailVO starfieldDetail(Long userId, Long cardId) {
        MemoryCard card = memoryCardMapper.selectById(cardId);
        if (card == null || !userId.equals(card.userId)) {
            return null;
        }
        StarfieldDetailVO detail = new StarfieldDetailVO();
        detail.card = card;

        QueryWrapper<ThoughtFragment> fragmentQuery = new QueryWrapper<>();
        fragmentQuery.eq("memory_card_id", cardId).orderByAsc("id");
        detail.fragments = thoughtFragmentMapper.selectList(fragmentQuery);

        QueryWrapper<TodoItem> todoQuery = new QueryWrapper<>();
        todoQuery.eq("source_memory_card_id", cardId).orderByAsc("id");
        detail.todos = todoItemMapper.selectList(todoQuery);

        QueryWrapper<EmotionTrace> emotionQuery = new QueryWrapper<>();
        emotionQuery.eq("source_session_id", card.sourceSessionId).orderByDesc("id");
        detail.emotions = emotionTraceMapper.selectList(emotionQuery);

        detail.relations = relationMentionMapper.selectList(
                new QueryWrapper<com.innercosmos.entity.RelationMention>().eq("memory_card_id", cardId));
        detail.themes = themeAggregationService.themesForCard(cardId);
        detail.gravityExplanation = "情感重力 " + String.format("%.2f", card.emotionalGravity)
                + "，由情绪强度 " + card.intensityScore
                + "、用户重要性 " + card.userImportance
                + "、出现次数 " + card.recurrenceCount + " 综合计算。";
        detail.auroraObservation = card.summary;
        detail.canCreateCapsule = card.emotionalGravity != null && card.emotionalGravity > 1.1;
        return detail;
    }

    @Override
    public void updateImportance(Long userId, Long cardId, Double importance) {
        MemoryCard card = memoryCardMapper.selectById(cardId);
        if (card == null || !userId.equals(card.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记忆卡");
        }
        card.userImportance = importance;
        card.emotionalGravity = gravityService.calculateGravity(
                card.intensityScore, card.recurrenceCount, importance, card.triggerCount, 0);
        memoryCardMapper.updateById(card);
    }

    @Override
    public void archiveCard(Long userId, Long cardId) {
        MemoryCard card = memoryCardMapper.selectById(cardId);
        if (card == null || !userId.equals(card.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记忆卡");
        }
        card.status = "ARCHIVED";
        memoryCardMapper.updateById(card);
    }

    @Override
    public List<DailyRecord> listDailyRecords(Long userId) {
        QueryWrapper<DailyRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("record_date");
        return dailyRecordMapper.selectList(query);
    }

    @Override
    public void acceptDailyRecord(Long userId, Long recordId) {
        DailyRecord record = dailyRecordMapper.selectById(recordId);
        if (record == null || !userId.equals(record.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记录");
        }
        record.userAccepted = true;
        dailyRecordMapper.updateById(record);
    }
}
