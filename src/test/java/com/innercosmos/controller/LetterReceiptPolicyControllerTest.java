package com.innercosmos.controller;

import com.innercosmos.scheduler.LetterDeliveryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-33 §2-6 HTTP-layer contract: read receipts are the RECIPIENT's opt-in choice.
 *
 * <p>Privacy default NEVER: once the recipient marks a letter read, the sender's outbox and
 * letter-detail views still present DELIVERED -- no read state, no readAt, and never the
 * recipient's preference itself. ALWAYS is the only way the sender ever sees READ. Only the
 * receiver can flip the switch (sender 403, third party 401), switching after the read takes
 * effect immediately, and a letter whose scheduled arrival (server UTC口径) has not come yet
 * is unreadable by its recipient through every path -- including the delivery scheduler's own
 * boundary, which must not drift across time zones.
 *
 * <p>Test parties use the dedicated large id segment 97xxxxxxx so they can never collide with
 * demo/seeded accounts.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.datasource.url=jdbc:h2:mem:testletterreceiptcp33;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class LetterReceiptPolicyControllerTest {

    private static final long SENDER_ID = 970000011L;
    private static final long RECEIVER_ID = 970000012L;
    private static final long OUTSIDER_ID = 970000013L;
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LetterDeliveryJob deliveryJob;

    private final MockHttpSession senderSession = session(SENDER_ID);
    private final MockHttpSession receiverSession = session(RECEIVER_ID);
    private final MockHttpSession outsiderSession = session(OUTSIDER_ID);

    private static MockHttpSession session(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("LOGIN_USER_ID", userId);
        return session;
    }

    @BeforeEach
    void seedParties() {
        // Real ACTIVE rows so SessionAuthenticationFilter bridges the session into Spring
        // Security for /api/** (authenticated). Fixed 97xxxxxxx ids -- the dedicated test range.
        insertUser(SENDER_ID, "cp33_receipt_sender");
        insertUser(RECEIVER_ID, "cp33_receipt_receiver");
        insertUser(OUTSIDER_ID, "cp33_receipt_outsider");
    }

    private void insertUser(long id, String username) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_user WHERE id = ?", Integer.class, id);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update("INSERT INTO tb_user (id, username, password_hash, nickname, role, status) "
                        + "VALUES (?, ?, ?, ?, 'USER', 'ACTIVE')",
                id, username + "_" + id, "not-a-real-hash", "CP-33 回执测试");
    }

    /** Inserts a letter sender -> receiver in the given status; a null policy row gets the
     *  table's NEVER default (COALESCE -- H2 rejects an explicit NULL against NOT NULL). */
    private long seedLetter(String status, LocalDateTime estimatedArrivalAt, String receiptPolicy) {
        jdbc.update("INSERT INTO tb_slow_letter (sender_user_id, receiver_user_id, title, letter_body, "
                        + "status, parallax_distance, estimated_arrival_at, receipt_policy) "
                        + "VALUES (?, ?, ?, ?, ?, 3, ?, COALESCE(?, 'NEVER'))",
                SENDER_ID, RECEIVER_ID, "回执测试信", "愿这封信的已读与否由收件人决定。", status,
                estimatedArrivalAt, receiptPolicy);
        return jdbc.queryForObject(
                "SELECT id FROM tb_slow_letter WHERE sender_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, SENDER_ID);
    }

    private long seedLetter(String status, LocalDateTime estimatedArrivalAt) {
        return seedLetter(status, estimatedArrivalAt, null);
    }

    private String senderOutboxStatus(long letterId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/letters/outbox").session(senderSession))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int index = body.indexOf("\"id\":" + letterId + ",");
        assertTrue(index >= 0, "sender outbox must contain letter " + letterId + ": " + body);
        int statusKey = body.indexOf("\"senderStatus\":\"", index);
        int start = statusKey + "\"senderStatus\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    @DisplayName("privacy default NEVER: a read letter still presents DELIVERED to the sender")
    void defaultNeverHidesTheReadStateFromTheSender() throws Exception {
        long letterId = seedLetter("DELIVERED", pastArrival());

        mockMvc.perform(post("/api/letters/" + letterId + "/read").session(receiverSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        // The recipient's own view keeps the honest READ state...
        mockMvc.perform(get("/api/letters/" + letterId).session(receiverSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty())
                .andExpect(jsonPath("$.data.receiptPolicy").value("NEVER"));

        // ...while the sender learns nothing: outbox keeps DELIVERED, no readAt, no preference.
        assertEquals("DELIVERED", senderOutboxStatus(letterId));
        mockMvc.perform(get("/api/letters/" + letterId).session(senderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.readAt").doesNotExist())
                .andExpect(jsonPath("$.data.receiptPolicy").doesNotExist());
    }

    @Test
    @DisplayName("ALWAYS: opting in is the only way the sender ever sees READ")
    void alwaysDisclosesTheReadReceipt() throws Exception {
        long letterId = seedLetter("DELIVERED", pastArrival());

        mockMvc.perform(patch("/api/letters/" + letterId + "/receipt-policy").session(receiverSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"ALWAYS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptPolicy").value("ALWAYS"));

        mockMvc.perform(post("/api/letters/" + letterId + "/read").session(receiverSession))
                .andExpect(status().isOk());

        assertEquals("READ", senderOutboxStatus(letterId));
        mockMvc.perform(get("/api/letters/" + letterId).session(senderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    @DisplayName("switching the policy after the read takes effect on the sender's next view")
    void recipientSwitchingAfterReadTakesEffect() throws Exception {
        long letterId = seedLetter("DELIVERED", pastArrival());
        mockMvc.perform(patch("/api/letters/" + letterId + "/receipt-policy").session(receiverSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"ALWAYS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/letters/" + letterId + "/read").session(receiverSession))
                .andExpect(status().isOk());
        assertEquals("READ", senderOutboxStatus(letterId));

        // Withdraw the receipt: the sender's view falls back to DELIVERED -- no faked state,
        // just the recipient's current choice about what may be disclosed.
        mockMvc.perform(patch("/api/letters/" + letterId + "/receipt-policy").session(receiverSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"NEVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptPolicy").value("NEVER"));
        assertEquals("DELIVERED", senderOutboxStatus(letterId));

        // Re-allow it: the same honest READ (with its original readAt) is disclosed again.
        mockMvc.perform(patch("/api/letters/" + letterId + "/receipt-policy").session(receiverSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"ALWAYS\"}"))
                .andExpect(status().isOk());
        assertEquals("READ", senderOutboxStatus(letterId));
    }

    @Test
    @DisplayName("only the recipient may change the preference: sender 403, outsider 401, bad value 400")
    void receiptPolicyIsTheReceiversChoiceAlone() throws Exception {
        long letterId = seedLetter("DELIVERED", pastArrival());
        String url = "/api/letters/" + letterId + "/receipt-policy";

        // The sender cannot change it...
        mockMvc.perform(patch(url).session(senderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"ALWAYS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // ...and the column still holds the recipient's untouched default.
        assertEquals("NEVER", dbPolicy(letterId));

        // A third party cannot even reach it.
        mockMvc.perform(patch(url).session(outsiderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"ALWAYS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // The receiver must pick a real policy value.
        mockMvc.perform(patch(url).session(receiverSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"SOMETIMES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(patch(url).session(receiverSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptPolicy\":\"always\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptPolicy").value("ALWAYS"));
    }

    @Test
    @DisplayName("a letter that has not arrived yet is unreadable by its recipient (early access rejected)")
    void preArrivalLetterIsUnreadableByRecipient() throws Exception {
        long letterId = seedLetter("SENT", utcNow().plusHours(1));

        // Detail view: sealed until arrival.
        mockMvc.perform(get("/api/letters/" + letterId).session(receiverSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LETTER_STATE_INVALID"));

        // Direct READ transition: the state machine only allows DELIVERED -> READ, and only
        // the scheduler may advance to DELIVERED, so an early read is refused.
        mockMvc.perform(post("/api/letters/" + letterId + "/read").session(receiverSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LETTER_STATE_INVALID"));

        // The scheduler has not delivered it either -- arrival is still in the future.
        deliveryJob.deliverArrivedLetters();
        deliveryJob.deliverArrivedLetters();
        assertEquals("FLYING", dbStatus(letterId));
        assertEquals("FLYING", senderOutboxStatus(letterId));

        // Once the server-UTC arrival moment passes, the same paths open honestly.
        jdbc.update("UPDATE tb_slow_letter SET estimated_arrival_at = ? WHERE id = ?",
                utcNow().minusSeconds(1), letterId);
        deliveryJob.deliverArrivedLetters();
        deliveryJob.deliverArrivedLetters();
        assertEquals("DELIVERED", dbStatus(letterId));
        mockMvc.perform(get("/api/letters/" + letterId).session(receiverSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
        mockMvc.perform(post("/api/letters/" + letterId + "/read").session(receiverSession))
                .andExpect(status().isOk());
        assertEquals("DELIVERED", senderOutboxStatus(letterId), "default NEVER still masks the read");
    }

    @Test
    @DisplayName("TONIGHT in a far-west zone is decided on the server's UTC口径 -- no drift at the boundary")
    void tonightInAFarWesternZoneIsDecidedInUtc() throws Exception {
        // Compose and send a real TONIGHT letter with an America/New_York delivery intent.
        Instant before = Instant.now();
        MvcResult draft = mockMvc.perform(post("/api/letters/draft").session(senderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\":" + RECEIVER_ID + ",\"title\":\"跨时区\","
                                + "\"letterBody\":\"愿它在正确的时刻抵达。\","
                                + "\"deliveryPreset\":\"TONIGHT\",\"timeZone\":\"America/New_York\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long letterId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(draft.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        Instant after = Instant.now();

        mockMvc.perform(post("/api/letters/" + letterId + "/send").session(senderSession))
                .andExpect(status().isOk());

        // The persisted arrival must equal "21:00 New York" of whichever side of that local
        // bedtime the send instant fell on -- computed independently here in UTC. If the server
        // had drifted onto its default zone (Asia/Shanghai) the value would differ by ~12h.
        LocalDateTime persisted = jdbc.queryForObject(
                "SELECT scheduled_arrival_at FROM tb_slow_letter WHERE id = ?",
                LocalDateTime.class, letterId);
        List<LocalDateTime> acceptable = List.of(tonightUtc(before), tonightUtc(after));
        assertTrue(acceptable.contains(persisted),
                "scheduled_arrival_at " + persisted + " must be the New-York 21:00 UTC instant "
                        + acceptable + " (server UTC口径, sender zone honored)");

        // And the delivery decision compares that same UTC value against the job's UTC now:
        // strictly before it -> still FLYING (the scheduler has not run yet in this test).
        assertTrue(persisted.isAfter(utcNow()),
                "a tonight letter in New York must still be in the future on the server's UTC clock");
        assertEquals("SENT", dbStatus(letterId));
        deliveryJob.deliverArrivedLetters();
        deliveryJob.deliverArrivedLetters();
        assertEquals("FLYING", dbStatus(letterId),
                "not yet DELIVERED -- the UTC arrival moment has not come, regardless of the sender zone");
    }

    // ---------------- helpers ----------------

    private String dbStatus(long letterId) {
        return jdbc.queryForObject("SELECT status FROM tb_slow_letter WHERE id = ?",
                String.class, letterId);
    }

    private String dbPolicy(long letterId) {
        return jdbc.queryForObject("SELECT receipt_policy FROM tb_slow_letter WHERE id = ?",
                String.class, letterId);
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    private static LocalDateTime pastArrival() {
        return utcNow().minusMinutes(5);
    }

    /** "Tonight 21:00" in New York, expressed as the UTC LocalDateTime the server persists. */
    private static LocalDateTime tonightUtc(Instant at) {
        ZonedDateTime localNow = at.atZone(NEW_YORK);
        ZonedDateTime candidate = localNow.toLocalDate().atTime(21, 0).atZone(NEW_YORK);
        if (!candidate.toInstant().isAfter(at)) {
            candidate = candidate.plusDays(1);
        }
        return LocalDateTime.ofInstant(candidate.toInstant(), ZoneOffset.UTC);
    }
}
