package com.innercosmos.ai.context;

import java.util.ArrayList;
import java.util.List;

public class AgentContext {
    public Long userId;
    public String profileSummary;
    public String timeLabel;
    public String weatherLabel;
    /**
     * IC-EMO-002: the richer "此刻情绪" perception — primary emotion + 0..10 intensity
     * + a brief top-2/3 spectrum (e.g. "平静（平静 60% · 期待 30%）"), read from the
     * latest enriched EmotionTrace. Falls back to "暂无此刻情绪" with no trace and to
     * "用户关闭了情绪感知" when the weather/emotion opt-out is set. Distinct from
     * {@link #weatherLabel}, which stays the compact weather/emotion pair.
     */
    public String momentEmotionLabel;
    public String environmentLabel;
    public String quietPolicy;
    public String focusPolicy;
    public Boolean memoryRecallAllowed;
    public Boolean multiMessageAllowed;
    public Integer proactiveSensitivity;
    /** City-level location resolved from lat/lon via {@link com.innercosmos.ai.perception.GeocodingService}. */
    public String cityLabel;
    /** Real-world weather snapshot from {@link com.innercosmos.ai.perception.WeatherContextService} (lat/lon based). */
    public String realWeatherLabel;
    /** True if the inferred sleep window is active (23:00–07:00). */
    public Boolean sleepInferred;
    /** Nearest open todo title, if any. */
    public String nearestTodo;
    public List<String> recentMessages = new ArrayList<>();
    public List<String> longTermMemories = new ArrayList<>();
    public List<String> activeTodos = new ArrayList<>();
    public List<String> completedTodoLessons = new ArrayList<>();
    public List<String> dailyObservations = new ArrayList<>();
    public List<String> weeklyObservations = new ArrayList<>();
    public List<String> relationSignals = new ArrayList<>();
    public List<String> themeSignals = new ArrayList<>();
    public List<Long> evidenceMemoryIds = new ArrayList<>();
    /** 3-model block: Aurora identity + Relationship state + User portrait */
    public String threeModelBlock = "";
    // Aurora subjectivity — Constitution + Continuity Anchors
    /** 【Aurora 存在宪法】... (from AuroraConstitutionService) */
    public String constitutionBlock;
    /** 【Aurora 身份锚点】... (from AuroraSelfContinuityService) */
    public String continuityAnchors;
}
