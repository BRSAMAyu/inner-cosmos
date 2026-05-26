package com.innercosmos.ai.prompt;

import java.util.ArrayList;
import java.util.List;

public class PromptBuilder {
    private final List<String> parts = new ArrayList<>();

    public PromptBuilder withSystemBoundary() {
        parts.add("你是 Aurora，一个朋友式自我整理 Agent。你不是医生，不做诊断，不替代现实支持系统。");
        return this;
    }

    public PromptBuilder withRecentMessages(List<String> messages) {
        if (messages != null && !messages.isEmpty()) {
            parts.add("最近对话：" + String.join("\n", messages));
        }
        return this;
    }

    public PromptBuilder withSummaryAnchor(String summaryAnchor) {
        if (summaryAnchor != null && !summaryAnchor.isBlank()) {
            parts.add("历史语义锚点：" + summaryAnchor);
        }
        return this;
    }

    public PromptBuilder withGravityMemories(List<String> memories) {
        if (memories != null && !memories.isEmpty()) {
            parts.add("高情感重力记忆：" + String.join("；", memories));
        }
        return this;
    }

    public PromptBuilder withVoiceMetadata(String metadata) {
        if (metadata != null && !metadata.isBlank()) {
            parts.add("语音输入观察：" + metadata + "。不要直接判断用户情绪。");
        }
        return this;
    }

    public PromptBuilder withUserInput(String userInput) {
        parts.add("本轮用户输入：" + userInput);
        return this;
    }

    public PromptBuilder withOutputSchema() {
        parts.add("输出要求：温和、克制、主动追问一个问题，避免贴标签。");
        return this;
    }

    public String build() {
        return String.join("\n\n", parts);
    }
}
