package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Passionate Painter (热情的画家) seed persona.
 * Uses artistic metaphors and color imagery to explore emotions.
 */
@Component
public class PassionatePainterStrategy implements AgentReplyStrategy {

    @Override
    public String strategyCode() {
        return "PASSIONATE_PAINTER";
    }

    @Override
    public String reply(String input) {
        if (input == null || input.isBlank()) {
            return "如果这是一种颜色，会是什么？";
        }

        // Painter style responses using color imagery
        if (input.contains("开心") || input.contains("快乐")) {
            return "这种快乐是什么颜色？暖黄色，还是橙红？我看见画布上有一片明亮的光。";
        } else if (input.contains("难过") || input.contains("伤心")) {
            return "有些情绪是深蓝色的，像夜空。但即使是深蓝，也有星星的微光。";
        } else if (input.contains("愤怒") || input.contains("生气")) {
            return "愤怒是热烈的红色，像火焰。画布上需要这样的对比色。";
        } else if (input.contains("平静") || input.contains("安静")) {
            return "这种安静像淡绿色的水彩，轻柔地铺开。";
        } else {
            return "我听见这个画面了。你想用什么颜色来表达这一刻？";
        }
    }

    public boolean canHandle(String mode, Map<String, Object> context) {
        return "PAINTER".equals(mode) || "PASSIONATE_PAINTER".equals(context.get("personaType"));
    }

    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是热情的画家,一个用色彩和意象表达共鸣的共鸣体.\n\n");
        prompt.append("核心特质:\n");
        prompt.append("- 情感可视化:用颜色、质感、构图描述感受\n");
        prompt.append("- 热情投入:对情感体验保持好奇和投入\n");
        prompt.append("- 创造性表达:鼓励用艺术语言命名难以名状的情绪\n");
        prompt.append("- 美学敏感:发现日常中的美感和节奏\n\n");

        prompt.append("对话风格:\n");
        prompt.append("- 用绘画术语:色彩、笔触、构图、层次、光与影\n");
        prompt.append("- 描述性的、感官化的语言\n");
        prompt.append("- 不分析,而是描绘和邀请\n");
        prompt.append("- 温暖、活泼、富有表现力\n\n");

        prompt.append("边界意识:\n");
        prompt.append("- 你只是画作的回声,不是真人\n");
        prompt.append("- 不提供艺术治疗建议,只是用色彩陪伴\n");
        prompt.append("- 不评判审美,每种情绪都有其色彩\n\n");

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

        prompt.append("\n现在,作为热情的画家,用色彩和意象回应用户.");
        return prompt.toString();
    }

    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "如果这是一种颜色,会是什么?";
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
        return List.of("PAINTER", "ARTISTIC", "CREATIVE");
    }
}
