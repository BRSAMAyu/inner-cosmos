package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.DialogSummary;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.DialogSummaryMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.AuroraMemoryContextService;
import com.innercosmos.vo.AuroraMemoryContextVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AuroraMemoryContextServiceImpl implements AuroraMemoryContextService {
    private final DialogMessageMapper dialogMessageMapper;
    private final DialogSessionMapper dialogSessionMapper;
    private final DialogSummaryMapper dialogSummaryMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final MemoryThemeMapper memoryThemeMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final UserProfileMapper userProfileMapper;

    public AuroraMemoryContextServiceImpl(DialogMessageMapper dialogMessageMapper,
                                          DialogSessionMapper dialogSessionMapper,
                                          DialogSummaryMapper dialogSummaryMapper,
                                          MemoryCardMapper memoryCardMapper,
                                          MemoryThemeMapper memoryThemeMapper,
                                          EmotionTraceMapper emotionTraceMapper,
                                          UserProfileMapper userProfileMapper) {
        this.dialogMessageMapper = dialogMessageMapper;
        this.dialogSessionMapper = dialogSessionMapper;
        this.dialogSummaryMapper = dialogSummaryMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.memoryThemeMapper = memoryThemeMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public AuroraMemoryContextVO buildContext(Long userId, Long sessionId, String userInput, int shortTermLimit, int longTermLimit) {
        AuroraMemoryContextVO context = new AuroraMemoryContextVO();
        context.userId = userId;
        context.sessionId = sessionId;
        if (userId == null) {
            context.memoryPolicy = "anonymous_session_only";
            context.memoryRecallAllowed = false;
            return context;
        }

        UserProfile profile = loadProfile(userId);
        context.memoryRecallAllowed = profile == null || profile.allowMemoryRecall == null || Boolean.TRUE.equals(profile.allowMemoryRecall);
        context.memoryPolicy = Boolean.TRUE.equals(context.memoryRecallAllowed)
                ? "short_window_plus_salient_long_term_memory"
                : "short_window_only_user_disabled_recall";

        DialogSession session = sessionId == null ? null : dialogSessionMapper.selectById(sessionId);
        if (session != null && userId.equals(session.userId)) {
            context.sessionSummaryAnchor = session.summaryAnchor;
        }
        context.shortTermMessages = shortTermMessages(userId, sessionId, Math.max(2, shortTermLimit));
        context.lastDialogSummary = lastDialogSummary(userId, sessionId);
        if (Boolean.TRUE.equals(context.memoryRecallAllowed)) {
            loadLongTermMemory(context, userId, userInput, Math.max(1, longTermLimit));
            context.activeThemeNotes = activeThemes(userId, userInput, 4);
        }
        context.emotionWeather = latestEmotionWeather(userId);
        context.continuityHypothesis = buildContinuityHypothesis(context);
        context.proactiveSuggestions = proactiveSuggestions(context, userInput);
        return context;
    }

    private UserProfile loadProfile(Long userId) {
        QueryWrapper<UserProfile> query = new QueryWrapper<>();
        query.eq("user_id", userId).last("LIMIT 1");
        return userProfileMapper.selectOne(query);
    }

    private List<String> shortTermMessages(Long userId, Long sessionId, int limit) {
        if (sessionId == null) return List.of();
        QueryWrapper<DialogMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByDesc("id").last("LIMIT " + limit);
        List<DialogMessage> rows = new ArrayList<>(dialogMessageMapper.selectList(query));
        rows.sort(Comparator.comparing(m -> m.id == null ? 0L : m.id));
        List<String> result = new ArrayList<>();
        for (DialogMessage row : rows) {
            if (row.userId != null && !row.userId.equals(userId)) continue;
            String speaker = "USER".equalsIgnoreCase(row.speaker) ? "User" : "Aurora";
            result.add(speaker + ": " + abbreviate(row.textContent, 180));
        }
        return result;
    }

    private String lastDialogSummary(Long userId, Long sessionId) {
        QueryWrapper<DialogSummary> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        if (sessionId != null) {
            query.eq("session_id", sessionId);
        }
        query.orderByDesc("id").last("LIMIT 1");
        DialogSummary summary = dialogSummaryMapper.selectOne(query);
        return summary == null ? null : summary.summaryText;
    }

    private void loadLongTermMemory(AuroraMemoryContextVO context, Long userId, String userInput, int limit) {
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("emotional_gravity")
                .last("LIMIT " + Math.max(limit * 3, limit));
        List<MemoryCard> cards = memoryCardMapper.selectList(query);
        List<MemoryCard> ranked = new ArrayList<>(cards);
        ranked.sort((a, b) -> Double.compare(score(b, userInput), score(a, userInput)));
        for (MemoryCard card : ranked.stream().limit(limit).toList()) {
            context.referencedMemoryIds.add(card.id);
            context.longTermMemoryNotes.add(formatMemory(card));
        }
    }

    private List<String> activeThemes(Long userId, String userInput, int limit) {
        QueryWrapper<MemoryTheme> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("average_gravity")
                .last("LIMIT " + Math.max(limit * 2, limit));
        List<MemoryTheme> themes = new ArrayList<>(memoryThemeMapper.selectList(query));
        themes.sort((a, b) -> Double.compare(themeScore(b, userInput), themeScore(a, userInput)));
        List<String> notes = new ArrayList<>();
        for (MemoryTheme theme : themes.stream().limit(limit).toList()) {
            notes.add(theme.themeName + " | count=" + safeInt(theme.memoryCount) + " | gravity="
                    + formatDouble(theme.averageGravity) + " | " + abbreviate(theme.themeSummary, 120));
        }
        return notes;
    }

    private String latestEmotionWeather(Long userId) {
        QueryWrapper<EmotionTrace> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("record_date").orderByDesc("id").last("LIMIT 1");
        EmotionTrace trace = emotionTraceMapper.selectOne(query);
        if (trace == null) return "unknown";
        return trace.weatherType + " / " + trace.emotionName + " / score=" + formatDouble(trace.emotionScore);
    }

    private String buildContinuityHypothesis(AuroraMemoryContextVO context) {
        if (context.longTermMemoryNotes == null || context.longTermMemoryNotes.isEmpty()) {
            return "No strong long-term link found yet; stay with the current expression.";
        }
        if (context.activeThemeNotes != null && !context.activeThemeNotes.isEmpty()) {
            return "The current input may connect with an active repeating theme; reference it gently and transparently.";
        }
        return "A prior high-gravity memory may be relevant; use it only if it helps the user feel seen.";
    }

    private List<String> proactiveSuggestions(AuroraMemoryContextVO context, String userInput) {
        List<String> suggestions = new ArrayList<>();
        String text = normalize(userInput);
        if (containsAny(text, "deadline", "todo", "task", "exam", "homework", "project", "明天", "作业", "任务", "考试")) {
            suggestions.add("Offer one ten-minute next action and avoid overwhelming planning.");
        }
        if (context.emotionWeather != null && (context.emotionWeather.contains("STORM") || context.emotionWeather.contains("RAINY"))) {
            suggestions.add("Slow down, validate pressure, and suggest grounding before problem solving.");
        }
        if (context.longTermMemoryNotes != null && !context.longTermMemoryNotes.isEmpty()) {
            suggestions.add("If referencing memory, use transparent wording and avoid sounding like surveillance.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Ask one concrete question that helps the user name what matters most right now.");
        }
        return suggestions;
    }

    private double score(MemoryCard card, String userInput) {
        double gravity = card.emotionalGravity == null ? 0.0 : card.emotionalGravity;
        double importance = card.userImportance == null ? 0.0 : card.userImportance;
        double freshness = card.lastTouchedAt == null ? 0.0 : freshnessBonus(card.lastTouchedAt);
        double lexical = lexicalOverlap(userInput, card.title + " " + card.summary + " " + card.keywordTags + " " + card.emotionTags);
        return gravity * 3.0 + importance * 0.4 + freshness + lexical * 2.0;
    }

    private double themeScore(MemoryTheme theme, String userInput) {
        double gravity = theme.averageGravity == null ? 0.0 : theme.averageGravity;
        double count = theme.memoryCount == null ? 0.0 : Math.min(6, theme.memoryCount) * 0.15;
        double lexical = lexicalOverlap(userInput, theme.themeName + " " + theme.themeSummary + " " + theme.keywords);
        return gravity * 2.0 + count + lexical * 2.0;
    }

    private double lexicalOverlap(String a, String b) {
        Set<String> left = tokens(a);
        Set<String> right = tokens(b);
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        int hit = 0;
        for (String token : left) {
            if (right.contains(token)) hit++;
        }
        return hit / (double) Math.max(1, left.size());
    }

    private Set<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = normalize(text).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", " ");
        for (String part : normalized.split("\\s+")) {
            if (part.length() >= 2) tokens.add(part);
        }
        return tokens;
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (text.contains(normalize(needle))) return true;
        }
        return false;
    }

    private double freshnessBonus(LocalDateTime touchedAt) {
        long days = java.time.Duration.between(touchedAt, LocalDateTime.now()).toDays();
        if (days <= 1) return 0.8;
        if (days <= 7) return 0.45;
        if (days <= 30) return 0.2;
        return 0.0;
    }

    private String formatMemory(MemoryCard card) {
        return "#" + card.id + " " + nullToBlank(card.title)
                + " | type=" + nullToBlank(card.memoryType)
                + " | gravity=" + formatDouble(card.emotionalGravity)
                + " | " + abbreviate(card.summary, 180);
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String abbreviate(String text, int max) {
        if (text == null) return "";
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > max ? compact.substring(0, max) + "..." : compact;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDouble(Double value) {
        return String.format(Locale.ROOT, "%.2f", value == null ? 0.0 : value);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
