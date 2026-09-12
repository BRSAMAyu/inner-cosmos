package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.ai.retrieval.RetrievalQueryNormalizer;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-22 retrieval quality: meta-discourse ("帮我分析一下") is a request for a service, not a
 * description of the memory to find. It must never drive lexical admission — an unrelated
 * memory whose content merely contains the service verb ("反复分析") must stay out of the
 * Evidence Pack, a meta-only request must honestly retrieve nothing, and the pack still
 * reports the user's original wording. Task fit keeps ordering deterministic.
 */
@SpringBootTest
class MemoryRetrievalQualityTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MemoryRetrievalService retrieval;
    @Autowired
    private UserService userService;
    @Autowired
    private MemoryCardMapper memoryCardMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(26).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private MemoryCard memory(User owner, String title, String summary, String type, String layer) {
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = title;
        card.summary = summary;
        card.memoryType = type;
        card.memoryLayer = layer;
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.emotionalGravity = 0.4;
        memoryCardMapper.insert(card);
        return card;
    }

    @Test
    void normalizerStripsServiceMarkersAndKeepsContent() {
        assertEquals("", RetrievalQueryNormalizer.normalize(null));
        assertEquals("", RetrievalQueryNormalizer.normalize("帮我分析一下"));
        assertEquals("", RetrievalQueryNormalizer.normalize("在吗？想聊聊"));
        assertEquals("数据分析报告的进展",
                RetrievalQueryNormalizer.normalize("帮我分析一下 数据分析报告的进展"));
        assertEquals("朋友说我什么都反复琢磨",
                RetrievalQueryNormalizer.normalize("我想聊聊朋友说我什么都反复琢磨"));
    }

    @Test
    void metaOnlyRequestHonestlyRetrievesNothing() {
        User owner = human("cp22q1");
        memory(owner, "朋友说我什么事都反复分析", "朋友觉得我什么事都反复分析，让人累", "FACT", "EPISODIC");
        memory(owner, "职业方向的整理", "之前梳理过一次职业方向，结论是先留在当前团队", "FACT", "SEMANTIC");

        MemoryEvidencePackVO pack = retrieval.retrieve(owner.id,
                new MemoryRetrievalQuery("帮我分析一下", null, null, null, null, false));
        assertTrue(pack.evidence().isEmpty(),
                "a meta-only request must not lexically admit memories that merely share the service verb");
        assertEquals("帮我分析一下", pack.query(),
                "the pack still reports the user's original wording");
    }

    @Test
    void contentQueryFindsTheRightMemoryAndSkipsVerbOnlyOverlap() {
        User owner = human("cp22q2");
        MemoryCard report = memory(owner, "季度数据分析报告", "用户负责季度数据分析报告，卡在图表部分",
                "FACT", "SEMANTIC");
        memory(owner, "朋友说我什么事都反复分析", "朋友觉得我什么事都反复分析，让人累", "FACT", "EPISODIC");

        MemoryEvidencePackVO pack = retrieval.retrieve(owner.id,
                new MemoryRetrievalQuery("帮我分析一下数据分析报告", null, null, null, null, false));
        List<Long> ids = pack.evidence().stream().map(MemoryEvidencePackVO.Evidence::memoryId).toList();
        assertTrue(ids.contains(report.id));
        assertEquals(1, ids.size(), "the verb-only-overlap memory stays out of the Evidence Pack");
    }

    @Test
    void actionTaskRanksProspectiveMemoryAboveEquallyRelevantEpisodicOne() {
        User owner = human("cp22q3");
        MemoryCard action = memory(owner, "数据分析报告的下一步", "把图表部分拆成三个小任务逐个完成",
                "TODO", "PROSPECTIVE");
        memory(owner, "上周讨论数据分析报告", "开会时同事问了数据分析报告的进度", "FACT", "EPISODIC");

        MemoryEvidencePackVO pack = retrieval.retrieve(owner.id,
                new MemoryRetrievalQuery("数据分析报告", "ACTION_PLAN", null, null, null, false));
        assertEquals(action.id, pack.evidence().get(0).memoryId(),
                "task fit must order the prospective TODO memory first for an ACTION task");
        assertTrue(pack.evidence().get(0).score() > pack.evidence().get(1).score());
    }
}
