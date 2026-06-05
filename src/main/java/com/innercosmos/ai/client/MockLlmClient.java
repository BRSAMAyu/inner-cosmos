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
        String structured = structuredJson(request, text);
        if (structured != null) {
            return structured;
        }
        String mode = resolveMode(request.moduleName, text);
        boolean shouldSlowDown = detectSlowDown(text);

        List<String> segments = AuroraContentLibrary.buildReply(mode, text, shouldSlowDown);
        return String.join("\n\n", segments);
    }

    private String structuredJson(LlmRequest request, String text) {
        String module = request.moduleName == null ? "" : request.moduleName.toUpperCase();

        // For LETTER_GUARD, extract the actual letter text from requestJson for analysis
        String textToAnalyze = text;
        if (module.contains("LETTER_GUARD") && request.requestJson != null) {
            textToAnalyze = extractLetterText(request.requestJson);
        }

        if (module.contains("AURORA") && request.requestJson != null) {
            textToAnalyze = extractAuroraUserText(request.requestJson, text);
        }

        // Analyze input for semantic understanding
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze(textToAnalyze);

        if (module.contains("AURORA_CHAT") || module.contains("AURORA_GREETING")) {
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
            return buildLetterGuardJson(textToAnalyze, analysis);
        }
        return null;
    }

    /**
     * Build dynamic AURORA_CHAT JSON based on semantic analysis.
     * Now produces input-dependent responses.
     */
    private String buildAuroraChatJson(String text, AnalysisResult analysis) {
        List<String> segments = new ArrayList<>();
        boolean greeting = text.contains("主动发起对话") || text.contains("AURORA_GREETING");
        if (greeting) {
            segments.add("我先来找你一下。今天不用等到想清楚再开口，我们可以从一句很小的话开始。");
            if (random.nextBoolean()) {
                segments.add("如果你愿意，我可以陪你聊今天最占心的位置，也可以只是陪你把脑子里的噪音放下来。");
            }
        } else if ("CRISIS".equals(analysis.sentimentLabel)) {
            segments.add("我先把安全放在最前面。你现在不需要一个人扛着，请尽快联系身边可信任的人或当地紧急支持。");
        } else if ("NEGATIVE".equals(analysis.sentimentLabel) || containsAny(text, List.of("累", "焦虑", "烦", "崩"))) {
            segments.add("我听见这件事对你不是轻轻掠过的那种影响。它像是在你心里占了一块位置，而且已经待了一阵子。");
            segments.add("我们先不急着把它解释成你哪里做得不够好，可以先把事实、感受和真正想要的东西分开。");
        } else if ("POSITIVE".equals(analysis.sentimentLabel) || containsAny(text, List.of("开心", "高兴", "顺利"))) {
            segments.add("这个瞬间我想先替你接住。不是所有好的感受都要立刻进入下一步，它本身就值得被看见。");
            segments.add("也许你可以把这件事存成一张记忆卡，让以后低落的时候还能回头看到它。");
        } else if (containsAny(text, List.of("关系", "朋友", "家人", "同事"))) {
            segments.add("关系里的事经常不是一句对错能说清的。我更想陪你先分清：发生了什么、你有什么反应、你真正希望被理解的是什么。");
        } else if (containsAny(text, List.of("任务", "工作", "拖延"))) {
            segments.add("这听起来像是任务已经在脑子里变得太大了。我们可以先不碰完整计划，只找一个十分钟内能开始的小动作。");
        } else {
            segments.add("我在。你可以不用把话组织得很漂亮，先把现在最真实的那一句放到这里。");
            if (random.nextBoolean()) {
                segments.add("我会根据你说的内容，帮你慢慢整理成记忆、情绪线索或一个很小的下一步。");
            }
        }
        if (segments.size() == 1 && random.nextInt(3) == 0) {
            segments.add("如果这句话后面还有一点没说完的地方，我也想听。");
        }

        String detectedTheme = analysis.detectedThemes.isEmpty() ? "日常倾诉" : analysis.detectedThemes.get(0);
        String nextQuestion;
        if ("SELF_HARM".equals(analysis.primaryIntent)) {
            nextQuestion = "你身边现在有没有一个可以立刻联系到的可信任的人？";
        } else if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            nextQuestion = "如果只允许做十分钟，第一步可以小到什么程度？";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            nextQuestion = "这段关系里，你最希望对方真正听懂哪一句话？";
        } else {
            nextQuestion = "此刻最需要被我听见的是哪一部分？";
        }

        String smallStep;
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            smallStep = "只打开任务入口，不要求完成。";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            smallStep = "写下事实和感受各一句。";
        } else {
            smallStep = "写下一句最真实的话。";
        }

        return String.format("""
                {
                  "segments": %s,
                  "detectedTheme": "%s",
                  "nextQuestion": "%s",
                  "smallStep": "%s",
                  "memoryReferenced": false,
                  "referencedMemoryIds": []
                }
                """,
            toJsonArray(segments),
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
        fragments.add("{\"type\":\"FEELING\",\"rawExcerpt\":\"" + extractFragment(text, "feeling") + "\",\"analysis\":\"最先需要被承认的是" + coreFeeling + ".\",\"reframe\":\"先允许这个感受存在.\"}");
        fragments.add("{\"type\":\"NEED\",\"rawExcerpt\":\"" + extractFragment(text, "need") + "\",\"analysis\":\"背后可能有一个尚未被满足的需要.\",\"reframe\":\"需要被看见并不等于脆弱.\"}");

        // Add BELIEF fragment if self-evaluation detected
        if (analysis.detectedThemes.contains("自我评价")) {
            fragments.add("{\"type\":\"BELIEF\",\"rawExcerpt\":\"" + extractFragment(text, "belief") + "\",\"analysis\":\"这里可能把事件和自我价值绑在一起.\",\"reframe\":\"一件事没做好不等于整个人不行.\"}");
        } else {
            fragments.add("{\"type\":\"BELIEF\",\"rawExcerpt\":\"自我判断\",\"analysis\":\"这里可能有一个过快的自我判断.\",\"reframe\":\"把事情没做好和我这个人不行暂时分开.\"}");
        }

        // Add ACTION fragment
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            fragments.add("{\"type\":\"ACTION\",\"rawExcerpt\":\"任务压力\",\"analysis\":\"可以留下十分钟动作.\",\"reframe\":\"先打开文件十分钟.\"}");
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            fragments.add("{\"type\":\"ACTION\",\"rawExcerpt\":\"关系\",\"analysis\":\"可以先写下对方说的话.\",\"reframe\":\"把对方的陈述和你的感受分开看.\"}");
        } else {
            fragments.add("{\"type\":\"ACTION\",\"rawExcerpt\":\"下一步\",\"analysis\":\"可以留下一个很小的下一步.\",\"reframe\":\"下一步小到十分钟内能开始.\"}");
        }

        // Noise to drop
        List<String> noise = new ArrayList<>();
        noise.add("\"把一次混乱直接解释成\\\"我整个人都不行\\\"的结论\"");
        if (containsAny(text, List.of("应该", "必须", "一定"))) {
            noise.add("\"那些把自己逼到没有余地的\\\"应该\\\"和\\\"必须\\\"\"");
        }
        if (containsAny(text, List.of("永远", "每次", "总是", "从来"))) {
            noise.add("\"把今天扩大成永远的绝对化说法\"");
        }

        // Suggested todo
        String todoJson = "null";
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            todoJson = "{\"taskName\":\"把任务打开并推进十分钟\",\"description\":\"由思维碎纸机从混乱输入中提取.\",\"priority\":\"HIGH\"}";
        } else if (analysis.intensityScore > 7) {
            todoJson = "{\"taskName\":\"把最重的一句话写下来\",\"description\":\"由思维碎纸机从混乱输入中提取.\",\"priority\":\"MEDIUM\"}";
        }

        return String.format("""
                {
                  "coreFeeling": "%s",
                  "hiddenNeed": "%s",
                  "noiseToDrop": [%s],
                  "sentenceToKeep": "我现在感到%s,背后也许是在需要%s.",
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
                  "emotionTrace": {"emotionName":"%s","emotionScore":%.1f,"weatherType":"%s","triggerScene":"用户完成了一次自我表达."},
                  "fragments": [
                    {"type":"FACT","rawExcerpt":"一次表达","analysis":"从表达中抽取出的事实片段.","reframe":"先区分事实和解释."},
                    {"type":"FEELING","rawExcerpt":"%s","analysis":"表达里出现的主要感受线索.","reframe":"允许感受存在."},
                    {"type":"BELIEF","rawExcerpt":"自我判断","analysis":"可能有过快的自我判断.","reframe":"把事件和自我价值分开."},
                    {"type":"ACTION","rawExcerpt":"下一步","analysis":"可以轻轻推进一步.","reframe":"下一步小到十分钟内能开始."}
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
                {"dominantTheme":"%s","themeSummary":"本周的记录显示出一些正在形成的模式.","emotionTrend":"%s","gravityChangeSummary":"高引力记忆开始形成可观察的模式.","weeklyObservation":"这一周最重要的变化,是你开始把混乱整理成可以被看见的线索."}
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
            reply = "我听见这句话里有一些很重的声音.作为有限的回声,我不能替代现实中的人,但我想说,你现在的感受很重要,值得被一个人真实地听见.";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            reply = "我听见关系里有一些牵动.作为有限的数字回声,我只能陪你看见其中一部分:这段关系里,你最想被理解的是什么?";
        } else {
            reply = "我听见了这个片段.作为有限的数字回声,我只能陪你看见其中一部分:你最想继续靠近的是什么?";
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
        String[] parts = text.split("[,.!?,\\.!\\?]", 3);
        return parts.length > 0 ? parts[0].trim() : text.substring(0, Math.min(20, text.length()));
    }

    private String extractLetterText(String requestJson) {
        try {
            if (requestJson == null || requestJson.isBlank()) {
                return "";
            }
            int start = requestJson.indexOf("\"letterText\":");
            if (start == -1) {
                return requestJson;
            }
            start = requestJson.indexOf("\"", start + 13) + 1;
            if (start == 0) return requestJson;
            int end = requestJson.indexOf("\"", start);
            if (end == -1) return requestJson;
            String extracted = requestJson.substring(start, end);
            // Handle escaped characters
            return extracted.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) {
            return requestJson;
        }
    }

    private String firstSentence(String raw) {
        if (raw == null || raw.isBlank()) return "用户完成了一次自我表达.";
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

    private String toJsonArray(List<String> values) {
        List<String> safe = values == null || values.isEmpty()
                ? List.of("我在。你可以先从最真实的一句话开始。")
                : values;
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < safe.size(); i++) {
            if (i > 0) builder.append(",");
            builder.append("\"").append(escapeJson(safe.get(i))).append("\"");
        }
        builder.append("]");
        return builder.toString();
    }

    private String extractAuroraUserText(String requestJson, String fallback) {
        requestJson = decodeUnicodeEscapes(requestJson);
        String direct = extractJsonString(requestJson, "userMessage");
        if (direct != null && !direct.isBlank()) return direct;
        String marker = "=== 用户刚刚说的话 ===";
        int start = requestJson.indexOf(marker);
        if (start < 0) return fallback == null ? "" : fallback;
        start += marker.length();
        int end = requestJson.indexOf("=== 结束 ===", start);
        String raw = end > start ? requestJson.substring(start, end) : requestJson.substring(start);
        return raw.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\r", "\r")
                .trim();
    }

    private String extractJsonString(String json, String key) {
        if (json == null || key == null) return null;
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                builder.append(c == 'n' ? '\n' : c == 'r' ? '\r' : c == 't' ? '\t' : c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return builder.toString();
            } else {
                builder.append(c);
            }
        }
        return null;
    }

    private String decodeUnicodeEscapes(String value) {
        if (value == null || !value.contains("\\u")) return value == null ? "" : value;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i + 5 < value.length() && value.charAt(i) == '\\' && value.charAt(i + 1) == 'u') {
                String hex = value.substring(i + 2, i + 6);
                try {
                    builder.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                    // fall through and keep the original characters
                }
            }
            builder.append(value.charAt(i));
        }
        return builder.toString();
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
