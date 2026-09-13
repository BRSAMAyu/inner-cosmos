package com.innercosmos.product;

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
 * CP-60C accessibility matrix contract over
 * docs/commercialization/product/cp60c-accessibility-matrix.yml. The matrix is the
 * blueprint §8 sub-gate structure: J01–J12 (§3.2 journeys) × five declared platforms
 * (web/android/ios/windows/harmonyos) × four dimensions (screen_reader/keyboard/
 * contrast/weak_network). Honesty discipline: one status per cell, only
 * {NOT_STARTED, AUTOMATED_PASS, MANUAL_PENDING}; an AUTOMATED_PASS cell must carry a
 * reproducible evidence reference in the sibling *_evidence field; and there is no
 * completion/done/passed roll-up field anywhere — 辅助技术人工验证与自动化检查分开记账，
 * 单端通过不冒充五端完成.
 */
class AccessibilityMatrixContractTest {

    private static final Path LEDGER = Path.of("docs", "commercialization", "product", "cp60c-accessibility-matrix.yml");

    private static final List<String> JOURNEYS = List.of(
            "J01", "J02", "J03", "J04", "J05", "J06", "J07", "J08", "J09", "J10", "J11", "J12");
    private static final Set<String> PLATFORMS = Set.of("web", "android", "ios", "windows", "harmonyos");
    private static final List<String> DIMENSIONS = List.of("screen_reader", "keyboard", "contrast", "weak_network");
    private static final Set<String> STATUSES = Set.of("NOT_STARTED", "AUTOMATED_PASS", "MANUAL_PENDING");
    /** The only structural fields an item may carry — anything else (done/passed/…) is a fake completion claim. */
    private static final Set<String> ALLOWED_KEYS = buildAllowedKeys();

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void matrixCoversTwelveJourneysAcrossAllFivePlatformsAndFourDimensions() throws IOException {
        List<Item> matrix = parse(LEDGER, "matrix");
        List<String> failures = new ArrayList<>();

        assertEquals(JOURNEYS.size() * PLATFORMS.size(), matrix.size(),
                "矩阵必须是 J01–J12 × 五端 = " + (JOURNEYS.size() * PLATFORMS.size()) + " 行");

        Map<String, Map<String, Item>> byJourney = new LinkedHashMap<>();
        for (Item item : matrix) {
            String journeyId = item.f("journey_id");
            String platform = item.f("platform");
            if (!JOURNEYS.contains(journeyId)) failures.add("未知旅程 " + journeyId);
            if (!PLATFORMS.contains(platform)) failures.add(journeyId + ": 未知端 " + platform);
            if (isBlank(item.f("journey"))) failures.add(journeyId + "/" + platform + ": 缺少旅程名称");
            // No completion-style field may ever appear — a cell states a status, nothing more.
            for (String key : item.fields().keySet()) {
                if (!ALLOWED_KEYS.contains(key)) {
                    failures.add(journeyId + "/" + platform + ": 字段 " + key
                            + " 不在允许的结构字段内（不存在完成态汇总字段）");
                }
            }
            for (String dimension : DIMENSIONS) {
                String status = item.f(dimension);
                if (!STATUSES.contains(status)) {
                    failures.add(journeyId + "/" + platform + "/" + dimension + ": status \""
                            + status + "\" 不在 " + STATUSES);
                }
                if (!item.fields().containsKey(dimension + "_evidence")) {
                    failures.add(journeyId + "/" + platform + "/" + dimension + ": 缺少 *_evidence 字段");
                }
            }
            Map<String, Item> platforms = byJourney.computeIfAbsent(journeyId, key -> new LinkedHashMap<>());
            if (platforms.put(platform, item) != null) {
                failures.add(journeyId + ": 平台 " + platform + " 重复出现");
            }
        }

        for (String journeyId : JOURNEYS) {
            Map<String, Item> platforms = byJourney.get(journeyId);
            if (platforms == null) {
                failures.add("缺少旅程 " + journeyId + " 的全部行");
            } else if (!platforms.keySet().equals(PLATFORMS)) {
                failures.add(journeyId + " 必须覆盖五端 " + PLATFORMS + "，实际 " + platforms.keySet());
            }
        }
        assertTrue(failures.isEmpty(), "accessibility matrix violations: " + failures);
    }

    @Test
    void automatedPassRequiresEvidenceAndManualPendingStaysSeparate() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Item item : parse(LEDGER, "matrix")) {
            String where = item.f("journey_id") + "/" + item.f("platform");
            for (String dimension : DIMENSIONS) {
                if ("AUTOMATED_PASS".equals(item.f(dimension)) && isBlank(item.f(dimension + "_evidence"))) {
                    failures.add(where + "/" + dimension
                            + ": AUTOMATED_PASS 必须带可复现实证（*_evidence 指向 CI 作业或报告）");
                }
                // Evidence references may never appear without the claim they support.
                if (!isBlank(item.f(dimension + "_evidence")) && isBlank(item.f(dimension))) {
                    failures.add(where + "/" + dimension + ": evidence 字段悬空");
                }
            }
        }
        assertTrue(failures.isEmpty(), "evidence discipline violations: " + failures);
    }

    @Test
    void currentFrozenStateIsHonestlyNotStartedEverywhere() throws IOException {
        // 冻结起点（2026-09）：CP-60C 尚未开始任何一格。随着真实自动化/人工证据落位，
        // 单元格会逐格更新——但每次更新都必须带证据，且由本契约逐格把关。
        List<String> notStarted = new ArrayList<>();
        for (Item item : parse(LEDGER, "matrix")) {
            for (String dimension : DIMENSIONS) {
                if (!"NOT_STARTED".equals(item.f(dimension))) {
                    notStarted.add(item.f("journey_id") + "/" + item.f("platform") + "/" + dimension
                            + "=" + item.f(dimension));
                }
            }
        }
        assertTrue(notStarted.isEmpty(),
                "当前矩阵应为全 NOT_STARTED（CP-60C 尚未启动）；已宣称的状态: " + notStarted);
    }

    private static Set<String> buildAllowedKeys() {
        Set<String> keys = new HashSet<>(List.of("journey_id", "journey", "platform"));
        for (String dimension : DIMENSIONS) {
            keys.add(dimension);
            keys.add(dimension + "_evidence");
        }
        return keys;
    }

    // ---------- tiny YAML subset parser (same discipline as the regulatory ledgers) ----------

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
