package com.innercosmos.operations;

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
 * CP-48 on-call/escalation runbook structure contract over
 * docs/commercialization/operations/*.yml: crisis on-call and general support hours must
 * exist as SEPARATE rotation kinds (危机值班与一般客服时段分开); every escalation ladder
 * strictly increases in time and only references schedulable roles; ticket categories
 * default to minimal metadata visibility (客服默认只看最小账户/订单元数据) with sensitive
 * access requiring an explicit authorized procedure; drill coverage must reference real
 * categories. As with the store checklists, there is NO FILLED/PASS state — staffing and
 * drill completions are operator-only facts recorded as external evidence, never a field
 * a coding agent may flip.
 */
class OnCallRunbookContractTest {

    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "IN_PROGRESS");
    private static final Path DIR = Path.of("docs", "commercialization", "operations");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");
    private static final Pattern LADDER_ROLE = Pattern.compile("^      - role:\\s*(\\S+)\\s*$");
    private static final Pattern LADDER_FIELD = Pattern.compile("^        after_minutes:\\s*(\\d+)\\s*$");

    private record LadderStep(String role, int afterMinutes) {
    }

    private record Item(Map<String, String> fields, List<LadderStep> ladder) {
        String f(String key) {
            return fields.get(key);
        }
    }

    private static final class Runbook {
        final Map<String, List<Item>> sections = new LinkedHashMap<>();

        List<Item> items(String section) {
            return sections.getOrDefault(section, List.of());
        }
    }

    @Test
    void oncallRunbookSatisfiesTheStructureContract() throws IOException {
        List<String> failures = new ArrayList<>();

        Runbook rotation = parse(DIR.resolve("oncall-rotation.yml"), false, "duty_roles", "rotation");
        Runbook paths = parse(DIR.resolve("escalation-paths.yml"), true, "paths");
        Runbook categories = parse(DIR.resolve("ticket-categories.yml"), false, "categories");
        Runbook drills = parse(DIR.resolve("drill-scenarios.yml"), false, "drills");

        // ---- duty roles registry ----
        Set<String> roles = new HashSet<>();
        for (Item role : rotation.items("duty_roles")) {
            if (isBlank(role.f("role_id")) || !roles.add(role.f("role_id"))) {
                failures.add("duty_roles: duplicate or blank role_id");
            }
        }
        for (String expected : List.of("duty_crisis_responder", "crisis_lead", "duty_support",
                "support_lead", "payments_engineer", "privacy_officer", "founder_on_duty")) {
            if (!roles.contains(expected)) failures.add("duty_roles: missing " + expected);
        }

        // ---- rotation: crisis and general support are separate kinds ----
        Set<String> kinds = new HashSet<>();
        for (Item slot : rotation.items("rotation")) {
            String id = slot.f("slot_id");
            requireFields(slot, List.of("slot_id", "kind", "coverage_window", "timezone",
                    "primary_role", "secondary_role", "handover_artifact", "status", "note"), failures);
            kinds.add(slot.f("kind"));
            if (!"CRISIS".equals(slot.f("kind")) && !"GENERAL".equals(slot.f("kind"))) {
                failures.add("rotation/" + id + ": kind must be CRISIS or GENERAL");
            }
            if (!roles.contains(slot.f("primary_role")) || !roles.contains(slot.f("secondary_role"))) {
                failures.add("rotation/" + id + ": roles outside the duty registry");
            }
            statusCheck(slot, "rotation/" + id, failures);
            if ("CRISIS".equals(slot.f("kind")) && isBlank(slot.f("crisis_hotline_ref"))) {
                failures.add("rotation/" + id + ": CRISIS slot needs crisis_hotline_ref");
            }
            if ("GENERAL".equals(slot.f("kind"))) {
                String hours = slot.f("published_hours");
                if (isBlank(hours)) {
                    failures.add("rotation/" + id + ": GENERAL slot needs published_hours");
                } else if (hours.contains("24小时") || hours.contains("24 小时") || hours.contains("24h")) {
                    failures.add("rotation/" + id
                            + ": published hours must not promise 24h human support");
                }
            }
        }
        assertTrue(kinds.contains("CRISIS") && kinds.contains("GENERAL"),
                "crisis on-call and general support hours must exist as separate kinds");

        // ---- escalation paths: bounded ladders, strictly increasing, schedulable roles ----
        Set<String> severities = new HashSet<>();
        for (Item path : paths.items("paths")) {
            String severity = path.f("severity");
            if (isBlank(severity) || !severities.add(severity)) {
                failures.add("paths: duplicate or blank severity " + severity);
            }
            requireFields(path, List.of("severity", "scope", "ack_minutes", "update_minutes",
                    "on_timeout", "privacy_rule"), failures);
            if (intAt(path, "ack_minutes") <= 0 || intAt(path, "update_minutes") <= 0) {
                failures.add("path/" + severity + ": ack/update minutes must be positive");
            }
            List<LadderStep> ladder = path.ladder();
            if (ladder.isEmpty()) failures.add("path/" + severity + ": empty escalation ladder");
            int previous = -1;
            for (LadderStep step : ladder) {
                if (step.afterMinutes() <= previous) {
                    failures.add("path/" + severity + ": ladder after_minutes must strictly increase");
                }
                previous = step.afterMinutes();
                if (!roles.contains(step.role())) {
                    failures.add("path/" + severity + ": ladder role " + step.role()
                            + " not schedulable in the duty registry");
                }
            }
            if (!ladder.isEmpty() && ladder.get(0).afterMinutes() != 0) {
                failures.add("path/" + severity + ": first ladder step must be after_minutes 0");
            }
        }
        for (String expected : List.of("SEV1-safety", "SEV2-payment", "SEV3-rights", "SEV4-general")) {
            if (!severities.contains(expected)) failures.add("paths: missing " + expected);
        }

        // ---- ticket categories: minimal metadata by default, sensitive access authorized ----
        Set<String> categoryIds = new HashSet<>();
        for (Item category : categories.items("categories")) {
            String id = category.f("category_id");
            if (isBlank(id) || !categoryIds.add(id)) {
                failures.add("categories: duplicate or blank category_id " + id);
            }
            requireFields(category, List.of("category_id", "user_visible_name", "priority",
                    "escalation_path", "data_visibility", "sla_feedback_hours",
                    "progress_visibility", "status"), failures);
            if (!severities.contains(category.f("escalation_path"))) {
                failures.add("category/" + id + ": escalation_path is not a defined severity");
            }
            String visibility = category.f("data_visibility");
            if (!"MINIMAL_METADATA".equals(visibility) && !"AUTHORIZED_SENSITIVE".equals(visibility)) {
                failures.add("category/" + id + ": unknown data_visibility " + visibility);
            }
            if ("AUTHORIZED_SENSITIVE".equals(visibility) && isBlank(category.f("authorized_procedure"))) {
                failures.add("category/" + id
                        + ": AUTHORIZED_SENSITIVE requires authorized_procedure (敏感调查按程序授权)");
            }
            statusCheck(category, "category/" + id, failures);
        }
        for (String required : List.of("billing_wrong_charge", "refund_request", "rights_request",
                "deletion_stuck", "ban_appeal", "harassment_report", "safety_crisis",
                "payment_no_unlock", "unsubscribe_misunderstanding")) {
            if (!categoryIds.contains(required)) failures.add("missing blueprint category " + required);
        }

        // ---- drills: coverage references real categories; completion is operator-only ----
        Set<String> coverables = new HashSet<>(categoryIds);
        coverables.add("platform_outage");
        Set<String> covered = new HashSet<>();
        for (Item drill : drills.items("drills")) {
            String id = drill.f("drill_id");
            requireFields(drill, List.of("drill_id", "covers", "cadence", "owner_role", "script",
                    "evidence_required", "status"), failures);
            if (isBlank(drill.f("covers"))) failures.add("drill/" + id + ": covers is blank");
            for (String c : drill.f("covers").split(",")) {
                String trimmed = c.trim();
                if (!coverables.contains(trimmed)) {
                    failures.add("drill/" + id + ": covers unknown category " + trimmed);
                }
                covered.add(trimmed);
            }
            if (!roles.contains(drill.f("owner_role"))) {
                failures.add("drill/" + id + ": owner_role not schedulable in the duty registry");
            }
            statusCheck(drill, "drill/" + id, failures);
            String last = drill.f("last_drilled");
            if (last != null && !isBlank(last) && !"null".equals(last)
                    && !last.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                failures.add("drill/" + id + ": last_drilled must be an operator-backfilled date");
            }
        }
        for (String required : List.of("billing_wrong_charge", "payment_no_unlock",
                "unsubscribe_misunderstanding", "deletion_stuck", "ban_appeal",
                "harassment_report", "safety_crisis", "platform_outage")) {
            if (!covered.contains(required)) failures.add("no drill covers " + required);
        }

        assertTrue(failures.isEmpty(), "runbook contract violations: " + failures);
        assertEquals(5, rotation.items("rotation").size(), "hand-maintained slot count sanity");
    }

    // ---------- tiny YAML subset parser (same discipline as the store checklists) ----------

    private static Runbook parse(Path file, boolean withLadders, String... sections)
            throws IOException {
        Runbook runbook = new Runbook();
        for (String section : sections) runbook.sections.put(section, new ArrayList<>());
        String section = null;
        Item current = null;
        String pendingLadderRole = null;
        for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\n")) {
            Matcher ladderField = LADDER_FIELD.matcher(line);
            if (withLadders && ladderField.find() && current != null && pendingLadderRole != null) {
                current.ladder().add(new LadderStep(pendingLadderRole,
                        Integer.parseInt(ladderField.group(1))));
                pendingLadderRole = null;
                continue;
            }
            Matcher ladderRole = LADDER_ROLE.matcher(line);
            if (withLadders && ladderRole.find()) {
                if (pendingLadderRole != null && current != null) {
                    current.ladder().add(new LadderStep(pendingLadderRole, -1));
                }
                pendingLadderRole = ladderRole.group(1);
                continue;
            }
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null && section != null) runbook.sections.get(section).add(current);
                current = new Item(new LinkedHashMap<>(), withLadders ? new ArrayList<>() : null);
                current.fields().put(item.group(1), item.group(2).trim());
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), field.group(2).trim());
                continue;
            }
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                if (current != null && section != null) runbook.sections.get(section).add(current);
                current = null;
                section = runbook.sections.containsKey(sec.group(1)) ? sec.group(1) : null;
            }
        }
        if (current != null && section != null) runbook.sections.get(section).add(current);
        return runbook;
    }

    private static void requireFields(Item item, List<String> fields, List<String> failures) {
        for (String field : fields) {
            if (isBlank(item.f(field))) failures.add("missing field " + field);
        }
    }

    private static void statusCheck(Item item, String where, List<String> failures) {
        String status = item.f("status");
        if (!ALLOWED_STATUS.contains(status)) {
            failures.add(where + ": status \"" + status + "\" not in " + ALLOWED_STATUS
                    + " — staffing/completion is operator-only evidence, never a field");
        }
    }

    private static int intAt(Item item, String key) {
        try {
            return Integer.parseInt(item.f(key).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
