package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.service.LetterSafetyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 3.3 (CONFIRMED/P1): pins the detect-and-gate flow at slow-letter SEND time --
 * credentials/secrets are hard-blocked outright; other PII (phone/email/address) requires the
 * sender's explicit confirmation, and once confirmed, only a category-name consent RECEIPT is
 * recorded (never the raw PII text). Never a keyword-deletion "sanitize": the letter body itself
 * is never rewritten, only the send action is gated.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterPiiGateTest {

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;

    private SlowLetterServiceImpl service() {
        return new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry, guardAgent,
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper, blockRelationMapper,
                new PiiCredentialDetector(), Clock.systemUTC());
    }

    private SlowLetter draftLetter(long id, long senderId, long receiverId, String body) {
        SlowLetter letter = new SlowLetter();
        letter.id = id;
        letter.senderUserId = senderId;
        letter.receiverUserId = receiverId;
        letter.status = "DRAFT";
        letter.letterBody = body;
        return letter;
    }

    private LetterSafetyFilter.FilterResult passedSafety() {
        LetterSafetyFilter.FilterResult r = new LetterSafetyFilter.FilterResult();
        r.passed = true;
        return r;
    }

    @Test
    @DisplayName("3.3: a letter containing a credential (password) is HARD-BLOCKED at send -- no write, no confirmation override")
    void send_withCredential_hardBlockedRegardlessOfConfirmation() {
        SlowLetter letter = draftLetter(1L, 10L, 20L, "我的密码是Tr0ub4dor&3，帮我记一下");
        when(letterMapper.selectById(1L)).thenReturn(letter);

        assertThrows(SafetyBlockedException.class,
                () -> service().transition(10L, 1L, "SENT", true));

        verify(letterMapper, never()).update(any(), any());
        verify(logMapper, never()).insert(any(LetterStatusLog.class));
    }

    @Test
    @DisplayName("3.3: a letter containing a phone number cannot send without explicit confirmation -- PII_CONFIRMATION_REQUIRED, no write")
    void send_withPhoneNumber_noConfirmation_requiresConfirmation() {
        SlowLetter letter = draftLetter(2L, 10L, 20L, "有空打给我，号码是13812345678");
        when(letterMapper.selectById(2L)).thenReturn(letter);
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passedSafety());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().transition(10L, 2L, "SENT", null));

        assertEquals("PII_CONFIRMATION_REQUIRED", ex.code);
        verify(letterMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("3.3: explicit confirmation lets a phone-number letter send, and records only a category-name consent receipt (never the raw phone number)")
    void send_withPhoneNumber_confirmed_sendsAndRecordsCategoryOnlyReceipt() {
        SlowLetter letter = draftLetter(3L, 10L, 20L, "有空打给我，号码是13812345678");
        when(letterMapper.selectById(3L)).thenReturn(letter);
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passedSafety());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        SlowLetter result = service().transition(10L, 3L, "SENT", true);

        assertEquals("SENT", result.status);
        ArgumentCaptor<LetterStatusLog> logCaptor = ArgumentCaptor.forClass(LetterStatusLog.class);
        verify(logMapper).insert(logCaptor.capture());
        String reason = logCaptor.getValue().reason;
        assertTrue(reason.contains("PII_CONSENT_CONFIRMED"), "must record a consent receipt");
        assertTrue(reason.contains("PHONE"), "receipt must name the detected category");
        assertFalse(reason.contains("13812345678"), "receipt must NEVER contain the raw matched PII text");
    }

    @Test
    @DisplayName("3.3: an ordinary letter with no PII/credentials sends normally, no confirmation needed, no consent receipt noise")
    void send_ordinaryLetter_noGateAtAll() {
        SlowLetter letter = draftLetter(4L, 10L, 20L, "谢谢你愿意听我说这些，最近确实不太容易。");
        when(letterMapper.selectById(4L)).thenReturn(letter);
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passedSafety());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        SlowLetter result = service().transition(10L, 4L, "SENT", null);

        assertEquals("SENT", result.status);
        ArgumentCaptor<LetterStatusLog> logCaptor = ArgumentCaptor.forClass(LetterStatusLog.class);
        verify(logMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().reason.contains("PII_CONSENT_CONFIRMED"));
    }

    @Test
    @DisplayName("3.3: the letter body itself is never rewritten/redacted by the PII gate -- detect-and-gate, not keyword-deletion sanitize")
    void send_withPii_bodyNeverRewritten() {
        String original = "有空打给我，号码是13812345678";
        SlowLetter letter = draftLetter(5L, 10L, 20L, original);
        when(letterMapper.selectById(5L)).thenReturn(letter);
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passedSafety());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        SlowLetter result = service().transition(10L, 5L, "SENT", true);

        assertEquals(original, result.letterBody, "the letter body must be completely untouched by the PII gate");
    }
}
