package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-22 labeled retrieval evaluation over the frozen memory-retrieval-bank-v1: every case
 * carries hand-authored RELEVANT/DISTRACTOR labels, and the REAL MemoryRetrievalService is
 * scored on precision@k (a returned DISTRACTOR is a failure — an unrelated memory must never
 * enter the Evidence Pack) and recall of the RELEVANT set, with the manifest's SHA-256
 * locking the fixtures exactly like the conversation bank. A machine-readable report lands
 * in target/memory-retrieval-eval/ so future reranker/embedding changes must keep these
 * numbers or consciously re-freeze.
 */
@SpringBootTest
class MemoryRetrievalLabeledEvaluationTest {

    @Autowired MemoryRetrievalService retrieval;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired UserService userService;
    @Autowired JdbcTemplate jdbc;

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void labeledBankIsFrozenByShaAndEveryCaseMeetsItsPrecisionRecallContract() throws Exception {
        byte[] raw = getClass().getResourceAsStream(
                "/evaluation/memory-retrieval-bank-v1/scenarios.jsonl").readAllBytes();
        JsonNode manifest = new ObjectMapper().readTree(getClass().getResourceAsStream(
                "/evaluation/memory-retrieval-bank-v1/manifest.json"));
        assertEquals(manifest.path("scenario_sha256").asText(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),
                "the labeled retrieval bank is frozen; regenerate the manifest deliberately");

        List<JsonNode> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(
                        "/evaluation/memory-retrieval-bank-v1/scenarios.jsonl"), StandardCharsets.UTF_8))) {
            for (String line : reader.lines().collect(Collectors.toList())) {
                if (line != null && !line.isBlank()) cases.add(new ObjectMapper().readTree(line));
            }
        }
        assertEquals(manifest.path("scenario_count").asInt(), cases.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (JsonNode scenario : cases) {
            String id = scenario.path("id").asText();
            Long owner = seedUser();
            List<Long> relevant = new ArrayList<>();
            for (JsonNode memory : scenario.path("memories")) {
                MemoryCard card = insert(owner, memory);
                if ("RELEVANT".equals(memory.path("label").asText())) relevant.add(card.id);
            }

            JsonNode expect = scenario.path("expect");
            Integer maxResults = expect.has("maxResults") ? expect.path("maxResults").asInt() : null;
            MemoryEvidencePackVO pack = retrieval.retrieve(owner, new MemoryRetrievalQuery(
                    scenario.path("query").asText(), scenario.path("task").asText(),
                    null, maxResults, null, false));
            List<Long> returned = pack.evidence().stream()
                    .map(MemoryEvidencePackVO.Evidence::memoryId).toList();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", id);
            row.put("family", scenario.path("family").asText());
            row.put("returned", returned);
            row.put("relevantIds", relevant);

            if (expect.path("expectEmpty").asBoolean(false)) {
                if (!returned.isEmpty()) failures.add(id + ": meta-only query must retrieve nothing");
                row.put("precision", "n/a (empty by design)");
                rows.add(row);
                continue;
            }

            long hits = returned.stream().filter(relevant::contains).count();
            double precision = returned.isEmpty() ? 0.0 : (double) hits / returned.size();
            double recall = relevant.isEmpty() ? 1.0 : (double) hits / relevant.size();
            row.put("precision", precision);
            row.put("recall", recall);
            rows.add(row);

            if (precision < expect.path("precisionAtLeast").asDouble()) {
                failures.add(id + ": precision " + precision + " < "
                        + expect.path("precisionAtLeast").asDouble()
                        + " — a DISTRACTOR entered the Evidence Pack");
            }
            if (recall < expect.path("recallAtLeast").asDouble()) {
                failures.add(id + ": recall " + recall + " < " + expect.path("recallAtLeast").asDouble());
            }
            if (expect.has("firstMustBeTitle")) {
                String firstTitle = pack.evidence().isEmpty() ? "" : pack.evidence().get(0).title();
                if (!expect.path("firstMustBeTitle").asText().equals(firstTitle)) {
                    failures.add(id + ": first evidence is \"" + firstTitle + "\", expected \""
                            + expect.path("firstMustBeTitle").asText() + "\"");
                }
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "memory-retrieval-bank-v1");
        report.put("caseCount", rows.size());
        report.put("runs", rows);
        report.put("failures", failures);
        Path reportPath = Path.of("target", "memory-retrieval-eval", "labeled-retrieval-report.json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertTrue(failures.isEmpty(), "labeled retrieval contract violations: " + failures);
    }

    private MemoryCard insert(Long owner, JsonNode memory) {
        MemoryCard card = new MemoryCard();
        card.userId = owner;
        card.title = memory.path("title").asText();
        card.summary = memory.path("summary").asText();
        card.memoryType = memory.path("type").asText("FACT");
        card.memoryLayer = memory.path("layer").asText("EPISODIC");
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        card.versionNo = 1;
        card.emotionalGravity = 0.4;
        memoryMapper.insert(card);
        return card;
    }

    private Long seedUser() {
        RegisterRequest request = new RegisterRequest();
        request.username = "mr-eval-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(28).toString();
        request.adultConfirmed = true;
        User user = userService.register(request);
        return user.id;
    }
}
