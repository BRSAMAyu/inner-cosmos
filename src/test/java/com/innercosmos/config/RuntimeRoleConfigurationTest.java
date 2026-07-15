package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeRoleConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeSchedulingConfiguration.class);

    @Test
    void enablesSchedulingOnlyForAllWorkerAndSchedulerRoles() {
        for (String role : new String[]{"all", "worker", "scheduler"}) {
            runner.withPropertyValues("inner-cosmos.runtime.role=" + role)
                    .run(context -> assertThat(context).hasBean(
                            TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
        }
        for (String role : new String[]{"api", "migration"}) {
            runner.withPropertyValues("inner-cosmos.runtime.role=" + role)
                    .run(context -> assertThat(context).doesNotHaveBean(
                            TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
        }
    }

    @Test
    void migrationExitClosesOnlyMigrationRole() throws Exception {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("inner-cosmos.runtime.role", "")).thenReturn("migration");

        new MigrationRoleExit(context).run(new DefaultApplicationArguments());
        verify(context).close();
    }

    @Test
    void migrationExitRejectsOtherRoles() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("inner-cosmos.runtime.role", "")).thenReturn("api");

        MigrationRoleExit exit = new MigrationRoleExit(context);
        assertThatThrownBy(() -> exit.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("migration runtime role");
    }
}
