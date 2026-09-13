package com.innercosmos.launch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-52 candidate manifest + per-channel Go/No-Go structure contract. The load-bearing
 * rule is CROSS-FILE: a channel may only be GO when its CP-51B submission row says
 * review_outcome=PASSED and blocks_launch has been lifted — the launch decision can
 * never outrun the submission ledger (未通过渠道保持关闭). Within the candidate
 * template, every "通过/签字/可用" fact (journey verdicts, five-party signoffs, launch
 * switches, P0 count) is an operator-only fact backed by rehearsal evidence; the
 * template ships entirely blank/pending, and a GO decision additionally requires a
 * decided_by/decided_at owner.
 */
class LaunchGoNoGoContractTest {

    private static final Path LAUNCH_DIR = Path.of("docs", "commercialization", "launch");
    private static final Path SUBMISSIONS =
            Path.of("docs", "commercialization", "regulatory", "submissions.ledger.yml");

    private static final List<String> JOURNEYS = List.of(
            "J01", "J02", "J03", "J04", "J05", "J06",
            "J07", "J08", "J09", "J10", "J11", "J12");
    private static final List<String> SIGNOFFS = List.of(
            "engineering", "legal", "experience", "operations", "payments_and_recovery");
    private static final List<String> SWITCHES = List.of(
            "announcement", "status_page", "support_ready", "app_update_channel",
            "rollback_plan", "registration_pause_switch");

    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(\\S.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");
    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern TWO_SPACE_FIELD = Pattern.compile("^  (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void goNoGoNeverOutrunsTheSubmissionLedger() throws IOException {
        List<Item> decisions = parseItems(LAUNCH_DIR.resolve("go-no-go.yml"), "channels");
        List<Item> submissions = parseItems(SUBMISSIONS, "submissions");
        List<String> failures = new ArrayList<>();

        Map<String, Item> submissionByChannel = new LinkedHashMap<>();
        for (Item submission : submissions) {
            submissionByChannel.put(submission.f("channel"), submission);
        }
        assertEquals(submissionByChannel.keySet().stream().collect(java.util.stream.Collectors.toSet()),
                decisions.stream().map(d -> d.f("channel")).collect(java.util.stream.Collectors.toSet()),
                "Go/No-Go must cover exactly the submission-ledger channels");

        for (Item decision : decisions) {
            String channel = decision.f("channel");
            if (!"GO".equals(decision.f("decision")) && !"NO_GO".equals(decision.f("decision"))) {
                failures.add(channel + ": decision must be GO or NO_GO");
            }
            Item submission = submissionByChannel.get(channel);
            if (submission == null) {
                failures.add(channel + ": no submission-ledger row");
                continue;
            }
            // CROSS-FILE rule: an unPASSED submission (or a still-blocking one) pins NO_GO.
            boolean passed = "PASSED".equals(submission.f("review_outcome"))
                    && !"true".equals(submission.f("blocks_launch"));
            if (!passed && "GO".equals(decision.f("decision"))) {
                failures.add(channel + ": GO while CP-51B review_outcome="
                        + submission.f("review_outcome") + " blocks_launch="
                        + submission.f("blocks_launch") + " — 未通过渠道保持关闭");
            }
            // A GO decision needs a named owner and a date; NO_GO while undecided stays anonymous.
            if ("GO".equals(decision.f("decision"))
                    && (isBlank(decision.f("decided_by")) || isBlank(decision.f("decided_at")))) {
                failures.add(channel + ": GO requires decided_by and decided_at");
            }
            if ("NO_GO".equals(decision.f("decision")) && isBlank(decision.f("rationale"))) {
                failures.add(channel + ": NO_GO requires a rationale");
            }
        }
        assertTrue(failures.isEmpty(), "go/no-go violations: " + failures);
    }

    @Test
    void candidateTemplateShipsEntirelyOperatorBlank() throws IOException {
        List<String> lines = Files.readAllLines(
                LAUNCH_DIR.resolve("candidate-manifest.template.yml"), StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();

        // Section walker: five_party_signoffs fields (2-space), journeys items,
        // launch_switches inline maps (2-space).
        Set<String> signoffs = new HashSet<>();
        for (String line : lines) {
            Matcher twoSpace = TWO_SPACE_FIELD.matcher(line);
            if (twoSpace.find()) {
                String key = twoSpace.group(1);
                String value = strip(twoSpace.group(2));
                if (SIGNOFFS.contains(key)) {
                    signoffs.add(key);
                    if (!isBlank(value)) {
                        failures.add("signoff " + key + " must ship blank — 签字是真人事实");
                    }
                }
                if (SWITCHES.contains(key)) {
                    if (!value.contains("available: \"false\"") || !value.contains("evidence: null")) {
                        failures.add("switch " + key + " must ship unavailable without evidence");
                    }
                }
            }
        }
        assertEquals(new HashSet<>(SIGNOFFS), signoffs, "five-party signoffs must all be present");
        int switchCount = (int) lines.stream()
                .map(TWO_SPACE_FIELD::matcher)
                .filter(Matcher::find)
                .filter(m -> SWITCHES.contains(m.group(1)))
                .count();
        assertEquals(SWITCHES.size(), switchCount, "six launch switches must all be present");

        // Journeys: all twelve, all PENDING, no evidence (the template is pre-rehearsal).
        List<Item> journeys = parseItems(
                LAUNCH_DIR.resolve("candidate-manifest.template.yml"), "journeys");
        List<String> ids = journeys.stream().map(j -> j.f("id")).toList();
        assertEquals(JOURNEYS, ids, "the twelve §3.2 journeys in order");
        for (Item journey : journeys) {
            if (!"PENDING".equals(journey.f("verdict"))) {
                failures.add(journey.f("id") + ": template journeys ship PENDING");
            }
            if (!isBlank(journey.f("evidence"))) {
                failures.add(journey.f("id") + ": no evidence until a real rehearsal");
            }
            if (isBlank(journey.f("name"))) {
                failures.add(journey.f("id") + ": journey name required");
            }
        }

        // The P0 count ships blank — never a defaulted "0".
        boolean p0Blank = lines.stream().anyMatch(l ->
                l.startsWith("open_p0_defects:") && l.contains("null"));
        assertTrue(p0Blank, "open_p0_defects ships null — zero-P0 is a tracker fact, not a default");

        assertTrue(failures.isEmpty(), "candidate template violations: " + failures);
    }

    // ---------- shared tiny parser ----------

    private static List<Item> parseItems(Path file, String section) throws IOException {
        List<Item> items = new ArrayList<>();
        boolean inSection = false;
        Item current = null;
        for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\n")) {
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                if (current != null) items.add(current);
                current = null;
                inSection = section.equals(sec.group(1));
                continue;
            }
            if (!inSection) continue;
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null) items.add(current);
                current = new Item(new LinkedHashMap<>());
                current.fields().put(item.group(1), strip(item.group(2)));
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), strip(field.group(2)));
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static String strip(String value) {
        String v = value.trim();
        int comment = v.indexOf(" #");
        if (comment >= 0) {
            v = v.substring(0, comment).trim();
        }
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value) || "{}".equals(value);
    }
}
