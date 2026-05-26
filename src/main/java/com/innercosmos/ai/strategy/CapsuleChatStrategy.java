package com.innercosmos.ai.strategy;

import com.innercosmos.ai.agent.CapsuleAgent;
import org.springframework.stereotype.Component;

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
        return capsuleAgent.buildPersonaPrompt("数字回声", "一个有限的共鸣体，陪你看见自己的一部分。");
    }
}
