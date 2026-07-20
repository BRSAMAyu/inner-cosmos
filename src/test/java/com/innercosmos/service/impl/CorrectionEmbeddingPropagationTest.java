package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.DataRetractionReceipt;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.mapper.DataRetractionReceiptMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.UserCorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * G5 PROFILE-PROPAGATION: an explicit user correction that supersedes a memory must (a) invalidate
 * that memory's retrieval embedding and (b) — when it delists an authorized capsule — erase that
 * capsule's matching vector, both with auditable receipts. Before this the correction path touched
 * neither derivative (memory embeddings relied only on a query-time status join, and the capsule
 * matching vector had no invalidation hook on this path at all).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:correction-propagation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
@Import(CapsuleEmbeddingIndexServiceIntegrationTest.FakeEmbeddingConfig.class)
class CorrectionEmbeddingPropagationTest {
    @Autowired UserCorrectionService correctionService;
    @Autowired CapsuleEmbeddingIndexService index;
    @Autowired CapsuleEmbeddingMapper capsuleEmbeddingMapper;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryEmbeddingMapper memoryEmbeddingMapper;
    @Autowired AuthorizedMemoryRefMapper refMapper;
    @Autowired DataRetractionReceiptMapper receiptMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void confirmingAMemoryCorrectionInvalidatesTheMemoryVectorAndErasesTheDelistedCapsuleVector() {
        Long owner = createOwner("correction_prop_owner");
        MemoryCard card = insertMemory(owner, "老板批评了我", "我觉得自己什么都做不好");
        insertActiveMemoryEmbedding(owner, card.id);
        EchoCapsule capsule = publicCapsule(owner, "Woven echo", "woven from a memory", "[\"life\"]");
        capsuleMapper.insert(capsule);
        linkAuthorized(capsule.id, card.id);
        assertEquals(1, index.rebuildMissing(10).indexed());

        correctionService.confirm(owner, new CorrectionCommand(
                "MEMORY_CARD", card.id, null, null,
                "我只是那天状态不好，并不是什么都做不好", "user corrected the inference"));

        // Memory retrieval vector invalidated (soft, since the memory still exists as SUPERSEDED).
        assertEquals(0, memoryEmbeddingMapper.selectCount(new QueryWrapper<MemoryEmbedding>()
                .eq("memory_id", card.id).eq("status", "ACTIVE")), "the superseded memory's ACTIVE vector must be gone");
        assertEquals(1, memoryEmbeddingMapper.selectCount(new QueryWrapper<MemoryEmbedding>()
                .eq("memory_id", card.id).eq("status", "STALE")));

        // Capsule matching vector physically erased because the capsule was delisted for review.
        assertEquals(0, capsuleEmbeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id)), "the delisted capsule's matching vector must be erased");

        List<DataRetractionReceipt> receipts = receiptMapper.selectList(
                new QueryWrapper<DataRetractionReceipt>().eq("user_id", owner));
        assertEquals(1, receipts.stream().filter(r ->
                DataRetractionReceiptService.DERIVATIVE_MEMORY_EMBEDDING.equals(r.derivativeType)
                && DataRetractionReceiptService.ACTION_CLEARED.equals(r.action)
                && card.id.equals(r.subjectId)).count(), "one MEMORY_EMBEDDING CLEARED receipt");
        assertEquals(1, receipts.stream().filter(r ->
                DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX.equals(r.derivativeType)
                && DataRetractionReceiptService.ACTION_ERASED.equals(r.action)
                && capsule.id.equals(r.subjectId)).count(), "one CAPSULE_MATCH_INDEX ERASED receipt");
    }

    private Long createOwner(String username) {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                username, "not-a-real-hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
    }

    private MemoryCard insertMemory(Long owner, String title, String summary) {
        MemoryCard card = new MemoryCard();
        card.userId = owner; card.title = title; card.summary = summary;
        card.memoryType = "COGNITION"; card.memoryLayer = "EPISODIC";
        card.emotionTags = "[]"; card.keywordTags = "[]"; card.peopleTags = "[]";
        card.status = "ACTIVE"; card.versionNo = 1; card.consentScope = "AURORA_PRIVATE";
        memoryMapper.insert(card);
        return card;
    }

    private void insertActiveMemoryEmbedding(Long owner, Long memoryId) {
        MemoryEmbedding row = new MemoryEmbedding();
        row.userId = owner; row.memoryId = memoryId;
        row.modelName = "capsule-contract-model"; row.modelVersion = "v1";
        row.sourceVersion = 1; row.taskScope = "GENERAL"; row.dimensions = 8;
        row.embeddingJson = "[0,0,0,0,0,0,0,1]"; row.status = "ACTIVE";
        memoryEmbeddingMapper.insert(row);
    }

    private void linkAuthorized(Long capsuleId, Long memoryId) {
        AuthorizedMemoryRef ref = new AuthorizedMemoryRef();
        ref.capsuleId = capsuleId; ref.memoryCardId = memoryId; ref.authorizationStatus = "AUTHORIZED";
        refMapper.insert(ref);
    }

    private static EchoCapsule publicCapsule(Long owner, String pseudonym, String intro, String tags) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = owner; capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = pseudonym; capsule.intro = intro; capsule.publicTags = tags;
        capsule.isPublic = true; capsule.visibilityStatus = "PUBLIC";
        capsule.simulatorOnly = false; capsule.echoEnergy = .5;
        return capsule;
    }
}
