package com.innercosmos.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.ArrayList;
import java.util.List;

public final class StructuredAiResults {
    private StructuredAiResults() {
    }

    public static class AuroraResult {
        public List<String> segments = new ArrayList<>();
        public Integer speakCount;
        public String continueReason;
        public String detectedTheme;
        public String nextQuestion;
        public String smallStep;
        public String featureSuggestion;
        public String featureTarget;
        public Boolean memoryReferenced;
        public List<Long> referencedMemoryIds = new ArrayList<>();
        public List<String> riskFlags = new ArrayList<>();
    }

    /** User-safe plan contract: decisions and constraints, never hidden chain-of-thought. */
    public static class AuroraPlanResult {
        /** Versioned, user-safe deliberation contract. This is a decision summary, never CoT. */
        public String contractVersion = "aurora.deliberation.v2";
        public TopicState topicState = new TopicState();
        public UserState userState = new UserState();
        public AuroraState auroraState = new AuroraState();
        public MemoryDecision memoryDecision = new MemoryDecision();
        public ResponsePlan responsePlan = new ResponsePlan();
        public InterruptionPlan interruptionPlan = new InterruptionPlan();
        public SafetyContract safetyContract = new SafetyContract();

        /*
         * Compatibility fields retained while stored plans and older providers migrate to v2.
         * AuroraDualKernelRuntime normalizes both representations into the typed contract.
         */
        public String userIntent;
        public String emotionalNeed;
        public String relationshipMove;
        public List<String> responseConstraints = new ArrayList<>();
        public List<String> bubblePurposes = new ArrayList<>();
        public List<Long> relevantMemoryIds = new ArrayList<>();
        public String uncertainty;
        public Boolean needsCritic;
        /** True only when the deep kernel found a genuinely new unsaid relational layer. */
        public Boolean innerVoiceWorthy;
        /** A bounded seed for the later side-channel composer; never exposed directly. */
        public String innerVoiceSeed;
    }

    public static class TopicState {
        public String activeTopicId;
        public List<String> anchors = new ArrayList<>();
        public List<String> unresolvedThreads = new ArrayList<>();
        public String competingAnchor;
        public Double confidence;
    }

    public static class UserState {
        public String intent;
        public String emotionalNeed;
        public String desiredDepth;
        public Boolean correctionDetected;
    }

    public static class AuroraState {
        public String currentIntention;
        /** AGREE / DISAGREE / NUANCE / CHALLENGE_GENTLY / DECLINE_CERTAINTY / ACKNOWLEDGE_ONLY. */
        public String stanceMode;
        public String stanceReason;
        public List<String> selfAnchorRefs = new ArrayList<>();
        public List<String> evidenceRefs = new ArrayList<>();
        public String uncertainty;
        public String changeMindIf;
        public String userAutonomyBoundary;
    }

    public static class MemoryDecision {
        public List<Long> admittedIds = new ArrayList<>();
        public List<Long> rejectedTopCandidates = new ArrayList<>();
        public List<String> rejectionReasons = new ArrayList<>();
    }

    public static class ResponsePlan {
        public List<String> bubblePurposes = new ArrayList<>();
        public Integer maxBubbles;
        public Boolean askQuestion;
        public String stopCondition;
    }

    public static class InterruptionPlan {
        public Long priorPlanRevision;
        public String deliveredSummary;
        public List<String> cancelledPurposes = new ArrayList<>();
        /** CONTINUE / PARK / SWITCH / MERGE. */
        public String continuityDecision;
        public List<String> changedFacts = new ArrayList<>();
        public List<String> mustNotRepeat = new ArrayList<>();
        public Long planRevision;
    }

    public static class SafetyContract {
        public Boolean responseAllowed;
        public Boolean gentleCheckIn;
        public Boolean resourceOffer;
        public String blockingReason;
    }

    public static class AuroraCriticResult {
        public Boolean pass;
        public List<String> issues = new ArrayList<>();
        public AuroraResult repaired;
    }

    /** Fast, non-thinking expression-core output shown while the deep dual kernel is still working. */
    public static class AuroraForegroundResult {
        public String text;
    }

    /**
     * Aurora's "inner voice" (心声): a genuine, first-person interior line -- distinct from the
     * visible spoken {@code segments} -- that may be surfaced (as text + synthesized audio) at
     * most once per turn. Never a restatement/paraphrase/summary of the spoken reply; see
     * {@code InnerVoiceComposer}.
     */
    public static class InnerVoiceResult {
        public String innerVoiceText;
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

    /**
     * A deliberately closed vocabulary for turning an owner's free-form sandbox correction into
     * auditable calibration data. Callers must still allow-list every value before persistence:
     * model output is data, never authority.
     */
    public static class CapsuleCalibrationResult {
        public List<String> toneCodes = new ArrayList<>();
        public List<String> avoidBehaviorCodes = new ArrayList<>();
        public List<String> boundaryCodes = new ArrayList<>();
        public String responseLengthCode;
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
        /** IC-EMO-001: optional emotion spectrum so settlement LLM can emit it directly. */
        public List<SpectrumEntry> spectrum = new ArrayList<>();
    }

    /** IC-EMO-001: a single {emotion, ratio} pair in an emotion spectrum. */
    public static class SpectrumEntry {
        public String emotion;
        public double ratio;
    }

    public static class Event {
        // DeepSeek's MEMORY_SETTLEMENT emits "title" for the event title; accept both.
        @JsonAlias("title")
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
