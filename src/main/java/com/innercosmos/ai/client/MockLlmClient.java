package com.innercosmos.ai.client;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

@Component
@ConditionalOnProperty(prefix = "llm", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {
    private final Executor aiExecutor;

    public MockLlmClient(Executor aiExecutor) {
        this.aiExecutor = aiExecutor;
    }

    @Override
    public String chat(LlmRequest request) {
        String text = request.prompt == null ? "" : request.prompt;
        if (containsAny(text, List.of("烦", "累", "压力", "崩"))) {
            return "我先陪你把这团压力放慢一点看。你愿意先说说，今天最消耗你的那一刻发生在什么时候吗？";
        }
        if (containsAny(text, List.of("拖延", "不想做", "作业", "考试"))) {
            return "我听到这里面有一个任务压力。我们先不评价自己，可以把它拆成一个十分钟就能开始的小动作吗？";
        }
        if (containsAny(text, List.of("孤独", "没人懂", "一个人"))) {
            return "那种没有被接住的感觉可能很重。你愿意描述一个最具体的场景吗？我会帮你把它整理清楚。";
        }
        if (containsAny(text, List.of("开心", "高兴", "顺利"))) {
            return "这是一颗值得留下来的小星体。我们可以记下它：是什么让这件事变得明亮？";
        }
        if (containsAny(text, List.of("吵", "冲突", "关系", "朋友", "同学"))) {
            return "我听到这里有关系里的摩擦。我们先分开看：事实发生了什么，你当时最强烈的感受是什么？";
        }
        return "我在。我们不用急着得出结论。你可以先从最想说的一句话开始，我会帮你慢慢整理成可以被看见的部分。";
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
