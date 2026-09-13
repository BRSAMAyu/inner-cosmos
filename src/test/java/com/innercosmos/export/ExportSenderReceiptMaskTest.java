package com.innercosmos.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.innercosmos.entity.SlowLetter;
import com.innercosmos.mapper.SlowLetterMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-33: the sender's data export must not bypass the recipient's read-receipt choice.
 * The exported sent-letter record carries exactly the sender-facing semantics of the
 * outbox view: NEVER hides READ as DELIVERED and strips the read moment; the recipient's
 * preference itself is never exported; DECLINED/BLOCKED fold to CLOSED.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class ExportSenderReceiptMaskTest {

    private static final AtomicLong USERS = new AtomicLong(97_500_000);

    @Autowired UserDataExportService exports;
    @Autowired SlowLetterMapper slowLetterMapper;

    private Map<String, Object> exportedSentLetter(String status, String receiptPolicy) {
        long sender = USERS.incrementAndGet();
        SlowLetter letter = new SlowLetter();
        letter.senderUserId = sender;
        letter.receiverUserId = sender + 1;
        letter.title = "写给远方";
        letter.letterBody = "body";
        letter.status = status;
        letter.receiptPolicy = receiptPolicy;
        letter.readAt = "READ".equals(status) || "REPLIED".equals(status)
                ? LocalDateTime.now(java.time.ZoneOffset.UTC) : null;
        slowLetterMapper.insert(letter);
        var pkg = exports.build(sender);
        var section = pkg.sections().get("slowLettersSent");
        assertEquals(1, section.records().size());
        return section.records().get(0);
    }

    @Test
    void neverPolicyHidesReadStateAndThePreferenceItself() {
        Map<String, Object> record = exportedSentLetter("READ", "NEVER");
        assertEquals("DELIVERED", record.get("status"), "READ must surface as DELIVERED under NEVER");
        assertNull(record.get("readAt"), "the read moment is the recipient's private signal");
        assertFalse(record.containsKey("receiptPolicy"), "the preference is the recipient's setting");
        assertFalse(record.containsKey("originalStatus"), "no raw-status duplicate may bypass the mask");
    }

    @Test
    void nullPolicyBehavesAsNeverForPreExistingRows() {
        Map<String, Object> record = exportedSentLetter("READ", null);
        assertEquals("DELIVERED", record.get("status"));
        assertNull(record.get("readAt"));
    }

    @Test
    void alwaysPolicyDisclosesReadButStillNotThePreference() {
        Map<String, Object> record = exportedSentLetter("READ", "ALWAYS");
        assertEquals("READ", record.get("status"));
        assertNotNull(record.get("readAt"), "ALWAYS shares the read moment");
        assertFalse(record.containsKey("receiptPolicy"), "the preference itself stays private");
    }

    @Test
    void repliedStaysVisibleButItsReadMomentDoesNotUnderNever() {
        Map<String, Object> record = exportedSentLetter("REPLIED", "NEVER");
        assertEquals("REPLIED", record.get("status"), "a reply discloses itself");
        assertNull(record.get("readAt"), "but the read moment stays hidden under NEVER");
    }

    @Test
    void declinedAndBlockedFoldToClosedForTheSender() {
        assertEquals("CLOSED", exportedSentLetter("DECLINED", "ALWAYS").get("status"));
        assertEquals("CLOSED", exportedSentLetter("BLOCKED", "ALWAYS").get("status"));
    }
}
