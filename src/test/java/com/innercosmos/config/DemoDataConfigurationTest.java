package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataConfigurationTest {

    @Test
    void prodNeverRegistersInitializerEvenWhenSeedFlagIsTrue() {
        new ApplicationContextRunner()
                .withUserConfiguration(DemoDataConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "inner-cosmos.demo.seed-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MockDataInitializer.class);
                });
    }

    @Test
    void nonProdDoesNotRegisterInitializerWithoutExplicitOptIn() {
        new ApplicationContextRunner()
                .withUserConfiguration(DemoDataConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "inner-cosmos.demo.seed-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MockDataInitializer.class);
                });
    }
}
