package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * IC-CAP-003 smart-matching tests. Deterministic, no LLM.
 * RED-before expectations are documented per test.
 */
@ExtendWith(MockitoExtension.class)
class CapsuleMatchingTest {

    @Mock EchoCapsuleMapper echoCapsuleMapper;
    @Mock CapsuleBoundaryMapper boundaryMapper;
    @Mock CapsuleAgent capsuleAgent;
    @Mock MemoryCardMapper memoryCardMapper;
    @Mock UserPortraitMapper userPortraitMapper;

    CapsuleServiceImpl service;

    static final Long USER_ID = 100L;
    static final Set<String> FAMILIES = Set.of(
            "任务压力", "关系牵动", "情绪承压", "认知探索", "自我评价", "希望期待");

    @BeforeEach
    void setUp() {
        service = new CapsuleServiceImpl(echoCapsuleMapper, boundaryMapper, capsuleAgent,
                memoryCardMapper, userPortraitMapper);
        // default: no portrait rows unless a test overrides
        lenient().when(userPortraitMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    // ----- helpers -----

    MemoryCard memory(long id, String title, String summary, String keywordTags, String emotionTags) {
        MemoryCard m = new MemoryCard();
        m.id = id;
        m.userId = USER_ID;
        m.title = title;
        m.summary = summary;
        m.keywordTags = keywordTags;
        m.emotionTags = emotionTags;
        m.status = "ACTIVE";
        m.emotionalGravity = 5.0;
        return m;
    }

    EchoCapsule capsule(long id, Long owner, String type, String pseudonym, String intro,
                        String publicTags, Double energy) {
        EchoCapsule c = new EchoCapsule();
        c.id = id;
        c.ownerUserId = owner;
        c.capsuleType = type;
        c.pseudonym = pseudonym;
        c.intro = intro;
        c.publicTags = publicTags;
        c.echoEnergy = energy;
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        return c;
    }

    UserPortrait portrait(String dim, String valueJson) {
        UserPortrait p = new UserPortrait();
        p.userId = USER_ID;
        p.dim = dim;
        p.valueJson = valueJson;
        return p;
    }

    void stubMemories(MemoryCard... cards) {
        when(memoryCardMapper.selectList(any())).thenReturn(Arrays.asList(cards));
    }

    void stubPlaza(EchoCapsule... capsules) {
        when(echoCapsuleMapper.selectList(any())).thenReturn(Arrays.asList(capsules));
    }

    @SuppressWarnings("unchecked")
    List<String> reasonsOf(Map<String, Object> item) {
        return (List<String>) item.get("matchReasons");
    }

    double scoreOf(Map<String, Object> item) {
        return ((Number) item.get("matchScore")).doubleValue();
    }

    long idOf(Map<String, Object> item) {
        return ((EchoCapsule) item.get("capsule")).id;
    }

    // ----- tests -----

    /** 1. zero-overlap noise capsule must drop out of top-12 (no floor). */
    @Test
    void zeroOverlap_dropsBelowTopN() {
        // User themes: 任务压力 (考试/作业/拖延) + 情绪承压 (压力/累)
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力 累", "考试,拖延", "焦虑"));

        List<EchoCapsule> all = new ArrayList<>();
        // 12 strong-overlap capsules
        for (int i = 0; i < 12; i++) {
            all.add(capsule(200 + i, 999L, "USER_CAPSULE", "回声" + i,
                    "考试复习的拖延和压力", "[\"考试\",\"压力\"]", 0.8));
        }
        // 1 noise capsule: 希望期待 only, shares ZERO families with user
        EchoCapsule noise = capsule(500L, 999L, "USER_CAPSULE", "梦想家",
                "梦想 希望 期待 憧憬 开心", "[\"希望\",\"梦想\"]", 0.5);
        all.add(noise);
        stubPlaza(all.toArray(new EchoCapsule[0]));

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        assertEquals(12, result.size(), "top-12 cap");
        boolean noisePresent = result.stream().anyMatch(it -> idOf(it) == 500L);
        assertFalse(noisePresent, "zero-overlap noise capsule must NOT be in top-12 (no floor)");
    }

    /**
     * 1b. Tight floor-removal guard. Small plaza (<12) so every capsule is returned regardless of
     * rank, isolating the SCORE rather than the ranking. A zero-overlap USER noise capsule with
     * empty portrait must score exactly seedBoost + energyScore = 0.06 + (0.5*0.18=0.09) = 0.15,
     * which is BELOW the removed 0.16 floor. Under the old floor it would have been clamped to
     * >= 0.16 — so `noiseScore < 0.16` is RED-before-the-fix and GREEN now.
     */
    @Test
    void noOverlapCapsule_scoresNearSeedBoost_noFloor() {
        // User themes: 任务压力 + 情绪承压, NO 希望期待.
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力 累", "考试,拖延", "焦虑"));
        // empty portrait => portraitSignal = 0
        when(userPortraitMapper.selectList(any())).thenReturn(new ArrayList<>());

        // overlapping capsule: shares 任务压力 / 情绪承压
        EchoCapsule overlap = capsule(700L, 999L, "USER_CAPSULE", "同路",
                "考试复习的拖延和压力", "[\"考试\",\"压力\"]", 0.5);
        // noise capsule: 希望期待 only — user shares ZERO families with it. USER type, energy 0.5.
        EchoCapsule noise = capsule(701L, 999L, "USER_CAPSULE", "梦想家",
                "梦想 期待 憧憬 开心", "[\"希望\",\"梦想\"]", 0.5);
        stubPlaza(overlap, noise);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        // small plaza (<12) => both capsules returned regardless of score.
        assertEquals(2, result.size(), "small plaza returns all capsules");

        Map<String, Object> noiseItem = result.stream()
                .filter(it -> idOf(it) == 701L).findFirst()
                .orElseThrow(() -> new AssertionError("noise capsule must be present in small plaza"));
        Map<String, Object> overlapItem = result.stream()
                .filter(it -> idOf(it) == 700L).findFirst()
                .orElseThrow(() -> new AssertionError("overlap capsule must be present"));

        double noiseScore = scoreOf(noiseItem);
        // expected = themeOverlap(0) + portraitSignal(0) + energyScore(0.5*0.18=0.09) + seedBoost(0.06) = 0.15
        assertEquals(0.15, noiseScore, 0.0051,
                "zero-overlap noise score == seedBoost + energyScore (0.15), no floor");
        // RED-before-the-fix guard: old 0.16 floor would have clamped this up to >= 0.16.
        assertTrue(noiseScore < 0.16,
                "noise score must be below the removed 0.16 floor (was " + noiseScore + ")");
        // overlap capsule must out-score the noise capsule.
        assertTrue(scoreOf(overlapItem) > noiseScore,
                "overlapping capsule must score strictly above zero-overlap noise capsule");
    }

    /** 2. SEED outranks USER capsule given identical overlap+energy (seedBoost direction). */
    @Test
    void seedSemanticMatch_ranksAboveUserCapsule() {
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力", "考试", "焦虑"));

        EchoCapsule seed = capsule(201L, 999L, "SEED_CAPSULE", "种子",
                "考试复习的拖延和压力", "[\"考试\"]", 0.8);
        EchoCapsule user = capsule(202L, 999L, "USER_CAPSULE", "用户",
                "考试复习的拖延和压力", "[\"考试\"]", 0.8);
        stubPlaza(seed, user);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        long firstId = idOf(result.get(0));
        long secondId = idOf(result.get(1));
        assertEquals(201L, firstId, "SEED should rank above USER capsule");
        assertEquals(202L, secondId);
        assertTrue(scoreOf(result.get(0)) > scoreOf(result.get(1)),
                "SEED matchScore must strictly exceed USER given identical overlap+energy");
    }

    /** 3. more theme overlap => higher rank and score. */
    @Test
    void themeOverlap_drivesRanking() {
        // user: 任务压力 + 情绪承压 + 关系牵动
        stubMemories(memory(1, "状态", "考试 拖延 压力 累 朋友 吵架", "考试,朋友", "焦虑"));

        // high: shares 任务压力 + 关系牵动 + 情绪承压
        EchoCapsule high = capsule(301L, 999L, "USER_CAPSULE", "全面",
                "考试拖延的压力和朋友吵架的焦虑", "[\"考试\",\"朋友\"]", 0.7);
        // low: shares only 任务压力
        EchoCapsule low = capsule(302L, 999L, "USER_CAPSULE", "单一",
                "考试复习", "[\"考试\"]", 0.7);
        stubPlaza(high, low);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        assertEquals(301L, idOf(result.get(0)), "higher overlap capsule ranks first");
        assertTrue(scoreOf(result.get(0)) > scoreOf(result.get(1)),
                "higher overlap => higher matchScore");
    }

    /** 4. portrait signal contributes positively. */
    @Test
    void portraitSignal_contributes() {
        stubMemories(memory(1, "复习", "考试 拖延 压力", "考试", "焦虑"));
        EchoCapsule cap = capsule(401L, 999L, "USER_CAPSULE", "种子",
                "考试复习的拖延和压力", "[\"考试\"]", 0.7);

        // WITHOUT portrait (default empty)
        stubPlaza(cap);
        double without = scoreOf(service.matchedCapsules(USER_ID).get(0));

        // WITH portrait whose dims carry matching themes (任务压力)
        when(userPortraitMapper.selectList(any())).thenReturn(List.of(
                portrait("CURRENT_STATE", "{\"text\":\"考试 拖延 压力\"}"),
                portrait("EMOTION_PATTERN", "{\"text\":\"焦虑 累 压力\"}")));
        double with = scoreOf(service.matchedCapsules(USER_ID).get(0));

        assertTrue(with > without,
                "matchScore WITH portrait signal must exceed WITHOUT (" + with + " vs " + without + ")");
    }

    /** 5. dynamic echoEnergy (Phase B) drives ranking. */
    @Test
    void energyScore_usesDynamicEnergy() {
        stubMemories(memory(1, "复习", "考试 拖延 压力", "考试", "焦虑"));

        EchoCapsule hi = capsule(501L, 999L, "USER_CAPSULE", "高能",
                "考试复习的拖延和压力", "[\"考试\"]", 0.95);
        EchoCapsule lo = capsule(502L, 999L, "USER_CAPSULE", "低能",
                "考试复习的拖延和压力", "[\"考试\"]", 0.35);
        stubPlaza(hi, lo);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        assertEquals(501L, idOf(result.get(0)), "higher echoEnergy ranks first");
        assertTrue(scoreOf(result.get(0)) > scoreOf(result.get(1)),
                "higher dynamic energy => higher matchScore");
    }

    /** 6. matchReasons are human theme families, not raw keywords. */
    @Test
    void matchReasons_areHumanThemes() {
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力 累", "考试", "焦虑"));
        EchoCapsule cap = capsule(601L, 999L, "USER_CAPSULE", "种子",
                "考试复习的拖延和压力", "[\"考试\",\"压力\"]", 0.7);
        stubPlaza(cap);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);
        List<String> reasons = reasonsOf(result.get(0));

        assertFalse(reasons.isEmpty(), "matchReasons should be non-empty on overlap");
        for (String r : reasons) {
            assertTrue(FAMILIES.contains(r),
                    "matchReason '" + r + "' must be one of the 6 theme families, not a raw keyword");
        }
    }
}
