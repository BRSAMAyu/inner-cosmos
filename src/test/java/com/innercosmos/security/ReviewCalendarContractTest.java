package com.innercosmos.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
 * CP-63 review-calendar + evidence-validity-engine contract over
 * docs/commercialization/security/review-calendar.ledger.yml. The honesty discipline is
 * structural, mirroring the CP-51A legal-procedures ledger: the calendar has NO
 * agent-writable pass state at all — status ∈ {PENDING, IN_PROGRESS, REMEDIATION_REQUIRED}
 * and 独立核验事实 (last_verified_at / receipt / evidence_path) can only be backfilled by
 * the operator against external receipts. The validity engine implements 蓝图 CP-63
 * "超过法定或内部有效期不能自动沿用旧 PASS": an entry whose last_verified_at is set and
 * whose evaluation date is past revalidate_due_at evaluates to EXPIRED, and EXPIRED
 * evidence blocks any 复核通过 claim (the ledger structurally contains no pass-like state
 * an expired row could silently degrade back into). 63A 与 63B 互不覆盖: 63A scopes stay on
 * the S3 首发基线, 63B scopes must explicitly cover the CP-57/58/59/60 新增暴露面 and never
 * reuse a 63A scope description. The engine takes the asOf date as a parameter — neither
 * the engine nor this test reads the system clock.
 */
class ReviewCalendarContractTest {

    private static final Path LEDGER = Path.of("docs", "commercialization", "security",
            "review-calendar.ledger.yml");

    /** 蓝图 CP-63 复核日历的四类条目。 */
    private static final Set<String> CALENDAR_KINDS = Set.of(
            "QUARTERLY_INTERNAL_AUDIT", "MODEL_CHANGE_CHECK",
            "REGULATORY_ANNUAL", "INCIDENT_TRIGGERED");
    /** 日历驱动条目必须登记有效期；事件驱动条目按触发复核、不设日历期限。 */
    private static final Set<String> CALENDAR_DRIVEN = Set.of(
            "QUARTERLY_INTERNAL_AUDIT", "REGULATORY_ANNUAL");
    private static final Set<String> HONEST_STATUS = Set.of(
            "PENDING", "IN_PROGRESS", "REMEDIATION_REQUIRED");
    /** 任何 PASS 形态都不是本台账的可写状态。 */
    private static final Set<String> PASS_LIKE = Set.of(
            "VERIFIED", "PASSED", "APPROVED", "PASS");
    /** CP-63B 必须显式覆盖的 S5 新增暴露面（蓝图子门 L328：不得用 63A 覆盖新增暴露面）。 */
    private static final Set<String> S5_EXPOSURE_CPS = Set.of("CP-57", "CP-58", "CP-59", "CP-60");
    private static final Pattern S5_CP_TOKEN = Pattern.compile("CP-(57|58|59|60)");
    private static final Pattern ANY_CP_TOKEN = Pattern.compile("CP-\\d{2}");
    /** 字段值位置上的任何 PASS 形态（`key: VERIFIED` 等）都不允许出现在文件里。 */
    private static final Pattern PASS_LIKE_VALUE = Pattern.compile(":\\s*\"?(VERIFIED|PASSED|APPROVED|PASS)\"?\\s*$");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    // ---------- 证据有效期引擎（可注入 asOf，不读系统时钟） ----------

    /**
     * CP-63 evidence validity engine. Decision table:
     * <pre>
     * last_verified_at   revalidate_due_at   asOf vs due        → result
     * null (未核验)      any                 any                → entry status（无可沿用的旧 PASS）
     * non-null           null                any                → entry status（未登记有效期，不判过期）
     * non-null           non-null            asOf ≤ due         → entry status（核验仍在有效期内）
     * non-null           non-null            asOf &gt; due        → EXPIRED（不能自动沿用旧 PASS）
     * </pre>
     * EXPIRED 意味着该条目阻断对应 CP 的复核通过宣称，须重新核验或整改复验。
     */
    static String effectiveStatus(Map<String, String> entry, LocalDate asOf) {
        String lastVerified = raw(entry.get("last_verified_at"));
        String due = raw(entry.get("revalidate_due_at"));
        if (lastVerified != null && due != null && asOf.isAfter(LocalDate.parse(due))) {
            return "EXPIRED";
        }
        return entry.get("status");
    }

    @Test
    void reviewCalendarSatisfiesTheStructureContract() throws IOException {
        List<Item> reviews = parse(LEDGER, "reviews");
        List<String> failures = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<String, Integer> kindCounts = new LinkedHashMap<>();

        assertTrue(reviews.size() >= 8, "四类条目各至少两行");
        for (Item review : reviews) {
            String id = review.f("review_id");
            if (!ids.add(id)) failures.add("duplicate review_id " + id);
            for (String field : List.of("review_id", "kind", "scope", "owner",
                    "effective_from", "last_verified_at", "revalidate_due_at", "status",
                    "receipt", "evidence_path")) {
                if (!review.fields().containsKey(field)) {
                    failures.add(id + ": missing " + field);
                }
            }
            String kind = review.f("kind");
            if (!CALENDAR_KINDS.contains(kind)) {
                failures.add(id + ": unknown kind " + kind + " not in " + CALENDAR_KINDS);
            } else {
                kindCounts.merge(kind, 1, Integer::sum);
            }
            String status = review.f("status");
            if (!HONEST_STATUS.contains(status)) {
                failures.add(id + ": status \"" + status + "\" is not in " + HONEST_STATUS
                        + " — 独立核验结论只能由 operator 以外部回执落位");
            }
            if (isBlank(review.f("scope")) || isBlank(review.f("owner"))) {
                failures.add(id + ": scope 与 owner 必须写明适用面与责任人");
            }
            if (!ANY_CP_TOKEN.matcher(review.f("scope") == null ? "" : review.f("scope")).find()) {
                failures.add(id + ": scope must name the applicable CP(s)");
            }
            // 生效日必填且为 ISO 日期；期限/核验日可空，但非空时必须可解析。
            failures.addAll(dateChecks(id, "effective_from", review.f("effective_from"), true));
            failures.addAll(dateChecks(id, "revalidate_due_at", review.f("revalidate_due_at"), false));
            failures.addAll(dateChecks(id, "last_verified_at", review.f("last_verified_at"), false));
            // 日历驱动条目必须带有效期；事件驱动条目不设日历期限。
            boolean calendarDriven = CALENDAR_DRIVEN.contains(kind);
            boolean hasDue = !isBlank(review.f("revalidate_due_at"));
            if (calendarDriven && !hasDue) {
                failures.add(id + ": " + kind + " 必须登记 revalidate_due_at（法定或内部有效期）");
            }
            if (!calendarDriven && hasDue) {
                failures.add(id + ": " + kind + " 按触发复核，revalidate_due_at 应为 null");
            }
            // 核验事实只能与外部回执一起回填；核验日不得晚于自身的重验期限。
            if (!isBlank(review.f("last_verified_at"))) {
                if (isBlank(review.f("receipt")) || isBlank(review.f("evidence_path"))) {
                    failures.add(id + ": last_verified_at 非 null 时必须同时回填 receipt 与 evidence_path");
                }
            }
            String lastVerified = raw(review.f("last_verified_at"));
            String due = raw(review.f("revalidate_due_at"));
            if (lastVerified != null && due != null
                    && LocalDate.parse(lastVerified).isAfter(LocalDate.parse(due))) {
                failures.add(id + ": last_verified_at 不得晚于 revalidate_due_at");
            }
        }
        for (String kind : CALENDAR_KINDS) {
            if (kindCounts.getOrDefault(kind, 0) < 2) {
                failures.add("kind " + kind + " needs at least two entries, has "
                        + kindCounts.getOrDefault(kind, 0));
            }
        }
        assertTrue(failures.isEmpty(), "review calendar contract violations: " + failures);
    }

    @Test
    void cp63BScopeExplicitlyCoversS5ExposureWithoutReusing63AScopes() throws IOException {
        List<Item> reviews = parse(LEDGER, "reviews");
        List<String> failures = new ArrayList<>();
        Set<String> scopes = new HashSet<>();
        Set<String> coveredBy63B = new HashSet<>();

        for (Item review : reviews) {
            String id = review.f("review_id");
            String scope = review.f("scope");
            if (!scopes.add(scope)) {
                failures.add(id + ": scope description shared with another entry"
                        + "（63A/63B 不得共用 scope 描述）");
            }
            boolean is63A = scope.startsWith("CP-63A");
            boolean is63B = scope.startsWith("CP-63B");
            if (is63A == is63B) {
                failures.add(id + ": scope must explicitly belong to CP-63A or CP-63B");
                continue;
            }
            Matcher s5 = S5_CP_TOKEN.matcher(scope);
            if (is63A && s5.find()) {
                failures.add(id + ": 63A 条目不等待 S5 新增功能，scope 不得引用 CP-57/58/59/60"
                        + " 暴露面（发现 " + s5.group() + "）");
            }
            if (is63B) {
                Matcher again = S5_CP_TOKEN.matcher(scope);
                if (!again.find()) {
                    failures.add(id + ": 63B scope 必须显式覆盖 CP-57/58/59/60 新增暴露面");
                } else {
                    do {
                        coveredBy63B.add("CP-" + again.group(1));
                    } while (again.find());
                }
            }
        }
        assertEquals(S5_EXPOSURE_CPS, coveredBy63B,
                "CP-63B 条目必须集体覆盖 CP-57/58/59/60 全部新增暴露面，不得用 63A 覆盖");
        assertTrue(failures.isEmpty(), "63A/63B scope separation violations: " + failures);
    }

    @Test
    void evidenceExpiryEngineTableNeverCarriesAnOldPastDuePass() {
        LocalDate asOf = LocalDate.of(2026, 9, 13);
        // 未核验：即使期限已过也没有可沿用的旧 PASS，返回条目自身状态。
        assertEquals("IN_PROGRESS", effectiveStatus(
                entry("IN_PROGRESS", null, "2026-06-30"), asOf),
                "未核验条目不存在旧 PASS，不得因期限已过而标 EXPIRED");
        assertEquals("PENDING", effectiveStatus(
                entry("PENDING", null, null), asOf));
        // 核验未到期：核验事实仍在有效期内，沿用条目状态。
        assertEquals("IN_PROGRESS", effectiveStatus(
                entry("IN_PROGRESS", "2026-01-15", "2026-12-31"), asOf),
                "核验未到期沿用当前状态");
        assertEquals("REMEDIATION_REQUIRED", effectiveStatus(
                entry("REMEDIATION_REQUIRED", "2025-08-01", "2027-08-01"), asOf));
        // 已过期：核验过但评估日晚于期限 → EXPIRED，不能自动沿用旧 PASS。
        assertEquals("EXPIRED", effectiveStatus(
                entry("IN_PROGRESS", "2026-01-15", "2026-06-30"), asOf),
                "超过法定或内部有效期不能自动沿用旧 PASS");
        assertEquals("EXPIRED", effectiveStatus(
                entry("REMEDIATION_REQUIRED", "2025-12-31", "2026-08-31"), asOf));
        // 边界：评估日等于期限当日仍有效，次日起 EXPIRED。
        LocalDate due = LocalDate.of(2026, 9, 13);
        Map<String, String> boundary = entry("IN_PROGRESS", "2026-03-01", "2026-09-13");
        assertEquals("IN_PROGRESS", effectiveStatus(boundary, due), "期限当日仍有效");
        assertEquals("EXPIRED", effectiveStatus(boundary, due.plusDays(1)), "期限次日起过期");
        // 核验过但未登记有效期：引擎不臆造期限，返回条目状态。
        assertEquals("IN_PROGRESS", effectiveStatus(
                entry("IN_PROGRESS", "2026-01-15", null), asOf));
    }

    @Test
    void expiredEvidenceBlocksVerifiedClaimsAcrossTheLedger() throws IOException {
        List<Item> reviews = parse(LEDGER, "reviews");
        List<String> failures = new ArrayList<>();
        LocalDate asOf = LocalDate.of(2026, 9, 13);
        LocalDate farFuture = LocalDate.of(2099, 12, 31);

        for (Item review : reviews) {
            String id = review.f("review_id");
            String status = review.f("status");
            if (PASS_LIKE.contains(status)) {
                failures.add(id + ": status " + status + " 不是可写状态");
            }
            // 极远期评估：引擎输出只能是自身状态或 EXPIRED，绝不会回到任何 PASS 形态——
            // 即 EXPIRED 条目阻断对应 CP 的复核通过宣称，结构上没有可沿用的旧 PASS。
            String farFutureEffective = effectiveStatus(review.fields(), farFuture);
            if (!farFutureEffective.equals(status) && !"EXPIRED".equals(farFutureEffective)) {
                failures.add(id + ": 远期评估输出 " + farFutureEffective + " 越界");
            }
            if (PASS_LIKE.contains(farFutureEffective)) {
                failures.add(id + ": 过期证据只会降级为 EXPIRED，不会回到 " + farFutureEffective);
            }
            // 当前台账没有任何独立核验事实，故固定评估日下不产生 EXPIRED。
            String todayEffective = effectiveStatus(review.fields(), asOf);
            if (!todayEffective.equals(status)) {
                failures.add(id + ": 无核验事实的条目在评估日 " + asOf + " 不应变为 "
                        + todayEffective);
            }
        }
        // 原文扫描：任何字段值位置都不允许出现 VERIFIED/PASSED/APPROVED/PASS 字样状态。
        for (String line : Files.readAllLines(LEDGER, StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("#")) continue;
            Matcher passLike = PASS_LIKE_VALUE.matcher(line);
            if (passLike.find()) {
                failures.add("文件存在 PASS 形态状态值: " + line.trim());
            }
        }
        assertTrue(failures.isEmpty(), "expiry blocking contract violations: " + failures);
    }

    // ---------- helpers ----------

    private static Map<String, String> entry(String status, String lastVerifiedAt,
            String revalidateDueAt) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("status", status);
        entry.put("last_verified_at", lastVerifiedAt);
        entry.put("revalidate_due_at", revalidateDueAt);
        return entry;
    }

    private static List<String> dateChecks(String id, String field, String value,
            boolean required) {
        List<String> failures = new ArrayList<>();
        if (isBlank(value)) {
            if (required) failures.add(id + ": " + field + " 必填");
            return failures;
        }
        try {
            LocalDate.parse(raw(value));
        } catch (RuntimeException e) {
            failures.add(id + ": " + field + " \"" + value + "\" 不是 ISO 日期 (YYYY-MM-DD)");
        }
        return failures;
    }

    /** null／"null"／空白视为未登记，返回 null；否则返回原文。 */
    private static String raw(String value) {
        return isBlank(value) ? null : value;
    }

    // ---------- tiny YAML subset parser (same discipline as the regulatory ledger) ----------

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
        return value == null || value.isBlank() || "null".equals(value);
    }
}
