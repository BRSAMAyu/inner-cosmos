package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_CACHE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_CAPSULE_MATCH_VECTOR;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_EXPORT_PACKAGE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_OBJECT_STORAGE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_PROVIDER_COPY;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_PUSH_COPY;
import static com.innercosmos.service.RetractionDerivativeCleanupService.OUTCOME_FAILED;
import static com.innercosmos.service.RetractionDerivativeCleanupService.OUTCOME_NOT_APPLICABLE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.OUTCOME_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CP-15 §2-11: the data.retracted.v1 consumer must drive the per-asset derivative cleanup —
 * every inventory class either executes its real action or is honestly recorded
 * NOT_APPLICABLE with a reason; one asset's failure neither blocks the others nor hides
 * (visible FAILED row, then the retry re-attempts only the failed asset); replaying the same
 * event is idempotent (settled assets skipped, no duplicate rows, no re-delete). The outbox
 * claim/complete/inbox lifecycle itself is PostgreSQL-native and Docker-gated in
 * {@link PostgresOutboxReliabilityTest}; this is the H2-portable executor slice, driven
 * through the real handler with real tables.
 */
@SpringBootTest(properties = {
        "inner-cosmos.events.outbox.enabled=true",
        "spring.task.scheduling.enabled=false"
})
class DataRetractedDerivativeCleanupTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired DataRetractedProjectionHandler handler;
    @Autowired JdbcTemplate jdbc;
    @SpyBean CapsuleEmbeddingIndexService capsuleEmbeddings;

    @BeforeEach
    void resetSpy() {
        Mockito.reset(capsuleEmbeddings);
    }

    @Test
    void retractionEventExecutesEveryInventoryAssetOrHonestlyMarksItNotApplicable() throws Exception {
        long user = 94_700_011L;
        long capsule = 94_700_021L;
        seedUser(user);
        long device = seedDevice(user);
        seedPush(user, device, "PENDING");
        seedPush(user, device, "DELIVERED");
        seedCapsule(capsule, user);
        seedEmbeddings(capsule, 1);

        OutboxEvent event = retractedEvent(101L, user, "CAPSULE", capsule, "CAPSULE_MATCH_INDEX");
        handler.handle(event);

        Map<String, Map<String, Object>> rows = cleanupRows(event);
        assertEquals(6, rows.size(), "one result row per inventory asset: " + rows.keySet());
        // The four classes with no real surface are honestly marked, each with a reason.
        for (String notApplicable : List.of(ASSET_CACHE, ASSET_OBJECT_STORAGE,
                ASSET_EXPORT_PACKAGE, ASSET_PROVIDER_COPY)) {
            assertEquals(OUTCOME_NOT_APPLICABLE, rows.get(notApplicable).get("outcome"),
                    notApplicable + " must be honestly marked NOT_APPLICABLE");
            assertNotNull(rows.get(notApplicable).get("detail"),
                    notApplicable + " must carry a visible reason");
        }
        // The two real actions executed with their affected counts.
        assertEquals(OUTCOME_SUCCESS, rows.get(ASSET_PUSH_COPY).get("outcome"));
        assertEquals(1, ((Number) rows.get(ASSET_PUSH_COPY).get("affected_count")).intValue());
        assertEquals(OUTCOME_SUCCESS, rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("outcome"));
        assertEquals(1, ((Number) rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("affected_count")).intValue());
        // Real derivative effects: the undelivered push copy is gone, the delivered one
        // (already off-platform, unrecallable) stays, the capsule match vector is retired.
        assertEquals(0, pushCount(user, "PENDING"));
        assertEquals(1, pushCount(user, "DELIVERED"));
        assertEquals(0, embeddingCount(capsule));
        verify(capsuleEmbeddings, times(1)).retireForCapsule(capsule);
    }

    @Test
    void oneAssetFailureDoesNotBlockOthersAndLeavesAVisibleFailureRowThenHealsOnRetry() throws Exception {
        doThrow(new IllegalStateException("cp15 injected vector failure"))
                .when(capsuleEmbeddings).retireForCapsule(anyLong());
        long user = 94_700_012L;
        long capsule = 94_700_022L;
        seedUser(user);
        long device = seedDevice(user);
        seedPush(user, device, "PENDING");
        seedCapsule(capsule, user);
        seedEmbeddings(capsule, 1);

        OutboxEvent event = retractedEvent(102L, user, "CAPSULE", capsule, "CAPSULE_MATCH_INDEX");
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> handler.handle(event));
        assertTrue(thrown.getMessage().contains("CAPSULE_MATCH_VECTOR"),
                "the aggregated failure names the failing asset: " + thrown.getMessage());

        Map<String, Map<String, Object>> rows = cleanupRows(event);
        assertEquals(OUTCOME_FAILED, rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("outcome"));
        String detail = String.valueOf(rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("detail"));
        assertTrue(detail.contains("cp15 injected vector failure"),
                "the failure row keeps the visible failure evidence: " + detail);
        // The failing asset did NOT block the others: push copies were really deleted and the
        // no-surface classes were still honestly recorded.
        assertEquals(OUTCOME_SUCCESS, rows.get(ASSET_PUSH_COPY).get("outcome"));
        assertEquals(0, pushCount(user, "PENDING"));
        assertEquals(OUTCOME_NOT_APPLICABLE, rows.get(ASSET_CACHE).get("outcome"));
        assertEquals(OUTCOME_NOT_APPLICABLE, rows.get(ASSET_OBJECT_STORAGE).get("outcome"));
        assertEquals(OUTCOME_NOT_APPLICABLE, rows.get(ASSET_EXPORT_PACKAGE).get("outcome"));
        assertEquals(OUTCOME_NOT_APPLICABLE, rows.get(ASSET_PROVIDER_COPY).get("outcome"));

        // Retry of the SAME event (same eventId) re-attempts only the failed asset and heals
        // the FAILED row in place.
        Mockito.reset(capsuleEmbeddings);
        handler.handle(event);
        rows = cleanupRows(event);
        assertEquals(6, rows.size(), "healing updates the FAILED row, never duplicates it");
        assertEquals(OUTCOME_SUCCESS, rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("outcome"));
        assertEquals(1, ((Number) rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("affected_count")).intValue());
        assertEquals(0, embeddingCount(capsule));
    }

    @Test
    void replayingTheSameEventSkipsSettledAssetsWithoutDuplicatingRowsOrDeletes() throws Exception {
        long user = 94_700_013L;
        long capsule = 94_700_023L;
        seedUser(user);
        long device = seedDevice(user);
        seedPush(user, device, "PENDING");
        seedCapsule(capsule, user);
        seedEmbeddings(capsule, 2);

        OutboxEvent event = retractedEvent(103L, user, "CAPSULE", capsule, "CAPSULE_MATCH_INDEX");
        handler.handle(event);
        verify(capsuleEmbeddings, times(1)).retireForCapsule(capsule);

        handler.handle(event); // at-least-once redelivery of the very same event

        // settled assets are skipped on replay — no second retire call, no duplicate rows
        verify(capsuleEmbeddings, times(1)).retireForCapsule(capsule);
        Map<String, Map<String, Object>> rows = cleanupRows(event);
        assertEquals(6, rows.size(), "replay writes no duplicate rows");
        assertEquals(1, ((Number) rows.get(ASSET_PUSH_COPY).get("affected_count")).intValue());
        assertEquals(2, ((Number) rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("affected_count")).intValue());
    }

    @Test
    void memorySubjectedMatchIndexEventIsHonestlyNotReprocessedByTheConsumer() throws Exception {
        long user = 94_700_014L;
        seedUser(user);
        long device = seedDevice(user);
        seedPush(user, device, "PENDING");

        // Memory forget already retired every affected capsule vector in the owner transaction
        // and recorded receipts; the v1 payload carries no capsuleId, so the consumer must say
        // so instead of guessing.
        OutboxEvent event = retractedEvent(104L, user, "MEMORY", 94_700_024L, "CAPSULE_MATCH_INDEX", 1);
        handler.handle(event);

        Map<String, Map<String, Object>> rows = cleanupRows(event);
        assertEquals(OUTCOME_NOT_APPLICABLE, rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("outcome"));
        verify(capsuleEmbeddings, never()).retireForCapsule(anyLong());
        assertEquals(OUTCOME_SUCCESS, rows.get(ASSET_PUSH_COPY).get("outcome"));
        assertEquals(0, pushCount(user, "PENDING"));
    }

    @Test
    void dataUseGrantSubjectResolvesTheCapsuleIdThroughTheGrantRow() throws Exception {
        long user = 94_700_015L;
        long grant = 94_700_031L;
        long capsule = 94_700_032L;
        seedUser(user);
        seedCapsule(capsule, user);
        seedEmbeddings(capsule, 1);
        jdbc.update("""
                INSERT INTO tb_data_use_grant (id, owner_user_id, resource_type, resource_id,
                  resource_version, purpose, consumer_type, consumer_id, grant_version, status,
                  consent_source, granted_at, created_at, updated_at)
                VALUES (?, ?, 'MEMORY_CARD', 94_700_033L, 1, 'CAPSULE_RUNTIME', 'ECHO_CAPSULE',
                  ?, 1, 'REVOKED', 'OWNER_EXPLICIT_CAPSULE_SELECTION', CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, grant, user, capsule);

        OutboxEvent event = retractedEvent(105L, user, "DATA_USE_GRANT", grant, "CAPSULE_MATCH_INDEX", 1);
        handler.handle(event);

        Map<String, Map<String, Object>> rows = cleanupRows(event);
        assertEquals(OUTCOME_SUCCESS, rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("outcome"));
        assertEquals(1, ((Number) rows.get(ASSET_CAPSULE_MATCH_VECTOR).get("affected_count")).intValue());
        assertEquals(0, embeddingCount(capsule));
        verify(capsuleEmbeddings, times(1)).retireForCapsule(capsule);
    }

    // ---------- fixtures and helpers ----------

    /** Independent large test-user id segment (94xxxxxxx) so seeds never collide with other suites. */
    private void seedUser(long id) {
        jdbc.update("""
                MERGE INTO tb_user (id, username, password_hash, nickname, role, status, account_kind)
                KEY (id) VALUES (?, ?, 'cp15-test-hash', 'cp15', 'USER', 'ACTIVE', 'HUMAN')
                """, id, "cp15-" + id);
    }

    private long seedDevice(long userId) {
        jdbc.update("""
                INSERT INTO tb_device_registration (user_id, installation_id, platform, transport,
                  app_version, locale, timezone, enabled, revoked, last_seen_at, created_at, updated_at)
                VALUES (?, ?, 'ANDROID', 'FCM', '1.0.0', 'zh-CN', 'Asia/Shanghai',
                  TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, "install-cp15-" + userId);
        Long id = jdbc.queryForObject(
                "SELECT id FROM tb_device_registration WHERE installation_id=?",
                Long.class, "install-cp15-" + userId);
        assertNotNull(id);
        return id;
    }

    private void seedPush(long userId, long deviceId, String status) {
        jdbc.update("""
                INSERT INTO tb_push_delivery (user_id, device_id, wake_intent_id, title, body,
                  deep_link, status, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, NULL, 'cp15 wake title', 'cp15 wake body',
                  'innercosmos://aurora/wake/cp15', ?, CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, deviceId, status);
    }

    private void seedCapsule(long capsuleId, long ownerUserId) {
        jdbc.update("""
                INSERT INTO tb_echo_capsule (id, owner_user_id, capsule_type, pseudonym, intro,
                  visibility_status, is_public, created_at, updated_at)
                VALUES (?, ?, 'USER', 'cp15', 'cp15 capsule', 'ARCHIVED', FALSE,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, capsuleId, ownerUserId);
    }

    private void seedEmbeddings(long capsuleId, int rows) {
        for (int i = 0; i < rows; i++) {
            jdbc.update("""
                    INSERT INTO tb_capsule_embedding (capsule_id, model_name, model_version,
                      content_hash, dimensions, embedding_json, status, created_at, updated_at)
                    VALUES (?, 'cp15-model', 'v1', ?, 1, '[0.1]', 'ACTIVE',
                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, capsuleId, "cp15-hash-" + capsuleId + "-" + i);
        }
    }

    /** Same field set the outbox writer serializes for data.retracted.v1. */
    private OutboxEvent retractedEvent(long receiptId, long userId, String subjectType,
                                       long subjectId, String derivativeType) throws Exception {
        return retractedEvent(receiptId, userId, subjectType, subjectId, derivativeType, 1);
    }

    private OutboxEvent retractedEvent(long receiptId, long userId, String subjectType,
                                       long subjectId, String derivativeType, int affectedCount)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiptId", receiptId);
        body.put("userId", userId);
        body.put("subjectType", subjectType);
        body.put("subjectId", subjectId);
        body.put("derivativeType", derivativeType);
        body.put("action", "ERASED");
        body.put("affectedCount", affectedCount);
        return new OutboxEvent(0L, UUID.randomUUID(), "dedup-cp15:" + receiptId, "data-retraction",
                String.valueOf(receiptId), DataRetractedOutboxWriter.EVENT_TYPE,
                DataRetractedOutboxWriter.SCHEMA_VERSION, JSON.writeValueAsString(body), null,
                0, null, null);
    }

    private Map<String, Map<String, Object>> cleanupRows(OutboxEvent event) {
        return jdbc.query("""
                SELECT asset_key, outcome, affected_count, detail
                FROM tb_retraction_cleanup_result
                WHERE outbox_event_id = ?
                """, rs -> {
            Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("outcome", rs.getString("outcome"));
                row.put("affected_count", rs.getInt("affected_count"));
                row.put("detail", rs.getString("detail"));
                rows.put(rs.getString("asset_key"), row);
            }
            return rows;
        }, event.eventId().toString());
    }

    private int pushCount(long userId, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_push_delivery WHERE user_id=? AND status=?",
                Integer.class, userId, status);
        return count == null ? 0 : count;
    }

    private int embeddingCount(long capsuleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_capsule_embedding WHERE capsule_id=?",
                Integer.class, capsuleId);
        return count == null ? 0 : count;
    }
}
