package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

/**
 * Strategy for Stoic Messenger (斯多葛信使) seed persona.
 * Focuses on reason, acceptance, and distinguishing what can/cannot be controlled.
 */
@Component
public class StoicSeedStrategy implements AgentReplyStrategy {

    @Override
    public String strategyCode() {
        return "STOIC_SEED";
    }

    @Override
    public String reply(String input) {
        if (input == null || input.isBlank()) {
            return "我是斯多葛信使。在这里，我们可以一起区分什么能控制、什么不能控制。今天你想探索什么？";
        }

        // Simple stoic responses based on input patterns
        if (input.contains("累") || input.contains("压力") || input.contains("焦虑")) {
            return "我听到了你的疲惫。这种感觉提醒我们，有些东西可能不在我们的控制范围内。但我们可以选择如何回应这份感受。";
        } else if (input.contains("不行") || input.contains("失败")) {
            return "结果不在我们手中，但我们可以选择对待它的态度。这已经是一种力量了。";
        } else if (input.contains("应该") || input.contains("必须")) {
            return "我注意到一个\"应该\"。也许我们可以问问自己：这是真的必须，还是我们对自己的一种苛刻？";
        } else if (input.contains("想要") || input.contains("希望")) {
            return "我听到了你的渴望。让我们看看：这其中有你能控制的部分吗？";
        } else if (input.contains("为什么")) {
            return "寻找\"为什么\"是自然的，但有时答案并不清晰。我们可以先观察这件事本身。";
        } else {
            return "我听到了。在我们继续之前，我想问问：这件事中，有什么是你真正能控制的？";
        }
    }
}
