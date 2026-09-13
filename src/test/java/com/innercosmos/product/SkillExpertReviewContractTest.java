package com.innercosmos.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-60A expert sign-off ledger contract over
 * docs/commercialization/product/skill-expert-review.ledger.yml. The frozen criterion
 * (experiment-registry.yml CP-60A) is "≥6技能×2专家、每项双专家通过" — and the honesty
 * discipline is structural, mirroring the CP-51A legal ledger: verdict only moves between
 * PENDING and SUBMITTED by structural work. "双专家通过" is an operator gate — there is
 * NO approved state, receipts stay blank until the operator backfills real external
 * sign-off evidence, and every skill must carry two independent expert rows whose ids
 * align 1:1 with the registered manifests in src/main/resources/skills/.
 */
class SkillExpertReviewContractTest {

    private static final Path LEDGER = Path.of("docs", "commercialization", "product", "skill-expert-review.ledger.yml");
    private static final Path SKILLS_DIR = Path.of("src", "main", "resources", "skills");

    private static final Set<String> ALLOWED_VERDICTS = Set.of("PENDING", "SUBMITTED");
    private static final int EXPERTS_PER_SKILL = 2;
    private static final List<String> REQUIRED_FIELDS = List.of("expert_id", "skill_id", "verdict", "receipt");

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");
    private static final Pattern JSON_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void everyRegisteredSkillCarriesTwoIndependentExpertRowsWithoutAnyApprovalState()
            throws IOException {
        List<String> failures = new ArrayList<>();
        Set<String> registeredSkills = registeredSkillIds();
        assertTrue(registeredSkills.size() >= 6,
                "CP-60A 冻结口径要求 ≥6 项技能，注册表当前只有 " + registeredSkills.size());

        List<Item> reviews = parse(LEDGER, "reviews");
        assertEquals(registeredSkills.size() * EXPERTS_PER_SKILL, reviews.size(),
                "台账行数必须是 技能数×2（每项双专家）");

        Set<String> experts = new HashSet<>();
        Map<String, Map<String, String>> skillToExperts = new HashMap<>();
        Set<String> expertSkillPairs = new HashSet<>();
        for (Item review : reviews) {
            String skill = review.f("skill_id");
            String expert = review.f("expert_id");
            for (String field : REQUIRED_FIELDS) {
                if (!review.fields().containsKey(field)) failures.add(skill + ": missing " + field);
            }
            if (!registeredSkills.contains(skill)) {
                failures.add("skill_id " + skill + " 不在 src/main/resources/skills 注册表中");
            }
            String verdict = review.f("verdict");
            if (!ALLOWED_VERDICTS.contains(verdict)) {
                failures.add(skill + ": verdict \"" + verdict + "\" 不在 " + ALLOWED_VERDICTS
                        + " —— 双专家通过是 operator 门禁，不存在 agent 可填的通过态");
            }
            if ("APPROVED".equalsIgnoreCase(verdict) || "PASSED".equalsIgnoreCase(verdict)) {
                failures.add(skill + ": 不存在 APPROVED/PASSED 状态（诚实规则）");
            }
            // Operator-only field must stay blank until real external sign-off lands.
            if (!isBlank(review.f("receipt"))) {
                failures.add(skill + "/" + expert + ": receipt 在 operator 落位真实签字回执前必须为空");
            }
            if (isBlank(expert)) {
                failures.add("row without expert_id");
                continue;
            }
            experts.add(expert);
            if (!expertSkillPairs.add(expert + "@" + skill)) {
                failures.add("duplicate expert row " + expert + "@" + skill);
            }
            skillToExperts.computeIfAbsent(skill, key -> new LinkedHashMap<>()).put(expert, verdict);
        }

        // Two independent experts overall, each reviewing every registered skill.
        assertEquals(Set.of("EXP-PSY-01", "EXP-PSY-02"), experts,
                "CP-60A 指定两名独立心理专家逐技能审阅（EXP-PSY-01 / EXP-PSY-02）");
        for (String skill : registeredSkills) {
            Map<String, String> expertVerdicts = skillToExperts.get(skill);
            if (expertVerdicts == null) {
                failures.add("registered skill " + skill + " 缺少专家签字台账行（注册表扩容必须同步扩台账）");
            } else if (expertVerdicts.size() != EXPERTS_PER_SKILL) {
                failures.add(skill + " 必须恰好有 " + EXPERTS_PER_SKILL + " 名独立专家的行，当前 "
                        + expertVerdicts.size());
            }
        }
        assertTrue(failures.isEmpty(), "skill expert review ledger violations: " + failures);
    }

    @Test
    void ledgerSkillIdsAlignWithTheRegistryManifestFiles() throws IOException {
        Set<String> registeredSkills = registeredSkillIds();
        Set<String> ledgerSkills = new HashSet<>();
        for (Item review : parse(LEDGER, "reviews")) {
            ledgerSkills.add(review.f("skill_id"));
        }
        assertEquals(registeredSkills, ledgerSkills,
                "台账 skill_id 集合必须与 src/main/resources/skills/*.v1.json 完全一致");
        for (String skill : registeredSkills) {
            assertTrue(Files.exists(SKILLS_DIR.resolve(skill + ".v1.json")),
                    "技能 " + skill + " 的清单文件名必须与 id 对齐（<id>.v1.json）");
        }
    }

    /** Skill ids come straight from the shipped manifests, not a hardcoded copy. */
    private static Set<String> registeredSkillIds() throws IOException {
        try (Stream<Path> files = Files.list(SKILLS_DIR)) {
            Set<String> ids = new HashSet<>();
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Matcher id = JSON_ID.matcher(Files.readString(file, StandardCharsets.UTF_8));
                assertTrue(id.find(), file + " must declare an \"id\" field");
                assertTrue(ids.add(id.group(1)), "duplicate skill id in " + file);
            }
            return ids;
        }
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
