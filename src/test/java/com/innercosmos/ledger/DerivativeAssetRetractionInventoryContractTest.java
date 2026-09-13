package com.innercosmos.ledger;

import com.innercosmos.event.reliable.DataRetractedOutboxWriter;
import com.innercosmos.event.reliable.DataRetractedProjectionHandler;
import com.innercosmos.service.impl.RetractionDerivativeCleanupServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_CACHE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_CAPSULE_MATCH_VECTOR;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_EXPORT_PACKAGE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_OBJECT_STORAGE;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_PROVIDER_COPY;
import static com.innercosmos.service.RetractionDerivativeCleanupService.ASSET_PUSH_COPY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-15 §2-11 contract over docs/commercialization/ledger/derivative-asset-retraction-inventory.yml:
 * the inventory must mirror the executable reality — exactly the five checklist asset classes
 * (plus the vector re-assert step) with the same asset keys the consumer's executor knows, only
 * REAL action references (Class#method that actually resolve), honest NOT_APPLICABLE rows that
 * carry no fabricated action, and evidence paths that exist in the repository. A surface that
 * does not exist (no object storage, no server-side export persistence) must say so instead of
 * inventing a cleanup command.
 */
class DerivativeAssetRetractionInventoryContractTest {

    private static final Path FILE = Path.of("docs", "commercialization", "ledger",
            "derivative-asset-retraction-inventory.yml");
    private static final Set<String> ALLOWED_STATUS = Set.of("IMPLEMENTED", "NOT_APPLICABLE");
    private static final Set<String> INVENTORY_ASSET_KEYS = Set.of(ASSET_CACHE, ASSET_OBJECT_STORAGE,
            ASSET_EXPORT_PACKAGE, ASSET_PUSH_COPY, ASSET_PROVIDER_COPY);
    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern META_FIELD = Pattern.compile("^  (\\w+):\\s*(.*)$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Parsed(Map<String, String> meta, List<Map<String, String>> assets,
                          List<Map<String, String>> vectorReassert) {
    }

    @Test
    void inventoryMirrorsTheExecutableDerivativeCleanupReality() throws Exception {
        List<String> failures = new ArrayList<>();
        Parsed parsed = parse(FILE);

        // ---- meta must point at the real event, consumer, executor and result table ----
        Map<String, String> meta = parsed.meta();
        for (String required : List.of("schema", "event_type", "consumer", "executor",
                "result_table", "sync_baseline")) {
            if (isBlank(meta.get(required))) failures.add("meta: missing " + required);
        }
        if (!DataRetractedOutboxWriter.EVENT_TYPE.equals(meta.get("event_type"))) {
            failures.add("meta: event_type must be " + DataRetractedOutboxWriter.EVENT_TYPE);
        }
        String consumerName = new DataRetractedProjectionHandler(new ObjectMapper(),
                command -> List.of()).consumerName();
        if (!consumerName.equals(meta.get("consumer"))) {
            failures.add("meta: consumer must be the handler's consumerName " + consumerName);
        }
        Class<?> executor = resolve(meta.get("executor"), failures);
        if (!RetractionDerivativeCleanupServiceImpl.RESULT_TABLE.equals(meta.get("result_table"))) {
            failures.add("meta: result_table must be " + RetractionDerivativeCleanupServiceImpl.RESULT_TABLE);
        }

        // ---- assets: exactly the five checklist classes, keys shared with the executor ----
        Set<String> keys = new HashSet<>();
        for (Map<String, String> asset : parsed.assets()) {
            String key = asset.get("asset_key");
            if (isBlank(key) || !keys.add(key)) {
                failures.add("assets: duplicate or blank asset_key " + key);
            }
            for (String required : List.of("asset_key", "surfaces", "retraction_action", "status",
                    "action_ref", "reason", "evidence")) {
                if (isBlank(asset.get(required))) failures.add("asset/" + key + ": missing " + required);
            }
            if (!ALLOWED_STATUS.contains(asset.get("status"))) {
                failures.add("asset/" + key + ": status not in " + ALLOWED_STATUS);
            }
            evidencePathsExist(asset.get("evidence"), "asset/" + key, failures);
            if ("IMPLEMENTED".equals(asset.get("status"))) {
                MethodRef ref = methodRef(asset.get("action_ref"), "asset/" + key, failures);
                if (ref != null) {
                    if (executor == null || !executor.getName().equals(ref.className())) {
                        failures.add("asset/" + key
                                + ": IMPLEMENTED action_ref must name the meta executor class");
                    }
                    methodDeclared(ref, "asset/" + key, failures);
                }
            } else if ("NOT_APPLICABLE".equals(asset.get("status"))) {
                if (!"none".equals(asset.get("action_ref"))) {
                    failures.add("asset/" + key
                            + ": NOT_APPLICABLE must carry action_ref none — no fabricated cleanup");
                }
                if (asset.getOrDefault("retraction_action", "").indexOf("NOT_APPLICABLE") < 0) {
                    failures.add("asset/" + key
                            + ": NOT_APPLICABLE rows must state it in retraction_action (如实标注)");
                }
            }
        }
        if (!keys.equals(INVENTORY_ASSET_KEYS)) {
            failures.add("assets: must be exactly the five inventory classes known to the executor: "
                    + INVENTORY_ASSET_KEYS + " — found " + keys
                    + " (new asset surfaces must be registered in BOTH places)");
        }

        // ---- vector re-assert: exactly the one step, reusing the real retire method ----
        Set<String> reassertKeys = new HashSet<>();
        for (Map<String, String> step : parsed.vectorReassert()) {
            String key = step.get("action_key");
            if (isBlank(key) || !reassertKeys.add(key)) {
                failures.add("vector_reassert: duplicate or blank action_key " + key);
            }
            for (String required : List.of("action_key", "surfaces", "retraction_action", "status",
                    "reuses", "reason", "evidence")) {
                if (isBlank(step.get(required))) failures.add("vector/" + key + ": missing " + required);
            }
            evidencePathsExist(step.get("evidence"), "vector/" + key, failures);
            if (!"IMPLEMENTED".equals(step.get("status"))) {
                failures.add("vector/" + key + ": must stay IMPLEMENTED while the consumer re-asserts");
            }
            MethodRef reuses = methodRef(step.get("reuses"), "vector/" + key, failures);
            if (reuses != null) {
                methodDeclared(reuses, "vector/" + key, failures);
            }
        }
        if (!reassertKeys.equals(Set.of(ASSET_CAPSULE_MATCH_VECTOR))) {
            failures.add("vector_reassert: exactly one step keyed " + ASSET_CAPSULE_MATCH_VECTOR);
        }

        assertTrue(failures.isEmpty(), "inventory contract violations: " + failures);
        assertEquals(5, parsed.assets().size(), "hand-maintained five-class inventory sanity");
    }

    // ---------- tiny YAML subset parser (same discipline as the store/ops checklists) ----------

    private static Parsed parse(Path file) throws IOException {
        Map<String, String> meta = new LinkedHashMap<>();
        List<Map<String, String>> assets = new ArrayList<>();
        List<Map<String, String>> vector = new ArrayList<>();
        String section = null;
        Map<String, String> currentItem = null;
        for (String raw : Files.readString(file, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.endsWith("\r") ? line0(raw) : raw;
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            Matcher item = ITEM.matcher(line);
            if (item.find() && ("assets".equals(section) || "vector_reassert".equals(section))) {
                currentItem = new LinkedHashMap<>();
                currentItem.put(item.group(1), unquote(item.group(2)));
                ("assets".equals(section) ? assets : vector).add(currentItem);
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && currentItem != null) {
                currentItem.put(field.group(1), unquote(field.group(2)));
                continue;
            }
            Matcher metaField = META_FIELD.matcher(line);
            if (metaField.find() && "meta".equals(section) && currentItem == null) {
                meta.put(metaField.group(1), unquote(metaField.group(2)));
                continue;
            }
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                section = sec.group(1);
                currentItem = null;
            }
        }
        return new Parsed(meta, assets, vector);
    }

    private static String line0(String raw) {
        return raw.substring(0, raw.length() - 1);
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    // ---------- helpers ----------

    private static void evidencePathsExist(String evidence, String where, List<String> failures) {
        if (isBlank(evidence)) return;
        String[] paths = evidence.split("\\|");
        if (paths.length < 2) {
            failures.add(where + ": needs at least two real evidence paths");
        }
        for (String path : paths) {
            if (!isBlank(path) && !Files.exists(Path.of(path.trim()))) {
                failures.add(where + ": evidence path does not exist: " + path.trim());
            }
        }
    }

    private record MethodRef(String className, String methodName) {
    }

    private static Class<?> resolve(String className, List<String> failures) {
        if (isBlank(className)) return null;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            failures.add("class does not exist: " + className);
            return null;
        }
    }

    private static MethodRef methodRef(String ref, String where, List<String> failures) {
        if (isBlank(ref) || !ref.matches("^[\\w.]+#[\\w]+$")) {
            failures.add(where + ": action_ref must be Class#method, got " + ref);
            return null;
        }
        String className = ref.substring(0, ref.indexOf('#'));
        return new MethodRef(className, ref.substring(ref.indexOf('#') + 1));
    }

    private static void methodDeclared(MethodRef ref, String where, List<String> failures) {
        Class<?> type = resolve(ref.className(), failures);
        if (type == null) return;
        boolean found = false;
        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(ref.methodName())) found = true;
        }
        if (!found) {
            failures.add(where + ": " + ref.className() + " declares no method " + ref.methodName());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
