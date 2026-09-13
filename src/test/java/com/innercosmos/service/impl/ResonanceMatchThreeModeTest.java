package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.ResonanceMatchStrategy;
import com.innercosmos.service.ResonanceModePreference;
import com.innercosmos.vo.ResonanceMatchExplanationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CP-32 三模式召回与排序测试（closing-checklist §2-8）。
 *
 * <p>确定性、无 LLM、无 embedding provider。夹具刻意使用「纯净」关键词组合（不含跨家族
 * 泄漏词，如「压力」同时属于任务压力/情绪承压），每条断言的 reason 都能从构造的信号复原。
 * 用户 id 全部取独立大号段 99xxxxxxx。
 */
@ExtendWith(MockitoExtension.class)
class ResonanceMatchThreeModeTest {

    @Mock EchoCapsuleMapper echoCapsuleMapper;
    @Mock CapsuleBoundaryMapper boundaryMapper;
    @Mock CapsuleAgent capsuleAgent;
    @Mock MemoryCardMapper memoryCardMapper;
    @Mock UserPortraitMapper userPortraitMapper;
    @Mock AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    @Mock CapsuleGenomeService genomeService;
    @Mock DataUseGrantService dataUseGrantService;
    @Mock BlockRelationMapper blockRelationMapper;
    @Mock com.innercosmos.mapper.CapsuleLandingMapper capsuleLandingMapper;
    @Mock com.innercosmos.mapper.PersonaChatSessionMapper personaChatSessionMapper;
    @Mock com.innercosmos.mapper.PersonaChatMessageMapper personaChatMessageMapper;
    @Mock com.innercosmos.service.CapsuleEmbeddingIndexService capsuleEmbeddingIndexService;
    @Mock com.innercosmos.service.DataRetractionReceiptService retractionReceiptService;

    CapsuleServiceImpl service;

    /** 独立测试用户段：990000001。 */
    static final Long VIEWER = 990000001L;
    static final Long OWNER_A = 990000101L;
    static final Long OWNER_B = 990000102L;

    // 纯净关键词组合（不触发跨家族泄漏）：见 PseudoSemanticAnalyzer.THEME_KEYWORDS。
    static final String TASK = "考试 作业 拖延";
    static final String RELATION = "朋友 同学 吵架";
    static final String HOPE = "梦想 憧憬 向往";
    static final String COGNITIVE = "迷茫 困惑 想不通";

    @BeforeEach
    void setUp() {
        service = new CapsuleServiceImpl(echoCapsuleMapper, boundaryMapper, capsuleLandingMapper,
                personaChatSessionMapper, personaChatMessageMapper, capsuleAgent,
                memoryCardMapper, userPortraitMapper, authorizedMemoryRefMapper, genomeService,
                dataUseGrantService, blockRelationMapper, new com.fasterxml.jackson.databind.ObjectMapper(),
                capsuleEmbeddingIndexService, retractionReceiptService,
                new DataMaskingServiceImpl(memoryCardMapper, authorizedMemoryRefMapper));
        lenient().when(userPortraitMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(blockRelationMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(capsuleEmbeddingIndexService.similarities(any(), any())).thenReturn(Map.of());
        lenient().when(dataUseGrantService.authorize(any(), any())).thenAnswer(invocation -> {
            com.innercosmos.entity.DataUseGrant primary = new com.innercosmos.entity.DataUseGrant();
            primary.id = 9907001L;
            return List.of(primary);
        });
        lenient().when(dataUseGrantService.authorizationsValid(any(), anySet())).thenReturn(true);
    }

    // ----- fixtures -----

    MemoryCard memory(long id, String text, String emotionTags, LocalDateTime createdAt) {
        MemoryCard m = new MemoryCard();
        m.id = id;
        m.userId = VIEWER;
        m.title = "记录";
        m.summary = text;
        m.keywordTags = "";
        m.emotionTags = emotionTags == null ? "" : emotionTags;
        m.status = "ACTIVE";
        m.emotionalGravity = 5.0;
        m.createdAt = createdAt;
        return m;
    }

    EchoCapsule capsule(long id, Long owner, String pseudonym, String introText,
                        String publicTagsJson, Double energy, LocalDateTime createdAt) {
        EchoCapsule c = new EchoCapsule();
        c.id = id;
        c.ownerUserId = owner;
        c.capsuleType = "USER_CAPSULE";
        c.pseudonym = pseudonym;
        c.intro = introText + " 的一段真实感受，来自一位匿名讲述者";
        c.publicTags = publicTagsJson;
        c.echoEnergy = energy;
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.createdAt = createdAt;
        return c;
    }

    void stubMemories(MemoryCard... cards) {
        when(memoryCardMapper.selectList(any())).thenReturn(Arrays.asList(cards));
    }

    void stubPlaza(EchoCapsule... capsules) {
        when(echoCapsuleMapper.selectList(any())).thenReturn(Arrays.asList(capsules));
    }

    Map<String, Object> itemOf(List<Map<String, Object>> result, long capsuleId) {
        return result.stream().filter(it -> idOf(it) == capsuleId).findFirst()
                .orElseThrow(() -> new AssertionError("capsule " + capsuleId + " missing from result"));
    }

    static long idOf(Map<String, Object> item) {
        return ((EchoCapsule) item.get("capsule")).id;
    }

    static ResonanceMatchExplanationVO explanationOf(Map<String, Object> item) {
        return (ResonanceMatchExplanationVO) item.get("modeExplanation");
    }

    // ----- SIMILAR -----

    /** 重合信号 → SIMILAR 标签，reasons 带真实计数，得分构成可核对。 */
    @Test
    void similarMode_labelsAndReasonsFromRealOverlap() {
        stubMemories(
                memory(990000011L, TASK, "", null),
                memory(990000012L, TASK, "", null),
                memory(990000013L, TASK, "", null));
        EchoCapsule overlap = capsule(990001001L, OWNER_A, "回声甲", TASK, "[\"复习\"]", 0.5, null);
        stubPlaza(overlap);

        List<Map<String, Object>> result = service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.SIMILAR);

        Map<String, Object> item = result.get(0);
        assertEquals("SIMILAR", item.get("mode"));
        assertTrue((Boolean) item.get("resonant"), "theme overlap is a real signal -> genuine result");
        ResonanceMatchExplanationVO vo = explanationOf(item);
        assertEquals("SIMILAR", vo.mode);
        assertEquals("相似", vo.modeLabel);
        assertEquals("sufficient", vo.confidence);
        assertTrue(vo.reasons.contains("共同主题：任务压力（你的记忆中出现3次）"),
                "reason must cite the constructed signal with its real count: " + vo.reasons);
        assertEquals(0.34, vo.similarScore, 1e-9);
        assertEquals(0.34, vo.scoreBreakdown.get("共同主题·任务压力"), 1e-9);
        assertEquals(0.0, vo.complementaryScore, 1e-9);
        assertEquals(0.0, vo.unexpectedScore, 1e-9);
        // matchScore keeps the documented relevance+energy+boost shape: 0.34+0.09+0.06 = 0.49
        assertEquals(0.49, ((Number) item.get("matchScore")).doubleValue(), 0.0051);
    }

    /** 诚实性：SIMILAR 候选的 reasons 不得出现未构造的跨域/互补/时段话术。 */
    @Test
    void similarMode_neverFabricatesOtherModeReasons() {
        stubMemories(memory(990000011L, TASK, "", null));
        stubPlaza(capsule(990001002L, OWNER_A, "回声乙", TASK, "[\"复习\"]", 0.5, null));

        List<String> reasons = explanationOf(service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.BALANCED).get(0)).reasons;

        for (String r : reasons) {
            assertTrue(r.startsWith("共同主题：") || r.startsWith("画像印证：") || r.startsWith("语义相近："),
                    "SIMILAR reasons must only cite overlap-family signals, got: " + r);
        }
    }

    // ----- COMPLEMENTARY -----

    /** 双桥接（压力主题 × 缺失的支撑主题）→ COMPLEMENTARY、sufficient、reasons 为定向组合。 */
    @Test
    void complementaryMode_doubleBridgeIsSufficientConfidence() {
        stubMemories(
                memory(990000021L, TASK, "", null),
                memory(990000022L, TASK, "", null),
                memory(990000023L, "焦虑 崩溃 委屈", "", null),
                memory(990000024L, "焦虑 崩溃 委屈", "", null));
        // 认知探索 + 希望期待：同时供给情绪承压与任务压力两个方向。
        EchoCapsule supply = capsule(990001003L, OWNER_B, "远望者",
                COGNITIVE + " " + HOPE, "[\"迷茫\"]", 0.5, null);
        stubPlaza(supply);

        List<Map<String, Object>> result = service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.COMPLEMENTARY);

        Map<String, Object> item = result.get(0);
        assertEquals("COMPLEMENTARY", item.get("mode"));
        assertTrue((Boolean) item.get("resonant"),
                "a genuine complementary signal surfaces as a real (non-backfill) result");
        ResonanceMatchExplanationVO vo = explanationOf(item);
        assertEquals("COMPLEMENTARY", vo.mode);
        assertEquals("sufficient", vo.confidence, "two bridges carry enough signal");
        assertTrue(vo.reasons.contains("互补方向：你的『任务压力』主题 × 对方的『希望期待』主题"),
                "reasons must name the demand x supply pair: " + vo.reasons);
        assertTrue(vo.reasons.contains("互补方向：你的『情绪承压』主题 × 对方的『认知探索』主题"));
        assertEquals(0.60, vo.complementaryScore, 1e-9);
    }

    /** 诚实降权：查看者已拥有该「供给」主题时，桥接不成立——它成了相似而非互补。 */
    @Test
    void complementaryBridgeSuppressedWhenViewerAlreadyOwnsSupplyFamily() {
        stubMemories(
                memory(990000031L, TASK, "", null),
                memory(990000032L, HOPE, "", null)); // 查看者自己也有希望期待
        EchoCapsule hope = capsule(990001004L, OWNER_B, "同行者", HOPE, "[\"梦想\"]", 0.5, null);
        stubPlaza(hope);

        ResonanceMatchExplanationVO vo = explanationOf(service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.BALANCED).get(0));

        assertEquals(0.0, vo.complementaryScore, 1e-9, "bridge must not fire when supply is not novel");
        assertEquals("SIMILAR", vo.mode, "shared 希望期待 makes this a similarity match, honestly labeled");
        assertTrue(vo.reasons.stream().noneMatch(r -> r.contains("×对方的")),
                "no complementary pairing reason may appear: " + vo.reasons);
    }

    // ----- UNEXPECTED -----

    /** 主题域完全不重合 + 两条跨域信号（情绪词重合 + 记录时段相近）→ UNEXPECTED、weak、触顶。 */
    @Test
    void unexpectedMode_combinesEmotionAndRhythmSignals() {
        stubMemories(
                memory(990000041L, RELATION, "低落", LocalDateTime.of(2026, 9, 1, 23, 30)),
                memory(990000042L, RELATION, "低落", LocalDateTime.of(2026, 9, 2, 0, 10)),
                memory(990000043L, RELATION, "低落", LocalDateTime.of(2026, 9, 3, 1, 0)),
                memory(990000044L, RELATION, "低落", LocalDateTime.of(2026, 9, 4, 14, 0))); // 3 夜 : 1 日
        EchoCapsule crossDomain = capsule(990001005L, OWNER_B, "夜航者",
                COGNITIVE, "[\"低落\"]", 0.5, LocalDateTime.of(2026, 9, 5, 23, 10));
        stubPlaza(crossDomain);

        List<Map<String, Object>> result = service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.UNEXPECTED);

        Map<String, Object> item = result.get(0);
        assertEquals("UNEXPECTED", item.get("mode"));
        assertTrue((Boolean) item.get("resonant"),
                "a real cross-domain signal surfaces the candidate instead of anonymous backfill");
        ResonanceMatchExplanationVO vo = explanationOf(item);
        assertEquals("UNEXPECTED", vo.mode);
        assertEquals("weak", vo.confidence, "cross-domain proxies are single-signal -> always weak");
        assertTrue(vo.reasons.contains("跨域信号：主题域不同，但双方都带有『低落』的情绪痕迹"),
                "emotion-tag overlap must be cited verbatim: " + vo.reasons);
        assertTrue(vo.reasons.contains("跨域信号：你的记忆多在夜间形成（3/4条），对方共鸣体也创建于夜间"),
                "rhythm reason must cite the real bucket counts: " + vo.reasons);
        assertEquals(0.55, vo.unexpectedScore, 1e-9, "0.40 + 0.30 capped at the documented 0.55");
        assertEquals(0.0, vo.similarScore, 1e-9, "theme families are disjoint by construction");
    }

    /** 时段信号只在桶一致时成立：共鸣体创建于日间则该 reason 不得出现。 */
    @Test
    void unexpectedMode_rhythmOnlyFiresWhenBucketsMatch() {
        stubMemories(
                memory(990000041L, RELATION, "低落", LocalDateTime.of(2026, 9, 1, 23, 30)),
                memory(990000042L, RELATION, "低落", LocalDateTime.of(2026, 9, 2, 0, 10)),
                memory(990000043L, RELATION, "低落", LocalDateTime.of(2026, 9, 3, 1, 0)));
        EchoCapsule dayTimeCapsule = capsule(990001006L, OWNER_B, "白昼者",
                COGNITIVE, "[\"低落\"]", 0.5, LocalDateTime.of(2026, 9, 5, 14, 0));
        stubPlaza(dayTimeCapsule);

        ResonanceMatchExplanationVO vo = explanationOf(service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.UNEXPECTED).get(0));

        assertEquals("UNEXPECTED", vo.mode);
        assertEquals(0.40, vo.unexpectedScore, 1e-9, "only the emotion signal fires");
        assertTrue(vo.reasons.stream().anyMatch(r -> r.contains("情绪痕迹")));
        assertTrue(vo.reasons.stream().noneMatch(r -> r.contains("记录时段") || r.contains("夜间形成")),
                "rhythm reason must not appear when the capsule was created in the other bucket: "
                        + vo.reasons);
    }

    // ----- insufficient_signal -----

    /** 冷启动（无记忆）→ 每个候选 mode=null、insufficient_signal、无理由、纯补充位。 */
    @Test
    void insufficientSignal_coldStartViewer() {
        stubMemories();
        EchoCapsule any = capsule(990001007L, OWNER_A, "任意回声", TASK, "[\"复习\"]", 0.5,
                LocalDateTime.of(2026, 9, 1, 23, 0));
        stubPlaza(any);

        List<Map<String, Object>> result = service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.UNEXPECTED);

        Map<String, Object> item = result.get(0);
        assertNull(item.get("mode"), "no trajectory -> no honest mode label");
        assertFalse((Boolean) item.get("resonant"), "cold start stays explicit backfill");
        ResonanceMatchExplanationVO vo = explanationOf(item);
        assertEquals("insufficient_signal", vo.confidence);
        assertTrue(vo.reasons.isEmpty(), "no fabricated reasons for a cold-start viewer");
        assertTrue(vo.scoreBreakdown.isEmpty());
        assertEquals(0.0, ((Number) item.get("modeRelevance")).doubleValue(), 1e-9);
    }

    /** 非冷启动但无任何模式信号（不重合/无桥/无跨域）→ 同样 insufficient_signal，不硬贴标签。 */
    @Test
    void insufficientSignal_whenNoModeSignalFires() {
        stubMemories(memory(990000051L, TASK, "", null)); // 无情绪标签、无时间戳
        // 认知探索-only：不重合、无桥接供给、无公开标签交集、无创建时间。
        EchoCapsule unrelated = capsule(990001008L, OWNER_B, "远方", COGNITIVE, "[\"思考\"]", 0.5, null);
        stubPlaza(unrelated);

        List<Map<String, Object>> result = service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.BALANCED);

        Map<String, Object> item = result.get(0);
        assertNull(item.get("mode"));
        assertEquals("insufficient_signal", explanationOf(item).confidence);
        assertTrue(explanationOf(item).reasons.isEmpty(), "prefer no label over an invented one");
        assertFalse((Boolean) item.get("resonant"));
    }

    // ----- 偏好与排序 -----

    /** 模式偏好重塑排序：BALANCED 按最强模式（相似 0.34 > 互补 0.30）；指定 COMPLEMENTARY 时互补候选登顶。 */
    @Test
    void modePreferenceReshapesRanking() {
        stubMemories(
                memory(990000061L, TASK, "", null),
                memory(990000062L, TASK, "", null));
        EchoCapsule similar = capsule(990001009L, OWNER_A, "同类", TASK, "[\"复习\"]", 0.5, null);
        EchoCapsule complementary = capsule(990001010L, OWNER_B, "远望", HOPE, "[\"梦想\"]", 0.5, null);
        stubPlaza(similar, complementary);

        List<Long> balanced = service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR,
                        ResonanceModePreference.BALANCED).stream().map(ResonanceMatchThreeModeTest::idOf).toList();
        assertEquals(List.of(990001009L, 990001010L), balanced,
                "BALANCED ranks by dominant mode: similar 0.34 edges complementary 0.30");

        List<Map<String, Object>> complementaryFirst = service.matchedCapsules(VIEWER,
                ResonanceMatchStrategy.MIRROR, ResonanceModePreference.COMPLEMENTARY);
        assertEquals(990001010L, idOf(complementaryFirst.get(0)), "complementary preference promotes the bridge candidate");
        assertEquals("COMPLEMENTARY", complementaryFirst.get(0).get("mode"));
        assertEquals(0.45, ((Number) complementaryFirst.get(0).get("matchScore")).doubleValue(), 0.0051);
        Map<String, Object> demotedSimilar = itemOf(complementaryFirst, 990001009L);
        assertEquals("SIMILAR", demotedSimilar.get("mode"),
                "label stays truthful about the candidate's real dominant mode");
        assertEquals(0.15, ((Number) demotedSimilar.get("matchScore")).doubleValue(), 0.0051,
                "zero complementary signal -> backfill-level score under this preference");

        List<Long> similarFirst = service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR,
                        ResonanceModePreference.SIMILAR).stream().map(ResonanceMatchThreeModeTest::idOf).toList();
        assertEquals(List.of(990001009L, 990001010L), similarFirst);
        assertTrue((Boolean) itemOf(service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR,
                ResonanceModePreference.SIMILAR), 990001009L).get("resonant"));
        assertFalse((Boolean) itemOf(service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR,
                ResonanceModePreference.SIMILAR), 990001010L).get("resonant"),
                "zero similar signal under SIMILAR preference -> explicit backfill");
    }

    // ----- 稳定性与兼容 -----

    /** 同输入同输出：两次调用产出完全一致的 id/score/mode/reasons 序列。 */
    @Test
    void deterministic_sameInputSameOutput() {
        stubMemories(
                memory(990000071L, TASK, "低落", LocalDateTime.of(2026, 9, 1, 23, 30)),
                memory(990000072L, TASK, "低落", LocalDateTime.of(2026, 9, 2, 23, 30)),
                memory(990000073L, RELATION, "", null));
        stubPlaza(
                capsule(990001011L, OWNER_A, "同类", TASK, "[\"复习\"]", 0.5, null),
                capsule(990001012L, OWNER_B, "远望", HOPE, "[\"低落\"]", 0.6, LocalDateTime.of(2026, 9, 5, 2, 0)),
                capsule(990001013L, OWNER_A, "远方", COGNITIVE, "[\"思考\"]", 0.4, null));

        List<Map<String, Object>> first = service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR,
                ResonanceModePreference.BALANCED);
        List<Map<String, Object>> second = service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR,
                ResonanceModePreference.BALANCED);

        for (List<Map<String, Object>> result : List.of(first, second)) {
            assertEquals(3, result.size());
        }
        for (int i = 0; i < first.size(); i++) {
            assertEquals(idOf(first.get(i)), idOf(second.get(i)), "stable order at slot " + i);
            assertEquals(first.get(i).get("matchScore"), second.get(i).get("matchScore"));
            assertEquals(first.get(i).get("mode"), second.get(i).get("mode"));
            assertEquals(first.get(i).get("modeRelevance"), second.get(i).get("modeRelevance"));
            assertEquals(explanationOf(first.get(i)).reasons, explanationOf(second.get(i)).reasons);
        }
    }

    /** 兼容守卫：双参遗留路径分数公式不变（0.15 无地板），同时已携带三模式解释键。 */
    @Test
    void legacyTwoArgPath_keepsLegacyScoreAndAddsModeKeys() {
        stubMemories(memory(990000081L, TASK, "", null));
        EchoCapsule noise = capsule(990001014L, OWNER_B, "梦想家", HOPE, "[\"梦想\"]", 0.5, null);
        stubPlaza(noise);

        List<Map<String, Object>> result = service.matchedCapsules(VIEWER, ResonanceMatchStrategy.MIRROR);

        Map<String, Object> item = result.get(0);
        assertEquals(0.15, ((Number) item.get("matchScore")).doubleValue(), 0.0051,
                "legacy path keeps userBoost + energyScore exactly (no mode re-scoring)");
        assertFalse((Boolean) item.get("resonant"));
        assertTrue(item.containsKey("modeExplanation"), "explanations are attached on the legacy path too");
        assertEquals("COMPLEMENTARY", item.get("mode"), "bridge signal is real even for backfill scoring");
    }
}
