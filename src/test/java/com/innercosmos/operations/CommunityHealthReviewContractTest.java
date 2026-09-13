package com.innercosmos.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-59 community-health review ledger contract: the review couples the relation-quality
 * snapshot (回轮深度 share + harassment rate, the two numbers read TOGETHER) with the
 * moderation SLA and appeal review — and none of it is agent-writable: values fill only
 * from real metric runs and moderation stats, statuses never express a "healthy" verdict.
 */
class CommunityHealthReviewContractTest {

    private static final Path FILE =
            Path.of("docs", "commercialization", "operations", "community-health-review.yml");
    private static final java.util.Set<String> ALLOWED_STATUS =
            java.util.Set.of("PENDING", "IN_PROGRESS");

    private static final Pattern FIELD = Pattern.compile("^\\s*(?:- )?(\\w+):\\s*(.*)$");

    @Test
    void communityHealthReviewShipsBlankAndHonest() throws IOException {
        List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        Map<String, String> flat = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            Matcher field = FIELD.matcher(line);
            if (field.find()) {
                String value = field.group(2).trim();
                int comment = value.indexOf(" #");
                if (comment >= 0) {
                    value = value.substring(0, comment).trim();
                }
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                flat.put(field.group(1), value);
            }
        }
        // Required structure present.
        for (String key : List.of("review_id", "period", "owner", "relation_quality",
                "harassment_rate", "moderation_sla", "appeals_reviewed", "incidents_open",
                "next_action", "status")) {
            if (!flat.containsKey(key)) {
                failures.add("missing " + key);
            }
        }
        // The two relation numbers must reference the real metric names.
        assertTrue(flat.getOrDefault("metric", "").contains("threeRoundTripShare")
                        || lines.stream().anyMatch(l -> l.contains("threeRoundTripShare")),
                "the review reads the round-trip-depth share");
        assertTrue(lines.stream().anyMatch(l -> l.contains("harassmentPerActiveThread")),
                "and the harassment rate beside it — the pair is the point");
        // Operator-only facts ship blank.
        for (String operatorField : List.of("latest_value", "snapshot", "appeals_reviewed",
                "incidents_open", "ci95", "anchor_week")) {
            String value = flat.get(operatorField);
            if (value != null && !value.isBlank() && !"null".equals(value)) {
                failures.add(operatorField + " must ship blank until real data lands");
            }
        }
        if (!ALLOWED_STATUS.contains(flat.get("status"))) {
            failures.add("status \"" + flat.get("status") + "\" not in " + ALLOWED_STATUS
                    + " — no agent-writable healthy verdict exists");
        }
        assertTrue(failures.isEmpty(), "community health review violations: " + failures);
    }
}
