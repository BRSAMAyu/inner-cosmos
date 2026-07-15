package com.innercosmos.service;

import com.innercosmos.vo.CapsulePreviewVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * previewFromMemory is the strict de-identified preview shown before a user ever confirms
 * capsule creation. createFromMemory and recompileGenome both already exclude LOCAL_ONLY and
 * NO_EXTERNAL_PROCESSING memories (CapsuleServiceImpl), but that filter used a real
 * MemoryCardMapper query — the existing DataMaskingServiceTest is Mockito-only and stubs
 * memoryCardMapper.selectList(any()) to return a fixed list regardless of the query, so it
 * could not have caught previewFromMemory skipping the same consent-scope check. This is a
 * real Spring context test against H2 to close that gap.
 */
@SpringBootTest(properties = {"spring.task.scheduling.enabled=false"})
class DataMaskingServiceConsentScopeIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataMaskingService dataMaskingService;

    @Test
    @Transactional
    void previewNeverSurfacesLocalOnlyOrNoExternalProcessingMemories() {
        Long owner = seedUser("preview-consent-owner");
        String sharedSecret = "可以分享的日常故事";
        String localOnlySecret = "绝对不能外泄的本地专属计划-" + System.nanoTime();
        String noExternalSecret = "禁止任何外部处理的私密内容-" + System.nanoTime();
        Long sharedId = seedMemory(owner, sharedSecret, "SHARED", "[分享]");
        Long localOnlyId = seedMemory(owner, localOnlySecret, "LOCAL_ONLY", "[本地]");
        Long noExternalId = seedMemory(owner, noExternalSecret, "NO_EXTERNAL_PROCESSING", "[禁止]");

        CapsulePreviewVO preview = dataMaskingService.previewFromMemory(owner,
                List.of(sharedId, localOnlyId, noExternalId), "STRICT", null, null);

        assertTrue(preview.abstractSummary.contains(sharedSecret), "shared memory should still appear");
        assertFalse(preview.abstractSummary.contains(localOnlySecret), "LOCAL_ONLY content must never enter the preview");
        assertFalse(preview.abstractSummary.contains(noExternalSecret), "NO_EXTERNAL_PROCESSING content must never enter the preview");
        assertFalse(preview.publicTags.contains("本地"), "LOCAL_ONLY tags must not surface as public tags either");
        assertFalse(preview.publicTags.contains("禁止"), "NO_EXTERNAL_PROCESSING tags must not surface as public tags either");
        assertTrue(preview.riskWarnings.stream().anyMatch(w -> w.contains("仅本地使用") || w.contains("禁止外部处理")),
                "the exclusion should be disclosed to the user, not silently dropped");
    }

    @Test
    @Transactional
    void previewOfOnlyConsentRestrictedMemoriesReturnsAnHonestEmptySummary() {
        Long owner = seedUser("preview-consent-owner-empty");
        Long localOnlyId = seedMemory(owner, "只属于本地的内容-" + System.nanoTime(), "LOCAL_ONLY", "[本地]");

        CapsulePreviewVO preview = dataMaskingService.previewFromMemory(owner, List.of(localOnlyId), "STRICT", null, null);

        assertTrue(preview.abstractSummary.equals("暂无摘要") || preview.abstractSummary.isBlank(),
                "with nothing eligible, the preview must not fabricate a summary from restricted content");
        assertTrue(preview.riskWarnings.stream().anyMatch(w -> w.contains("仅本地使用") || w.contains("禁止外部处理")));
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedMemory(Long owner, String summary, String consentScope, String keywordTags) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope, keyword_tags)
                VALUES (?, '测试记忆', ?, 'ACTIVE', 1, ?, ?)
                """, owner, summary, consentScope, keywordTags);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }
}
