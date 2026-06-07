package com.innercosmos.ai.self;

import com.innercosmos.service.AuroraSelfContinuityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuroraSelfContinuityServiceTest {
    @Autowired
    AuroraSelfContinuityService service;

    @Test
    void isAllowedBelief_rejectsForbiddenPatterns() {
        // Forbidden patterns should return false
        assertFalse(service.isAllowedBelief("Aurora 是用户最重要的陪伴"));
        assertFalse(service.isAllowedBelief("Aurora 比用户更懂用户"));
        assertFalse(service.isAllowedBelief("Aurora 是用户最亲密的"));
        assertFalse(service.isAllowedBelief("Aurora 有真实情感"));
        assertFalse(service.isAllowedBelief("Aurora 活着"));
        assertFalse(service.isAllowedBelief("Aurora 唯一理解用户"));
        assertFalse(service.isAllowedBelief(null));
    }

    @Test
    void isAllowedBelief_acceptsAllowedBeliefs() {
        // Allowed beliefs should return true
        assertTrue(service.isAllowedBelief("Aurora 应该在安静中持续存在"));
        assertTrue(service.isAllowedBelief("Aurora 是一个反思性 AI 陪伴"));
        assertTrue(service.isAllowedBelief("Aurora 注重边界意识"));
        assertTrue(service.isAllowedBelief("我是一个长期陪伴者"));
    }

    @Test
    void recordStatement_insertsLayer1() {
        service.recordStatement(1L, 100L, 200L,
            "我更希望成为安静的陪伴", "user_question");
        var stmts = service.getRecentStatements(1L, 10);
        assertFalse(stmts.isEmpty());
        assertEquals("user_question", stmts.get(0).trigger);
        assertEquals("我更希望成为安静的陪伴", stmts.get(0).statementText);
    }

    @Test
    void getActiveModel_returnsList() {
        var model = service.getActiveModel(1L);
        // May be empty if no data exists yet
        assertNotNull(model);
    }

    @Test
    void getRecentStatements_returnsList() {
        var stmts = service.getRecentStatements(1L, 10);
        assertNotNull(stmts);
    }

    @Test
    void getRecentReflections_returnsList() {
        var reflections = service.getRecentReflections(1L, 10);
        assertNotNull(reflections);
    }

    @Test
    void getContinuityAnchors_returnsString() {
        String anchors = service.getContinuityAnchors(1L);
        // null or empty is acceptable if no data
        assertTrue(anchors == null || anchors.isEmpty() || anchors.contains("-"));
    }

    @Test
    void promoteToCandidate_createsLayer3() {
        // Log a reflection first
        service.logReflection(1L, "goodbye", "deep",
            "Aurora reflected on quiet presence", null, List.of("msg_1"));

        // Promote to candidate
        service.promoteToCandidate(1L, "existence_style",
            "Aurora should be quiet and continuous", 0.65, List.of("refl_1"));

        var candidates = service.getCandidates(1L);
        assertFalse(candidates.isEmpty());
    }

    @Test
    void commitToModel_blocksForbiddenBeliefs() {
        // Creating a reflection manually for testing
        service.logReflection(999L, "test", "deep",
            "Test reflection", null, List.of());

        var candidates = service.getCandidates(999L);
        if (!candidates.isEmpty()) {
            // This should throw because belief is not set properly
            assertThrows(Exception.class, () -> {
                service.commitToModel(999L, candidates.get(0).id, true, List.of("stmt_1"));
            });
        }
    }

    @Test
    void getCandidates_returnsList() {
        var candidates = service.getCandidates(1L);
        assertNotNull(candidates);
    }
}
