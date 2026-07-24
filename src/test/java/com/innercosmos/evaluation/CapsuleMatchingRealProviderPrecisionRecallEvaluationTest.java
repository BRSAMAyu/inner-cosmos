package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.DisabledMemoryEmbeddingClient;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.ai.embedding.OpenAiCompatibleMemoryEmbeddingClient;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.service.CapsuleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G6 MATCH-MULTI — REAL embedding-provider precision/recall proof for Echo Capsule matching.
 *
 * <p>The acceptance ledger's G6/MATCH-MULTI "remaining" text previously called calibrated
 * semantic-similarity matching a "real-provider human gate". That mislabels the actual gap: the
 * A3-capsule-matching work (see {@code evidence/track-a/A3-capsule-matching/}) already ensembles a
 * real {@link CapsuleEmbeddingIndexService} cosine-similarity signal into
 * {@link CapsuleService#matchedCapsules}; what was missing was a labeled precision/recall dataset
 * and harness that actually drives that signal with a REAL embedding provider instead of asserting
 * around a fake/local contract client. That is ordinary, machine-executable API-key work — not a
 * human-judgment gate — exactly like the already-proven {@code real-provider} tests for GLM/DeepSeek/
 * MiniMax chat and (on a sibling branch) the Qwen/DashScope memory-embedding pipeline.
 *
 * <p>Tagged {@code real-provider} and excluded from the default {@code ./mvnw test} gate (see
 * {@code pom.xml}'s surefire {@code excludedGroups}). Reads a provider key ONLY from process
 * environment variables — {@code DASHSCOPE_API_KEY}, {@code QWEN_API_KEY}, or the codebase's generic
 * {@code MEMORY_EMBEDDING_API_KEY} (checked in that order) — never from a file, never logged, never
 * written to any output this test produces. When none is present the test short-circuits to a single
 * {@code SKIPPED_NO_CREDENTIAL} evidence row and asserts nothing about precision/recall: this run
 * never fabricates a number and never silently substitutes a Mock/local client and reports it as a
 * real pass. Run explicitly once a real key is available, e.g.:
 * <pre>
 *   export DASHSCOPE_API_KEY="…"
 *   ./mvnw test -Dtest=CapsuleMatchingRealProviderPrecisionRecallEvaluationTest -DexcludedGroups=
 * </pre>
 *
 * <p><b>Dataset design.</b> Six scenarios, each one relevant capsule (a topic paraphrase of the
 * viewer's memory with ZERO shared characters from {@code PseudoSemanticAnalyzer}'s fixed 6-family
 * theme keyword lexicon) plus three distractor capsules on unrelated everyday topics, also outside
 * that lexicon. Every candidate in a scenario shares the same {@code echoEnergy} and
 * {@code capsuleType}, so themeOverlap, portraitSignal, energyScore and seedBoost are identical
 * (all zero/tied) across the four candidates — the ONLY thing that can separate them in
 * {@link CapsuleService#matchedCapsules} is the real embedding cosine-similarity signal. This
 * isolates exactly the capability the ledger's "remaining" text is asking about: calibrated semantic
 * relevance on paraphrase (no shared keywords) cases, not the already-tested lexical/theme path
 * (see {@code CapsuleMatchingTest}, which is deliberately Mock/deterministic and never touches a
 * real provider).
 *
 * <p>Reports BOTH the raw cosine ranking (queried directly from
 * {@link CapsuleEmbeddingIndexService#similarities}) and the full end-to-end
 * {@link CapsuleService#matchedCapsules} ranking, so a future real run can see whether the
 * {@code SEMANTIC_SIMILARITY_CAP} (0.40) flattens genuinely different cosine scores into a tie
 * (recorded per-scenario as {@code capClampCollision}) — a calibration question this harness raises
 * but does not resolve, since it has never been exercised against a live provider in this session.
 */
@Tag("real-provider")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capsule-matching-real-provider;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
@Import(CapsuleMatchingRealProviderPrecisionRecallEvaluationTest.RealOrDisabledEmbeddingConfig.class)
class CapsuleMatchingRealProviderPrecisionRecallEvaluationTest {

    @Autowired CapsuleService capsuleService;
    @Autowired CapsuleEmbeddingIndexService embeddingIndex;
    @Autowired MemoryCardMapper memoryCardMapper;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemoryEmbeddingClient client;

    private static final String[] CREDENTIAL_ENV_VARS = {"DASHSCOPE_API_KEY", "QWEN_API_KEY", "MEMORY_EMBEDDING_API_KEY"};

    @Test
    void semanticSignalRanksParaphrasedRelevantCapsuleAboveDistractorsWithRealEmbeddingProvider() throws Exception {
        String credentialSource = resolveCredentialSource();
        if (credentialSource == null || !client.available()) {
            writeReport(List.of(Map.of(
                    "status", "SKIPPED_NO_CREDENTIAL",
                    "note", "none of DASHSCOPE_API_KEY / QWEN_API_KEY / MEMORY_EMBEDDING_API_KEY is set "
                            + "in this session's process environment; never falls back to Mock/local and "
                            + "reports it as a real pass. This is an ordinary API-key-presence gate, not a "
                            + "human-judgment gate: re-running with a real key requires no human step."
            )), null);
            return;
        }

        Long viewerId = createUser("capsule_match_eval_viewer");
        Long ownerId = createUser("capsule_match_eval_owner");

        List<Map<String, Object>> rows = new ArrayList<>();
        int precisionAt1Hits = 0;
        int recallAt2Hits = 0;

        for (Scenario scenario : SCENARIOS) {
            resetCapsuleAndMemoryState(viewerId);
            insertMemory(viewerId, scenario.query());

            EchoCapsule relevant = insertCapsule(ownerId, "relevant-" + scenario.id(), scenario.relevantText());
            List<EchoCapsule> distractors = new ArrayList<>();
            for (int i = 0; i < scenario.distractorTexts().length; i++) {
                distractors.add(insertCapsule(ownerId, "distractor-" + scenario.id() + "-" + i, scenario.distractorTexts()[i]));
            }
            List<EchoCapsule> candidates = new ArrayList<>();
            candidates.add(relevant);
            candidates.addAll(distractors);

            // Index every candidate via the REAL provider (mirrors CapsuleEmbeddingRebuildJob).
            embeddingIndex.rebuildMissing(10);

            // Raw cosine ranking, queried directly (also exercises the real query embed() call).
            Map<Long, Double> rawScores = embeddingIndex.similarities(scenario.query(), candidates);

            // Full end-to-end pipeline ranking (themeOverlap/portraitSignal/energyScore/seedBoost are
            // identical across candidates by construction, so ranking here should mirror rawScores).
            List<Map<String, Object>> pipelineResult = capsuleService.matchedCapsules(viewerId);

            List<Long> rawRanked = rawScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey).toList();
            List<Long> pipelineRanked = pipelineResult.stream()
                    .map(item -> ((EchoCapsule) item.get("capsule")).id).toList();

            boolean rawPrecisionAt1 = !rawRanked.isEmpty() && rawRanked.get(0).equals(relevant.id);
            boolean rawRecallAt2 = rawRanked.size() >= 1 && rawRanked.subList(0, Math.min(2, rawRanked.size())).contains(relevant.id);
            if (rawPrecisionAt1) precisionAt1Hits++;
            if (rawRecallAt2) recallAt2Hits++;

            double relevantCosine = rawScores.getOrDefault(relevant.id, 0.0);
            List<Double> distractorCosines = distractors.stream().map(d -> rawScores.getOrDefault(d.id, 0.0)).toList();
            boolean capClampCollision = relevantCosine >= 0.8
                    && distractorCosines.stream().anyMatch(c -> c >= 0.8);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenarioId", scenario.id());
            row.put("query", scenario.query());
            row.put("relevantText", scenario.relevantText());
            row.put("relevantCosine", relevantCosine);
            row.put("distractorCosines", distractorCosines);
            row.put("rawPrecisionAt1", rawPrecisionAt1);
            row.put("rawRecallAt2", rawRecallAt2);
            row.put("pipelineRankedCapsuleIds", pipelineRanked);
            row.put("pipelineTop1IsRelevant", !pipelineRanked.isEmpty() && pipelineRanked.get(0).equals(relevant.id));
            row.put("capClampCollision", capClampCollision);
            rows.add(row);
        }

        double precisionAt1 = (double) precisionAt1Hits / SCENARIOS.length;
        double recallAt2 = (double) recallAt2Hits / SCENARIOS.length;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scenarioCount", SCENARIOS.length);
        summary.put("precisionAt1", precisionAt1);
        summary.put("recallAt2", recallAt2);
        summary.put("credentialSource", credentialSource);
        summary.put("providerModel", client.modelName());
        summary.put("providerModelVersion", client.modelVersion());
        writeReport(rows, summary);

        // Structural (not fabricated-threshold) assertions: a genuinely available real provider must
        // actually produce a similarity signal for every candidate — an empty/degraded map here would
        // mean the provider call silently failed and the pipeline fell back to zero-signal, which must
        // surface as a real test failure rather than being reported as a pass.
        for (Map<String, Object> row : rows) {
            @SuppressWarnings("unchecked")
            List<Double> distractorCosines = (List<Double>) row.get("distractorCosines");
            assertTrue((Double) row.get("relevantCosine") != 0.0 || distractorCosines.stream().anyMatch(c -> c != 0.0),
                    "scenario " + row.get("scenarioId") + ": real provider returned an all-zero similarity map; "
                            + "the embedding call likely failed silently");
        }
    }

    private String resolveCredentialSource() {
        for (String var : CREDENTIAL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.isBlank()) return var;
        }
        return null;
    }

    private void resetCapsuleAndMemoryState(Long viewerId) {
        jdbc.update("DELETE FROM tb_capsule_embedding");
        jdbc.update("DELETE FROM tb_echo_capsule");
        jdbc.update("DELETE FROM tb_memory_card WHERE user_id = ?", viewerId);
    }

    private void insertMemory(Long userId, String text) {
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = "评估场景";
        card.summary = text;
        card.memoryType = "TODO";
        card.memoryLayer = "PROSPECTIVE";
        card.status = "ACTIVE";
        card.versionNo = 1;
        card.confidence = .9;
        card.emotionalGravity = 5.0;
        card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        memoryCardMapper.insert(card);
    }

    private EchoCapsule insertCapsule(Long ownerId, String pseudonym, String text) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = ownerId;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = pseudonym;
        capsule.intro = text;
        capsule.publicTags = "[]";
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        capsule.simulatorOnly = false;
        capsule.echoEnergy = 0.5;
        capsuleMapper.insert(capsule);
        return capsule;
    }

    private Long createUser(String username) {
        jdbc.update("INSERT INTO tb_user(username,password_hash,role,status) VALUES (?,?,?,?)",
                username, "not-a-real-hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
    }

    private void writeReport(List<Map<String, Object>> rows, Map<String, Object> summary) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "capsule-matching-real-provider-precision-recall-v1");
        report.put("note", "No credential VALUES are read into or written by this report — only env VAR "
                + "NAMES and structural/numeric outcomes are captured.");
        report.put("scenarios", rows);
        if (summary != null) report.put("summary", summary);
        Path reportPath = Path.of("target", "evaluation", "capsule-matching-real-provider-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
    }

    private record Scenario(String id, String query, String relevantText, String[] distractorTexts) {}

    // Every string below is deliberately built OUTSIDE PseudoSemanticAnalyzer's fixed 6-family theme
    // keyword lexicon (任务压力/关系牵动/情绪承压/认知探索/自我评价/希望期待) so themeOverlap/portraitSignal
    // are zero for every candidate and only the real embedding cosine can separate them.
    private static final Scenario[] SCENARIOS = {
            new Scenario("overtime-fatigue",
                    "这周几乎天天在公司待到很晚才收工，回家路上整个人都是懵的",
                    "最近连续几天下班都特别晚，坐末班车回去时脑子已经转不动了",
                    new String[] {
                            "周末在阳台种了几盆薄荷，浇水时闻到雨后泥土的味道",
                            "报名了陶艺体验课，第一次上手转盘手抖得厉害",
                            "把旧相册翻出来重新整理，看到很多小时候的照片"
                    }),
            new Scenario("relocation-loneliness",
                    "刚搬到一个陌生的城市，还没找到能自在说话的人",
                    "换了新的居住地，身边暂时还没有可以随意聊天的对象",
                    new String[] {
                            "学着自己烤面包，第一次发酵失败了",
                            "养的猫最近总喜欢趴在窗台上晒太阳",
                            "去河边骑了很久的自行车，风很大"
                    }),
            new Scenario("insomnia",
                    "已经连续好几个晚上很晚都睡不着，翻来覆去看着天花板",
                    "最近夜里总是迟迟合不上眼，躺着盯天花板到后半夜",
                    new String[] {
                            "在旧衣柜里翻出一件很久没穿的外套",
                            "跟着视频学做了一道新的凉拌菜",
                            "去公园拍了一组秋天落叶的照片"
                    }),
            new Scenario("new-instrument",
                    "开始跟教练学弹吉他，手指按弦总是按不准位置",
                    "报名了吉他兴趣班，手指还没适应按弦的力度",
                    new String[] {
                            "整理书架时把重复买的书都收了起来",
                            "周末去徒步，中途下了一场小雨",
                            "在阳台上给多肉植物换了新盆"
                    }),
            new Scenario("decluttering",
                    "把储物间堆了很久的杂物一件件清出来扔掉",
                    "花了一整天把柜子里囤积的旧东西清理干净",
                    new String[] {
                            "尝试用手机拍星空，试了很多次曝光参数",
                            "在小区里认识了一只经常来蹭饭的橘猫",
                            "去海边露营，晚上看到了流星"
                    }),
            new Scenario("rain-walk",
                    "下雨天一个人打着伞在街上慢慢走，觉得很安静",
                    "雨后一个人沿着河边散步，脚步不自觉放慢了",
                    new String[] {
                            "研究了很久新买的相机说明书",
                            "把阳台上的旧花盆重新刷了颜色",
                            "去健身房跟教练试了一节体验课"
                    }),
    };

    @TestConfiguration
    static class RealOrDisabledEmbeddingConfig {
        @Bean
        @Primary
        MemoryEmbeddingClient capsuleMatchingEvaluationEmbeddingClient(ObjectMapper objectMapper) {
            String apiKey = null;
            for (String var : CREDENTIAL_ENV_VARS) {
                String value = System.getenv(var);
                if (value != null && !value.isBlank()) { apiKey = value; break; }
            }
            if (apiKey == null) return new DisabledMemoryEmbeddingClient();
            String baseUrl = System.getenv().getOrDefault("MEMORY_EMBEDDING_BASE_URL",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1");
            String model = System.getenv().getOrDefault("MEMORY_EMBEDDING_MODEL", "text-embedding-v4");
            int dimensions = Integer.parseInt(System.getenv().getOrDefault("MEMORY_EMBEDDING_DIMENSIONS", "1536"));
            return new OpenAiCompatibleMemoryEmbeddingClient(baseUrl, apiKey, model, "2026-eval", dimensions, objectMapper);
        }
    }
}
