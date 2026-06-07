package com.innercosmos.ai.context;

import java.util.ArrayList;
import java.util.List;

public class AgentContext {
    public Long userId;
    public String profileSummary;
    public String timeLabel;
    public String weatherLabel;
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
}
