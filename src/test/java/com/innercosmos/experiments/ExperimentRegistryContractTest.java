package com.innercosmos.experiments;

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
 * CP-53/CP-55 pre-registered experiment registry contract over
 * docs/commercialization/experiments/registry.yml. The honesty discipline: primary
 * endpoints may ONLY be CP-03 dictionary metrics (K1/K2/K3) — diagnostic numbers (时长/
 * DAU/消息数/流水额) can never headline an experiment; the K2 decision band is pinned to
 * the blueprint constants 0.15/0.25 (no moving goalposts after enrollment); subsidy is
 * always counted into CAC and natural retention is always reported separately; growth
 * channels need community permission before enrollment and are capped at three
 * concurrent experiments; pricing experiments carry the fixed forbidden-monetization
 * assertions; and DRAFT is the only state a coding agent may author — frozen_at /
 * owner_signature / conclusion are operator-or-reviewer facts backed by external
 * evidence. (variables_changed ≤ 2 is an internal POLICY stricter than the blueprint's
 * "少量", not blueprint text.)
 */
class ExperimentRegistryContractTest {

    private static final Path REGISTRY = Path.of("docs", "commercialization", "experiments",
            "registry.yml");

    private static final Set<String> ALLOWED_ENDPOINTS = Set.of(
            "K1_weekly_confirmed_value", "K2_d30_window_value_retention",
            "K3_monthly_contribution_margin");
    /** 蓝图 §4.1：这些只作诊断，禁止作主终点。 */
    private static final Set<String> DIAGNOSTIC_ONLY = Set.of(
            "session_duration", "dau", "message_count", "memory_card_count",
            "slow_letter_count", "gross_payment_volume");
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "PREREGISTERED", "ENROLLING", "MATURED_ANALYSIS", "STOPPED");
    private static final Set<String> CONCLUSIONS = Set.of(
            "SUPPORTED", "NOT_SUPPORTED", "INCONCLUSIVE");
    private static final Pattern ITEM = Pattern.compile("^  - experiment_id:\\s*(\\S+)\\s*$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(String id, Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void experimentRegistrySatisfiesThePreregistrationContract() throws IOException {
        List<Item> experiments = parse();
        List<String> failures = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int growthChannels = 0;

        assertTrue(experiments.size() >= 4, "three growth channels plus one pricing draft");
        for (Item experiment : experiments) {
            String id = experiment.id;
            if (!ids.add(id)) failures.add("duplicate experiment_id " + id);
            if (!id.matches("(growth-channel|pricing|social-supply)-\\d{3}")) {
                failures.add(id + ": id pattern");
            }
            for (String field : List.of("kind", "owner", "reviewer", "hypothesis",
                    "randomization_unit", "primary_endpoint", "secondary_endpoints",
                    "gate_metrics", "minimum_meaningful_difference", "stopping_gates",
                    "statistical_method", "sample_size_exploratory_n", "budget_cap_amount",
                    "budget_cap_currency", "subsidy_counts_into_cac",
                    "natural_retention_reported_separately", "variables_changed",
                    "status", "owner_signature")) {
                if (!experiment.fields().containsKey(field)) {
                    failures.add(id + ": missing " + field);
                }
            }
            if (experiment.f("owner") != null && experiment.f("owner").equals(experiment.f("reviewer"))) {
                failures.add(id + ": reviewer must differ from owner (独立审核不能自签)");
            }
            String kind = experiment.f("kind");
            if ("growth_channel".equals(kind)) growthChannels++;
            // Endpoints: dictionary keys only, never diagnostic numbers.
            if (!ALLOWED_ENDPOINTS.contains(experiment.f("primary_endpoint"))) {
                failures.add(id + ": primary_endpoint " + experiment.f("primary_endpoint")
                        + " is not a CP-03 dictionary metric");
            }
            for (String endpoint : csv(experiment.f("secondary_endpoints"))) {
                if (!ALLOWED_ENDPOINTS.contains(endpoint)) {
                    failures.add(id + ": secondary endpoint " + endpoint + " outside dictionary");
                }
                if (DIAGNOSTIC_ONLY.contains(endpoint)) {
                    failures.add(id + ": " + endpoint + " is diagnostic-only");
                }
            }
            if (!csv(experiment.f("gate_metrics")).containsAll(List.of("G-SAFE", "G-TRUST"))) {
                failures.add(id + ": gate metrics must always include G-SAFE and G-TRUST");
            }
            // K2 endpoints pin the decision band to the blueprint constants.
            if ("K2_d30_window_value_retention".equals(experiment.f("primary_endpoint"))) {
                if (!"0.15".equals(experiment.f("decision_band_pause_below"))
                        || !"0.25".equals(experiment.f("decision_band_further_validation_at"))) {
                    failures.add(id + ": K2 decision band must stay at the frozen 0.15/0.25");
                }
            }
            // Subsidy honesty: always in CAC, natural retention always separate.
            if (!"true".equals(experiment.f("subsidy_counts_into_cac"))
                    || !"true".equals(experiment.f("natural_retention_reported_separately"))) {
                failures.add(id + ": subsidy counts into CAC; natural retention reported separately");
            }
            // Variables cap: internal policy stricter than the blueprint's 少量.
            if (csv(experiment.f("variables_changed")).size() > 2) {
                failures.add(id + ": variables_changed exceeds the internal cap of 2");
            }
            // Stopping gates must exist and include the safety hard stop.
            if (csv(experiment.f("stopping_gates")).isEmpty()
                    || !experiment.f("stopping_gates").contains("G-SAFE/G-TRUST")) {
                failures.add(id + ": stopping gates must include the safety hard stop");
            }
            // Kind-specific requirements.
            if ("growth_channel".equals(kind)) {
                for (String field : List.of("channel", "community_permission_obtained",
                        "funnel", "cac_cost_components")) {
                    if (isBlank(experiment.f(field))) failures.add(id + ": missing " + field);
                }
                if (!csv(experiment.f("cac_cost_components")).containsAll(
                        List.of("labor", "subsidy", "creator_fees"))) {
                    failures.add(id + ": CAC must include labor, subsidy and creator fees");
                }
                String status = experiment.f("status");
                if (("ENROLLING".equals(status) || "MATURED_ANALYSIS".equals(status))
                        && !"true".equals(experiment.f("community_permission_obtained"))) {
                    failures.add(id + ": no enrollment without obtained community permission");
                }
            }
            if ("social_supply".equals(kind)
                    && !"social_supply_unit".equals(experiment.f("randomization_unit"))) {
                failures.add(id + ": social supply experiments randomize by supply unit");
            }
            if ("pricing".equals(kind)) {
                for (String field : List.of("single_membership_only", "price_points_research_only",
                        "payment_disclosure", "intent_vs_payment", "forbidden_monetization")) {
                    if (isBlank(experiment.f(field))) failures.add(id + ": missing " + field);
                }
                if (!"true".equals(experiment.f("single_membership_only"))
                        || !"true".equals(experiment.f("price_points_research_only"))) {
                    failures.add(id + ": single membership; 29/49/79 are research points only");
                }
                String forbidden = experiment.f("forbidden_monetization");
                for (String must : List.of("更爱你", "收件人回应", "差别定价", "暗扣")) {
                    if (forbidden == null || !forbidden.contains(must)) {
                        failures.add(id + ": forbidden monetization must name " + must);
                    }
                }
            }
            // State honesty: statuses from the honest set; frozen/signed/concluded are
            // external facts unavailable to a DRAFT author.
            String status = experiment.f("status");
            if (!STATUSES.contains(status)) failures.add(id + ": unknown status " + status);
            if ("DRAFT".equals(status)) {
                for (String operatorField : List.of("frozen_at", "owner_signature",
                        "first_enrolled_at", "conclusion")) {
                    if (!isBlank(experiment.f(operatorField))) {
                        failures.add(id + ": " + operatorField
                                + " must stay blank while DRAFT — it is an operator/reviewer fact");
                    }
                }
            } else if (isBlank(experiment.f("frozen_at"))) {
                failures.add(id + ": status beyond DRAFT requires frozen_at");
            }
            String conclusion = experiment.f("conclusion");
            if (!isBlank(conclusion) && !CONCLUSIONS.contains(conclusion)) {
                failures.add(id + ": unknown conclusion " + conclusion);
            }
        }
        assertTrue(growthChannels <= 3, "CP-53: at most three budget-capped channel experiments");
        assertTrue(failures.isEmpty(), "experiment registry violations: " + failures);
        assertEquals(3, growthChannels);
    }

    private static List<String> csv(String value) {
        if (isBlank(value)) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).toList();
    }

    private static List<Item> parse() throws IOException {
        List<Item> items = new ArrayList<>();
        boolean inExperiments = false;
        Item current = null;
        for (String line : Files.readString(REGISTRY, StandardCharsets.UTF_8).split("\n")) {
            if (line.equals("experiments:")) {
                inExperiments = true;
                continue;
            }
            if (!inExperiments) continue;
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null) items.add(current);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("experiment_id", item.group(1));
                current = new Item(item.group(1), fields);
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                String value = field.group(2).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                current.fields().put(field.group(1), value);
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
