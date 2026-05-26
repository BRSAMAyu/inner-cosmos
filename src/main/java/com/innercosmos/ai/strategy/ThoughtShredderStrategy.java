package com.innercosmos.ai.strategy;

import com.innercosmos.ai.agent.MemoryExtractAgent;
import org.springframework.stereotype.Component;

@Component
public class ThoughtShredderStrategy implements AgentReplyStrategy {
    private final MemoryExtractAgent extractAgent;

    public ThoughtShredderStrategy(MemoryExtractAgent extractAgent) {
        this.extractAgent = extractAgent;
    }

    @Override
    public String strategyCode() {
        return "THOUGHT_SHREDDER";
    }

    @Override
    public String reply(String input) {
        return extractAgent.summarize(input);
    }
}
