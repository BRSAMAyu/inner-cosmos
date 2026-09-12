package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.ai.retrieval.TimeWindowParser;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-22 hard time-window retrieval semantics. A measurable recency expression is a hard
 * constraint (an outside-window memory never becomes a candidate, even with strong lexical
 * overlap), while a bare unmeasurable "最近" stays the soft freshness signal (it often
 * describes the recalling, not the memory — hard-filtering on it would hide exactly the
 * older material the user is recalling).
 */
@SpringBootTest
class TimeWindowSemanticsTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired MemoryRetrievalService retrieval;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired UserService userService;

    @Test
    void parserMapsMeasurableWindowsAndLeavesBareRecencyUnmeasured() {
        assertNull(TimeWindowParser.parseMaxAgeDays(null));
        assertNull(TimeWindowParser.parseMaxAgeDays(""));
        assertNull(TimeWindowParser.parseMaxAgeDays("膝盖恢复的安排"));
        // Bare, unmeasurable recency: soft signal only.
        assertNull(TimeWindowParser.parseMaxAgeDays("最近膝盖恢复的安排"));
        assertEquals(1, TimeWindowParser.parseMaxAgeDays("今天膝盖恢复的安排"));
        assertEquals(7, TimeWindowParser.parseMaxAgeDays("这周膝盖恢复的安排"));
        assertEquals(7, TimeWindowParser.parseMaxAgeDays("本周的复盘"));
        assertEquals(62, TimeWindowParser.parseMaxAgeDays("上个月的项目复盘"));
        assertEquals(14, TimeWindowParser.parseMaxAgeDays("上周的复盘"));
        assertEquals(31, TimeWindowParser.parseMaxAgeDays("最近一个月的复盘"));
        assertEquals(93, TimeWindowParser.parseMaxAgeDays("最近三个月的复盘"));
        assertEquals(90, TimeWindowParser.parseMaxAgeDays("最近90天的记录"));
        assertEquals(62, TimeWindowParser.parseMaxAgeDays("最近两个月"));
        assertEquals(366, TimeWindowParser.parseMaxAgeDays("今年年中的复盘"));
        assertEquals(732, TimeWindowParser.parseMaxAgeDays("最近两年的变化"));
    }

    @Test
    void measurableWindowIsAHardExclusionEvenForStronglyOverlappingOldMemories() {
        User owner = human("cp22t1");
        MemoryCard recent = memory(owner, "膝盖恢复训练的当下安排", "本周开始每天靠墙静蹲，游泳代替跑步", 2);
        memory(owner, "膝盖恢复训练的旧安排", "膝盖恢复训练当时是每天靠墙静蹲，游泳代替跑步", 700);

        MemoryEvidencePackVO pack = retrieval.retrieve(owner.id,
                new MemoryRetrievalQuery("这周膝盖恢复训练的安排", null, null, null, null, false));
        assertTrue(pack.evidence().stream()
                        .anyMatch(evidence -> evidence.memoryId().equals(recent.id)),
                "the in-window memory is retrieved");
        assertTrue(pack.evidence().stream().noneMatch(evidence -> evidence.title().contains("旧安排")),
                "the 700-day-old strongly-overlapping memory is excluded by the hard window, "
                        + "not merely down-ranked");
    }

    @Test
    void bareRecencyStaysSoftTheOldMemoryRemainsRetrievable() {
        User owner = human("cp22t2");
        MemoryCard recent = memory(owner, "膝盖恢复训练的当下安排", "本周开始每天靠墙静蹲，游泳代替跑步", 2);
        MemoryCard old = memory(owner, "膝盖恢复训练的旧安排", "膝盖恢复训练当时是每天靠墙静蹲，游泳代替跑步", 700);

        MemoryEvidencePackVO pack = retrieval.retrieve(owner.id,
                new MemoryRetrievalQuery("最近膝盖恢复训练的安排", null, null, null, null, false));
        // No hard window: both stay candidates; freshness only ORDERS them.
        assertTrue(pack.evidence().stream()
                .anyMatch(evidence -> evidence.memoryId().equals(recent.id)));
        assertTrue(pack.evidence().stream()
                .anyMatch(evidence -> evidence.memoryId().equals(old.id)),
                "bare '最近' must not hard-hide the older memory the user may be recalling");
        long recentIndex = indexOf(pack, recent.id);
        long oldIndex = indexOf(pack, old.id);
        assertTrue(recentIndex < oldIndex, "freshness still orders recent above old");
        assertFalse(pack.evidence().isEmpty());
    }

    private static long indexOf(MemoryEvidencePackVO pack, Long memoryId) {
        for (int i = 0; i < pack.evidence().size(); i++) {
            if (pack.evidence().get(i).memoryId().equals(memoryId)) return i;
        }
        return Long.MAX_VALUE;
    }

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(29).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private MemoryCard memory(User owner, String title, String summary, int daysAgo) {
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = title;
        card.summary = summary;
        card.memoryType = "FACT";
        card.memoryLayer = "EPISODIC";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        card.versionNo = 1;
        card.emotionalGravity = 0.4;
        card.lastTouchedAt = LocalDateTime.now().minusDays(daysAgo);
        card.createdAt = card.lastTouchedAt;
        memoryMapper.insert(card);
        return card;
    }
}
