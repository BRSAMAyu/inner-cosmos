package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capsule-embedding;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
@Import(CapsuleEmbeddingIndexServiceIntegrationTest.FakeEmbeddingConfig.class)
class CapsuleEmbeddingIndexServiceIntegrationTest {
    @Autowired CapsuleEmbeddingIndexService index;
    @Autowired CapsuleEmbeddingMapper embeddingMapper;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeEmbeddingClient client;

    @Test
    void onlineMatchingNeverIndexesAndRebuildRetiresStaleContent() {
        Long owner = createOwner("capsule_embedding_owner");
        EchoCapsule capsule = publicCapsule(owner, "Course echo", "course deliverable", "[\"study\"]");
        capsuleMapper.insert(capsule);

        int callsBeforeOnline = client.calls.get();
        assertTrue(index.similarities("deadline", List.of(capsule)).isEmpty());
        assertEquals(callsBeforeOnline + 1, client.calls.get(),
                "interactive matching may embed the query once but must not embed candidates");
        assertEquals(0, embeddingMapper.selectCount(null));

        var first = index.rebuildMissing(10);
        assertEquals(1, first.selected());
        assertEquals(1, first.indexed());
        assertEquals(0, first.failed());
        assertEquals(0, first.remaining());
        int callsAfterRebuild = client.calls.get();
        assertTrue(index.similarities("deadline", List.of(capsule)).get(capsule.id) > .99);
        assertEquals(callsAfterRebuild + 1, client.calls.get(),
                "warm interactive matching still performs only the query embedding");

        capsule.intro = "quiet garden";
        capsuleMapper.updateById(capsule);
        assertTrue(index.similarities("deadline", List.of(capsule)).isEmpty(),
                "a stale active row must not score after public content changes");
        assertEquals(1, index.pendingCount());

        var second = index.rebuildMissing(10);
        assertEquals(1, second.indexed());
        assertEquals(1, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id).eq("status", "ACTIVE")));
        assertEquals(1, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id).eq("status", "SUPERSEDED")));
        assertTrue(index.similarities("garden", List.of(capsule)).get(capsule.id) > .99);
    }

    @Test
    void rebuildExcludesPrivateAndSimulatorCapsules() {
        Long owner = createOwner("capsule_embedding_scope_owner");
        EchoCapsule privateCapsule = publicCapsule(owner, "Private", "private text", "[]");
        privateCapsule.isPublic = false;
        privateCapsule.visibilityStatus = "PRIVATE";
        capsuleMapper.insert(privateCapsule);
        EchoCapsule simulator = publicCapsule(owner, "Simulator", "simulator text", "[]");
        simulator.simulatorOnly = true;
        capsuleMapper.insert(simulator);

        assertEquals(0, index.rebuildMissing(10).selected());
        assertEquals(0, embeddingMapper.selectCount(null));
    }

    private Long createOwner(String username) {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                username, "not-a-real-hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
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
        public String modelName() { return "capsule-contract-model"; }
        public String modelVersion() { return "v1"; }
        public int dimensions() { return 8; }

        public float[] embed(String text) {
            calls.incrementAndGet();
            float[] vector = new float[8];
            String lower = text.toLowerCase();
            int group = lower.contains("garden") ? 1
                    : lower.contains("deadline") || lower.contains("course") ? 0 : 7;
            vector[group] = 1;
            return vector;
        }
    }
}
