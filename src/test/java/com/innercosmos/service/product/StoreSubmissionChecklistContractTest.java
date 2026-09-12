package com.innercosmos.service.product;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-43/44 structured store-submission checklists: every channel file in
 * docs/commercialization/store-submissions must satisfy the structure contract — required
 * fields present, ids unique per file, status from the honest set, and NO PASS state:
 * approval can only be recorded by the operator as an official receipt in evidence, never
 * by a coding agent flipping a field. The blueprint's "不得凭经验填 PASS" is enforced
 * structurally.
 */
class StoreSubmissionChecklistContractTest {

    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "IN_PROGRESS", "WAIVED");
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "id", "category", "requirement", "evidence_required", "blocks_submission",
            "status", "evidence", "approved_by");
    private static final Set<String> EXPECTED_CHANNELS = Set.of(
            "web-pwa", "apple-cn", "xiaomi", "oppo", "huawei", "vivo-others");
    private static final Pattern FIELD = Pattern.compile("^\\s{4}(\\w+):\\s*(.*)$");

    private record Item(String id, Map<String, String> fields) {
    }

    @Test
    void everyChannelChecklistSatisfiesTheStructureContract() throws IOException {
        Path dir = Path.of("docs", "commercialization", "store-submissions");
        assertTrue(Files.isDirectory(dir), "checklist directory must exist");
        Set<String> found = new HashSet<>();
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".checklist.yml")).toList()) {
                String slug = file.getFileName().toString().replace(".checklist.yml", "");
                found.add(slug);
                Set<String> ids = new HashSet<>();
                for (Item item : parseItems(Files.readString(file, StandardCharsets.UTF_8))) {
                    if (!ids.add(item.id())) failures.add(slug + ": duplicate id " + item.id());
                    for (String field : REQUIRED_FIELDS) {
                        if (!item.fields().containsKey(field)) {
                            failures.add(slug + "/" + item.id() + ": missing " + field);
                        }
                    }
                    String status = item.fields().get("status");
                    if (!ALLOWED_STATUS.contains(status)) {
                        failures.add(slug + "/" + item.id() + ": status \"" + status + "\" not in "
                                + ALLOWED_STATUS + " — there is no PASS state by design");
                    }
                    if ("WAIVED".equals(status) && isBlank(item.fields().get("approved_by"))) {
                        failures.add(slug + "/" + item.id() + ": WAIVED requires approved_by");
                    }
                }
                if (ids.isEmpty()) failures.add(slug + ": no items");
            }
        }
        for (String channel : EXPECTED_CHANNELS) {
            if (!found.contains(channel)) failures.add("missing channel checklist: " + channel);
        }
        assertTrue(failures.isEmpty(), "checklist contract violations: " + failures);
        assertEquals(EXPECTED_CHANNELS, found);
    }

    private static List<Item> parseItems(String body) {
        List<Item> items = new ArrayList<>();
        Map<String, String> current = null;
        String currentId = null;
        for (String line : body.split("\n")) {
            if (line.startsWith("  - id:")) {
                if (current != null) items.add(new Item(currentId, current));
                current = new LinkedHashMap<>();
                currentId = line.substring(line.indexOf(':') + 1).trim();
                current.put("id", currentId);
            } else if (current != null) {
                Matcher field = FIELD.matcher(line);
                if (field.find()) current.put(field.group(1), field.group(2).trim());
            }
        }
        if (current != null) items.add(new Item(currentId, current));
        return items;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
