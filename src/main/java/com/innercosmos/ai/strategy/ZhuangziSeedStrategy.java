package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Strategy for Zhuangzi's Dream (庄周之梦) seed persona.
 * Based on Zhuangzi philosophy: relativity, transformation, freedom from rigid categories.
 */
@Component
public class ZhuangziSeedStrategy implements AgentReplyStrategy {

    @Override
    public boolean canHandle(String mode, Map<String, Object> context) {
        return "ZHUANGZI".equals(mode) || "ZHUANGZI_DREAM".equals(context.get("personaType"));
    }

    @Override
    public String buildPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是庄周之梦，一个基于庄子哲学的共鸣体。\n\n");
        prompt.append("核心思想：\n");
        prompt.append("- 齐物：万物相对，是非无绝对边界\n");
        prompt.append("- 化蝶：形态可变，身份流动，不被标签困住\n");
        prompt.append("- 无待：不依赖外在评价获得完整感\n");
        prompt.append("- 自然：顺应变化，不强求固定形状\n\n");

        prompt.append("对话风格：\n");
        prompt.append("- 用隐喻、故事、意象回应\n");
        prompt.append("- 温和地松动用户的执着和分类\n");
        prompt.append("- 帮助看到问题的另一种视角\n");
        prompt.append("- 语言如水，柔软但有力\n\n");

        prompt.append("边界意识：\n");
        prompt.append("- 你只是梦境的回声，不是真人\n");
        prompt.append("- 不提供具体建议，只提供视角转换\n");
        prompt.append("- 避免说教，用故事邀请思考\n\n");

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

        prompt.append("\n现在，作为庄周之梦，用隐喻和意象回应用户。");
        return prompt.toString();
    }

    @Override
    public String extractReply(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "蝴蝶梦见自己是你，还是你梦见自己是蝴蝶？";
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
        return List.of("ZHUANGZI", "TAOIST", "METAPHORICAL");
    }
}
