package com.innercosmos.util;

public final class TextSanitizer {
    private TextSanitizer() {
    }

    public static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
