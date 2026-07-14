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
                    "spring.datasource.url=jdbc:mysql://db.example/inner_cosmos?sslMode=VERIFY_IDENTITY",
                    "spring.datasource.username=app",
                    "spring.datasource.password=test-only-placeholder");

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
    void rejectsDemoSeedingEvenWhenEnvironmentOverridesProfile() {
        runner.withPropertyValues("inner-cosmos.demo.seed-enabled=true")
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
        runner.withPropertyValues("spring.datasource.url=jdbc:mysql://db.example/inner_cosmos?useSSL=true")
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
