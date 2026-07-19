package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.DataRetractionReceipt;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.mapper.DataRetractionReceiptMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A5 right-to-erasure: deleting an account must purge the compiled derivatives that have no FK cascade
 * — the capsule matching vectors, memory retrieval vectors and the data-retraction audit trail — not
 * just the source rows. Before this, account deletion orphaned all three.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-erasure;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
class AccountDeletionErasureTest {
    @Autowired UserService userService;
    @Autowired DataRetractionReceiptService receiptService;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired CapsuleEmbeddingMapper capsuleEmbeddingMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryEmbeddingMapper memoryEmbeddingMapper;
    @Autowired DataRetractionReceiptMapper receiptMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deletingAnAccountPurgesCompiledDerivativesAndAuditTrail() {
        Long user = createUser("erasure_owner");

        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = user; capsule.capsuleType = "USER_CAPSULE"; capsule.pseudonym = "Echo";
        capsule.intro = "woven"; capsule.publicTags = "[]"; capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC"; capsule.simulatorOnly = false; capsule.echoEnergy = .5;
        capsuleMapper.insert(capsule);
        capsuleEmbeddingMapper.insert(embedding(capsule.id));

        MemoryCard card = new MemoryCard();
        card.userId = user; card.title = "t"; card.summary = "s"; card.memoryType = "COGNITION";
        card.memoryLayer = "EPISODIC"; card.emotionTags = "[]"; card.keywordTags = "[]";
        card.peopleTags = "[]"; card.status = "ACTIVE"; card.versionNo = 1; card.consentScope = "AURORA_PRIVATE";
        memoryMapper.insert(card);
        MemoryEmbedding me = new MemoryEmbedding();
        me.userId = user; me.memoryId = card.id; me.modelName = "m"; me.modelVersion = "v1";
        me.sourceVersion = 1; me.taskScope = "GENERAL"; me.dimensions = 8; me.embeddingJson = "[0,0,0,0,0,0,0,1]";
        me.status = "ACTIVE";
        memoryEmbeddingMapper.insert(me);

        receiptService.record(user, DataRetractionReceiptService.SUBJECT_CAPSULE, capsule.id,
                DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.ACTION_ERASED, 1, "test");

        // sanity: all present
        assertEquals(1, capsuleEmbeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>().eq("capsule_id", capsule.id)));
        assertEquals(1, memoryEmbeddingMapper.selectCount(new QueryWrapper<MemoryEmbedding>().eq("user_id", user)));
        assertEquals(1, receiptMapper.selectCount(new QueryWrapper<DataRetractionReceipt>().eq("user_id", user)));

        userService.deleteAccount(user);

        assertEquals(0, capsuleEmbeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>().eq("capsule_id", capsule.id)),
                "capsule matching vector must be erased on account deletion");
        assertEquals(0, memoryEmbeddingMapper.selectCount(new QueryWrapper<MemoryEmbedding>().eq("user_id", user)),
                "memory retrieval vectors must be erased on account deletion");
        assertEquals(0, receiptMapper.selectCount(new QueryWrapper<DataRetractionReceipt>().eq("user_id", user)),
                "data-retraction audit trail must be erased on account deletion");
        assertEquals(0, capsuleMapper.selectCount(new QueryWrapper<EchoCapsule>().eq("owner_user_id", user)));
    }

    private Long createUser(String username) {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
    }

    private static CapsuleEmbedding embedding(Long capsuleId) {
        CapsuleEmbedding e = new CapsuleEmbedding();
        e.capsuleId = capsuleId; e.modelName = "m"; e.modelVersion = "v1"; e.contentHash = "h";
        e.dimensions = 8; e.embeddingJson = "[0,0,0,0,0,0,0,1]"; e.status = "ACTIVE";
        return e;
    }
}
