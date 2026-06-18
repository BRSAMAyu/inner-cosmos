package com.innercosmos.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic contract test for {@link CapsuleIdentityUtils}.
 *
 * <p>This is the source-of-truth test for the per-capsule visual-identity
 * algorithm. The pure JS twin in {@code static/js/capsule-personality.js}
 * MUST produce identical outputs.
 */
class CapsuleIdentityUtilsTest {

    // Determinism: same seed always yields identical palette + mood + avatar params
    @Test
    void sameSeedYieldsIdenticalIdentity() {
        String seed = "id:42";
        long h1 = CapsuleIdentityUtils.hashSeed(seed);
        long h2 = CapsuleIdentityUtils.hashSeed(seed);
        assertEquals(h1, h2, "hash must be stable");

        assertEquals(CapsuleIdentityUtils.primaryColor(seed), CapsuleIdentityUtils.primaryColor(seed));
        assertEquals(CapsuleIdentityUtils.mood(seed), CapsuleIdentityUtils.mood(seed));
        assertEquals(CapsuleIdentityUtils.starPoints(seed), CapsuleIdentityUtils.starPoints(seed));
    }

    // Determinism is stable across many invocations (no hidden state / time)
    @Test
    void hashIsStableAcrossManyInvocations() {
        long first = CapsuleIdentityUtils.hashSeed("id:777");
        for (int i = 0; i < 100; i++) {
            assertEquals(first, CapsuleIdentityUtils.hashSeed("id:777"),
                    "hash drifted on invocation " + i);
        }
    }

    // Distinct seeds must produce distinct identities (not a single default).
    // We assert palette OR mood differ across a spread of ids, and that the
    // full identity string is unique for the vast majority of distinct seeds.
    @RepeatedTest(value = 20, name = "distinctness-{currentRepetition}")
    void distinctSeedsProduceDistinctIdentities(RepetitionInfo info) {
        int n = 60;
        Set<String> identities = new HashSet<>();
        Set<Integer> palettes = new HashSet<>();
        Set<String> moods = new HashSet<>();
        for (long id = info.getCurrentRepetition(); id < info.getCurrentRepetition() + n; id++) {
            String seed = "id:" + id;
            identities.add(CapsuleIdentityUtils.primaryColor(seed)
                    + "|" + CapsuleIdentityUtils.secondaryColor(seed)
                    + "|" + CapsuleIdentityUtils.borderColor(seed)
                    + "|" + CapsuleIdentityUtils.glowColor(seed)
                    + "|" + CapsuleIdentityUtils.mood(seed)
                    + "|" + CapsuleIdentityUtils.starPoints(seed));
            palettes.add(CapsuleIdentityUtils.paletteIndex(seed));
            moods.add(CapsuleIdentityUtils.mood(seed));
        }
        // 60 seeds against 12 palettes / 10 moods: we should see real spread.
        assertTrue(palettes.size() >= 5, "expected palette spread, got " + palettes.size());
        assertTrue(moods.size() >= 4, "expected mood spread, got " + moods.size());
        // The composite identity must be unique for >half of the 60 seeds.
        assertTrue(identities.size() >= n / 2,
                "expected at least " + (n / 2) + " unique identities, got " + identities.size());
    }

    // Two specifically-chosen distinct seeds differ in their derived identity
    @Test
    void twoDistinctSeedsDiffer() {
        String a = "id:1";
        String b = "id:2";
        String idA = CapsuleIdentityUtils.primaryColor(a) + CapsuleIdentityUtils.mood(a);
        String idB = CapsuleIdentityUtils.primaryColor(b) + CapsuleIdentityUtils.mood(b);
        assertNotEquals(idA, idB, "seeds 1 and 2 collapsed to the same identity");
    }

    // Palette + mood values stay within the curated Morandi range (table sizes)
    @Test
    void paletteAndMoodStayWithinCuratedRange() {
        for (long id = 0; id < 200; id++) {
            String seed = "id:" + id;
            int p = CapsuleIdentityUtils.paletteIndex(seed);
            int m = CapsuleIdentityUtils.moodIndex(seed);
            assertTrue(p >= 0 && p < CapsuleIdentityUtils.PALETTE_COUNT,
                    "palette index out of range: " + p);
            assertTrue(m >= 0 && m < CapsuleIdentityUtils.MOOD_COUNT,
                    "mood index out of range: " + m);
            String primary = CapsuleIdentityUtils.primaryColor(seed);
            assertNotNull(primary);
            assertTrue(primary.startsWith("#") && primary.length() == 7,
                    "primary must be a #rrggbb hex: " + primary);
            int pts = CapsuleIdentityUtils.starPoints(seed);
            assertTrue(pts >= 4 && pts <= 8, "star points out of [4,8]: " + pts);
            String glow = CapsuleIdentityUtils.glowColor(seed);
            assertTrue(glow.startsWith("rgba(") && glow.endsWith("0.3)"),
                    "glow must be rgba(...,0.3): " + glow);
        }
    }

    // resolveSeed prefers numeric id; falls back to pseudonym when id is absent
    @Test
    void resolveSeedPrefersIdOverPseudonym() {
        // Numeric id wins, so renaming the pseudonym keeps the identity stable
        assertEquals("id:42", CapsuleIdentityUtils.resolveSeed(42L, "any-pseudonym"));
        // Null/empty id falls back to pseudonym string
        assertEquals("some-name", CapsuleIdentityUtils.resolveSeed((Long) null, "some-name"));
        // Object variants: numeric string and number both parse to the id
        assertEquals("id:42", CapsuleIdentityUtils.resolveSeed((Object) "42", "ignored"));
        assertEquals("id:42", CapsuleIdentityUtils.resolveSeed((Object) 42L, "ignored"));
        // Non-numeric object id falls back to pseudonym
        assertEquals("fallback", CapsuleIdentityUtils.resolveSeed((Object) "abc", "fallback"));
    }

    // Id-based identity is stable across pseudonym renames (rename-safety)
    @Test
    void idBasedIdentityIsStableAcrossPseudonymRenames() {
        String beforeRename = CapsuleIdentityUtils.primaryColor(CapsuleIdentityUtils.resolveSeed(99L, "旧名字"));
        String afterRename = CapsuleIdentityUtils.primaryColor(CapsuleIdentityUtils.resolveSeed(99L, "新名字"));
        assertEquals(beforeRename, afterRename,
                "id-based identity must not change when pseudonym is renamed");
    }
}
