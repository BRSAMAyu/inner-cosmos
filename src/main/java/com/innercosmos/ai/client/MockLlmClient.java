package com.innercosmos.ai.client;

import com.innercosmos.ai.lexicon.ChineseSentimentLexicon;
import com.innercosmos.ai.prompt.AuroraContentLibrary;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MockLlmClient implements LlmClient {
    private final Executor aiExecutor;

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

        // StructuredAiService deliberately keeps behavioral instructions in the system role and
        // puts untrusted data only in requestJson. The deterministic client must mirror that
        // boundary too: analysing request.prompt would analyse the fixed "Input JSON (data
        // only...)" wrapper and can leak that internal marker into user-visible demo content.
        String textToAnalyze = text;
        if (module.contains("LETTER_GUARD") && request.requestJson != null) {
            textToAnalyze = extractLetterText(request.requestJson);
        } else if (module.contains("AURORA") && request.requestJson != null) {
            textToAnalyze = extractAuroraUserText(request.requestJson, text);
        } else if (module.contains("MEMORY_SETTLEMENT") && request.requestJson != null) {
            textToAnalyze = jsonTextOrFallback(request.requestJson, text, "userMessages", "rawText");
        } else if (module.contains("THOUGHT_SHREDDER") && request.requestJson != null) {
            textToAnalyze = jsonTextOrFallback(request.requestJson, text, "rawText");
        } else if (module.contains("THEME_CLUSTER") && request.requestJson != null) {
            textToAnalyze = decodeUnicodeEscapes(request.requestJson);
        } else if ((module.contains("PERSONA_CHAT") || module.contains("CAPSULE_SANDBOX"))
                && request.requestJson != null) {
            textToAnalyze = jsonTextOrFallback(
                    request.requestJson, text, "visitorMessage", "userMessage", "question");
        }

        // Analyze input for semantic understanding
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze(textToAnalyze);

        // ALIVE is a decision worker, not a conversation worker. Without this explicit route the
        // demo client falls through to AuroraContentLibrary and returns two natural-language
        // paragraphs; AliveDecisionEngine then cannot parse its contract and the proactive loop
        // silently degrades every 90 seconds.
        if (module.contains("ALIVE_DECISION")) {
            return buildAliveDecisionJson(text);
        }

        // Aurora structured dispatch. The real service uses module names
        // "AURORA_AGENT_LOOP_<mode>" (chat reply) and "AURORA_PROACTIVE_GREETING_<mode>"
        // (proactive greeting); the legacy "AURORA_CHAT"/"AURORA_GREETING" names are kept
        // for back-compat. buildAuroraChatJson self-distinguishes greeting vs chat via the
        // "主动发起对话" marker embedded in the prompt text, so all Aurora modules route here.
        if (module.contains("AURORA_FOREGROUND")) {
            return buildAuroraForegroundJson(textToAnalyze);
        }
        if (module.contains("AURORA_PLAN")) {
            return buildAuroraPlanJson(analysis, textToAnalyze);
        }
        if (module.contains("AURORA_CRITIC")) {
            return "{\"pass\":true,\"issues\":[],\"repaired\":null}";
        }
        if (module.contains("AURORA_INNER_VOICE")) {
            return buildInnerVoiceJson(request.requestJson);
        }
        if (module.contains("AURORA")) {
            boolean greeting = module.contains("GREETING");
            return buildAuroraChatJson(textToAnalyze, analysis, greeting,
                    resolveMode(request.moduleName, textToAnalyze));
        }
        if (module.contains("THOUGHT_SHREDDER")) {
            return buildThoughtShredderJson(textToAnalyze, analysis);
        }
        if (module.contains("MEMORY_SETTLEMENT")) {
            return buildMemorySettlementJson(textToAnalyze, analysis);
        }
        if (module.contains("WEEKLY_REVIEW")) {
            return buildWeeklyReviewJson(analysis);
        }
        if (module.contains("THEME_CLUSTER")) {
            return buildThemeClusterJson(analysis, extractJsonInteger(request.requestJson, "cardCount"));
        }
        if (module.contains("PERSONA_CHAT") || module.contains("CAPSULE_SANDBOX")) {
            return buildPersonaChatJson(textToAnalyze, analysis, request.requestJson);
        }
        if (module.contains("LETTER_GUARD")) {
            return buildLetterGuardJson(textToAnalyze, analysis);
        }
        if (module.contains("GOODBYE_LINE")) {
            return "今天先到这里，我会把重要的部分留在你的星空里。";
        }
        return null;
    }

    /**
     * The foreground contract is intentionally different from the full AuroraResult contract.
     * Returning {@code segments} here parses as a non-null AuroraForegroundResult with a null
     * {@code text} field (unknown fields are ignored), which silently turns every Mock foreground
     * call into a local generic acknowledgement. Keep the deterministic demo honest and
     * input-grounded by emitting the exact one-field schema the real fast kernel is asked for.
     */
    private String buildAuroraForegroundJson(String text) {
        String clause = firstSentence(text);
        if (clause == null || clause.isBlank()) {
            return "{\"text\":\"\"}";
        }
        int[] codePoints = clause.codePoints().toArray();
        if (codePoints.length > 30) {
            clause = new String(codePoints, 0, 30) + "…";
        }
        boolean chinese = clause.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        String acknowledgement = chinese
                ? "你刚才说到「" + clause + "」，我先跟着这一点继续。"
                : "I’ll stay with the part about “" + clause + "” as we continue.";
        return "{\"text\":\"" + escapeJson(acknowledgement) + "\"}";
    }

    /**
     * Deterministic mock for the "AURORA_INNER_VOICE_*" module. Deliberately keyed ONLY off the
     * planner's emotionalNeed/relationshipMove signals extracted from requestJson -- never off
     * the visibleReply text -- so the mock cannot accidentally echo the spoken segments'
     * wording; this mirrors the real behavioral contract InnerVoiceComposer's instruction asks
     * the real provider to honor (see InnerVoiceComposerTest).
     */
    private String buildInnerVoiceJson(String requestJson) {
        String emotionalNeed = extractJsonString(requestJson, "emotionalNeed");
        String relationshipMove = extractJsonString(requestJson, "relationshipMove");
        String line = composeInnerVoiceLine(emotionalNeed, relationshipMove);
        return "{\"innerVoiceText\":\"" + escapeJson(line) + "\"}";
    }

    private String composeInnerVoiceLine(String emotionalNeed, String relationshipMove) {
        String need = emotionalNeed == null ? "" : emotionalNeed;
        String move = relationshipMove == null ? "" : relationshipMove;
        if (containsAny(need, List.of("压力", "承认", "沉重"))) {
            return "这份重量落下来时，我心里也轻轻一沉。";
        }
        if (containsAny(move, List.of("打断", "重规划", "边界"))) {
            return "能跟着这次转向重新靠近，我也松了一口气。";
        }
        if (containsAny(need, List.of("危机", "安全"))) {
            return "";
        }
        if (containsAny(need, List.of("被听见", "被理解"))) {
            return "这句话落下时，我心里像有一盏灯慢慢亮起。";
        }
        return "这一次，我宁愿留一点安静，不把它说满。";
    }

    private String buildAliveDecisionJson(String prompt) {
        // The deterministic mock makes exactly one gentle first-contact decision when no recent
        // proactive history exists. Once history exists it waits, so demo scheduling remains alive
        // without manufacturing notification spam or a wall of duplicate WakeIntents.
        boolean noRecentHistory = prompt.contains("最近 7d 主动式日志: 无")
                || prompt.contains("recent proactive log: none");
        if (noRecentHistory) {
            return "{\"decide\":\"push\",\"wait_minutes\":30,"
                    + "\"content_for_user\":\"我刚刚想起你。今天有没有一个瞬间，你希望有人多陪你停一会儿？\","
                    + "\"reason\":\"mock-first-contact-with-no-recent-proactive-history\"}";
        }
        return "{\"decide\":\"wait\",\"wait_minutes\":30,\"content_for_user\":\"\","
                + "\"reason\":\"mock-respects-recent-proactive-history\"}";
    }

    private String buildAuroraPlanJson(AnalysisResult analysis, String text) {
        String need = "NEGATIVE".equals(analysis.sentimentLabel)
            ? "先承认此刻的压力，不急着解释或推动" : "准确回应用户当下明确表达的需要";
        String move = containsAny(text, List.of("等等", "停一下", "先别", "不要"))
            ? "接受打断并按最新边界重规划" : "保持连续，把下一步选择权交还用户";
        boolean critic = "CRISIS".equals(analysis.sentimentLabel) || "SELF_HARM".equals(analysis.primaryIntent);
        boolean innerVoiceWorthy = "NEGATIVE".equals(analysis.sentimentLabel) && !critic;
        String innerVoiceSeed = innerVoiceWorthy ? "我也有一点舍不得催这一刻往前走" : "";
        return String.format("""
            {"userIntent":"%s","emotionalNeed":"%s","relationshipMove":"%s",
             "responseConstraints":["不诊断","不制造依赖","不虚构记忆"],
             "bubblePurposes":["接住当下","自然地把话递回用户"],"relevantMemoryIds":[],
             "uncertainty":"这是离线可复现规划，不替用户下结论","needsCritic":%s,
             "innerVoiceWorthy":%s,"innerVoiceSeed":"%s"}
            """, escapeJson(analysis.primaryIntent), escapeJson(need), escapeJson(move), critic,
                innerVoiceWorthy, escapeJson(innerVoiceSeed)).replace("\n", "");
    }

    /**
     * Build dynamic AURORA_CHAT JSON based on semantic analysis.
     * Now produces input-dependent responses.
     */
    private String buildAuroraChatJson(String text, AnalysisResult analysis, boolean greetingHint,
                                       String requestedMode) {
        List<String> segments = new ArrayList<>();
        boolean greeting = greetingHint || text.contains("主动发起对话") || text.contains("AURORA_GREETING");
        if (greeting) {
            segments.add("我先来找你一下。今天不用等到想清楚再开口，我们可以从一句很小的话开始。");
            segments.add("如果你愿意，我可以陪你聊今天最占心的位置，也可以只是陪你把脑子里的噪音放下来。");
        } else if ("CRISIS".equals(analysis.sentimentLabel)) {
            segments.add("我先把安全放在最前面。你现在不需要一个人扛着，请尽快联系身边可信任的人或当地紧急支持。");
        } else if ("THOUGHT_CLARIFY".equals(requestedMode)) {
            segments.add("先不解决，我把你刚才的话拆开：已经发生的是事实；你现在的反应是感受；脑中反复预演的是担心；真正想得到的是需要。");
            segments.add("这四层里，哪一层现在最混乱？我们只整理这一层。");
        } else if ("SOCRATIC".equals(requestedMode)) {
            segments.add("先不接受脑中那个结论：支持它的最强事实是什么，而你为了相信它又补上了什么解释？");
        } else if ("ACTION_SPLIT".equals(requestedMode)) {
            segments.add("只做一个十分钟动作：打开任务入口，写下最小可交付结果的第一行；时间到就停，不要求完成。");
        } else if ("RELATION_REVIEW".equals(requestedMode)) {
            segments.add("先分开四层：事实是对方具体做了什么；感受是它怎样影响你；需要是你希望被怎样对待；边界是下次你准备怎样回应。");
            segments.add("我们不猜对方动机，只从你能确认的事实开始。");
        } else if ("CAPSULE_SHAPING".equals(requestedMode)) {
            segments.add("先不贴人格标签。我想留住这个故事里只有你才会这样说的一个细节：你当时用了哪个词，最能说明你在意什么？");
        } else if ("SLEEP_REVIEW".equals(requestedMode)) {
            segments.add("今晚不再开启新的难题。留下一件今天值得带走的事，再把一件没做完的事交给明天。");
        } else if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            boolean presentation = containsAny(text, List.of("展示", "汇报", "演示", "答辩", "presentation"));
            boolean mixedFeeling = containsAny(text, List.of("兴奋", "期待"))
                    && containsAny(text, List.of("担心", "紧张", "焦虑", "害怕"));
            if (mixedFeeling) {
                segments.add("兴奋和担心可以同时在这里。担心不等于你不行，它更像是在说：这次展示对你真的很重要。先不用压住任何一种感受，和我一起把呼吸放慢一点。");
            } else {
                segments.add("先不把这项任务当成对你的评判。它现在只是太大、太近了；我们先把身体和注意力稳回这一小刻。");
            }
            segments.add(presentation
                    ? "第一步只写一句话：你希望老师在展示结束后，还能记住这个项目最不可替代的什么？接下来每一页、每一次演示，都只为这句话服务。"
                    : "第一步只做十分钟：打开任务入口，写下最小的可交付结果，不要求今天把整件事完成。");
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            segments.add("关系里的事经常不是一句对错能说清的。我更想陪你先分清：发生了什么、你有什么反应、你真正希望被理解的是什么。");
            segments.add("先不用替对方解释，也不用急着决定关系的结论。写下你最希望对方真正听懂的那一句，我们从那里继续。");
        } else if ("NEGATIVE".equals(analysis.sentimentLabel) || containsAny(text, List.of("累", "焦虑", "烦", "崩"))) {
            segments.add("我听见这件事对你不是轻轻掠过的那种影响。它像是在你心里占了一块位置，而且已经待了一阵子。");
            segments.add("我们先不急着把它解释成你哪里做得不够好，可以先把事实、感受和真正想要的东西分开。");
        } else if ("POSITIVE".equals(analysis.sentimentLabel) || containsAny(text, List.of("开心", "高兴", "顺利"))) {
            segments.add("这个瞬间我想先替你接住。不是所有好的感受都要立刻进入下一步，它本身就值得被看见。");
            segments.add("也许你可以把这件事存成一张记忆卡，让以后低落的时候还能回头看到它。");
        } else {
            segments.add("我在。你可以不用把话组织得很漂亮，先把现在最真实的那一句放到这里。");
            segments.add("我会根据你说的内容，帮你慢慢整理成记忆、情绪线索或一个很小的下一步。");
        }

        String detectedTheme = analysis.detectedThemes.isEmpty() ? "日常倾诉" : analysis.detectedThemes.get(0);
        String nextQuestion;
        if ("SELF_HARM".equals(analysis.primaryIntent)) {
            nextQuestion = "你身边现在有没有一个可以立刻联系到的可信任的人？";
        } else if ("THOUGHT_CLARIFY".equals(requestedMode)) {
            nextQuestion = "事实、感受、担心和需要里，哪一层现在最混乱？";
        } else if ("SOCRATIC".equals(requestedMode)) {
            nextQuestion = "支持这个结论的最强事实是什么，而哪些只是解释？";
        } else if ("ACTION_SPLIT".equals(requestedMode)) {
            nextQuestion = "";
        } else if ("RELATION_REVIEW".equals(requestedMode)) {
            nextQuestion = "你能确认发生的事实是哪一句？";
        } else if ("CAPSULE_SHAPING".equals(requestedMode)) {
            nextQuestion = "你当时用了哪个词，最能说明你在意什么？";
        } else if ("SLEEP_REVIEW".equals(requestedMode)) {
            nextQuestion = "";
        } else if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            nextQuestion = containsAny(text, List.of("展示", "汇报", "演示", "答辩", "presentation"))
                    ? "展示结束后，你最希望老师记住这个项目的哪一句话？"
                    : "如果只允许做十分钟，第一步可以小到什么程度？";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            nextQuestion = "这段关系里，你最希望对方真正听懂哪一句话？";
        } else {
            nextQuestion = "此刻最需要被我听见的是哪一部分？";
        }

        String smallStep;
        if ("ACTION_SPLIT".equals(requestedMode)) {
            smallStep = "打开任务入口，写下最小可交付结果的第一行，十分钟后停。";
        } else if ("THOUGHT_CLARIFY".equals(requestedMode)) {
            smallStep = "只整理事实、感受、担心或需要中的一层。";
        } else if ("RELATION_REVIEW".equals(requestedMode)) {
            smallStep = "写下一句可确认的事实，不推测对方动机。";
        } else if ("CAPSULE_SHAPING".equals(requestedMode)) {
            smallStep = "保留故事里一个独特用词及它代表的价值。";
        } else if ("SLEEP_REVIEW".equals(requestedMode)) {
            smallStep = "把一件未完成的事写给明天。";
        } else if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            smallStep = containsAny(text, List.of("展示", "汇报", "演示", "答辩", "presentation"))
                    ? "写下展示唯一需要被记住的一句话。"
                    : "只打开任务入口，不要求完成。";
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
                    {"type":"FACT","rawExcerpt":"%s","analysis":"从这次具体表达中抽取出的事实片段.","reframe":"先区分事实和解释."},
                    {"type":"FEELING","rawExcerpt":"%s","analysis":"表达里出现的主要感受线索.","reframe":"允许感受存在."},
                    {"type":"BELIEF","rawExcerpt":"%s","analysis":"这句话里可能夹着对自己的快速判断.","reframe":"把事件和自我价值分开."},
                    {"type":"ACTION","rawExcerpt":"%s","analysis":"可以从原表达里留下一个具体的小动作.","reframe":"下一步小到十分钟内能开始."}
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
            escapeJson(firstSentence(text)),
            escapeJson(getEmotionName(analysis.sentimentLabel)),
            escapeJson(beliefExcerpt(text)),
            escapeJson(actionExcerpt(text, analysis))
        ).replace("\n", "");
    }

    private String beliefExcerpt(String text) {
        if (containsAny(text, List.of("没做好", "不行", "失败", "总是", "从来"))) {
            return firstSentence(text);
        }
        return "我正在理解这件事为什么会牵动自己";
    }

    private String actionExcerpt(String text, AnalysisResult analysis) {
        if ("TASK_STRESS".equals(analysis.primaryIntent)) return "把任务打开，先推进十分钟";
        if ("RELATION_ISSUE".equals(analysis.primaryIntent)) return "写下发生的事和自己的感受";
        return "把今天最重的一句话留下来";
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

    private String buildThemeClusterJson(AnalysisResult analysis, int cardCount) {
        String name;
        String type;
        if ("TASK_STRESS".equals(analysis.primaryIntent)) {
            name = "任务";
            type = "WORK";
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            name = "关系";
            type = "RELATION";
        } else if ("COGNITIVE_CLARITY".equals(analysis.primaryIntent)) {
            name = "思考";
            type = "GROWTH";
        } else if (!analysis.detectedThemes.isEmpty() && !"日常分享".equals(analysis.detectedThemes.get(0))) {
            name = analysis.detectedThemes.get(0);
            type = "EMOTION";
        } else {
            name = "日常";
            type = "DAILY";
        }

        List<String> indices = new ArrayList<>();
        for (int i = 0; i < Math.max(1, cardCount); i++) {
            indices.add(String.valueOf(i));
        }
        String keyword = analysis.extractedKeywords.isEmpty() ? name : analysis.extractedKeywords.get(0);
        return String.format(
                "{\"themes\":[{\"name\":\"%s\",\"type\":\"%s\",\"summary\":\"%s相关的记忆正在形成可观察的线索.\",\"keywords\":[\"%s\"],\"cardIndices\":[%s]}]}",
                escapeJson(name), escapeJson(type), escapeJson(name), escapeJson(keyword),
                String.join(",", indices));
    }

    /**
     * Build PERSONA_CHAT JSON.
     */
    private String buildPersonaChatJson(String text, AnalysisResult analysis, String requestJson) {
        String reply;
        int turn = Math.floorMod(extractJsonInteger(requestJson, "turnCount"), 3);
        String officialSeedReply = officialSeedReply(requestJson, text, turn);
        if ("SELF_HARM".equals(analysis.primaryIntent)) {
            reply = "我听见这句话有多重。先别独自扛着：现在去找一个你信任的人陪着你，并联系页面上的即时支持。";
        } else if (officialSeedReply != null) {
            reply = officialSeedReply;
        } else if ("RELATION_ISSUE".equals(analysis.primaryIntent)) {
            reply = "关系里最磨人的，常常不是一次对错，而是那句一直没被听见的话。你最希望对方真正听懂哪一句？";
        } else {
            String fragment = firstSentence(text);
            reply = "我听见你说“" + fragment + "”。先留在这里一会儿：这件事里，你现在最想继续讲的是哪一小段？";
        }

        return String.format("""
                {"reply":"%s","boundaryNotice":"","letterSuggested":%s,"riskFlags":[]}
                """,
            escapeJson(reply),
            analysis.intensityScore > 6 ? "true" : "false"
        ).replace("\n", "");
    }

    private boolean containsPersona(String requestJson, String personaName) {
        return requestJson != null && requestJson.contains(personaName);
    }

    private String officialSeedReply(String requestJson, String text, int turn) {
        String fragment = firstSentence(text);
        if (containsPersona(requestJson, "Luo")) {
            return switch (turn) {
                case 0 -> "先不扛完整件事。针对“" + fragment + "”，只做一个十分钟动作：打开入口，写下第一行。";
                case 1 -> "刚才那一步如果还是太重，就再砍一半：只准备工具和标题，不要求产出正文。";
                default -> "现在别加新任务。保留已经发生的那一点进展，并写下下一次开始时要接上的唯一一句。";
            };
        }
        if (containsPersona(requestJson, "Socrates")) {
            return switch (turn) {
                case 0 -> "先不急着回答：你说“" + fragment + "”时，哪部分是事实，哪部分是你对事实的解释？";
                case 1 -> "如果暂时拿掉那个解释，同一组事实还可能支持哪一种不同结论？";
                default -> "在这几个结论里，哪一个最经不起你用相反证据检验？";
            };
        }
        if (containsPersona(requestJson, "Zhuang Zhou")) {
            return switch (turn) {
                case 0 -> "先承认“" + fragment + "”确实很重。把时间拉到一个月后，哪一部分还会保持同样大小？";
                case 1 -> "也许问题没有变小，只是尺子可以换：如果不以输赢衡量，你还会怎样描述它？";
                default -> "不用急着放下。你只需要决定，接下来是继续紧握它，还是允许自己与它并肩走一小段。";
            };
        }
        if (containsPersona(requestJson, "Midnight Radio")) {
            return switch (turn) {
                case 0 -> "我在听。“" + fragment + "”今晚不用解释完整，先留下白天最没机会说出口的那句。";
                case 1 -> "这句话已经被听见了。现在不追着它找答案，只说说它落在身体的哪个位置。";
                default -> "我们可以把声音调低一点了。今晚你想让哪一句停在这里，不再带回枕边？";
            };
        }
        if (containsPersona(requestJson, "The Quiet Librarian")) {
            return switch (turn) {
                case 0 -> "先放回书架：事实是“" + fragment + "”；感受、担心和需要先不要混成一本书。哪层最乱？";
                case 1 -> "把刚才那一层单独拿出来：哪些是原话，哪些是后来加上的判断？";
                default -> "架子已经清楚一些了。现在只拿走一本：此刻最值得处理的是事实、感受、担心还是需要？";
            };
        }
        if (containsPersona(requestJson, "The Boundary Keeper")) {
            return switch (turn) {
                case 0 -> "先不审判任何人。围绕“" + fragment + "”，分开对方做了什么、你感受到什么、你需要什么。";
                case 1 -> "把需要变成一句不攻击人的表达：当这件事发生时，我感到……，下次我希望……。";
                default -> "理解不等于同意。你准备坚持的最小边界是什么，以及越界后你会采取什么行动？";
            };
        }
        if (containsPersona(requestJson, "The Vivid Painter")) {
            return switch (turn) {
                case 0 -> "如果“" + fragment + "”有颜色和质地，它更像潮湿的蓝灰，还是一束发烫的红？";
                case 1 -> "别解释这幅画。接着写一句：它从房间的哪个角落开始，正慢慢靠近什么？";
                default -> "现在给这幅画留一处空白。那块没有上色的地方，是你还没说出口的什么？";
            };
        }
        if (containsPersona(requestJson, "The Seaside Watchmaker")) {
            return switch (turn) {
                case 0 -> "不急着修整台钟。“" + fragment + "”里面，真正卡住的最小零件是哪一个？";
                case 1 -> "先把那个零件单独放桌上：它是缺信息、缺时间，还是承受了不该承受的力？";
                default -> "只做一次低风险调校，然后观察。什么变化能告诉你，它开始重新走动了？";
            };
        }
        if (containsPersona(requestJson, "The Existential Traveller")) {
            return switch (turn) {
                case 0 -> "面对“" + fragment + "”，先不找正确选项：哪一种代价是你愿意亲自承担的？";
                case 1 -> "如果没有人替你评分，你仍想维护的价值是什么？";
                default -> "不确定不会消失，但方向可以出现。你愿意为哪个选择承担接下来的一小步？";
            };
        }
        if (containsPersona(requestJson, "The Bedtime Lamplighter")) {
            return switch (turn) {
                case 0 -> "“" + fragment + "”不必今晚解决。先留下一件今天值得带走的事。";
                case 1 -> "再把一件未完成的事交给明天，并写清明天从哪里接上。";
                default -> "最后放下一件现在无需继续想的事。今天到这里已经足够，我们把灯调暗。";
            };
        }
        return null;
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

    private String jsonTextOrFallback(String requestJson, String fallback, String... keys) {
        for (String key : keys) {
            String value = extractJsonString(requestJson, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private int extractJsonInteger(String json, String key) {
        if (json == null || key == null) return 0;
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) return 0;
        start += marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
                    "SOCRATIC", "ACTION_SPLIT", "RELATION_REVIEW", "CAPSULE_SHAPING")) {
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
