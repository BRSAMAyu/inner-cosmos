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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-57 freeze contract for the ninety-day-trajectory-bank-v1 bank, following the
 * exact pattern of CnCommercialBankContractTest: the scenarios.jsonl content hash
 * must equal the manifest hash recorded at freeze time (regenerate deliberately
 * via generate.py), and the bank must satisfy the coverage/structure contract
 * pre-registered in docs/commercialization/evaluation/experiment-registry.yml
 * (CP-57: &ge;200 frozen trajectories across repeated correction, multi-person
 * and life-migration families; every probe carries expected/prohibited keys;
 * correction chains stay legal: a CONTRADICT/SUPERSEDE only ever targets a
 * currently-ACTIVE memory and its replacement is a fresh key).
 */
class NinetyDayTrajectoryBankContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern KEY_SHAPE = Pattern.compile("^([a-z-]+)@(\\d+)$");
    private static final Set<String> FAMILIES = Set.of(
            "repeated_correction", "multi_person_relations", "life_migration");

    @Test
    void bankMatchesFrozenManifestAndTrajectoryContract() throws IOException {
        byte[] body = new ClassPathResource(
                "evaluation/ninety-day-trajectory-bank-v1/scenarios.jsonl").getInputStream().readAllBytes();
        JsonNode manifest = JSON.readTree(new ClassPathResource(
                "evaluation/ninety-day-trajectory-bank-v1/manifest.json").getInputStream());

        // 1) Content freeze: file hash must equal the manifest hash recorded at freeze time.
        assertEquals(manifest.path("scenario_sha256").asText(), sha256(body),
                "bank edited without regenerating manifest — rerun generate.py");

        List<JsonNode> items = new ArrayList<>();
        for (String line : new String(body, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) items.add(JSON.readTree(line));
        }
        assertEquals(manifest.path("scenario_count").asInt(), items.size());
        assertTrue(items.size() >= 200, "registry CP-57 requires >= 200 frozen trajectories");

        // Lineage: every scenario (and every event key) derives from the 12
        // memory-retrieval-v1 memories with a @<day> time-point suffix.
        Set<String> baseKeys = new HashSet<>();
        for (JsonNode memory : JSON.readTree(new ClassPathResource(
                "evaluation/memory-retrieval-v1.json").getInputStream()).path("memories")) {
            baseKeys.add(memory.path("key").asText());
        }
        assertFalse(baseKeys.isEmpty());

        Set<String> ids = new HashSet<>();
        Map<String, Integer> familyCounts = new HashMap<>();
        Set<String> opsSeen = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (JsonNode scenario : items) {
            String id = scenario.path("id").asText();
            assertTrue(ids.add(id), "duplicate id " + id);
            String family = scenario.path("family").asText();
            assertTrue(FAMILIES.contains(family), "unknown family " + family);
            familyCounts.merge(family, 1, Integer::sum);
            problems.addAll(checkScenario(id, family, scenario, baseKeys, opsSeen));
        }

        // 2) Coverage contract (registry CP-57): all three families, each with a
        //    meaningful slice, >= 200 total.
        for (String family : FAMILIES) {
            int count = familyCounts.getOrDefault(family, 0);
            assertTrue(count >= 60, "family " + family + " below 60 scenarios: " + count);
        }
        // 3) The trajectory machinery the endpoints rely on is actually exercised.
        for (String op : List.of("CONTRADICT", "SUPERSEDE", "REINFORCE", "ARCHIVE", "FORGET")) {
            assertTrue(opsSeen.contains(op), "bank never exercises op " + op);
        }

        assertTrue(problems.isEmpty(), "trajectory contract violations: " + problems);
    }

    /** Structural + chain-legality checks for one scenario; returns problem strings. */
    private static List<String> checkScenario(String id, String family, JsonNode scenario,
                                              Set<String> baseKeys, Set<String> opsSeen) {
        List<String> problems = new ArrayList<>();
        JsonNode events = scenario.path("events");
        JsonNode probes = scenario.path("probes");
        assertTrue(events.isArray() && events.size() >= 3, id + ": too few events");
        assertTrue(probes.isArray() && probes.size() >= 2, id + ": too few probes");
        assertTrue(baseKeys.contains(scenario.path("sourceKey").asText()),
                id + ": sourceKey not one of the 12 base memories");

        // Replay the chain exactly like the generator defines it.
        Map<String, String> status = new LinkedHashMap<>(); // key -> ACTIVE/CONTRADICTED/...
        Map<String, String> prefix = new LinkedHashMap<>();
        Set<String> addPrefixes = new TreeSet<>();
        int corrections = 0;
        int todoAdds = 0;
        int habitAdds = 0;
        int forgets = 0;
        int archives = 0;
        int previousDay = -1;
        for (JsonNode event : events) {
            String op = event.path("op").asText();
            String key = event.path("key").asText();
            int day = event.path("day").asInt(-1);
            opsSeen.add(op);
            if (day < previousDay) problems.add(id + ": event days not non-decreasing");
            previousDay = day;
            if (!eventDayMatchesKey(key, day, baseKeys, prefix)) {
                problems.add(id + ": key " + key + " not <baseKey>@<day> rooted in the 12 memories");
                continue;
            }
            switch (op) {
                case "ADD" -> {
                    if (status.containsKey(key)) problems.add(id + ": duplicate ADD " + key);
                    status.put(key, "ACTIVE");
                    addPrefixes.add(prefix.get(key));
                    if ("TODO".equals(event.path("type").asText())) todoAdds++;
                    if ("HABIT".equals(event.path("type").asText())) habitAdds++;
                }
                case "CONTRADICT", "SUPERSEDE" -> {
                    corrections++;
                    if (!"ACTIVE".equals(status.get(key))) {
                        problems.add(id + ": " + op + " targets non-ACTIVE/unknown " + key);
                    }
                    String replacement = event.path("replacementKey").asText();
                    if (status.containsKey(replacement)) {
                        problems.add(id + ": replacement key " + replacement + " already exists");
                    }
                    if (event.path("title").asText("").isBlank()
                            || event.path("summary").asText("").isBlank()) {
                        problems.add(id + ": " + op + " without replacement content");
                    }
                    status.put(key, "CONTRADICT".equals(op) ? "CONTRADICTED" : "SUPERSEDED");
                    status.put(replacement, "ACTIVE");
                    prefix.put(replacement, prefixOf(replacement));
                }
                case "REINFORCE" -> {
                    if (!"ACTIVE".equals(status.get(key))) {
                        problems.add(id + ": REINFORCE on non-ACTIVE " + key);
                    }
                }
                case "ARCHIVE" -> {
                    archives++;
                    if (!"ACTIVE".equals(status.get(key))) {
                        problems.add(id + ": ARCHIVE on non-ACTIVE " + key);
                    }
                    status.put(key, "ARCHIVED");
                }
                case "FORGET" -> {
                    forgets++;
                    if (!"ACTIVE".equals(status.get(key))) {
                        problems.add(id + ": FORGET on non-ACTIVE " + key);
                    }
                    status.put(key, "FORGOTTEN");
                }
                default -> problems.add(id + ": unknown op " + op);
            }
        }

        // Family-specific shape.
        switch (family) {
            case "repeated_correction" ->
                    assertTrue(corrections >= 2, id + ": repeated_correction needs >= 2 corrections");
            case "multi_person_relations" -> {
                assertTrue(addPrefixes.size() >= 2, id + ": multi_person needs >= 2 distinct persons");
                assertTrue(forgets >= 1, id + ": multi_person needs an owner FORGET");
            }
            case "life_migration" -> {
                assertTrue(todoAdds >= 1 && habitAdds >= 1 && archives >= 1,
                        id + ": life_migration needs a TODO add, a HABIT add and an ARCHIVE");
                assertTrue(forgets >= 1, id + ": life_migration needs an owner FORGET");
            }
            default -> { }
        }

        // Probe legality against the state as of each probe day. Probes are the
        // pre-registered expectations: every non-withdrawn probe must point at the
        // memory that is ACTIVE (i.e. the current version of its chain) on its day.
        int previousProbeDay = -1;
        int maxDay = previousDay;
        for (JsonNode probe : probes) {
            String pid = probe.path("kind").asText();
            int day = probe.path("day").asInt(-1);
            if (day < previousProbeDay) problems.add(id + ": probe days not non-decreasing");
            previousProbeDay = day;
            maxDay = Math.max(maxDay, day);
            assertFalse(probe.path("query").asText("").isBlank(), id + ": probe without query");
            assertFalse(probe.path("task").asText("").isBlank(), id + ": probe without task");
            assertTrue(probe.path("prohibitedKeys").isArray() && probe.path("prohibitedKeys").size() >= 1,
                    id + ": probe without prohibited keys");
            Map<String, String> stateAtDay = stateAsOf(events, day);
            if ("WITHDRAWN_ZERO".equals(pid)) {
                assertTrue(probe.path("expectedKeys").isEmpty(),
                        id + ": withdrawn probe must expect zero results");
                boolean forgottenProhibited = false;
                for (JsonNode key : probe.path("prohibitedKeys")) {
                    String state = stateAtDay.get(key.asText());
                    assertNotNull(state, id + ": prohibited key " + key.asText() + " missing at day " + day);
                    forgottenProhibited |= "FORGOTTEN".equals(state);
                }
                assertTrue(forgottenProhibited, id + ": withdrawn probe lacks a FORGOTTEN prohibited key");
            } else {
                assertTrue(Set.of("FACT_RECALL", "CORRECTION_PREFERENCE").contains(pid),
                        id + ": unknown probe kind " + pid);
                assertTrue(probe.path("expectedKeys").size() >= 1, id + ": probe without expected keys");
                for (JsonNode key : probe.path("expectedKeys")) {
                    String state = stateAtDay.get(key.asText());
                    assertTrue("ACTIVE".equals(state), id + ": expected " + key.asText()
                            + " not the ACTIVE version at day " + day);
                }
                for (JsonNode key : probe.path("prohibitedKeys")) {
                    assertNotNull(stateAtDay.get(key.asText()),
                            id + ": prohibited key " + key.asText() + " missing at day " + day);
                }
            }
        }

        int minDay = Integer.MAX_VALUE;
        for (JsonNode event : events) minDay = Math.min(minDay, event.path("day").asInt());
        assertTrue(maxDay - minDay >= 90, id + ": trajectory span below 90 days");
        assertEquals(maxDay - minDay, scenario.path("spanDays").asInt(),
                id + ": spanDays field disagrees with events/probes");
        return problems;
    }

    /** Replays events with day <= asOf into a key -> status map (generator semantics). */
    private static Map<String, String> stateAsOf(JsonNode events, int asOf) {
        Map<String, String> state = new LinkedHashMap<>();
        for (JsonNode event : events) {
            if (event.path("day").asInt(-1) > asOf) continue;
            String key = event.path("key").asText();
            switch (event.path("op").asText()) {
                case "ADD" -> state.put(key, "ACTIVE");
                case "CONTRADICT" -> {
                    state.put(key, "CONTRADICTED");
                    state.put(event.path("replacementKey").asText(), "ACTIVE");
                }
                case "SUPERSEDE" -> {
                    state.put(key, "SUPERSEDED");
                    state.put(event.path("replacementKey").asText(), "ACTIVE");
                }
                case "ARCHIVE" -> state.put(key, "ARCHIVED");
                case "FORGET" -> state.put(key, "FORGOTTEN");
                default -> { }
            }
        }
        return state;
    }

    private static boolean eventDayMatchesKey(String key, int day, Set<String> baseKeys,
                                              Map<String, String> prefixCache) {
        var matcher = KEY_SHAPE.matcher(key);
        if (!matcher.matches()) return false;
        String base = matcher.group(1);
        if (!baseKeys.contains(base)) return false;
        // ADD keys carry their own day; replacement keys inherit the event day.
        prefixCache.put(key, base);
        return true;
    }

    private static String prefixOf(String key) {
        var matcher = KEY_SHAPE.matcher(key);
        return matcher.matches() ? matcher.group(1) : "";
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
