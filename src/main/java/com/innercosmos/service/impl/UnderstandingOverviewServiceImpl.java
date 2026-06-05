package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.EventCard;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.EventCardMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.UnderstandingOverviewService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UnderstandingOverviewServiceImpl implements UnderstandingOverviewService {
    private final DailyRecordMapper dailyRecordMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final MemoryThemeMapper memoryThemeMapper;
    private final EventCardMapper eventCardMapper;

    public UnderstandingOverviewServiceImpl(DailyRecordMapper dailyRecordMapper,
                                            EmotionTraceMapper emotionTraceMapper,
                                            MemoryCardMapper memoryCardMapper,
                                            TodoItemMapper todoItemMapper,
                                            RelationMentionMapper relationMentionMapper,
                                            MemoryThemeMapper memoryThemeMapper,
                                            EventCardMapper eventCardMapper) {
        this.dailyRecordMapper = dailyRecordMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.memoryThemeMapper = memoryThemeMapper;
        this.eventCardMapper = eventCardMapper;
    }

    @Override
    public Map<String, Object> overview(Long userId, int rangeDays) {
        int days = rangeDays == 30 ? 30 : 7;
        LocalDate since = LocalDate.now().minusDays(days - 1);
        LocalDateTime sinceTime = since.atStartOfDay();

        List<DailyRecord> daily = dailyRecordMapper.selectList(new QueryWrapper<DailyRecord>()
                .eq("user_id", userId).ge("record_date", since).orderByAsc("record_date"));
        List<EmotionTrace> emotions = emotionTraceMapper.selectList(new QueryWrapper<EmotionTrace>()
                .eq("user_id", userId).ge("record_date", since).orderByAsc("record_date"));
        List<MemoryCard> memories = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).ge("created_at", sinceTime).orderByDesc("emotional_gravity"));
        List<TodoItem> todos = todoItemMapper.selectList(new QueryWrapper<TodoItem>().eq("user_id", userId));
        List<RelationMention> relations = relationMentionMapper.selectList(new QueryWrapper<RelationMention>()
                .eq("user_id", userId).ge("created_at", sinceTime).orderByDesc("id"));
        List<MemoryTheme> themes = memoryThemeMapper.selectList(new QueryWrapper<MemoryTheme>()
                .eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("average_gravity").last("LIMIT 12"));
        List<EventCard> events = eventCardMapper.selectList(new QueryWrapper<EventCard>()
                .eq("user_id", userId).ge("created_at", sinceTime).orderByDesc("id").last("LIMIT 12"));

        long completedTodos = todos.stream().filter(t -> "DONE".equals(t.status)).count();
        long activeTodos = todos.stream().filter(t -> !"DONE".equals(t.status) && !"CANCELLED".equals(t.status)).count();
        int rememberedChars = memories.stream().mapToInt(m -> length(m.summary) + length(m.title)).sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rangeDays", days);
        data.put("dailyRecords", daily);
        data.put("emotionTraces", emotions);
        data.put("newMemories", memories.stream().limit(10).toList());
        data.put("importantEvents", events);
        data.put("relationSignals", relations);
        data.put("themeEvolution", themes);
        data.put("todoStats", Map.of("active", activeTodos, "completed", completedTodos, "total", todos.size()));
        data.put("systemLearning", Map.of(
                "rememberedMemoryCount", memories.size(),
                "rememberedChars", rememberedChars,
                "relationSignalCount", relations.size(),
                "themeCount", themes.size(),
                "auroraObservation", auroraObservation(daily, memories, themes)
        ));
        data.put("evidence", memories.stream()
                .map(m -> Map.of("memoryId", m.id, "title", safe(m.title), "summary", safe(m.summary)))
                .limit(8).toList());
        return data;
    }

    private String auroraObservation(List<DailyRecord> daily, List<MemoryCard> memories, List<MemoryTheme> themes) {
        if (!daily.isEmpty()) {
            DailyRecord latest = daily.get(daily.size() - 1);
            if (latest.auroraSummary != null && !latest.auroraSummary.isBlank()) return latest.auroraSummary;
        }
        if (!themes.isEmpty()) return "Aurora 最近主要看见的主题是：" + themes.get(0).themeName;
        if (!memories.isEmpty()) return "Aurora 正在从新的记忆里学习你最近的状态。";
        return "还没有足够的数据形成稳定观察。";
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
