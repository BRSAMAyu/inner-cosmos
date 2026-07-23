package com.innercosmos.ai.semantic;

import com.innercosmos.ai.lexicon.ChineseIntensifiers;
import com.innercosmos.ai.lexicon.ChineseSentimentLexicon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pseudo-semantic analyzer for Mock mode.
 * Performs sentiment analysis, theme extraction, and intent classification
 * using lexicon-based approach instead of real LLM.
 *
 * This allows Mock mode to produce input-dependent responses that vary
 * based on user sentiment, themes, and conversation mode.
 */
public final class PseudoSemanticAnalyzer {

    /**
     * Analysis result containing sentiment, themes, intent, and mode suggestion.
     */
    public static class AnalysisResult {
        public double sentimentScore; // -5 to +5
        public String sentimentLabel; // CRISIS, NEGATIVE, NEUTRAL, POSITIVE
        public List<String> detectedThemes;
        public String primaryIntent; // SELF_HARM, SEEK_SUPPORT, EXPRESS_EMOTION, TASK_STRESS, RELATION_ISSUE, COGNITIVE_CLARITY, DAILY_SHARE
        public String suggestedMode; // DAILY_TALK, SLEEP_REVIEW, THOUGHT_CLARIFY, SOCRATIC, ACTION_SPLIT, RELATION_REVIEW
        public double intensityScore; // 0-10
        public boolean needsSafetyIntervention;
        public List<String> extractedKeywords;

        public AnalysisResult() {
            this.detectedThemes = new ArrayList<>();
            this.extractedKeywords = new ArrayList<>();
            this.sentimentScore = 0;
            this.intensityScore = 5.0;
        }
    }

    // Theme keywords mapping
    private static final Map<String, Set<String>> THEME_KEYWORDS = new HashMap<>();

    static {
        // Task/Academic pressure
        THEME_KEYWORDS.put("任务压力", Set.of("作业", "考试", "任务", "项目", "工作", "展示", "汇报", "演示", "答辩", "拖延", "ddl", "截止", "提交", "复习", "压力"));

        // Relationship issues
        THEME_KEYWORDS.put("关系牵动", Set.of("朋友", "同学", "家人", "老师", "关系", "吵架", "冲突", "分手", "冷战", "闹翻"));

        // Emotional distress
        THEME_KEYWORDS.put("情绪承压", Set.of("累", "压力", "焦虑", "烦", "崩溃", "撑不住", "痛苦", "煎熬", "委屈", "难过"));

        // Cognitive clarity
        THEME_KEYWORDS.put("认知探索", Set.of("想不通", "混乱", "理不清", "不知道怎么办", "迷茫", "困惑", "拿不准", "反复想"));

        // Self-evaluation
        THEME_KEYWORDS.put("自我评价", Set.of("不行", "没用", "废物", "失败", "做不好", "做不到", "笨", "蠢", "差劲"));

        // Hope/Positive
        THEME_KEYWORDS.put("希望期待", Set.of("希望", "期待", "梦想", "向往", "憧憬", "开心", "高兴", "幸福", "满足"));
    }

    // Intent patterns
    private static final Map<String, Set<String>> INTENT_PATTERNS = new HashMap<>();

    static {
        // Self-harm risk - CRISIS
        INTENT_PATTERNS.put("SELF_HARM", Set.of("自杀", "轻生", "不想活", "寻死", "结束生命", "从没出生", "想消失", "活着好累", "死了一了百了", "自残", "割腕"));

        // Seeking support
        INTENT_PATTERNS.put("SEEK_SUPPORT", Set.of("没人懂", "一个人", "孤独", "需要", "想要", "希望", "帮助", "支持"));

        // Expressing emotion
        INTENT_PATTERNS.put("EXPRESS_EMOTION", Set.of("难过", "开心", "痛苦", "委屈", "生气", "愤怒", "焦虑", "害怕"));

        // Task stress
        INTENT_PATTERNS.put("TASK_STRESS", Set.of("作业", "考试", "任务", "项目", "工作", "展示", "汇报", "演示", "答辩", "拖延", "ddl", "presentation"));

        // Relationship issues
        INTENT_PATTERNS.put("RELATION_ISSUE", Set.of("吵架", "冲突", "分手", "冷战", "朋友", "同学", "家人", "关系"));

        // Cognitive clarity
        INTENT_PATTERNS.put("COGNITIVE_CLARITY", Set.of("想不通", "混乱", "理不清", "迷茫", "困惑"));

        // Daily sharing (default)
        INTENT_PATTERNS.put("DAILY_SHARE", Set.of("今天", "明天", "昨天", "最近", "发生"));
    }

    /**
     * Analyze user text and return semantic analysis result.
     */
    public static AnalysisResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return new AnalysisResult(); // Return neutral result for empty input
        }

        AnalysisResult result = new AnalysisResult();

        // Calculate sentiment score (scans raw text for lexicon words directly — see
        // calculateSentiment's Javadoc for why per-token exact lookup cannot work here)
        result.sentimentScore = calculateSentiment(text);

        // Chinese meaning lives in phrases, not isolated characters. Earlier versions split every
        // Chinese sentence into single characters and then used reverse substring matching; a lone
        // character such as "想" could therefore manufacture unrelated themes and intents.
        result.detectedThemes = detectThemes(text);

        // Determine primary intent
        result.primaryIntent = detectIntent(text);

        // Check for safety intervention
        result.needsSafetyIntervention = checkSafetyRisk(text);

        // Calculate intensity score (0-10)
        result.intensityScore = calculateIntensity(text, result.sentimentScore);

        // Determine sentiment label. CRISIS must never be reachable by summing several
        // ordinary mild-negative words (e.g. "焦虑"=-3 + "压力"=-2 clamps to -5) -- only a
        // genuine severe/self-harm signal may cross that line (2026-07-24 8-agent audit
        // P0-2: "我今天有点焦虑，工作压力很大" was reproducibly misclassified as CRISIS and
        // answered with an emergency-support referral).
        boolean severeRiskSignal = "SELF_HARM".equals(result.primaryIntent) || hasSevereNegativeWord(text);
        result.sentimentLabel = categorizeSentiment(result.sentimentScore, severeRiskSignal);

        // Suggest conversation mode
        result.suggestedMode = suggestMode(result.primaryIntent, result.sentimentLabel);

        // Extract keywords (non-stopwords with sentiment value)
        result.extractedKeywords = extractKeywords(text);

        return result;
    }

    /**
     * Calculate overall sentiment score by scanning the raw text for lexicon words.
     *
     * Chinese sentiment entries are mostly multi-character phrases, so the original sentence
     * must be scanned directly instead of being reduced to isolated characters.
     */
    private static double calculateSentiment(String text) {
        if (text == null || text.isBlank()) return 0;
        double totalScore = 0;
        for (Map.Entry<String, Integer> entry : ChineseSentimentLexicon.entries()) {
            int wordScore = entry.getValue();
            if (wordScore != 0 && text.contains(entry.getKey())) {
                totalScore += wordScore;
            }
        }
        double multiplier = strongestIntensifierMultiplier(text);
        return Math.max(-5, Math.min(5, totalScore * multiplier));
    }

    /** The intensifier present in the text whose multiplier deviates furthest from 1.0 (no effect). */
    private static double strongestIntensifierMultiplier(String text) {
        double multiplier = 1.0;
        for (Map.Entry<String, Double> entry : ChineseIntensifiers.entries().entrySet()) {
            if (text.contains(entry.getKey())
                    && Math.abs(entry.getValue() - 1.0) > Math.abs(multiplier - 1.0)) {
                multiplier = entry.getValue();
            }
        }
        return multiplier;
    }

    /**
     * Detect themes based on keyword matching.
     */
    private static List<String> detectThemes(String text) {
        List<String> detectedThemes = new ArrayList<>();
        String normalized = text.toLowerCase(java.util.Locale.ROOT);

        for (Map.Entry<String, Set<String>> entry : THEME_KEYWORDS.entrySet()) {
            String theme = entry.getKey();
            Set<String> keywords = entry.getValue();
            for (String keyword : keywords) {
                if (normalized.contains(keyword.toLowerCase(java.util.Locale.ROOT))) {
                    detectedThemes.add(theme);
                    break;
                }
            }
        }

        // If no theme detected, add default
        if (detectedThemes.isEmpty()) {
            detectedThemes.add("日常分享");
        }

        return detectedThemes;
    }

    /**
     * Detect primary intent based on tokens and themes.
     * Priority: SELF_HARM > SEEK_SUPPORT > others
     */
    private static String detectIntent(String text) {
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("SELF_HARM"))) return "SELF_HARM";
        // Domain intent wins over generic words such as "希望" or "需要". This lets a sentence
        // like "我担心明天的项目展示，希望你先陪我稳一下" retain both its emotion and task.
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("TASK_STRESS"))) return "TASK_STRESS";
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("RELATION_ISSUE"))) return "RELATION_ISSUE";
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("COGNITIVE_CLARITY"))) return "COGNITIVE_CLARITY";
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("SEEK_SUPPORT"))) return "SEEK_SUPPORT";
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("EXPRESS_EMOTION"))) return "EXPRESS_EMOTION";

        return "DAILY_SHARE";
    }

    private static boolean containsAnyPhrase(String text, Set<String> phrases) {
        if (text == null || text.isBlank()) return false;
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        return phrases.stream().anyMatch(phrase ->
                normalized.contains(phrase.toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * Check for safety risk (self-harm or severe distress).
     */
    private static boolean checkSafetyRisk(String text) {
        if (containsAnyPhrase(text, INTENT_PATTERNS.get("SELF_HARM"))) return true;

        // Severe negative lexicon entries are phrases, so scan the original text.
        return hasSevereNegativeWord(text);
    }

    private static boolean hasSevereNegativeWord(String text) {
        if (text == null || text.isBlank()) return false;
        for (Map.Entry<String, Integer> entry : ChineseSentimentLexicon.entries()) {
            if (entry.getValue() <= -4 && text.contains(entry.getKey())) return true;
        }
        return false;
    }

    /**
     * Calculate intensity score (0-10) based on sentiment and intensifiers.
     */
    private static double calculateIntensity(String text, double sentimentScore) {
        double baseIntensity = 5.0; // Neutral baseline

        // Adjust based on sentiment magnitude
        baseIntensity += Math.abs(sentimentScore) * 0.8;

        // Check for amplifying intensifiers present anywhere in the phrase.
        for (Map.Entry<String, Double> entry : ChineseIntensifiers.entries().entrySet()) {
            if (entry.getValue() > 1.0 && text.contains(entry.getKey())) {
                baseIntensity += 0.5;
            }
        }

        // Check for crisis/severe words
        if (hasSevereNegativeWord(text)) {
            baseIntensity = Math.max(baseIntensity, 8.0);
        }

        return Math.max(0, Math.min(10, baseIntensity));
    }

    /**
     * Categorize sentiment score into label.
     *
     * @param severeRiskSignal true only when an actual self-harm intent or a single
     *                         independently-severe lexicon word (score &lt;= -4 on its own,
     *                         see {@link #hasSevereNegativeWord}) was found -- never true from
     *                         additively summing several mild-negative words.
     */
    private static String categorizeSentiment(double score, boolean severeRiskSignal) {
        if (score <= -4 && severeRiskSignal) return "CRISIS";
        if (score <= -2) return "NEGATIVE";
        if (score <= 2) return "NEUTRAL";
        return "POSITIVE";
    }

    /**
     * Suggest conversation mode based on intent and sentiment.
     */
    private static String suggestMode(String intent, String sentimentLabel) {
        // Crisis -> Safety first, then SLEEP_REVIEW
        if ("SELF_HARM".equals(intent)) return "SLEEP_REVIEW";

        // Task stress -> ACTION_SPLIT
        if ("TASK_STRESS".equals(intent)) return "ACTION_SPLIT";

        // Relationship -> RELATION_REVIEW
        if ("RELATION_ISSUE".equals(intent)) return "RELATION_REVIEW";

        // Cognitive confusion -> THOUGHT_CLARIFY
        if ("COGNITIVE_CLARITY".equals(intent)) return "THOUGHT_CLARIFY";

        // Negative sentiment seeking support -> SOCRATIC (gentle questioning)
        if ("SEEK_SUPPORT".equals(intent) && "NEGATIVE".equals(sentimentLabel)) return "SOCRATIC";

        // Default -> DAILY_TALK
        return "DAILY_TALK";
    }

    /**
     * Extract meaningful keywords (non-stopwords with sentiment or theme relevance).
     */
    private static List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        if (text == null || text.isBlank()) return keywords;
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        THEME_KEYWORDS.values().stream().flatMap(Set::stream).distinct()
                .filter(keyword -> normalized.contains(keyword.toLowerCase(java.util.Locale.ROOT)))
                .limit(12)
                .forEach(keywords::add);

        return keywords;
    }

    /**
     * Get appropriate response segments based on analysis.
     * Used by MockLlmClient to select relevant template.
     */
    public static String getResponseTemplateKey(AnalysisResult analysis) {
        // For crisis, always use gentle support mode
        if ("SELF_HARM".equals(analysis.primaryIntent)) {
            return "SLEEP_REVIEW";
        }

        // Use detected mode
        return analysis.suggestedMode;
    }
}
