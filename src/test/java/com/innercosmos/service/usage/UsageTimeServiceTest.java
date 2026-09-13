package com.innercosmos.service.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * CP-08 使用时长: today's conversation presence summed from real completed/partial turns,
 * anchored to the same Shanghai day as the metric store, with ONE calm reminder flag at
 * the configured threshold — no lockout, no streak, no judgemental copy, and no
 * fabricated time (cancelled or in-flight turns count for nothing).
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class UsageTimeServiceTest {

    private static final AtomicLong USERS = new AtomicLong(92_800_000);

    @Autowired UsageTimeService usageTime;
    @Autowired ConversationTurnMapper turns;
    @Autowired UserMapper userMapper;
    @Autowired com.innercosmos.mapper.DialogSessionMapper sessions;
    @Autowired WebApplicationContext context;

    private long human() {
        long id = USERS.incrementAndGet();
        com.innercosmos.entity.User user = new com.innercosmos.entity.User();
        user.id = id;
        user.username = "cp08-" + id;
        user.passwordHash = "x";
        user.nickname = "cp08";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        userMapper.insert(user);
        // tb_conversation_turn has an FK to tb_dialog_session -- one notional session.
        com.innercosmos.entity.DialogSession session = new com.innercosmos.entity.DialogSession();
        session.userId = id;
        session.title = "cp08 session";
        session.sessionType = "COMPANION";
        session.status = "ACTIVE";
        session.messageCount = 0;
        session.tokenEstimate = 0;
        sessions.insert(session);
        SESSIONS.put(id, session.id);
        return id;
    }

    private static final java.util.Map<Long, Long> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private long sessionId(long user) {
        return SESSIONS.get(user);
    }

    private static final AtomicLong MESSAGES = new AtomicLong(928_000_000);

    /** user_message_id also carries an FK -- seed the referenced dialog message row. */
    private long messageRow(long user) {
        com.innercosmos.entity.DialogMessage message = new com.innercosmos.entity.DialogMessage();
        message.sessionId = sessionId(user);
        message.userId = user;
        message.speaker = "USER";
        message.textContent = "cp08 fixture";
        message.inputType = "TEXT";
        messages.insert(message);
        return message.id;
    }

    @Autowired com.innercosmos.mapper.DialogMessageMapper messages;

    private void turn(long user, String status, int startMinutesAgo, int durationMinutes) {
        ConversationTurn turn = new ConversationTurn();
        turn.userId = user;
        turn.sessionId = sessionId(user);
        turn.userMessageId = messageRow(user); // UNIQUE(user_message_id) + real FK target
        turn.status = status;
        turn.startedAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(startMinutesAgo);
        turn.completedAt = turn.startedAt.plusMinutes(durationMinutes);
        turns.insert(turn);
    }

    @Test
    void sumsOnlyFinishedPresenceAndKeepsQuietBelowTheThreshold() {
        long user = human();
        turn(user, "COMPLETED", 120, 10);
        turn(user, "PARTIAL", 60, 5);
        turn(user, "CANCELLED", 90, 30);   // cancelled: no finished presence
        turn(user, "COMPLETED", 10_000_000, 30); // far past: different day entirely

        UsageTimeService.UsageToday usage = usageTime.usageToday(user);
        assertEquals(15 * 60, usage.activeSeconds(), "10 + 5 minutes of real presence");
        assertEquals(2, usage.turnCount());
        assertFalse(usage.reminderDue());
        assertEquals(null, usage.reminderNote());
    }

    @Test
    void gentleReminderFiresOncePastThresholdWithCalmCopy() {
        long user = human();
        turn(user, "COMPLETED", 100, 46);

        UsageTimeService.UsageToday usage = usageTime.usageToday(user);
        assertTrue(usage.reminderDue());
        assertNotNull(usage.reminderNote());
        assertTrue(usage.reminderNote().contains("照顾好自己"),
                "calm, non-judgemental copy — a check-in, never a scolding");
        assertTrue(usage.reminderNote().contains("分钟"));
    }

    @Test
    void inFlightTurnsWithoutCompletionCountForNothing() {
        long user = human();
        ConversationTurn inFlight = new ConversationTurn();
        inFlight.userId = user;
        inFlight.sessionId = sessionId(user);
        inFlight.userMessageId = messageRow(user);
        inFlight.status = "GENERATING";
        inFlight.startedAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(20);
        turns.insert(inFlight);

        assertEquals(0, usageTime.usageToday(user).activeSeconds(),
                "no completion timestamp -> no fabricated time");
    }

    @Test
    void controllerServesTheHonestView() throws Exception {
        long user = human();
        turn(user, "COMPLETED", 30, 12);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(com.innercosmos.common.Constants.SESSION_USER_KEY, user);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/me/usage/today").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("activeSeconds"));
        assertTrue(body.contains("COMPLETED_TURN_DURATION"));
        assertTrue(body.contains("reminderAfterMinutes"));
    }

    @Test
    void dayBoundaryFollowsTheShanghaiAnchorNotUtcMidnight() {
        // A turn finishing at 00:30 Shanghai (16:30 UTC of the previous UTC day) belongs to
        // TODAY on the Shanghai anchor. Build it from the service's own anchor math.
        long user = human();
        LocalDateTime utcWindowStart = java.time.LocalDate.now(UsageTimeService.ANCHOR_ZONE)
                .atStartOfDay(UsageTimeService.ANCHOR_ZONE)
                .toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        ConversationTurn justAfterBoundary = new ConversationTurn();
        justAfterBoundary.userId = user;
        justAfterBoundary.sessionId = sessionId(user);
        justAfterBoundary.userMessageId = messageRow(user);
        justAfterBoundary.status = "COMPLETED";
        justAfterBoundary.startedAt = utcWindowStart.plusMinutes(5);
        justAfterBoundary.completedAt = utcWindowStart.plusMinutes(35);
        turns.insert(justAfterBoundary);

        assertEquals(30 * 60, usageTime.usageToday(user).activeSeconds(),
                "Shanghai-day boundary buckets this turn into today");
        assertEquals(ZoneId.of("Asia/Shanghai"), UsageTimeService.ANCHOR_ZONE);
    }
}
