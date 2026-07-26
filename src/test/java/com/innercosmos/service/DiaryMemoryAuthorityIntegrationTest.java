package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.asr.AsrResult;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.VoiceTranscription;
import com.innercosmos.mapper.MemoryCardMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class DiaryMemoryAuthorityIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired VoiceTranscriptionService transcriptionService;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryRetrievalService retrievalService;
    @Autowired MemoryLifecycleService lifecycleService;
    @Autowired DataRetractionReceiptService retractionReceiptService;

    @Test
    void submittedDiaryIsTraceableRetrievableIdempotentAndForgettable() {
        Long owner = seedUser();
        String diaryText = "我把蓝色折纸船放在桌上，提醒自己展示前先做两分钟呼吸练习。";
        VoiceTranscription source = transcriptionService.create(
                owner, diaryText, new AsrResult(), "DIARY");

        transcriptionService.submitFinal(source.id, owner, diaryText);
        String provenance = "VOICE_TRANSCRIPTION:" + source.id
                + " · source-version:1 · consent:AURORA_PRIVATE";
        MemoryCard card = memoryMapper.selectOne(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).eq("provenance_refs", provenance));

        assertThat(card).isNotNull();
        assertThat(card.versionNo).isEqualTo(1);
        assertThat(card.memoryLayer).isEqualTo("EPISODIC");
        assertThat(card.consentScope).isEqualTo("AURORA_PRIVATE");
        assertThat(card.provenanceRefs).isEqualTo(provenance);

        // A retried submit resolves to the same source artifact and cannot duplicate memory.
        transcriptionService.submitFinal(source.id, owner, diaryText);
        assertThat(memoryMapper.selectCount(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).eq("provenance_refs", provenance))).isEqualTo(1);

        var beforeForget = retrievalService.retrieve(owner, new MemoryRetrievalQuery(
                "", "AURORA_CONVERSATION", List.of(), 8, 800, false));
        assertThat(beforeForget.evidence()).anySatisfy(evidence -> {
            assertThat(evidence.memoryId()).isEqualTo(card.id);
            assertThat(evidence.versionNo()).isEqualTo(1);
            assertThat(evidence.consentScope()).isEqualTo("AURORA_PRIVATE");
            assertThat(evidence.provenanceRefs()).isEqualTo(provenance);
        });

        lifecycleService.execute(owner, new MemoryOperationCommand(
                "FORGET", card.id, List.of(), null, null, null,
                "owner withdrew diary memory", 1.0, provenance));

        MemoryCard forgotten = memoryMapper.selectById(card.id);
        assertThat(forgotten.status).isEqualTo("FORGOTTEN");
        assertThat(forgotten.versionNo).isEqualTo(2);
        assertThat(forgotten.provenanceRefs).isNull();
        assertThat(retrievalService.retrieve(owner, new MemoryRetrievalQuery(
                "", "AURORA_CONVERSATION", List.of(), 8, 800, false)).evidence())
                .noneMatch(evidence -> evidence.memoryId().equals(card.id));
        assertThat(lifecycleService.history(owner, card.id))
                .anySatisfy(operation -> {
                    assertThat(operation.operationType).isEqualTo("FORGET");
                    assertThat(operation.oldVersion).isEqualTo(1);
                    assertThat(operation.newVersion).isEqualTo(2);
                });
        assertThat(retractionReceiptService.listForOwner(owner, 20))
                .anySatisfy(receipt -> {
                    assertThat(receipt.subjectId).isEqualTo(card.id);
                    assertThat(receipt.derivativeType)
                            .isEqualTo(DataRetractionReceiptService.DERIVATIVE_MEMORY_EMBEDDING);
                    assertThat(receipt.action).isEqualTo(DataRetractionReceiptService.ACTION_ERASED);
                });
    }

    private Long seedUser() {
        String username = "diary-authority-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }
}
