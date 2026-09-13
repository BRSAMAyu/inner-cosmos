package com.innercosmos.service.product;

import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.WeeklyReviewV2Service;
import com.innercosmos.vo.StarfieldVO;
import com.innercosmos.vo.WeeklyReviewV2VO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-24 纠正贯穿全部视图旅程 (J04/J05/J06 联动): a user-corrected memory is the SAME
 * fact everywhere — the version-bumped card, the starfield rendering, and the weekly
 * review's EVIDENCE REFERENCES all carry the corrected version, never the superseded
 * wording; and dimensions without data get honest missing notes (J06 允许"不是重点"),
 * never faked blanks. Style follows StateMatrixAndPortraitAndPersonaTest.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class CorrectionAcrossViewsJourneyTest {

    private static final AtomicLong USERS = new AtomicLong(990_000_001L);

    @Autowired MemoryCardMapper memoryCardMapper;
    @Autowired MemoryLifecycleService lifecycle;
    @Autowired MemoryService memoryService;
    @Autowired WeeklyReviewV2Service weeklyReview;

    @Test
    void correctedMemoryCarriesThroughCardStarfieldAndWeeklyEvidence() {
        long user = USERS.incrementAndGet();
        MemoryCard card = new MemoryCard();
        card.userId = user;
        card.title = "晚睡";
        card.summary = "我晚睡是因为怕黑，开着灯才敢睡";
        card.memoryType = "FACT";
        card.memoryLayer = "CORE";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.versionNo = 1;
        memoryCardMapper.insert(card);

        // J04: the user corrects the memory, version-pinned.
        MemoryOperationCommand correction = new MemoryOperationCommand(
                "UPDATE", card.id, List.of(),
                "晚睡", "后来发现听雨声比开灯更能让我睡着", null,
                "用户主动纠正", 0.9, "user-correction", 1);
        var result = lifecycle.execute(user, correction);
        assertNotNull(result);
        MemoryCard corrected = memoryCardMapper.selectById(card.id);
        assertEquals("后来发现听雨声比开灯更能让我睡着", corrected.summary,
                "the corrected wording IS the memory now");
        assertEquals(2, corrected.versionNo, "correction bumps the version");

        // J05: the starfield renders the corrected fact, not the superseded one.
        List<StarfieldVO> stars = memoryService.starfield(user);
        assertFalse(stars.isEmpty());
        assertTrue(stars.stream().anyMatch(s -> s.summary != null && s.summary.contains("雨声")),
                "starfield shows the corrected summary");
        assertFalse(String.valueOf(stars).contains("开着灯"),
                "the superseded wording never renders in the view");

        // J06: the weekly review cites the corrected card as evidence, and the
        // no-emotion-trajectory dimension is honestly missing — not zero, not blank.
        WeeklyReviewV2VO review = weeklyReview.generateForRange(user,
                LocalDate.now().minusDays(6), LocalDate.now().plusDays(1));
        assertTrue(review.evidenceRefs.stream()
                        .anyMatch(ref -> "topThemes".equals(ref.dimension())
                                && ref.sourceIds().contains(card.id)),
                "the weekly review's theme evidence cites the corrected card");
        assertTrue(review.missingNotes.stream()
                        .anyMatch(note -> "dominantEmotion".equals(note.dimension())),
                "no emotion data this week is STATED as missing");
        assertFalse(review.evidenceRefs.stream()
                        .anyMatch(ref -> "dominantEmotion".equals(ref.dimension())),
                "an empty dimension never fakes evidence");
    }
}
