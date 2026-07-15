package com.innercosmos.service;

import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class CapsuleGenomeServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CapsuleService capsuleService;
    @Autowired CapsuleGenomeService genomeService;
    @Autowired PersonaChatService personaChatService;

    @Test
    @Transactional
    void versionChainRequiresReviewBeforeRepublishAndWithdrawsSelectedVersion() {
        Long owner = seedUser("genome-owner");
        Long stranger = seedUser("genome-stranger");
        String privateSummary = "不应进入授权快照的私人原文-" + System.nanoTime();
        Long memoryId = seedMemory(owner, privateSummary, "AURORA_PRIVATE");

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "雨后回声";
        request.intro = "在变化中保持温柔";
        request.memoryIds = List.of(memoryId);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        CapsuleGenomeVersion v1 = genomeService.current(capsule.id);
        assertNotNull(v1);
        assertEquals(1, v1.versionNo);
        assertEquals("ACTIVE", v1.status);
        assertTrue(v1.authorizationSnapshotJson.contains("\"memoryId\":" + memoryId));
        assertFalse(v1.authorizationSnapshotJson.contains(privateSummary));
        assertEquals(v1.id, capsuleService.getOwnedCapsule(owner, capsule.id).activeGenomeVersionId);
        assertThrows(BusinessException.class, () -> genomeService.history(stranger, capsule.id));

        genomeService.markNeedsReview(capsule.id, "source memory corrected");
        assertNull(genomeService.current(capsule.id));
        assertThrows(BusinessException.class,
                () -> capsuleService.updateVisibility(owner, capsule.id, "PUBLIC", true));

        CapsuleGenomeVersion v2 = capsuleService.recompileGenome(owner, capsule.id, List.of(memoryId));
        assertEquals(2, v2.versionNo);
        assertEquals(v1.id, v2.parentVersionId);
        assertEquals("ACTIVE", v2.status);
        assertEquals(List.of(2, 1), genomeService.history(owner, capsule.id).stream()
                .map(version -> version.versionNo).toList());
        EchoCapsule published = capsuleService.updateVisibility(owner, capsule.id, "PUBLIC", true);
        assertTrue(published.isPublic);
        assertEquals("PUBLIC", published.visibilityStatus);
        assertNotNull(personaChatService.create(stranger, capsule.id),
                "historical withdrawn references must not block the newly authorized Genome");

        genomeService.markNeedsReview(capsule.id, "second review");
        capsuleService.archiveCapsule(owner, capsule.id);
        CapsuleGenomeVersion latest = genomeService.history(owner, capsule.id).getFirst();
        assertEquals(v2.id, latest.id);
        assertEquals("WITHDRAWN", latest.status);
        assertNull(genomeService.current(capsule.id));
    }

    @Test
    @Transactional
    void recompileRejectsMemoryWithoutCapsuleConsentAndPreservesPriorVersion() {
        Long owner = seedUser("genome-consent");
        Long allowed = seedMemory(owner, "可授权摘要", "AURORA_PRIVATE");
        Long localOnly = seedMemory(owner, "仅本地摘要", "LOCAL_ONLY");
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(allowed);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleGenomeVersion original = genomeService.current(capsule.id);

        assertThrows(BusinessException.class,
                () -> capsuleService.recompileGenome(owner, capsule.id, List.of(localOnly)));

        CapsuleGenomeVersion stillActive = genomeService.current(capsule.id);
        assertNotNull(stillActive);
        assertEquals(original.id, stillActive.id);
        assertEquals(1, genomeService.history(owner, capsule.id).size());
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedMemory(Long owner, String summary, String consentScope) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, '测试记忆', ?, 'ACTIVE', 1, ?)
                """, owner, summary, consentScope);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }
}
