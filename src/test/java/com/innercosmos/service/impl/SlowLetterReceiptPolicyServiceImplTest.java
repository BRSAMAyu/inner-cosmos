package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.dto.LetterDeliveryPreset;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.service.LetterSafetyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CP-33 §2-6: read receipts are the RECIPIENT's opt-in choice. These tests pin the service
 * contract with a fixed UTC clock and the dedicated large test-user id segment (97xxxxxxx):
 * the receiver alone sets the per-letter policy; the sender's views mask READ -> DELIVERED
 * under NEVER (default) and never see readAt or the policy itself; a letter that has not
 * arrived yet is unreadable by its recipient on every read path; and the scheduled arrival
 * is computed on the server's UTC口径 regardless of how far the sender's IANA zone sits from
 * the server default (Asia/Shanghai) -- no drift.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterReceiptPolicyServiceImplTest {

    /** Dedicated large test-user ids -- no overlap with demo/seeded accounts. */
    private static final long SENDER = 970000001L;
    private static final long RECEIVER = 970000002L;
    private static final long OUTSIDER = 970000003L;

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;

    private SlowLetterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry, guardAgent,
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper,
                blockRelationMapper, new PiiCredentialDetector(), CLOCK);
    }

    // ---------------- setReceiptPolicy: whose choice is it? ----------------

    @Test
    void onlyTheReceiverMaySetThePolicy() {
        SlowLetter letter = letter("READ", null);
        when(letterMapper.selectById(501L)).thenReturn(letter);
        when(letterMapper.update(any(), any())).thenReturn(1);

        SlowLetter updated = service.setReceiptPolicy(RECEIVER, 501L, "ALWAYS");

        assertEquals("ALWAYS", updated.receiptPolicy);
        // The receiver still sees their own TRUE state -- nothing about the lifecycle is faked.
        assertEquals("READ", updated.status);
    }

    @Test
    void senderChangingThePreferenceIsForbidden() {
        when(letterMapper.selectById(501L)).thenReturn(letter("DELIVERED", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.setReceiptPolicy(SENDER, 501L, "ALWAYS"));

        assertEquals(ErrorCode.FORBIDDEN, error.code);
    }

    @Test
    void aThirdPartyCannotTouchThePreference() {
        when(letterMapper.selectById(501L)).thenReturn(letter("DELIVERED", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.setReceiptPolicy(OUTSIDER, 501L, "ALWAYS"));

        assertEquals(ErrorCode.UNAUTHORIZED, error.code);
    }

    @Test
    void onlyAlwaysOrNeverAreAccepted() {
        SlowLetter letter = letter("DELIVERED", null);
        when(letterMapper.selectById(501L)).thenReturn(letter);

        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.setReceiptPolicy(RECEIVER, 501L, "SOMETIMES"));
        BusinessException blank = assertThrows(BusinessException.class,
                () -> service.setReceiptPolicy(RECEIVER, 501L, " "));
        BusinessException absent = assertThrows(BusinessException.class,
                () -> service.setReceiptPolicy(RECEIVER, 501L, null));

        assertEquals(ErrorCode.BAD_REQUEST, invalid.code);
        assertEquals(ErrorCode.BAD_REQUEST, blank.code);
        assertEquals(ErrorCode.BAD_REQUEST, absent.code);
    }

    @Test
    void policyValueIsNormalizedCaseInsensitivelyForTheReceiver() {
        SlowLetter letter = letter("DELIVERED", null);
        when(letterMapper.selectById(501L)).thenReturn(letter);
        when(letterMapper.update(any(), any())).thenReturn(1);

        assertEquals("NEVER", service.setReceiptPolicy(RECEIVER, 501L, " never ").receiptPolicy);
    }

    // ---------------- sender visibility under each policy ----------------

    @Test
    void defaultNeverMasksTheReadStateForTheSender() {
        // null policy = the persisted NOT NULL DEFAULT 'NEVER' (older rows / fresh letters).
        // Fresh instance per call: the mask mutates its (detached) argument, exactly as each
        // production selectById would hand back a fresh row.
        when(letterMapper.selectById(501L)).thenAnswer(inv -> letter("READ", null));

        SlowLetter senderView = service.getLetter(SENDER, 501L);

        assertEquals("DELIVERED", senderView.status, "NEVER keeps the DELIVERED presentation");
        assertNull(senderView.readAt, "the read moment is never disclosed under NEVER");
        assertNull(senderView.receiptPolicy, "the recipient's preference is not queryable by the sender");
        // The receiver still sees their own true state, read moment included.
        SlowLetter receiverView = service.getLetter(RECEIVER, 501L);
        assertEquals("READ", receiverView.status);
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), receiverView.readAt);
    }

    @Test
    void alwaysDisclosesTheReadReceiptToTheSender() {
        when(letterMapper.selectById(501L)).thenReturn(letter("READ", "ALWAYS"));

        SlowLetter senderView = service.getLetter(SENDER, 501L);

        assertEquals("READ", senderView.status);
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), senderView.readAt);
        // Even under ALWAYS the preference itself stays the recipient's private setting.
        assertNull(senderView.receiptPolicy);
    }

    @Test
    void repliedStaysVisibleUnderNeverButWithholdsTheReadMoment() {
        // A reply is the recipient's own affirmative act -- it cannot be hidden (the reply letter
        // itself arrives at the sender). But WHEN the original was read stays masked.
        when(letterMapper.selectById(501L)).thenAnswer(inv -> letter("REPLIED", null));

        SlowLetter senderView = service.getLetter(SENDER, 501L);

        assertEquals("REPLIED", senderView.status);
        assertNull(senderView.readAt);
    }

    @Test
    void senderDetailFoldsDeclinedAndBlockedToClosed() {
        // Same fold as the outbox list: a rejection must not reveal WHICH way it went,
        // on the detail endpoint too. The receiver's own view keeps the true state.
        when(letterMapper.selectById(501L)).thenAnswer(inv -> letter("DECLINED", "ALWAYS"));

        assertEquals("CLOSED", service.getLetter(SENDER, 501L).status);
        assertEquals("DECLINED", service.getLetter(RECEIVER, 501L).status);

        when(letterMapper.selectById(501L)).thenAnswer(inv -> letter("BLOCKED", "ALWAYS"));

        assertEquals("CLOSED", service.getLetter(SENDER, 501L).status);
        assertEquals("BLOCKED", service.getLetter(RECEIVER, 501L).status);
    }

    @Test
    void outboxRowsAreMaskedUnderNever() {
        SlowLetter readUnderNever = letter("READ", null);
        SlowLetter readUnderAlways = letter("READ", "ALWAYS");
        SlowLetter flying = letter("FLYING", null);
        when(letterMapper.selectList(any()))
                .thenReturn(List.of(readUnderNever, readUnderAlways, flying));

        List<SlowLetter> outbox = service.outbox(SENDER);

        assertEquals("DELIVERED", outbox.get(0).status);
        assertNull(outbox.get(0).readAt);
        assertEquals("READ", outbox.get(1).status);
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), outbox.get(1).readAt);
        assertEquals("FLYING", outbox.get(2).status);
        assertTrue(outbox.stream().allMatch(row -> row.receiptPolicy == null),
                "no sender-facing row carries the recipient's private preference");
    }

    @Test
    void threadViewMasksCallerSentLettersAndWithholdsNotYetArrivedOnes() {
        LetterThread thread = new LetterThread();
        thread.id = 77L;
        thread.firstLetterId = 501L;
        thread.participantA = SENDER;
        thread.participantB = RECEIVER;
        when(threadMapper.selectById(77L)).thenReturn(thread);
        SlowLetter mineReadUnderNever = letter("READ", null);   // caller sent; receiver read it
        SlowLetter mineFlying = letter("FLYING", null);         // caller sent; still in transit
        SlowLetter theirsDelivered = letter("DELIVERED", null); // counterpart -> caller, arrived
        SlowLetter theirsFlying = letter("SENT", null);         // counterpart -> caller, NOT arrived
        theirsDelivered.senderUserId = RECEIVER;
        theirsDelivered.receiverUserId = SENDER;
        theirsFlying.senderUserId = RECEIVER;
        theirsFlying.receiverUserId = SENDER;
        when(letterMapper.selectList(any())).thenReturn(
                List.of(mineReadUnderNever, mineFlying, theirsDelivered, theirsFlying));

        List<SlowLetter> view = service.getThreadLetters(SENDER, 77L);

        assertEquals(3, view.size(), "the not-yet-arrived letter TO this caller is withheld");
        assertTrue(view.stream().noneMatch(row -> row.id == theirsFlying.id),
                "the sealed in-flight letter never reaches the recipient through a thread");
        assertEquals("DELIVERED", view.get(0).status,
                "the caller's own READ letter is masked through the recipient's NEVER policy");
        assertNull(view.get(0).readAt);
        assertEquals("FLYING", view.get(1).status);
        assertEquals("DELIVERED", view.get(2).status);
    }

    // ---------------- §2-6 negative gate: not-yet-arrived letters are unreadable ----------------

    @Test
    void receiverCannotOpenALetterThatHasNotArrivedYet() {
        for (String preArrival : List.of("SENT", "FLYING", "DRAFT")) {
            when(letterMapper.selectById(501L)).thenReturn(letter(preArrival, null));
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.getLetter(RECEIVER, 501L),
                    "status " + preArrival + " must stay sealed until arrival");
            assertEquals(ErrorCode.LETTER_STATE_INVALID, error.code);
        }
    }

    @Test
    void receiverCanOpenOnceDelivered() {
        when(letterMapper.selectById(501L)).thenReturn(letter("DELIVERED", null));
        assertEquals("DELIVERED", service.getLetter(RECEIVER, 501L).status);
    }

    // ---------------- §2-6 timezone boundary: server-UTC口径, no drift ----------------

    @Test
    void tonightInAFarWesternZoneIsResolvedInUtcNotTheServerDefaultZone() {
        // 10:00Z is 06:00 in New York (EDT, UTC-4): "tonight 21:00" there is 2026-07-27T01:00Z.
        // If the computation drifted onto the server default (Asia/Shanghai) it would land at
        // 2026-07-26T13:00Z -- a 12-hour drift. The letter's own IANA zone must win, and the
        // persisted instant is the UTC one the scheduler later compares against (also UTC).
        SlowLetter sent = send(LetterDeliveryPreset.TONIGHT, "America/New_York");
        assertEquals(LocalDateTime.of(2026, 7, 27, 1, 0), sent.estimatedArrivalAt);
        assertEquals("America/New_York", sent.deliveryTimeZone);
    }

    @Test
    void tonightAfterLocalBedtimeRollsToTheNextLocalEvening() {
        // 01:30Z on the 27th is 21:30 on the 26th in New York: tonight's 21:00 already passed,
        // so "tonight" becomes tomorrow 21:00 local = 2026-07-28T01:00Z. The roll happens on
        // the SENDER's wall clock, not the server's.
        draftOf(LetterDeliveryPreset.TONIGHT, "America/New_York");
        clockAt(Instant.parse("2026-07-27T01:30:00Z"));
        SlowLetter sent = service.transition(SENDER, 42L, "SENT");
        assertEquals(LocalDateTime.of(2026, 7, 28, 1, 0), sent.estimatedArrivalAt);
    }

    @Test
    void tonightInAFarEasternZoneCrossesIntoTheNextUtcDay() {
        // 10:00Z is already 2026-07-27T00:00 in Kiritimati (UTC+14): its "today 21:00" is
        // 2026-07-27T07:00Z -- the arrival's UTC day differs from both the send instant and
        // the Shanghai default. One UTC口径 has to hold for both edges of the date line.
        SlowLetter sent = send(LetterDeliveryPreset.TONIGHT, "Pacific/Kiritimati");
        assertEquals(LocalDateTime.of(2026, 7, 27, 7, 0), sent.estimatedArrivalAt);
    }

    @Test
    void tomorrowMeansTheSameLocalWallClockInTheSenderZone() {
        // 10:00Z = 06:00 New York; "this time tomorrow" = 07-27 06:00 EDT = 2026-07-27T10:00Z.
        SlowLetter sent = send(LetterDeliveryPreset.TOMORROW, "America/New_York");
        assertEquals(LocalDateTime.of(2026, 7, 27, 10, 0), sent.estimatedArrivalAt);
    }

    @Test
    void customArrivalIsPersistedAsTheExactUtcInstantNoZoneShift() {
        LetterCreateRequest request = request(LetterDeliveryPreset.CUSTOM);
        request.customArrivalAt = Instant.parse("2026-07-26T10:00:01Z");
        when(guardAgent.allow(any())).thenReturn(true);
        SlowLetter draft = service.draft(SENDER, request);
        draft.id = 42L;
        when(letterMapper.selectById(42L)).thenReturn(draft);
        when(letterMapper.update(any(), any())).thenReturn(1);
        allowDelivery();

        SlowLetter sent = service.transition(SENDER, 42L, "SENT");

        assertEquals(LocalDateTime.of(2026, 7, 26, 10, 0, 1), sent.estimatedArrivalAt);
    }

    // ---------------- helpers ----------------

    private SlowLetter letter(String status, String receiptPolicy) {
        SlowLetter letter = new SlowLetter();
        letter.id = 501L;
        letter.senderUserId = SENDER;
        letter.receiverUserId = RECEIVER;
        letter.title = "一封慢信";
        letter.letterBody = "慢慢说，也认真抵达。";
        letter.status = status;
        letter.receiptPolicy = receiptPolicy;
        letter.deliveryPreset = "DEMO_3M";
        if ("READ".equals(status)) {
            letter.readAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        }
        return letter;
    }

    private SlowLetter draftOf(LetterDeliveryPreset preset, String zone) {
        SlowLetter letter = new SlowLetter();
        letter.id = 42L;
        letter.senderUserId = SENDER;
        letter.receiverUserId = RECEIVER;
        letter.title = "跨时区的信";
        letter.letterBody = "愿它在正确的时刻抵达。";
        letter.status = "DRAFT";
        letter.deliveryPreset = preset.name();
        letter.deliveryTimeZone = zone;
        when(letterMapper.selectById(42L)).thenReturn(letter);
        when(letterMapper.update(any(), any())).thenReturn(1);
        allowDelivery();
        return letter;
    }

    private SlowLetter send(LetterDeliveryPreset preset, String zone) {
        draftOf(preset, zone);
        return service.transition(SENDER, 42L, "SENT");
    }

    /** Rebuilds the service around a different fixed clock; mapper stubs survive (same mocks). */
    private void clockAt(Instant fixedAt) {
        service = new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry, guardAgent,
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper,
                blockRelationMapper, new PiiCredentialDetector(), Clock.fixed(fixedAt, ZoneOffset.UTC));
    }

    private LetterCreateRequest request(LetterDeliveryPreset preset) {
        LetterCreateRequest request = new LetterCreateRequest();
        request.receiverUserId = RECEIVER;
        request.title = "给远方的你";
        request.letterBody = "愿这封信在合适的时候抵达。";
        request.deliveryPreset = preset;
        return request;
    }

    private void allowDelivery() {
        LetterSafetyFilter.FilterResult result = new LetterSafetyFilter.FilterResult();
        result.passed = true;
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(result);
    }
}
