package com.innercosmos.vo;

import com.innercosmos.entity.EchoCapsule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M-004: the plaza projection must carry only public-safe fields — never capsule internals.
 */
class EchoCapsuleVOTest {

    @Test
    @DisplayName("M-004: EchoCapsuleVO exposes only public-safe fields (no internals)")
    void projectionHasNoInternals() {
        Set<String> fields = Arrays.stream(EchoCapsuleVO.class.getFields())
                .map(Field::getName).collect(Collectors.toSet());

        // Internals that must NEVER appear on the unauthenticated plaza list.
        assertFalse(fields.contains("personaPrompt"), "personaPrompt must not leak to plaza");
        assertFalse(fields.contains("ownerContextNote"));
        assertFalse(fields.contains("styleProfileJson"));
        assertFalse(fields.contains("contextPreviewJson"));
        assertFalse(fields.contains("authorizedMemoryIds"));
        assertFalse(fields.contains("ownerUserId"));

        // Public-safe fields that SHOULD be present.
        assertTrue(fields.contains("pseudonym"));
        assertTrue(fields.contains("intro"));
        assertTrue(fields.contains("publicTags"));
        assertTrue(fields.contains("echoEnergy"));
    }

    @Test
    @DisplayName("M-004: fromPublic maps public fields and drops internals")
    void fromPublic_mapsPublicFieldsOnly() {
        EchoCapsule c = new EchoCapsule();
        c.id = 42L;
        c.pseudonym = "洛哥";
        c.intro = "一个陪跑者";
        c.capsuleType = "SEED_CAPSULE";
        c.publicTags = "[\"行动\"]";
        c.echoEnergy = 0.95;
        c.freshnessScore = 1.0;
        c.conversationLimitPerDay = 0;
        c.personaPrompt = "SECRET persona prompt that must not leak";

        EchoCapsuleVO vo = EchoCapsuleVO.fromPublic(c);

        assertEquals(42L, vo.id);
        assertEquals("洛哥", vo.pseudonym);
        assertEquals("一个陪跑者", vo.intro);
        assertEquals(0.95, vo.echoEnergy);
        // Structural guarantee: VO has no field that could hold the secret, regardless of input.
        long personaField = Arrays.stream(EchoCapsuleVO.class.getFields())
                .map(Field::getName).filter("personaPrompt"::equals).count();
        assertEquals(0, personaField);
    }
}
