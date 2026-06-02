package com.innercosmos.ai.semantic;

import com.innercosmos.ai.lexicon.ChineseIntensifiers;
import com.innercosmos.ai.lexicon.ChineseSentimentLexicon;
import com.innercosmos.ai.lexicon.ChineseStopwords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        THEME_KEYWORDS.put("任务压力", Set.of("作业", "考试", "任务", "项目", "拖延", "ddl", "截止", "交", "提交", "复习", "压力", "累"));

        // Relationship issues
        THEME_KEYWORDS.put("关系牵动", Set.of("朋友", "同学", "家人", "老师", "关系", "吵架", "冲突", "分手", "冷战", "闹翻"));

        // Emotional distress
        THEME_KEYWORDS.put("情绪承压", Set.of("累", "压力", "焦虑", "烦", "崩溃", "撑不住", "痛苦", "煎熬", "委屈", "难过"));

        // Cognitive clarity
        THEME_KEYWORDS.put("认知探索", Set.of("想不通", "混乱", "理不清", "不知道", "迷茫", "困惑", "想", "觉得", "认为"));

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
        INTENT_PATTERNS.put("TASK_STRESS", Set.of("作业", "考试", "任务", "项目", "拖延", "ddl"));

        // Relationship issues
        INTENT_PATTERNS.put("RELATION_ISSUE", Set.of("吵架", "冲突", "分手", "冷战", "朋友", "同学", "家人", "关系"));

        // Cognitive clarity
        INTENT_PATTERNS.put("COGNITIVE_CLARITY", Set.of("想不通", "混乱", "理不清", "迷茫", "困惑"));

        // Daily sharing (default)
        INTENT_PATTERNS.put("DAILY_SHARE", Set.of("今天", "明天", "昨天", "最近", "发生"));
    }

    // Simple tokenizer (splits by common delimiters)
    private static final Pattern TOKENIZER = Pattern.compile("[\\s,.,!?!?;;、]+");

    /**
     * Analyze user text and return semantic analysis result.
     */
    public static AnalysisResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return new AnalysisResult(); // Return neutral result for empty input
        }

        AnalysisResult result = new AnalysisResult();

        // Tokenize
        List<String> tokens = tokenize(text);

        // Calculate sentiment score
        result.sentimentScore = calculateSentiment(tokens);

        // Detect themes
        result.detectedThemes = detectThemes(tokens);

        // Determine primary intent
        result.primaryIntent = detectIntent(tokens, result.detectedThemes);

        // Check for safety intervention
        result.needsSafetyIntervention = checkSafetyRisk(tokens);

        // Calculate intensity score (0-10)
        result.intensityScore = calculateIntensity(tokens, result.sentimentScore);

        // Determine sentiment label
        result.sentimentLabel = categorizeSentiment(result.sentimentScore);

        // Suggest conversation mode
        result.suggestedMode = suggestMode(result.primaryIntent, result.sentimentLabel);

        // Extract keywords (non-stopwords with sentiment value)
        result.extractedKeywords = extractKeywords(tokens);

        return result;
    }

    /**
     * Tokenize Chinese text into individual words/characters.
     */
    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String[] parts = TOKENIZER.split(text.trim());
        for (String part : parts) {
            if (part.isBlank()) continue;
            // For Chinese, also split by individual characters for granular matching
            if (containsChinese(part)) {
                for (char c : part.toCharArray()) {
                    if (!isWhitespace(c)) {
                        tokens.add(String.valueOf(c));
                    }
                }
            } else {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static boolean containsChinese(String text) {
        return text.matches(".*[\\u4e00-\\u9fa5]+.*");
    }

    private static boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }

    /**
     * Calculate overall sentiment score from tokens.
     * Uses lexicon lookup and intensifier adjustment.
     */
    private static double calculateSentiment(List<String> tokens) {
        double totalScore = 0;
        double currentMultiplier = 1.0;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            // Skip stopwords
            if (ChineseStopwords.isStopword(token)) continue;

            // Check for intensifier
            if (ChineseIntensifiers.isIntensifier(token)) {
                currentMultiplier = ChineseIntensifiers.getMultiplier(token);
                continue;
            }

            // Check for sentiment word
            int wordScore = ChineseSentimentLexicon.getScore(token);
            if (wordScore != 0) {
                totalScore += wordScore * currentMultiplier;
                // Reset multiplier after applying
                currentMultiplier = 1.0;
            }
        }

        // Normalize to -5 to +5 range
        return Math.max(-5, Math.min(5, totalScore));
    }

    /**
     * Detect themes based on keyword matching.
     */
    private static List<String> detectThemes(List<String> tokens) {
        Set<String> tokensSet = new HashSet<>(tokens);
        List<String> detectedThemes = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : THEME_KEYWORDS.entrySet()) {
            String theme = entry.getKey();
            Set<String> keywords = entry.getValue();

            // Check if any theme keyword appears in tokens
            for (String keyword : keywords) {
                boolean found = false;
                for (String token : tokens) {
                    if (token.contains(keyword) || keyword.contains(token)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
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
    private static String detectIntent(List<String> tokens, List<String> themes) {
        // Check self-harm first (highest priority)
        for (String token : tokens) {
            for (String crisisKeyword : INTENT_PATTERNS.get("SELF_HARM")) {
                if (token.contains(crisisKeyword) || crisisKeyword.contains(token)) {
                    return "SELF_HARM";
                }
            }
        }

        // Check other intents by priority
        Set<String> tokensSet = new HashSet<>(tokens);

        if (hasAnyMatch(tokensSet, INTENT_PATTERNS.get("SEEK_SUPPORT"))) return "SEEK_SUPPORT";
        if (hasAnyMatch(tokensSet, INTENT_PATTERNS.get("TASK_STRESS"))) return "TASK_STRESS";
        if (hasAnyMatch(tokensSet, INTENT_PATTERNS.get("RELATION_ISSUE"))) return "RELATION_ISSUE";
        if (hasAnyMatch(tokensSet, INTENT_PATTERNS.get("COGNITIVE_CLARITY"))) return "COGNITIVE_CLARITY";
        if (hasAnyMatch(tokensSet, INTENT_PATTERNS.get("EXPRESS_EMOTION"))) return "EXPRESS_EMOTION";

        return "DAILY_SHARE";
    }

    private static boolean hasAnyMatch(Set<String> tokens, Set<String> patterns) {
        for (String token : tokens) {
            for (String pattern : patterns) {
                // Only match if token contains the full pattern, or if pattern contains token
                // but avoid single-character false positives by requiring minimum length
                if (token.contains(pattern)) {
                    return true;
                }
                // For reverse match (pattern contains token), require token to be at least 2 characters
                // to avoid single-character tokens matching multi-character patterns
                if (pattern.contains(token) && token.length() >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check for safety risk (self-harm or severe distress).
     */
    private static boolean checkSafetyRisk(List<String> tokens) {
        Set<String> tokensSet = new HashSet<>(tokens);
        // Check self-harm keywords
        if (hasAnyMatch(tokensSet, INTENT_PATTERNS.get("SELF_HARM"))) return true;

        // Check severe negative sentiment words (score <= -4)
        for (String token : tokens) {
            int score = ChineseSentimentLexicon.getScore(token);
            if (score <= -4) return true;
        }

        return false;
    }

    /**
     * Calculate intensity score (0-10) based on sentiment and intensifiers.
     */
    private static double calculateIntensity(List<String> tokens, double sentimentScore) {
        double baseIntensity = 5.0; // Neutral baseline

        // Adjust based on sentiment magnitude
        baseIntensity += Math.abs(sentimentScore) * 0.8;

        // Check for intensifiers
        for (String token : tokens) {
            if (ChineseIntensifiers.isAmplifying(token)) {
                baseIntensity += 0.5;
            }
        }

        // Check for crisis/severe words
        for (String token : tokens) {
            int score = ChineseSentimentLexicon.getScore(token);
            if (score <= -4) {
                baseIntensity = Math.max(baseIntensity, 8.0);
                break;
            }
        }

        return Math.max(0, Math.min(10, baseIntensity));
    }

    /**
     * Categorize sentiment score into label.
     */
    private static String categorizeSentiment(double score) {
        if (score <= -4) return "CRISIS";
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
    private static List<String> extractKeywords(List<String> tokens) {
        List<String> keywords = new ArrayList<>();

        for (String token : tokens) {
            // Skip stopwords
            if (ChineseStopwords.isStopword(token)) continue;

            // Skip intensifiers
            if (ChineseIntensifiers.isIntensifier(token)) continue;

            // Include if has sentiment value
            if (ChineseSentimentLexicon.getScore(token) != 0) {
                keywords.add(token);
                continue;
            }

            // Include if matches any theme keyword
            boolean matchesTheme = false;
            for (Set<String> themeKeywords : THEME_KEYWORDS.values()) {
                for (String kw : themeKeywords) {
                    if (token.contains(kw) || kw.contains(token)) {
                        matchesTheme = true;
                        break;
                    }
                }
                if (matchesTheme) break;
            }
            if (matchesTheme) {
                keywords.add(token);
            }
        }

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
