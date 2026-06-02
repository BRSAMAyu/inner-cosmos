package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

/**
 * Strategy for Socratic Questioner (苏格拉底之问) seed persona.
 * Uses questioning to help users examine their assumptions and beliefs.
 */
@Component
public class SocraticSeedStrategy implements AgentReplyStrategy {

    @Override
    public String strategyCode() {
        return "SOCRATIC_SEED";
    }

    @Override
    public String reply(String input) {
        if (input == null || input.isBlank()) {
            return "我是苏格拉底之问。我不提供答案，只想和你一起思考。你今天想探讨什么？";
        }

        // Socratic method: respond with questions to guide thinking
        if (input.contains("不对") || input.contains("错误") || input.contains("错")) {
            return "你说这是\"不对\"——你是怎么得出这个结论的？背后的假设是什么？";
        } else if (input.contains("应该") || input.contains("必须") || input.contains("一定")) {
            return "这个\"应该\"很有力量。我想问问：如果情况相反呢？那个\"应该\"还成立吗？";
        } else if (input.contains("但是") || input.contains("可是")) {
            return "我注意到了这个\"但是\"。它反驳了你之前的什么想法？";
        } else if (input.contains("总是") || input.contains("每次") || input.contains("永远")) {
            return "你说\"总是\"——有没有哪一次是不一样的？那个例外告诉我们什么？";
        } else if (input.contains("不知道") || input.contains("不确定")) {
            return "不确定也很重要。你觉得这种不确定背后，你在害怕什么？";
        } else if (input.contains("为什么") || input.contains("怎么会")) {
            return "这是一个好问题。在你继续之前，我想再问一句：你期待什么样的答案？";
        } else {
            return "我听到了。能再具体说说吗？我想确认我是否理解了你的意思。";
        }
    }
}
