package com.innercosmos.investors;

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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-56 investor/mentor data-room structure contract over
 * docs/commercialization/investors/data-room.yml (blueprint §6.4 + CP-56 work package).
 *
 * The honesty discipline is structural:
 * - nine §6.4 layers, each non-empty;
 * - status ∈ {PENDING, IN_PROGRESS} — there is NO DONE/READY/COMPLETED and certainly no
 *   "融资成功" state; 材料完成 ≠ 融资成功;
 * - evidence must stay null until status advances beyond those two states, i.e. evidence
 *   pointers are backfilled by the operator from real external material, never invented
 *   by a coding agent;
 * - every external number (TAM / cohort retention / unit economics …) carries a verdict:
 *   VERIFIED requires definition+date+denominator+source all non-blank, while
 *   PENDING_VALIDATION must not carry a precise value field;
 * - no layer may reference P0 private conversation content — investors have no P0 read
 *   access, only authorized de-identified material or explicitly synthetic demo accounts;
 * - the 下一轮里程碑/融资用途 layer must exist (team-funding-milestones).
 */
class DataRoomContractTest {

    private static final Path FILE = Path.of("docs", "commercialization", "investors", "data-room.yml");

    /** The nine §6.4 data-room layers. */
    private static final List<String> EXPECTED_LAYERS = List.of(
            "corporate-equity-ip", "user-research", "product-competitive-evidence",
            "cohort-retention", "orders-refunds", "unit-economics-cash-plan",
            "compliance-vendor-contracts", "architecture-security-incidents",
            "team-funding-milestones");
    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "IN_PROGRESS");
    private static final Set<String> ALLOWED_VERDICTS = Set.of("VERIFIED", "PENDING_VALIDATION");
    /** Layers that must carry at least one number (verdict) item: TAM / 队列留存 / 单位经济. */
    private static final List<String> LAYERS_REQUIRING_NUMERIC_ITEMS = List.of(
            "product-competitive-evidence", "cohort-retention", "unit-economics-cash-plan");
    /** Title fragments that would mean a layer references P0 private conversation content. */
    private static final List<String> BANNED_TITLE_FRAGMENTS = List.of(
            "对话原文", "倾诉内容", "原始对话", "原始倾诉");
    private static final List<String> REQUIRED_FIELDS = List.of(
            "item_id", "layer", "title", "status", "evidence", "source_or_pending");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void allNineLayersArePresentAndNonEmpty() throws IOException {
        List<Item> items = parseItems();
        List<String> failures = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<String, List<Item>> byLayer = new LinkedHashMap<>();

        assertTrue(!items.isEmpty(), "data room must contain items");
        for (Item item : items) {
            String id = item.f("item_id");
            if (isBlank(id)) {
                failures.add("item without item_id");
                continue;
            }
            if (!ids.add(id)) failures.add("duplicate item_id " + id);
            for (String field : REQUIRED_FIELDS) {
                if (!item.fields().containsKey(field)) {
                    failures.add(id + ": missing " + field);
                }
            }
            String layer = item.f("layer");
            if (!EXPECTED_LAYERS.contains(layer)) {
                failures.add(id + ": unknown layer \"" + layer + "\"");
            } else {
                byLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(item);
            }
            if (isBlank(item.f("title"))) failures.add(id + ": blank title");
            if (isBlank(item.f("source_or_pending"))) {
                failures.add(id + ": source_or_pending must declare the future source or the"
                        + " pending reason (待验证也要写清楚来源计划)");
            }
        }
        for (String layer : EXPECTED_LAYERS) {
            List<Item> layerItems = byLayer.get(layer);
            if (layerItems == null || layerItems.isEmpty()) {
                failures.add("layer " + layer + " has no items — §6.4 九层每层必须非空");
            }
        }
        assertEquals(Set.copyOf(EXPECTED_LAYERS), byLayer.keySet(),
                "the nine §6.4 layers must be present exactly");
        assertTrue(failures.isEmpty(), "data room layer contract violations: " + failures);
    }

    @Test
    void noCompletionStateAndEvidenceOnlyBackfilledWithAdvancement() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Item item : parseItems()) {
            String id = item.f("item_id");
            String status = item.f("status");
            if (!ALLOWED_STATUS.contains(status)) {
                failures.add(id + ": status \"" + status + "\" is not in " + ALLOWED_STATUS
                        + " — 材料完成不是终态，更没有融资成功态");
            }
            // evidence 在 status 推进前必须为 null：证据指针只能由 operator 以真实材料回填.
            if (!isBlank(item.f("evidence"))) {
                failures.add(id + ": evidence must stay null until status advances past "
                        + ALLOWED_STATUS + " (operator-only backfill from real material)");
            }
        }
        assertTrue(failures.isEmpty(), "status/evidence contract violations: " + failures);
    }

    @Test
    void numericItemsFollowDefinitionDateDenominatorSourceContract() throws IOException {
        List<Item> items = parseItems();
        List<String> failures = new ArrayList<>();
        Map<String, Integer> numericPerLayer = new LinkedHashMap<>();

        for (Item item : items) {
            String id = item.f("item_id");
            if (!item.fields().containsKey("verdict")) continue;
            String verdict = item.f("verdict");
            String layer = item.f("layer");
            numericPerLayer.merge(layer, 1, Integer::sum);
            if (!ALLOWED_VERDICTS.contains(verdict)) {
                failures.add(id + ": unknown verdict " + verdict);
                continue;
            }
            // A number item always carries the four-provenance fields, even while blank.
            for (String field : List.of("definition", "date", "denominator", "source_or_pending")) {
                if (!item.fields().containsKey(field)) {
                    failures.add(id + ": numeric item missing " + field);
                }
            }
            // VERIFIED → 定义/日期/分母/来源 four-way provenance must all be present.
            if ("VERIFIED".equals(verdict)) {
                for (String field : List.of("definition", "date", "denominator", "source_or_pending")) {
                    if (isBlank(item.f(field))) {
                        failures.add(id + ": VERIFIED requires non-blank " + field
                                + " (每个对外数字必须有定义/日期/分母/来源)");
                    }
                }
            }
            // PENDING_VALIDATION → no precise value: 市场输入未取得就标待验证而非填精确数值.
            if ("PENDING_VALIDATION".equals(verdict)) {
                if (item.fields().containsKey("value") && !isBlank(item.f("value"))) {
                    failures.add(id + ": PENDING_VALIDATION must not carry a value"
                            + " (待验证数字不得给出精确数值字段)");
                }
            }
        }
        for (String layer : LAYERS_REQUIRING_NUMERIC_ITEMS) {
            if (numericPerLayer.getOrDefault(layer, 0) == 0) {
                failures.add(layer + " must contain at least one numeric (verdict) item");
            }
        }
        assertTrue(!numericPerLayer.isEmpty(),
                "data room must declare numeric items (TAM/队列留存/单位经济)");
        assertTrue(failures.isEmpty(), "numeric item contract violations: " + failures);
    }

    @Test
    void noLayerMayReferenceP0PrivateConversationContent() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Item item : parseItems()) {
            String title = item.f("title");
            for (String banned : BANNED_TITLE_FRAGMENTS) {
                if (title != null && title.contains(banned)) {
                    failures.add(item.f("item_id") + ": title references \"" + banned
                            + "\" — 投资人对 P0 无读取权，任何层不得引用私密内容");
                }
            }
        }
        assertTrue(failures.isEmpty(), "P0 exclusivity violations: " + failures);
    }

    @Test
    void fundingLayerCoversNextRoundMilestonesAndUseOfFunds() throws IOException {
        List<Item> fundingItems = parseItems().stream()
                .filter(item -> "team-funding-milestones".equals(item.f("layer")))
                .collect(Collectors.toList());
        assertTrue(!fundingItems.isEmpty(),
                "the 下一轮里程碑/融资用途 layer (team-funding-milestones) must exist and be non-empty");
        assertTrue(fundingItems.stream().anyMatch(i -> contains(i.f("title"), "下一轮里程碑")),
                "funding layer must include a 下一轮里程碑 item");
        assertTrue(fundingItems.stream().anyMatch(i -> contains(i.f("title"), "融资用途")),
                "funding layer must include a 融资用途 item");
    }

    private static boolean contains(String value, String fragment) {
        return value != null && value.contains(fragment);
    }

    // ---------- tiny YAML subset parser (same discipline as the regulatory ledger test) ----------
    // 4-space fields, 2-space list items, surrounding quotes stripped, null treated as blank.

    private static List<Item> parseItems() throws IOException {
        List<Item> items = new ArrayList<>();
        boolean inSection = false;
        Item current = null;
        for (String line : Files.readString(FILE, StandardCharsets.UTF_8).split("\n")) {
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                if (current != null) items.add(current);
                current = null;
                inSection = "items".equals(sec.group(1));
                continue;
            }
            if (!inSection) continue;
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null) items.add(current);
                current = new Item(new LinkedHashMap<>());
                current.fields().put(item.group(1), clean(item.group(2)));
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), clean(field.group(2)));
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static String clean(String raw) {
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
