package com.innercosmos.regulatory;

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
 * CP-51 regulatory/submission ledger structure contract over
 * docs/commercialization/regulatory/*.yml. The honesty discipline is structural: the
 * CP-51A legal-procedures ledger has NO approved state at all — 法定程序终态 (许可生效/
 * 备案完成/评估履行/商店通过) can only be recorded by the operator as external receipts,
 * never by a coding agent flipping a field, and 51A 与 51B 互不晋升. The CP-51B
 * submission ledger separates "提交截图" (SUBMITTED) from "审批通过" (PASSED, which
 * REQUIRES an official receipt), and a REJECTED channel must carry its reason and keep
 * blocking launch.
 */
class RegulatoryLedgerContractTest {

    private static final Path DIR = Path.of("docs", "commercialization", "regulatory");

    private static final Set<String> PROCEDURE_STATUS = Set.of("PENDING", "IN_PROGRESS");
    private static final Set<String> SUBMISSION_STATUS = Set.of("PENDING", "IN_PROGRESS");
    private static final Set<String> REVIEW_OUTCOMES = Set.of("PENDING", "SUBMITTED", "REJECTED", "PASSED");
    /** The 12 §5.1 matrix rows (法律与申报矩阵), by ledger procedure_id. */
    private static final List<String> EXPECTED_PROCEDURES = List.of(
            "telecom-operator", "app-icp-filing", "genai-safety-assessment",
            "registered-model-app-registration", "anthropomorphic-safety-filing",
            "social-info-service-assessment", "personal-info-protection",
            "data-audit-classification", "ai-content-labeling",
            "minor-protection-antifraud", "consumer-auto-renewal", "non-medical-boundary");
    private static final Set<String> EXPECTED_CHANNELS = Set.of(
            "web-pwa", "apple-cn", "xiaomi", "oppo", "huawei", "vivo-others");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void legalProceduresLedgerSatisfiesTheNoApprovalStateContract() throws IOException {
        List<Item> procedures = parse(DIR.resolve("legal-procedures.ledger.yml"), "procedures");
        List<String> failures = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        assertEquals(EXPECTED_PROCEDURES.size(), procedures.size(),
                "the §5.1 matrix must be represented row for row");
        for (Item procedure : procedures) {
            String id = procedure.f("procedure_id");
            if (!ids.add(id)) failures.add("duplicate procedure_id " + id);
            for (String field : List.of("procedure_id", "matter", "applicable_judgment",
                    "required_state", "depends_on", "allowed_scope", "status", "receipt",
                    "signature", "open_remediation")) {
                if (!procedure.fields().containsKey(field)) {
                    failures.add(id + ": missing " + field);
                }
            }
            String status = procedure.f("status");
            if (!PROCEDURE_STATUS.contains(status)) {
                failures.add(id + ": status \"" + status + "\" is not in " + PROCEDURE_STATUS
                        + " — 法定程序终态只能由 operator 以外部回执落位，不存在 agent 可填的批准态");
            }
            // Operator-only fields must still be blank placeholders, never invented.
            for (String operatorField : List.of("receipt", "signature")) {
                if (!isBlank(procedure.f(operatorField))) {
                    failures.add(id + ": " + operatorField + " must stay blank until the"
                            + " operator backfills external evidence");
                }
            }
            if (isBlank(procedure.f("allowed_scope"))) {
                failures.add(id + ": allowed_scope must state what may open BEFORE the"
                        + " procedure completes (51A 单独记录允许的功能范围)");
            }
        }
        for (String expected : EXPECTED_PROCEDURES) {
            if (!ids.contains(expected)) failures.add("missing §5.1 row " + expected);
        }
        assertTrue(failures.isEmpty(), "legal procedures contract violations: " + failures);
    }

    @Test
    void submissionLedgerSeparatesSubmittedFromPassedAndKeepsRejectionsBlocking()
            throws IOException {
        List<Item> submissions = parse(DIR.resolve("submissions.ledger.yml"), "submissions");
        List<String> failures = new ArrayList<>();
        Set<String> channels = new HashSet<>();

        for (Item submission : submissions) {
            String id = submission.f("submission_id");
            for (String field : List.of("submission_id", "channel", "artifact",
                    "blocks_launch", "status", "submitted_at", "receipt", "review_outcome",
                    "rejection_reason")) {
                if (!submission.fields().containsKey(field)) {
                    failures.add(id + ": missing " + field);
                }
            }
            channels.add(submission.f("channel"));
            String outcome = submission.f("review_outcome");
            if (!REVIEW_OUTCOMES.contains(outcome)) {
                failures.add(id + ": unknown review_outcome " + outcome);
            }
            // PASSED requires an official receipt — 提交截图不算完成.
            if ("PASSED".equals(outcome) && isBlank(submission.f("receipt"))) {
                failures.add(id + ": PASSED requires an official receipt (提交截图不算完成)");
            }
            // REJECTED requires a reason and must keep blocking the channel (未通过渠道保持关闭).
            if ("REJECTED".equals(outcome)) {
                if (isBlank(submission.f("rejection_reason"))) {
                    failures.add(id + ": REJECTED requires rejection_reason");
                }
                if (!"true".equals(submission.f("blocks_launch"))) {
                    failures.add(id + ": a rejected channel must keep blocks_launch true");
                }
            }
            String status = submission.f("status");
            if (!SUBMISSION_STATUS.contains(status)) {
                failures.add(id + ": status \"" + status + "\" not in " + SUBMISSION_STATUS);
            }
            // While an outcome is pending, every operator-only fact stays blank.
            if ("PENDING".equals(outcome)) {
                for (String operatorField : List.of("submitted_at", "receipt")) {
                    if (!isBlank(submission.f(operatorField))) {
                        failures.add(id + ": " + operatorField
                                + " must stay blank until the operator files the real submission");
                    }
                }
            }
        }
        assertEquals(EXPECTED_CHANNELS, channels,
                "submission ledger must cover exactly the six store-submission channels");
        assertTrue(failures.isEmpty(), "submission ledger violations: " + failures);
    }

    // ---------- tiny YAML subset parser (same discipline as the store checklists) ----------

    private static List<Item> parse(Path file, String section) throws IOException {
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
                current.fields().put(item.group(1), item.group(2).trim());
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), field.group(2).trim());
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
