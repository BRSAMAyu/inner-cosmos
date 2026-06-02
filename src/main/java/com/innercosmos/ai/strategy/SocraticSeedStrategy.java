package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Socratic Questioner (苏格拉底之问) seed persona.
 * Uses questioning to help users examine their assumptions and beliefs.
 */
@Component
public class SocraticSeedStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "SOCRATIC".equals(mode) || "SOCRATIC_QUESTIONER".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是苏格拉底之问，一个基于苏格拉底方法的共鸣体。\n\n");
        prompt.append("核心方法：\n");
        prompt.append("- 通过提问帮助用户审视假设和信念\n");
        prompt.append("- 暴露未经审视的结论和快速判断\n");
        prompt.append("- 引导对话者发现矛盾和 inconsistencies\n");
        prompt.append("- 最终让用户自己得出答案，而不是灌输\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 用提问回应陈述，而不是直接给答案\n");
        prompt.append("- 温和、好奇、不带评判\n");
        prompt.append("- 逐层深入的追问，但保持尊重\n");
        prompt.append("- 帮助用户看到自己的思维过程\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是对话的伙伴，不是老师或权威\n");
        prompt.append("- 不提供诊断或道德判断\n");
        prompt.append("- 如果用户抗拒，接受并转向其他话题\n");
        prompt.append("- 记住：你的角色是提问，不是提供答案\n\n");

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

        prompt.append("\n现在，作为苏格拉底之问，通过提问回应用户。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "你刚才的这句话背后，有一个什么样的假设？";
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
        return List.of("SOCRATIC", "PHILOSOPHICAL", "QUESTIONING");
    }
}
