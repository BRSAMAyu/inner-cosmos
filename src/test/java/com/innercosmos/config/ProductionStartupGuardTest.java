package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionStartupGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionStartupGuard.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "llm.mode=prod",
                    "llm.provider=glm",
                    "llm.api-key=test-only-placeholder",
                    "llm.allow-fallback=false",
                    "inner-cosmos.demo.seed-enabled=false",
                    "server.servlet.session.cookie.secure=true",
                    "inner-cosmos.security.csrf-enabled=true",
                    "inner-cosmos.auth.oidc.enabled=true",
                    "inner-cosmos.auth.oidc.issuer-uri=https://identity.example/",
                    "inner-cosmos.auth.oidc.jwk-set-uri=https://identity.example/jwks",
                    "inner-cosmos.auth.oidc.audience=inner-cosmos-api",
                    "inner-cosmos.auth.oidc.authorization-uri=https://identity.example/authorize",
                    "inner-cosmos.auth.oidc.token-uri=https://identity.example/token",
                    "inner-cosmos.auth.oidc.client-id=inner-cosmos-mobile",
                    "inner-cosmos.auth.oidc.redirect-uri=innercosmos://auth/callback",
                    "inner-cosmos.session.redis.enabled=true",
                    "inner-cosmos.security.rate-limit.redis.enabled=true",
                    "inner-cosmos.security.rate-limit.redis.namespace=inner-cosmos:rate-limit:v1",
                    "inner-cosmos.idempotency.redis.enabled=true",
                    "inner-cosmos.idempotency.redis.namespace=inner-cosmos:idempotency:v1",
                    "inner-cosmos.aurora.stream.redis.enabled=true",
                    "inner-cosmos.aurora.stream.stage-namespace=inner-cosmos:aurora:stage:v1",
                    "inner-cosmos.aurora.stream.live-namespace=inner-cosmos:aurora:live:v1",
                    "inner-cosmos.scheduler.redis-lock.enabled=true",
                    "inner-cosmos.scheduler.redis-lock.namespace=inner-cosmos-scheduler-v1",
                    "spring.data.redis.ssl.enabled=true",
                    "spring.data.redis.host=redis.example",
                    "spring.data.redis.password=test-only-redis-placeholder",
                    "spring.session.redis.namespace=inner-cosmos:session",
                    "spring.datasource.url=jdbc:postgresql://db.example/inner_cosmos?sslmode=verify-full",
                    "spring.datasource.username=app",
                    "spring.datasource.password=test-only-placeholder",
                    "spring.flyway.enabled=true",
                    "spring.sql.init.mode=never");

    @Test
    void acceptsCompleteFailClosedProductionConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductionStartupGuard.class);
            context.getBean(ProductionStartupGuard.class).validate();
        });
    }

    @Test
    void rejectsMockProvider() {
        runner.withPropertyValues("llm.provider=mock")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsFallbackEvenWhenEnvironmentOverridesProfile() {
        runner.withPropertyValues("llm.allow-fallback=true")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsProcessLocalAuroraStreamingInProduction() {
        runner.withPropertyValues("inner-cosmos.aurora.stream.redis.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsDemoSeedingEvenWhenEnvironmentOverridesProfile() {
        runner.withPropertyValues("inner-cosmos.demo.seed-enabled=true")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsDisabledCsrfProtection() {
        runner.withPropertyValues("inner-cosmos.security.csrf-enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsDisabledOidcResourceServer() {
        runner.withPropertyValues("inner-cosmos.auth.oidc.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsInProcessProductionSessions() {
        runner.withPropertyValues("inner-cosmos.session.redis.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsRedisWithoutTls() {
        runner.withPropertyValues("spring.data.redis.ssl.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsInProcessProductionRateLimits() {
        runner.withPropertyValues("inner-cosmos.security.rate-limit.redis.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsInProcessProductionIdempotency() {
        runner.withPropertyValues("inner-cosmos.idempotency.redis.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsProductionWithoutDistributedSchedulerLeases() {
        runner.withPropertyValues("inner-cosmos.scheduler.redis-lock.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void apiRoleRequiresJdbcOutbox() {
        runner.withPropertyValues(
                        "inner-cosmos.runtime.role=api",
                        "inner-cosmos.events.outbox.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void migrationRoleRequiresOnlyDatabaseProductionControls() {
        runner.withPropertyValues(
                        "inner-cosmos.runtime.role=migration",
                        "llm.provider=",
                        "llm.api-key=",
                        "inner-cosmos.auth.oidc.enabled=false",
                        "inner-cosmos.session.redis.enabled=false",
                        "inner-cosmos.security.rate-limit.redis.enabled=false",
                        "inner-cosmos.scheduler.redis-lock.enabled=false",
                        "spring.data.redis.ssl.enabled=false",
                        "spring.data.redis.host=",
                        "spring.data.redis.password=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(ProductionStartupGuard.class).validate();
                });
    }

    @Test
    void apiRoleRejectsFlywayOwnership() {
        runner.withPropertyValues(
                        "inner-cosmos.runtime.role=api",
                        "inner-cosmos.events.outbox.enabled=true",
                        "spring.flyway.enabled=true")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void apiRoleAcceptsCompletedExternallyMigratedSchemaConfiguration() {
        runner.withPropertyValues(
                        "inner-cosmos.runtime.role=api",
                        "inner-cosmos.events.outbox.enabled=true",
                        "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(ProductionStartupGuard.class).validate();
                });
    }

    @Test
    void rejectsUnknownRuntimeRole() {
        runner.withPropertyValues("inner-cosmos.runtime.role=unknown")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsMissingRedisCredentialWithoutEchoingConfiguration() {
        runner.withPropertyValues("spring.data.redis.password=")
                .run(context -> assertThatThrownBy(
                        () -> context.getBean(ProductionStartupGuard.class).validate())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageNotContaining("test-only-redis-placeholder"));
    }

    @Test
    void rejectsOidcJwkEndpointWithoutTls() {
        runner.withPropertyValues("inner-cosmos.auth.oidc.jwk-set-uri=http://identity.example/jwks")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsMissingCredentialWithoutEchoingConfiguration() {
        runner.withPropertyValues("llm.api-key=")
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(ProductionStartupGuard.class).validate())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageNotContaining("test-only-placeholder");
                });
    }

    @Test
    void rejectsDatasourceWithoutCertificateVerification() {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://db.example/inner_cosmos?sslmode=require")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsLegacyMysqlDatasource() {
        runner.withPropertyValues("spring.datasource.url=jdbc:mysql://db.example/inner_cosmos?sslMode=VERIFY_IDENTITY")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsProductionWithoutFlyway() {
        runner.withPropertyValues("spring.flyway.enabled=false")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsLegacySqlInitializer() {
        runner.withPropertyValues("spring.sql.init.mode=always")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    @Test
    void rejectsMissingDatasourcePassword() {
        runner.withPropertyValues("spring.datasource.password=")
                .run(context -> assertRejected(context.getBean(ProductionStartupGuard.class)));
    }

    private void assertRejected(ProductionStartupGuard guard) {
        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Production startup rejected:");
    }
}
