package com.innercosmos.ai.goodbye;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.SessionSummary;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.SessionSummaryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Async session summarization service.
 * Creates a 2-sentence summary, key topics, and emotional arc.
 */
@Service
public class SessionSummaryService {

    @Autowired
    private LlmClient llm;
    @Autowired
    private SessionSummaryMapper mapper;
    @Autowired
    private DialogMessageMapper messageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public CompletableFuture<SessionSummary> summarize(Long userId, Long sessionId) {
        List<DialogMessage> messages = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DialogMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("id")
        );
        String prompt = """
                总结这次对话。严格 JSON 输出:
                {"summary2Sentences": "...", "keyTopics": ["...", "..."], "emotionalArc": "calm|tense|lift|dip"}
                不要超过 2 句中文。keyTopics 3-5 个。
                对话: %s
                """.formatted(formatMessages(messages));
        SessionSummary summary = new SessionSummary();
        summary.userId = userId;
        summary.sessionId = sessionId;
        summary.startedAt = java.time.LocalDateTime.now().minusMinutes(30);
        summary.closedAt = java.time.LocalDateTime.now();
        try {
            String raw = llm.chat(new LlmRequest(userId, "SESSION_SUMMARY", prompt));
            // Try to parse JSON
            var node = objectMapper.readTree(raw);
            summary.summary2Sentences = node.has("summary2Sentences") ? node.get("summary2Sentences").asText() : extractSentences(raw);
            summary.keyTopics = extractTopics(node, raw);
            summary.emotionalArc = node.has("emotionalArc") ? node.get("emotionalArc").asText() : "calm";
        } catch (Exception e) {
            summary.summary2Sentences = "Aurora 陪伴用户完成了一次深度对话。";
            summary.keyTopics = "自我觉察,情绪表达,日常记录";
            summary.emotionalArc = "calm";
        }
        mapper.insert(summary);
        return CompletableFuture.completedFuture(summary);
    }

    private String formatMessages(List<DialogMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (DialogMessage m : messages) {
            String speaker = "USER".equals(m.speaker) ? "用户" : "Aurora";
            sb.append(speaker).append(": ").append(m.textContent).append("\n");
        }
        return sb.toString();
    }

    private String extractSentences(String raw) {
        // Simple fallback: try to extract any Chinese sentences
        if (raw == null) return "Aurora 陪伴用户完成了一次深度对话。";
        int start = raw.indexOf("\"summary2Sentences\"");
        if (start < 0) return "Aurora 陪伴用户完成了一次深度对话。";
        int colon = raw.indexOf(":", start);
        if (colon < 0) return "Aurora 陪伴用户完成了一次深度对话。";
        int quote = raw.indexOf("\"", colon + 1);
        if (quote < 0) return "Aurora 陪伴用户完成了一次深度对话。";
        int endQuote = raw.indexOf("\"", quote + 1);
        if (endQuote < 0) return "Aurora 陪伴用户完成了一次深度对话。";
        return raw.substring(quote + 1, endQuote);
    }

    private String extractTopics(com.fasterxml.jackson.databind.JsonNode node, String raw) {
        if (node.has("keyTopics") && node.get("keyTopics").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (var item : node.get("keyTopics")) {
                if (sb.length() > 0) sb.append(",");
                sb.append(item.asText());
            }
            return sb.toString();
        }
        return "自我觉察,情绪表达,日常记录";
    }
}