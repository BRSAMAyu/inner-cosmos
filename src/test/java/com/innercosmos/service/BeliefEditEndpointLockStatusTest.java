package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.BeliefPattern;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CP-21 residual — the BELIEF half is now genuinely LOCKED (V54 gave
 * {@code tb_belief_pattern} a {@code version} column; the negative-test layer this class
 * used to carry is retired).
 *
 * <p>Endpoint classification (unchanged): {@code POST /api/belief/extract/{cardId}} is a
 * create/append action (upserts belief rows derived from a memory card — nothing for a
 * caller to pin), while {@code POST /api/belief/{id}/recalculate} edits an existing
 * {@code tb_belief_pattern} row and therefore takes an {@code expectedVersion}
 * optimistic lock, same contract as the portrait claim transitions
 * (PortraitClaimControlServiceImpl):</p>
 *
 * <ul>
 *   <li>a stale pin dies as {@link ErrorCode#CONFLICT} (409 via GlobalExceptionHandler)
 *       with the row untouched — no lost update, no silent overwrite;</li>
 *   <li>a fresh pin recalculates and bumps {@code version} by one (atomic conditional
 *       UPDATE, so a racing writer between read and write also surfaces as CONFLICT);</li>
 *   <li>legacy callers with no pin keep working (null = unconditional intent);</li>
 *   <li>extract stays append/upsert — new rows are born at version 1 and re-extraction
 *       confirms in place instead of churning the version.</li>
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
    void staleExpectedVersionIsRejectedAsConflictAndTouchesNothing() {
        User owner = human("cp21e");
        BeliefPattern belief = belief(owner, 4);

        // A pin that never matched the row (insert default is version 1) is rejected before
        // any write: the row keeps its inserted shape — strength 0.5, not the 1.0 a
        // successful recalculate of 4 confirmations would produce.
        assertThatThrownBy(() -> beliefService.recalculateStrength(owner.id, belief.id, 99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被更新")
                .extracting(e -> ((BusinessException) e).code)
                .isEqualTo(ErrorCode.CONFLICT);
        BeliefPattern untouched = beliefMapper.selectById(belief.id);
        assertThat(untouched.version).isEqualTo(1);
        assertThat(untouched.strengthScore).isEqualTo(0.5);

        // The caller re-pins the real version and lands: 1 -> 2, strength recalculated.
        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id, 1))
                .doesNotThrowAnyException();
        BeliefPattern recalculated = beliefMapper.selectById(belief.id);
        assertThat(recalculated.version).isEqualTo(2);
        // 0.3 base + min(4*0.1, 0.4) confirmation + same-day recency 0.3, clamped.
        assertThat(recalculated.strengthScore).isEqualTo(1.0);

        // The OLD pin (1) is now stale and must not overwrite the newer write: still 409,
        // version and data exactly where the winning recalculate left them.
        assertThatThrownBy(() -> beliefService.recalculateStrength(owner.id, belief.id, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code)
                .isEqualTo(ErrorCode.CONFLICT);
        BeliefPattern stillRecalculated = beliefMapper.selectById(belief.id);
        assertThat(stillRecalculated.version).isEqualTo(2);
        assertThat(stillRecalculated.strengthScore).isEqualTo(1.0);
        assertThat(beliefService.findBeliefs(owner.id)).hasSize(1);
    }

    @Test
    void freshExpectedVersionLandsAndBumpsTheVersionByOne() {
        User owner = human("cp21f");
        BeliefPattern belief = belief(owner, 0);

        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id, 1))
                .doesNotThrowAnyException();
        BeliefPattern first = beliefMapper.selectById(belief.id);
        assertThat(first.version).isEqualTo(2);
        // 0.3 base + 0 confirmation + same-day recency 0.3.
        assertThat(first.strengthScore).isEqualTo(0.6);

        // Sequential recalculates with the freshly read pin keep landing, one bump each.
        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id, 2))
                .doesNotThrowAnyException();
        assertThat(beliefMapper.selectById(belief.id).version).isEqualTo(3);
    }

    @Test
    void legacyCallerWithoutExpectedVersionStillWorks() {
        User owner = human("cp21g");
        BeliefPattern belief = belief(owner, 4);

        // null pin = legacy unconditional intent: both sequential recalculates land,
        // the version bumps each time (and the atomic conditional UPDATE still guards
        // against a racing writer for unpinned callers too).
        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> beliefService.recalculateStrength(owner.id, belief.id, null))
                .doesNotThrowAnyException();

        BeliefPattern row = beliefMapper.selectById(belief.id);
        assertThat(row.version).isEqualTo(3);
        assertThat(row.strengthScore).isEqualTo(1.0);
        assertThat(beliefService.findBeliefs(owner.id)).hasSize(1);
    }

    @Test
    void extractRemainsAppendOnly_newRowsBornAtVersionOne() {
        User owner = human("cp21h");
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = "项目复盘";
        card.summary = "我说我不行，也担心别人怎么看我";
        card.memoryType = "REFLECTION";
        card.status = "ACTIVE";
        memoryMapper.insert(card);

        // Fallback extractor (mock LLM in tests) derives at least one belief from the card;
        // extraction keeps working with no version parameter anywhere.
        assertThatCode(() -> beliefService.extractFromMemory(owner.id, card.id))
                .doesNotThrowAnyException();

        assertThat(beliefService.findBeliefs(owner.id)).isNotEmpty();
        // New rows from the extract upsert are born at version 1 (V54 column default +
        // the explicit set in BeliefExtractServiceImpl), ready to be pinned.
        assertThat(beliefService.findBeliefs(owner.id))
                .allSatisfy(b -> assertThat(b.version).isEqualTo(1));

        // Re-extraction confirms the same content in place instead of conflicting with
        // itself — an append/upsert action, by design never an optimistic-lock candidate:
        // the version does not churn on confirmation.
        assertThatCode(() -> beliefService.extractFromMemory(owner.id, card.id))
                .doesNotThrowAnyException();
        assertThat(beliefService.findBeliefs(owner.id))
                .allSatisfy(b -> assertThat(b.version).isEqualTo(1));
    }
}
