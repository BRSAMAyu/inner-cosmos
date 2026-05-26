package com.innercosmos.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StructuredOutputParser {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private StructuredOutputParser() {
    }

    public static <T> T parse(String raw, Class<T> clazz) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String json = extractJson(raw);
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("Failed to parse structured output as {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    public static <T> T parseWithFallback(String raw, Class<T> clazz, T fallback) {
        T result = parse(raw, clazz);
        return result != null ? result : fallback;
    }

    private static String extractJson(String raw) {
        String trimmed = raw.trim();

        // Try to extract JSON from markdown code block: ```json ... ```
        int codeBlockStart = trimmed.indexOf("```json");
        if (codeBlockStart >= 0) {
            int jsonStart = codeBlockStart + "```json".length();
            int codeBlockEnd = trimmed.indexOf("```", jsonStart);
            if (codeBlockEnd > jsonStart) {
                return trimmed.substring(jsonStart, codeBlockEnd).trim();
            }
        }

        // Try generic code block: ``` ... ```
        int genericBlockStart = trimmed.indexOf("```");
        if (genericBlockStart >= 0) {
            int jsonStart = trimmed.indexOf('\n', genericBlockStart);
            if (jsonStart >= 0) {
                jsonStart++;
                int codeBlockEnd = trimmed.indexOf("```", jsonStart);
                if (codeBlockEnd > jsonStart) {
                    String candidate = trimmed.substring(jsonStart, codeBlockEnd).trim();
                    if (candidate.startsWith("{") || candidate.startsWith("[")) {
                        return candidate;
                    }
                }
            }
        }

        // Try to find raw JSON object or array
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        // Look for first { or [ in the string
        int objStart = trimmed.indexOf('{');
        int arrStart = trimmed.indexOf('[');
        int start = -1;
        if (objStart >= 0 && arrStart >= 0) {
            start = Math.min(objStart, arrStart);
        } else if (objStart >= 0) {
            start = objStart;
        } else if (arrStart >= 0) {
            start = arrStart;
        }

        if (start >= 0) {
            int end = findMatchingClose(trimmed, start);
            if (end > start) {
                return trimmed.substring(start, end + 1);
            }
        }

        return null;
    }

    private static int findMatchingClose(String text, int openPos) {
        char openChar = text.charAt(openPos);
        char closeChar = openChar == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }
}
