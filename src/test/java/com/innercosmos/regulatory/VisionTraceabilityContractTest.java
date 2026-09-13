package com.innercosmos.regulatory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-61 vision traceability guard (早写测试、晚关包门 — agent triage's advice taken):
 * the ledger's 77 nodes (64 packages + 13 stage sub-gates) must form the closure that
 * CP-61 aggregates, the blueprint §11 V01–V20 table and every ledger vision_ids line
 * must agree BIDIRECTIONALLY, and none of the three terminal verdicts
 * (PRODUCT_READY / CN_RELEASE_READY / BUSINESS_VALIDATED) may exist as a ledger
 * status — those are owner/independent-signoff facts, never agent-writable fields.
 */
class VisionTraceabilityContractTest {

    private static final Path LEDGER =
            Path.of("docs", "commercialization", "ledger", "commercial-cn-ledger.yml");

    private static final List<String> SUB_GATES = List.of(
            "CP-12A", "CP-12B", "CP-50A", "CP-50B", "CP-51A", "CP-51B",
            "CP-54A", "CP-54B", "CP-60A", "CP-60B", "CP-60C", "CP-63A", "CP-63B");

    /** Blueprint §11 (L963-982): V → the CP set that must complete for the promise. */
    private static final Map<String, Set<String>> VISION_TO_PACKAGES = Map.ofEntries(
            Map.entry("V01", Set.of("CP-01", "CP-02", "CP-05", "CP-06", "CP-08", "CP-10",
                    "CP-13", "CP-36", "CP-44", "CP-51", "CP-52", "CP-53", "CP-63")),
            Map.entry("V02", Set.of("CP-09", "CP-10", "CP-11", "CP-12", "CP-24", "CP-43", "CP-60")),
            Map.entry("V03", Set.of("CP-04", "CP-17", "CP-18", "CP-19", "CP-22", "CP-27", "CP-57")),
            Map.entry("V04", Set.of("CP-08", "CP-19", "CP-20", "CP-25", "CP-26")),
            Map.entry("V05", Set.of("CP-07", "CP-14", "CP-15", "CP-21", "CP-22", "CP-23",
                    "CP-24", "CP-54", "CP-57")),
            Map.entry("V06", Set.of("CP-09", "CP-12", "CP-21", "CP-23", "CP-24", "CP-57")),
            Map.entry("V07", Set.of("CP-07", "CP-10", "CP-13", "CP-14", "CP-15", "CP-16",
                    "CP-17", "CP-29", "CP-31", "CP-37", "CP-48", "CP-50", "CP-58", "CP-62", "CP-63")),
            Map.entry("V08", Set.of("CP-26", "CP-27", "CP-41", "CP-42", "CP-46", "CP-60")),
            Map.entry("V09", Set.of("CP-20", "CP-28", "CP-60")),
            Map.entry("V10", Set.of("CP-04", "CP-14", "CP-15", "CP-29", "CP-30", "CP-31", "CP-58")),
            Map.entry("V11", Set.of("CP-04", "CP-23", "CP-32", "CP-34", "CP-54", "CP-59")),
            Map.entry("V12", Set.of("CP-12", "CP-32", "CP-33", "CP-34", "CP-35", "CP-36",
                    "CP-54", "CP-59")),
            Map.entry("V13", Set.of("CP-06", "CP-07", "CP-08", "CP-16", "CP-20", "CP-33",
                    "CP-34", "CP-35", "CP-36", "CP-48", "CP-51", "CP-59", "CP-63")),
            Map.entry("V14", Set.of("CP-09", "CP-11", "CP-12", "CP-13", "CP-18", "CP-26",
                    "CP-27", "CP-41", "CP-42", "CP-43", "CP-44", "CP-46", "CP-50", "CP-60")),
            Map.entry("V15", Set.of("CP-16", "CP-17", "CP-18", "CP-37", "CP-38", "CP-39",
                    "CP-40", "CP-49", "CP-50", "CP-62", "CP-63")),
            Map.entry("V16", Set.of("CP-01", "CP-03", "CP-04", "CP-17", "CP-19", "CP-22",
                    "CP-25", "CP-28", "CP-30", "CP-40", "CP-49", "CP-56", "CP-57", "CP-58")),
            Map.entry("V17", Set.of("CP-03", "CP-40", "CP-42", "CP-45", "CP-46", "CP-47",
                    "CP-48", "CP-55", "CP-62", "CP-64")),
            Map.entry("V18", Set.of("CP-02", "CP-03", "CP-31", "CP-32", "CP-35", "CP-53",
                    "CP-54", "CP-55", "CP-56", "CP-59", "CP-64")),
            Map.entry("V19", Set.of("CP-01", "CP-05", "CP-06", "CP-44", "CP-45", "CP-47",
                    "CP-49", "CP-51", "CP-53", "CP-55", "CP-56", "CP-63", "CP-64")),
            Map.entry("V20", Set.of("CP-15", "CP-43", "CP-50", "CP-58", "CP-60", "CP-62", "CP-64")));

    private static final Set<String> TERMINAL_VERDICTS = Set.of(
            "PRODUCT_READY", "CN_RELEASE_READY", "BUSINESS_VALIDATED");

    private record Node(String id, Set<String> dependsOn, Set<String> visionIds, String status) {
    }

    private static final Pattern ID_LINE = Pattern.compile("^  - id:\\s*(\\S+)\\s*$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    @Test
    void cp61AggregatesTheFull77NodeClosureWithoutCycles() throws IOException {
        Map<String, Node> nodes = parseLedger();
        List<String> failures = new ArrayList<>();

        // 64 main packages + 13 sub-gates = the blueprint's 77 aggregation nodes.
        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= 64; i++) {
            expected.add(String.format("CP-%02d", i));
        }
        expected.addAll(SUB_GATES);
        assertEquals(expected, nodes.keySet(),
                "the ledger must carry exactly the 64 packages + 13 sub-gates");

        // Transitive closure of CP-61's dependencies must reach every node (77/77).
        // A stage sub-gate is PART of its parent package (CP-51B ⊂ CP-51), so depending
        // on either side implies the whole unit — modeled as bidirectional implied
        // edges without rewriting the ledger's explicit depends_on lines.
        Set<String> closure = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add("CP-61");
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!closure.add(current)) {
                continue;
            }
            for (String dep : expandImplied(nodes, current)) {
                if (!nodes.containsKey(dep)) {
                    failures.add(current + " depends on unknown node " + dep);
                    continue;
                }
                if (!closure.contains(dep)) {
                    queue.add(dep);
                }
            }
        }
        for (String missing : new HashSet<>(expected)) {
            if (!closure.contains(missing)) {
                failures.add("CP-61 closure misses " + missing);
            }
        }
        assertTrue(failures.isEmpty(), "CP-61 aggregation closure violations: " + failures);

        // No dependency cycles: a cycle would make "aggregate everything" ill-defined.
        for (String start : nodes.keySet()) {
            Set<String> seen = new HashSet<>();
            Deque<String> walk = new ArrayDeque<>(nodes.get(start).dependsOn());
            while (!walk.isEmpty()) {
                String current = walk.poll();
                if (current.equals(start)) {
                    throw new AssertionError("dependency cycle through " + start);
                }
                if (!seen.add(current)) {
                    continue;
                }
                Node node = nodes.get(current);
                if (node != null) {
                    walk.addAll(node.dependsOn());
                }
            }
        }
    }

    @Test
    void visionMappingAndLedgerAgreeBidirectionallyAndVerdictsStayUnwritten()
            throws IOException {
        Map<String, Node> nodes = parseLedger();
        List<String> failures = new ArrayList<>();

        // Forward: every V's package list resolves in the ledger.
        for (Map.Entry<String, Set<String>> vision : VISION_TO_PACKAGES.entrySet()) {
            for (String pkg : vision.getValue()) {
                if (!nodes.containsKey(pkg)) {
                    failures.add(vision.getKey() + " references unknown " + pkg);
                }
            }
        }
        // Bidirectional: ledger vision_ids ⇄ §11 table rows must be the same relation.
        for (Node node : nodes.values()) {
            Set<String> tableSays = new HashSet<>();
            for (Map.Entry<String, Set<String>> vision : VISION_TO_PACKAGES.entrySet()) {
                if (vision.getValue().contains(node.id())) {
                    tableSays.add(vision.getKey());
                }
            }
            if (!node.visionIds().equals(tableSays)) {
                failures.add(node.id() + " vision_ids " + node.visionIds()
                        + " disagree with the blueprint table " + tableSays);
            }
        }
        // Terminal verdicts are owner facts: they may never appear as a status value.
        for (Node node : nodes.values()) {
            if (node.status() != null && TERMINAL_VERDICTS.contains(node.status())) {
                failures.add(node.id() + " carries terminal verdict " + node.status()
                        + " — 终局判定只能由 owner/独立验收以外部签字落位");
            }
        }
        // Every V01..V20 is represented (guards accidental table truncation).
        assertEquals(20, VISION_TO_PACKAGES.size());
        for (int i = 1; i <= 20; i++) {
            assertTrue(VISION_TO_PACKAGES.containsKey(String.format("V%02d", i)));
        }
        assertTrue(failures.isEmpty(), "vision traceability violations: " + failures);
        assertFalse(nodes.isEmpty());
    }

    private static Map<String, Node> parseLedger() throws IOException {
        Map<String, Node> nodes = new LinkedHashMap<>();
        String currentId = null;
        Set<String> dependsOn = new HashSet<>();
        Set<String> visionIds = new HashSet<>();
        String status = null;
        // Only the sub_gates: and work_packages: sections carry aggregation nodes; the
        // ledger's open_questions: section also uses "- id:" items and must stay out.
        boolean inNodes = false;
        for (String line : Files.readString(LEDGER, StandardCharsets.UTF_8).split("\n")) {
            if (line.equals("sub_gates:") || line.equals("work_packages:")) {
                inNodes = true;
                continue;
            }
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                inNodes = false; // any other top-level key (open_questions:, …) ends the scope
            }
            if (!inNodes) {
                continue;
            }
            Matcher idLine = ID_LINE.matcher(line);
            if (idLine.find()) {
                if (currentId != null) {
                    nodes.put(currentId, new Node(currentId, dependsOn, visionIds, status));
                }
                currentId = idLine.group(1);
                dependsOn = new HashSet<>();
                visionIds = new HashSet<>();
                status = null;
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && currentId != null) {
                String key = field.group(1);
                String value = field.group(2).trim();
                switch (key) {
                    case "depends_on" -> dependsOn.addAll(ids(value));
                    case "vision_ids" -> visionIds.addAll(ids(value));
                    case "status" -> status = value;
                    default -> {
                    }
                }
            }
        }
        if (currentId != null) {
            nodes.put(currentId, new Node(currentId, dependsOn, visionIds, status));
        }
        return nodes;
    }

    /** Explicit deps plus the sub-gate⇄parent implication (both directions). */
    private static Set<String> expandImplied(Map<String, Node> nodes, String id) {
        Set<String> out = new HashSet<>(nodes.get(id).dependsOn());
        String parent = id.matches("CP-\\d\\d[A-C]") ? id.substring(0, 5) : null;
        if (parent != null && nodes.containsKey(parent)) {
            out.add(parent);
        }
        for (String sub : SUB_GATES) {
            if (sub.substring(0, 5).equals(id) && nodes.containsKey(sub)) {
                out.add(sub);
            }
        }
        return out;
    }

    /** ["CP-05", "V17"] → {CP-05, V17}. */
    private static Set<String> ids(String bracketList) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(bracketList);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
