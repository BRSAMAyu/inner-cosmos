package com.innercosmos.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden-vector contract test for {@link CapsuleIdentityUtils}.
 *
 * <p>Locks a FIXED table of {@code seed -> {paletteIndex, moodIndex, starPoints}}
 * values computed from the canonical Java implementation. The pure JS twin in
 * {@code static/js/capsule-personality.js} ({@code CapsulePersonality._index})
 * MUST produce identical outputs — that file carries the same golden vectors as
 * a traceability comment so drift is visible on diff.
 *
 * <p>The seeds below cover ASCII {@code id:N} inputs plus non-numeric pseudonym
 * fallbacks (multi-byte UTF-8), exercising both the hash and the index spread.
 * If any value below changes, Java and JS have drifted: re-derive from the Java
 * source-of-truth and update both sides together.
 */
class CapsuleIdentityUtilsGoldenVectorTest {

    /** A single golden vector: seed + expected palette/mood/star indices. */
    static final class Vector {
        final String seed;
        final int paletteIndex;
        final int moodIndex;
        final int starPoints;
        Vector(String seed, int paletteIndex, int moodIndex, int starPoints) {
            this.seed = seed;
            this.paletteIndex = paletteIndex;
            this.moodIndex = moodIndex;
            this.starPoints = starPoints;
        }
    }

    // Fixed golden table — computed from CapsuleIdentityUtils after the JS-salt
    // alignment (IC-VIS-001). Mirrors the comment block in capsule-personality.js.
    static Stream<Vector> goldenVectors() {
        return Stream.of(
                new Vector("id:0", 9, 6, 6),
                new Vector("id:1", 6, 7, 6),
                new Vector("id:2", 7, 2, 6),
                new Vector("id:7", 0, 9, 8),
                new Vector("id:42", 3, 4, 8),
                new Vector("id:100", 10, 5, 5),
                new Vector("id:9999", 9, 2, 7),
                new Vector("深夜写作者", 11, 8, 7),
                new Vector("风筝的线", 10, 1, 5)
        );
    }

    @ParameterizedTest(name = "golden[{0}] palette")
    @MethodSource("goldenVectors")
    void goldenPaletteIndex(Vector v) {
        assertEquals(v.paletteIndex, CapsuleIdentityUtils.paletteIndex(v.seed),
                "paletteIndex drift for seed: " + v.seed);
    }

    @ParameterizedTest(name = "golden[{0}] mood")
    @MethodSource("goldenVectors")
    void goldenMoodIndex(Vector v) {
        assertEquals(v.moodIndex, CapsuleIdentityUtils.moodIndex(v.seed),
                "moodIndex drift for seed: " + v.seed);
    }

    @ParameterizedTest(name = "golden[{0}] star")
    @MethodSource("goldenVectors")
    void goldenStarPoints(Vector v) {
        assertEquals(v.starPoints, CapsuleIdentityUtils.starPoints(v.seed),
                "starPoints drift for seed: " + v.seed);
    }

    // Belt-and-braces: assert the full (palette,mood,star) tuple in one place
    // so a single failing seed reports all three derived indices at once.
    @Test
    void goldenTupleTable() {
        goldenVectors().forEach(v -> {
            assertEquals(v.paletteIndex, CapsuleIdentityUtils.paletteIndex(v.seed),
                    "palette for " + v.seed);
            assertEquals(v.moodIndex, CapsuleIdentityUtils.moodIndex(v.seed),
                    "mood for " + v.seed);
            assertEquals(v.starPoints, CapsuleIdentityUtils.starPoints(v.seed),
                    "star for " + v.seed);
        });
    }
}
