package com.innercosmos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.dto.CapsuleSandboxFeedbackRequest;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.vo.CapsuleSandboxVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class CapsuleGenomeServiceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CapsuleService capsuleService;
    @Autowired CapsuleGenomeService genomeService;
    @Autowired PersonaChatService personaChatService;
    @Autowired CapsuleSandboxService sandboxService;
    @Autowired ObjectMapper objectMapper;

    @Test
    @Transactional
    void boundaryCompareAndSetRejectsStaleWriter() {
        Long owner = seedUser("boundary-cas");
        Long memory = seedMemory(owner, "允许用于边界并发测试的摘要", "AURORA_PRIVATE");
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleBoundary initial = capsuleService.getBoundary(owner, capsule.id);
        assertEquals(1, initial.version);

        CapsuleBoundary change = new CapsuleBoundary();
        change.maxConversationTurns = 8;
        CapsuleBoundary updated = capsuleService.updateBoundary(owner, capsule.id, change, initial.version);

        assertEquals(2, updated.version);
        assertEquals(8, updated.maxConversationTurns);
        BusinessException stale = assertThrows(BusinessException.class,
                () -> capsuleService.updateBoundary(owner, capsule.id, change, initial.version));
        assertEquals("CONFLICT", stale.code);
    }

    @Test
    @Transactional
    void versionChainRequiresReviewBeforeRepublishAndWithdrawsSelectedVersion() {
        Long owner = seedUser("genome-owner");
        Long stranger = seedUser("genome-stranger");
        String privateSummary = "不应进入授权快照的私人原文-" + System.nanoTime();
        Long memoryId = seedMemory(owner, privateSummary, "AURORA_PRIVATE");

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "雨后回声";
        request.intro = "在变化中保持温柔";
        request.memoryIds = List.of(memoryId);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        CapsuleGenomeVersion v1 = genomeService.current(capsule.id);
        assertNotNull(v1);
        assertEquals(1, v1.versionNo);
        assertEquals("ACTIVE", v1.status);
        assertTrue(v1.authorizationSnapshotJson.contains("\"memoryId\":" + memoryId));
        assertTrue(v1.authorizationSnapshotJson.contains("\"schemaVersion\":\"capsule-authorization.v2\""));
        assertTrue(v1.authorizationSnapshotJson.contains("\"dataUseGrants\""));
        assertTrue(v1.authorizationSnapshotJson.contains("\"purpose\":\"CAPSULE_RUNTIME\""));
        assertTrue(v1.authorizationSnapshotJson.contains("\"purpose\":\"PROVIDER_EGRESS\""));
        assertFalse(v1.authorizationSnapshotJson.contains(privateSummary));
        assertEquals(v1.id, capsuleService.getOwnedCapsule(owner, capsule.id).activeGenomeVersionId);
        assertThrows(BusinessException.class, () -> genomeService.history(stranger, capsule.id));

        genomeService.markNeedsReview(capsule.id, "source memory corrected");
        assertNull(genomeService.current(capsule.id));
        assertThrows(BusinessException.class,
                () -> capsuleService.updateVisibility(owner, capsule.id, "PUBLIC", true));

        CapsuleGenomeVersion v2 = capsuleService.recompileGenome(owner, capsule.id, List.of(memoryId));
        assertEquals(2, v2.versionNo);
        assertEquals(v1.id, v2.parentVersionId);
        assertEquals("ACTIVE", v2.status);
        assertEquals(List.of(2, 1), genomeService.history(owner, capsule.id).stream()
                .map(version -> version.versionNo).toList());
        EchoCapsule published = capsuleService.updateVisibility(owner, capsule.id, "PUBLIC", true);
        assertTrue(published.isPublic);
        assertEquals("PUBLIC", published.visibilityStatus);
        assertNotNull(personaChatService.create(stranger, capsule.id),
                "historical withdrawn references must not block the newly authorized Genome");

        genomeService.markNeedsReview(capsule.id, "second review");
        capsuleService.archiveCapsule(owner, capsule.id);
        CapsuleGenomeVersion latest = genomeService.history(owner, capsule.id).getFirst();
        assertEquals(v2.id, latest.id);
        assertEquals("WITHDRAWN", latest.status);
        assertNull(genomeService.current(capsule.id));
    }

    @Test
    @Transactional
    void recompileRejectsMemoryWithoutCapsuleConsentAndPreservesPriorVersion() {
        Long owner = seedUser("genome-consent");
        Long allowed = seedMemory(owner, "可授权摘要", "AURORA_PRIVATE");
        Long localOnly = seedMemory(owner, "仅本地摘要", "LOCAL_ONLY");
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(allowed);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleGenomeVersion original = genomeService.current(capsule.id);

        assertThrows(BusinessException.class,
                () -> capsuleService.recompileGenome(owner, capsule.id, List.of(localOnly)));

        CapsuleGenomeVersion stillActive = genomeService.current(capsule.id);
        assertNotNull(stillActive);
        assertEquals(original.id, stillActive.id);
        assertEquals(1, genomeService.history(owner, capsule.id).size());
    }

    @Test
    @Transactional
    void ownerSandboxIsIsolatedAndFeedbackDoesNotMutateGenome() {
        Long owner = seedUser("sandbox-owner");
        Long stranger = seedUser("sandbox-stranger");
        Long memory = seedMemory(owner, "遇到压力时会先把问题拆小，再慢慢说明自己的边界", "AURORA_PRIVATE");
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "慢慢说的人";
        request.memoryIds = List.of(memory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleGenomeVersion before = genomeService.current(capsule.id);

        CapsuleSandboxVO response = sandboxService.respond(owner, capsule.id, "你遇到误解时会怎么说？");
        assertEquals(before.id, response.genomeVersionId());
        assertTrue(response.providerAvailable());
        assertFalse(response.reply().isBlank());
        assertTrue(response.identityNotice().contains("不会发送"));
        assertThrows(BusinessException.class,
                () -> sandboxService.respond(stranger, capsule.id, "越权试聊"));

        CapsuleSandboxFeedbackRequest feedback = new CapsuleSandboxFeedbackRequest();
        feedback.genomeVersionId = before.id;
        feedback.question = response.question();
        feedback.response = response.reply();
        feedback.rating = "TONE_WRONG";
        feedback.comment = "更克制一点";
        assertEquals("OPEN", sandboxService.recordFeedback(owner, capsule.id, feedback).status);
        assertEquals(1, sandboxService.feedback(owner, capsule.id).size());
        assertEquals(before.id, genomeService.current(capsule.id).id,
                "sandbox feedback is a proposal signal and must not drift the live Genome");
    }

    @Test
    @Transactional
    void simulatorCapsuleRequiresExplicitAuthorizationAndIsPermanentlyIsolated() {
        Long owner = seedUser("simulator-owner");
        Long visitor = seedUser("simulator-visitor");
        Long personalMemory = seedMemory(owner, "已授权给真实共鸣体的记忆", "AURORA_PRIVATE");
        Long simulatorMemory = seedMemory(owner, "明确只授权给模拟器的记忆", "SIMULATOR_AUTHORIZED");

        // A personal capsule's own normally-authorized memory must not be silently reusable
        // for the Simulator — the contract requires an explicit, distinct authorization.
        CapsuleCreateRequest reusesPersonalConsent = new CapsuleCreateRequest();
        reusesPersonalConsent.memoryIds = List.of(personalMemory);
        assertThrows(BusinessException.class,
                () -> capsuleService.createSimulatorCapsule(owner, reusesPersonalConsent));

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "模拟器侧面";
        request.memoryIds = List.of(simulatorMemory);
        // Even if the caller asks for PUBLIC, the Simulator capsule must come back private.
        request.visibilityStatus = "PUBLIC";
        request.isPublic = true;
        EchoCapsule simulator = capsuleService.createSimulatorCapsule(owner, request);
        assertTrue(simulator.simulatorOnly);
        assertEquals("PRIVATE", simulator.visibilityStatus);
        assertFalse(simulator.isPublic);
        assertNotNull(genomeService.current(simulator.id), "the Simulator still gets a real compiled Genome");

        assertThrows(BusinessException.class,
                () -> capsuleService.updateVisibility(owner, simulator.id, "PUBLIC", true),
                "a Simulator capsule can never be published");
        assertThrows(BusinessException.class,
                () -> capsuleService.updateVisibility(owner, simulator.id, null, true),
                "isPublic alone must not bypass the Simulator publish guard either");

        assertTrue(capsuleService.plazaCapsules().stream().noneMatch(row -> row.id.equals(simulator.id)),
                "Simulator capsules must never appear in the public plaza");
        assertThrows(BusinessException.class, () -> personaChatService.create(visitor, simulator.id),
                "real visitors can never reach a Simulator capsule through persona chat");

        // The isolation does not block the actual purpose: the owner can still sandbox-test it.
        CapsuleSandboxVO response = sandboxService.respond(owner, simulator.id, "在陌生场景下你会怎么回应？");
        assertTrue(response.providerAvailable());
    }

    @Test
    @Transactional
    void fidelitySummaryAggregatesFeedbackPerVersionAndIsOwnerIsolated() {
        Long owner = seedUser("fidelity-owner");
        Long stranger = seedUser("fidelity-stranger");
        Long memory = seedMemory(owner, "遇到冲突时会先冷静下来，再说明具体的边界", "AURORA_PRIVATE");
        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "先冷静的人";
        request.memoryIds = List.of(memory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleGenomeVersion v1 = genomeService.current(capsule.id);

        assertTrue(sandboxService.fidelitySummary(owner, capsule.id).isEmpty(),
                "no fidelity signal before any sandbox feedback exists");

        rate(owner, capsule.id, v1.id, "LIKE_ME");
        rate(owner, capsule.id, v1.id, "LIKE_ME");
        rate(owner, capsule.id, v1.id, "NOT_ME");

        CapsuleGenomeVersion v2 = capsuleService.recompileGenome(owner, capsule.id, List.of(memory));
        rate(owner, capsule.id, v2.id, "LIKE_ME");

        List<com.innercosmos.vo.CapsuleFidelitySummaryVO> summaries = sandboxService.fidelitySummary(owner, capsule.id);
        assertEquals(2, summaries.size());
        assertEquals(2, summaries.get(0).versionNo(), "newest version must be first");
        assertEquals(1, summaries.get(0).totalRatings());
        assertEquals(1, summaries.get(0).likeMeCount());
        assertEquals(1.0, summaries.get(0).fidelityScore());
        assertEquals(1, summaries.get(1).versionNo());
        assertEquals(3, summaries.get(1).totalRatings());
        assertEquals(2, summaries.get(1).likeMeCount());
        assertEquals(1, summaries.get(1).notMeCount());
        assertEquals(2.0 / 3.0, summaries.get(1).fidelityScore(), 0.0001);

        assertThrows(BusinessException.class, () -> sandboxService.fidelitySummary(stranger, capsule.id));
    }

    @Test
    @Transactional
    void simulatorPurposeGrantCannotLeakIntoNormalCapsuleOrBeSwappedOutLater() {
        // A user-review finding: SIMULATOR_AUTHORIZED was only enforced at
        // createSimulatorCapsule time. createFromMemory and the shared replaceAuthorizations
        // path (updateContext/recompileGenome) did not exclude/require it, so a memory marked
        // "simulator testing only" could end up in a normal, potentially-public capsule, and a
        // Simulator capsule could later be recompiled with ordinary memories.
        Long owner = seedUser("purpose-grant-owner");
        Long normalMemory = seedMemory(owner, "普通授权记忆", "AURORA_PRIVATE");
        Long simulatorMemory = seedMemory(owner, "仅供模拟器使用的记忆", "SIMULATOR_AUTHORIZED");

        // (1) createFromMemory must silently exclude a SIMULATOR_AUTHORIZED memory, exactly like
        // it already does for LOCAL_ONLY/NO_EXTERNAL_PROCESSING.
        CapsuleCreateRequest normalRequest = new CapsuleCreateRequest();
        normalRequest.memoryIds = List.of(normalMemory, simulatorMemory);
        normalRequest.visibilityStatus = "PRIVATE";
        normalRequest.isPublic = false;
        EchoCapsule normalCapsule = capsuleService.createFromMemory(owner, normalRequest);
        assertEquals(List.of(normalMemory), parseAuthorizedIds(normalCapsule.authorizedMemoryIds),
                "a SIMULATOR_AUTHORIZED memory must never enter a normal capsule's authorization set");

        // (2) recompileGenome on that same normal capsule must reject a SIMULATOR_AUTHORIZED
        // memoryId outright (mirrors the existing LOCAL_ONLY rejection contract).
        assertThrows(BusinessException.class,
                () -> capsuleService.recompileGenome(owner, normalCapsule.id, List.of(simulatorMemory)),
                "a normal capsule must not be recompilable with a simulator-only memory");

        // (3) The reverse direction: a Simulator capsule's authorization must not be swappable
        // for an ordinary memory via recompile — it may ONLY ever hold SIMULATOR_AUTHORIZED memories.
        CapsuleCreateRequest simulatorRequest = new CapsuleCreateRequest();
        simulatorRequest.memoryIds = List.of(simulatorMemory);
        EchoCapsule simulator = capsuleService.createSimulatorCapsule(owner, simulatorRequest);
        assertThrows(BusinessException.class,
                () -> capsuleService.recompileGenome(owner, simulator.id, List.of(normalMemory)),
                "a Simulator capsule must not be recompilable with an ordinary, non-simulator memory");
    }

    private List<Long> parseAuthorizedIds(String authorizedMemoryIdsJson) {
        try {
            JsonNode node = objectMapper.readTree(authorizedMemoryIdsJson);
            return StreamSupport.stream(node.spliterator(), false).map(JsonNode::asLong).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Transactional
    void compilerIndexesDistinctScenesAndSurfacesOpposingSentimentAsTension() throws Exception {
        // Campaign C punch-list item 2: the compiler must do real feature extraction (scene
        // indexing by theme, not a flat 420-char truncation) and real tension surfacing
        // (opposing-sentiment memories on the same theme surfaced as emotional complexity —
        // NOT labeled a "conflict"/contradiction, since one warm and one difficult memory about
        // the same relationship is normal, not a logical inconsistency).
        // Keyword-style summaries (not natural prose) — the same convention CapsuleMatchingTest
        // already relies on, since PseudoSemanticAnalyzer's theme detection runs on individual
        // Chinese characters and free prose collides across families far too easily (e.g. any
        // "试" collides with the "考试" keyword) to isolate a single theme deterministically.
        Long owner = seedUser("compiler-quality-owner");
        Long warmMemory = seedMemory(owner, "片段",
                "朋友 顺利 轻松", "AURORA_PRIVATE");
        Long tenseMemory = seedMemory(owner, "片段",
                "朋友 吵架 难过 委屈", "AURORA_PRIVATE");
        Long unrelatedMemory = seedMemory(owner, "片段",
                "项目 任务 截止", "AURORA_PRIVATE");

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.pseudonym = "编译质量样本";
        request.memoryIds = List.of(warmMemory, tenseMemory, unrelatedMemory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleGenomeVersion genome = genomeService.current(capsule.id);

        JsonNode preview = objectMapper.readTree(capsule.contextPreviewJson);
        JsonNode scenes = preview.get("scenes");
        assertEquals(2, scenes.size(), "distinct-theme memories must land in distinct scenes, "
                + "not one flat truncation");

        JsonNode relationshipScene = findScene(scenes, "关系牵动");
        assertNotNull(relationshipScene, "关系牵动 theme must be indexed as its own scene");
        assertEquals(2, relationshipScene.get("memoryCount").asInt());
        assertTrue(relationshipScene.get("hasTension").asBoolean(),
                "a warm and a distressing memory about the same relationship must be surfaced as tension, not blended");
        // Genome IR slice 1: every scene must cite the exact memoryIds it was built from.
        Set<Long> relationshipProvenance = StreamSupport.stream(relationshipScene.get("memories").spliterator(), false)
                .map(ref -> ref.get("memoryId").asLong()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(warmMemory, tenseMemory), relationshipProvenance,
                "scene provenance must name exactly the memories that produced it, not just an aggregate excerpt");
        for (JsonNode ref : relationshipScene.get("memories")) {
            assertTrue(ref.has("sourceVersion") && ref.has("confidence"),
                    "each provenance entry must carry sourceVersion and confidence, not just an id");
        }

        JsonNode taskScene = findScene(scenes, "任务压力");
        assertNotNull(taskScene, "任务压力 theme must be indexed as its own scene");
        assertEquals(1, taskScene.get("memoryCount").asInt());
        assertFalse(taskScene.get("hasTension").asBoolean());
        assertEquals(unrelatedMemory, taskScene.get("memories").get(0).get("memoryId").asLong());

        JsonNode tensions = preview.get("tensions");
        assertEquals(1, tensions.size());
        assertEquals("关系牵动", tensions.get(0).get("theme").asText());

        JsonNode styleProfile = objectMapper.readTree(capsule.styleProfileJson);
        assertTrue(styleProfile.get("voice").asText().contains("在关系中重视被认真回应"),
                "the dominant theme (2 of 3 memories) must drive the voice descriptor");
        assertEquals(3, styleProfile.get("sampleSize").asInt());
        // Genome IR slice 1: the voice descriptor must be traceable to specific memoryIds, not an
        // unexplained personality adjective.
        JsonNode voiceEvidence = styleProfile.get("voiceEvidence").get("关系牵动");
        Set<Long> voiceProvenance = StreamSupport.stream(voiceEvidence.spliterator(), false)
                .map(JsonNode::asLong).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(warmMemory, tenseMemory), voiceProvenance,
                "voiceEvidence must cite the memories that actually drove the dominant theme");

        JsonNode evaluation = objectMapper.readTree(genome.evaluationJson);
        assertEquals(2, evaluation.get("sceneCount").asInt(),
                "compiler-evaluation must expose the same structural scene count the preview computed");
        assertEquals(1, evaluation.get("tensionCount").asInt());
        assertEquals(0, styleProfile.get("unknowns").size(),
                "real theme signal exists here, so there must be no 'unknowns' disclaimer");
    }

    @Test
    @Transactional
    void compilerDisclosesUnknownsInsteadOfPresentingAFallbackVoiceWithFalseConfidence() throws Exception {
        // A capsule compiled from memories with no detectable theme signal (and one with no
        // readable content at all) must say so honestly via 'unknowns', instead of silently
        // presenting the generic default voice with the same authority as a real theme match.
        Long owner = seedUser("unknowns-owner");
        Long blankMemory = seedMemory(owner, "", "", "AURORA_PRIVATE");
        Long themelessMemory = seedMemory(owner, "片段", "随便的日常", "AURORA_PRIVATE");

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(blankMemory, themelessMemory);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        JsonNode styleProfile = objectMapper.readTree(capsule.styleProfileJson);
        assertEquals("温和、诚实、慢热", styleProfile.get("voice").asText());
        List<String> unknowns = StreamSupport.stream(styleProfile.get("unknowns").spliterator(), false)
                .map(JsonNode::asText).toList();
        assertTrue(unknowns.stream().anyMatch(u -> u.contains("通用默认值")),
                "must disclose that the fallback voice is a real absence of signal, not a match");
        assertTrue(unknowns.stream().anyMatch(u -> u.contains("1") && u.contains("未参与特征提取")),
                "must disclose that the blank-content memory could not be read");
    }

    @Test
    @Transactional
    void compilerBuildsProvenanceCarryingIrWithoutGeneralizingUnsupportedCategories() throws Exception {
        Long owner = seedUser("genome-ir-owner");
        Long episode = seedMemory(owner, "搬家片段", "去年搬到成都，开始适应新的通勤路线", "AURORA_PRIVATE");
        Long value = seedMemory(owner, "关系原则", "我很重视被认真回应，也不愿用冷处理解决分歧", "AURORA_PRIVATE");
        Long habit = seedMemory(owner, "冲突习惯", "遇到冲突时我通常会先冷静，再说明具体边界", "AURORA_PRIVATE");
        Long temporal = seedMemory(owner, "近期状态", "最近正在准备一次重要展示，目前有些紧张", "AURORA_PRIVATE");

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(episode, value, habit, temporal);
        request.visibilityStatus = "PRIVATE";
        request.isPublic = false;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        CapsuleGenomeVersion genome = genomeService.current(capsule.id);

        JsonNode preview = objectMapper.readTree(genome.contextPreviewJson);
        assertEquals("capsule-context-preview.v3", preview.path("schemaVersion").asText());
        JsonNode ir = preview.path("genomeIr");
        assertEquals("capsule-genome-ir.v1", ir.path("schemaVersion").asText());
        assertEquals(4, ir.path("claims").size(), "every readable episode may become a scoped claim");
        assertEquals(1, ir.path("values").size(), "only an explicit value cue may become a value feature");
        assertEquals(1, ir.path("habits").size(), "only an explicit habit cue may become a habit feature");
        assertEquals(1, ir.path("temporalState").size(), "only an explicit temporal cue may become current state");

        assertFeatureCitesOnly(ir.path("values").get(0), value);
        assertFeatureCitesOnly(ir.path("habits").get(0), habit);
        assertFeatureCitesOnly(ir.path("temporalState").get(0), temporal);
        assertEquals("DETERMINISTIC_EXPLICIT_CUE", ir.path("values").get(0).path("extractionMethod").asText());
        assertEquals("capsule-retrieval-policy.v1", preview.path("retrievalPolicy").path("schemaVersion").asText());
        assertEquals("ACKNOWLEDGE_UNKNOWN", preview.path("retrievalPolicy").path("unsupportedBehavior").asText());

        JsonNode evaluation = objectMapper.readTree(genome.evaluationJson);
        assertEquals(4, evaluation.path("claimCount").asInt());
        assertEquals(1, evaluation.path("valueCount").asInt());
        assertEquals(1, evaluation.path("habitCount").asInt());
        assertEquals(1, evaluation.path("temporalStateCount").asInt());
        assertEquals("capsule-genome.v3", genome.compilerVersion);
    }

    private void assertFeatureCitesOnly(JsonNode feature, Long memoryId) {
        assertEquals(1, feature.path("evidence").size());
        JsonNode evidence = feature.path("evidence").get(0);
        assertEquals(memoryId, evidence.path("memoryId").asLong());
        assertTrue(evidence.has("sourceVersion") && evidence.has("confidence"));
    }

    private JsonNode findScene(JsonNode scenes, String theme) {
        return StreamSupport.stream(scenes.spliterator(), false)
                .filter(scene -> theme.equals(scene.get("theme").asText()))
                .findFirst().orElse(null);
    }

    private void rate(Long owner, Long capsuleId, Long genomeVersionId, String rating) {
        CapsuleSandboxFeedbackRequest feedback = new CapsuleSandboxFeedbackRequest();
        feedback.genomeVersionId = genomeVersionId;
        feedback.question = "评测夹具问题";
        feedback.response = "评测夹具回答";
        feedback.rating = rating;
        sandboxService.recordFeedback(owner, capsuleId, feedback);
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedMemory(Long owner, String summary, String consentScope) {
        return seedMemory(owner, "测试记忆", summary, consentScope);
    }

    private Long seedMemory(Long owner, String title, String summary, String consentScope) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, ?, ?, 'ACTIVE', 1, ?)
                """, owner, title, summary, consentScope);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }
}
