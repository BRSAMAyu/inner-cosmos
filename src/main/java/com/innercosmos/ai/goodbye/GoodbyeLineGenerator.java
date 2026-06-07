package com.innercosmos.ai.goodbye;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Generates a personalized goodbye line using LLM.
 * Falls back to template if LLM fails.
 */
@Service
public class GoodbyeLineGenerator {

    @Autowired
    private LlmClient llm;

    public CompletableFuture<String> generate(Long userId, Long sessionId, String trigger) {
        return CompletableFuture.supplyAsync(() -> {
            String prompt = "用户触发了告别流程（触发方式：" + trigger + "）。"
                    + "请写一句温柔的告别语，不超过30字，符合Aurora的角色定位。"
                    + "Aurora 是一个温柔、具体、像朋友一样的AI伴侣。";
            try {
                return llm.chat(new LlmRequest(userId, "GOODBYE_LINE", prompt));
            } catch (Exception e) {
                return null;
            }
        });
    }
}