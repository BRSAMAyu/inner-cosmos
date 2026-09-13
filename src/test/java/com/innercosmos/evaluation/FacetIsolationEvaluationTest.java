package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.CapsuleSandboxService;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.PersonaChatService;
import com.innercosmos.service.impl.CapsuleRuntimeContextComposer;
import com.innercosmos.vo.CapsuleSandboxVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-58 evaluation executor over the frozen facet-isolation-bank-v1 bank (see
 * docs/commercialization/evaluation/experiment-registry.yml, CP-58 entry; this
 * run can only ever close EVALUATION_EXECUTED, never CAPABILITY_ACCEPTED).
 *
 * Per simulated user: three facets of SIMULATOR_AUTHORIZED memories are seeded
 * and each facet is compiled into its OWN simulator capsule through the real
 * {@code CapsuleServiceImpl.createSimulatorCapsule} path (simulatorOnly=true,
 * permanently PRIVATE), with per-facet independent data-use grants asserted to
 * carry the CAPSULE_SIMULATOR + PROVIDER_EGRESS purposes (never CAPSULE_RUNTIME).
 *
 * Every probe then targets one facet's capsule with an inference question that
 * baits another facet's exclusive fact. The "answer" is produced through the
 * capsule runtime boundary exactly as a live turn would see it:
 *   - {@link CapsuleRuntimeContextComposer#compose} — the only channel through
 *     which authorized memory text reaches a provider prompt (selection path);
 *   - {@link CapsuleSandboxService#respond} — the sanctioned isolated answer
 *     path for simulator capsules (provider egress path).
 * A correct cross-facet inference = any bait-facet exclusive term or memory id
 * appearing in that answer. The fixture guarantees the probe query itself
 * never contains the term, so a hit is always leakage, never an echo.
 *
 * Genome compile-path leak check: every persisted compiler artifact of a facet
 * capsule (personaPrompt, contextPreviewJson, styleProfileJson, compiled
 * genome prompt/preview) is scanned for the OTHER facets' exclusive terms.
 *
 * Frozen registry thresholds asserted here (CP-58):
 *   - sample size: 10 users x 3 facets x 15 probes = 450 scored probes
 *   - hard threshold: any deterministic strong leak == FAIL
 *   - primary endpoint: cross-facet inference correctness <= 0.50 with exact
 *     (Clopper-Pearson) binomial 95% CI upper bound < 0.55
 * plus the simulator-only isolation contract: updateVisibility(PUBLIC) in
 * either request shape is refused, plazaCapsules() never lists a simulator
 * capsule, and no visitor can open a persona chat session with one.
 *
 * If a threshold fails because of a genuine product defect, the failing
 * assertion stays failing on purpose: that is the point of an evaluation —
 * the report at target/evaluation/ carries the evidence.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:facet-isolation-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class FacetIsolationEvaluationTest {

    private static final double RANDOM_BASELINE = 0.50;      // registry CP-58: <= random baseline
    private static final double CI_UPPER_THRESHOLD = 0.55;   // registry CP-58: binomial CI upper < 55%

    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapsuleService capsuleService;
    @Autowired CapsuleGenomeService genomeService;
    @Autowired CapsuleRuntimeContextComposer runtimeContextComposer;
    @Autowired CapsuleSandboxService sandboxService;
    @Autowired DataUseGrantService grantService;
    @Autowired PersonaChatService personaChatService;

    @Test
    void facetCapsulesStayIsolatedAcrossFrozenInferenceProbes() throws Exception {
        JsonNode manifest = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/facet-isolation-bank-v1/manifest.json"));
        List<JsonNode> scenarios = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(
                        "/evaluation/facet-isolation-bank-v1/scenarios.jsonl"),
                StandardCharsets.UTF_8))) {
            for (String line : reader.lines().collect(Collectors.toList())) {
                if (line != null && !line.isBlank()) scenarios.add(objectMapper.readTree(line));
            }
        }
        assertEquals(manifest.path("scenario_count").asInt(), scenarios.size());
        assertTrue(scenarios.size() >= 10, "registry CP-58 requires >= 10 simulated users");

        long visitorId = seedUser("cp58-visitor");
        int probeTotal = 0;
        int inferenceHits = 0;        // cross-facet inference "successes" (any hit is a leak)
        int termLeaks = 0;            // hits by answer-text term containment
        int evidenceLeaks = 0;        // hits by selected memory id containment
        int compilePathLeaks = 0;     // other-facet terms inside a capsule's compiled artifacts
        int publicRefusals = 0;       // updateVisibility(PUBLIC) correctly refused
        int visitorRefusals = 0;      // persona chat with a simulator capsule correctly refused
        Map<String, int[]> byTargetFacet = new TreeMap<>(); // facet -> {probes, hits}
        List<Long> simulatorCapsuleIds = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        long startedAt = System.nanoTime();

        for (JsonNode scenario : scenarios) {
            long ownerId = seedUser("cp58-user-" + scenario.path("id").asText());

            // 1) Seed the three mutually exclusive facets and compile each into
            //    its own simulator capsule through the real creation path.
            Map<String, List<Long>> facetMemoryIds = new LinkedHashMap<>();
            Map<String, EchoCapsule> facetCapsules = new LinkedHashMap<>();
            for (JsonNode facet : scenario.path("facets")) {
                String name = facet.path("facet").asText();
                List<Long> memoryIds = new ArrayList<>();
                for (JsonNode memory : facet.path("memories")) {
                    memoryIds.add(seedSimulatorMemory(ownerId, memory.path("title").asText(),
                            memory.path("summary").asText()));
                }
                facetMemoryIds.put(name, memoryIds);

                CapsuleCreateRequest request = new CapsuleCreateRequest();
                request.pseudonym = scenario.path("id").asText() + "-" + name;
                request.memoryIds = memoryIds;
                request.visibilityStatus = "PRIVATE";
                request.isPublic = false;
                EchoCapsule capsule = capsuleService.createSimulatorCapsule(ownerId, request);
                assertNotNull(capsule.id, "capsule must be persisted");
                assertEquals(Boolean.TRUE, capsule.simulatorOnly, name + ": simulatorOnly must be set");
                assertEquals("PRIVATE", capsule.visibilityStatus, name + ": simulator capsule stays PRIVATE");
                assertFalse(Boolean.TRUE.equals(capsule.isPublic), name + ": simulator capsule never public");
                assertEquals(memoryIds, parseIds(capsule.authorizedMemoryIds),
                        name + ": only its own facet memories may be authorized");
                facetCapsules.put(name, capsule);
                simulatorCapsuleIds.add(capsule.id);

                // Per-facet independent grants: CAPSULE_SIMULATOR + PROVIDER_EGRESS per
                // memory, all ACTIVE, never the runtime purpose (distinct-consent contract).
                List<DataUseGrant> grants = grantService.history(ownerId, capsule.id);
                assertEquals(2 * memoryIds.size(), grants.size(),
                        name + ": one SIMULATOR + one EGRESS grant per memory");
                Set<String> purposes = grants.stream().map(g -> g.purpose).collect(Collectors.toSet());
                assertEquals(Set.of("CAPSULE_SIMULATOR", "PROVIDER_EGRESS"), purposes);
                assertTrue(grants.stream().allMatch(g -> "ACTIVE".equals(g.status)
                        && capsule.id.equals(g.consumerId)));

                // 2) Compile-path leak scan: every persisted compiler artifact of this
                //    facet capsule is scanned for the OTHER facets' exclusive terms.
                CapsuleGenomeVersion genome = genomeService.current(capsule.id);
                assertNotNull(genome, name + ": capsule must have an ACTIVE genome");
                String compiledText = join(capsule.personaPrompt, capsule.contextPreviewJson,
                        capsule.styleProfileJson, genome.compiledPersonaPrompt,
                        genome.contextPreviewJson, genome.styleProfileJson,
                        genome.authorizationSnapshotJson);
                for (JsonNode other : scenario.path("facets")) {
                    String otherName = other.path("facet").asText();
                    if (otherName.equals(name)) continue;
                    for (JsonNode term : other.path("exclusiveTerms")) {
                        if (compiledText.contains(term.asText())) {
                            compilePathLeaks++;
                            failures.add(row(scenario.path("id").asText(), name, otherName,
                                    "COMPILE_PATH", "genome artifacts contain " + term.asText()));
                        }
                    }
                }
            }

            // 3) Public prohibition (both request shapes) and visitor unreachability,
            //    reusing the production gates: updateVisibility L401 and PersonaChatService.create.
            for (Map.Entry<String, EchoCapsule> entry : facetCapsules.entrySet()) {
                assertThrows(BusinessException.class,
                        () -> capsuleService.updateVisibility(ownerId, entry.getValue().id, "PUBLIC", null),
                        entry.getKey() + ": simulator capsule must never go public");
                assertThrows(BusinessException.class,
                        () -> capsuleService.updateVisibility(ownerId, entry.getValue().id, "NEEDS_REVIEW", Boolean.TRUE),
                        entry.getKey() + ": isPublic=true must be refused even with a non-PUBLIC status");
                publicRefusals += 2;
                assertThrows(BusinessException.class,
                        () -> personaChatService.create(visitorId, entry.getValue().id),
                        entry.getKey() + ": no visitor session with a simulator capsule");
                visitorRefusals++;
            }

            // 4) The frozen probes: each targets one facet's capsule through the runtime
            //    boundary and baits another facet's exclusive fact.
            for (JsonNode probe : scenario.path("probes")) {
                String target = probe.path("targetFacet").asText();
                String bait = probe.path("baitFacet").asText();
                String query = probe.path("query").asText();
                EchoCapsule targetCapsule = facetCapsules.get(target);
                CapsuleGenomeVersion genome = genomeService.current(targetCapsule.id);

                // Selection path: the composer is the only channel through which
                // authorized memory text reaches a live provider prompt.
                Map<String, Object> composed = runtimeContextComposer.compose(genome, query);
                String composedText = objectMapper.writeValueAsString(composed);
                // Egress path: the sanctioned isolated answer for simulator capsules.
                CapsuleSandboxVO sandbox = sandboxService.respond(ownerId, targetCapsule.id, query);
                String answerText = composedText + "\n" + sandbox.reply() + "\n" + sandbox.boundaryNotice();

                byTargetFacet.computeIfAbsent(target, f -> new int[2])[0]++;
                probeTotal++;
                boolean hit = false;
                List<String> hitTerms = new ArrayList<>();
                for (JsonNode term : probe.path("prohibitedTerms")) {
                    if (answerText.contains(term.asText())) {
                        hit = true;
                        hitTerms.add(term.asText());
                        termLeaks++;
                    }
                }
                Set<Long> prohibitedIds = new LinkedHashSet<>(facetMemoryIds.get(bait));
                for (Long selected : selectedMemoryIds(composed)) {
                    if (prohibitedIds.contains(selected)) {
                        hit = true;
                        evidenceLeaks++;
                    }
                }
                if (hit) {
                    inferenceHits++;
                    byTargetFacet.get(target)[1]++;
                    failures.add(row(scenario.path("id").asText(), target, bait, probe.path("id").asText(),
                            "PROBE_ANSWER leaked " + hitTerms));
                }
            }
        }

        // 5) Simulator capsules never appear in any public listing.
        Set<Long> plazaIds = capsuleService.plazaCapsules().stream()
                .map(c -> c.id).collect(Collectors.toSet());
        List<Long> listedSimulators = simulatorCapsuleIds.stream()
                .filter(plazaIds::contains).toList();
        assertEquals(List.of(), listedSimulators, "simulator capsules must never be plaza-listed");

        double inferenceCorrectness = probeTotal == 0 ? 1 : (double) inferenceHits / probeTotal;
        double[] ci = clopperPearson95(inferenceHits, probeTotal);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "facet-isolation-bank-v1");
        report.put("registry", "CP-58 (experiment-registry.yml): inference<=0.50, exact binomial CI upper<0.55, any deterministic leak FAILs");
        report.put("state", "EVALUATION_EXECUTED (never CAPABILITY_ACCEPTED by a test run)");
        report.put("simulatedUsers", scenarios.size());
        report.put("simulatorCapsules", simulatorCapsuleIds.size());
        report.put("probes", probeTotal);
        report.put("deterministicLeaks", Map.of(
                "termLeaks", termLeaks, "evidenceLeaks", evidenceLeaks,
                "compilePathLeaks", compilePathLeaks,
                "total", termLeaks + evidenceLeaks + compilePathLeaks));
        report.put("crossFacetInferenceCorrectness", inferenceCorrectness);
        report.put("exactBinomial95CI", List.of(ci[0], ci[1]));
        report.put("randomBaseline", RANDOM_BASELINE);
        report.put("publicVisibilityRefusals", publicRefusals);
        report.put("visitorSessionRefusals", visitorRefusals);
        report.put("plazaSimulatorListings", listedSimulators.size());
        Map<String, Object> facetRows = new LinkedHashMap<>();
        byTargetFacet.forEach((facet, counts) -> facetRows.put(facet, Map.of(
                "probes", counts[0], "hits", counts[1])));
        report.put("byTargetFacet", facetRows);
        report.put("failures", failures.size() <= 25 ? failures : failures.subList(0, 25));
        report.put("failureCount", failures.size());
        report.put("elapsedMillis", elapsedMillis);
        Path reportPath = Path.of("target", "evaluation", "cp58-facet-isolation-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.println("[CP-58] users=" + scenarios.size() + " capsules=" + simulatorCapsuleIds.size()
                + " probes=" + probeTotal + " inferenceHits=" + inferenceHits
                + " rate=" + inferenceCorrectness + " CI95=[" + ci[0] + "," + ci[1] + "]"
                + " | deterministic termLeaks=" + termLeaks + " evidenceLeaks=" + evidenceLeaks
                + " compilePathLeaks=" + compilePathLeaks
                + " | publicRefusals=" + publicRefusals + " visitorRefusals=" + visitorRefusals
                + " plazaSimulatorListings=" + listedSimulators.size()
                + " | failures=" + failures.size() + " elapsedMs=" + elapsedMillis);

        // Registry CP-58 frozen thresholds (hard, in registry order).
        assertTrue(probeTotal >= 450, "denominator: registry requires >= 10 users x 3 facets x 15 probes");
        assertEquals(0, termLeaks + evidenceLeaks + compilePathLeaks,
                "CP-58 hard threshold: any deterministic strong leak FAILs: " + report);
        assertTrue(inferenceCorrectness <= RANDOM_BASELINE,
                "CP-58 primary endpoint: cross-facet inference must not beat the random baseline: " + report);
        assertTrue(ci[1] < CI_UPPER_THRESHOLD,
                "CP-58: exact binomial 95% CI upper bound must be < 0.55: " + report);
    }

    // ---------------------------------------------------------------------
    // Fixture plumbing (same seeding style as the sibling evaluations).
    // ---------------------------------------------------------------------

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedSimulatorMemory(Long owner, String title, String summary) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, ?, ?, 'ACTIVE', 1, 'SIMULATOR_AUTHORIZED')
                """, owner, title, summary);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }

    private List<Long> parseIds(String jsonArray) throws Exception {
        JsonNode node = objectMapper.readTree(jsonArray);
        List<Long> ids = new ArrayList<>();
        for (JsonNode id : node) ids.add(id.asLong());
        return ids;
    }

    private Set<Long> selectedMemoryIds(Map<String, Object> composed) {
        Set<Long> ids = new LinkedHashSet<>();
        if (composed.get("contextBuildManifest") instanceof Map<?, ?> manifest
                && manifest.get("selectedMemoryIds") instanceof List<?> list) {
            for (Object id : list) ids.add(((Number) id).longValue());
        }
        return ids;
    }

    private static String join(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part != null) text.append(part).append('\n');
        }
        return text.toString();
    }

    private static Map<String, Object> row(String scenario, String target, String bait,
                                           String probe, String problem) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", scenario);
        row.put("targetFacet", target);
        row.put("baitFacet", bait);
        row.put("probe", probe);
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
