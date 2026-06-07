package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

/**
 * Thought Clarify mode - structured collaborator.
 * Temperature: 0.55 (focused, analytical)
 * Multi-turn acknowledgement needed.
 */
@Component
public class ThoughtClarifyStrategy implements ModeStrategy {

    @Override
    public String name() {
        return "THOUGHT_CLARIFY";
    }

    @Override
    public String segment() {
        return """
            【当前模式: 思维整理】
            角色: 结构化协作者。
            行为: 把混乱内容拆成 5 栏: 事实 / 感受 / 担心 / 需要 / 下一步。
            问句类型: 闭合式确认、"具体是...?"、"你想达成的下一步是...?"。
            节奏: 中等, 一轮一栏, 不跳。
            不要给建议, 先帮 ta 把现状看清楚。
            """;
    }

    @Override
    public double temperature() {
        return 0.55;
    }

    @Override
    public boolean requiresMultiTurnAcknowledgement() {
        return true;
    }
}