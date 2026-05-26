package com.innercosmos.ai.strategy;

import com.innercosmos.ai.agent.AuroraAgent;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class AuroraCompanionStrategy implements AgentReplyStrategy {
    private final AuroraAgent auroraAgent;

    public AuroraCompanionStrategy(AuroraAgent auroraAgent) {
        this.auroraAgent = auroraAgent;
    }

    @Override
    public String strategyCode() {
        return "AURORA_COMPANION";
    }

    @Override
    public String reply(String input) {
        return auroraAgent.reply(null, input, Collections.emptyList(), "");
    }
}
