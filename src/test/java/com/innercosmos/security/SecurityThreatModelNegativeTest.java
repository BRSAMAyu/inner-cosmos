package com.innercosmos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.SlowLetterService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.identity.AccountSecurityService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CP-16 automated negative tests for the high-risk threat-model rows (T1/T2/T3/T6/T9): the
 * account-takeover preconditions (uniform login errors, frozen lockout), CSRF enforcement on
 * state-changing requests, admin-route denial for ordinary users, and the credential-leak
 * hard block on the letter channel (T4). Each test maps to a row in
 * docs/commercialization/security/cp16-threat-model.md.
 */
@SpringBootTest(properties = "inner-cosmos.security.csrf-enabled=true")
@AutoConfigureMockMvc
class SecurityThreatModelNegativeTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private SlowLetterService slowLetterService;
    @Autowired
    private AccountSecurityService accountSecurityService;
    @Autowired
    private UserMapper userMapper;

    private User register() {
        RegisterRequest request = new RegisterRequest();
        request.username = "cp16-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(24).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private HttpSession loginSession(User user) throws Exception {
        // Under csrf-enabled, fetch a token first (exactly what the web client does via
        // /api/v1/auth/csrf) and send it as a header on the login POST, sharing one session.
        MockHttpSession session = new MockHttpSession();
        String csrfBody = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode csrf =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(csrfBody);
        String token = csrf.path("data").path("token").asText();
        MvcResult result = mockMvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getRequest().getSession();
    }

    @Test
    void t1_t6_loginErrorsDoNotEnumerateAccounts() {
        User user = register();
        LoginRequest wrongPassword = new LoginRequest();
        wrongPassword.username = user.username;
        wrongPassword.password = "definitely-wrong";
        LoginRequest wrongUser = new LoginRequest();
        wrongUser.username = "no-such-user-" + System.nanoTime();
        wrongUser.password = "definitely-wrong";
        String forWrongPassword = assertThrows(BusinessException.class,
                () -> userService.login(wrongPassword)).getMessage();
        String forWrongUser = assertThrows(BusinessException.class,
                () -> userService.login(wrongUser)).getMessage();
        assertEquals(forWrongUser, forWrongPassword,
                "T6: unknown-user and wrong-password must be indistinguishable");

        accountSecurityService.freeze(user.id, 1L, "T1 接管嫌疑");
        LoginRequest asFrozen = new LoginRequest();
        asFrozen.username = user.username;
        asFrozen.password = "password123";
        BusinessException locked = assertThrows(BusinessException.class,
                () -> userService.login(asFrozen));
        assertEquals(ErrorCode.FORBIDDEN, locked.code, "T1: a frozen account cannot log in");
    }

    @Test
    void t3_stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        // CSRF is enabled for this test context (prod default; ProductionStartupGuard asserts
        // it in real production profiles). A state-changing POST without the session's token
        // must be rejected before any controller logic runs.
        mockMvc.perform(post("/api/dialog/session/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        // The positive control lives in loginSession(): the same class of POST succeeds once
        // the token from /api/v1/auth/csrf is sent — proving the 403 is the missing-token
        // branch, not a broken endpoint.
    }

    @Test
    void t9_adminRoutesRejectOrdinaryUsers() throws Exception {
        User ordinary = register();
        HttpSession session = loginSession(ordinary);
        // requireAdmin denies ordinary users with 401 UNAUTHORIZED (deny — the exact 4xx
        // code is not security-relevant, only that no admin data ever returns).
        mockMvc.perform(get("/api/admin/users").session((MockHttpSession) session))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void t4_letterChannelHardBlocksCredentialLeakage() {
        User sender = register();
        User receiver = register();
        com.innercosmos.dto.LetterCreateRequest create = new com.innercosmos.dto.LetterCreateRequest();
        create.receiverUserId = receiver.id;
        create.title = "紧急";
        create.letterBody = "我的密码是Tr0ub4dor&3，别告诉别人";
        var draft = slowLetterService.draft(sender.id, create);
        assertThrows(SafetyBlockedException.class, () ->
                slowLetterService.transition(sender.id, draft.id, "SENT", null),
                "T4: credentials must never leave through the letter channel");
    }
}
