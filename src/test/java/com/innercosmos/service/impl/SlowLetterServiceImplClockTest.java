package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.service.LetterSafetyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 1.7 (PARTIAL/P1): SlowLetterServiceImpl used to call the bare, untestable
 * {@code LocalDateTime.now()} (implicit platform-default zone) at every departure-time call
 * site, with the parallax "3 minutes" flight duration inlined as a magic number at each of
 * those call sites. Neither was controllable from a test, so no test could ever pin an exact
 * expected estimatedArrivalAt/thread-freshness timestamp -- only assert "is roughly now".
 *
 * These tests inject a fixed Clock and assert the EXACT resulting timestamp, proving the
 * class now advances on a single explicit, controllable time source rather than the wall
 * clock, and that the flight duration is a named, reusable policy constant.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterServiceImplClockTest {

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-23T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private SlowLetterServiceImpl service() {
        return new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry, guardAgent,
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper, blockRelationMapper, new PiiCredentialDetector(),
                FIXED_CLOCK);
    }

    private LetterCreateRequest request(Long receiverUserId) {
        LetterCreateRequest r = new LetterCreateRequest();
        r.receiverUserId = receiverUserId;
        r.title = "致远方";
        r.letterBody = "今天想和你说说话。";
        return r;
    }

    @Test
    @DisplayName("1.7: draft() stamps estimatedArrivalAt as exactly now(injected clock) + the named parallax flight duration")
    void draft_usesInjectedClock_forEstimatedArrival() {
        when(guardAgent.allow(any())).thenReturn(true);

        SlowLetter created = service().draft(1L, request(2L));

        LocalDateTime expected = LocalDateTime.now(FIXED_CLOCK).plus(SlowLetterServiceImpl.PARALLAX_FLIGHT_DURATION);
        assertEquals(expected, created.estimatedArrivalAt,
                "estimatedArrivalAt must be computed off the injected Clock, not the wall clock");

        ArgumentCaptor<SlowLetter> captor = ArgumentCaptor.forClass(SlowLetter.class);
        verify(letterMapper).insert(captor.capture());
        assertEquals(expected, captor.getValue().estimatedArrivalAt);
    }

    @Test
    @DisplayName("1.7: transition() stamps sent_at using the injected clock, not the wall clock")
    void transition_usesInjectedClock_forStatusTimestamp() {
        SlowLetter letter = new SlowLetter();
        letter.id = 5L;
        letter.senderUserId = 1L;
        letter.receiverUserId = 2L;
        letter.status = "DRAFT";
        when(letterMapper.selectById(5L)).thenReturn(letter);
        when(letterMapper.update(any(), any())).thenReturn(1);
        com.innercosmos.service.LetterSafetyFilter.FilterResult safe = new com.innercosmos.service.LetterSafetyFilter.FilterResult();
        safe.passed = true;
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(safe);

        SlowLetter result = service().transition(1L, 5L, "SENT");

        assertEquals(LocalDateTime.now(FIXED_CLOCK), result.sentAt,
                "sent_at must be computed off the injected Clock");
    }
}
