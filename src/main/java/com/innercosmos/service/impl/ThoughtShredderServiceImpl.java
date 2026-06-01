package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.service.ThoughtShredderService;
import com.innercosmos.vo.SafetyResult;
import com.innercosmos.vo.ShredderResultVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ThoughtShredderServiceImpl implements ThoughtShredderService {
    private final MemoryCardMapper memoryCardMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final TodoItemMapper todoItemMapper;
    private final GravityService gravityService;
    private final SafetyService safetyService;
    private final StructuredAiService structuredAiService;

    public ThoughtShredderServiceImpl(MemoryCardMapper memoryCardMapper,
                                      ThoughtFragmentMapper thoughtFragmentMapper,
                                      TodoItemMapper todoItemMapper,
                                      GravityService gravityService,
                                      SafetyService safetyService,
                                      StructuredAiService structuredAiService) {
        this.memoryCardMapper = memoryCardMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.todoItemMapper = todoItemMapper;
        this.gravityService = gravityService;
        this.safetyService = safetyService;
        this.structuredAiService = structuredAiService;
    }

    @Override
    public ShredderResultVO process(Long userId, String rawText, String originalHandlingMode) {
        SafetyResult safety = safetyService.check(rawText, userId, null);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            com.innercosmos.exception.SafetyBlockedException blocked =
                    new com.innercosmos.exception.SafetyBlockedException(safety.safeMessage);
            throw blocked;
        }

        String raw = normalize(rawText);
        String mode = normalizeMode(originalHandlingMode);
        StructuredAiResults.ShredderResult ai = structuredAiService.call(userId, "THOUGHT_SHREDDER",
                """
                Return JSON for a thought-shredder result:
                coreFeeling, hiddenNeed, noiseToDrop array, sentenceToKeep,
                fragments array with type/rawExcerpt/analysis/reframe,
                suggestedTodo object when a concrete action exists, intensityScore 0-10, memoryType.
                Keep wording warm, concrete, non-clinical, and in Chinese if the input is Chinese.
                """,
                java.util.Map.of("rawText", raw, "handlingMode", mode),
                StructuredAiResults.ShredderResult.class,
                () -> fallbackShredder(raw));
        String coreFeeling = blank(ai.coreFeeling, inferCoreFeeling(raw));
        String hiddenNeed = blank(ai.hiddenNeed, inferHiddenNeed(raw, coreFeeling));
        String sentenceToKeep = blank(ai.sentenceToKeep, sentenceToKeep(raw, coreFeeling, hiddenNeed));
        List<String> noiseToDrop = ai.noiseToDrop == null || ai.noiseToDrop.isEmpty() ? noiseToDrop(raw) : ai.noiseToDrop;
        double intensity = ai.intensityScore == null ? inferIntensity(raw) : Math.max(0, Math.min(10, ai.intensityScore));

        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = "从混乱里留下的一句话";
        card.summary = sentenceToKeep;
        card.memoryType = blank(ai.memoryType, "SHREDDER");
        card.emotionTags = "[\"" + json(coreFeeling) + "\"]";
        card.keywordTags = "[\"thought-shredder\",\"" + json(hiddenNeed) + "\"]";
        card.peopleTags = "[]";
        card.intensityScore = intensity;
        card.recurrenceCount = 1;
        card.userImportance = 3.0;
        card.triggerCount = 1;
        card.emotionalGravity = gravityService.calculateGravity(intensity, 1, 3, 1, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE";
        card.status = "DISPLAY_ONCE".equals(mode) ? "TRANSIENT" : "ACTIVE";
        memoryCardMapper.insert(card);

        List<ThoughtFragment> fragments = new ArrayList<>();
        if (ai.fragments != null && !ai.fragments.isEmpty()) {
            for (StructuredAiResults.Fragment fragment : ai.fragments) {
                fragments.add(createFragment(userId, card.id, blank(fragment.type, "OBSERVATION"),
                        blank(fragment.rawExcerpt, firstSentence(raw)),
                        blank(fragment.analysis, "这是模型从混乱表达里整理出的片段。"),
                        blank(fragment.reframe, "先把它放成一个可以看见的形状。")));
            }
        }
        while (fragments.size() < 4) {
            if (fragments.size() == 0) fragments.add(createFragment(userId, card.id, "FEELING", firstSentence(raw), "混乱里最先需要被承认的是：" + coreFeeling, "先允许这个感受存在，不急着证明它合理。"));
            else if (fragments.size() == 1) fragments.add(createFragment(userId, card.id, "NEED", hiddenNeed, "这段表达背后可能有一个尚未被满足的需要。", "需要被看见并不等于脆弱，它只是说明这件事对你有重量。"));
            else if (fragments.size() == 2) fragments.add(createFragment(userId, card.id, "BELIEF", inferBelief(raw), "这里可能有一个过快的自我判断。", "把事情没做好和我这个人不行暂时分开。"));
            else fragments.add(createFragment(userId, card.id, "ACTION", inferAction(raw), "可以留下一个很小的下一步。", "下一步只需要小到十分钟内能开始。"));
        }

        TodoItem todo = maybeCreateTodo(userId, card.id, raw, hiddenNeed, ai.suggestedTodo);

        ShredderResultVO result = new ShredderResultVO();
        result.originalHandlingMode = mode;
        result.coreFeeling = coreFeeling;
        result.hiddenNeed = hiddenNeed;
        result.noiseToDrop = noiseToDrop;
        result.sentenceToKeep = sentenceToKeep;
        result.memoryCard = card;
        result.fragments = fragments;
        result.suggestedTodo = todo;
        return result;
    }

    @Override
    public List<MemoryCard> history(Long userId) {
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("memory_type", "SHREDDER").orderByDesc("id");
        return memoryCardMapper.selectList(query);
    }

    @Override
    public void settle(Long userId, Long memoryCardId) {
        MemoryCard card = memoryCardMapper.selectById(memoryCardId);
        if (card == null || !userId.equals(card.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记忆卡");
        }
        card.status = "ACTIVE";
        memoryCardMapper.updateById(card);
    }

    @Override
    public void delete(Long userId, Long memoryCardId) {
        MemoryCard card = memoryCardMapper.selectById(memoryCardId);
        if (card == null || !userId.equals(card.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记忆卡");
        }
        memoryCardMapper.deleteById(memoryCardId);
    }

    private ThoughtFragment createFragment(Long userId, Long cardId, String type, String rawExcerpt, String analysis, String reframe) {
        ThoughtFragment fragment = new ThoughtFragment();
        fragment.userId = userId;
        fragment.memoryCardId = cardId;
        fragment.fragmentType = type;
        fragment.rawExcerpt = rawExcerpt;
        fragment.aiAnalysis = analysis;
        fragment.reframeText = reframe;
        thoughtFragmentMapper.insert(fragment);
        return fragment;
    }

    private TodoItem maybeCreateTodo(Long userId, Long cardId, String raw, String hiddenNeed,
                                     StructuredAiResults.TodoSuggestion suggestion) {
        if (suggestion == null && !containsAny(raw, List.of("作业", "考试", "任务", "ddl", "截止", "明天", "拖延", "项目", "提交"))) {
            return null;
        }
        TodoItem todo = new TodoItem();
        todo.userId = userId;
        todo.sourceMemoryCardId = cardId;
        todo.taskName = suggestion == null || suggestion.taskName == null || suggestion.taskName.isBlank()
                ? (containsAny(raw, List.of("作业", "项目", "提交")) ? "把任务打开并推进十分钟" : "把压力源拆成一个小动作")
                : suggestion.taskName;
        todo.description = suggestion == null || suggestion.description == null || suggestion.description.isBlank()
                ? "由思维碎纸机从混乱输入中提取。它不是审判，只是为了满足这个需要：" + hiddenNeed
                : suggestion.description;
        todo.priority = suggestion == null || suggestion.priority == null || suggestion.priority.isBlank()
                ? (containsAny(raw, List.of("考试", "截止", "ddl")) ? "HIGH" : "MEDIUM")
                : suggestion.priority;
        todo.status = "TODO";
        todoItemMapper.insert(todo);
        return todo;
    }

    private String normalize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "我现在有点乱，还不知道该怎么说。";
        }
        return rawText.replaceAll("\\s+", " ").trim();
    }

    private StructuredAiResults.ShredderResult fallbackShredder(String raw) {
        StructuredAiResults.ShredderResult result = new StructuredAiResults.ShredderResult();
        result.coreFeeling = inferCoreFeeling(raw);
        result.hiddenNeed = inferHiddenNeed(raw, result.coreFeeling);
        result.sentenceToKeep = sentenceToKeep(raw, result.coreFeeling, result.hiddenNeed);
        result.noiseToDrop = noiseToDrop(raw);
        result.intensityScore = inferIntensity(raw);
        result.memoryType = "SHREDDER";
        return result;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeMode(String mode) {
        if ("KEEP_RAW".equals(mode) || "DISPLAY_ONCE".equals(mode)) {
            return mode;
        }
        return "KEEP_ONLY_RESULT";
    }

    private String inferCoreFeeling(String raw) {
        if (containsAny(raw, List.of("气", "愤怒", "烦", "受够"))) return "愤怒和烦躁";
        if (containsAny(raw, List.of("累", "疲惫", "压力", "撑不住"))) return "疲惫和压力";
        if (containsAny(raw, List.of("怕", "担心", "焦虑", "慌"))) return "焦虑和不安";
        if (containsAny(raw, List.of("孤独", "没人懂", "一个人"))) return "孤独和未被理解";
        if (containsAny(raw, List.of("委屈", "难过", "想哭"))) return "委屈和难过";
        return "还没被命名的复杂感受";
    }

    private String inferHiddenNeed(String raw, String coreFeeling) {
        if (containsAny(raw, List.of("没人懂", "看见", "理解", "在乎"))) return "被理解和被看见";
        if (containsAny(raw, List.of("作业", "任务", "项目", "拖延"))) return "把压力变成可开始的一步";
        if (containsAny(raw, List.of("朋友", "同学", "家人", "老师", "关系"))) return "在关系里保留自己的位置";
        if (containsAny(raw, List.of("不行", "失败", "没做好", "废物"))) return "把事件和自我价值分开";
        if (coreFeeling.contains("焦虑")) return "获得一点确定感";
        return "让混乱先有一个可以被放下的形状";
    }

    private String sentenceToKeep(String raw, String coreFeeling, String hiddenNeed) {
        return "我现在感到" + coreFeeling + "，背后也许是在需要" + hiddenNeed + "。";
    }

    private List<String> noiseToDrop(String raw) {
        List<String> noise = new ArrayList<>();
        noise.add("把一次混乱直接解释成“我整个人都不行”的结论。");
        if (containsAny(raw, List.of("应该", "必须", "一定"))) {
            noise.add("那些把自己逼到没有余地的“应该”和“必须”。");
        }
        if (containsAny(raw, List.of("永远", "每次", "总是", "从来"))) {
            noise.add("把今天扩大成永远的绝对化说法。");
        }
        if (noise.size() == 1) {
            noise.add("暂时不需要反复咀嚼的责备语气。");
        }
        return noise;
    }

    private String inferBelief(String raw) {
        if (containsAny(raw, List.of("不行", "失败", "没做好", "废物"))) {
            return "如果一件事没做好，就说明我这个人不行。";
        }
        if (containsAny(raw, List.of("没人懂", "一个人"))) {
            return "如果别人没有立刻理解我，也许我就只能一个人承受。";
        }
        return "这件事现在很乱，所以我可能会急着给自己下结论。";
    }

    private String inferAction(String raw) {
        if (containsAny(raw, List.of("作业", "任务", "项目", "提交"))) return "只打开任务文件，推进十分钟。";
        if (containsAny(raw, List.of("朋友", "同学", "家人", "老师"))) return "写下对方说了什么，以及我实际感受到什么。";
        return "把最重的一句话留下来，明天再看一次。";
    }

    private double inferIntensity(String raw) {
        double value = 4.5;
        if (containsAny(raw, List.of("崩溃", "撑不住", "受够", "绝望"))) value += 2.5;
        if (containsAny(raw, List.of("很", "特别", "真的", "太"))) value += 1.0;
        if (raw.length() > 120) value += 0.8;
        return Math.min(9.0, value);
    }

    private String firstSentence(String raw) {
        return raw.length() > 72 ? raw.substring(0, 72) + "..." : raw;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text != null && text.contains(keyword)) return true;
        }
        return false;
    }

    private String json(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
