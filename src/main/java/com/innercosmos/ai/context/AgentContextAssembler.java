package com.innercosmos.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.entity.WeeklyReview;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.mapper.WeeklyReviewMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class AgentContextAssembler {
    private final UserProfileMapper userProfileMapper;
    private final DialogMessageMapper dialogMessageMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final WeeklyReviewMapper weeklyReviewMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final MemoryThemeMapper memoryThemeMapper;

    public AgentContextAssembler(UserProfileMapper userProfileMapper,
                                 DialogMessageMapper dialogMessageMapper,
                                 MemoryCardMapper memoryCardMapper,
                                 TodoItemMapper todoItemMapper,
                                 DailyRecordMapper dailyRecordMapper,
                                 WeeklyReviewMapper weeklyReviewMapper,
                                 EmotionTraceMapper emotionTraceMapper,
                                 RelationMentionMapper relationMentionMapper,
                                 MemoryThemeMapper memoryThemeMapper) {
        this.userProfileMapper = userProfileMapper;
        this.dialogMessageMapper = dialogMessageMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.memoryThemeMapper = memoryThemeMapper;
    }

    public AgentContext assemble(Long userId, Long sessionId, String currentMessage, boolean includeMemory) {
        AgentContext context = new AgentContext();
        context.userId = userId;
        UserProfile profile = profile(userId);
        context.memoryRecallAllowed = includeMemory && allowMemory(profile);
        context.multiMessageAllowed = profile == null || profile.allowMultiMessage == null || Boolean.TRUE.equals(profile.allowMultiMessage);
        context.proactiveSensitivity = profile == null || profile.proactiveSensitivity == null ? 3 : profile.proactiveSensitivity;
        context.timeLabel = Boolean.FALSE.equals(profile == null ? null : profile.timeAwarenessEnabled) ? "用户关闭了时间感知" : timeLabel();
        context.weatherLabel = weatherLabel(userId, profile);
        context.environmentLabel = profile == null || blank(profile.currentEnvironmentLabel)
                ? inferEnvironment(currentMessage)
                : profile.currentEnvironmentLabel;
        context.profileSummary = profileSummary(profile);
        context.quietPolicy = quietPolicy(profile);
        context.focusPolicy = focusPolicy(profile, currentMessage);
        context.recentMessages = recentMessages(sessionId);
        context.activeTodos = activeTodos(userId);
        context.completedTodoLessons = completedTodoLessons(userId);
        context.dailyObservations = dailyObservations(userId);
        context.weeklyObservations = weeklyObservations(userId);
        context.relationSignals = relationSignals(userId);
        context.themeSignals = themeSignals(userId);
        if (Boolean.TRUE.equals(context.memoryRecallAllowed)) {
            List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                    .eq("user_id", userId)
                    .eq("status", "ACTIVE")
                    .orderByDesc("emotional_gravity")
                    .last("LIMIT 8"));
            for (MemoryCard card : cards) {
                context.longTermMemories.add("#" + card.id + " " + safe(card.title) + "：" + abbreviate(card.summary, 180));
                context.evidenceMemoryIds.add(card.id);
            }
        }
        return context;
    }

    private UserProfile profile(Long userId) {
        if (userId == null) return null;
        return userProfileMapper.selectOne(new QueryWrapper<UserProfile>().eq("user_id", userId).last("LIMIT 1"));
    }

    private boolean allowMemory(UserProfile profile) {
        return profile == null || profile.allowMemoryRecall == null || Boolean.TRUE.equals(profile.allowMemoryRecall);
    }

    private List<String> recentMessages(Long sessionId) {
        if (sessionId == null) return List.of();
        List<DialogMessage> rows = dialogMessageMapper.selectList(new QueryWrapper<DialogMessage>()
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("LIMIT 10"));
        java.util.Collections.reverse(rows);
        return rows.stream()
                .map(m -> ("USER".equals(m.speaker) ? "用户" : "Aurora") + "：" + abbreviate(m.textContent, 140))
                .toList();
    }

    private List<String> activeTodos(Long userId) {
        return todoItemMapper.selectList(new QueryWrapper<TodoItem>()
                        .eq("user_id", userId)
                        .notIn("status", List.of("DONE", "CANCELLED"))
                        .orderByDesc("priority")
                        .orderByAsc("deadline")
                        .last("LIMIT 6"))
                .stream()
                .map(t -> safe(t.taskName) + "（" + safe(t.priority) + "/" + safe(t.status) + deadlineText(t) + "）")
                .toList();
    }

    private List<String> completedTodoLessons(Long userId) {
        return todoItemMapper.selectList(new QueryWrapper<TodoItem>()
                        .eq("user_id", userId)
                        .eq("status", "DONE")
                        .orderByDesc("updated_at")
                        .last("LIMIT 4"))
                .stream()
                .map(t -> "已完成：" + safe(t.taskName) + "；经验：用户能从小入口推进。")
                .toList();
    }

    private List<String> dailyObservations(Long userId) {
        return dailyRecordMapper.selectList(new QueryWrapper<DailyRecord>()
                        .eq("user_id", userId)
                        .ge("record_date", LocalDate.now().minusDays(7))
                        .orderByDesc("record_date")
                        .last("LIMIT 5"))
                .stream()
                .map(d -> safe(d.recordDate) + " " + safe(d.theme) + "：" + abbreviate(firstNotBlank(d.auroraSummary, d.cognitiveSummary, d.eventSummary), 160))
                .toList();
    }

    private List<String> weeklyObservations(Long userId) {
        return weeklyReviewMapper.selectList(new QueryWrapper<WeeklyReview>()
                        .eq("user_id", userId)
                        .orderByDesc("week_start_date")
                        .last("LIMIT 2"))
                .stream()
                .map(w -> "周观察：" + safe(w.dominantTheme) + "；Aurora：" + abbreviate(w.auroraObservation, 160))
                .toList();
    }

    private List<String> relationSignals(Long userId) {
        return relationMentionMapper.selectList(new QueryWrapper<RelationMention>()
                        .eq("user_id", userId)
                        .orderByDesc("id")
                        .last("LIMIT 5"))
                .stream()
                .map(r -> safe(r.relationLabel) + "：" + abbreviate(firstNotBlank(r.triggerSummary, r.boundaryHint), 120))
                .toList();
    }

    private List<String> themeSignals(Long userId) {
        return memoryThemeMapper.selectList(new QueryWrapper<MemoryTheme>()
                        .eq("user_id", userId)
                        .eq("status", "ACTIVE")
                        .orderByDesc("average_gravity")
                        .last("LIMIT 5"))
                .stream()
                .map(t -> safe(t.themeName) + "：" + abbreviate(t.themeSummary, 120))
                .toList();
    }

    private String weatherLabel(Long userId, UserProfile profile) {
        if (Boolean.FALSE.equals(profile == null ? null : profile.weatherAwarenessEnabled)) {
            return "用户关闭了天气感知";
        }
        EmotionTrace trace = emotionTraceMapper.selectOne(new QueryWrapper<EmotionTrace>()
                .eq("user_id", userId)
                .orderByDesc("record_date")
                .orderByDesc("id")
                .last("LIMIT 1"));
        return trace == null ? "暂无情绪天气" : safe(trace.weatherType) + " / " + safe(trace.emotionName);
    }

    private String quietPolicy(UserProfile profile) {
        if (profile == null || blank(profile.quietHoursStart) || blank(profile.quietHoursEnd)) {
            return "无安静时段限制";
        }
        return inWindow(profile.quietHoursStart, profile.quietHoursEnd)
                ? "当前处于安静时段，不主动打扰，只回应用户主动输入"
                : "当前不在安静时段";
    }

    private String focusPolicy(UserProfile profile, String message) {
        if (profile == null || !Boolean.TRUE.equals(profile.focusModeEnabled)) {
            return "专注模式未开启";
        }
        boolean inFocus = focusWindowActive(profile.focusWindowsJson);
        if (!inFocus) return "专注模式开启，但当前不在专注时段";
        boolean taskRelated = containsAny(message, List.of("学习", "作业", "考试", "项目", "代码", "复习", "任务", "论文"));
        return taskRelated
                ? "当前在专注时段；用户聊的是任务相关内容，可以帮他拆解并回到行动"
                : "当前在专注时段；如用户只是闲聊，应像朋友一样温柔提醒回到专注";
    }

    private boolean focusWindowActive(String json) {
        if (blank(json)) return false;
        LocalTime now = LocalTime.now();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})").matcher(json);
        while (matcher.find()) {
            if (inWindow(matcher.group(1), matcher.group(2), now)) return true;
        }
        return false;
    }

    private boolean inWindow(String start, String end) {
        return inWindow(start, end, LocalTime.now());
    }

    private boolean inWindow(String start, String end, LocalTime now) {
        try {
            LocalTime s = LocalTime.parse(start);
            LocalTime e = LocalTime.parse(end);
            if (s.equals(e)) return false;
            if (s.isBefore(e)) return !now.isBefore(s) && now.isBefore(e);
            return !now.isBefore(s) || now.isBefore(e);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String profileSummary(UserProfile profile) {
        if (profile == null) return "默认用户画像：需要温柔、具体、不过度打扰。";
        return "Aurora名=" + safe(profile.auroraName)
                + "；风格=" + safe(profile.auroraTone)
                + "；反思深度=" + safe(profile.reflectionDepth)
                + "；社交可达=" + safe(profile.socialReachabilityStatus)
                + "；自述=" + abbreviate(profile.bio, 180);
    }

    private String inferEnvironment(String text) {
        if (containsAny(text, List.of("考试", "复习", "期末"))) return "备考/期末压力";
        if (containsAny(text, List.of("项目", "代码", "论文", "科研"))) return "项目推进期";
        if (containsAny(text, List.of("朋友", "关系", "恋爱", "同学"))) return "关系复盘期";
        return "日常自我照顾";
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

    private boolean containsAny(String text, List<String> words) {
        if (text == null) return false;
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private String deadlineText(TodoItem todo) {
        return todo.deadline == null ? "" : "，截止 " + todo.deadline.toLocalDate();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return "";
    }

    private String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
