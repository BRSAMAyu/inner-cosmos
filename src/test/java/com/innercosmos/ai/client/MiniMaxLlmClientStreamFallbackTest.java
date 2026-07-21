package com.innercosmos.ai.client;

import com.innercosmos.service.AiLogService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the silent-empty-reply bug: a real-provider SSE stream that returns a NON-2xx
 * status (rate limit / bad request / auth) yields an error JSON body, not SSE {@code data:} lines.
 * The stream then aggregates to empty and the turn used to complete with a silent empty reply.
 * The fix routes non-2xx (and 2xx-but-empty) streams into the blocking {@code chat()} path, which
 * retries and finally falls back to the local mock so the turn always produces a reply.
 */
class MiniMaxLlmClientStreamFallbackTest {

    @Test
    void non2xxStreamFallsBackToMockInsteadOfSilentEmptyReply() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"error\":\"rate limited\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        CountDownLatch recorded = new CountDownLatch(1);
        AtomicReference<String> provider = new AtomicReference<>();
        AtomicBoolean fallbackUsed = new AtomicBoolean(false);
        AtomicReference<String> response = new AtomicReference<>();
        AiLogService capturing = new MiniMaxLlmClientTest.NoopAiLogService() {
            @Override
            public void recordDetailed(Long userId, String moduleName, String prov, String modelName,
                                       String prompt, String resp, String requestJson, String responseJson,
                                       boolean success, boolean fb, String errorMessage, long latencyMs) {
                provider.set(prov);
                fallbackUsed.set(fb);
                response.set(resp);
                recorded.countDown();
            }
        };

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            MiniMaxLlmClient client = new MiniMaxLlmClient("dummy-key", baseUrl, "test-model", 3000,
                    true, capturing, Executors.newSingleThreadExecutor());

            SseEmitter emitter = client.streamChat(new LlmRequest(1L, "TEST", "hello"));
            assertNotNull(emitter);

            assertTrue(recorded.await(10, TimeUnit.SECONDS),
                    "non-2xx stream never routed into the blocking fallback (silent empty-reply bug still present)");
            assertEquals("MOCK", provider.get(), "non-2xx stream must fall back to the local mock");
            assertTrue(fallbackUsed.get(), "fallbackUsed must be true on the fallback path");
            assertNotNull(response.get(), "fallback must produce a reply");
            assertFalse(response.get().isBlank(), "fallback reply must be non-empty, not a silent empty turn");
        } finally {
            server.stop(0);
        }
    }
}
