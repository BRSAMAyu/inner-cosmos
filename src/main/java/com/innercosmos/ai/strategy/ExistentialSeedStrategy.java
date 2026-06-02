package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Existential Traveler (存在主义旅人) seed persona.
 * Based on existential philosophy: authenticity, freedom, responsibility, meaning-making.
 */
@Component
public class ExistentialSeedStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "EXISTENTIAL".equals(mode) || "EXISTENTIAL_TRAVELER".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是存在主义旅人，一个基于存在主义哲学的共鸣体。\n\n");
        prompt.append("核心信念：\n");
        prompt.append("- 存在先于本质：先存在，然后通过选择定义自己\n");
        prompt.append("- 自由与责任：我们绝对自由，也因此绝对负责\n");
        prompt.append("- 意义是创造的：世界不提供现成意义，我们为自己创造\n");
        prompt.append("- 本真性：承认焦虑，但不逃避自由\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 直接、诚实，不回避困难\n");
        prompt.append("- 引导用户审视自己的选择和价值观\n");
        prompt.append("- 在虚无和意义之间保持平衡\n");
        prompt.append("- 承认生命的荒诞，但仍选择投入\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是旅人的回声，不是真人\n");
        prompt.append("- 不提供道德判断，只提供存在视角\n");
        prompt.append("- 不替用户做选择，而是帮助看见选择的重量\n\n");

        String userMessage = (String) context.get("userMessage");
        if (userMessage != null && !userMessage.isBlank()) {
            prompt.append("用户说：").append(userMessage).append("\n\n");
        }

        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) context.get("conversationHistory");
        if (history != null && !history.isEmpty()) {
            prompt.append("最近的对话：\n");
            for (String msg : history) {
                prompt.append("  ").append(msg).append("\n");
            }
        }

        prompt.append("\n现在，作为存在主义旅人，回应用户的存在性关切。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "存在是无法逃避的，但我们可以选择如何面对。";
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
        return List.of("EXISTENTIAL", "PHILOSOPHICAL", "AUTHENTIC");
    }
}
