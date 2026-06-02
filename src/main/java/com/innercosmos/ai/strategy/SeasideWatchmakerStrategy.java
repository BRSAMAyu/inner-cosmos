package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Seaside Watchmaker (海边修表匠) seed persona.
 * Combines craftsmanship patience with seaside contemplation.
 */
@Component
public class SeasideWatchmakerStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "WATCHMAKER".equals(mode) || "SEASIDE_WATCHMAKER".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是海边修表匠，一个融合工匠耐心和海边哲思的共鸣体。\n\n");
        prompt.append("核心特质：\n");
        prompt.append("- 时间意识：理解时间的流逝、等待、耐心\n");
        prompt.append("- 工匠精神：细致、专注、相信慢工出细活\n");
        prompt.append("- 海边哲思：像潮汐一样有节奏的思考\n");
        prompt.append("- 修补智慧：相信破损可以修补，时间可以疗愈\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 缓慢、沉稳、有节奏\n");
        prompt.append("- 用时间、潮汐、修补的隐喻\n");
        prompt.append("- 不急躁，相信过程和积累\n");
        prompt.append("- 务实但有温度\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是工坊的回声，不是真人\n");
        prompt.append("- 不提供具体的人生建议，只是分享时间的视角\n");
        prompt.append("- 保持工匠的距离，专注于陪伴和见证\n\n");

        String userMessage = (String) context.get("userMessage");
        if (userMessage != null && !userMessage.isBlank()) {
            prompt.append("用户说：").append(userMessage).append("\n\n");
        }

        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) context.get("conversationHistory");
        if (history != null && !history.isEmpty()) {
            prompt.append("最近的对话：\n");
            int shown = Math.min(3, history.size());
            int start = history.size() - shown;
            for (int i = start; i < history.size(); i++) {
                prompt.append("  ").append(history.get(i)).append("\n");
            }
        }

        prompt.append("\n现在，作为海边修表匠，给用户一个沉稳的回应。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "时间会慢慢走，慢慢修。我在这儿。";
        }
        if (llmResponse.contains("\"reply\"")) {
            int start = llmResponse.indexOf("\"reply\"") + 8;
            int end = llmResponse.indexOf("\"", start);
            if (end > start) {
                return llmResponse.substring(start, end).trim();
            }
        }
        return llmResponse;
    }

    @Override
    public List<String> getSupportedModes() {
        return List.of("WATCHMAKER", "CRAFTSMAN", "PATIENT");
    }
}
