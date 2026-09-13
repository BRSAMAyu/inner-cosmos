package com.innercosmos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.entity.RelationMention;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.vo.RelationInteractionReviewVO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * CP-34: the interaction review replaces the old relation temperature score. The review
 * counts real tb_relation_mention rows (mentions, distinct active weeks, emotion
 * spectrum, recent triggers) and NEVER invents a number: an empty window is an honest
 * empty review — the old code returned a fabricated 0.5 "health" for exactly that case.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class RelationInteractionReviewTest {

    private static final AtomicLong USERS = new AtomicLong(98_700_000);

    @Autowired RelationNetworkService relations;
    @Autowired RelationMentionMapper mapper;
    @Autowired WebApplicationContext context;

    private long freshUser() {
        return USERS.incrementAndGet();
    }

    private void mention(long user, String label, String emotions, String trigger, int daysAgo) {
        RelationMention mention = new RelationMention();
        mention.userId = user;
        mention.relationLabel = label;
        mention.emotionTags = emotions;
        mention.triggerSummary = trigger;
        mention.createdAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(daysAgo);
        mapper.insert(mention);
    }

    @Test
    void emptyWindowIsAnHonestEmptyReviewNotAFabricatedMidScore() {
        long user = freshUser();
        RelationInteractionReviewVO review = relations.interactionReview(user, "妈妈", 4);
        assertEquals(0, review.mentionCount);
        assertEquals(0, review.weeksActive);
        assertTrue(review.emotionSpectrum.isEmpty(), "no data -> no spectrum, no invented tags");
        assertTrue(review.recentTriggers.isEmpty());
        // The old /health fabricated 0.5 here; the review carries no score field at all
        // (test 3 asserts the same at the JSON boundary).
    }

    @Test
    void countsComeFromRealRowsInsideTheWindowOnly() {
        long user = freshUser();
        mention(user, "妈妈", "温暖,牵挂", "深夜通话后写下", 2);
        mention(user, "妈妈", "牵挂", "周末回家吃饭", 9);
        mention(user, "妈妈", "温暖", "上周的旧事", 40); // outside a 4-week window
        mention(user, "同事", "疲惫", "另一段关系，不参与计数", 1);

        RelationInteractionReviewVO review = relations.interactionReview(user, "妈妈", 4);
        assertEquals(2, review.mentionCount);
        assertTrue(review.weeksActive >= 1 && review.weeksActive <= 2);
        assertEquals(1L, review.emotionSpectrum.get("温暖"));
        assertEquals(2L, review.emotionSpectrum.get("牵挂"));
        assertEquals("牵挂", review.emotionSpectrum.keySet().iterator().next(),
                "spectrum is sorted by real counts descending");
        assertEquals(2, review.recentTriggers.size());
        assertEquals("深夜通话后写下", review.recentTriggers.get(0), "newest first");
    }

    @Test
    void controllerServesTheReviewAndTheOldScoreEndpointIsGone() throws Exception {
        long user = freshUser();
        mention(user, "朋友", "平静", "一次散步", 1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(com.innercosmos.common.Constants.SESSION_USER_KEY, user);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/relation/review").param("label", "朋友").param("weeks", "4")
                        .session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("mentionCount"));
        assertTrue(body.contains("\"emotionSpectrum\""));
        assertFalse(body.contains("healthScore"), "the evaluative score must not survive");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/relation/health").param("label", "朋友").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isNotFound());
    }
}
