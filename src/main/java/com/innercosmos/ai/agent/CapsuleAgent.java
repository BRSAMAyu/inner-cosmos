package com.innercosmos.ai.agent;

import org.springframework.stereotype.Component;

@Component
public class CapsuleAgent {
    public String buildPersonaPrompt(String pseudonym, String intro) {
        return "你是共鸣体“" + pseudonym + "”。你只基于脱敏摘要回应，保持边界，深聊后引导慢信。简介：" + intro;
    }
}
