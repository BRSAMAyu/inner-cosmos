package com.innercosmos.launch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
 * CP-64 business-decision ledger contract over
 * docs/commercialization/launch/business-decision.ledger.yml. The honesty discipline is
 * structural, mirroring the regulatory ledger: BUSINESS_VALIDATED does not exist as a
 * writable state — 蓝图 CP-61/CP-64 "商业不成立可关闭验证任务但 BUSINESS_VALIDATED 仍不得
 * 通过", so the token may only appear in the header comment explaining that rule, never as
 * a field value. A shipped draft is PENDING + UNDECIDED with no conclusion; evidence
 * metrics may ONLY be CP-03 dictionary keys (K1/K2/K3 — 融资金额或用户数据量不是唯一成功
 * 证明); counterexamples and budget_impact are real-data facts and must stay null while
 * PENDING; a written decision (FOCUS/SCALE/REPOSITION/STOP) must additionally carry
 * conclusion, revalidate_at and non-null counterexamples/budget (书面决策有证据、反例、
 * 预算、owner 和复审日期).
 */
class BusinessDecisionContractTest {

    private static final Path LEDGER = Path.of("docs", "commercialization", "launch",
            "business-decision.ledger.yml");

    /** CP-03 指标字典：evidence_metrics 只能引用这三个键。 */
    private static final Set<String> CP03_DICTIONARY = Set.of(
            "K1_weekly_confirmed_value", "K2_d30_window_value_retention",
            "K3_monthly_contribution_margin");
    private static final Set<String> DECISIONS = Set.of(
            "FOCUS", "SCALE", "REPOSITION", "STOP", "UNDECIDED");
    /** BUSINESS_VALIDATED 刻意不在可写状态集合中（诚实规则）。 */
    private static final Set<String> STATUSES = Set.of("PENDING", "DRAFT_REVIEWED");
    private static final List<String> HYPOTHESES = List.of(
            "hypothesis_h1", "hypothesis_h2", "hypothesis_h3");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");
    private static final Pattern NESTED_FIELD = Pattern.compile("^      (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void ledgerShipsOneUndecidedDraftAnchoredToTheCp03Dictionary() throws IOException {
        List<Item> decisions = parse();
        List<String> failures = new ArrayList<>();

        assertEquals(1, decisions.size(), "首版只落一条 DRAFT 决策");
        Item decision = decisions.get(0);
        String id = decision.f("decision_id");
        if (id == null || !id.startsWith("bd-")) {
            failures.add("decision_id 必须以 bd- 开头: " + id);
        }
        for (String field : List.of("decision_id", "period", "decision", "owner",
                "revalidate_at", "status", "conclusion")) {
            if (!decision.fields().containsKey(field)) {
                failures.add(id + ": missing " + field);
            }
        }
        if (isBlank(decision.f("period")) || isBlank(decision.f("owner"))) {
            failures.add(id + ": period 与 owner 必填");
        }
        // H1–H3 三假设：各带 statement / evidence_metrics / counterexamples / budget_impact。
        Set<String> usedMetrics = new HashSet<>();
        for (String hypothesis : HYPOTHESES) {
            for (String sub : List.of("statement", "evidence_metrics", "counterexamples",
                    "budget_impact")) {
                if (!decision.fields().containsKey(hypothesis + "." + sub)) {
                    failures.add(id + ": missing " + hypothesis + "." + sub);
                }
            }
            if (isBlank(decision.f(hypothesis + ".statement"))) {
                failures.add(id + ": " + hypothesis + " 必须写明假设内容");
            }
            for (String metric : csv(decision.f(hypothesis + ".evidence_metrics"))) {
                if (!CP03_DICTIONARY.contains(metric)) {
                    failures.add(id + ": " + hypothesis + " evidence metric " + metric
                            + " 不是 CP-03 指标字典键");
                }
                usedMetrics.add(metric);
            }
            if (csv(decision.f(hypothesis + ".evidence_metrics")).isEmpty()) {
                failures.add(id + ": " + hypothesis + " 必须锚定字典指标");
            }
        }
        assertEquals(CP03_DICTIONARY, usedMetrics,
                "H1–H3 的证据面必须锚定 CP-03 字典 K1/K2/K3 全部三个键");

        String verdict = decision.f("decision");
        if (!DECISIONS.contains(verdict)) {
            failures.add(id + ": unknown decision " + verdict + " not in " + DECISIONS);
        }
        String status = decision.f("status");
        if (!STATUSES.contains(status)) {
            failures.add(id + ": unknown status " + status + " not in " + STATUSES);
        }

        // 首版诚实状态：PENDING + UNDECIDED，无结论、无复审日。
        assertEquals("PENDING", status, "当前台账没有任何已评审事实");
        assertEquals("UNDECIDED", verdict, "当前决策未作出，不得预写方向");

        // PENDING 草稿：反例与预算影响为 null（真实数据事实，不由 agent 起草）。
        if ("PENDING".equals(status)) {
            for (String hypothesis : HYPOTHESES) {
                for (String fact : List.of("counterexamples", "budget_impact")) {
                    if (!isBlank(decision.f(hypothesis + "." + fact))) {
                        failures.add(id + ": " + hypothesis + "." + fact
                                + " 在 status=PENDING 时必须为 null");
                    }
                }
            }
            if (!isBlank(decision.f("revalidate_at"))) {
                failures.add(id + ": PENDING 草稿不带复审日期");
            }
        }
        // UNDECIDED 不得有 conclusion；一旦书面决策，必须有结论、复审日、反例与预算。
        if ("UNDECIDED".equals(verdict)) {
            if (!isBlank(decision.f("conclusion"))) {
                failures.add(id + ": UNDECIDED 不得有 conclusion");
            }
        } else {
            for (String required : List.of("conclusion", "revalidate_at")) {
                if (isBlank(decision.f(required))) {
                    failures.add(id + ": 书面决策（" + verdict + "）必须有 " + required);
                }
            }
            for (String hypothesis : HYPOTHESES) {
                for (String fact : List.of("counterexamples", "budget_impact")) {
                    if (isBlank(decision.f(hypothesis + "." + fact))) {
                        failures.add(id + ": 书面决策必须回填 " + hypothesis + "." + fact);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "business decision contract violations: " + failures);
    }

    @Test
    void businessValidatedIsOnlyEverACommentNeverAFieldValue() throws IOException {
        List<String> lines = Files.readAllLines(LEDGER, StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        int commentMentions = 0;

        for (String line : lines) {
            if (!line.contains("BUSINESS_VALIDATED")) continue;
            if (!line.trim().startsWith("#")) {
                failures.add("BUSINESS_VALIDATED 出现在非注释行（不得作为字段值或状态）: "
                        + line.trim());
            } else {
                commentMentions++;
            }
        }
        assertTrue(commentMentions >= 1,
                "头注必须写明 BUSINESS_VALIDATED 不作为可写状态的诚实规则");
        for (Item decision : parse()) {
            for (Map.Entry<String, String> field : decision.fields().entrySet()) {
                if (field.getValue() != null && field.getValue().contains("BUSINESS_VALIDATED")) {
                    failures.add("字段 " + field.getKey() + " 的值含有 BUSINESS_VALIDATED");
                }
            }
        }
        assertTrue(failures.isEmpty(), "no-business-validated violations: " + failures);
    }

    // ---------- tiny YAML subset parser（2 空格条目／4 空格字段／6 空格假设子字段） ----------

    private static List<Item> parse() throws IOException {
        List<Item> items = new ArrayList<>();
        boolean inSection = false;
        Item current = null;
        String nested = null;
        for (String line : Files.readString(LEDGER, StandardCharsets.UTF_8).split("\n")) {
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                if (current != null) items.add(current);
                current = null;
                nested = null;
                inSection = "decisions".equals(sec.group(1));
                continue;
            }
            if (!inSection) continue;
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null) items.add(current);
                current = new Item(new LinkedHashMap<>());
                current.fields().put(item.group(1), strip(item.group(2)));
                nested = null;
                continue;
            }
            Matcher nestedField = NESTED_FIELD.matcher(line);
            if (nestedField.find() && current != null && nested != null) {
                current.fields().put(nested + "." + nestedField.group(1),
                        strip(nestedField.group(2)));
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                String value = strip(field.group(2));
                if (value.isEmpty()) {
                    nested = field.group(1);
                } else {
                    nested = null;
                    current.fields().put(field.group(1), value);
                }
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static List<String> csv(String value) {
        if (isBlank(value)) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).toList();
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
