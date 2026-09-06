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

    public ConsentEnforcingLlmClient(LlmClient delegate, ConsentCenterService consentCenter) {
        this.delegate = delegate;
        this.consentCenter = consentCenter;
    }

    @Override
    public String chat(LlmRequest request) {
        guard(request);
        return delegate.chat(request);
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        guard(request);
        return delegate.streamChat(request);
    }

    private void guard(LlmRequest request) {
        if (Boolean.TRUE.equals(request.forceMock)) {
            return; // A/B experiment forced local mock: no egress on this call.
        }
        consentCenter.assertProviderEgress(request.userId);
    }
}
