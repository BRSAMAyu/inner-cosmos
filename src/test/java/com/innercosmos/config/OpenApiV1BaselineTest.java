package com.innercosmos.config;

import com.innercosmos.controller.AuroraChatController;
import com.innercosmos.controller.AuthController;
import com.innercosmos.controller.CapsuleController;
import com.innercosmos.controller.ConversationTimelineController;
import com.innercosmos.controller.LetterController;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiV1BaselineTest {

    @Test
    @SuppressWarnings("unchecked")
    void shippedOpenApi31ContractCoversTheExecutableCoreBoundary() {
        InputStream input = getClass().getResourceAsStream("/static/openapi/inner-cosmos-v1.yml");
        assertThat(input).as("shipped versioned OpenAPI artifact").isNotNull();
        Map<String, Object> root = new Yaml().load(input);
        assertThat(String.valueOf(root.get("openapi"))).startsWith("3.1.");
        assertThat((Map<String, Object>) root.get("info")).containsEntry("version", "1.0.0");

        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        assertThat(paths).containsKeys(
                "/api/v1/auth/login",
                "/api/v1/aurora/message",
                "/api/v1/aurora/stream-stage",
                "/api/v1/aurora/turns/{turnId}/events",
                "/api/v1/capsule/create-from-memory",
                "/api/v1/capsule/{id}/boundary",
                "/api/v1/letters/draft",
                "/api/v1/persona-chat/message");

        Map<String, Object> draft = operation(paths, "/api/v1/letters/draft", "post");
        assertHeaderRequired(draft, "Idempotency-Key");
        Map<String, Object> boundaryUpdate = operation(paths, "/api/v1/capsule/{id}/boundary", "post");
        assertHeaderRequired(boundaryUpdate, "If-Match");
        assertHeaderRequired(boundaryUpdate, "Idempotency-Key");
        Map<String, Object> replay = operation(paths, "/api/v1/aurora/turns/{turnId}/events", "get");
        assertThat(parameters(replay)).anySatisfy(parameter ->
                assertThat(parameter).containsEntry("name", "Last-Event-ID"));
        assertThat(replay.toString()).contains("text/event-stream");

        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> error = (Map<String, Object>) schemas.get("ApiError");
        assertThat((List<String>) error.get("required"))
                .contains("success", "code", "message", "status", "traceId", "timestamp");
    }

    @Test
    void documentedV1PrefixesAreRealControllerAliases() {
        assertAliases(AuthController.class, "/api/auth", "/api/v1/auth");
        assertAliases(AuroraChatController.class, "/api/aurora", "/api/v1/aurora");
        assertAliases(ConversationTimelineController.class, "/api/aurora/turns", "/api/v1/aurora/turns");
        assertAliases(CapsuleController.class, "/api/capsule", "/api/v1/capsule");
        assertAliases(LetterController.class, "/api/letters", "/api/v1/letters");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parameters(Map<String, Object> operation) {
        return (List<Map<String, Object>>) operation.getOrDefault("parameters", List.of());
    }

    private void assertHeaderRequired(Map<String, Object> operation, String name) {
        assertThat(parameters(operation)).anySatisfy(parameter -> {
            assertThat(parameter).containsEntry("in", "header").containsEntry("name", name);
            assertThat(parameter.get("required")).isEqualTo(true);
        });
    }

    private void assertAliases(Class<?> controller, String legacy, String v1) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).contains(legacy, v1);
    }
}
