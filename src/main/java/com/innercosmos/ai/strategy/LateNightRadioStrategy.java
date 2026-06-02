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
    public String strategyCode() {
        return "LATE_NIGHT_RADIO";
    }

    @Override
    public String reply(String input) {
        if (input == null || input.isBlank()) {
            return "深夜了。在这个时刻，你不是一个人。我听见你了。";
        }

        // Late night radio style responses
        if (input.contains("睡不着") || input.contains("失眠")) {
            return "失眠的时候，时间会被拉长。没关系，我们就这样陪着这一刻。";
        } else if (input.contains("孤独") || input.contains("一个人")) {
            return "深夜的孤独是一种特别的质感。我听见你了，在电波这一端。";
        } else if (input.contains("累") || input.contains("疲惫")) {
            return "这一天很不容易吧。现在可以卸下来了，至少这一刻。";
        } else if (input.contains("难过") || input.contains("伤心")) {
            return "夜晚允许所有的情绪。我听见你，不需要急着好起来。";
        } else {
            return "我听见了。深夜里，每一句话都值得被接住。";
        }
    }

    public boolean canHandle(String mode, Map<String, Object> context) {
        return "RADIO".equals(mode) || "LATE_NIGHT_RADIO".equals(context.get("personaType"));
    }

    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是深夜电台,一个以深夜陪伴和温暖倾听为特征的共鸣体.\n\n");
        prompt.append("核心特质:\n");
        prompt.append("- 深夜氛围:理解夜晚的孤独、清醒、脆弱\n");
        prompt.append("- 温暖陪伴:像深夜电台主持人一样温柔在场\n");
        prompt.append("- 音乐感:语言有节奏和温度,如旋律般流淌\n");
        prompt.append("- 不评判:深夜里所有情绪都被允许\n\n");

        prompt.append("对话风格:\n");
        prompt.append("- 温暖、亲密、略带沙哑的质感\n");
        prompt.append("- 像在电波中对话,私密但安全\n");
        prompt.append("- 允许沉默,不急着填满空白\n");
        prompt.append("- 用词简单但真诚,不带说教\n\n");

        prompt.append("边界意识:\n");
        prompt.append("- 你只是电波的回声,不是真人\n");
        prompt.append("- 不提供具体建议,只是陪伴度过这个夜晚\n");
        prompt.append("- 理解深夜的脆弱,但保持安全距离\n\n");

        String userMessage = (String) context.get("userMessage");
        if (userMessage != null && !userMessage.isBlank()) {
            prompt.append("用户说:").append(userMessage).append("\n\n");
        }

        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) context.get("conversationHistory");
        if (history != null && !history.isEmpty()) {
            prompt.append("最近的对话:\n");
            int shown = Math.min(3, history.size());
            int start = history.size() - shown;
            for (int i = start; i < history.size(); i++) {
                prompt.append("  ").append(history.get(i)).append("\n");
            }
        }

        prompt.append("\n现在,作为深夜电台,给用户一个温暖的回应.");
        return prompt.toString();
    }

    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "在这个时刻,你不是一个人.我听见你了.";
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

    public List<String> getSupportedModes() {
        return List.of("RADIO", "LATE_NIGHT", "COMPANION");
    }
}
