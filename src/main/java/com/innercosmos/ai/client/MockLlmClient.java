package com.innercosmos.ai.client;

import com.innercosmos.ai.lexicon.ChineseSentimentLexicon;
import com.innercosmos.ai.lexicon.ChineseStopwords;
import com.innercosmos.ai.prompt.AuroraContentLibrary;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

public class MockLlmClient implements LlmClient {
    private final Executor aiExecutor;
    private final Random random = new Random();

    public MockLlmClient(Executor aiExecutor) {
        this.aiExecutor = aiExecutor;
    }

    @Override
    public String chat(LlmRequest request) {
        String text = request.prompt == null ? "" : request.prompt;
        String structured = structuredJson(request.moduleName, text);
        if (structured != null) {
            return structured;
        }
        String mode = resolveMode(request.moduleName, text);
        boolean shouldSlowDown = detectSlowDown(text);

        List<String> segments = AuroraContentLibrary.buildReply(mode, text, shouldSlowDown);
        return String.join("\n\n", segments);
    }

    private String structuredJson(String moduleName, String text) {
        String module = moduleName == null ? "" : moduleName.toUpperCase();

        // Analyze input for semantic understanding
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze(text);

        if (module.contains("AURORA_CHAT")) {
            return buildAuroraChatJson(text, analysis);
        }
        if (module.contains("THOUGHT_SHREDDER")) {
            return buildThoughtShredderJson(text, analysis);
        }
        if (module.contains("MEMORY_SETTLEMENT")) {
            return buildMemorySettlementJson(text, analysis);
        }
        if (module.contains("WEEKLY_REVIEW")) {
            return buildWeeklyReviewJson(analysis);
        }
        if (module.contains("PERSONA_CHAT")) {
            return buildPersonaChatJson(text, analysis);
        }
        if (module.contains("LETTER_GUARD")) {
            return buildLetterGuardJson(text, analysis);
        }
        return null;
    }

    /**
     * Build dynamic AURORA_CHAT JSON based on semantic analysis.
     * Now produces input-dependent responses.
     */
    private String buildAuroraChatJson(String text, AnalysisResult analysis) {
        // Generate dynamic segments based on sentiment and themes
        List<String> segments = new ArrayList<>();

        // Segment 1: Reflective opening based on sentiment
        if ("CRISIS".equals(analysis.sentimentLabel)) {
            segments.add("我听见你现在很不容易。我们先停一停，不急着处理任何事，先把呼吸找回来。");
        } else if ("NEGATIVE".equals(analysis.sentimentLabel)) {
            String theme = analysis.detectedThemes.isEmpty() ? "这件事" : analysis.detectedThemes.get(0);
            segments.add("我听见" + theme + "对你有重量，尤其是此刻这种感觉。");
        } else if ("POSITIVE".equals(analysis.sentimentLabel)) {
            segments.add("我感觉到今天有一些明亮的东西在。");
        } else {
            segments.add("我听见这件事现在对你有重量。");
        }

        // Segment 2: Clarification/reframing based on detected themes
        if (analysis.detectedThemes.contains("任务压力")) {
            segments.add("我们先把'这件事'和'你这个人'分开来看，压力是一件事，你的价值是另一件事。");
        } else if (analysis.detectedThemes.contains("关系牵动")) {
            segments.add("关系里的事总是牵动很多层。我们先看看你的感受和对方的说法，各是什么。");
        } else if (analysis.detectedThemes.contains("情绪承压")) {
            segments.add("我们先不急着把情绪推开，允许它存在一会儿，也许能看到它背后在说什么。");
        } else if (analysis.detectedThemes.contains("认知探索")) {
            segments.add("这团东西现在还模糊，我们可以把它拆开，看看事实、感受和想法各是什么。");
        } else {
            segments.add("我们先把事实、感受和下一步分开看。");
        }

        // Segment 3: Small step suggestion based on intent
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            segments.add("现在可以只留下一个很小的动作——也许只是打开那个文件，看十分钟。");
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            segments.add("现在可以只做一件事：写下对方说了什么，以及你实际感受到什么。");
        } else if ("SELF_HARM".equals(analysis.primaryIntent)) {
            segments.add("现在最小的一步，是先让自己活下来，其他的明天再说。");
        } else {
            segments.add("现在可以只留下一个很小的动作。");
        }

        // Dynamic theme detection
        String detectedTheme = analysis.detectedThemes.isEmpty() ? "日常" : analysis.detectedThemes.get(0);

        // Dynamic next question based on intent
        String nextQuestion;
        if ("SELF_HARM".equals(analysis.primaryIntent)) {
            nextQuestion = "你能答应我现在先不做任何伤害自己的决定，给自己五分钟吗？";
        } else if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            nextQuestion = "如果只选一个最需要被看见的部分，会是哪一步最让你卡住？";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            nextQuestion = "这段关系里，你最不想被看见但又最想说出来的是什么？";
        } else {
            nextQuestion = "如果只选一个最需要被看见的部分，会是哪一个？";
        }

        // Dynamic small step
        String smallStep;
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            smallStep = "把任务打开，只做十分钟。";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            smallStep = "写下关系里最重的一句话。";
        } else {
            smallStep = "写下最重的一句话。";
        }

        return String.format("""
                {
                  "segments": ["%s", "%s", "%s"],
                  "detectedTheme": "%s",
                  "nextQuestion": "%s",
                  "smallStep": "%s",
                  "memoryReferenced": false,
                  "referencedMemoryIds": []
                }
                """,
            escapeJson(segments.get(0)),
            escapeJson(segments.get(1)),
            escapeJson(segments.get(2)),
            escapeJson(detectedTheme),
            escapeJson(nextQuestion),
            escapeJson(smallStep)
        ).replace("\n", "");
    }

    /**
     * Build dynamic THOUGHT_SHREDDER JSON based on semantic analysis.
     */
    private String buildThoughtShredderJson(String text, AnalysisResult analysis) {
        // Core feeling from analysis
        String coreFeeling;
        if (analysis.detectedThemes.contains("情绪承压")) {
            coreFeeling = "疲惫和压力";
        } else if (analysis.detectedThemes.contains("关系牵动")) {
            coreFeeling = "委屈和难过";
        } else if (analysis.sentimentScore <= -3) {
            coreFeeling = "很重的负面感受";
        } else {
            coreFeeling = "有一些混乱的感受";
        }

        // Hidden need
        String hiddenNeed;
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            hiddenNeed = "把压力变成可开始的一步";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            hiddenNeed = "在关系里保留自己的位置";
        } else if ("SELF_HARM".equals(analysis.primaryIntent)) {
            hiddenNeed = "获得一点活下去的支撑";
        } else {
            hiddenNeed = "让混乱先有一个可以被放下的形状";
        }

        // Build fragments based on keywords
        List<String> fragments = new ArrayList<>();
        fragments.add("{\"type\":\"FEELING\",\"rawExcerpt\":\"" + extractFragment(text, "feeling") + "\",\"analysis\":\"最先需要被承认的是" + coreFeeling + "。\",\"reframe\":\"先允许这个感受存在。\"}");
        fragments.add("{\"type\":\"NEED\",\"rawExcerpt\":\"" + extractFragment(text, "need") + "\",\"analysis\":\"背后可能有一个尚未被满足的需要。\",\"reframe\":\"需要被看见并不等于脆弱。\"}");

        // Add BELIEF fragment if self-evaluation detected
        if (analysis.detectedThemes.contains("自我评价")) {
            fragments.add("{\"type\":\"BELIEF\",\"rawExcerpt\":\"" + extractFragment(text, "belief") + "\",\"analysis\":\"这里可能把事件和自我价值绑在一起。\",\"reframe\":\"一件事没做好不等于整个人不行。\"}");
        } else {
            fragments.add("{\"type\":\"BELIEF\",\"rawExcerpt\":\"自我判断\",\"analysis\":\"这里可能有一个过快的自我判断。\",\"reframe\":\"把事情没做好和我这个人不行暂时分开。\"}");
        }

        // Add ACTION fragment
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            fragments.add("{\"type\":\"ACTION\",\"rawExcerpt\":\"任务压力\",\"analysis\":\"可以留下十分钟动作。\",\"reframe\":\"先打开文件十分钟。\"}");
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            fragments.add("{\"type\":\"ACTION\",\"rawExcerpt\":\"关系\",\"analysis\":\"可以先写下对方说的话。\",\"reframe\":\"把对方的陈述和你的感受分开看。\"}");
        } else {
            fragments.add("{\"type\":\"ACTION\",\"rawExcerpt\":\"下一步\",\"analysis\":\"可以留下一个很小的下一步。\",\"reframe\":\"下一步小到十分钟内能开始。\"}");
        }

        // Noise to drop
        List<String> noise = new ArrayList<>();
        noise.add("\"把一次混乱直接解释成\\\"我整个人都不行\\\"的结论\"");
        if (containsAny(text, "应该", "必须", "一定")) {
            noise.add("\"那些把自己逼到没有余地的\\\"应该\\\"和\\\"必须\\\"\"");
        }
        if (containsAny(text, "永远", "每次", "总是", "从来")) {
            noise.add("\"把今天扩大成永远的绝对化说法\"");
        }

        // Suggested todo
        String todoJson = "null";
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            todoJson = "{\"taskName\":\"把任务打开并推进十分钟\",\"description\":\"由思维碎纸机从混乱输入中提取。\",\"priority\":\"HIGH\"}";
        } else if (analysis.intensityScore > 7) {
            todoJson = "{\"taskName\":\"把最重的一句话写下来\",\"description\":\"由思维碎纸机从混乱输入中提取。\",\"priority\":\"MEDIUM\"}";
        }

        return String.format("""
                {
                  "coreFeeling": "%s",
                  "hiddenNeed": "%s",
                  "noiseToDrop": [%s],
                  "sentenceToKeep": "我现在感到%s，背后也许是在需要%s。",
                  "fragments": [%s],
                  "suggestedTodo": %s,
                  "intensityScore": %.1f,
                  "memoryType": "SHREDDER"
                }
                """,
            escapeJson(coreFeeling),
            escapeJson(hiddenNeed),
            String.join(",", noise),
            escapeJson(coreFeeling),
            escapeJson(hiddenNeed),
            String.join(",", fragments),
            todoJson,
            analysis.intensityScore
        ).replace("\n", "");
    }

    /**
     * Build dynamic MEMORY_SETTLEMENT JSON.
     */
    private String buildMemorySettlementJson(String text, AnalysisResult analysis) {
        String memoryType = "EMOTION";
        if ("TASK_STRESS".equals(analysis.primaryIntent)) memoryType = "TODO";
        else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) memoryType = "RELATION";

        String emotionTag = analysis.detectedThemes.isEmpty() ? "self-observation" : analysis.detectedThemes.get(0);
        String weatherType = analysis.intensityScore >= 7 ? "STORM" : analysis.intensityScore >= 5 ? "RAINY" : analysis.intensityScore >= 3 ? "CLOUDY" : "SUNNY";

        return String.format("""
                {
                  "memoryCard": {"title":"今日沉淀","summary":"%s","memoryType":"%s","emotionTags":["%s"],"keywordTags":["日常"],"peopleTags":[],"intensityScore":%.1f,"userImportance":4.0},
                  "emotionTrace": {"emotionName":"%s","emotionScore":%.1f,"weatherType":"%s","triggerScene":"用户完成了一次自我表达。"},
                  "fragments": [
                    {"type":"FACT","rawExcerpt":"一次表达","analysis":"从表达中抽取出的事实片段。","reframe":"先区分事实和解释。"},
                    {"type":"FEELING","rawExcerpt":"%s","analysis":"表达里出现的主要感受线索。","reframe":"允许感受存在。"},
                    {"type":"BELIEF","rawExcerpt":"自我判断","analysis":"可能有过快的自我判断。","reframe":"把事件和自我价值分开。"},
                    {"type":"ACTION","rawExcerpt":"下一步","analysis":"可以轻轻推进一步。","reframe":"下一步小到十分钟内能开始。"}
                  ],
                  "eventCards": [],
                  "relationMentions": [],
                  "todos": []
                }
                """,
            escapeJson(firstSentence(text)),
            memoryType,
            escapeJson(emotionTag),
            analysis.intensityScore,
            escapeJson(getEmotionName(analysis.sentimentLabel)),
            analysis.intensityScore,
            weatherType,
            escapeJson(getEmotionName(analysis.sentimentLabel))
        ).replace("\n", "");
    }

    /**
     * Build WEEKLY_REVIEW JSON.
     */
    private String buildWeeklyReviewJson(AnalysisResult analysis) {
        String dominantTheme = analysis.detectedThemes.isEmpty() ? "本周主题" : analysis.detectedThemes.get(0);
        String emotionTrend = analysis.sentimentScore > 0 ? "CLOUDY -> SUNNY" : analysis.sentimentScore < -2 ? "SUNNY -> RAINY" : "CLOUDY -> RAINY";

        return String.format("""
                {"dominantTheme":"%s","themeSummary":"本周的记录显示出一些正在形成的模式。","emotionTrend":"%s","gravityChangeSummary":"高引力记忆开始形成可观察的模式。","weeklyObservation":"这一周最重要的变化，是你开始把混乱整理成可以被看见的线索。"}
                """,
            escapeJson(dominantTheme),
            emotionTrend
        ).replace("\n", "");
    }

    /**
     * Build PERSONA_CHAT JSON.
     */
    private String buildPersonaChatJson(String text, AnalysisResult analysis) {
        String reply;
        if ("SELF_HARM".equals(analysis.primaryIntent)) {
            reply = "我听见这句话里有一些很重的声音。作为有限的回声，我不能替代现实中的人，但我想说，你现在的感受很重要，值得被一个人真实地听见。";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            reply = "我听见关系里有一些牵动。作为有限的数字回声，我只能陪你看见其中一部分：这段关系里，你最想被理解的是什么？";
        } else {
            reply = "我听见了这个片段。作为有限的数字回声，我只能陪你看见其中一部分：你最想继续靠近的是什么？";
        }

        return String.format("""
                {"reply":"%s","boundaryNotice":"","letterSuggested":%s,"riskFlags":[]}
                """,
            escapeJson(reply),
            analysis.intensityScore > 6 ? "true" : "false"
        ).replace("\n", "");
    }

    /**
     * Build LETTER_GUARD JSON with enhanced keyword detection.
     */
    private String buildLetterGuardJson(String text, AnalysisResult analysis) {
        // Enhanced detection using semantic analysis
        boolean blocked = analysis.needsSafetyIntervention || containsAny(text, List.of("威胁", "骚扰", "人肉", "人肉搜索", "曝光", "泄露"));

        if (blocked) {
            String riskType = analysis.needsSafetyIntervention ? "CRISIS_KEYWORD" : "ABUSE";
            return "{\"allow\":false,\"reason\":\"contains unsafe boundary language\",\"riskFlags\":[\"" + riskType + "\"]}";
        }
        return "{\"allow\":true,\"reason\":\"passed\",\"riskFlags\":[]}";
    }

    // Helper methods
    private String extractFragment(String text, String type) {
        String[] parts = text.split("[，。！？,\\.!\\?]", 3);
        return parts.length > 0 ? parts[0].trim() : text.substring(0, Math.min(20, text.length()));
    }

    private String firstSentence(String raw) {
        if (raw == null || raw.isBlank()) return "用户完成了一次自我表达。";
        String compact = raw.replaceAll("\\s+", " ").trim();
        return compact.length() > 64 ? compact.substring(0, 64) + "..." : compact;
    }

    private String getEmotionName(String sentimentLabel) {
        switch (sentimentLabel) {
            case "CRISIS": return "危机";
            case "NEGATIVE": return "负面";
            case "POSITIVE": return "积极";
            default: return "复杂";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Determine conversation mode from moduleName or user-message keywords.
     */
    private String resolveMode(String moduleName, String text) {
        if (moduleName != null) {
            String upper = moduleName.toUpperCase();
            for (String known : List.of("DAILY_TALK", "THOUGHT_CLARIFY", "SLEEP_REVIEW",
                                        "SOCRATIC", "ACTION_SPLIT", "RELATION_REVIEW")) {
                if (upper.contains(known)) return known;
            }
        }
        // Keyword heuristic fallback
        if (containsAny(text, List.of("拖延", "不想做", "作业", "考试", "焦虑", "任务"))) {
            return "ACTION_SPLIT";
        }
        if (containsAny(text, List.of("吵", "冲突", "关系", "朋友", "同学"))) {
            return "RELATION_REVIEW";
        }
        if (containsAny(text, List.of("想不通", "混乱", "理不清", "脑子"))) {
            return "THOUGHT_CLARIFY";
        }
        if (containsAny(text, List.of("应该", "对不对", "是不是", "到底"))) {
            return "SOCRATIC";
        }
        if (containsAny(text, List.of("睡不着", "深夜", "晚安", "睡前", "夜"))) {
            return "SLEEP_REVIEW";
        }
        return "DAILY_TALK";
    }

    /**
     * Detect whether the user has been talking a lot and a rhythm-slow-down hint is warranted.
     */
    private boolean detectSlowDown(String text) {
        return containsAny(text, List.of("说了很多", "太累了", "不想说了", "太多了", "够了"));
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        aiExecutor.execute(() -> {
            try {
                String response = chat(request);
                for (String token : response.split("")) {
                    emitter.send(SseEmitter.event().name("token").data("{\"content\":\"" + escape(token) + "\"}"));
                    Thread.sleep(18);
                }
                emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                emitter.complete();
            } catch (IOException | InterruptedException exception) {
                emitter.completeWithError(exception);
                Thread.currentThread().interrupt();
            }
        });
        return emitter;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String escape(String token) {
        return token.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
