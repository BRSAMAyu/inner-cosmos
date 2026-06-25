package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.impl.MemoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SECURITY (IDOR) regression guard for {@code POST /api/memory/extract/{sessionId}}.
 *
 * <p>Dialog messages are queried by {@code session_id} only, so without an ownership check
 * user A could extract user B's private session into A's memory card and read B's private
 * conversation. {@link MemoryServiceImpl#extractFromSession} must call
 * {@code dialogService.verifyOwnership(userId, sessionId)} BEFORE reading any messages.
 */
class MemoryServiceOwnershipTest {

    private static final Long ATTACKER_ID = 2L;
    private static final Long VICTIM_SESSION_ID = 99L;
    private static final Long OWNER_ID = 1L;
    private static final Long OWN_SESSION_ID = 100L;

    /**
     * Negative path: user A cannot extract user B's session. The ownership guard throws,
     * no dialog message is read, and NO memory card is created for the attacker.
     */
    @Test
    @DisplayName("extractFromSession rejects a foreign-owned session and creates no memory card")
    void extractFromSession_foreignSession_throwsAndCreatesNoCard() {
        MemoryCardMapper memoryCardMapper = mock(MemoryCardMapper.class);
        DialogMessageMapper messageMapper = mock(DialogMessageMapper.class);
        DialogService dialogService = mock(DialogService.class);

        // Simulate "session 99 belongs to someone else" — verifyOwnership throws.
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "无权访问此会话"))
                .when(dialogService).verifyOwnership(ATTACKER_ID, VICTIM_SESSION_ID);

        // Only memoryCardMapper / messageMapper / dialogService are reachable before the guard
        // throws; the remaining collaborators are never touched, so null is safe here.
        MemoryServiceImpl service = new MemoryServiceImpl(
                memoryCardMapper, messageMapper, null, null, null, null, null,
                null, null, null, null, dialogService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.extractFromSession(ATTACKER_ID, VICTIM_SESSION_ID));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.code);

        verify(dialogService).verifyOwnership(ATTACKER_ID, VICTIM_SESSION_ID);
        // The victim's private messages must never be read...
        verifyNoInteractions(messageMapper);
        // ...and no memory card may be persisted for the attacker.
        verify(memoryCardMapper, never()).insert(any(MemoryCard.class));
        verify(memoryCardMapper, never()).updateById(any(MemoryCard.class));
    }

    /**
     * Positive path: the legitimate owner passes the ownership check and reaches message
     * reading. We stop at the message query (empty list) — this proves the guard does not
     * block the owner; full extraction is exercised by the integration tests.
     */
    @Test
    @DisplayName("extractFromSession lets the owner through to read their own messages")
    void extractFromSession_owner_passesOwnershipGuard() {
        MemoryCardMapper memoryCardMapper = mock(MemoryCardMapper.class);
        DialogMessageMapper messageMapper = mock(DialogMessageMapper.class);
        DialogService dialogService = mock(DialogService.class);

        // Owner: verifyOwnership does not throw (default mock behaviour).
        when(messageMapper.selectList(any())).thenReturn(List.<DialogMessage>of());

        MemoryServiceImpl service = new MemoryServiceImpl(
                memoryCardMapper, messageMapper, null, null, null, null, null,
                null, null, null, null, dialogService);

        // With no messages + no extract agent wired, an empty session throws an NPE
        // downstream (summarize on a null agent) — but crucially AFTER the ownership
        // check has passed and the owner's own messages were queried. We assert that the
        // guard let the owner through by verifying the query ran.
        assertThrows(Exception.class,
                () -> service.extractFromSession(OWNER_ID, OWN_SESSION_ID));

        verify(dialogService).verifyOwnership(OWNER_ID, OWN_SESSION_ID);
        verify(messageMapper).selectList(any()); // owner reached message reading
    }
}
