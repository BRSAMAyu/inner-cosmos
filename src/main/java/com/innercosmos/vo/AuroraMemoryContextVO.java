package com.innercosmos.vo;

import java.util.ArrayList;
import java.util.List;

public class AuroraMemoryContextVO {
    public Long userId;
    public Long sessionId;
    public String memoryPolicy;
    public String sessionSummaryAnchor;
    public String lastDialogSummary;
    public List<String> shortTermMessages = new ArrayList<>();
    public List<String> longTermMemoryNotes = new ArrayList<>();
    public List<Long> referencedMemoryIds = new ArrayList<>();
    public List<String> activeThemeNotes = new ArrayList<>();
    public String emotionWeather;
    public String continuityHypothesis;
    public List<String> proactiveSuggestions = new ArrayList<>();
    public Boolean memoryRecallAllowed = true;
}
