package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.DataRetractionReceipt;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.mapper.DataRetractionReceiptMapper;
import com.innercosmos.mapper.DataUseGrantMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.MemoryLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G5 PROFILE-PROPAGATION: a data-rights action (memory forget, capsule archive, consent-grant
 * revocation) must physically erase the capsule's compiled matching vector immediately — not leave
 * it ACTIVE until the next rebuild — and must leave an auditable, sensitive-payload-free receipt.
 * These are the regressions that pin the previously-missing propagation from source retraction to
 * the tb_capsule_embedding matching index.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capsule-retire;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
@Import(CapsuleEmbeddingRetirementTest.FakeEmbeddingConfig.class)
class CapsuleEmbeddingRetirementTest {
    @Autowired CapsuleEmbeddingIndexService index;
    @Autowired CapsuleEmbeddingMapper embeddingMapper;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired CapsuleService capsuleService;
    @Autowired DataUseGrantService grantService;
    @Autowired DataUseGrantMapper grantMapper;
    @Autowired MemoryLifecycleService lifecycleService;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired AuthorizedMemoryRefMapper refMapper;
    @Autowired DataRetractionReceiptMapper receiptMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void retireForCapsuleErasesAllRowsIsIdempotentAndAllowsAFreshRebuild() {
        Long owner = createOwner("retire_direct_owner");
        EchoCapsule capsule = publicCapsule(owner, "Course echo", "course deliverable", "[\"study\"]");
        capsuleMapper.insert(capsule);

        assertEquals(1, index.rebuildMissing(10).indexed());
        assertEquals(1, activeCount(capsule.id));

        int erased = index.retireForCapsule(capsule.id);
        assertEquals(1, erased, "the one ACTIVE derived vector must be erased");
        assertEquals(0, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id)), "no vector row may survive an erasure");

        assertEquals(0, index.retireForCapsule(capsule.id), "erasure is idempotent");

        // A future re-consent must be able to rebuild a fresh vector — the erased content hash is
        // no longer occupying the uniqueness slot.
        assertEquals(1, index.rebuildMissing(10).indexed());
        assertEquals(1, activeCount(capsule.id));
    }

    @Test
    void archivingACapsuleErasesItsMatchingVectorAndWritesAReceipt() {
        Long owner = createOwner("retire_archive_owner");
        EchoCapsule capsule = publicCapsule(owner, "Archive me", "archive text", "[\"study\"]");
        capsuleMapper.insert(capsule);
        assertEquals(1, index.rebuildMissing(10).indexed());

        capsuleService.archiveCapsule(owner, capsule.id);

        assertEquals(0, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id)), "archived capsule must not keep a matching vector");
        DataRetractionReceipt receipt = onlyReceipt(owner);
        assertEquals(DataRetractionReceiptService.SUBJECT_CAPSULE, receipt.subjectType);
        assertEquals(capsule.id, receipt.subjectId);
        assertEquals(DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX, receipt.derivativeType);
        assertEquals(DataRetractionReceiptService.ACTION_ERASED, receipt.action);
        assertEquals(1, receipt.affectedCount);
    }

    @Test
    void revokingADataUseGrantErasesTheMatchingVectorAndWritesAReceipt() {
        Long owner = createOwner("retire_revoke_owner");
        MemoryCard card = insertMemory(owner, "seed", "seed summary");
        EchoCapsule capsule = publicCapsule(owner, "Grant echo", "grant text", "[\"study\"]");
        capsuleMapper.insert(capsule);
        DataUseGrant grant = insertGrant(owner, capsule.id, card.id);
        assertEquals(1, index.rebuildMissing(10).indexed());

        grantService.revoke(owner, capsule.id, grant.id, "owner revoked consent in test");

        assertEquals(0, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id)));
        DataRetractionReceipt receipt = onlyReceipt(owner);
        assertEquals(DataRetractionReceiptService.SUBJECT_DATA_USE_GRANT, receipt.subjectType);
        assertEquals(grant.id, receipt.subjectId);
        assertEquals(DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX, receipt.derivativeType);
        assertEquals(1, receipt.affectedCount);
    }

    @Test
    void forgettingASourceMemoryErasesDependentCapsuleVectorsAndWritesAReceipt() {
        Long owner = createOwner("retire_forget_owner");
        MemoryCard card = insertMemory(owner, "a hard week", "a private summary about a hard week");
        EchoCapsule capsule = publicCapsule(owner, "Woven echo", "woven from a memory", "[\"life\"]");
        capsuleMapper.insert(capsule);
        linkMemoryToCapsule(capsule.id, card.id);
        assertEquals(1, index.rebuildMissing(10).indexed());

        lifecycleService.execute(owner, new MemoryOperationCommand(
                "FORGET", card.id, null, null, null, null, "user asked to forget", null, null));

        assertEquals(0, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id)), "forgetting the source memory must erase the derived vector");
        DataRetractionReceipt receipt = onlyReceipt(owner);
        assertEquals(DataRetractionReceiptService.SUBJECT_MEMORY, receipt.subjectType);
        assertEquals(card.id, receipt.subjectId);
        assertEquals(DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX, receipt.derivativeType);
        assertEquals(1, receipt.affectedCount);
    }

    // ----- helpers -----

    private long activeCount(Long capsuleId) {
        return embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsuleId).eq("status", "ACTIVE"));
    }

    private DataRetractionReceipt onlyReceipt(Long owner) {
        List<DataRetractionReceipt> rows = receiptMapper.selectList(
                new QueryWrapper<DataRetractionReceipt>().eq("user_id", owner));
        assertEquals(1, rows.size(), "exactly one retraction receipt expected");
        return rows.get(0);
    }

    private Long createOwner(String username) {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                username, "not-a-real-hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
    }

    private MemoryCard insertMemory(Long owner, String title, String summary) {
        MemoryCard card = new MemoryCard();
        card.userId = owner;
        card.title = title;
        card.summary = summary;
        card.memoryType = "COGNITION";
        card.memoryLayer = "EPISODIC";
        card.emotionTags = "[]"; card.keywordTags = "[]"; card.peopleTags = "[]";
        card.status = "ACTIVE"; card.versionNo = 1; card.consentScope = "AURORA_PRIVATE";
        memoryMapper.insert(card);
        return card;
    }

    private DataUseGrant insertGrant(Long owner, Long capsuleId, Long memoryId) {
        DataUseGrant grant = new DataUseGrant();
        grant.ownerUserId = owner;
        grant.resourceType = "MEMORY_CARD";
        grant.resourceId = memoryId;
        grant.resourceVersion = 1;
        grant.purpose = "CAPSULE_RUNTIME";
        grant.consumerType = "ECHO_CAPSULE";
        grant.consumerId = capsuleId;
        grant.grantVersion = 1;
        grant.status = "ACTIVE";
        grant.consentSource = "OWNER_EXPLICIT_CAPSULE_SELECTION";
        grant.grantedAt = LocalDateTime.now();
        grantMapper.insert(grant);
        return grant;
    }

    private void linkMemoryToCapsule(Long capsuleId, Long memoryId) {
        AuthorizedMemoryRef ref = new AuthorizedMemoryRef();
        ref.capsuleId = capsuleId;
        ref.memoryCardId = memoryId;
        ref.authorizationStatus = "AUTHORIZED";
        refMapper.insert(ref);
    }

    private static EchoCapsule publicCapsule(Long owner, String pseudonym, String intro, String tags) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = owner;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = pseudonym;
        capsule.intro = intro;
        capsule.publicTags = tags;
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        capsule.simulatorOnly = false;
        capsule.echoEnergy = .5;
        return capsule;
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean @Primary
        FakeEmbeddingClient fakeEmbeddingClient() {
            return new FakeEmbeddingClient();
        }
    }

    static class FakeEmbeddingClient implements MemoryEmbeddingClient {
        final AtomicInteger calls = new AtomicInteger();

        public boolean available() { return true; }
        public String modelName() { return "capsule-retire-model"; }
        public String modelVersion() { return "v1"; }
        public int dimensions() { return 8; }

        public float[] embed(String text) {
            calls.incrementAndGet();
            float[] vector = new float[8];
            vector[Math.floorMod(text.hashCode(), 8)] = 1;
            return vector;
        }
    }
}
