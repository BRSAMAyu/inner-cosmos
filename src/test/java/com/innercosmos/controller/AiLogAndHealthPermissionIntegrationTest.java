package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.AiLogService;
import com.innercosmos.vo.AiHealthVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini audit / remaining-work-handoff.md 2.2.5: corrects the mismatch between a comment in
 * AuroraApp.tsx claiming both /api/ai-logs and /api/ai/health were already requireAdmin-gated,
 * and the actual code -- which only ever called currentUserId(session) for both. This pins the
 * corrected real contract: /api/ai-logs is now admin-only and system-wide (its only caller,
 * AdminAiLogsTab.tsx, needs to see every user's interactions, not just the admin's own); /api/ai/
 * health stays open to any authenticated user (ThoughtShredderSection needs it for regular
 * users), but its "last call" detail is now scoped to the caller instead of leaking whichever row
 * was globally most recent.
 */
@SpringBootTest(properties = { "llm.mode=dev", "llm.provider=mock", "llm.allow-fallback=true" })
@Transactional
class AiLogAndHealthPermissionIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiLogController aiLogController;
    @Autowired private AiHealthController aiHealthController;
    @Autowired private AiLogService aiLogService;

    private Long seedUser(String usernamePrefix, String role) {
        String username = usernamePrefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", role, "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private MockHttpSession sessionFor(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, userId);
        return session;
    }

    @Test
    void aiLogsRejectsAnOrdinaryUser() {
        Long ordinary = seedUser("ordinary", "USER");
        BusinessException error = assertThrows(BusinessException.class,
                () -> aiLogController.list(null, null, null, sessionFor(ordinary)));
        assertEquals("UNAUTHORIZED", error.code);
    }

    @Test
    void aiLogsShowsEveryUsersInteractionsToAnAdminNotJustTheAdminsOwn() {
        Long admin = seedUser("admin", "ADMIN");
        Long someoneElse = seedUser("someone-else", "USER");
        aiLogService.record(someoneElse, "AURORA", "prompt", "response", true, 10L);

        ApiResponse<List<AiInteractionLog>> response = aiLogController.list(null, null, null, sessionFor(admin));

        assertTrue(response.data.stream().anyMatch(row -> someoneElse.equals(row.userId)),
                "admin must see another user's AI interaction, not only their own");
    }

    @Test
    void aiHealthIsReachableByAnOrdinaryUserNotJustAdmin() {
        Long ordinary = seedUser("ordinary2", "USER");
        ApiResponse<AiHealthVO> response = aiHealthController.health(sessionFor(ordinary));
        assertNotNull(response.data);
    }

    @Test
    void aiHealthsLastCallIsScopedToTheCallerNotGloballyMostRecent() {
        Long userA = seedUser("user-a", "USER");
        Long userB = seedUser("user-b", "USER");
        // userB is the globally most recent AI interaction; userA has none of their own.
        aiLogService.record(userB, "CAPSULE_CHAT", "p", "r", true, 5L);

        AiHealthVO healthForA = aiHealthController.health(sessionFor(userA)).data;
        assertNull(healthForA.lastModule, "userA has no AI interactions of their own -- must not see userB's");

        aiLogService.record(userA, "AURORA", "p2", "r2", true, 7L);
        AiHealthVO healthForAAfterOwnCall = aiHealthController.health(sessionFor(userA)).data;
        assertEquals("AURORA", healthForAAfterOwnCall.lastModule);
    }
}
