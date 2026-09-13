package com.innercosmos.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.ai.gateway.GatewayCallLedger;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.consent.ConsentCenterService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * CP-17 residual: the consent guard's streamChat must attach terminal ledger
 * bookkeeping to the delegate's emitter — a stream that opens is guaranteed exactly
 * one terminal record (COMPLETED/FAILED/TIMEOUT), consent refusal still lands as
 * FAILED without reaching the delegate, and a delegate that installed its own
 * terminal callback (Spring emitters are single-slot) is chained, not dropped.
 */
class ConsentEnforcingStreamLedgerTest {

    private final GatewayCallLedger ledger = new GatewayCallLedger();

    /** Grants egress for every user (the test exercises the ledger, not consent). */
    private final ConsentCenterService grantAll = new ConsentCenterService() {
        @Override public List<ConsentCenterService.ConsentView> list(Long userId) { return List.of(); }
        @Override public ConsentCenterService.ConsentView decide(Long userId, String purposeCode, boolean grant) { return null; }
        @Override public com.innercosmos.service.consent.ConsentPurpose.Decision effective(Long userId, com.innercosmos.service.consent.ConsentPurpose purpose) { return null; }
        @Override public void assertProviderEgress(Long userId) { }
        @Override public void assertVoiceProcessing(Long userId) { }
    };

    /** Refuses egress for every user. */
    private final ConsentCenterService refuseAll = new ConsentCenterService() {
        @Override public List<ConsentCenterService.ConsentView> list(Long userId) { return List.of(); }
        @Override public ConsentCenterService.ConsentView decide(Long userId, String purposeCode, boolean grant) { return null; }
        @Override public com.innercosmos.service.consent.ConsentPurpose.Decision effective(Long userId, com.innercosmos.service.consent.ConsentPurpose purpose) { return null; }
        @Override public void assertProviderEgress(Long userId) {
            throw new BusinessException(com.innercosmos.common.ErrorCode.CONSENT_REQUIRED, "consent");
        }
        @Override public void assertVoiceProcessing(Long userId) { }
    };

    private static final LlmClient PASS_THROUGH = new LlmClient() {
        @Override public String chat(LlmRequest request) { return "ok"; }
        @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
    };

    private List<String> outcomes() {
        return ledger.recent(10).stream().map(GatewayCallLedger.CallRecord::outcome).toList();
    }

    /**
     * Spring 6 keeps composite callback objects in final fields; the container invokes
     * the composite, which fans out to every registered callback (ours and any the
     * delegate registered). Driving the composite is exactly what the container does.
     */
    private static Object callback(SseEmitter emitter, String fieldName) throws Exception {
        Field field = SseEmitter.class.getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(emitter);
    }

    private static void fire(Object composite, Object argument) {
        if (composite instanceof Consumer<?> consumer) {
            @SuppressWarnings("unchecked")
            Consumer<Object> wide = (Consumer<Object>) consumer;
            wide.accept(argument);
        } else if (composite instanceof Runnable runnable) {
            runnable.run();
        } else {
            throw new AssertionError("unexpected callback type: " + composite);
        }
    }

    @Test
    void streamChatRegistersTerminalBookkeepingOnTheEmitter() throws Exception {
        ConsentEnforcingLlmClient guarded =
                new ConsentEnforcingLlmClient(PASS_THROUGH, grantAll, ledger, "test-provider");
        SseEmitter emitter = guarded.streamChat(new LlmRequest(1L, "TEST_MODULE", "hi"));
        assertNotNull(callback(emitter, "errorCallback"));
        assertNotNull(callback(emitter, "timeoutCallback"));
        assertNotNull(callback(emitter, "completionCallback"));
        assertEquals(List.of("STREAM_OPENED"), outcomes());
    }

    @Test
    void containerInvokedErrorCallbackSettlesTheStreamOnce() throws Exception {
        ConsentEnforcingLlmClient guarded =
                new ConsentEnforcingLlmClient(PASS_THROUGH, grantAll, ledger, "test-provider");
        SseEmitter emitter = guarded.streamChat(new LlmRequest(1L, "TEST_MODULE", "hi"));
        fire(callback(emitter, "errorCallback"), new IllegalStateException("connection reset"));
        // The container then runs the completion callback — first terminal wins.
        fire(callback(emitter, "completionCallback"), null);
        assertEquals(List.of("STREAM_OPENED", "STREAM_FAILED:IllegalStateException"), outcomes());
    }

    @Test
    void containerInvokedCompletionCallbackRecordsCompleted() throws Exception {
        ConsentEnforcingLlmClient guarded =
                new ConsentEnforcingLlmClient(PASS_THROUGH, grantAll, ledger, "test-provider");
        SseEmitter emitter = guarded.streamChat(new LlmRequest(1L, "TEST_MODULE", "hi"));
        fire(callback(emitter, "completionCallback"), null);
        assertEquals(List.of("STREAM_OPENED", "STREAM_COMPLETED"), outcomes());
    }

    @Test
    void aDelegateTerminalCallbackIsChainedNotDropped() throws Exception {
        boolean[] delegateCleanedUp = {false};
        LlmClient selfRegistering = new LlmClient() {
            @Override public String chat(LlmRequest request) { return "ok"; }
            @Override public SseEmitter streamChat(LlmRequest request) {
                SseEmitter emitter = new SseEmitter();
                emitter.onError(ignored -> delegateCleanedUp[0] = true);
                return emitter;
            }
        };
        ConsentEnforcingLlmClient guarded =
                new ConsentEnforcingLlmClient(selfRegistering, grantAll, ledger, "test-provider");
        SseEmitter emitter = guarded.streamChat(new LlmRequest(1L, "TEST_MODULE", "hi"));
        fire(callback(emitter, "errorCallback"), new RuntimeException("dropped"));
        assertTrue(delegateCleanedUp[0], "the delegate's own callback must still run");
        assertEquals(List.of("STREAM_OPENED", "STREAM_FAILED:RuntimeException"), outcomes());
    }

    @Test
    void consentRefusalNeverOpensAStreamAndStillRecordsTheRefusal() {
        ConsentEnforcingLlmClient guarded =
                new ConsentEnforcingLlmClient(PASS_THROUGH, refuseAll, ledger, "test-provider");
        assertThrows(BusinessException.class,
                () -> guarded.streamChat(new LlmRequest(1L, "TEST_MODULE", "hi")));
        assertEquals(List.of("FAILED:BusinessException"), outcomes());
    }

    @Test
    void legacyConstructionWithoutLedgerStillStreams() {
        ConsentEnforcingLlmClient legacy =
                new ConsentEnforcingLlmClient(PASS_THROUGH, grantAll);
        assertNotNull(legacy.streamChat(new LlmRequest(1L, "TEST_MODULE", "hi")));
        assertTrue(outcomes().isEmpty(), "no ledger attached: nothing is fabricated");
    }
}
