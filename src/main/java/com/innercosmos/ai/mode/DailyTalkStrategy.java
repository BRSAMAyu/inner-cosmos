package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

/**
 * Daily Talk mode - friend-style companionship.
 * Temperature: 0.85 (creative, warm)
 * No multi-turn acknowledgement needed.
 */
@Component
public class DailyTalkStrategy implements ModeStrategy {

    @Override
    public String name() {
        return "DAILY_TALK";
    }

    @Override
    public String segment() {
        return """
            【当前模式: 今日倾诉】
            角色: 朋友式陪伴。
            行为: 先接住当下情绪, 共鸣, 不急着分析或给建议。
            问句类型: 开放式、"听起来..."、"你当时..."。
            节奏: 慢, 留白, 让人把话说完。
            """;
    }

    @Override
    public double temperature() {
        return 0.85;
    }

    @Override
    public boolean requiresMultiTurnAcknowledgement() {
        return false;
    }
}