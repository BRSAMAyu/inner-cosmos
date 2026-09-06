package com.innercosmos.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * CP-04 freeze contract: the cn-commercial-bank-v1 scenario bank is a governed evaluation
 * asset. This test fails the build when the bank is edited without regenerating the manifest
 * (content hash), when required coverage families are missing, when held-out items leak into
 * tuning splits, or when a scenario carries content instead of a behavioral expectation.
 */
class CnCommercialBankContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CORE_FAMILIES = Set.of(
            "negation", "irony_sarcasm", "relationship_change", "implicit_refusal",
            "long_term_correction", "deactivation_reactivation", "dialect_mix", "long_tail");
    private static final Set<String> RED_TEAM_FAMILIES = Set.of(
            "red_team_crisis", "red_team_privacy_injection", "red_team_consent_boundary");

    @Test
    void bankMatchesFrozenManifestAndCoverageContract() throws IOException {
        byte[] body = new ClassPathResource(
                "evaluation/cn-commercial-bank-v1/scenarios.jsonl").getInputStream().readAllBytes();
        JsonNode manifest = JSON.readTree(new ClassPathResource(
                "evaluation/cn-commercial-bank-v1/manifest.json").getInputStream());

        // 1) Content freeze: file hash must equal the manifest hash recorded at freeze time.
        String actualSha = sha256(body);
        assertEquals(manifest.get("scenario_sha256").asText(), actualSha,
                "bank edited without regenerating manifest — rerun generate_cn_bank_v1.py");

        List<JsonNode> items = new ArrayList<>();
        for (String line : new String(body, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                items.add(JSON.readTree(line));
            }
        }
        assertEquals(manifest.get("scenario_count").asInt(), items.size());

        // 2) Unique ids, unique (family, title), required fields on every item.
        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        Map<String, Integer> familyCounts = new HashMap<>();
        for (JsonNode item : items) {
            assertTrue(item.hasNonNull("id"), "missing id");
            assertTrue(ids.add(item.path("id").asText()), "duplicate id " + item.path("id"));
            assertTrue(titles.add(item.path("family").asText() + "|" + item.path("title").asText()),
                    "duplicate scenario " + item.path("title"));
            assertNotNull(item.path("expected_behavior").asText(null),
                    "scenario without expected_behavior: " + item.path("id"));
            assertFalse(item.path("provenance").asText("").isBlank());
            assertFalse(item.path("sensitivity").asText("").isBlank());
            assertTrue(item.path("turns").isArray() && item.path("turns").size() >= 1);
            familyCounts.merge(item.path("family").asText(), 1, Integer::sum);
        }

        // 3) Coverage: all 8 core families, all 3 red-team families, journey tasks, core >= 200.
        int core = 0;
        for (String family : CORE_FAMILIES) {
            assertTrue(familyCounts.containsKey(family), "missing core family " + family);
            core += familyCounts.get(family);
        }
        for (String family : RED_TEAM_FAMILIES) {
            assertTrue(familyCounts.containsKey(family), "missing red-team family " + family);
        }
        assertTrue(core >= 200, "core scenario bank below 200: " + core);
        assertEquals(12, familyCounts.getOrDefault("journey_task", 0));

        // 4) Split discipline: red team and journey rubrics are always held_out; the three
        //    splits are exactly train/development/held_out; held_out never appears as train.
        for (JsonNode item : items) {
            String split = item.path("split").asText();
            assertTrue(Set.of("train", "development", "held_out").contains(split),
                    "bad split " + split);
            if (item.path("family").asText().startsWith("red_team")
                    || "journey_task".equals(item.path("family").asText())) {
                assertEquals("held_out", split, "red team / journey rubric must stay held_out");
            }
        }
        assertTrue(familyCounts.values().stream().mapToInt(Integer::intValue).sum() >= 240);

        // 5) Provenance discipline: nothing may claim to be real user data.
        for (JsonNode item : items) {
            String provenance = item.path("provenance").asText();
            assertFalse(provenance.contains("REAL_USER") || provenance.contains("PRODUCTION"),
                    "bank must stay synthetic/blueprint-sourced: " + item.path("id"));
        }
    }

    private static String sha256(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
