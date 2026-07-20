package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest(properties = {
        "inner-cosmos.demo.seed-enabled=false",
        "spring.task.scheduling.enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
@Import(CapsuleEmbeddingIndexServiceIntegrationTest.FakeEmbeddingConfig.class)
class CapsuleEmbeddingPostgresIntegrationTest {
    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("capsule_embedding")
            .withUsername("inner_cosmos")
            .withPassword("capsule-contract-only");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired CapsuleEmbeddingIndexService index;
    @Autowired CapsuleEmbeddingMapper embeddingMapper;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pgvectorScoresOnlyCurrentContentHashAndRetiresPreviousVector() {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                "capsule_pg_owner", "not-a-real-hash", "USER", "ACTIVE");
        Long owner = jdbc.queryForObject(
                "SELECT id FROM tb_user WHERE username='capsule_pg_owner'", Long.class);
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = owner;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = "Course echo";
        capsule.intro = "course deliverable";
        capsule.publicTags = "[\"study\"]";
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        capsule.simulatorOnly = false;
        capsule.echoEnergy = .5;
        capsuleMapper.insert(capsule);

        assertEquals(1, index.rebuildMissing(10).indexed());
        assertEquals(1L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM tb_capsule_embedding
                WHERE capsule_id=? AND embedding_vector IS NOT NULL
                """, Long.class, capsule.id));
        assertTrue(index.similarities("deadline", List.of(capsule)).get(capsule.id) > .99);

        capsule.pseudonym = "Garden echo";
        capsule.intro = "quiet garden";
        capsuleMapper.updateById(capsule);
        assertTrue(index.similarities("garden", List.of(capsule)).isEmpty(),
                "the old ACTIVE vector cannot score when current public content changed");

        assertEquals(1, index.rebuildMissing(10).indexed());
        assertEquals(1, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id).eq("status", "ACTIVE")));
        assertEquals(1, embeddingMapper.selectCount(new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id).eq("status", "SUPERSEDED")));
        assertTrue(index.similarities("garden", List.of(capsule)).get(capsule.id) > .99);

        // G5 PROFILE-PROPAGATION: withdrawing consent must physically erase the pgvector column,
        // not merely soft-flag it. Both the ACTIVE and SUPERSEDED rows (and their vectors) go.
        int erased = index.retireForCapsule(capsule.id);
        assertEquals(2, erased, "both current and superseded derived vectors must be erased");
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_capsule_embedding WHERE capsule_id=?", Long.class, capsule.id),
                "no pgvector row may survive a consent withdrawal");
        assertTrue(index.similarities("garden", List.of(capsule)).isEmpty(),
                "an erased capsule can never score again");
    }
}
