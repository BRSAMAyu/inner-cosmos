package com.innercosmos.service;

import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.task.scheduling.enabled=false", "llm.provider=mock"})
class DataUseGrantServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CapsuleService capsuleService;
    @Autowired DataUseGrantService grantService;
    @Autowired MemoryLifecycleService memoryLifecycleService;

    @Test
    @Transactional
    void normalAndSimulatorAuthorizationsCreateDifferentVersionBoundPurposes() {
        Long owner = seedUser("grant-purpose");
        Long normalMemory = seedMemory(owner, "普通授权", "AURORA_PRIVATE");
        Long simulatorMemory = seedMemory(owner, "模拟器授权", "SIMULATOR_AUTHORIZED");

        EchoCapsule normal = create(owner, normalMemory, false);
        List<DataUseGrant> normalGrants = grantService.history(owner, normal.id);
        assertEquals(Set.of("CAPSULE_RUNTIME", "PROVIDER_EGRESS"), purposes(normalGrants));
        assertTrue(normalGrants.stream().allMatch(g -> "ACTIVE".equals(g.status)
                && g.resourceVersion == 1 && g.grantVersion == 1));

        EchoCapsule simulator = create(owner, simulatorMemory, true);
        List<DataUseGrant> simulatorGrants = grantService.history(owner, simulator.id);
        assertEquals(Set.of("CAPSULE_SIMULATOR", "PROVIDER_EGRESS"), purposes(simulatorGrants));
        assertFalse(purposes(simulatorGrants).contains("CAPSULE_RUNTIME"));
    }

    @Test
    @Transactional
    void ownerRevocationDelistsCapsuleAndPreservesVersionedAuditHistory() {
        Long owner = seedUser("grant-revoke");
        Long memory = seedMemory(owner, "可撤销授权", "AURORA_PRIVATE");
        EchoCapsule capsule = create(owner, memory, false);
        capsuleService.updateVisibility(owner, capsule.id, "PUBLIC", true);
        DataUseGrant providerGrant = grantService.history(owner, capsule.id).stream()
                .filter(g -> "PROVIDER_EGRESS".equals(g.purpose)).findFirst().orElseThrow();

        grantService.revoke(owner, capsule.id, providerGrant.id, "owner changed consent");

        DataUseGrant revoked = grantService.history(owner, capsule.id).stream()
                .filter(g -> g.id.equals(providerGrant.id)).findFirst().orElseThrow();
        assertEquals("REVOKED", revoked.status);
        assertNotNull(revoked.revokedAt);
        assertEquals("owner changed consent", revoked.revokeReason);
        EchoCapsule delisted = capsuleService.getOwnedCapsule(owner, capsule.id);
        assertFalse(delisted.isPublic);
        assertEquals("NEEDS_REVIEW", delisted.visibilityStatus);
        assertThrows(BusinessException.class,
                () -> capsuleService.updateVisibility(owner, capsule.id, "PUBLIC", true));
    }

    @Test
    @Transactional
    void recompileCreatesParentedGrantVersionsAndRevokesThePriorGeneration() {
        Long owner = seedUser("grant-version");
        Long memory = seedMemory(owner, "版本化授权", "AURORA_PRIVATE");
        EchoCapsule capsule = create(owner, memory, false);
        List<DataUseGrant> v1 = grantService.history(owner, capsule.id);

        capsuleService.recompileGenome(owner, capsule.id, List.of(memory));

        List<DataUseGrant> history = grantService.history(owner, capsule.id);
        assertEquals(4, history.size());
        assertEquals(2, history.stream().filter(g -> "ACTIVE".equals(g.status) && g.grantVersion == 2).count());
        assertEquals(2, history.stream().filter(g -> "REVOKED".equals(g.status) && g.grantVersion == 1).count());
        Set<Long> v1Ids = v1.stream().map(g -> g.id).collect(Collectors.toSet());
        assertTrue(history.stream().filter(g -> g.grantVersion == 2)
                .allMatch(g -> g.parentGrantId != null && v1Ids.contains(g.parentGrantId)));
    }

    @Test
    @Transactional
    void forgettingRevokesButDoesNotEraseGrantAuditTombstones() {
        Long owner = seedUser("grant-forget");
        Long memory = seedMemory(owner, "必须消失的授权内容", "AURORA_PRIVATE");
        EchoCapsule capsule = create(owner, memory, false);

        memoryLifecycleService.execute(owner, new MemoryOperationCommand(
                "FORGET", memory, null, null, null, null, "owner forget", null, null));

        List<DataUseGrant> history = grantService.history(owner, capsule.id);
        assertEquals(2, history.size(), "purpose audit tombstones survive without carrying memory content");
        assertTrue(history.stream().allMatch(g -> "REVOKED".equals(g.status) && g.revokedAt != null));
        assertTrue(history.stream().noneMatch(g -> String.valueOf(g).contains("必须消失的授权内容")));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_authorized_memory_ref WHERE memory_card_id = ?", Integer.class, memory));
    }

    private EchoCapsule create(Long owner, Long memory, boolean simulator) {
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        return simulator ? capsuleService.createSimulatorCapsule(owner, request)
                : capsuleService.createFromMemory(owner, request);
    }

    private Set<String> purposes(List<DataUseGrant> grants) {
        return grants.stream().map(g -> g.purpose).collect(Collectors.toSet());
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, 'hash', 'USER', 'ACTIVE')", username);
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedMemory(Long owner, String summary, String scope) {
        jdbc.update("INSERT INTO tb_memory_card (user_id,title,summary,status,version_no,consent_scope) VALUES (?,'授权记忆',?,'ACTIVE',1,?)",
                owner, summary, scope);
        return jdbc.queryForObject("SELECT id FROM tb_memory_card WHERE user_id=? ORDER BY id DESC LIMIT 1", Long.class, owner);
    }
}
