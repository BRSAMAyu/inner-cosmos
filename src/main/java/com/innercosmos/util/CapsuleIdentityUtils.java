package com.innercosmos.util;

/**
 * Deterministic per-capsule visual identity (Morandi day / warm-night taste).
 *
 * <p>Pure &amp; deterministic: same seed input always yields the identical palette,
 * mood and SVG avatar parameters. This is the Java source-of-truth port of the
 * identical algorithm implemented in {@code static/js/capsule-personality.js}
 * ({@code CapsulePersonality.deriveIdentity}). Both sides MUST stay in sync.
 *
 * <p>No randomness, no {@link System#currentTimeMillis()}, no I/O. The seed hash
 * is a djb2 variant over the UTF-8 bytes of the seed string, then a curated
 * Morandi palette table and a small mood table are indexed by the hash.
 */
public final class CapsuleIdentityUtils {

    private CapsuleIdentityUtils() {
    }

    /** Number of curated Morandi palettes. */
    public static final int PALETTE_COUNT = 12;
    /** Number of curated moods. */
    public static final int MOOD_COUNT = 10;

    // Curated Morandi-day / warm-night palette table (soft, desaturated hues).
    // Each row: { primary, secondary, border, glowR, glowG, glowB }
    private static final String[][] PALETTES = {
            {"#8FA994", "#C8D4C4", "#6B8974", "143", "169", "148"},   // 0 sage green
            {"#A89BB8", "#D4CCDE", "#897BA0", "168", "155", "184"},   // 1 mauve
            {"#8B9DA8", "#C4D0D8", "#6B7D88", "139", "157", "168"},   // 2 slate blue
            {"#B8A89B", "#D8CFC4", "#98887B", "184", "168", "155"},   // 3 warm taupe
            {"#9CB0A2", "#CCD8CE", "#7C9082", "156", "176", "162"},   // 4 eucalyptus
            {"#B89B9B", "#DECBCB", "#987B7B", "184", "155", "155"},   // 5 dusty rose
            {"#9BA8B8", "#CBD4DE", "#7B8898", "155", "168", "184"},   // 6 periwinkle
            {"#A8A089", "#D0CABC", "#88806B", "168", "160", "137"},   // 7 olive sand
            {"#8BA8A0", "#C0D4CC", "#6B887F", "139", "168", "160"},   // 8 seafoam
            {"#B09BA8", "#D4C8D0", "#907B88", "176", "155", "168"},   // 9 heather
            {"#A89B95", "#D0C8C2", "#887B75", "168", "155", "149"},   //10 mushroom
            {"#9FA8B0", "#CBD2D8", "#7F8890", "159", "168", "176"}    //11 silver blue
    };

    // Mood table aligned with the JS mood labels.
    private static final String[] MOODS = {
            "steady",        // 0
            "curious",       // 1
            "dreamy",        // 2
            "contemplative", // 3
            "gentle",        // 4
            "intimate",      // 5
            "patient",       // 6
            "hopeful",       // 7
            "quiet",         // 8
            "warm"           // 9
    };

    /**
     * djb2-style deterministic hash over the UTF-8 bytes of the seed string.
     * Stable across runs and platforms; identical to the JS implementation.
     */
    public static long hashSeed(String seed) {
        if (seed == null) {
            return 5381L;
        }
        long hash = 5381L;
        for (int i = 0; i < seed.length(); i++) {
            int c = seed.charAt(i) & 0xFFFF;
            // Encode code points < 0x10000 as a single char; for BMP this is UTF-16 == UTF-8 byte sum
            // For determinism we sum the UTF-8 bytes of the char (JS encodeURIComponent-style not used;
            // JS mirrors this exact byte expansion).
            if (c < 0x80) {
                hash = ((hash << 5) + hash) + c;
            } else if (c < 0x800) {
                hash = ((hash << 5) + hash) + (0xC0 | (c >> 6));
                hash = ((hash << 5) + hash) + (0x80 | (c & 0x3F));
            } else {
                hash = ((hash << 5) + hash) + (0xE0 | (c >> 12));
                hash = ((hash << 5) + hash) + (0x80 | ((c >> 6) & 0x3F));
                hash = ((hash << 5) + hash) + (0x80 | (c & 0x3F));
            }
        }
        // Normalize to unsigned 32-bit range, matching JS bitwise | 0 wraparound.
        return hash & 0xFFFFFFFFL;
    }

    private static int index(long hash, int modulus, int salt) {
        // Spread bits so palette and mood indices are decorrelated.
        long mixed = (hash * 2654435761L + salt * 0x9E3779B1L) & 0xFFFFFFFFL;
        return (int) (mixed % modulus);
    }

    /** Index into the curated palette table for a given seed. */
    public static int paletteIndex(String seed) {
        return index(hashSeed(seed), PALETTE_COUNT, 1);
    }

    /** Index into the curated mood table for a given seed. */
    public static int moodIndex(String seed) {
        return index(hashSeed(seed), MOOD_COUNT, 2);
    }

    /** Primary Morandi color for the seed (e.g. {@code --capsule-color}). */
    public static String primaryColor(String seed) {
        return PALETTES[paletteIndex(seed)][0];
    }

    /** Secondary Morandi color for the seed. */
    public static String secondaryColor(String seed) {
        return PALETTES[paletteIndex(seed)][1];
    }

    /** Border color for the seed. */
    public static String borderColor(String seed) {
        return PALETTES[paletteIndex(seed)][2];
    }

    /** Glow color (rgba string) for the seed. */
    public static String glowColor(String seed) {
        String[] p = PALETTES[paletteIndex(seed)];
        return "rgba(" + p[3] + ", " + p[4] + ", " + p[5] + ", 0.3)";
    }

    /** Linear gradient background string for the seed. */
    public static String background(String seed) {
        return "linear-gradient(135deg, " + primaryColor(seed) + " 0%, " + secondaryColor(seed) + " 100%)";
    }

    /** Mood key for the seed (maps to a Chinese label on the JS side). */
    public static String mood(String seed) {
        return MOODS[moodIndex(seed)];
    }

    /**
     * Number of star points in the deterministic SVG avatar (4..8), derived
     * from the seed. Mirrored exactly by the JS implementation.
     */
    public static int starPoints(String seed) {
        return 4 + index(hashSeed(seed), 5, 3);
    }

    /**
     * Resolve the effective seed for a capsule given an optional numeric id and
     * its pseudonym. Prefer the numeric id (stable across renames); fall back
     * to the pseudonym string when only that is available.
     */
    public static String resolveSeed(Long id, String pseudonym) {
        if (id != null) {
            return "id:" + id;
        }
        return pseudonym == null ? "" : pseudonym;
    }

    /**
     * Resolve the effective seed when the id arrives as an arbitrary object
     * (the JS side may pass a number or numeric string). Returns the
     * pseudonym string when the id is null/empty/non-numeric.
     */
    public static String resolveSeed(Object id, String pseudonym) {
        Long parsed = null;
        if (id != null) {
            String s = id.toString().trim();
            if (!s.isEmpty()) {
                try {
                    parsed = Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                    parsed = null;
                }
            }
        }
        return resolveSeed(parsed, pseudonym);
    }
}
