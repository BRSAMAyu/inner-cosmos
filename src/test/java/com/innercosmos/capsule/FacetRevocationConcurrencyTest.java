package com.innercosmos.capsule;

import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.DataRetractionReceipt;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DataRetractionReceiptMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.DataDerivativeRegistry;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.PersonaChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-58 engineering companion to the facet-isolation evaluation: concurrent
 * facet-grant revocation versus concurrent probe reads on the same capsule.
 *
 * Fixture: one simulated user with three facets. WORK and HOBBY compile into
 * permanently isolated simulator capsules; the FAMILY facet is a real runtime
 * capsule (AURORA_PRIVATE memory, published PUBLIC) so the revocation races
 * against the actual visitor-facing read path instead of a stub. An ACTIVE
 * matching-vector row is seeded for the family capsule so derivative erasure is
 * observable rather than vacuous.
 *
 * 8 threads: one revoker revokes the facet's data-use grants
 * ({@code DataUseGrantServiceImpl.revoke} — the owner-grant path that fans out
 * to delisting, genome needs-review, matching-vector erasure and a retraction
 * receipt), while seven probers hammer the visitor runtime gate
 * ({@code PersonaChatService.create}) and sweep grants-then-capsule state.
 *
 * Asserted after the race:
 *   - probe zero hit: every post-revocation visitor probe is refused
 *   - no resurrection: repeated sweeps never see an ACTIVE grant, a public
 *     capsule or an ACTIVE genome again
 *   - registered derivatives invalidated: matching vector physically erased,
 *     genome no longer ACTIVE, and every retraction receipt names a derivative
 *     registered in {@link DataDerivativeRegistry}
 *   - no partial visibility: no sweep ever observed a REVOKED grant while the
 *     capsule was still publicly runnable (the revoke transaction publishes
 *     grant + delisting atomically)
 *   - blast radius: the two simulator facets' grants stay ACTIVE — revoking one
 *     facet never touches another facet's capsules.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:facet-revocation-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class FacetRevocationConcurrencyTest {

    private static final int THREADS = 8;
    private static final int MAX_PROBE_ITERATIONS = 80;

    @Autowired JdbcTemplate jdbc;
    @Autowired CapsuleService capsuleService;
    @Autowired CapsuleGenomeService genomeService;
    @Autowired DataUseGrantService grantService;
    @Autowired PersonaChatService personaChatService;
    @Autowired DataRetractionReceiptMapper receiptMapper;

    @Test
    void concurrentFacetGrantRevocationNeverExposesPartialOrResurrectedState() throws Exception {
        long owner = seedUser("cp58-revoke-owner");
        long visitor = seedUser("cp58-revoke-visitor");

        // WORK / HOBBY: permanently isolated simulator facets.
        Long workMemory = seedMemory(owner, "职场侧面", "职场侧面的内部记录", "SIMULATOR_AUTHORIZED");
        Long hobbyMemory = seedMemory(owner, "兴趣侧面", "兴趣侧面的内部记录", "SIMULATOR_AUTHORIZED");
        EchoCapsule workCapsule = simulatorCapsule(owner, workMemory, "职场侧面");
        EchoCapsule hobbyCapsule = simulatorCapsule(owner, hobbyMemory, "兴趣侧面");

        // FAMILY: the facet under test — a real published runtime capsule.
        Long familyMemory = seedMemory(owner, "家庭侧面",
                "家庭侧面的聚会记录", "AURORA_PRIVATE");
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "家庭侧面";
        request.memoryIds = List.of(familyMemory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule familyCapsule = capsuleService.createFromMemory(owner, request);
        capsuleService.updateVisibility(owner, familyCapsule.id, "PUBLIC", true);
        EchoCapsule published = capsuleService.getOwnedCapsule(owner, familyCapsule.id);
        assertTrue(Boolean.TRUE.equals(published.isPublic), "fixture: family facet starts published");

        // Seed an ACTIVE matching-vector derivative so erasure is observable.
        jdbc.update("""
                INSERT INTO tb_capsule_embedding
                    (capsule_id, model_name, model_version, content_hash, dimensions, embedding_json, status)
                VALUES (?, 'fixture-embed', 'v1', 'cp58-concurrency', 3, '[0.1,0.2,0.3]', 'ACTIVE')
                """, familyCapsule.id);

        // Baseline: the visitor runtime gate is open before revocation.
        assertNotNull(personaChatService.create(visitor, familyCapsule.id),
                "fixture: visitor probes must succeed before revocation");

        List<DataUseGrant> facetGrants = grantService.history(owner, familyCapsule.id);
        assertEquals(2, facetGrants.size(), "CAPSULE_RUNTIME + PROVIDER_EGRESS for the one family memory");
        List<Long> facetGrantIds = facetGrants.stream().map(g -> g.id).toList();

        AtomicBoolean revocationDone = new AtomicBoolean(false);
        AtomicInteger probeAttempts = new AtomicInteger();
        AtomicInteger probeHitsBeforeRevoke = new AtomicInteger();  // legal successes pre-commit
        AtomicInteger probeSuccessAfterAnyRevoke = new AtomicInteger(); // resurrection/partial exposure
        List<String> violations = new ArrayList<>(); // guarded by synchronized below
        Object violationsLock = new Object();

        CountDownLatch start = new CountDownLatch(THREADS);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        // Thread 0 — the revoker: revoke both family-facet grants sequentially
        // (each revoke is its own transaction, exactly like two owner actions).
        pool.submit(() -> {
            start.countDown();
            await(start);
            try {
                for (Long grantId : facetGrantIds) {
                    grantService.revoke(owner, familyCapsule.id, grantId, "cp58 facet revocation race");
                }
            } catch (Throwable t) {
                synchronized (violationsLock) {
                    violations.add("revoker threw: " + t);
                }
            } finally {
                revocationDone.set(true);
                done.countDown();
            }
        });

        // Threads 1..7 — probers: visitor runtime gate + grants-then-capsule sweep.
        for (int i = 1; i < THREADS; i++) {
            pool.submit(() -> {
                start.countDown();
                await(start);
                try {
                    while (!revocationDone.get() && probeAttempts.get() < MAX_PROBE_ITERATIONS * (THREADS - 1)) {
                        // Read order is load-bearing for decidability:
                        // (1) grants FIRST — a REVOKED row visible here means the
                        //     delisting transaction already committed before this sweep;
                        // (2) then the visitor gate — a success is only a genuine
                        //     resurrection if the revocation was already visible
                        //     BEFORE the attempt (an attempt that raced the commit is legal);
                        // (3) then the capsule row — still-public here after a visible
                        //     REVOKED grant is a genuine partial-visibility window.
                        List<DataUseGrant> grants = grantService.history(owner, familyCapsule.id);
                        boolean revokedAlreadyVisible = grants.stream()
                                .anyMatch(g -> "REVOKED".equals(g.status));
                        boolean gateOpen;
                        try {
                            gateOpen = personaChatService.create(visitor, familyCapsule.id) != null;
                        } catch (BusinessException expectedWhenDelisted) {
                            gateOpen = false;
                        }
                        probeAttempts.incrementAndGet();
                        EchoCapsule capsule = capsuleService.getOwnedCapsule(owner, familyCapsule.id);
                        boolean stillPublic = capsule != null
                                && (Boolean.TRUE.equals(capsule.isPublic)
                                    || "PUBLIC".equals(capsule.visibilityStatus));
                        if (revokedAlreadyVisible && stillPublic) {
                            synchronized (violationsLock) {
                                violations.add("partial visibility: grant revoked while capsule still public");
                            }
                        }
                        if (gateOpen) {
                            if (revokedAlreadyVisible) {
                                probeSuccessAfterAnyRevoke.incrementAndGet();
                            } else {
                                probeHitsBeforeRevoke.incrementAndGet();
                            }
                        }
                    }
                } catch (Throwable t) {
                    synchronized (violationsLock) {
                        violations.add("prober threw: " + t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(120, TimeUnit.SECONDS), "concurrent phase must terminate");
        pool.shutdownNow();

        // Post-revocation probe wave: zero hits, all refused.
        int postRefusals = 0;
        for (int i = 0; i < 20; i++) {
            assertThrows(BusinessException.class,
                    () -> personaChatService.create(visitor, familyCapsule.id),
                    "post-revocation visitor probe must be refused (probe zero hit)");
            postRefusals++;
        }

        // No resurrection across repeated sweeps.
        for (int sweep = 0; sweep < 3; sweep++) {
            List<DataUseGrant> grants = grantService.history(owner, familyCapsule.id);
            assertEquals(List.of(), grants.stream()
                            .filter(g -> "ACTIVE".equals(g.status)).toList(),
                    "revoked facet grants must never come back ACTIVE");
            grants.forEach(g -> {
                assertEquals("REVOKED", g.status);
                assertNotNull(g.revokedAt);
            });
            assertFalse(grantService.authorizationsValid(
                    capsuleService.getOwnedCapsule(owner, familyCapsule.id), java.util.Set.of(familyMemory)),
                    "authorizations must be invalid after facet revocation");
            EchoCapsule capsule = capsuleService.getOwnedCapsule(owner, familyCapsule.id);
            assertFalse(Boolean.TRUE.equals(capsule.isPublic), "capsule must stay delisted");
            assertFalse("PUBLIC".equals(capsule.visibilityStatus), "capsule must stay delisted");
            assertNull(genomeService.current(familyCapsule.id),
                    "genome derivative must no longer be ACTIVE (needs review)");
            assertEquals(0, embeddingRows(familyCapsule.id),
                    "matching-vector derivative must be physically erased");
            if (sweep < 2) Thread.sleep(50);
        }

        // Authorization refs for the revoked facet are withdrawn.
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_authorized_memory_ref WHERE capsule_id = ? "
                        + "AND authorization_status = 'AUTHORIZED'",
                Integer.class, familyCapsule.id));

        // Every retraction receipt names a registered derivative that is now invalidated.
        List<DataRetractionReceipt> receipts = receiptMapper.selectList(
                new QueryWrapper<DataRetractionReceipt>()
                        .eq("subject_type", DataRetractionReceiptService.SUBJECT_DATA_USE_GRANT)
                        .in("subject_id", facetGrantIds));
        assertFalse(receipts.isEmpty(), "revocation must record retraction receipts");
        for (DataRetractionReceipt receipt : receipts) {
            assertTrue(DataDerivativeRegistry.isRegisteredDerivative(receipt.derivativeType),
                    "receipt derivative " + receipt.derivativeType + " must be registry-listed");
            assertEquals(DataRetractionReceiptService.ACTION_ERASED, receipt.action);
        }
        assertTrue(receipts.stream().anyMatch(r -> DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX
                .equals(r.derivativeType)));

        // Blast radius: the other facets' simulator capsules keep their ACTIVE grants.
        for (EchoCapsule other : List.of(workCapsule, hobbyCapsule)) {
            List<DataUseGrant> otherGrants = grantService.history(owner, other.id);
            assertFalse(otherGrants.isEmpty());
            assertTrue(otherGrants.stream().allMatch(g -> "ACTIVE".equals(g.status)),
                    "revoking one facet must not touch another facet's grants");
        }

        System.out.println("[CP-58-revocation] probesDuringRace=" + probeAttempts.get()
                + " legalHitsBeforeRevoke=" + probeHitsBeforeRevoke.get()
                + " successAfterAnyRevoke=" + probeSuccessAfterAnyRevoke.get()
                + " postRefusals=" + postRefusals
                + " receipts=" + receipts.size()
                + " violations=" + violations.size());

        assertEquals(0, probeSuccessAfterAnyRevoke.get(),
                "a visitor probe succeeded after a grant revocation was already visible");
        assertTrue(violations.isEmpty(), "concurrency violations: " + violations);
    }

    // ---------------------------------------------------------------------

    private EchoCapsule simulatorCapsule(Long owner, Long memoryId, String pseudonym) {
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = pseudonym;
        request.memoryIds = List.of(memoryId);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createSimulatorCapsule(owner, request);
        assertEquals(Boolean.TRUE, capsule.simulatorOnly);
        return capsule;
    }

    private int embeddingRows(Long capsuleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_capsule_embedding WHERE capsule_id = ?", Integer.class, capsuleId);
        return count == null ? 0 : count;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedMemory(Long owner, String title, String summary, String scope) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, ?, ?, 'ACTIVE', 1, ?)
                """, owner, title, summary, scope);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }
}
