package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.entity.Notification;
import com.innercosmos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RUN-006 closed-loop coverage for the notifications HTTP contract that the dashboard
 * strip now consumes (CapsuleSyncService writes SYNC_DONE/SYNC_FAILED; the UI reads them).
 * - GET /api/notifications requires auth
 * - GET returns unread items newest-first with the fields the frontend renders
 *   (id/type/title/body)
 * - POST /api/notifications/{id}/read clears the item from unread.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testnotification;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationService notificationService;

    private MockHttpSession session;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
        userId = (Long) session.getAttribute(Constants.SESSION_USER_KEY);
    }

    @Test
    void notifications_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unread_surfacesItemsWithRenderableFields() throws Exception {
        notificationService.notify(userId, "SYNC_DONE", "共鸣体已同步",
                "你授权的画像更新已同步到共鸣体。", 11L, "CAPSULE_SYNC");
        notificationService.notify(userId, "SYNC_FAILED", "共鸣体同步失败",
                "同步暂时失败，系统会自动重试。", 12L, "CAPSULE_SYNC");

        // Order between two same-instant inserts isn't asserted (created_at is second-grained);
        // the contract the dashboard relies on is that every unread item carries the fields it
        // renders (id/type/title/body).
        mockMvc.perform(get("/api/notifications").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].type").value(hasItem("SYNC_FAILED")))
                .andExpect(jsonPath("$.data[*].type").value(hasItem("SYNC_DONE")))
                .andExpect(jsonPath("$.data[*].title").value(hasItem("共鸣体同步失败")))
                .andExpect(jsonPath("$.data[*].body").value(hasItem("同步暂时失败，系统会自动重试。")))
                .andExpect(jsonPath("$.data[0].id").isNumber());
    }

    @Test
    void markRead_clearsItFromUnread() throws Exception {
        Notification n = notificationService.notify(userId, "SYNC_DONE", "共鸣体已同步",
                "你授权的画像更新已同步到共鸣体。", 13L, "CAPSULE_SYNC");

        mockMvc.perform(post("/api/notifications/" + n.id + "/read").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------------- helpers ----------------

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "notifuser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Notif Test\"}";

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession s = (MockHttpSession) regResult.getRequest().getSession(false);
        if (s == null) {
            String loginJson = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson))
                    .andExpect(status().isOk())
                    .andReturn();
            s = (MockHttpSession) loginResult.getRequest().getSession(false);
        }
        return s;
    }
}
