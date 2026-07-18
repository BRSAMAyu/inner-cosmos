package com.innercosmos.util;

import com.innercosmos.entity.EchoCapsule;

import java.util.ArrayList;
import java.util.List;

/**
 * A3-capsule-matching: single source of truth for "what text about a capsule is safe to send to
 * matching (lexical or embedding) and, when an embedding provider is configured, to a third-party
 * provider." Deliberately built ONLY from {@link EchoCapsule#pseudonym}, {@link EchoCapsule#intro}
 * and {@link EchoCapsule#publicTags} — the same three fields the plaza already renders publicly.
 * NEVER includes personaPrompt, ownerContextNote, styleProfileJson or contextPreviewJson, which are
 * private authoring/runtime fields the owner never consented to expose to matching or to an
 * external embedding call.
 */
public final class CapsulePublicTextUtils {
    private CapsulePublicTextUtils() {}

    public static String publicSafeText(EchoCapsule capsule) {
        if (capsule == null) return "";
        return joinText(capsule.intro, String.join(" ", parseTags(capsule.publicTags)), capsule.pseudonym);
    }

    public static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<String> tags = new ArrayList<>();
        for (String item : cleaned.split(",")) {
            String tag = item.trim().replace("\"", "");
            if (!tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static String joinText(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
