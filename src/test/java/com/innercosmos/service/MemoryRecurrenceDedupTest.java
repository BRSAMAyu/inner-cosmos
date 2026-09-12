package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.memory.MemoryRecurrenceMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-21 duplicate-event dedup in the settlement pipeline. The same event re-told in a later
 * session folds into its existing memory as a RECURRENCE (counts rise, version bumps, a
 * REINFORCE/DUPLICATE_EVENT_DEDUP operation records the evidence session) — never a parallel
 * ACTIVE duplicate. Different events stay separate. A forgotten (or backup-resurrected
 * tombstoned) event may NOT absorb a recurrence: the retelling becomes a genuinely new card.
 */
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class MemoryRecurrenceDedupTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DialogSessionMapper sessionMapper;
    @Autowired DialogMessageMapper messageMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryService memoryService;
    @Autowired MemoryLifecycleService lifecycleService;

    private static final String FIRST_TELLING =
            "今天和导师讨论了论文方向，他觉得我的选题范围太宽了，要收窄到一个问题，我有点挫败但明白他的意思。";
    private static final String SECOND_TELLING =
            "今天和导师讨论了论文方向，他觉得我的选题范围太宽了，要收窄到一个问题。又想了一遍还是有点挫败，打算明天改一版提纲。";
    private static final String DIFFERENT_EVENT =
            "周末去了海边露营，日落的时候风特别大，帐篷差点被吹走，我们笑着压了一晚上帐篷。";

    @Test
    void retoldEventFoldsIntoOneVersionedMemoryWithAuditableRecurrence() {
        Long owner = seedUser();
        Long first = settle(owner, "第一次谈起", FIRST_TELLING);
        Long second = settle(owner, "再次谈起", SECOND_TELLING);

        List<MemoryCard> cards = memoryMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).ne("status", "FORGOTTEN"));
        assertThat(cards).hasSize(1);
        MemoryCard the = cards.get(0);
        assertThat(the.sourceSessionId).isEqualTo(first);
        assertThat(the.recurrenceCount).isEqualTo(2);
        assertThat(the.versionNo).isEqualTo(2);
        assertThat(the.lastTouchedAt).isNotNull();

        // The version trail shows WHY the version bumped and WHICH retelling caused it.
        assertThat(lifecycleService.history(owner, the.id).stream()
                .anyMatch(op -> "REINFORCE".equals(op.operationType)
                        && "DUPLICATE_EVENT_DEDUP".equals(op.reasonCode)
                        && op.evidenceRefs != null && op.evidenceRefs.contains("AURORA_SESSION:" + second)))
                .isTrue();
    }

    @Test
    void differentEventsStaySeparateMemories() {
        Long owner = seedUser();
        settle(owner, "导师谈话", FIRST_TELLING);
        settle(owner, "海边露营", DIFFERENT_EVENT);

        List<MemoryCard> cards = memoryMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).eq("status", "ACTIVE"));
        assertThat(cards).hasSize(2);
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.recurrenceCount).isEqualTo(1);
            assertThat(card.versionNo).isEqualTo(1);
        });
    }

    @Test
    void forgottenEventDoesNotAbsorbItsRetellingEvenAfterBackupResurrection() {
        Long owner = seedUser();
        Long first = settle(owner, "第一次谈起", FIRST_TELLING);
        MemoryCard card = memoryMapper.selectOne(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).eq("source_session_id", first));
        lifecycleService.execute(owner, new MemoryOperationCommand(
                "FORGET", card.id, null, null, null, null, "owner forget", null, null));
        // Backup resurrection: the forgotten row comes back looking ACTIVE.
        MemoryCard resurrected = memoryMapper.selectById(card.id);
        resurrected.status = "ACTIVE";
        memoryMapper.updateById(resurrected);

        Long second = settle(owner, "再次谈起", SECOND_TELLING);

        // The retelling becomes a genuinely NEW memory — reinforcing the withdrawn row would
        // be resurrection with new content attached.
        MemoryCard forgottenRow = memoryMapper.selectById(card.id);
        assertThat(forgottenRow.recurrenceCount).isEqualTo(1);
        assertThat(forgottenRow.versionNo).isEqualTo(2); // only the FORGET bump, no recurrence bump
        List<MemoryCard> liveCards = memoryMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).ne("id", card.id).ne("status", "FORGOTTEN"));
        assertThat(liveCards).hasSize(1);
        assertThat(liveCards.get(0).sourceSessionId).isEqualTo(second);
        assertThat(liveCards.get(0).recurrenceCount).isEqualTo(1);
    }

    @Test
    void settlingTheSameSessionTwiceStillProducesExactlyOneCardWithoutDoubleRecurrence() {
        Long owner = seedUser();
        Long session = settle(owner, "唯一会话", FIRST_TELLING);
        memoryService.extractFromSession(owner, session);
        memoryService.extractFromSession(owner, session);

        List<MemoryCard> cards = memoryMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner));
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).recurrenceCount).isEqualTo(1);
        assertThat(cards.get(0).sourceSessionId).isEqualTo(session);
    }

    @Test
    void matcherIsDeterministicAroundItsThresholdAndSafeOnEmptyInput() {
        assertThat(MemoryRecurrenceMatcher.similarity(FIRST_TELLING, SECOND_TELLING))
                .isGreaterThanOrEqualTo(MemoryRecurrenceMatcher.EVENT_MATCH_THRESHOLD);
        assertThat(MemoryRecurrenceMatcher.similarity(FIRST_TELLING, DIFFERENT_EVENT))
                .isLessThan(MemoryRecurrenceMatcher.EVENT_MATCH_THRESHOLD);
        assertThat(MemoryRecurrenceMatcher.similarity("", FIRST_TELLING)).isZero();
        assertThat(MemoryRecurrenceMatcher.similarity(null, null)).isZero();
        assertThat(MemoryRecurrenceMatcher.match(List.of(), FIRST_TELLING)).isNull();
        assertThat(MemoryRecurrenceMatcher.match(null, FIRST_TELLING)).isNull();
    }

    private Long settle(Long owner, String title, String text) {
        DialogSession session = new DialogSession();
        session.userId = owner;
        session.title = title;
        session.sessionType = "AURORA_CHAT";
        session.status = "FINISHED";
        sessionMapper.insert(session);
        DialogMessage message = new DialogMessage();
        message.sessionId = session.id;
        message.userId = owner;
        message.speaker = "USER";
        message.textContent = text;
        message.inputType = "TEXT";
        messageMapper.insert(message);
        memoryService.extractFromSession(owner, session.id);
        return session.id;
    }

    private Long seedUser() {
        String username = "memory-recurrence-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }
}
