package com.innercosmos.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-43/44 contract over docs/commercialization/store-submissions/web-pwa.checklist.yml —
 * the 大陆 Web/PWA submission skeleton. Honesty discipline mirrors the sibling product
 * ledgers (landing-copy.deck.yml, cp60c-accessibility-matrix.yml):
 * - status only ever ∈ {PENDING, IN_PROGRESS, WAIVED} — there is NO PASS state: 审批通过
 *   must land as an official receipt in `evidence`, filled by the operator, never by an
 *   agent or a test;
 * - WAIVED needs a non-blank approved_by (and only WAIVED may carry one);
 * - a non-null evidence value means a receipt is being placed, so the item must have left
 *   PENDING; a PENDING item with a receipt attached would be a silent completion claim;
 * - blocking items (blocks_submission: True) must state what receipt is required.
 * The automatable execution leg (service worker registration, manifest/icon serving) lives
 * in web/e2e/pwa-submission-smoke.spec.ts and never writes anything back into this file.
 */
class WebPwaChecklistContractTest {

    private static final Path CHECKLIST = Path.of(
            "docs", "commercialization", "store-submissions", "web-pwa.checklist.yml");

    /** The submission skeleton the CP-43/44 blueprint lays out; removing one fails loudly. */
    private static final Set<String> REQUIRED_ITEMS = Set.of(
            "domain-icp", "https-cert", "download-parity", "account-flows",
            "payment-permit", "seo-public-only", "pwa-update");

    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "IN_PROGRESS", "WAIVED");
    private static final Set<String> BOOLEAN_FLAGS = Set.of("True", "False");

    /**
     * Fake-completion tokens no field value may ever carry (there is no PASS state).
     * Compound forms only — bare "完成" also appears inside honest requirement wording
     * such as "备案手续完成，备案号展示", which describes the demand, not a status claim.
     */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "PASS", "APPROVED", "已通过", "已完成", "已审批");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void checklistCoversTheSubmissionSkeletonWithHonestStatusesOnly() throws IOException {
        List<Item> items = parse(CHECKLIST, "items");
        List<String> failures = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        assertTrue(items.size() >= REQUIRED_ITEMS.size(),
                "提交骨架至少覆盖 " + REQUIRED_ITEMS + "，实际 " + items.size() + " 项");
        for (Item item : items) {
            String id = item.f("id");
            if (!REQUIRED_ITEMS.contains(id)) {
                failures.add("未知清单项 " + id);
            }
            if (!seen.add(id)) {
                failures.add("清单项重复 " + id);
            }
            if (isBlank(id)) {
                failures.add("存在缺少 id 的清单项");
                continue;
            }
            if (isBlank(item.f("category"))) failures.add(id + ": 缺少 category");
            if (isBlank(item.f("requirement"))) failures.add(id + ": 缺少 requirement");
            if (isBlank(item.f("evidence_required"))) {
                failures.add(id + ": 缺少 evidence_required（每项都要写明需要什么回执）");
            }
            String status = item.f("status");
            if (!ALLOWED_STATUSES.contains(status)) {
                failures.add(id + ": status \"" + status + "\" 不在 " + ALLOWED_STATUSES
                        + "（不存在 PASS 态——审批通过只能以 evidence 中的官方回执为准）");
            }
            String blocks = item.f("blocks_submission");
            if (!BOOLEAN_FLAGS.contains(blocks)) {
                failures.add(id + ": blocks_submission 必须是 True/False，实际 \"" + blocks + "\"");
            }
        }
        for (String required : REQUIRED_ITEMS) {
            if (!seen.contains(required)) {
                failures.add("缺少清单项 " + required);
            }
        }
        assertTrue(failures.isEmpty(), "web-pwa checklist 结构 violations: " + failures);
    }

    @Test
    void waiverNeedsAnApproverAndReceiptsNeverAppearOnPendingItems() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Item item : parse(CHECKLIST, "items")) {
            String where = "items/" + item.f("id");
            String status = item.f("status");
            String approver = item.f("approved_by");

            if ("WAIVED".equals(status) && isBlank(approver)) {
                failures.add(where + ": WAIVED 必须带 approved_by（谁批准豁免）");
            }
            if (!"WAIVED".equals(status) && !isBlank(approver)) {
                failures.add(where + ": approved_by 只有 WAIVED 才允许出现");
            }
            if (!isBlank(item.f("evidence")) && "PENDING".equals(status)) {
                failures.add(where + ": PENDING 项不允许携带 evidence —— 回执落位即离开 PENDING，"
                        + "且只能由 operator 填入");
            }
        }
        assertTrue(failures.isEmpty(), "豁免/回执纪律 violations: " + failures);
    }

    @Test
    void noFieldValueAnywhereClaimsCompletion() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Item item : parse(CHECKLIST, "items")) {
            for (Map.Entry<String, String> field : item.fields().entrySet()) {
                String value = field.getValue();
                if (value == null) {
                    continue;
                }
                for (String token : FORBIDDEN_TOKENS) {
                    if (value.contains(token)) {
                        failures.add("items/" + item.f("id") + "/" + field.getKey()
                                + ": 出现完成态用语 \"" + token + "\" —— 本骨架不存在任何自动完成声明");
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "伪完成态 violations: " + failures);
    }

    // ---------- tiny YAML subset parser (same discipline as the sibling product contracts) ----------

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
                current.fields().put(item.group(1), stripQuotes(item.group(2)));
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), stripQuotes(field.group(2)));
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static String stripQuotes(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
