package com.innercosmos.ai.strategy;

public interface AgentReplyStrategy {
    String strategyCode();

    String reply(String input);
}
