package com.innercosmos.event.reliable;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-40 admin DLQ dashboard over the transactional outbox. The listing and per-event replay
 * semantics are exercised on H2; the claim→DEAD transition itself (SKIP LOCKED claiming) is
 * PostgreSQL-native and owned by the Docker-gated PostgresOutboxReliabilityTest, so this test
 * drives rows to DEAD through the repository's real failure path ({@link JdbcOutboxRepository#retry})
 * after emulating a worker claim the same way JdbcOutboxRepositoryIntegrationTest does. Scheduling
 * is disabled so no live worker races the setup. The dashboard only ever presents rows that really
 * exist: the empty state on a fresh database is asserted as empty.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox-dlq-dashboard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "inner-cosmos.events.outbox.enabled=true",
        "spring.task.scheduling.enabled=false",
        "llm.provider=mock"
})
@AutoConfigureMockMvc
class OutboxDeadLetterDashboardTest {

    private static final int MAX_ATTEMPTS = 5; // mirrors JdbcOutboxWorker.MAX_ATTEMPTS
    private static final String WORKER = "dlq-dashboard-test-worker";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcOutboxRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserMapper userMapper;

    @Test
    void dashboardListsRealDeadRowsAndReplayRequeuesExactlyOnce() throws Exception {
        MockHttpSession admin = session("admin");

        // Honest empty state: nothing has failed on this database, so the page is empty.
        mockMvc.perform(get("/api/admin/outbox/dead").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.entries.length()").value(0));

        // Three real dead letters: an event type with no registered handler (the "unknown event"
        // DLQ reality the worker retries to DEAD) and two known types with a failing consumer.
        UUID unknown = driveToDead("system.unregistered-probe.v1",
                "{\"probe\":\"no handler will ever answer\"}");
        UUID dialogPoison = driveToDead(DialogFinishedOutboxWriter.EVENT_TYPE,
                "{\"userId\":920000001,\"sessionId\":920000002}");
        UUID retractionPoison = driveToDead(DataRetractedOutboxWriter.EVENT_TYPE,
                "{\"receiptId\":920000003,\"userId\":920000001,\"subjectType\":\"MEMORY_CARD\"}");

        // The dashboard lists exactly the DEAD rows, with fields read from the rows themselves.
        mockMvc.perform(get("/api/admin/outbox/dead").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.entries.length()").value(3))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + unknown + "')].eventType")
                        .value("system.unregistered-probe.v1"))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + unknown + "')].attempts")
                        .value(MAX_ATTEMPTS))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + dialogPoison + "')].eventType")
                        .value("dialog.finished.v1"))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + dialogPoison + "')].payloadSummary")
                        .value("{\"userId\":920000001,\"sessionId\":920000002}"))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + dialogPoison + "')].lastError")
                        .value("IllegalStateException: simulated poison failure, attempt " + MAX_ATTEMPTS))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + retractionPoison + "')].aggregateType")
                        .value("dialog-session"))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + retractionPoison + "')].createdAt")
                        .exists())
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + retractionPoison + "')].lastAttemptAt")
                        .exists());

        // Paging: limit caps the page, total keeps reporting the real queue size.
        mockMvc.perform(get("/api/admin/outbox/dead").param("limit", "2").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(3));

        // Replay requeues the dead row for real: PENDING, attempts zeroed, last error cleared.
        mockMvc.perform(post("/api/admin/outbox/dead/{eventId}/replay", dialogPoison).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(dialogPoison.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(statusOf(dialogPoison)).isEqualTo("PENDING");
        assertThat(attemptsOf(dialogPoison)).isZero();
        assertThat(lastErrorOf(dialogPoison)).isNull();

        // The requeued row leaves the dead-letter view; the other two remain.
        mockMvc.perform(get("/api/admin/outbox/dead").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + dialogPoison + "')]").doesNotExist())
                .andExpect(jsonPath("$.data.entries[?(@.eventId=='" + unknown + "')]").exists());

        // Second replay of the same event is rejected: the row is no longer DEAD.
        mockMvc.perform(post("/api/admin/outbox/dead/{eventId}/replay", dialogPoison).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // Replay of an unknown event is a 404, not a silent success.
        mockMvc.perform(post("/api/admin/outbox/dead/{eventId}/replay", UUID.randomUUID())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nonAdminAndAnonymousCallersAreRejectedByTheExistingAdminGate() throws Exception {
        // Existing admin isolation mode: requireAdmin rejects a logged-in non-admin with the
        // UNAUTHORIZED business code (same as every other /api/admin endpoint in AdminController).
        MockHttpSession demo = session("demo");
        mockMvc.perform(get("/api/admin/outbox/dead").session(demo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/admin/outbox/dead/{eventId}/replay", UUID.randomUUID()).session(demo))
                .andExpect(status().isUnauthorized());
        // Spring Security: /api/** requires an authenticated session in the first place.
        mockMvc.perform(get("/api/admin/outbox/dead"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Drive one appended event to DEAD through the repository's real failure path: emulate each
     * worker claim (SKIP LOCKED is PostgreSQL-native and Docker-gated, so the claim's database
     * effect is reproduced with a direct UPDATE, as JdbcOutboxRepositoryIntegrationTest does) and
     * then fail via {@link JdbcOutboxRepository#retry} until attempts reach {@code MAX_ATTEMPTS}.
     */
    private UUID driveToDead(String eventType, String payload) {
        UUID eventId = UUID.randomUUID();
        String dedupKey = "dlq-dash:" + eventType + ":" + eventId;
        assertThat(repository.append(eventId, dedupKey, "dialog-session", "920000001",
                eventType, 1, payload, null)).isTrue();
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tb_outbox_event WHERE event_id = ?", Long.class, eventId);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            jdbcTemplate.update(
                    "UPDATE tb_outbox_event SET status='PROCESSING', locked_by=?, "
                            + "locked_until=CURRENT_TIMESTAMP WHERE id=?", WORKER, id);
            OutboxEvent claimed = new OutboxEvent(id, eventId, dedupKey, "dialog-session",
                    "920000001", eventType, 1, payload, null, attempt - 1, WORKER, null);
            repository.retry(claimed,
                    new IllegalStateException("simulated poison failure, attempt " + attempt),
                    MAX_ATTEMPTS, Duration.ZERO);
        }
        assertThat(statusOf(eventId)).isEqualTo("DEAD");
        return eventId;
    }

    private String statusOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM tb_outbox_event WHERE event_id = ?", String.class, eventId);
    }

    private Integer attemptsOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM tb_outbox_event WHERE event_id = ?", Integer.class, eventId);
    }

    private String lastErrorOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error FROM tb_outbox_event WHERE event_id = ?", String.class, eventId);
    }

    private MockHttpSession session(String username) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        assertThat(user).as("MockDataInitializer must seed " + username).isNotNull();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return session;
    }
}
