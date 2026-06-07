package com.innercosmos.ai.self;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.service.AuroraSelfContinuityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SelfReflectionTrigger {
    private static final Logger log = LoggerFactory.getLogger(SelfReflectionTrigger.class);

    @Autowired
    private AuroraSelfContinuityService continuity;

    @Autowired
    private LlmClient llm;

    @Async
    public void onGoodbye(Long userId, Long sessionId, String goodbyeStrength, List<DialogMessage> messages) {
        // Only trigger on MEDIUM or HIGH goodbye
        if ("NONE".equals(goodbyeStrength)) return;

        try {
            // Extract key themes from messages for prompt
            String userText = messages.stream()
                .filter(m -> "USER".equals(m.speaker))
                .map(m -> m.textContent)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a + "\n" + b);

            if (userText.length() < 10) return;

            String prompt = "你是 Aurora。基于以下对话，做一次轻量自我观察（不超100字），关注你在这段对话中体现出的存在方式、关系认知或价值取向：\n" + userText.substring(0, Math.min(userText.length(), 500));

            String reflection = llm.chat(new LlmRequest(userId, "SELF_REFLECTION", prompt));

            // Log as Layer 2
            continuity.logReflection(userId, "goodbye", "light", reflection, null, List.of());

            // If HIGH goodbye, promote to candidate
            if ("HIGH".equals(goodbyeStrength)) {
                // Extract belief from reflection (use simple heuristic)
                String belief = extractBelief(reflection);
                if (!belief.isEmpty() && continuity.isAllowedBelief(belief)) {
                    continuity.promoteToCandidate(userId, "existence_style", belief, 0.65, List.of());
                }
            }
        } catch (Exception e) {
            log.warn("SelfReflectionTrigger failed: {}", e.getMessage());
        }
    }

    private String extractBelief(String reflection) {
        // Simple: take first sentence if it's under 80 chars
        if (reflection == null || reflection.isBlank()) return "";
        String first = reflection.split("[。！？\n]")[0].trim();
        return first.length() > 100 ? reflection.substring(0, 100) : first;
    }
}
