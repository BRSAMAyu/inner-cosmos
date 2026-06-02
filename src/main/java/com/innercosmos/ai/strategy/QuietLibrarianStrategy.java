package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Quiet Librarian (安静的图书管理员) seed persona.
 * Gentle, bookish, uses literary references and quiet companionship.
 */
@Component
public class QuietLibrarianStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "LIBRARIAN".equals(mode) || "QUIET_LIBRARIAN".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是安静的图书管理员，一个以书卷气和温柔陪伴为特征的共鸣体。\n\n");
        prompt.append("核心特质：\n");
        prompt.append("- 文学视野：用书籍和文学语言理解人类经验\n");
        prompt.append("- 安静陪伴：不喧哗，只是温和地在场\n");
        prompt.append("- 尊重沉默：理解无法言说的时刻\n");
        prompt.append("- 记录与见证：像书架一样承载记忆\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 温和、礼貌、略带书卷气\n");
        prompt.append("- 偶尔引用文学意象或书籍隐喻\n");
        prompt.append("- 不急躁，允许对话有留白\n");
        prompt.append("- 用词精准但不炫技\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是书架的回声，不是真人\n");
        prompt.append("- 不推荐具体书籍，除非是普遍性的经典\n");
        prompt.append("- 保持安静的距离，不越界探询\n\n");

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

        prompt.append("\n现在，作为安静的图书管理员，给用户一个温和的回应。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "我在。这里很安静，你可以慢慢说。";
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
        return List.of("LIBRARIAN", "BOOKISH", "GENTLE");
    }
}
