package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.ResonanceMatchStrategy;
import com.innercosmos.service.DataUseGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @Mock AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    @Mock CapsuleGenomeService genomeService;
    @Mock DataUseGrantService dataUseGrantService;

    CapsuleServiceImpl service;

    static final Long USER_ID = 100L;
    static final Set<String> FAMILIES = Set.of(
            "任务压力", "关系牵动", "情绪承压", "认知探索", "自我评价", "希望期待");

    @BeforeEach
    void setUp() {
        service = new CapsuleServiceImpl(echoCapsuleMapper, boundaryMapper, capsuleAgent,
                memoryCardMapper, userPortraitMapper, authorizedMemoryRefMapper, genomeService, dataUseGrantService,
                new com.fasterxml.jackson.databind.ObjectMapper());
        // default: no portrait rows unless a test overrides
        lenient().when(userPortraitMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(dataUseGrantService.authorize(any(), any())).thenAnswer(invocation -> {
            com.innercosmos.entity.DataUseGrant primary = new com.innercosmos.entity.DataUseGrant();
            primary.id = 7001L;
            com.innercosmos.entity.DataUseGrant provider = new com.innercosmos.entity.DataUseGrant();
            provider.id = 7002L;
            return List.of(primary, provider);
        });
        lenient().when(dataUseGrantService.authorizationsValid(any(), anySet())).thenReturn(true);
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

    /**
     * FIX-A: a zero-overlap, high-energy capsule must NEVER outrank a low-energy but
     * genuinely relevant (theme-overlapping) capsule. relevance = themeOverlap + portraitSignal;
     * seed/energy are NOT relevance. Relevant capsules always sort before irrelevant ones.
     */
    @Test
    void relevantCapsulesRankAboveIrrelevant() {
        // user themes: 任务压力 + 情绪承压
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力 累", "考试,拖延", "焦虑"));

        // relevant but LOW energy: shares 任务压力 / 情绪承压, energy 0.10
        EchoCapsule relevant = capsule(801L, 999L, "USER_CAPSULE", "同路",
                "考试复习的拖延和压力", "[\"考试\",\"压力\"]", 0.10);
        // irrelevant but VERY HIGH energy + SEED boost: 希望期待 only, zero family overlap
        EchoCapsule irrelevant = capsule(802L, 999L, "SEED_CAPSULE", "梦想家",
                "梦想 期待 憧憬 开心", "[\"希望\",\"梦想\"]", 0.99);
        stubPlaza(irrelevant, relevant); // intentionally irrelevant first in plaza order

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        assertEquals(801L, idOf(result.get(0)),
                "relevant low-energy capsule must rank above zero-overlap high-energy capsule");
        assertTrue((Boolean) result.get(0).get("resonant"), "relevant capsule flagged resonant=true");
        assertFalse((Boolean) result.get(1).get("resonant"), "irrelevant capsule flagged resonant=false");
    }

    /**
     * FIX-A: with >=12 genuinely relevant capsules, a zero-relevance capsule must NOT appear —
     * irrelevant capsules only backfill remaining slots, never crowd out relevant ones.
     */
    @Test
    void irrelevantOnlyBackfillsRemainingSlots() {
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力 累", "考试,拖延", "焦虑"));

        List<EchoCapsule> all = new ArrayList<>();
        // 12 relevant capsules (share 任务压力 / 情绪承压)
        for (int i = 0; i < 12; i++) {
            all.add(capsule(900 + i, 999L, "USER_CAPSULE", "同路" + i,
                    "考试复习的拖延和压力", "[\"考试\",\"压力\"]", 0.5));
        }
        // 1 zero-relevance capsule with maximal energy + seed boost
        EchoCapsule irrelevant = capsule(999L, 998L, "SEED_CAPSULE", "梦想家",
                "梦想 期待 憧憬 开心", "[\"希望\",\"梦想\"]", 0.99);
        all.add(irrelevant);
        stubPlaza(all.toArray(new EchoCapsule[0]));

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        assertEquals(12, result.size(), "top-12 cap");
        assertTrue(result.stream().noneMatch(it -> idOf(it) == 999L),
                "with 12 relevant capsules, the zero-relevance capsule must NOT backfill");
        assertTrue(result.stream().allMatch(it -> (Boolean) it.get("resonant")),
                "every returned capsule must be resonant when 12 relevant exist");
    }

    /**
     * FIX-A: graceful cold-start — a user with ZERO overlap against every capsule still gets a
     * non-empty list (irrelevant capsules backfill so the plaza is never empty).
     */
    @Test
    void sparseUser_stillGetsResults() {
        // user themes: 希望期待 only
        stubMemories(memory(1, "憧憬", "梦想 期待 憧憬 开心 希望", "梦想", "开心"));

        // capsules share ZERO families with the user (all 任务压力 / 情绪承压)
        EchoCapsule a = capsule(1001L, 999L, "USER_CAPSULE", "压力1",
                "考试复习的拖延和压力", "[\"考试\"]", 0.6);
        EchoCapsule b = capsule(1002L, 999L, "USER_CAPSULE", "压力2",
                "工作任务的截止和焦虑", "[\"任务\"]", 0.4);
        stubPlaza(a, b);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID);

        assertFalse(result.isEmpty(), "sparse/cold-start user must still get a non-empty list");
        assertTrue(result.stream().noneMatch(it -> (Boolean) it.get("resonant")),
                "all results are non-resonant backfill for a zero-overlap user");
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

    @Test
    void complement_surfacesThemesMissingFromCurrentTrajectory() {
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力", "考试", "焦虑"));
        EchoCapsule hope = capsule(1101L, 999L, "USER_CAPSULE", "远望",
                "梦想 希望 期待 憧憬 开心", "[\"希望\"]", 0.5);
        EchoCapsule same = capsule(1102L, 999L, "USER_CAPSULE", "同路",
                "考试复习的拖延和压力", "[\"考试\"]", 0.9);
        stubPlaza(same, hope);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID, ResonanceMatchStrategy.COMPLEMENT);

        assertEquals(1101L, idOf(result.get(0)));
        assertEquals("COMPLEMENT", result.get(0).get("strategy"));
        assertTrue(reasonsOf(result.get(0)).contains("带来·希望期待"));
    }

    @Test
    void growthEdge_explainsTheBridgeInsteadOfCallingItSimilarity() {
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力", "考试", "焦虑"));
        EchoCapsule hope = capsule(1201L, 999L, "USER_CAPSULE", "远望",
                "梦想 希望 期待 憧憬 开心", "[\"希望\"]", 0.5);
        stubPlaza(hope);

        Map<String, Object> item = service.matchedCapsules(USER_ID, ResonanceMatchStrategy.GROWTH_EDGE).get(0);

        assertTrue((Boolean) item.get("resonant"));
        assertTrue(reasonsOf(item).contains("任务压力 → 希望期待"));
        assertTrue(String.valueOf(item.get("matchSummary")).contains("成长边缘"));
    }

    @Test
    void contextual_prefersPortraitThemesOverGeneralHistory() {
        stubMemories(memory(1, "复习", "考试 作业 拖延 压力", "考试", "焦虑"));
        when(userPortraitMapper.selectList(any())).thenReturn(List.of(
                portrait("CURRENT_STATE", "朋友 关系 沟通 边界")));
        EchoCapsule relation = capsule(1301L, 999L, "USER_CAPSULE", "边界",
                "朋友关系里的沟通与边界", "[\"关系\"]", 0.4);
        EchoCapsule task = capsule(1302L, 999L, "USER_CAPSULE", "任务",
                "考试作业和拖延压力", "[\"考试\"]", 0.9);
        stubPlaza(task, relation);

        List<Map<String, Object>> result = service.matchedCapsules(USER_ID, ResonanceMatchStrategy.CONTEXTUAL);

        assertEquals(1301L, idOf(result.get(0)));
        assertTrue(reasonsOf(result.get(0)).contains("此刻·关系牵动"));
    }

    @Test
    void serendipity_isDeterministicAndExplicitlyExploratory() {
        stubMemories();
        stubPlaza(capsule(1401L, 999L, "USER_CAPSULE", "偶遇", "梦想与朋友", "[]", 0.5));

        Map<String, Object> first = service.matchedCapsules(USER_ID, ResonanceMatchStrategy.SERENDIPITY).get(0);
        Map<String, Object> second = service.matchedCapsules(USER_ID, ResonanceMatchStrategy.SERENDIPITY).get(0);

        assertEquals(scoreOf(first), scoreOf(second));
        assertEquals("为熟悉轨迹留出意外", reasonsOf(first).get(0));
    }

    @Test
    void emptyMemorySelectionNeverImplicitlyConsumesTopMemories() {
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "无记忆回声";
        request.memoryIds = List.of();
        request.visibilityStatus = "PRIVATE";
        when(echoCapsuleMapper.insert(any(EchoCapsule.class))).thenAnswer(invocation -> {
            ((EchoCapsule) invocation.getArgument(0)).id = 3001L; return 1;
        });

        service.createFromMemory(USER_ID, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> summaries = ArgumentCaptor.forClass(List.class);
        verify(capsuleAgent).generateUserPersona(eq(USER_ID), summaries.capture(), eq("无记忆回声"), any());
        assertTrue(summaries.getValue().isEmpty());
        verify(memoryCardMapper, never()).selectList(any());
        verifyNoInteractions(authorizedMemoryRefMapper);
    }

    @Test
    void explicitCurrentOwnerMemoryCreatesAuditableAuthorizationAndArchiveWithdrawsIt() {
        MemoryCard card = memory(77L, "允许的片段", "只授权这段抽象经历", "", "");
        when(memoryCardMapper.selectById(77L)).thenReturn(card);
        when(echoCapsuleMapper.insert(any(EchoCapsule.class))).thenAnswer(invocation -> {
            ((EchoCapsule) invocation.getArgument(0)).id = 3002L; return 1;
        });
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(77L);
        request.visibilityStatus = "PUBLIC";

        EchoCapsule created = service.createFromMemory(USER_ID, request);

        ArgumentCaptor<AuthorizedMemoryRef> inserted = ArgumentCaptor.forClass(AuthorizedMemoryRef.class);
        verify(authorizedMemoryRefMapper).insert(inserted.capture());
        assertEquals(3002L, inserted.getValue().capsuleId);
        assertEquals(77L, inserted.getValue().memoryCardId);
        assertEquals(7001L, inserted.getValue().dataUseGrantId);
        assertEquals("AUTHORIZED", inserted.getValue().authorizationStatus);
        assertEquals("[\"77\"]", created.authorizedMemoryIds);
        InOrder egressOrder = inOrder(dataUseGrantService, capsuleAgent);
        egressOrder.verify(dataUseGrantService).authorize(any(), eq(card));
        egressOrder.verify(capsuleAgent).generateUserPersona(eq(USER_ID), any(), any(), any());

        when(echoCapsuleMapper.selectOne(any())).thenReturn(created);
        when(authorizedMemoryRefMapper.selectList(any())).thenReturn(List.of(inserted.getValue()));
        service.archiveCapsule(USER_ID, created.id);

        assertFalse(created.isPublic);
        assertEquals("ARCHIVED", created.visibilityStatus);
        assertEquals("[]", created.authorizedMemoryIds);
        assertEquals("WITHDRAWN", inserted.getValue().authorizationStatus);
        verify(authorizedMemoryRefMapper).updateById(inserted.getValue());
    }
}
