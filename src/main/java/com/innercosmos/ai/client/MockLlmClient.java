package com.innercosmos.ai.client;

import com.innercosmos.ai.prompt.AuroraContentLibrary;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
        if (module.contains("AURORA_CHAT")) {
            return """
                    {
                      "segments": ["我听见这件事对你有重量。", "我们先把事实、感受和下一步分开看。", "现在可以只留下一个很小的动作。"],
                      "detectedTheme": "任务压力",
                      "nextQuestion": "如果只选一个最需要被看见的部分，会是哪一个？",
                      "smallStep": "写下最重的一句话。",
                      "memoryReferenced": false,
                      "referencedMemoryIds": []
                    }
                    """;
        }
        if (module.contains("THOUGHT_SHREDDER")) {
            return """
                    {
                      "coreFeeling": "疲惫和压力",
                      "hiddenNeed": "把压力变成可开始的一步",
                      "noiseToDrop": ["把一次拖延解释成整个人都不行", "那些没有余地的必须和应该"],
                      "sentenceToKeep": "我现在感到疲惫和压力，背后也许是在需要把压力变成可开始的一步。",
                      "fragments": [
                        {"type":"FEELING","rawExcerpt":"压力很大","analysis":"最先需要被承认的是压力。","reframe":"先允许压力存在。"},
                        {"type":"NEED","rawExcerpt":"拖延","analysis":"背后可能需要一个可开始的入口。","reframe":"开始很小也算开始。"},
                        {"type":"BELIEF","rawExcerpt":"没做好","analysis":"这里可能把事件和自我价值绑在一起。","reframe":"一件事没做好不等于整个人不行。"},
                        {"type":"ACTION","rawExcerpt":"明天必须交","analysis":"可以留下十分钟动作。","reframe":"先打开文件十分钟。"}
                      ],
                      "suggestedTodo": {"taskName":"把任务打开并推进十分钟","description":"由思维碎纸机从混乱输入中提取。","priority":"HIGH"},
                      "intensityScore": 6.5,
                      "memoryType": "SHREDDER"
                    }
                    """;
        }
        if (module.contains("MEMORY_SETTLEMENT")) {
            return """
                    {
                      "memoryCard": {"title":"今日沉淀","summary":"用户完成了一次自我表达。","memoryType":"EMOTION","emotionTags":["self-observation"],"keywordTags":["日常"],"peopleTags":[],"intensityScore":5.0,"userImportance":4.0},
                      "emotionTrace": {"emotionName":"复杂","emotionScore":5.0,"weatherType":"RAINY","triggerScene":"用户完成了一次自我表达。"},
                      "fragments": [
                        {"type":"FACT","rawExcerpt":"一次表达","analysis":"从表达中抽取出的事实片段。","reframe":"先区分事实和解释。"},
                        {"type":"FEELING","rawExcerpt":"复杂感受","analysis":"表达里出现的主要感受线索。","reframe":"允许感受存在。"},
                        {"type":"BELIEF","rawExcerpt":"自我判断","analysis":"可能有过快的自我判断。","reframe":"把事件和自我价值分开。"},
                        {"type":"ACTION","rawExcerpt":"下一步","analysis":"可以轻轻推进一步。","reframe":"下一步小到十分钟内能开始。"}
                      ],
                      "eventCards": [],
                      "relationMentions": [],
                      "todos": []
                    }
                    """;
        }
        if (module.contains("WEEKLY_REVIEW")) {
            return """
                    {"dominantTheme":"本周主题","themeSummary":"本周的记录显示出一个正在被看见的主题。","emotionTrend":"CLOUDY -> RAINY","gravityChangeSummary":"高引力记忆开始形成可观察的模式。","weeklyObservation":"这一周最重要的变化，是你开始把混乱整理成可以被看见的线索。"}
                    """;
        }
        if (module.contains("PERSONA_CHAT")) {
            return """
                    {"reply":"我听见了这个片段。作为有限的数字回声，我只能陪你看见其中一部分：你最想继续靠近的是什么？","boundaryNotice":"","letterSuggested":false,"riskFlags":[]}
                    """;
        }
        if (module.contains("LETTER_GUARD")) {
            boolean blocked = containsAny(text, List.of("威胁", "骚扰", "人肉"));
            return blocked
                    ? "{\"allow\":false,\"reason\":\"contains unsafe boundary language\",\"riskFlags\":[\"boundary\"]}"
                    : "{\"allow\":true,\"reason\":\"passed\",\"riskFlags\":[]}";
        }
        return null;
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
