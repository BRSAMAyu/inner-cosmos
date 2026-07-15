package com.innercosmos.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.InnerCosmosApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisSessionPersistenceIntegrationTest {

    private static final String IMAGE = "redis:7.4.2-alpine@sha256:"
            + "02419de7eddf55aa5bcf49efb74e88fa8d931b4d77c07eff8a6b2144472b6952";
    private static final String REDIS_PASSWORD = "redis-session-contract-only";
    private static final String NAMESPACE = "inner-cosmos:test:session";
    private static final String DATABASE = "redis-session-" + UUID.randomUUID();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @Test
    void authenticatedSessionSurvivesApplicationProcessRestart() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String username = "redis_user_" + UUID.randomUUID().toString().replace("-", "");

        try (ConfigurableApplicationContext first = startApplication()) {
            URI baseUri = baseUri(first);
            ObjectMapper mapper = first.getBean(ObjectMapper.class);
            JsonNode csrf = getJson(client, mapper, baseUri.resolve("/api/auth/csrf"));
            String token = csrf.path("data").path("token").asText();
            String header = csrf.path("data").path("headerName").asText();
            assertThat(token).isNotBlank();

            String body = mapper.createObjectNode()
                    .put("username", username)
                    .put("password", "redis-session-password")
                    .put("nickname", "Redis Session User")
                    .toString();
            HttpResponse<String> registration = client.send(HttpRequest.newBuilder(
                            baseUri.resolve("/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .header(header, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(registration.statusCode()).isEqualTo(200);

            StringRedisTemplate redis = first.getBean(StringRedisTemplate.class);
            Set<String> sessionKeys = redis.keys(NAMESPACE + ":sessions:*");
            assertThat(sessionKeys).isNotNull().isNotEmpty();
        }

        try (ConfigurableApplicationContext second = startApplication()) {
            JsonNode current = getJson(client, second.getBean(ObjectMapper.class),
                    baseUri(second).resolve("/api/auth/current"));
            assertThat(current.path("success").asBoolean()).isTrue();
            assertThat(current.path("data").path("username").asText()).isEqualTo(username);
        }
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(InnerCosmosApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:" + DATABASE
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "--spring.sql.init.mode=always",
                        "--spring.flyway.enabled=false",
                        "--spring.task.scheduling.enabled=false",
                        "--inner-cosmos.demo.seed-enabled=false",
                        "--inner-cosmos.security.csrf-enabled=true",
                        "--inner-cosmos.session.redis.enabled=true",
                        "--spring.session.redis.namespace=" + NAMESPACE,
                        "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                        "--spring.data.redis.password=" + REDIS_PASSWORD,
                        "--spring.data.redis.ssl.enabled=false");
    }

    private URI baseUri(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port);
    }

    private JsonNode getJson(HttpClient client, ObjectMapper mapper, URI uri) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
}
