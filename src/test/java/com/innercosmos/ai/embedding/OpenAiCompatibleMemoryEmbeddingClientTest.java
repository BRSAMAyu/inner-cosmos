package com.innercosmos.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.innercosmos.config.MemoryEmbeddingConfig;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleMemoryEmbeddingClientTest {
    @Test
    void explicitlyEnabledProviderFailsClosedWithoutCredential() {
        MemoryEmbeddingConfig config = new MemoryEmbeddingConfig(); config.enabled = true; config.apiKey = "";
        assertThrows(IllegalStateException.class, () -> config.memoryEmbeddingClient(new ObjectMapper()));
    }

    @Test
    void sendsOpenAiCompatibleRequestAndValidatesDimension() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"data\":[{\"embedding\":[1,0,0,0,0,0,0,0]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new OpenAiCompatibleMemoryEmbeddingClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-only-key",
                    "embedding-contract-model", "v1", 8, new ObjectMapper());
            assertArrayEquals(new float[]{1,0,0,0,0,0,0,0}, client.embed("private text never logged"));
            assertEquals("Bearer test-only-key", authorization.get());
            assertTrue(requestBody.get().contains("embedding-contract-model"));
            assertTrue(requestBody.get().contains("private text never logged"));
        } finally { server.stop(0); }
    }
}
