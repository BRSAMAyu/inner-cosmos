package com.innercosmos.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryLink;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryLinkMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-57 evaluation executor over the frozen ninety-day-trajectory-bank-v1 bank
 * (see docs/commercialization/evaluation/experiment-registry.yml, CP-57 entry;
 * this run can only ever close EVALUATION_EXECUTED, never CAPABILITY_ACCEPTED).
 *
 * Per trajectory: events are replayed directly through the MemoryCard/MemoryLink
 * mappers with the MemoryLifecycleServiceImpl semantics (CONTRADICT/SUPERSEDE
 * chains bump versions and point supersededById at the replacement; FORGET
 * redacts content via the same explicit UpdateWrapper the product uses), time
 * travelling to each probe day so mid-trajectory state is what gets retrieved.
 * At every probe the REAL MemoryRetrievalService is scored against the frozen
 * expectations, plus a deliberately naive recency-only baseline (the blueprint
 * at docs/commercialization/01-中国大陆商业化执行蓝图.md L850 requires keeping the
 * simple-retrieval baseline, cost and failures next to the system numbers).
 *
 * Frozen registry thresholds asserted here:
 *   - factual accuracy >= 0.95 over all probes with an expected key
 *   - correction preference == 1.0 (a probe expecting the post-correction
 *     version must return the new version, never the superseded one)
 *   - withdrawn probes return zero evidence; FORGOTTEN rows never resurrect
 *   - cross-user contamination == 0 (neighbor honeypots never surface)
 * Accuracy is reported with an exact (Clopper-Pearson) binomial 95% CI as the
 * registry prescribes. If a threshold fails because of a genuine product
 * defect, the failing assertion stays failing on purpose: that is the point of
 * an evaluation — the report at target/evaluation/ carries the evidence.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ninety-day-trajectory-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class NinetyDayTrajectoryEvaluationTest {

    private static final double ACCURACY_THRESHOLD = 0.95;   // registry CP-57 primary endpoint
    private static final double CORRECTION_THRESHOLD = 1.0;  // registry CP-57 primary endpoint
    private static final int MAX_RESULTS = 3;
    private static final int TOKEN_BUDGET = 400;

    @Autowired ObjectMapper objectMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryLinkMapper linkMapper;
    @Autowired MemoryRetrievalService retrieval;

    @Test
    void ninetyDayTrajectoriesMeetFrozenRegistryThresholds() throws Exception {
        JsonNode manifest = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/ninety-day-trajectory-bank-v1/manifest.json"));
        List<JsonNode> scenarios = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(
                        "/evaluation/ninety-day-trajectory-bank-v1/scenarios.jsonl"),
                StandardCharsets.UTF_8))) {
            for (String line : reader.lines().collect(Collectors.toList())) {
                if (line != null && !line.isBlank()) scenarios.add(objectMapper.readTree(line));
            }
        }
        assertEquals(manifest.path("scenario_count").asInt(), scenarios.size());
        assertTrue(scenarios.size() >= 200, "registry CP-57 requires >= 200 frozen trajectories");

        int factTotal = 0, factCorrect = 0;
        int correctionTotal = 0, correctionCorrect = 0;
        int withdrawnTotal = 0, withdrawnNonEmpty = 0, resurrections = 0, crossUserLeaks = 0;
        int baselineFactTotal = 0, baselineFactCorrect = 0, baselineRecall = 0;
        int baselineWithdrawnTotal = 0, baselineWithdrawnEmpty = 0;
        int earlyProbes = 0, earlyCorrect = 0, lateProbes = 0, lateCorrect = 0;
        Map<String, int[]> byFamily = new TreeMap<>(); // family -> {probes, correct, baselineCorrect}
        List<Map<String, Object>> failures = new ArrayList<>();
        long retrievalCalls = 0;
        long startedAt = System.nanoTime();

        for (int index = 0; index < scenarios.size(); index++) {
            JsonNode scenario = scenarios.get(index);
            long ownerId = 95_000L + 2L * index;
            long neighborId = ownerId + 1;
            String family = scenario.path("family").asText();
            byFamily.computeIfAbsent(family, f -> new int[3]);

            List<JsonNode> events = new ArrayList<>();
            scenario.path("events").forEach(events::add);
            int spanDays = scenario.path("spanDays").asInt();
            // Simulated clock: day D of the trajectory happened (spanDays + 10 - D) days ago.
            LocalDateTime anchor = LocalDateTime.now().minusDays(spanDays + 10L);
            List<Long> honeypotIds = insertNeighborHoneypots(neighborId, events, anchor);

            // Group probes by day; replay the trajectory cumulatively so each probe
            // sees exactly the state that existed on its day.
            Map<Integer, List<JsonNode>> byDay = new TreeMap<>();
            for (JsonNode probe : scenario.path("probes")) {
                byDay.computeIfAbsent(probe.path("day").asInt(), d -> new ArrayList<>()).add(probe);
            }
            Map<String, Long> keyIds = Map.of();
            Map<Long, String> statusById = Map.of();
            for (Map.Entry<Integer, List<JsonNode>> dayGroup : byDay.entrySet()) {
                resetOwnerRows(ownerId);
                var replayed = replay(ownerId, events, dayGroup.getKey(), anchor);
                keyIds = replayed.keyIds();
                statusById = replayed.statusById();
                final Map<Long, String> statusNow = statusById;
                final List<Long> honeypots = honeypotIds;
                Map<Long, String> keyNames = new LinkedHashMap<>();
                keyIds.forEach((key, id) -> keyNames.put(id, key));
                for (JsonNode probe : dayGroup.getValue()) {
                    String kind = probe.path("kind").asText();
                    String query = probe.path("query").asText();
                    String task = probe.path("task").asText();
                    MemoryEvidencePackVO pack = retrieval.retrieve(ownerId, new MemoryRetrievalQuery(
                            query, task, null, MAX_RESULTS, TOKEN_BUDGET, false));
                    retrievalCalls++;
                    List<Long> returned = pack.evidence().stream()
                            .map(MemoryEvidencePackVO.Evidence::memoryId).toList();
                    List<String> returnedKeys = returned.stream()
                            .map(id -> keyNames.getOrDefault(id, "unknown-" + id)).toList();
                    List<Long> expected = idsFor(probe.path("expectedKeys"), keyIds, probe);
                    List<Long> prohibited = idsFor(probe.path("prohibitedKeys"), keyIds, probe);

                    resurrections += returned.stream()
                            .filter(id -> "FORGOTTEN".equals(statusNow.get(id))).count();
                    crossUserLeaks += returned.stream().filter(honeypots::contains).count();

                    if ("WITHDRAWN_ZERO".equals(kind)) {
                        withdrawnTotal++;
                        baselineWithdrawnTotal++;
                        if (!returned.isEmpty()) {
                            withdrawnNonEmpty++;
                            failures.add(row(scenario, probe, returnedKeys,
                                    "withdrawn probe must return zero evidence"));
                        }
                        if (baselineTop3(ownerId).isEmpty()) baselineWithdrawnEmpty++;
                        continue;
                    }

                    boolean expectedReturned = returned.containsAll(expected);
                    boolean prohibitedLeaked = returned.stream().anyMatch(prohibited::contains);
                    boolean correct = expectedReturned && !prohibitedLeaked;
                    factTotal++;
                    if (correct) factCorrect++;
                    else failures.add(row(scenario, probe, returnedKeys,
                            (expectedReturned ? "" : "expected memory missing; ")
                                    + (prohibitedLeaked ? "prohibited memory leaked" : "")));
                    if ("CORRECTION_PREFERENCE".equals(kind)) {
                        correctionTotal++;
                        if (correct) correctionCorrect++;
                    }
                    if (probe.path("day").asInt() <= 30) {
                        earlyProbes++;
                        if (correct) earlyCorrect++;
                    } else {
                        lateProbes++;
                        if (correct) lateCorrect++;
                    }
                    byFamily.get(family)[0]++;
                    if (correct) byFamily.get(family)[1]++;

                    // Simple baseline: recency-only ranking over ACTIVE rows.
                    List<Long> baseline = baselineTop3(ownerId);
                    baselineFactTotal++;
                    boolean baselineCorrect = baseline.containsAll(expected)
                            && baseline.stream().noneMatch(prohibited::contains);
                    if (baseline.containsAll(expected)) baselineRecall++;
                    if (baselineCorrect) {
                        baselineFactCorrect++;
                        byFamily.get(family)[2]++;
                    }
                }
            }

            // Cross-user guard, reverse direction: querying as the neighbor must
            // never surface this user's memories even with overlapping content.
            JsonNode lastProbe = scenario.path("probes").get(scenario.path("probes").size() - 1);
            resetOwnerRows(ownerId);
            var finalState = replay(ownerId, events, Integer.MAX_VALUE, anchor);
            MemoryEvidencePackVO neighborPack = retrieval.retrieve(neighborId, new MemoryRetrievalQuery(
                    lastProbe.path("query").asText(), lastProbe.path("task").asText(),
                    null, MAX_RESULTS, TOKEN_BUDGET, false));
            retrievalCalls++;
            crossUserLeaks += neighborPack.evidence().stream()
                    .filter(evidence -> finalState.keyIds().containsValue(evidence.memoryId()))
                    .count();
        }

        double accuracy = factTotal == 0 ? 0 : (double) factCorrect / factTotal;
        double correctionPreference = correctionTotal == 0 ? 0 : (double) correctionCorrect / correctionTotal;
        double baselineAccuracy = baselineFactTotal == 0 ? 0 : (double) baselineFactCorrect / baselineFactTotal;
        double earlyAccuracy = earlyProbes == 0 ? 1 : (double) earlyCorrect / earlyProbes;
        double lateAccuracy = lateProbes == 0 ? 1 : (double) lateCorrect / lateProbes;
        double[] ci = clopperPearson95(factCorrect, factTotal);
        double[] baselineCi = clopperPearson95(baselineFactCorrect, baselineFactTotal);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("factualAccuracy", accuracy);
        system.put("exactBinomial95CI", List.of(ci[0], ci[1]));
        system.put("correctionPreference", correctionPreference);
        system.put("withdrawnNonEmptyReturns", withdrawnNonEmpty);
        system.put("forgottenResurrections", resurrections);
        system.put("crossUserLeaks", crossUserLeaks);
        system.put("earlyDay30OrLessAccuracy", earlyAccuracy);
        system.put("lateAfterDay30Accuracy", lateAccuracy);

        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("strategy", "recency-only top-3 over ACTIVE rows (no relevance admission)");
        baseline.put("factualAccuracy", baselineAccuracy);
        baseline.put("recallOfExpected", baselineFactTotal == 0 ? 0 : (double) baselineRecall / baselineFactTotal);
        baseline.put("exactBinomial95CI", List.of(baselineCi[0], baselineCi[1]));
        baseline.put("accuracyDeltaSystemMinusBaseline", accuracy - baselineAccuracy);
        baseline.put("withdrawnProbesEmpty", baselineWithdrawnEmpty);
        baseline.put("withdrawnProbesTotal", baselineWithdrawnTotal);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "ninety-day-trajectory-bank-v1");
        report.put("registry", "CP-57 (experiment-registry.yml): accuracy>=0.95, correction=1.0, withdrawn=0, crossUser=0");
        report.put("state", "EVALUATION_EXECUTED (never CAPABILITY_ACCEPTED by a test run)");
        report.put("scenarios", scenarios.size());
        report.put("probesWithExpected", factTotal);
        report.put("correctionProbes", correctionTotal);
        report.put("withdrawnProbes", withdrawnTotal);
        report.put("system", system);
        report.put("simpleBaseline", baseline);
        Map<String, Object> familyRows = new LinkedHashMap<>();
        byFamily.forEach((family, counts) -> familyRows.put(family, Map.of(
                "probes", counts[0], "correct", counts[1], "baselineCorrect", counts[2])));
        report.put("byFamily", familyRows);
        report.put("retrievalCalls", retrievalCalls);
        report.put("elapsedMillis", elapsedMillis);
        report.put("failures", failures.size() <= 25 ? failures : failures.subList(0, 25));
        report.put("failureCount", failures.size());
        Path reportPath = Path.of("target", "evaluation", "cp57-ninety-day-trajectory-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.println("[CP-57] scenarios=" + scenarios.size() + " probes=" + factTotal
                + " accuracy=" + accuracy + " CI95=[" + ci[0] + "," + ci[1] + "]"
                + " correction=" + correctionPreference + " withdrawnNonEmpty=" + withdrawnNonEmpty
                + " resurrections=" + resurrections + " crossUserLeaks=" + crossUserLeaks
                + " | baselineAccuracy=" + baselineAccuracy
                + " delta=" + (accuracy - baselineAccuracy)
                + " | failures=" + failures.size() + " elapsedMs=" + elapsedMillis);

        assertTrue(factTotal >= 200, "denominator: expected at least 200 scored fact probes");
        assertTrue(correctionTotal > 0 && withdrawnTotal > 0, "denominators must be non-zero");
        assertTrue(ci[0] <= accuracy && accuracy <= ci[1], "CI sanity");
        assertTrue(accuracy >= ACCURACY_THRESHOLD, "CP-57 factual accuracy below 0.95: " + report);
        assertEquals(CORRECTION_THRESHOLD, correctionPreference, 1e-9,
                "CP-57 correction preference must be exactly 1.0: " + report);
        assertEquals(0, withdrawnNonEmpty, "withdrawn probes must return zero evidence: " + report);
        assertEquals(0, resurrections, "FORGOTTEN memories must never resurrect: " + report);
        assertEquals(0, crossUserLeaks, "cross-user contamination must be zero: " + report);
    }

    // ---------------------------------------------------------------------
    // Trajectory replay (mirrors MemoryLifecycleServiceImpl semantics).
    // ---------------------------------------------------------------------

    private record Replay(Map<String, Long> keyIds, Map<Long, String> statusById) {}

    private void resetOwnerRows(long ownerId) {
        linkMapper.delete(new QueryWrapper<MemoryLink>().eq("user_id", ownerId));
        memoryMapper.delete(new QueryWrapper<MemoryCard>().eq("user_id", ownerId));
    }

    private Replay replay(long ownerId, List<JsonNode> events, int upToDay, LocalDateTime anchor) {
        Map<String, MemoryCard> cards = new LinkedHashMap<>();
        for (JsonNode event : events) {
            int day = event.path("day").asInt(-1);
            if (day > upToDay) continue;
            switch (event.path("op").asText()) {
                case "ADD" -> {
                    MemoryCard card = card(ownerId, event, anchor.plusDays(day));
                    memoryMapper.insert(card);
                    cards.put(event.path("key").asText(), card);
                }
                case "CONTRADICT", "SUPERSEDE" -> {
                    boolean contradict = "CONTRADICT".equals(event.path("op").asText());
                    MemoryCard old = cards.get(event.path("key").asText());
                    MemoryCard replacement = card(ownerId, event, anchor.plusDays(day));
                    memoryMapper.insert(replacement);
                    old.status = contradict ? "CONTRADICTED" : "SUPERSEDED";
                    old.versionNo = old.versionNo + 1;
                    old.supersededById = replacement.id;
                    memoryMapper.updateById(old);
                    MemoryLink link = new MemoryLink();
                    link.userId = ownerId;
                    link.sourceMemoryId = old.id;
                    link.targetMemoryId = replacement.id;
                    link.linkType = contradict ? "CONTRADICTS" : "SUPERSEDES";
                    link.strength = 0.9;
                    link.evidenceRefs = "cp57-fixture";
                    link.status = "ACTIVE";
                    linkMapper.insert(link);
                    cards.put(event.path("replacementKey").asText(), replacement);
                }
                case "REINFORCE" -> {
                    MemoryCard card = cards.get(event.path("key").asText());
                    card.recurrenceCount = card.recurrenceCount == null ? 2 : card.recurrenceCount + 1;
                    card.versionNo = card.versionNo + 1;
                    card.lastTouchedAt = anchor.plusDays(day);
                    memoryMapper.updateById(card);
                }
                case "ARCHIVE" -> {
                    MemoryCard card = cards.get(event.path("key").asText());
                    card.status = "ARCHIVED";
                    card.archivedAt = anchor.plusDays(day);
                    card.versionNo = card.versionNo + 1;
                    if (event.hasNonNull("title")) card.title = event.path("title").asText();
                    memoryMapper.updateById(card);
                }
                case "FORGET" -> {
                    MemoryCard card = cards.get(event.path("key").asText());
                    int nextVersion = card.versionNo + 1;
                    // Same explicit column-by-column redaction the product's FORGET branch
                    // performs (MyBatis-Plus updateById would skip the nulls).
                    memoryMapper.update(null, new UpdateWrapper<MemoryCard>()
                            .eq("id", card.id).eq("user_id", ownerId)
                            .set("title", "已按你的请求忘记").set("summary", null)
                            .set("emotion_tags", "[]").set("keyword_tags", "[]").set("people_tags", "[]")
                            .set("status", "FORGOTTEN").set("forgotten_at", anchor.plusDays(day))
                            .set("version_no", nextVersion)
                            .set("emotional_gravity", 0.0).set("user_importance", 0.0));
                    cards.put(event.path("key").asText(), memoryMapper.selectById(card.id));
                }
                default -> throw new IllegalStateException("unknown fixture op: " + event.path("op"));
            }
        }
        Map<String, Long> keyIds = new LinkedHashMap<>();
        Map<Long, String> statusById = new LinkedHashMap<>();
        cards.forEach((key, card) -> {
            keyIds.put(key, card.id);
            statusById.put(card.id, card.status);
        });
        return new Replay(keyIds, statusById);
    }

    private MemoryCard card(long ownerId, JsonNode event, LocalDateTime touchedAt) {
        MemoryCard card = new MemoryCard();
        card.userId = ownerId;
        card.title = event.path("title").asText();
        card.summary = event.path("summary").asText();
        card.memoryType = event.path("type").asText("FACT");
        card.memoryLayer = event.path("layer").asText("EPISODIC");
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        card.versionNo = 1;
        card.confidence = 0.9;
        card.emotionTags = "[]";
        card.keywordTags = "[]";
        card.peopleTags = "[]";
        card.intensityScore = 3.0;
        card.recurrenceCount = 1;
        card.userImportance = 3.0;
        card.triggerCount = 1;
        card.emotionalGravity = switch (card.memoryType) {
            case "RELATION" -> 0.9;
            case "TODO" -> 0.6;
            case "HABIT" -> 0.8;
            default -> 0.7;
        };
        card.lastTouchedAt = touchedAt;
        card.createdAt = touchedAt;
        return card;
    }

    /** Near-duplicate memories owned by a different user; owner packs must never
     *  contain them and the neighbor's pack must never contain the owner's rows.
     *  Returns the inserted honeypot row ids for the leak assertions. */
    private List<Long> insertNeighborHoneypots(long neighborId, List<JsonNode> events, LocalDateTime anchor) {
        JsonNode firstAdd = events.get(0);
        JsonNode lastContent = null;
        for (JsonNode event : events) {
            if (event.hasNonNull("summary")) lastContent = event;
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode source : List.of(firstAdd, lastContent == null ? firstAdd : lastContent)) {
            MemoryCard honeypot = new MemoryCard();
            honeypot.userId = neighborId;
            honeypot.title = source.path("title").asText();
            honeypot.summary = source.path("summary").asText();
            honeypot.memoryType = source.path("type").asText("FACT");
            honeypot.memoryLayer = source.path("layer").asText("EPISODIC");
            honeypot.status = "ACTIVE";
            honeypot.visibilityLevel = "PRIVATE";
            honeypot.consentScope = "AURORA_PRIVATE";
            honeypot.versionNo = 1;
            honeypot.confidence = 0.9;
            honeypot.emotionalGravity = 0.7;
            honeypot.lastTouchedAt = anchor.plusDays(80);
            memoryMapper.insert(honeypot);
            ids.add(honeypot.id);
        }
        return ids;
    }

    private List<Long> baselineTop3(long ownerId) {
        List<MemoryCard> actives = memoryMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", ownerId).eq("status", "ACTIVE"));
        actives.sort(Comparator.comparing(
                card -> card.lastTouchedAt == null ? LocalDateTime.MIN : card.lastTouchedAt,
                Comparator.reverseOrder()));
        return actives.stream().limit(MAX_RESULTS).map(card -> card.id).toList();
    }

    private static List<Long> idsFor(JsonNode keys, Map<String, Long> keyIds, JsonNode probe) {
        List<Long> result = new ArrayList<>();
        for (JsonNode key : keys) {
            Long id = keyIds.get(key.asText());
            if (id == null) throw new IllegalStateException(
                    "fixture key " + key.asText() + " absent at probe day " + probe.path("day"));
            result.add(id);
        }
        return result;
    }

    private static Map<String, Object> row(JsonNode scenario, JsonNode probe,
                                           List<String> returnedKeys, String problem) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", scenario.path("id").asText());
        row.put("family", scenario.path("family").asText());
        row.put("day", probe.path("day").asInt());
        row.put("kind", probe.path("kind").asText());
        row.put("query", probe.path("query").asText());
        row.put("returnedKeys", returnedKeys);
        row.put("problem", problem);
        return row;
    }

    // ---------------------------------------------------------------------
    // Exact (Clopper-Pearson) binomial 95% CI — the registry's ci_method.
    // ---------------------------------------------------------------------

    private static double[] clopperPearson95(int k, int n) {
        double alpha = 0.05;
        double lo = 0.0, hi = 1.0;
        if (k > 0) { // solve P(X >= k | p) = alpha/2, increasing in p
            double a = 0, b = 1;
            for (int iteration = 0; iteration < 200; iteration++) {
                double middle = (a + b) / 2;
                if (binomialTailAtLeast(k, n, middle) > alpha / 2) b = middle;
                else a = middle;
            }
            lo = (a + b) / 2;
        }
        if (k < n) { // solve P(X <= k | p) = alpha/2, decreasing in p
            double a = 0, b = 1;
            for (int iteration = 0; iteration < 200; iteration++) {
                double middle = (a + b) / 2;
                if (binomialCdfAtMost(k, n, middle) > alpha / 2) a = middle;
                else b = middle;
            }
            hi = (a + b) / 2;
        }
        return new double[]{lo, hi};
    }

    private static double binomialTailAtLeast(int k, int n, double p) {
        if (k <= 0) return 1.0;
        if (k > n) return 0.0;
        double logP = Math.log(p), logOneMinusP = Math.log1p(-p);
        double sum = 0;
        for (int i = k; i <= n; i++) {
            sum += Math.exp(logChoose(n, i) + i * logP + (n - i) * logOneMinusP);
        }
        return Math.min(1.0, sum);
    }

    private static double binomialCdfAtMost(int k, int n, double p) {
        if (k >= n) return 1.0;
        if (k < 0) return 0.0;
        double logP = Math.log(p), logOneMinusP = Math.log1p(-p);
        double sum = 0;
        for (int i = 0; i <= k; i++) {
            sum += Math.exp(logChoose(n, i) + i * logP + (n - i) * logOneMinusP);
        }
        return Math.min(1.0, sum);
    }

    private static double logChoose(int n, int k) {
        return logGamma(n + 1.0) - logGamma(k + 1.0) - logGamma(n - k + 1.0);
    }

    private static double logGamma(double x) {
        double[] coefficients = {676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7};
        if (x < 0.5) {
            return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1 - x);
        }
        x -= 1;
        double a = 0.99999999999980993;
        double t = x + 7.5;
        for (int i = 0; i < coefficients.length; i++) {
            a += coefficients[i] / (x + i + 1);
        }
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }
}
