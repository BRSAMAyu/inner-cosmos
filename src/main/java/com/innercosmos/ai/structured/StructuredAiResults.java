package com.innercosmos.ai.structured;

import java.util.ArrayList;
import java.util.List;

public final class StructuredAiResults {
    private StructuredAiResults() {
    }

    public static class AuroraResult {
        public List<String> segments = new ArrayList<>();
        public String detectedTheme;
        public String nextQuestion;
        public String smallStep;
        public Boolean memoryReferenced;
        public List<Long> referencedMemoryIds = new ArrayList<>();
    }

    public static class ShredderResult {
        public String coreFeeling;
        public String hiddenNeed;
        public List<String> noiseToDrop = new ArrayList<>();
        public String sentenceToKeep;
        public List<Fragment> fragments = new ArrayList<>();
        public TodoSuggestion suggestedTodo;
        public Double intensityScore;
        public String memoryType;
    }

    public static class SettlementResult {
        public Memory memoryCard = new Memory();
        public Emotion emotionTrace = new Emotion();
        public List<Fragment> fragments = new ArrayList<>();
        public List<Event> eventCards = new ArrayList<>();
        public List<Relation> relationMentions = new ArrayList<>();
        public List<TodoSuggestion> todos = new ArrayList<>();
        public String dailyTheme;
        public String dailyObservation;
    }

    public static class WeeklyResult {
        public String dominantTheme;
        public String themeSummary;
        public String emotionTrend;
        public String gravityChangeSummary;
        public String weeklyObservation;
    }

    public static class PersonaResult {
        public String reply;
        public String boundaryNotice;
        public Boolean letterSuggested;
        public List<String> riskFlags = new ArrayList<>();
    }

    public static class LetterGuardResult {
        public Boolean allow;
        public String reason;
        public List<String> riskFlags = new ArrayList<>();
    }

    public static class Memory {
        public String title;
        public String summary;
        public String memoryType;
        public List<String> emotionTags = new ArrayList<>();
        public List<String> keywordTags = new ArrayList<>();
        public List<String> peopleTags = new ArrayList<>();
        public Double intensityScore;
        public Double userImportance;
    }

    public static class Fragment {
        public String type;
        public String rawExcerpt;
        public String analysis;
        public String reframe;
    }

    public static class TodoSuggestion {
        public String taskName;
        public String description;
        public String priority;
    }

    public static class Emotion {
        public String emotionName;
        public Double emotionScore;
        public String weatherType;
        public String triggerScene;
    }

    public static class Event {
        public String eventTitle;
        public String eventSummary;
        public String eventTimeLabel;
        public String scene;
        public List<String> peopleTags = new ArrayList<>();
        public List<String> emotionTags = new ArrayList<>();
    }

    public static class Relation {
        public String relationLabel;
        public String relationType;
        public List<String> emotionTags = new ArrayList<>();
        public String triggerSummary;
        public String boundaryHint;
    }
}
