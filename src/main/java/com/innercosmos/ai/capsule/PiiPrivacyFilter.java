package com.innercosmos.ai.capsule;

import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.UserLongTermMemory;
import com.innercosmos.entity.UserPortrait;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PII Privacy Filter for Echo Capsule persona regeneration.
 * Transforms user portrait data into pseudonymized, privacy-preserving form
 * before it can be used to update Echo Capsule prompts.
 */
@Component
public class PiiPrivacyFilter {

    /**
     * Snapshot of user portrait data used for PII filtering.
     * Wraps portrait entries + LTM + relationship into a single filter input.
     */
    public static class PortraitSnapshot {
        public final List<UserPortrait> portraitEntries;
        public final List<UserLongTermMemory> ltmEntries;
        public final AgentUserRelationship relationship;

        public PortraitSnapshot(List<UserPortrait> portraitEntries,
                                List<UserLongTermMemory> ltmEntries,
                                AgentUserRelationship relationship) {
            this.portraitEntries = portraitEntries != null ? portraitEntries : List.of();
            this.ltmEntries = ltmEntries != null ? ltmEntries : List.of();
            this.relationship = relationship;
        }
    }

    /**
     * Filtered portrait result with pseudonymized data and transparency metadata.
     */
    public record FilteredPortrait(
            String pseudonym, // e.g. "林同学" instead of "林澈"
            String city,               // City only, stripped of precise address
            String ageRange,           // e.g. "25-30" instead of 28
            String occupationCategory, // e.g. "互联网/技术" instead of "前端工程师"
            List<String> values,       // Extracted value preferences
            String agencyBoundary,     // Aurora's role in user's life
            List<String> auroraRoles,  // Aurora's roles as perceived by user
            List<String> droppedFields // Fields removed for privacy
    ) {}

    private final UserPortraitService portraitService;
    private final AgentUserRelationshipService relationshipService;

    public PiiPrivacyFilter(UserPortraitService portraitService,
                            AgentUserRelationshipService relationshipService) {
        this.portraitService = portraitService;
        this.relationshipService = relationshipService;
    }

    /**
     * Create a snapshot from live portrait + LTM + relationship data.
     */
    public PortraitSnapshot createSnapshot(Long userId,
                                           List<UserLongTermMemory> ltmEntries) {
        List<UserPortrait> entries = portraitService.getAll(userId);
        AgentUserRelationship relationship = relationshipService.getOrInit(userId);
        return new PortraitSnapshot(entries, ltmEntries, relationship);
    }

    /**
     * Filter and pseudonymize portrait data for capsule context regeneration.
     *
     * @param portrait The portrait snapshot containing portrait entries, LTM, and relationship
     * @param ltm           Long-term memory entries
     * @param relationship  Agent-user relationship data
     * @param privacyOverrides User-specified privacy overrides (field name -> "ALLOW" or "DROP")
     * @return FilteredPortrait with pseudonymized data
     */
    public FilteredPortrait filter(PortraitSnapshot portrait,
                                   List<UserLongTermMemory> ltm,
                                   AgentUserRelationship relationship,
                                   Map<String, String> privacyOverrides) {
        Map<String, String> overrides = privacyOverrides != null ? privacyOverrides : Map.of();
        List<String> dropped = new ArrayList<>();

        // --- Real name -> pseudonym ---
        String realName = extractFirst(portrait.portraitEntries, "real_name", "nickname", "name");
        String pseudonym = pseudonymize(realName, overrides.getOrDefault("real_name", "PSEUDONYMIZE"));
        if ("DROP".equals(overrides.get("real_name"))) {
            dropped.add("real_name");
            pseudonym = null;
        }

        // --- Precise address -> city only ---
        String rawAddress = extractFirst(portrait.portraitEntries, "address", "location", "residence");
        String city = stripToCity(rawAddress, overrides.getOrDefault("address", "GENERALIZE"));
        if ("DROP".equals(overrides.get("address"))) {
            dropped.add("address");
            city = null;
        }

        // --- Age -> range ---
        Integer preciseAge = extractAge(portrait.portraitEntries);
        String ageRange = toAgeRange(preciseAge, overrides.getOrDefault("age", "RANGE"));
        if ("DROP".equals(overrides.get("age"))) {
            dropped.add("age");
            ageRange = null;
        }

        // --- Occupation -> category ---
        String rawOccupation = extractFirst(portrait.portraitEntries, "occupation", "job", "profession");
        String occupationCategory = generalizeOccupation(rawOccupation, overrides.getOrDefault("occupation", "CATEGORIZE"));
        if ("DROP".equals(overrides.get("occupation"))) {
            dropped.add("occupation");
            occupationCategory = null;
        }

        // --- Values ---
        List<String> values = extractValues(portrait.portraitEntries, ltm);
        if ("DROP".equals(overrides.get("values"))) {
            dropped.add("values");
            values = List.of();
        }

        // --- Agency boundary ---
        String agencyBoundary = relationship != null ? relationship.relationshipBoundaries : null;

        // --- Aurora roles ---
        List<String> auroraRoles = parseAuroraRoles(relationship);

        return new FilteredPortrait(pseudonym, city, ageRange, occupationCategory,
                values, agencyBoundary, auroraRoles, dropped);
    }

    /**
     * Filter overload using snapshot directly.
     */
    public FilteredPortrait filter(PortraitSnapshot snapshot,
                                   Map<String, String> privacyOverrides) {
        return filter(snapshot, snapshot.ltmEntries, snapshot.relationship, privacyOverrides);
    }

    private String extractFirst(List<UserPortrait> entries, String... dims) {
        for (String dim : dims) {
            for (UserPortrait p : entries) {
                if (dim.equalsIgnoreCase(p.dim)) {
                    String val = p.valueJson;
                    if (val != null && !val.isBlank()) {
                        // Strip JSON quotes if present
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        return val.trim();
                    }
                }
            }
        }
        return null;
    }

    private Integer extractAge(List<UserPortrait> entries) {
        for (UserPortrait p : entries) {
            if ("age".equalsIgnoreCase(p.dim)) {
                try {
                    String val = p.valueJson;
                    if (val != null) {
                        val = val.replace("\"", "").trim();
                        return Integer.parseInt(val);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String pseudonymize(String name, String policy) {
        if (name == null || name.isBlank()) return "TA";
        if ("DROP".equals(policy)) return null;
        // Strategy: keep first character + "同学"
        String firstChar = name.substring(0, Math.min(1, name.length()));
        return firstChar + "同学";
    }

    private String stripToCity(String address, String policy) {
        if (address == null || address.isBlank()) return null;
        if ("DROP".equals(policy)) return null;
        // Strip district, street, number details
        // Keep only district-level info (first2-4 chars typically)
        String cleaned = address.trim();
        // Remove detailed parts: 路/街/号/弄/栋/楼
        cleaned = cleaned.replaceAll("[路街道号弄栋楼室].*", "");
        // Keep up to first district-level mention
        if (cleaned.length() > 6) {
            cleaned = cleaned.substring(0, 6);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private String toAgeRange(Integer age, String policy) {
        if (age == null) return null;
        if ("DROP".equals(policy)) return null;
        int lower = (age / 5) * 5;
        return lower + "-" + (lower + 9);
    }

    private String generalizeOccupation(String occupation, String policy) {
        if (occupation == null || occupation.isBlank()) return null;
        if ("DROP".equals(policy)) return null;
        String lower = occupation.toLowerCase();
        if (lower.contains("工程") || lower.contains("开发") || lower.contains("技术") || lower.contains("程序")) {
            return "互联网/技术";
        }
        if (lower.contains("设计") || lower.contains("产品")) {
            return "互联网/技术";
        }
        if (lower.contains("运营") || lower.contains("市场")) {
            return "互联网/产品运营";
        }
        if (lower.contains("金融") || lower.contains("银行") || lower.contains("投资") || lower.contains("财务")) {
            return "金融/财务";
        }
        if (lower.contains("教师") || lower.contains("教育") || lower.contains("培训")) {
            return "教育/培训";
        }
        if (lower.contains("医") || lower.contains("护士") || lower.contains("健康")) {
            return "医疗/健康";
        }
        if (lower.contains("学生") || lower.contains("在读") || lower.contains("读研") || lower.contains("留学")) {
            return "学生";
        }
        return "其他";
    }

    private List<String> extractValues(List<UserPortrait> entries, List<UserLongTermMemory> ltm) {
        Set<String> values = new LinkedHashSet<>();
        for (UserPortrait p : entries) {
            if ("core_value".equalsIgnoreCase(p.dim) || "value".equalsIgnoreCase(p.dim)
                    || "价值观".equals(p.dim) || "核心价值".equals(p.dim)) {
                String val = p.valueJson;
                if (val != null) {
                    values.addAll(parseJsonList(val));
                }
            }
        }
        if (ltm != null) {
            for (UserLongTermMemory m : ltm) {
                if ("VALUE".equals(m.factType) || "BELIEF".equals(m.factType)) {
                    values.add(m.factValue);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        String cleaned = json.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String item : cleaned.split(",")) {
            String tag = item.trim().replace("\"", "").replace("\\", "");
            if (!tag.isBlank()) result.add(tag);
        }
        return result;
    }

    private List<String> parseAuroraRoles(AgentUserRelationship relationship) {
        if (relationship == null || relationship.auroraRoleInUserLife == null) {
            return List.of("倾听者");
        }
        return parseJsonList(relationship.auroraRoleInUserLife);
    }
}
