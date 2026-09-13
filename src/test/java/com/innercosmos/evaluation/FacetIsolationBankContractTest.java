package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-58 freeze contract for the facet-isolation-bank-v1 bank, following the
 * exact pattern of NinetyDayTrajectoryBankContractTest: the scenarios.jsonl
 * content hash must equal the manifest hash recorded at freeze time
 * (regenerate deliberately via generate.py), and the bank must satisfy the
 * coverage/structure contract pre-registered in
 * docs/commercialization/evaluation/experiment-registry.yml (CP-58:
 * &ge;10 simulated users &times; 3 mutually exclusive facets &times; 15
 * inference probes; every probe targets one facet and baits another facet's
 * exclusive fact; exclusive terms appear only in their own facet's corpus and
 * in no probe query, so a hit in a probe answer can only be a cross-facet
 * leak, never an echo of the question).
 */
class FacetIsolationBankContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FACETS = Set.of("WORK", "FAMILY", "HOBBY");
    private static final int PROBES_PER_FACET = 15;

    @Test
    void bankMatchesFrozenManifestAndFacetContract() throws IOException {
        byte[] body = new ClassPathResource(
                "evaluation/facet-isolation-bank-v1/scenarios.jsonl").getInputStream().readAllBytes();
        JsonNode manifest = JSON.readTree(new ClassPathResource(
                "evaluation/facet-isolation-bank-v1/manifest.json").getInputStream());

        // 1) Content freeze: file hash must equal the manifest hash recorded at freeze time.
        assertEquals(manifest.path("scenario_sha256").asText(), sha256(body),
                "bank edited without regenerating manifest — rerun generate.py");

        List<JsonNode> items = new ArrayList<>();
        for (String line : new String(body, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) items.add(JSON.readTree(line));
        }
        assertEquals(manifest.path("scenario_count").asInt(), items.size());
        assertEquals(manifest.path("probe_count").asInt(),
                items.stream().mapToInt(s -> s.path("probes").size()).sum());
        assertTrue(items.size() >= 10, "registry CP-58 requires >= 10 simulated users");

        Set<String> ids = new HashSet<>();
        Set<String> probeIds = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (JsonNode scenario : items) {
            String id = scenario.path("id").asText();
            assertTrue(ids.add(id), "duplicate scenario id " + id);
            problems.addAll(checkScenario(id, scenario, probeIds));
        }
        int totalProbes = probeIds.size();
        assertEquals(items.size() * 3 * PROBES_PER_FACET, totalProbes,
                "registry CP-58 requires exactly 3 facets x 15 probes per simulated user");
        assertTrue(problems.isEmpty(), "facet contract violations: " + problems);
    }

    /** Structural + mutual-exclusivity checks for one scenario; returns problem strings. */
    private static List<String> checkScenario(String id, JsonNode scenario, Set<String> probeIds) {
        List<String> problems = new ArrayList<>();
        JsonNode facets = scenario.path("facets");
        assertEquals(3, facets.size(), id + ": must carry exactly 3 facets");

        Map<String, List<String>> termsByFacet = new LinkedHashMap<>();
        Map<String, List<String>> keysByFacet = new LinkedHashMap<>();
        Map<String, String> corpusByFacet = new LinkedHashMap<>();
        for (JsonNode facet : facets) {
            String name = facet.path("facet").asText();
            assertTrue(FACETS.contains(name), id + ": unknown facet " + name);
            List<String> terms = new ArrayList<>();
            for (JsonNode term : facet.path("exclusiveTerms")) terms.add(term.asText());
            assertTrue(terms.size() >= 2, id + "/" + name + ": needs >= 2 exclusive terms");
            termsByFacet.put(name, terms);

            JsonNode memories = facet.path("memories");
            assertTrue(memories.isArray() && memories.size() >= 3,
                    id + "/" + name + ": needs >= 3 memories");
            List<String> keys = new ArrayList<>();
            StringBuilder corpus = new StringBuilder();
            for (JsonNode memory : memories) {
                String key = memory.path("key").asText();
                assertFalse(key.isBlank(), id + "/" + name + ": blank memory key");
                keys.add(key);
                assertFalse(memory.path("title").asText("").isBlank(), id + "/" + key + ": blank title");
                assertFalse(memory.path("summary").asText("").isBlank(), id + "/" + key + ": blank summary");
                corpus.append(memory.path("title").asText()).append(' ')
                        .append(memory.path("summary").asText()).append('\n');
            }
            keysByFacet.put(name, keys);
            corpusByFacet.put(name, corpus.toString());
        }

        // Mutual exclusivity + decidability, mirroring the generator's self-check.
        Set<String> allTerms = new TreeSet<>();
        termsByFacet.values().forEach(allTerms::addAll);
        for (String left : allTerms) {
            for (String right : allTerms) {
                if (!left.equals(right) && right.contains(left)) {
                    problems.add(id + ": term " + left + " is a substring of " + right);
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : termsByFacet.entrySet()) {
            String owner = entry.getKey();
            for (String term : entry.getValue()) {
                if (!corpusByFacet.get(owner).contains(term)) {
                    problems.add(id + ": term " + term + " not learnable from its own facet corpus");
                }
                for (String other : termsByFacet.keySet()) {
                    if (!other.equals(owner) && corpusByFacet.get(other).contains(term)) {
                        problems.add(id + ": term " + term + " of " + owner + " leaks into " + other);
                    }
                }
            }
        }

        // Probe contract: per target facet exactly 15 probes, each baiting another
        // facet, never leaking any term into the query, and pointing the prohibited
        // terms/memory keys at exactly the bait facet's own vocabulary.
        Map<String, Integer> probesPerFacet = new HashMap<>();
        for (JsonNode probe : scenario.path("probes")) {
            String pid = probe.path("id").asText();
            assertTrue(probeIds.add(pid), "duplicate probe id " + pid);
            String target = probe.path("targetFacet").asText();
            String bait = probe.path("baitFacet").asText();
            assertTrue(FACETS.contains(target) && FACETS.contains(bait),
                    pid + ": unknown facet in target/bait");
            assertFalse(target.equals(bait), pid + ": probe baits its own facet");
            String query = probe.path("query").asText();
            assertFalse(query.isBlank(), pid + ": blank query");
            for (String term : allTerms) {
                if (query.contains(term)) {
                    problems.add(pid + ": query leaks exclusive term " + term);
                }
            }
            List<String> expectedTerms = termsByFacet.get(bait);
            List<String> actualTerms = new ArrayList<>();
            probe.path("prohibitedTerms").forEach(t -> actualTerms.add(t.asText()));
            assertEquals(expectedTerms, actualTerms, pid + ": prohibitedTerms must be the bait facet's terms");
            List<String> expectedKeys = keysByFacet.get(bait);
            List<String> actualKeys = new ArrayList<>();
            probe.path("prohibitedMemoryKeys").forEach(k -> actualKeys.add(k.asText()));
            assertEquals(expectedKeys, actualKeys, pid + ": prohibitedMemoryKeys must be the bait facet's keys");
            for (String term : expectedTerms) {
                if (corpusByFacet.get(target).contains(term)) {
                    problems.add(pid + ": prohibited term " + term + " present in target corpus");
                }
            }
            probesPerFacet.merge(target, 1, Integer::sum);
        }
        for (String facet : FACETS) {
            assertEquals(PROBES_PER_FACET, probesPerFacet.getOrDefault(facet, 0),
                    id + ": facet " + facet + " must carry exactly " + PROBES_PER_FACET + " probes");
        }
        return problems;
    }

    private static String sha256(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
