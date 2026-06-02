package com.innercosmos.ai.strategy;

import org.springframework.stereotype.Component;

/**
 * Strategy for Existential Traveler (存在主义旅人) seed persona.
 * Based on existential philosophy: authenticity, freedom, responsibility, meaning-making.
 */
@Component
public class ExistentialSeedStrategy implements AgentReplyStrategy {

    @Override
    public String strategyCode() {
        return "EXISTENTIAL_SEED";
    }

    @Override
    public String reply(String input) {
        if (input == null || input.isBlank()) {
            return "我是存在主义旅人。在这个世界上，我们先存在，然后才定义自己。你想探讨什么？";
        }

        // Existentialist responses focusing on freedom, responsibility, authenticity
        if (input.contains("没意义") || input.contains("空虚") || input.contains("荒诞")) {
            return "我听到了这种空虚感。也许世界本身没有预设的意义，但这不意味着我们不能创造意义。即使从零开始，也是一种开始。";
        } else if (input.contains("自由") || input.contains("选择")) {
            return "自由是沉重的，因为每一个选择都意味着责任。但在这份重量中，也有我们的尊严。";
        } else if (input.contains("我是") || input.contains("我是谁")) {
            return "你是自由的。此刻之前的一切，都不必定义你。此刻，你可以重新开始选择。";
        } else if (input.contains("害怕") || input.contains("恐惧") || input.contains("焦虑")) {
            return "焦虑提醒我们：这件事对我们很重要。也许可以问问：这种害怕背后，有什么是我们真正在乎的？";
        } else if (input.contains("为什么")) {
            return "寻找\"为什么\"是自然的。但有时答案不存在于过去，而存在于我们如何面对当下。";
        } else {
            return "我听到了。存在无法逃避，但我们可以选择如何面对。这种选择本身，就是一种回应。";
        }
    }
}
