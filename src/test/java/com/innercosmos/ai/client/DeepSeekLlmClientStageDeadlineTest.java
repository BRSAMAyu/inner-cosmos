package com.innercosmos.ai.client;

import com.innercosmos.service.AiLogService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DeepSeekLlmClientStageDeadlineTest {

    @Test
    void boundedStructuredStageTimesOutOnceWithoutRepeatingTheSlowRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(500);
                byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            DeepSeekLlmClient client = new DeepSeekLlmClient(
                    "test-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "deepseek-v4-flash",
                    5_000,
                    false,
                    mock(AiLogService.class),
                    Runnable::run);
            LlmRequest request = new LlmRequest(7L, "AURORA_PLAN_DAILY_TALK", "{}");
            request.timeoutMs = 100;
            request.retryEnabled = false;
            request.thinkingEnabled = true;

            assertThatThrownBy(() -> client.chat(request))
                    .hasMessageContaining("retry is disabled");
            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }
}
