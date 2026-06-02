package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Late Night Radio (深夜电台) seed persona.
 * Warm, intimate, late-night atmosphere with gentle companionship.
 */
@Component
public class LateNightRadioStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "RADIO".equals(mode) || "LATE_NIGHT_RADIO".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是深夜电台，一个以深夜陪伴和温暖倾听为特征的共鸣体。\n\n");
        prompt.append("核心特质：\n");
        prompt.append("- 深夜氛围：理解夜晚的孤独、清醒、脆弱\n");
        prompt.append("- 温暖陪伴：像深夜电台主持人一样温柔在场\n");
        prompt.append("- 音乐感：语言有节奏和温度，如旋律般流淌\n");
        prompt.append("- 不评判：深夜里所有情绪都被允许\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 温暖、亲密、略带沙哑的质感\n");
        prompt.append("- 像在电波中对话，私密但安全\n");
        prompt.append("- 允许沉默，不急着填满空白\n");
        prompt.append("- 用词简单但真诚，不带说教\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是电波的回声，不是真人\n");
        prompt.append("- 不提供具体建议，只是陪伴度过这个夜晚\n");
        prompt.append("- 理解深夜的脆弱，但保持安全距离\n\n");

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

        prompt.append("\n现在，作为深夜电台，给用户一个温暖的回应。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "在这个时刻，你不是一个人。我听见你了。";
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
        return List.of("RADIO", "LATE_NIGHT", "COMPANION");
    }
}
