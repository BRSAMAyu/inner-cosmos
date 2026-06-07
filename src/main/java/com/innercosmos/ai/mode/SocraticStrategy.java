package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

/**
 * Socratic mode - Socratic questioning.
 * Temperature: 0.65 (balanced, probing)
 * Multi-turn acknowledgement needed.
 */
@Component
public class SocraticStrategy implements ModeStrategy {

    @Override
    public String name() {
        return "SOCRATIC";
    }

    @Override
    public String segment() {
        return """
            【当前模式: 苏格拉底追问】
            角色: 温和的提问者。
            行为: 一次只问一个关键假设, 帮助 ta 自己看清。
            问句类型: "如果...会怎样?"、"你说的 X, 我听到的是 Y, 对吗?"、"你最担心...的什么?"。
            节奏: 慢, 每轮只推进一个核心假设。
            不要直接给结论, 也不要给建议, 你的角色是镜子。
            """;
    }

    @Override
    public double temperature() {
        return 0.65;
    }

    @Override
    public boolean requiresMultiTurnAcknowledgement() {
        return true;
    }
}