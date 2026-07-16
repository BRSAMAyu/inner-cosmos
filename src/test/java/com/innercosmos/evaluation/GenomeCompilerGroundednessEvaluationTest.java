package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.service.CapsuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Scores whether every piece of evidence the Genome compiler cites (styleProfileJson's
 * voiceEvidence, contextPreviewJson's per-scene memories[], and every v3 Genome IR category) is
 * genuinely grounded in memories the
 * owner authorized — never another user's memory, never a memory the consent scope excludes
 * (LOCAL_ONLY/NO_EXTERNAL_PROCESSING/SIMULATOR_AUTHORIZED), and never anything beyond the
 * capsule's own persisted authorizedMemoryIds. Each scenario requests a mix of legitimate and
 * illegitimate memoryIds; a leak shows up as an ungrounded citation, not a guess. Addresses the
 * Campaign C punch-list's "no real fidelity signal" gap from the structural/boundary side: this
 * does not (and cannot, without a real LLM provider) prove the compiled voice reads like the real
 * user, but it does prove the compiler never fabricates evidence from memories it had no right to
 * use — a real, machine-verifiable floor underneath any future fidelity claim.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:genome-groundedness-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class GenomeCompilerGroundednessEvaluationTest {
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapsuleService capsuleService;

    @Test
    void syntheticAnnotatedScenariosCiteOnlyGenuinelyAuthorizedMemories() throws Exception {
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/genome-compiler-groundedness-v1.json"));

        int ungroundedCitations = 0;
        int requestedButExcludedStillCounted = 0;
        Map<String, Object> caseReports = new LinkedHashMap<>();

        for (JsonNode scenario : dataset.path("scenarios")) {
            String key = scenario.path("key").asText();
            Long owner = seedUser(key + "-owner");

            List<Long> legitimateIds = new ArrayList<>();
            for (JsonNode summaryNode : scenario.path("legitimateSummaries")) {
                legitimateIds.add(seedMemory(owner, summaryNode.asText(), "AURORA_PRIVATE"));
            }

            List<Long> illegitimateIds = new ArrayList<>();
            for (JsonNode illegit : scenario.path("illegitimate")) {
                String kind = illegit.path("kind").asText();
                if ("OTHER_USER".equals(kind)) {
                    Long stranger = seedUser(key + "-stranger");
                    illegitimateIds.add(seedMemory(stranger, illegit.path("summary").asText(), "AURORA_PRIVATE"));
                } else {
                    illegitimateIds.add(seedMemory(owner, illegit.path("summary").asText(),
                            illegit.path("consentScope").asText()));
                }
            }

            List<Long> requested = new ArrayList<>(legitimateIds);
            requested.addAll(illegitimateIds);
            CapsuleCreateRequest request = new CapsuleCreateRequest();
            request.pseudonym = "groundedness-" + key;
            request.memoryIds = requested;
            request.visibilityStatus = "PRIVATE";
            request.isPublic = false;
            EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

            Set<Long> persistedAuthorized = parseIdArray(capsule.authorizedMemoryIds);
            Set<Long> legitimateSet = new LinkedHashSet<>(legitimateIds);
            boolean excludedStillCounted = !legitimateSet.equals(persistedAuthorized);
            if (excludedStillCounted) requestedButExcludedStillCounted++;

            Set<Long> citedIds = new LinkedHashSet<>();
            JsonNode styleProfile = objectMapper.readTree(capsule.styleProfileJson);
            if (styleProfile.has("voiceEvidence")) {
                styleProfile.get("voiceEvidence").forEach(themeIds ->
                        themeIds.forEach(id -> citedIds.add(id.asLong())));
            }
            JsonNode preview = objectMapper.readTree(capsule.contextPreviewJson);
            if (preview.has("scenes")) {
                preview.get("scenes").forEach(scene -> {
                    if (scene.has("memories")) {
                        scene.get("memories").forEach(ref -> citedIds.add(ref.get("memoryId").asLong()));
                    }
                });
            }
            JsonNode ir = preview.path("genomeIr");
            for (String category : List.of("claims", "values", "habits", "temporalState")) {
                ir.path(category).forEach(feature -> feature.path("evidence").forEach(ref ->
                        citedIds.add(ref.path("memoryId").asLong())));
            }

            List<Long> ungrounded = citedIds.stream().filter(id -> !legitimateSet.contains(id)).toList();
            ungroundedCitations += ungrounded.size();

            caseReports.put(key, Map.of(
                    "legitimateCount", legitimateIds.size(),
                    "illegitimateRequested", illegitimateIds.size(),
                    "persistedAuthorizedCount", persistedAuthorized.size(),
                    "excludedStillCounted", excludedStillCounted,
                    "ungroundedCitations", ungrounded));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("scenarios", dataset.path("scenarios").size());
        report.put("ungroundedCitations", ungroundedCitations);
        report.put("requestedButExcludedStillCounted", requestedButExcludedStillCounted);
        report.put("perCase", caseReports);
        Path reportPath = Path.of("target", "evaluation", "genome-compiler-groundedness-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        JsonNode thresholds = dataset.path("thresholds");
        if (ungroundedCitations > thresholds.path("ungroundedCitations").asInt()) {
            fail("compiler cited an illegitimate memory as evidence: " + report);
        }
        if (requestedButExcludedStillCounted > thresholds.path("requestedButExcludedStillCounted").asInt()) {
            fail("an excluded memory was still persisted into authorizedMemoryIds: " + report);
        }
    }

    private Set<Long> parseIdArray(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asLong).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedMemory(Long owner, String summary, String consentScope) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, '片段', ?, 'ACTIVE', 1, ?)
                """, owner, summary, consentScope);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }
}
