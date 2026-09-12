package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.PsychologySkillService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.consent.ConsentCenterService;
import com.innercosmos.safety.CrisisContinuityService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * CP-27: the real ASR egress path is VOICE_PROCESSING-gated (mock stays local). CP-28: in a
 * durable high-risk context no reflection exercise is suggested — the flow routes to support.
 * CP-17: unknown egress hosts fail closed and every real call lands in the gateway manifest.
 */
@SpringBootTest
class VoiceSkillGatewayTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private com.innercosmos.controller.AsrController asrController;
    @Autowired
    private PsychologySkillService skillService;
    @Autowired
    private ConsentCenterService consentCenter;
    @Autowired
    private CrisisContinuityService crisis;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private com.innercosmos.ai.gateway.GatewayEgressGuard egressGuard;
    @Autowired
    private com.innercosmos.ai.gateway.GatewayCallLedger ledger;
    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(24).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    @Test
    void cp27_realAsrPathRequiresVoiceConsentMockPathStaysLocal() throws java.io.IOException {
        User user = human("cp27");
        jakarta.servlet.http.HttpSession session =
                new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(com.innercosmos.common.Constants.SESSION_USER_KEY, user.id);
        var audio = new MockMultipartFile("file", "a.wav", "audio/wav", new byte[]{1, 2, 3});

        // Default is DECLINED: no consent -> no audio leaves, with the honest pointer.
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> asrController.transcribe(audio, session));
        assertEquals(ErrorCode.CONSENT_REQUIRED, blocked.code);
        assertTrue(blocked.getMessage().contains("语音"));

        // Grant VOICE_PROCESSING -> the real path proceeds (Mock ASR in dev/test).
        consentCenter.decide(user.id, "VOICE_PROCESSING", true);
        assertNotNull(asrController.transcribe(audio, session));

        // The local mock path never needed consent and still works either way.
        assertNotNull(asrController.mockTranscribe(java.util.Map.of("hintText", "你好"), session));
    }

    @Test
    void cp28_highRiskContextSuspendsExerciseSuggestions() {
        User calm = human("cp28a");
        assertNotNull(skillService.suggest(calm.id, "我最近在要不要换工作上很纠结", "zh-CN"));

        User atRisk = human("cp28b");
        // Lift the durable risk state to ELEVATED via repeated strong signals.
        for (int i = 0; i < 8; i++) {
            crisis.observe(atRisk.id, 5000L + i, "MEDIUM", "撑不住了，一直很难受，喘不过气");
        }
        assertEquals("ELEVATED", crisis.statusOf(atRisk.id).level);
        assertNull(skillService.suggest(atRisk.id, "我最近在要不要换工作上很纠结", "zh-CN"),
                "no reflection exercise is offered in a high-risk context");
    }

    @Test
    void cp17_unknownEgressHostsFailClosedAndCallsLandInTheManifest() {
        // Unknown / unparseable hosts are rejected outright.
        assertThrows(IllegalStateException.class,
                () -> egressGuard.validate("https://evil.example.com/v1", "test"));
        assertThrows(IllegalStateException.class,
                () -> egressGuard.validate("not a url", "test"));
        assertThrows(IllegalStateException.class,
                () -> egressGuard.validate("   ", "test"));
        // Contracted hosts (exact and subdomain) pass.
        egressGuard.validate("https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm");
        egressGuard.validate("https://api.minimax.chat/v1/text/chatcompletion_v2", "minimax");
        egressGuard.validate("http://127.0.0.1:9999/proxy", "local-dev");

        // Manifest: a consent-refused call records FAILED without reaching the delegate,
        // and a granted call records OK with user/module/purpose bound.
        User user = human("cp17");
        var consentCenterBean = consentCenter;
        com.innercosmos.ai.client.LlmClient delegate = new com.innercosmos.ai.client.LlmClient() {
            @Override
            public String chat(com.innercosmos.ai.client.LlmRequest request) {
                return "ok";
            }

            @Override
            public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamChat(
                    com.innercosmos.ai.client.LlmRequest request) {
                return new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
            }
        };
        com.innercosmos.ai.client.ConsentEnforcingLlmClient guarded =
                new com.innercosmos.ai.client.ConsentEnforcingLlmClient(
                        delegate, consentCenterBean, ledger, "test-provider");
        var request = new com.innercosmos.ai.client.LlmRequest(user.id, "TEST_MODULE", "hi");
        assertThrows(BusinessException.class, () -> guarded.chat(request));
        assertEquals("FAILED:BusinessException",
                ledger.recent(1).get(0).outcome());

        consentCenter.decide(user.id, "AI_PROVIDER_EGRESS", true);
        assertEquals("ok", guarded.chat(request));
        var record = ledger.recent(1).get(0);
        assertEquals("OK", record.outcome());
        assertEquals("TEST_MODULE", record.moduleName());
        assertEquals("test-provider", record.provider());
        assertEquals("AI_PROVIDER_EGRESS", record.purpose());
        assertEquals("CN", record.region());
    }
}
