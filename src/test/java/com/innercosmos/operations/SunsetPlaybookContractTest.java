package com.innercosmos.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-62 sunset playbook contract: all seven blueprint scenarios present (90 日低现金/
 * 供应商退出/创始人不可用/云冻结/证书过期/并购/停服)， each with the full decision
 * chain (trigger → first 24h → readonly → refunds → contact → retention → destruction),
 * and nothing that looks like a rehearsed or completed state — rehearsal evidence is an
 * operator fact. The service-sunset path must explicitly keep obligations alive
 * (导出/数据权利/退款) and never bind exit to purchase.
 */
class SunsetPlaybookContractTest {

    private static final Path FILE =
            Path.of("docs", "commercialization", "operations", "sunset-playbook.yml");
    private static final List<String> SCENARIOS = List.of(
            "ninety-day-cash", "vendor-exit", "founder-unavailable", "cloud-account-frozen",
            "certificate-expiry", "acquisition-merger", "service-sunset");
    private static final List<String> REQUIRED_FIELDS = List.of(
            "scenario_id", "name", "trigger", "first_24h", "readonly_steps",
            "refund_batch", "contact_channel", "legal_retention", "final_destruction",
            "rehearsal", "status");

    private static final Pattern ITEM = Pattern.compile("^  - scenario_id:\\s*(\\S+)\\s*$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(String id, Map<String, String> fields) {
    }

    @Test
    void sunsetPlaybookCoversAllSevenScenariosHonestly() throws IOException {
        List<Item> items = new ArrayList<>();
        boolean inSection = false;
        Item current = null;
        for (String line : Files.readString(FILE, StandardCharsets.UTF_8).split("\n")) {
            if (line.equals("scenarios:")) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null) {
                    items.add(current);
                }
                java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
                fields.put("scenario_id", item.group(1));
                current = new Item(item.group(1), fields);
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), field.group(2).trim());
            }
        }
        if (current != null) {
            items.add(current);
        }

        List<String> failures = new ArrayList<>();
        assertEquals(SCENARIOS.size(), items.size(), "the seven blueprint scenarios");
        for (Item item : items) {
            for (String field : REQUIRED_FIELDS) {
                if (!item.fields().containsKey(field)) {
                    failures.add(item.id + ": missing " + field);
                }
            }
            if (!"PENDING".equals(item.fields().get("status"))
                    && !"IN_PROGRESS".equals(item.fields().get("status"))) {
                failures.add(item.id + ": no agent-writable completed state exists");
            }
            if (!isBlank(item.fields().get("rehearsal"))) {
                failures.add(item.id + ": rehearsal evidence is an operator fact");
            }
        }
        // The sunset path keeps obligations alive and never charges for exit.
        Item sunset = items.stream()
                .filter(i -> "service-sunset".equals(i.id)).findFirst().orElseThrow();
        assertTrue(sunset.fields().get("readonly_steps").contains("导出"),
                "sunset readonly keeps export/data-rights/refunds alive");
        assertTrue(sunset.fields().get("refund_batch").contains("退款"));
        assertTrue(sunset.fields().get("final_destruction").contains("销毁"));
        // Every scenario names the readonly switch or states why readonly is not needed.
        for (Item item : items) {
            String steps = item.fields().get("readonly_steps");
            if (isBlank(steps)) {
                failures.add(item.id + ": readonly decision must be explicit");
            }
        }
        assertTrue(failures.isEmpty(), "sunset playbook violations: " + failures);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
