package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.DialogService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * M-001: the Aurora chat endpoints must reject a dialog session owned by another user
 * BEFORE any reply is generated. (Negative-ownership guard the audit flagged as missing.)
 */
class AuroraChatOwnershipTest {

    @Test
    @DisplayName("M-001: message() rejects a foreign-owned session and never calls the agent")
    void message_rejectsForeignSession() {
        DialogService dialogService = mock(DialogService.class);
        // Simulate "session 99 belongs to someone else" — verifyOwnership throws.
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "无权访问此会话"))
                .when(dialogService).verifyOwnership(2L, 99L);

        AuroraAgentService aurora = mock(AuroraAgentService.class);
        // Only the agent + dialogService are touched before the ownership guard throws;
        // the remaining collaborators are unreachable here, so null is safe.
        AuroraChatController controller = new AuroraChatController(
                aurora, null, null, null, null, null, null, null, null, dialogService);

        HttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 2L);

        ChatRequest request = new ChatRequest();
        request.sessionId = 99L;
        request.message = "hi";

        assertThrows(BusinessException.class, () -> controller.message(request, session));
        verifyNoInteractions(aurora); // the reply path must never be reached for a foreign session
    }
}
