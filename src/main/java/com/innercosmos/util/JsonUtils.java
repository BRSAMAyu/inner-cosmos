package com.innercosmos.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    /**
     * Escape a raw string for safe interpolation inside a JSON string literal.
     * Shared helper so hand-built JSON (e.g. CapsuleSyncService.buildDiffSummary) does not
     * produce malformed output when a value contains a double-quote or backslash.
     * Backslash MUST be escaped before the double-quote.
     */
    public static String escapeJsonString(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
