package com.innercosmos.service.continuity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.DialogSummary;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.DialogSummaryMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CP-18 closing-checklist §2-13: the owner's withdrawal switch for VISIBLE cross-session
 * continuity. Positive: default open, provenance-labeled supply while on, toggle persists.
 * Negative: while off, the SUPPLY endpoint serves an explicit withdrawn marker with zero
 * prior material — but the honest FACTS underneath are never fabricated away, other users
 * are untouched, and recording-side services are unaffected by a display choice.
 *
 * Users live in the independent 93xxxxxxx id segment reserved for this batch.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "spring.datasource.url=jdbc:h2:mem:cp18vis;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class SessionContinuityVisibilityToggleTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final AtomicLong USER_SEQ = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SessionContinuityService continuity;
    @Autowired
    private DialogSessionMapper sessionMapper;
    @Autowired
    private DialogSummaryMapper summaryMapper;

    @Test
    void visibilityDefaultsOpenAndTogglePersistsPerOwner() throws Exception {
        MockHttpSession owner = register();
        MockHttpSession other = register();

        mockMvc.perform(get("/api/dialog/session/continuity/visibility").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(true));

        mockMvc.perform(put("/api/dialog/session/continuity/visibility").session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingVisible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(false));

        // Persists for the owner across a fresh read…
        mockMvc.perform(get("/api/dialog/session/continuity/visibility").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(false));
        // …and never leaks to another user (owner-only semantics).
        mockMvc.perform(get("/api/dialog/session/continuity/visibility").session(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(true));

        // Re-enable is the same path, honestly symmetric.
        mockMvc.perform(put("/api/dialog/session/continuity/visibility").session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingVisible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(true));
    }

    @Test
    void toggleRequiresAnExplicitBoolean() throws Exception {
        MockHttpSession owner = register();
        mockMvc.perform(put("/api/dialog/session/continuity/visibility").session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whileOnTheSupplyEndpointServesTheHonestCarryForward() throws Exception {
        MockHttpSession owner = register();
        Long userId = userIdOf(owner);
        seedRealPriorConversation(userId);

        mockMvc.perform(get("/api/dialog/session/continuity").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(true))
                .andExpect(jsonPath("$.data.hasPrior").value(true))
                .andExpect(jsonPath("$.data.carryForward[0].kind").value("PRIOR_SUMMARY"))
                .andExpect(jsonPath("$.data.carryForward[0].provenance").isNotEmpty());
    }

    @Test
    void whileOffTheSupplyEndpointServesTheWithdrawnMarkerNotThePriorMaterial() throws Exception {
        MockHttpSession owner = register();
        Long userId = userIdOf(owner);
        DialogSession prior = seedRealPriorConversation(userId);

        mockMvc.perform(put("/api/dialog/session/continuity/visibility").session(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingVisible\":false}")).andExpect(status().isOk());

        // Negative: ZERO prior material is supplied — no carry notes, no prior reference,
        // and an explicit openingVisible=false marker so "withdrawn" can never be mistaken
        // for a genuine first conversation.
        mockMvc.perform(get("/api/dialog/session/continuity").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingVisible").value(false))
                .andExpect(jsonPath("$.data.hasPrior").value(false))
                .andExpect(jsonPath("$.data.carryForward").isEmpty())
                .andExpect(jsonPath("$.data.priorSessionId").doesNotExist());

        // Honesty guard: the switch is a DISPLAY choice — the fact layer underneath still
        // knows the prior conversation happened. No fabricated "nothing ever happened".
        var facts = continuity.openingContext(userId);
        assertTrue(facts.hasPrior(), "facts must keep recording the prior conversation");
        assertTrue(facts.priorSessionId() != null && facts.priorSessionId().equals(prior.id));
        assertTrue(facts.carryForward().stream().anyMatch(note ->
                "PRIOR_SUMMARY".equals(note.kind())));

        // …and the durable summary row itself is untouched by the display switch.
        assertTrue(summaryMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                        .QueryWrapper<DialogSummary>().eq("session_id", prior.id)).size() >= 1,
                "the recorded summary must survive the visibility withdrawal");
    }

    // ---------------- helpers ----------------

    /** Independent user segment for this batch: usernames embed a 93xxxxxxx id. */
    private MockHttpSession register() throws Exception {
        String username = "cp18vis93" + String.format("%07d",
                93_000_000L + USER_SEQ.incrementAndGet() % 10_000_000L);
        String dob = LocalDate.now(SHANGHAI).minusYears(24)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        String registerJson = "{\"username\":\"" + username + "\",\"password\":\"password123\","
                + "\"nickname\":\"CP18 可见性\",\"dateOfBirth\":\"" + dob + "\",\"adultConfirmed\":true}";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            MvcResult login = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            session = (MockHttpSession) login.getRequest().getSession(false);
        }
        return session;
    }

    /** Resolves the logged-in user behind a session (LOGIN_USER_ID) to seed their prior conversation. */
    private Long userIdOf(MockHttpSession session) {
        return (Long) session.getAttribute("LOGIN_USER_ID");
    }

    private DialogSession seedRealPriorConversation(Long userId) {
        DialogSession prior = new DialogSession();
        prior.userId = userId;
        prior.title = "上次的事";
        prior.sessionType = "AURORA_CHAT";
        prior.status = "FINISHED";
        prior.startedAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
        prior.endedAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(2).plusHours(1);
        sessionMapper.insert(prior);

        DialogSummary summary = new DialogSummary();
        summary.sessionId = prior.id;
        summary.userId = userId;
        summary.summaryText = "用户谈到工作转向的犹豫，区分了'不想做'和'怕做不好'。";
        summary.keyTopics = "职业转换,自我评价";
        summary.emotionTone = "审慎";
        summary.messageCountAtSummary = 12;
        summaryMapper.insert(summary);
        return prior;
    }
}
