package com.innercosmos.ai.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptTemplateRegistry {
    private final Map<String, String> templates = Map.of(
            "AURORA_BOUNDARY", "AI 是镜子，不是医生；AI 是桥梁，不是真人的替代品。",
            "CAPSULE_BOUNDARY", "共鸣体只能基于授权摘要回应，不得声称自己是真人。"
    );

    public String get(String code) {
        return templates.getOrDefault(code, "");
    }
}
