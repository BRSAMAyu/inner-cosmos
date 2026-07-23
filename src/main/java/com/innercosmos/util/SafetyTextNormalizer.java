package com.innercosmos.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gemini audit 3.7 (CONFIRMED/P0): shared Unicode normalization for every crisis/abuse/distress
 * keyword match in the codebase. Before this, every matcher (CrisisKeywordRule, AbuseKeywordRule,
 * DistressSignalDetector, SafetyReviewService's fallback heuristics) compared the raw input text
 * against a keyword list via plain {@code String.contains(...)}. A zero-width space inserted
 * mid-keyword (e.g. {@code "自​杀"}) or a full-width/compatibility character variant defeats
 * that exact-codepoint comparison while rendering identically (or near-identically) to the eye.
 *
 * <p>This is the one shared chokepoint every matcher normalizes through, so the defense applies
 * consistently everywhere crisis/abuse detection runs, not just wherever someone remembered to
 * add it.
 */
public final class SafetyTextNormalizer {

    private SafetyTextNormalizer() {
    }

    /**
     * Produces a throwaway copy of {@code text} for keyword MATCHING only -- never use this as a
     * replacement for the text actually stored, displayed, or logged; NFKC normalization and
     * whitespace removal are lossy/mutating and must not silently rewrite what the user wrote.
     *
     * <p>Three passes:
     * <ol>
     *   <li>NFKC normalization -- collapses full-width/compatibility character variants (e.g.
     *       full-width Latin "ｋｉｌｌ" or circled/compatibility forms) to their canonical form,
     *       the same way a keyword-list author wrote the keyword.</li>
     *   <li>Strip every Unicode category Cf (FORMAT) code point outright -- this covers zero-width
     *       space (U+200B), zero-width non-joiner/joiner (U+200C/U+200D), the BOM/zero-width
     *       no-break space (U+FEFF), word joiner (U+2060), and the rest of the "invisible,
     *       renders as nothing" family in one general rule, rather than a hand-maintained list of
     *       specific code points that the next obfuscation variant would slip past.</li>
     *   <li>Drop whitespace that touches a CJK ideograph on either side. CJK phrases (this
     *       project's crisis/abuse keyword lists are mostly Chinese) never legitimately contain an
     *       internal space, so a space inserted mid-keyword ("自 杀") is exactly the kind of
     *       trivial bypass zero-width insertion is. English multi-word phrases ("kill myself")
     *       need their spaces preserved for matching -- and neither side of THOSE spaces is CJK --
     *       so this is deliberately targeted, not a blanket "strip all whitespace" that would
     *       silently break every multi-word English keyword.</li>
     * </ol>
     * Finally lower-cased ({@code Locale.ROOT}) so callers no longer need their own separate
     * case-folding step for English phrase matching.
     */
    public static String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);

        List<Integer> withoutFormatChars = new ArrayList<>(nfkc.length());
        nfkc.codePoints().forEach(cp -> {
            if (Character.getType(cp) != Character.FORMAT) {
                withoutFormatChars.add(cp);
            }
        });

        StringBuilder result = new StringBuilder(withoutFormatChars.size());
        for (int i = 0; i < withoutFormatChars.size(); i++) {
            int cp = withoutFormatChars.get(i);
            if (Character.isWhitespace(cp)) {
                boolean prevIsCjk = i > 0 && isCjk(withoutFormatChars.get(i - 1));
                boolean nextIsCjk = i + 1 < withoutFormatChars.size() && isCjk(withoutFormatChars.get(i + 1));
                if (prevIsCjk || nextIsCjk) {
                    continue;
                }
            }
            result.appendCodePoint(cp);
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }
}
