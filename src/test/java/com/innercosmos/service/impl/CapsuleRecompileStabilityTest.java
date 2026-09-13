package com.innercosmos.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.CapsuleService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CP-29 §2-10: a failed (re)compile must never replace the capsule's stable version, and
 * an authorization-list conflict must be EXPLAINED per memory, not waved off with one
 * generic line. The recompile path is transactional: a genome compile that throws rolls
 * the persona/visibility rewrite back with it; an ineligible memory aborts with the honest
 * reason for every refused id, leaving the previous authorization snapshot intact.
 */
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.social.group-review-pending-capacity=20"
})
class CapsuleRecompileStabilityTest {

    private static final AtomicLong USERS = new AtomicLong(95_800_000);

    @Autowired CapsuleService capsules;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired MemoryCardMapper memoryCardMapper;
    @SpyBean CapsuleGenomeService genomeService;
    @Autowired JdbcTemplate jdbc;

    /**
     * The CP-14 boundary guard fail-closes for requesters with no tb_user row, so the
     * fixture seeds a real ACTIVE user (same pattern as LetterReceiptPolicyControllerTest).
     */
    private long freshUser() {
        long id = USERS.incrementAndGet();
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_user WHERE id = ?", Integer.class, id);
        if (existing == null || existing == 0) {
            jdbc.update("INSERT INTO tb_user (id, username, password_hash, nickname, role, status) "
                            + "VALUES (?, ?, ?, ?, 'USER', 'ACTIVE')",
                    id, "cp29_" + id, "not-a-real-hash", "CP-29 稳定性负测");
        }
        return id;
    }

    private MemoryCard card(long user, String title, String status, String scope) {
        MemoryCard card = new MemoryCard();
        card.userId = user;
        card.title = title;
        card.summary = title + " 的摘要";
        card.memoryType = "FACT";
        card.memoryLayer = "CORE";
        card.consentScope = scope;
        card.versionNo = 1;
        card.visibilityLevel = "PRIVATE";
        card.status = status;
        card.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        memoryCardMapper.insert(card);
        return card;
    }

    /** A capsule whose current stable state is known, with one authorized healthy memory. */
    private EchoCapsule stableCapsule(long user, MemoryCard authorized) {
        com.innercosmos.dto.CapsuleCreateRequest request = new com.innercosmos.dto.CapsuleCreateRequest();
        request.pseudonym = "稳定版共鸣体";
        request.intro = "用于稳定性负测";
        request.memoryIds = List.of(authorized.id);
        EchoCapsule capsule = capsules.createFromMemory(user, request);
        return capsuleMapper.selectById(capsule.id);
    }

    @Test
    void genomeCompileFailureLeavesTheStableVersionUntouched() {
        long user = freshUser();
        MemoryCard healthy = card(user, "健康的记忆", "ACTIVE", "AURORA_PRIVATE");
        EchoCapsule stable = stableCapsule(user, healthy);
        String stablePersona = stable.personaPrompt;
        String stablePreview = stable.contextPreviewJson;
        assertNotNull(stablePersona, "fixture: the stable version carries a persona");

        // The genome write fails AFTER the capsule row rewrite -- the transaction must
        // roll the rewrite back with it (stable version preserved, not half-replaced).
        doThrow(new RuntimeException("genome store down"))
                .when(genomeService).compile(any(), anyList(), anyString());

        RuntimeException fired = assertThrows(RuntimeException.class,
                () -> capsules.recompileGenome(user, stable.id, List.of(healthy.id)));
        assertTrue(fired.getMessage() != null && fired.getMessage().contains("genome store down"),
                "the injected genome failure must be the one that fired, got: " + fired.getMessage());

        EchoCapsule after = capsuleMapper.selectById(stable.id);
        assertEquals(stablePersona, after.personaPrompt, "stable persona must survive");
        assertEquals(stablePreview, after.contextPreviewJson, "stable preview must survive");
        assertEquals("PRIVATE", after.visibilityStatus);
    }

    @Test
    void recompileConflictNamesEveryRefusedMemoryWithItsReason() {
        long user = freshUser();
        long stranger = freshUser();
        MemoryCard healthy = card(user, "健康", "ACTIVE", "AURORA_PRIVATE");
        EchoCapsule capsule = stableCapsule(user, healthy);
        MemoryCard retracted = card(user, "被撤回", "FORGOTTEN", "AURORA_PRIVATE");
        MemoryCard foreign = card(stranger, "别人的", "ACTIVE", "AURORA_PRIVATE");
        MemoryCard localOnly = card(user, "本地范围", "ACTIVE", "LOCAL_ONLY");

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> capsules.recompileGenome(user, capsule.id,
                        List.of(healthy.id, retracted.id, foreign.id, localOnly.id)));
        assertEquals(ErrorCode.BAD_REQUEST, conflict.code);
        assertTrue(conflict.getMessage().contains("记忆 " + retracted.id), "names the retracted id");
        assertTrue(conflict.getMessage().contains("记忆 " + foreign.id), "names the foreign id");
        assertTrue(conflict.getMessage().contains("记忆 " + localOnly.id), "names the scope-refused id");
        assertTrue(conflict.getMessage().contains("已撤回"), "explains the retraction reason");
        assertTrue(conflict.getMessage().contains("非本人"), "explains the ownership reason");
        assertTrue(conflict.getMessage().contains("LOCAL_ONLY"), "explains the consent-scope reason");
    }

    @Test
    void updateContextAuthorizationFailureLeavesThePreviousSnapshotIntact() {
        long user = freshUser();
        MemoryCard first = card(user, "第一段", "ACTIVE", "AURORA_PRIVATE");
        EchoCapsule capsule = stableCapsule(user, first);
        MemoryCard retracted = card(user, "第二段", "FORGOTTEN", "AURORA_PRIVATE");

        // Snapshot replacement refuses part of the list -> whole edit aborts; the capsule
        // keeps its previous authorized set (transactional, CP-29) and stays review-clean.
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> capsules.updateContext(user, capsule.id,
                        Map.of("authorizedMemoryIds", List.of(String.valueOf(first.id),
                                String.valueOf(retracted.id)))));
        assertEquals(ErrorCode.BAD_REQUEST, conflict.code);
        assertTrue(conflict.getMessage().contains("记忆 " + retracted.id));

        EchoCapsule after = capsuleMapper.selectById(capsule.id);
        assertEquals(capsule.authorizedMemoryIds, after.authorizedMemoryIds,
                "previous authorization snapshot must survive the refused edit");
    }
}
