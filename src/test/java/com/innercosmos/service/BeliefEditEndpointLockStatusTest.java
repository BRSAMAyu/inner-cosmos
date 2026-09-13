package com.innercosmos.service;

import com.innercosmos.entity.BeliefPattern;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.BeliefPatternMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CP-21 residual batch — the BELIEF half stays honestly UNLOCKED (negative-test layer only).
 *
 * <p>Endpoint classification (see the batch report): {@code POST /api/belief/extract/{cardId}}
 * is a create/append action (upserts belief rows derived from a memory card — nothing for a
 * caller to pin), while {@code POST /api/belief/{id}/recalculate} edits an existing
 * {@code tb_belief_pattern} row and therefore WANTS an expectedVersion optimistic lock.
 * It cannot get one honestly: {@code tb_belief_pattern} has NO version column (V1 baseline +
 * schema.sql), and this batch is schema-frozen — adding {@code expectedVersion} against a
 * nonexistent column, or inventing a lock token out of {@code updated_at}, would be theater.
 * These tests document the current behavior so the gap is a recorded fact, not folklore:</p>
 *
 * <ul>
 *   <li>recalculate edits a row with no conflict channel — never CONFLICT, last call wins;</li>
 *   <li>extract keeps appending/upserting untouched — no lock to trip over;</li>
 *   <li>the read endpoints the belief gallery loads stay conflict-free (409 only ever comes
 *       from a real optimistic lock, which is exactly what the CP-21 UI waits for).</li>
 * </ul>
 */
@SpringBootTest
class BeliefEditEndpointLockStatusTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired BeliefExtractService beliefService;
    @Autowired UserService userService;
    @Autowired BeliefPatternMapper beliefMapper;
    @Autowired MemoryCardMapper memoryMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(26).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private BeliefPattern belief(User owner, int confirmations) {
        BeliefPattern belief = new BeliefPattern();
        belief.userId = owner.id;
        belief.beliefContent = "我可能不够好";
        belief.beliefType = "SELF";
        belief.beliefCategory = "能力认知";
        belief.strengthScore = BeliefPattern.clampStrength(0.5);
        belief.supportingMemoryIds = "";
        belief.contradictingMemoryIds = "";
        belief.firstDetectedAt = LocalDateTime.now();
        belief.lastConfirmedAt = LocalDateTime.now();
        belief.confirmationCount = confirmations;
        belief.status = "ACTIVE";
        beliefMapper.insert(belief);
        return belief;
    }

    @Test
    void recalculateIsCurrentlyUnlocked_noConflictChannel_lastCallWins() {
        User owner = human("cp21e");
        BeliefPattern belief = belief(owner, 4);

        // Two sequential recalculates both land: there is no version token to pin, so no
        // second caller can be told "someone updated this before you". Recorded as the
        // honest current state — the lock lands when tb_belief_pattern gets a version
        // column (schema decision, outside this schema-frozen batch).
        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id))
                .doesNotThrowAnyException();
        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id))
                .doesNotThrowAnyException();

        BeliefPattern row = beliefMapper.selectById(belief.id);
        // 0.3 base + min(4*0.1, 0.4) confirmation + same-day recency 0.3, clamped.
        assertThat(row.strengthScore).isEqualTo(1.0);
        // And the edit leaves no conflict-shaped trace: no version field exists to move.
        assertThat(beliefService.findBeliefs(owner.id)).hasSize(1);
    }

    @Test
    void extractRemainsAppendOnly_noLockToTripOver() {
        User owner = human("cp21f");
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = "项目复盘";
        card.summary = "我说我不行，也担心别人怎么看我";
        card.memoryType = "REFLECTION";
        card.status = "ACTIVE";
        memoryMapper.insert(card);

        // Fallback extractor (mock LLM in tests) derives at least one belief from the card;
        // extraction must keep working with no version parameter anywhere.
        assertThatCode(() -> beliefService.extractFromMemory(owner.id, card.id))
                .doesNotThrowAnyException();

        assertThat(beliefService.findBeliefs(owner.id)).isNotEmpty();
        // Re-extraction confirms the same content instead of conflicting with itself —
        // an append/upsert action, by design never an optimistic-lock candidate.
        assertThatCode(() -> beliefService.extractFromMemory(owner.id, card.id))
                .doesNotThrowAnyException();
    }
}
