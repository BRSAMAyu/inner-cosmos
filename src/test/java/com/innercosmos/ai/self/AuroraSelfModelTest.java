package com.innercosmos.ai.self;

import com.innercosmos.entity.AuroraSelfModel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuroraSelfModelTest {

    @Test
    void constructor_createsInstanceWithAllFields() {
        AuroraSelfModel model = new AuroraSelfModel();
        model.id = 1L;
        model.userId = 100L;
        model.dimension = "existence_style";
        model.belief = "Aurora should be a quiet continuous presence";
        model.confidence = 0.75;
        model.evidenceRefs = "[\"stmt_001\", \"stmt_002\"]";
        model.status = "active";
        model.committedAt = LocalDateTime.now();
        model.revisionCount = 1;

        assertNotNull(model);
        assertEquals(1L, model.id);
        assertEquals(100L, model.userId);
        assertEquals("existence_style", model.dimension);
        assertEquals("Aurora should be a quiet continuous presence", model.belief);
        assertEquals(0.75, model.confidence);
        assertEquals("[\"stmt_001\", \"stmt_002\"]", model.evidenceRefs);
        assertEquals("active", model.status);
        assertEquals(1, model.revisionCount);
    }

    @Test
    void defaultValues_areNullUntilAssigned() {
        AuroraSelfModel model = new AuroraSelfModel();

        // Default values are null - database applies defaults on insert
        assertNull(model.status);
        assertNull(model.confidence);
        assertNull(model.revisionCount);
    }

    @Test
    void status_canBeSetToRetired() {
        AuroraSelfModel model = new AuroraSelfModel();
        model.status = "retired";

        assertEquals("retired", model.status);
    }

    @Test
    void status_canBeSetToCandidate() {
        AuroraSelfModel model = new AuroraSelfModel();
        model.status = "candidate";

        assertEquals("candidate", model.status);
    }
}
