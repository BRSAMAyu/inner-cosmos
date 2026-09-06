package com.innercosmos.service.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.ai.client.ConsentEnforcingLlmClient;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.consent.ConsentCenterService.ConsentView;
import com.innercosmos.service.consent.ConsentPurpose.Decision;
import com.innercosmos.service.metric.MetricCode;
import com.innercosmos.service.metric.MetricEventService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-07 acceptance: the consent center contract — purpose registry defaults, decisions
 * and their constraints, the ANALYTICS dual-write into the CP-03 metric gate, and the
 * provider-egress guard that never silently degrades.
 */
@SpringBootTest
class ConsentCenterTest {

    @Autowired
    private ConsentCenterService consentCenter;
    @Autowired
    private MetricEventService metricEventService;
    @Autowired
    private UserMapper userMapper;

    private User human() {
        User user = new User();
        user.username = "cp07-" + System.nanoTime();
        user.passwordHash = "x";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        userMapper.insert(user);
        return user;
    }

    private User synthetic() {
        User user = new User();
        user.username = "cp07-syn-" + System.nanoTime();
        user.passwordHash = "x";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "SYNTHETIC";
        userMapper.insert(user);
        return user;
    }

    @Test
    void defaultsMatchTheFrozenSpec() {
        User user = human();
        List<ConsentView> views = consentCenter.list(user.id);
        assertEquals(ConsentPurpose.values().length, views.size());
        for (ConsentView view : views) {
            switch (ConsentPurpose.valueOf(view.purposeCode())) {
                case CORE_SERVICE, ANALYTICS, CAPSULE_COMPILE -> assertTrue(view.granted(),
                        view.purposeCode() + " defaults to granted");
                case AI_PROVIDER_EGRESS, PUBLIC_DISCOVERABLE, PROACTIVE_CARE, VOICE_PROCESSING ->
                        assertFalse(view.granted(), view.purposeCode() + " defaults to not granted");
            }
            assertEquals("DEFAULT", view.source());
        }
        // The egress purpose carries the honest withdrawal effect text.
        ConsentView egress = views.stream()
                .filter(v -> v.purposeCode().equals("AI_PROVIDER_EGRESS")).findFirst().orElseThrow();
        assertTrue(egress.withdrawalEffect().contains("不可用"));
    }

    @Test
    void decisionsAreRecordedWithVersionAndConstraintsHold() {
        User user = human();
        ConsentView granted = consentCenter.decide(user.id, "AI_PROVIDER_EGRESS", true);
        assertTrue(granted.granted());
        assertEquals("CONSENT_CENTER", granted.source());
        assertEquals(ConsentPurpose.CURRENT_VERSION, granted.version());
        assertEquals(Decision.GRANTED, consentCenter.effective(user.id, ConsentPurpose.AI_PROVIDER_EGRESS));

        ConsentView declined = consentCenter.decide(user.id, "AI_PROVIDER_EGRESS", false);
        assertFalse(declined.granted());
        assertEquals(Decision.DECLINED, consentCenter.effective(user.id, ConsentPurpose.AI_PROVIDER_EGRESS));

        BusinessException core = assertThrows(BusinessException.class,
                () -> consentCenter.decide(user.id, "CORE_SERVICE", false));
        assertEquals(ErrorCode.BAD_REQUEST, core.code);
        BusinessException managed = assertThrows(BusinessException.class,
                () -> consentCenter.decide(user.id, "CAPSULE_COMPILE", true));
        assertEquals(ErrorCode.BAD_REQUEST, managed.code);
        assertThrows(BusinessException.class,
                () -> consentCenter.decide(user.id, "NOT_A_PURPOSE", true));
    }

    @Test
    void providerEgressGuardsHumansOnlyAndNeverSilentlyDegrades() {
        User human = human();
        User synthetic = synthetic();

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> consentCenter.assertProviderEgress(human.id));
        assertEquals(ErrorCode.CONSENT_REQUIRED, blocked.code);
        assertTrue(blocked.getMessage().contains("同意"));

        // Internal/synthetic traffic (evaluation harness, seeds) and userless calls pass.
        consentCenter.assertProviderEgress(synthetic.id);
        consentCenter.assertProviderEgress(null);
        consentCenter.assertProviderEgress(999_999_999L);

        consentCenter.decide(human.id, "AI_PROVIDER_EGRESS", true);
        consentCenter.assertProviderEgress(human.id); // granted -> no throw
    }

    /** Declining ANALYTICS dual-writes into the CP-03 metric gate (and back on re-grant). */
    @Test
    void declinedAnalyticsUserLeavesTheMetricPipeline() {
        User user = human();
        String context = "cp07-b-" + System.nanoTime();
        assertNotNull(metricEventService.record(MetricCode.VALUE_CONFIRMED_PRIVATE, user.id,
                java.time.Instant.now(), null, null, null, null,
                Map.of("scope", "PRIVATE_REVIEW")));
        consentCenter.decide(user.id, "ANALYTICS", false);
        assertNull(metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, user.id,
                java.time.Instant.now(), "DIALOG_SESSION", context, null, null,
                Map.of("sessionId", context)), "declined analytics must stop event ingestion");
        consentCenter.decide(user.id, "ANALYTICS", true);
        String context2 = "cp07-c-" + System.nanoTime();
        assertNotNull(metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, user.id,
                java.time.Instant.now(), "DIALOG_SESSION", context2, null, null,
                Map.of("sessionId", context2)), "re-granting restores ingestion");
    }

    @Test
    void egressDecoratorBlocksBeforeDelegateAndHonorsForcedMock() {
        AtomicInteger guardCalls = new AtomicInteger();
        AtomicInteger delegateCalls = new AtomicInteger();
        ConsentCenterService counting = new ConsentCenterService() {
            @Override
            public List<ConsentView> list(Long userId) {
                return List.of();
            }

            @Override
            public ConsentView decide(Long userId, String purposeCode, boolean grant) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Decision effective(Long userId, ConsentPurpose purpose) {
                return Decision.NOT_GRANTED;
            }

            @Override
            public void assertProviderEgress(Long userId) {
                guardCalls.incrementAndGet();
                throw new BusinessException(ErrorCode.CONSENT_REQUIRED, "需要同意");
            }
        };
        LlmClient delegate = new LlmClient() {
            @Override
            public String chat(LlmRequest request) {
                delegateCalls.incrementAndGet();
                return "ok";
            }

            @Override
            public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamChat(
                    LlmRequest request) {
                delegateCalls.incrementAndGet();
                return new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
            }
        };
        ConsentEnforcingLlmClient guard = new ConsentEnforcingLlmClient(delegate, counting);

        LlmRequest egress = new LlmRequest(42L, "TEST", "hi");
        assertThrows(BusinessException.class, () -> guard.chat(egress));
        assertThrows(BusinessException.class, () -> guard.streamChat(egress));
        assertEquals(2, guardCalls.get());
        assertEquals(0, delegateCalls.get(), "refusal must be loud: the delegate never runs");

        LlmRequest forcedMock = new LlmRequest(42L, "TEST", "hi");
        forcedMock.forceMock = true;
        assertEquals("ok", guard.chat(forcedMock), "forced local mock performs no egress");
    }
}
