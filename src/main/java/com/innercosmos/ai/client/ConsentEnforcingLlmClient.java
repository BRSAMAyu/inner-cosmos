package com.innercosmos.ai.client;

import com.innercosmos.service.consent.ConsentCenterService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * CP-07 egress guard. Wraps the real-provider client chain (failover included) so user
 * content can never reach an external model without an explicit AI_PROVIDER_EGRESS
 * grant. Refusal is loud — CONSENT_REQUIRED with what still works — never a silent
 * fallback to another route. Mock-only configurations are never wrapped (local
 * processing, nothing leaves), which also keeps dev/test behavior unchanged.
 */
public class ConsentEnforcingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final ConsentCenterService consentCenter;
    /** CP-17 call manifest; null in legacy direct-construction tests. */
    private final com.innercosmos.ai.gateway.GatewayCallLedger ledger;
    private final String providerLabel;

    public ConsentEnforcingLlmClient(LlmClient delegate, ConsentCenterService consentCenter) {
        this(delegate, consentCenter, null, "configured-provider");
    }

    public ConsentEnforcingLlmClient(LlmClient delegate, ConsentCenterService consentCenter,
                                     com.innercosmos.ai.gateway.GatewayCallLedger ledger,
                                     String providerLabel) {
        this.delegate = delegate;
        this.consentCenter = consentCenter;
        this.ledger = ledger;
        this.providerLabel = providerLabel == null ? "configured-provider" : providerLabel;
    }

    @Override
    public String chat(LlmRequest request) {
        try {
            guard(request);
            String reply = delegate.chat(request);
            manifest(request, "OK");
            return reply;
        } catch (RuntimeException failure) {
            // Refusals (consent) and failures are both auditable egress outcomes.
            manifest(request, "FAILED:" + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        try {
            guard(request);
            SseEmitter emitter = delegate.streamChat(request);
            if (ledger != null) {
                // CP-17: the manifest must record how a stream ENDED, not only that it
                // opened — a mid-flight break used to leave STREAM_OPENED as the last
                // word. Spring 6 composite callbacks coexist with any the delegate
                // registered, and exactly one terminal outcome is appended per stream
                // (first terminal wins — onCompletion also runs after error/timeout).
                com.innercosmos.ai.gateway.StreamOutcomeLedger stream =
                        com.innercosmos.ai.gateway.StreamOutcomeLedger.open(
                                ledger, request.userId,
                                request.moduleName == null ? "unknown" : request.moduleName,
                                providerLabel);
                emitter.onError(stream::failed);
                emitter.onTimeout(stream::timedOut);
                emitter.onCompletion(stream::completed);
            }
            return emitter;
        } catch (RuntimeException failure) {
            manifest(request, "FAILED:" + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private void guard(LlmRequest request) {
        if (Boolean.TRUE.equals(request.forceMock)) {
            return; // A/B experiment forced local mock: no egress on this call.
        }
        consentCenter.assertProviderEgress(request.userId);
    }

    private void manifest(LlmRequest request, String outcome) {
        if (ledger != null) {
            ledger.record(request.userId,
                    request.moduleName == null ? "unknown" : request.moduleName,
                    providerLabel, outcome);
        }
    }
}
