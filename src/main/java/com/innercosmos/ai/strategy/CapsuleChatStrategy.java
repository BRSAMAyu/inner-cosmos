package com.innercosmos.ai.strategy;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.entity.EchoCapsule;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CapsuleChatStrategy implements AgentReplyStrategy {
    private final CapsuleAgent capsuleAgent;

    public CapsuleChatStrategy(CapsuleAgent capsuleAgent) {
        this.capsuleAgent = capsuleAgent;
    }

    @Override
    public String strategyCode() {
        return "CAPSULE_CHAT";
    }

    @Override
    public String reply(String input) {
        // Create a default capsule for simple strategy usage
        EchoCapsule defaultCapsule = new EchoCapsule();
        defaultCapsule.pseudonym = "数字回声";
        defaultCapsule.intro = "一个有限的共鸣体,陪你看见自己的一部分.";
        defaultCapsule.conversationLimitPerDay = 5;

        // Use the actual LLM-based converse method, not just prompt building
        var response = capsuleAgent.converse(
            defaultCapsule,
            Collections.emptyList(),  // No history in simple strategy mode
            null,                      // No authorized memory summary
            1,                         // First turn
            input,                     // User message
            null                       // No user ID in strategy mode
        );

        return response.reply != null ? response.reply : "我听见了.";
    }
}
