package com.innercosmos.config;

import com.innercosmos.ai.goodbye.SessionIdleWatcher;
import com.innercosmos.event.reliable.JdbcOutboxWorker;
import com.innercosmos.scheduler.AuroraProactiveJob;
import com.innercosmos.scheduler.CapsuleEmbeddingRebuildJob;
import com.innercosmos.scheduler.CapsuleSyncRetryJob;
import com.innercosmos.scheduler.ClaimDecaySweepJob;
import com.innercosmos.scheduler.LetterDeliveryJob;
import com.innercosmos.scheduler.MemoryEmbeddingRebuildJob;
import com.innercosmos.scheduler.NightlyMemorySettlementJob;
import com.innercosmos.scheduler.PushDeliveryJob;
import com.innercosmos.scheduler.WakeIntentDeliveryJob;
import com.innercosmos.InnerCosmosApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2.RUNTIME-ROLES: an explicit, per-role job/worker bean-presence assertion. Unlike
 * {@link RuntimeRoleConfigurationTest} (which checks only the scheduling annotation processor and
 * the migration exit runner in isolation), this test boots the *real* production application
 * context -- via Testcontainers Postgres, not H2 -- once per runtime role and asserts exactly
 * which of the role-gated job/worker beans are and are not present.
 *
 * <p>H2 is deliberately avoided: {@link JdbcOutboxWorker#poll()} issues a claim query written for
 * PostgreSQL row-locking semantics, and a prior full-boot attempt on H2 with the worker role
 * enabled tripped that poll and had to be reverted. Real Postgres removes that failure mode, and
 * {@code spring.task.scheduling.enabled=false} additionally suppresses actual firing of any
 * {@code @Scheduled} method during the short window the context is open for assertion --
 * bean *presence* is governed by the {@code @ConditionalOnExpression}/{@code @ConditionalOnProperty}
 * gates on the bean definitions themselves, not by whether scheduling is turned on, so disabling
 * scheduling does not weaken what this test proves.
 */
@Testcontainers
class RuntimeRoleJobBeanPresenceIntegrationTest {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inner_cosmos_runtime_roles")
            .withUsername("inner_cosmos")
            .withPassword("runtime-role-contract-only");

    /** All beans whose presence is gated by {@code inner-cosmos.runtime.role}. */
    private static final List<Class<?>> SCHEDULER_JOB_TYPES = List.of(
            AuroraProactiveJob.class,
            CapsuleEmbeddingRebuildJob.class,
            CapsuleSyncRetryJob.class,
            ClaimDecaySweepJob.class,
            LetterDeliveryJob.class,
            MemoryEmbeddingRebuildJob.class,
            NightlyMemorySettlementJob.class,
            PushDeliveryJob.class,
            WakeIntentDeliveryJob.class,
            SessionIdleWatcher.class);

    private ConfigurableApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    void apiRoleHasNeitherOutboxWorkerNorAnySchedulerJob() {
        context = boot("api");

        assertThat(context.getBeansOfType(JdbcOutboxWorker.class)).isEmpty();
        for (Class<?> jobType : SCHEDULER_JOB_TYPES) {
            assertThat(context.getBeansOfType(jobType))
                    .as("api role must not assemble %s", jobType.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void workerRoleHasOnlyTheOutboxWorker() {
        context = boot("worker");

        assertThat(context.getBeansOfType(JdbcOutboxWorker.class)).hasSize(1);
        for (Class<?> jobType : SCHEDULER_JOB_TYPES) {
            assertThat(context.getBeansOfType(jobType))
                    .as("worker role must not assemble %s", jobType.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void schedulerRoleHasEverySchedulerJobButNotTheOutboxWorker() {
        context = boot("scheduler");

        assertThat(context.getBeansOfType(JdbcOutboxWorker.class)).isEmpty();
        for (Class<?> jobType : SCHEDULER_JOB_TYPES) {
            assertThat(context.getBeansOfType(jobType))
                    .as("scheduler role must assemble %s", jobType.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    void migrationRoleHasNeitherOutboxWorkerNorAnySchedulerJob() {
        // exit-after-startup is deliberately NOT set here: MigrationRoleExit only fires on that
        // separate property (asserted by RuntimeRoleConfigurationTest), so the context stays open
        // long enough to inspect which job/worker beans the migration role itself assembles.
        context = boot("migration");

        assertThat(context.getBeansOfType(JdbcOutboxWorker.class)).isEmpty();
        for (Class<?> jobType : SCHEDULER_JOB_TYPES) {
            assertThat(context.getBeansOfType(jobType))
                    .as("migration role must not assemble %s", jobType.getSimpleName())
                    .isEmpty();
        }
    }

    private ConfigurableApplicationContext boot(String role) {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        props.put("spring.datasource.username", POSTGRES.getUsername());
        props.put("spring.datasource.password", POSTGRES.getPassword());
        props.put("spring.datasource.driver-class-name", POSTGRES.getDriverClassName());
        props.put("spring.flyway.enabled", true);
        props.put("spring.flyway.locations", "classpath:db/migration/postgresql");
        props.put("spring.flyway.validate-migration-naming", true);
        props.put("spring.flyway.clean-disabled", true);
        props.put("spring.sql.init.mode", "never");
        // Suppress actual @Scheduled firing so the short assertion window can never race a real
        // poll -- bean presence is decided by the conditional gates below, not by this flag.
        props.put("spring.task.scheduling.enabled", false);
        props.put("inner-cosmos.demo.seed-enabled", false);
        props.put("llm.provider", "mock");
        props.put("llm.mode", "dev");
        props.put("server.port", "0");
        props.put("inner-cosmos.runtime.role", role);
        // Enable every conditional gate an individual job also carries on top of the role gate, so
        // the role dimension is isolated: any absence below is attributable to the role alone.
        props.put("inner-cosmos.events.outbox.enabled", true);
        props.put("memory.embedding.enabled", true);
        // MemoryEmbeddingConfig#memoryEmbeddingClient requires a non-blank key to construct once
        // enabled, even though nothing in this test ever calls out to a provider. Not a real
        // credential -- a fixture value scoped to this test process only.
        props.put("memory.embedding.api-key", "test-fixture-key-not-a-real-credential");
        props.put("inner-cosmos.push.worker-enabled", true);

        // SpringApplicationBuilder#properties() registers a *default* (lowest-priority) property
        // source, so it cannot override src/test/resources/application.yml's H2/Flyway-disabled
        // test defaults. Command-line args sit above profile/application YAML in Boot's precedence
        // order, so pass everything as "--key=value" instead to make the Postgres/role overrides
        // actually stick.
        String[] args = props.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);

        return new SpringApplicationBuilder(InnerCosmosApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
