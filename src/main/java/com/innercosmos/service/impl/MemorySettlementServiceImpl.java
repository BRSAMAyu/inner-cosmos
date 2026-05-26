package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
                                       ThemeAggregationService themeAggregationService) {
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

        // Extract MemoryCard
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.sourceSessionId = sessionId;
        card.title = "今日沉淀";
        card.summary = firstSentence(raw);
        card.memoryType = inferType(raw);
        card.emotionTags = "[\"self-observation\"]";
        card.keywordTags = inferKeywords(raw);
        card.peopleTags = "[]";
        card.intensityScore = inferIntensity(raw);
        card.recurrenceCount = 1;
        card.userImportance = 4.0;
        card.triggerCount = 1;
        card.emotionalGravity = gravityService.calculateGravity(card.intensityScore, 1, card.userImportance, 1, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        memoryCardMapper.insert(card);

        // Create structured assets: fragments
        createFragment(userId, card.id, "FACT", firstSentence(raw), "从用户表达中抽取出的事实片段。", "先区分事实和解释。");
        createFragment(userId, card.id, "FEELING", inferEmotion(raw), "表达里出现的主要感受线索。", "允许感受存在，不急着证明它合理。");
        createFragment(userId, card.id, "BELIEF", inferBelief(raw), "可能影响用户自我评价的信念。", "把事件和自我价值暂时分开看。");
        createFragment(userId, card.id, "ACTION", inferAction(raw), "可以轻轻推进的一步。", "把下一步压缩到十分钟内能开始。");

        if (raw.contains("需要") || raw.contains("想要")) {
            createFragment(userId, card.id, "NEED", inferNeed(raw), "表达中隐含的深层需要。", "把需要从期待中分出来看看。");
        }
        if (raw.contains("担心") || raw.contains("害怕") || raw.contains("焦虑")) {
            createFragment(userId, card.id, "WORRY", inferWorry(raw), "来自用户的担忧。", "先承认这个担心是合理的。");
        }

        // Create EmotionTrace
        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.sourceSessionId = sessionId;
        trace.emotionName = inferEmotionName(raw);
        trace.emotionScore = card.intensityScore;
        trace.weatherType = inferWeather(card.intensityScore);
        trace.triggerScene = firstSentence(raw);
        trace.recordDate = LocalDate.now();
        emotionTraceMapper.insert(trace);

        // Create TodoItems if task-related
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

        // Create EventCards if event-related
        if (raw.contains("今天") || raw.contains("昨天") || raw.contains("上周") || raw.contains("发生")) {
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
        if (raw.contains("朋友") || raw.contains("同学") || raw.contains("老师") || raw.contains("家人") || raw.contains("关系")) {
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
            vo.auroraSummary = "和 Aurora 完成一次对话后，这里会生成今日记录卡。";
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

    private String firstSentence(String raw) {
        if (raw == null || raw.isBlank()) {
            return "用户完成了一次自我表达。";
        }
        String compact = raw.replaceAll("\\s+", " ").trim();
        return compact.length() > 64 ? compact.substring(0, 64) + "..." : compact;
    }

    private String inferType(String raw) {
        if (raw.contains("作业") || raw.contains("考试") || raw.contains("任务")) return "TODO";
        if (raw.contains("朋友") || raw.contains("同学") || raw.contains("关系")) return "RELATION";
        if (raw.contains("想") || raw.contains("觉得")) return "COGNITION";
        return "EMOTION";
    }

    private String inferKeywords(String raw) {
        List<String> keywords = new ArrayList<>();
        if (raw.contains("作业") || raw.contains("考试")) keywords.add("学业");
        if (raw.contains("朋友") || raw.contains("同学")) keywords.add("社交");
        if (raw.contains("家人")) keywords.add("家庭");
        if (raw.contains("累") || raw.contains("压力")) keywords.add("压力");
        if (raw.contains("开心") || raw.contains("高兴")) keywords.add("积极");
        if (keywords.isEmpty()) keywords.add("日常");
        return keywords.toString();
    }

    private Double inferIntensity(String raw) {
        if (raw.contains("非常") || raw.contains("特别") || raw.contains("极度")) return 8.0;
        if (raw.contains("很") || raw.contains("挺")) return 6.0;
        if (raw.contains("有点") || raw.contains("稍微")) return 3.5;
        return 5.0;
    }

    private String inferEmotion(String raw) {
        if (raw.contains("累") || raw.contains("压力")) return "疲惫和压力";
        if (raw.contains("烦") || raw.contains("不舒服")) return "烦躁和不舒服";
        if (raw.contains("开心") || raw.contains("高兴")) return "明亮和开心";
        if (raw.contains("孤独") || raw.contains("没人懂")) return "孤独和未被理解";
        return "还没有被命名的复杂感受";
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

    private String inferNeed(String raw) {
        if (raw.contains("需要休息") || raw.contains("想要休息")) return "需要一个不被打扰的空间";
        if (raw.contains("需要帮助") || raw.contains("想要帮助")) return "需要一个可以求助的人";
        return "需要被看见和理解";
    }

    private String inferWorry(String raw) {
        if (raw.contains("考试")) return "担心考试考不好";
        if (raw.contains("朋友")) return "担心这段关系会变差";
        return "有一个还没有被说清楚的担心";
    }

    private String inferEmotionName(String raw) {
        if (raw.contains("开心") || raw.contains("高兴")) return "开心";
        if (raw.contains("孤独")) return "孤独";
        if (raw.contains("烦")) return "烦躁";
        if (raw.contains("累")) return "疲惫";
        if (raw.contains("担心") || raw.contains("害怕")) return "焦虑";
        return "复杂";
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
}
