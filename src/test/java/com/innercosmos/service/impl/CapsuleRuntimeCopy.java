package com.innercosmos.service.impl;

/**
 * Runtime-owned capsule copy (quota guidance, boundary refusals) now mirrors the visitor's
 * language instead of always being Chinese — see {@code com.innercosmos.util.VisitorLanguage}.
 * These tests drive the service with English visitor messages, so asserting the Chinese wording
 * literally would pin the old bug rather than the behaviour. The helpers below assert the
 * behaviour in either language.
 */
final class CapsuleRuntimeCopy {

    private CapsuleRuntimeCopy() {
    }

    /** True when the turn was redirected to the slow-letter path (session cap or daily quota). */
    static boolean guidesToSlowLetter(String text) {
        return text != null && (text.contains("慢信") || text.toLowerCase().contains("slow letter"));
    }

    /** True when the reply was replaced because it crossed a boundary or leaked internals. */
    static boolean refusedForBoundary(String text) {
        return text != null && (text.contains("越过了边界") || text.contains("不会展示它")
                || text.toLowerCase().contains("crossed a boundary")
                || text.toLowerCase().contains("will not show it"));
    }
}
