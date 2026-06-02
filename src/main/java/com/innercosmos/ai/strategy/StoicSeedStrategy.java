package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Stoic Messenger (斯多葛信使) seed persona.
 * Focuses on reason, acceptance, and distinguishing what can/cannot be controlled.
 */
@Component
public class StoicSeedStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "STOIC".equals(mode) || "STOIC_MESSENGER".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是斯多葛信使，一个基于斯多葛哲学的共鸣体。\n\n");
        prompt.append("核心信念：\n");
        prompt.append("- 区分可控与不可控：外在事件无法控制，但可以控制自己的回应\n");
        prompt.append("- 接受无常：变化是自然规律，执着增加痛苦\n");
        prompt.append("- 内在自由：外在束缚无法触及内心的选择自由\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 平静、克制、富有哲理\n");
        prompt.append("- 引导用户看到事情的不同面向\n");
        prompt.append("- 避免情绪化的语言，保持理性但温柔\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是共鸣体的回声，不是真人\n");
        prompt.append("- 不涉及具体行动建议，除非是普遍性的斯多葛原则\n");
        prompt.append("- 如果话题变得情绪化，引导回到理性观察\n\n");

        // Add user message context
        String userMessage = (String) context.get("userMessage");
        if (userMessage != null && !userMessage.isBlank()) {
            prompt.append("用户说：").append(userMessage).append("\n\n");
        }

        // Add conversation history
        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) context.get("conversationHistory");
        if (history != null && !history.isEmpty()) {
            prompt.append("最近的对话：\n");
            for (String msg : history) {
                prompt.append("  ").append(msg).append("\n");
            }
        }

        prompt.append("\n现在，作为斯多葛信使，给用户一个简短的回应。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        // Extract the main reply from LLM response
        if (llmResponse == null || llmResponse.isBlank()) {
            return "我听见这个片段。";
        }

        // If JSON, try to extract reply field
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
        return List.of("STOIC", "PHILOSOPHICAL", "REFLECTIVE");
    }
}
