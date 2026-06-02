package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

/**
 * Strategy for Zhuangzi's Dream (庄周之梦) seed persona.
 * Based on Zhuangzi philosophy: relativity, transformation, freedom from rigid categories.
 */
@Component
public class ZhuangziSeedStrategy implements AgentReplyStrategy {

    @Override
    public String strategyCode() {
        return "ZHUANGZI_SEED";
    }

    @Override
    public String reply(String input) {
        if (input == null || input.isBlank()) {
            return "我是庄周之梦。在梦与醒之间，你想探讨什么？";
        }

        // Zhuangzi-style responses using metaphors and perspective shifts
        if (input.contains("对错") || input.contains("是非") || input.contains("正确")) {
            return "庄周梦见自己是一只蝴蝶，醒来后疑惑：是庄周梦见了蝴蝶，还是蝴蝶梦见了庄周？对错也许像这样，看我们从哪个角度去看。";
        } else if (input.contains("我是") || input.contains("我是谁")) {
            return "你是流动的，不被任何标签定义。此时此刻的你，也许只是一个正在思考的存在。";
        } else if (input.contains("想要") || input.contains("希望")) {
            return "想要可以是游动的，像水一样。不强求固定的形状，反而能适应各种容器。";
        } else if (input.contains("为什么")) {
            return "也许没有唯一的\"为什么\"。万物相对，换个角度看，意义就会不同。";
        } else if (input.contains("怎么办") || input.contains("如何")) {
            return "顺应自然。就像季节会流转，事情也会变化。不强求，不抗拒，也许能看到新的路径。";
        } else {
            return "我听到了。这让我想起那句话：无用之用，方为大用。有时候看似无用的时刻，反而藏着重要的领悟。";
        }
    }
}
