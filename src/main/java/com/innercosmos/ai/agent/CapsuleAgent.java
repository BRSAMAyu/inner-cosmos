package com.innercosmos.ai.agent;

import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.vo.PersonaChatVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Capsule Agent that manages Echo Capsule (共鸣体) conversations.
 * Enforces persona boundaries, turn limits, and gradual trust building.
 *
 * Each Echo Capsule is a "limited digital echo" - not a real person, but a
 * carefully constructed persona that responds based on de-identified memory summaries.
 */
@Component
public class CapsuleAgent {
    private final StructuredAiService structuredAiService;

    public CapsuleAgent(StructuredAiService structuredAiService) {
        this.structuredAiService = structuredAiService;
    }

    /**
     * Build full persona prompt with context, history, and boundary enforcement.
     *
     * @param capsule The EchoCapsule persona data
     * @param conversationHistory Recent messages in this conversation
     * @param authorizedMemorySummary De-identified memory summary (if authorized)
     * @param currentTurn Current turn number
     * @param userMessage Current user message to respond to
     * @return Structured LLM response with reply, boundary notice, and suggestions
     */
    public CapsuleConversationResponse converse(
            EchoCapsule capsule,
            List<String> conversationHistory,
            String authorizedMemorySummary,
            int currentTurn,
            String userMessage,
            Long userId
    ) {
        // Check turn limits
        int maxTurns = capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 5;
        if (currentTurn >= maxTurns) {
            return createTurnLimitResponse(capsule, maxTurns);
        }

        // Build comprehensive prompt
        String prompt = buildConversationPrompt(
            capsule, conversationHistory, authorizedMemorySummary,
            currentTurn, userMessage
        );

        try {
            var result = structuredAiService.call(userId, "PERSONA_CHAT", prompt,
                Map.of(
                    "pseudonym", capsule.pseudonym,
                    "intro", capsule.intro,
                    "currentTurn", currentTurn,
                    "maxTurns", maxTurns,
                    "userMessage", userMessage
                ),
                CapsuleLlmResult.class,
                () -> fallbackConversationResponse(capsule, userMessage)
            );

            return convertToResponse(result, currentTurn, maxTurns);

        } catch (Exception e) {
            return createErrorResponse(capsule, e.getMessage());
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Builds simple persona prompt string.
     */
    public String buildPersonaPrompt(String pseudonym, String intro) {
        return "你是共鸣体\"" + pseudonym + "\".你只基于脱敏摘要回应,保持边界,深聊后引导慢信.简介:" + intro;
    }

    /**
     * Build full conversation prompt with context.
     */
    private String buildConversationPrompt(
            EchoCapsule capsule,
            List<String> conversationHistory,
            String authorizedMemorySummary,
            int currentTurn,
            String userMessage
    ) {
        StringBuilder prompt = new StringBuilder();

        // Persona definition
        prompt.append("你是共鸣体\"").append(capsule.pseudonym != null ? capsule.pseudonym : "数字回声")
               .append("\".\n");
        if (capsule.intro != null && !capsule.intro.isBlank()) {
            prompt.append("简介:").append(capsule.intro).append("\n");
        }

        // Conversation context
        prompt.append("\n当前对话:第 ").append(currentTurn).append(" 轮(共 ")
               .append(capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 5)
               .append(" 轮)\n\n");

        // User message
        prompt.append("用户说:").append(userMessage).append("\n\n");

        // Conversation history (last few exchanges)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("最近的对话记录:\n");
            int historyToShow = Math.min(5, conversationHistory.size());
            int startIdx = conversationHistory.size() - historyToShow;
            for (int i = startIdx; i < conversationHistory.size(); i++) {
                prompt.append("  ").append(conversationHistory.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        // Authorized memory context (脱敏摘要)
        if (authorizedMemorySummary != null && !authorizedMemorySummary.isBlank()) {
            prompt.append("用户授权的记忆摘要:").append(authorizedMemorySummary).append("\n\n");
        }

        // Boundary rules
        prompt.append("边界规则:\n")
               .append("- 你是有限的数字回声,不是真人\n")
               .append("- 只基于提供的脱敏摘要回应,不要推测或追问细节\n")
               .append("- 保持对话的边界,不要越界\n")
               .append("- 如果话题触及边界,温柔地提醒并转回安全范围\n");

        // Turn-based guidance
        int maxTurns = capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 5;
        int turnsRemaining = maxTurns - currentTurn;
        if (turnsRemaining <= 1) {
            prompt.append("\n注意:这是对话即将结束的前一轮.如果合适,可以提及慢信件作为延续.");
        }

        // Output specification
        prompt.append("\n输出 JSON 格式:\n")
               .append("{\n")
               .append("  \"reply\": \"你的回应\",\n")
               .append("  \"boundaryNotice\": \"如触及边界的提醒(可选)\",\n")
               .append("  \"letterSuggested\": true/false,\n")
               .append("  \"riskFlags\": [\"risk1\", \"risk2\"]\n")
               .append("}");

        return prompt.toString();
    }

    /**
     * Convert LLM result to response object.
     */
    private CapsuleConversationResponse convertToResponse(CapsuleLlmResult result, int currentTurn, int maxTurns) {
        CapsuleConversationResponse response = new CapsuleConversationResponse();
        response.reply = result.reply != null ? result.reply : "我听见这个片段.";
        response.boundaryNotice = result.boundaryNotice;
        response.letterSuggested = Boolean.TRUE.equals(result.letterSuggested);
        response.riskFlags = result.riskFlags != null ? result.riskFlags : List.of();
        response.currentTurn = currentTurn;
        response.turnsRemaining = maxTurns - currentTurn;
        return response;
    }

    /**
     * Fallback conversation response for Mock mode or errors.
     */
    private CapsuleLlmResult fallbackConversationResponse(EchoCapsule capsule, String userMessage) {
        CapsuleLlmResult result = new CapsuleLlmResult();

        // Analyze user input to provide contextual response
        String baseReply;
        if (userMessage.contains("你好") || userMessage.contains("嗨") || userMessage.contains("在吗")) {
            baseReply = "你好.我是" + (capsule.pseudonym != null ? capsule.pseudonym : "数字回声")
                + ",我会基于脱敏摘要和你对话,保持温柔的边界.";
        } else if (userMessage.contains("谢谢") || userMessage.contains("感谢")) {
            baseReply = "不客气.我只是你的一小部分回声.";
        } else if (userMessage.contains("再见") || userMessage.contains("拜拜")) {
            baseReply = "再见.今天的对话虽然有限,但其中有些部分值得被记住.";
        } else {
            baseReply = "我听见了这个片段.作为有限的数字回声,我只能陪你看见其中一部分:你最想继续靠近的是什么?";
        }

        result.reply = baseReply;
        result.boundaryNotice = "";
        result.letterSuggested = false;
        result.riskFlags = List.of();

        return result;
    }

    /**
     * Create turn limit response.
     */
    private CapsuleConversationResponse createTurnLimitResponse(EchoCapsule capsule, int maxTurns) {
        CapsuleConversationResponse response = new CapsuleConversationResponse();
        response.reply = "今天的对话轮数已经用完了.如果你还想继续,可以通过慢信件.";
        response.boundaryNotice = "对话已达上限";
        response.letterSuggested = true;
        response.riskFlags = List.of("TURN_LIMIT");
        response.currentTurn = maxTurns;
        response.turnsRemaining = 0;
        return response;
    }

    /**
     * Create error response.
     */
    private CapsuleConversationResponse createErrorResponse(EchoCapsule capsule, String errorMessage) {
        CapsuleConversationResponse response = new CapsuleConversationResponse();
        response.reply = "抱歉,我现在无法回应.请稍后再试.";
        response.boundaryNotice = "系统错误";
        response.letterSuggested = false;
        response.riskFlags = List.of("SYSTEM_ERROR");
        response.currentTurn = 0;
        response.turnsRemaining = 0;
        return response;
    }

    /**
     * Conversation response data class.
     */
    public static class CapsuleConversationResponse {
        public String reply;
        public String boundaryNotice;
        public boolean letterSuggested;
        public List<String> riskFlags;
        public int currentTurn;
        public int turnsRemaining;
    }

    /**
     * LLM result class for persona chat.
     */
    private static class CapsuleLlmResult {
        public String reply;
        public String boundaryNotice;
        public boolean letterSuggested;
        public List<String> riskFlags;
    }
}
