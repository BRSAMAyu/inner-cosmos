package com.innercosmos.ai.client;

import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.exception.AiProviderException;
import com.innercosmos.service.AiLogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MiniMaxLlmClientTest {
    @Test
    void prodStyleFallbackDisabledThrowsWhenKeyMissing() {
        MiniMaxLlmClient client = new MiniMaxLlmClient("", "", "", 1000,
                false, new NoopAiLogService(), Executors.newSingleThreadExecutor());

        assertThrows(AiProviderException.class,
                () -> client.chat(new LlmRequest(1L, "TEST", "hello")));
    }

    static class NoopAiLogService implements AiLogService {
        @Override
        public void record(Long userId, String moduleName, String prompt, String response, boolean success, long latencyMs) {
        }

        @Override
        public void recordDetailed(Long userId, String moduleName, String provider, String modelName,
                                   String prompt, String response, String requestJson, String responseJson,
                                   boolean success, boolean fallbackUsed, String errorMessage, long latencyMs) {
        }

        @Override
        public List<AiInteractionLog> listRecent(Long userId) {
            return List.of();
        }

        @Override
        public List<AiInteractionLog> listRecent(Long userId, String moduleName, String provider, Boolean success) {
            return List.of();
        }

        @Override
        public AiInteractionLog latest() {
            return null;
        }
    }
}
