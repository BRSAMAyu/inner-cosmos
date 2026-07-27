package com.innercosmos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.vo.ShredderResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the thought-shredder robustness audit (思维碎纸机):
 *
 * <ul>
 *   <li>FIX 3 -- a blank/missing {@code text} must be rejected with a 400-mapped
 *       {@link BusinessException} instead of silently substituting a placeholder and burning a
 *       real AI provider call + persisted {@link MemoryCard}.</li>
 *   <li>FIX 1 -- {@code DISPLAY_ONCE} must persist nothing at all (no MemoryCard, no
 *       ThoughtFragment, no TodoItem) and return an id-less card.</li>
 *   <li>FIX 2 -- {@code coreFeeling}/{@code hiddenNeed} values containing control characters
 *       (newline, double-quote) must still round-trip into syntactically valid JSON in
 *       {@code emotionTags}/{@code keywordTags}.</li>
 *   <li>{@code history()} must stay owner-scoped.</li>
 * </ul>
 *
 * <p>Uses a real {@code @SpringBootTest} + H2 (test profile, MODE=MySQL) context, following the
 * same convention as {@link CapsuleP1P2PrivacyBoundaryTest}: {@link StructuredAiService} is
 * {@code @MockBean}-replaced so each test controls the exact AI-provider output deterministically,
 * while persistence goes through the real MyBatis-Plus mappers against the real schema.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class ThoughtShredderRobustnessTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThoughtShredderService thoughtShredderService;

    @MockBean
    private StructuredAiService structuredAiService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private StructuredAiResults.ShredderResult stubShredderResult(String coreFeeling) {
        StructuredAiResults.ShredderResult result = new StructuredAiResults.ShredderResult();
        result.coreFeeling = coreFeeling;
        result.hiddenNeed = "被看见";
        result.memoryType = "SHREDDER";
        return result;
    }

    private void stubAi(StructuredAiResults.ShredderResult result) {
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(result);
    }

    private long countShredderMemoryCards(Long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_memory_card WHERE user_id = ? AND memory_type = 'SHREDDER'",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    private long countThoughtFragments(Long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_thought_fragment WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private long countTodoItems(Long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_todo_item WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    @Test
    @Transactional
    @DisplayName("FIX 3: blank/missing text is rejected with BAD_REQUEST and no MemoryCard row is created")
    void blankText_isRejected_andPersistsNothing() {
        Long userId = seedUser("shredder-blank");

        BusinessException blankException = assertThrows(BusinessException.class,
                () -> thoughtShredderService.process(userId, "   ", "KEEP_ONLY_RESULT"));
        assertEquals("BAD_REQUEST", blankException.code);

        BusinessException nullException = assertThrows(BusinessException.class,
                () -> thoughtShredderService.process(userId, null, "KEEP_ONLY_RESULT"));
        assertEquals("BAD_REQUEST", nullException.code);

        assertEquals(0, countShredderMemoryCards(userId),
                "a blank/missing text must never reach persistence");
    }

    @Test
    @Transactional
    @DisplayName("FIX 3: text over the max length is rejected with BAD_REQUEST")
    void overLongText_isRejected() {
        Long userId = seedUser("shredder-too-long");
        String tooLong = "字".repeat(2001);

        BusinessException tooLongException = assertThrows(BusinessException.class,
                () -> thoughtShredderService.process(userId, tooLong, "KEEP_ONLY_RESULT"));
        assertEquals("BAD_REQUEST", tooLongException.code);
        assertEquals(0, countShredderMemoryCards(userId));
    }

    @Test
    @Transactional
    @DisplayName("FIX 1: DISPLAY_ONCE persists no MemoryCard, ThoughtFragment, or TodoItem row, and returns an id-less card")
    void displayOnce_persistsNothing() {
        Long userId = seedUser("shredder-display-once");
        stubAi(stubShredderResult("累"));

        // Deliberately mundane text with no crisis/abuse/distress-signal keyword overlap: those
        // trigger SafetyReviewService.recheckSync, which also calls the (here @MockBean-replaced)
        // StructuredAiService with a different result type -- a second, differently-typed call
        // through the same blanket `any()` stub would return this test's ShredderResult where a
        // safety-review result type is expected.
        ShredderResultVO result = thoughtShredderService.process(userId,
                "今天整理了书桌，把杂物分类摆好，顺便回顾了一下课程安排。", "DISPLAY_ONCE");

        assertNull(result.memoryCard.id, "DISPLAY_ONCE must return an id-less, unsaved card");
        assertFalse(result.fragments.isEmpty(), "one-time response still contains useful fragments in-memory");
        assertEquals(0, countShredderMemoryCards(userId));
        assertEquals(0, countThoughtFragments(userId));
        assertEquals(0, countTodoItems(userId));
    }

    @Test
    @Transactional
    @DisplayName("FIX 2: a coreFeeling with a newline and a double-quote round-trips into valid JSON in emotionTags")
    void coreFeelingWithControlCharacters_producesValidJson() throws Exception {
        Long userId = seedUser("shredder-json");
        String trickyFeeling = "累\n\"崩溃\"";
        stubAi(stubShredderResult(trickyFeeling));

        ShredderResultVO result = thoughtShredderService.process(userId,
                "今天完成了一部分作业，准备明天继续推进。", "KEEP_RAW");

        JsonNode parsed = MAPPER.readTree(result.memoryCard.emotionTags);
        assertTrue(parsed.isArray(), "emotionTags must parse as a JSON array");
        assertEquals(1, parsed.size());
        assertEquals(trickyFeeling, parsed.get(0).asText(),
                "the control characters must survive a real JSON parse round-trip unchanged");

        // keywordTags carries hiddenNeed alongside the fixed "thought-shredder" tag -- same
        // hand-rolled-JSON risk existed there, so assert it is valid JSON too.
        JsonNode keywordTags = MAPPER.readTree(result.memoryCard.keywordTags);
        assertTrue(keywordTags.isArray());
        assertEquals("thought-shredder", keywordTags.get(0).asText());
        assertEquals("被看见", keywordTags.get(1).asText());
    }

    @Test
    @Transactional
    @DisplayName("history() returns only the caller's own shredder cards")
    void history_scopedToCaller() {
        Long owner = seedUser("shredder-owner");
        Long other = seedUser("shredder-other");
        stubAi(stubShredderResult("累"));

        thoughtShredderService.process(owner, "今天写完了一份读书笔记，想把这句话留下来。", "KEEP_RAW");
        thoughtShredderService.process(other, "今天和朋友讨论了一下选课的事情，做了简单记录。", "KEEP_RAW");

        List<MemoryCard> ownerHistory = thoughtShredderService.history(owner);

        assertFalse(ownerHistory.isEmpty(), "the owner's own card must appear in their history");
        assertTrue(ownerHistory.stream().allMatch(card -> owner.equals(card.userId)),
                "history() must never leak another user's shredder cards");
    }
}
