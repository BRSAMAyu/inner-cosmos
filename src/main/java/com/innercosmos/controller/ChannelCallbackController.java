package com.innercosmos.controller;

import com.innercosmos.payments.channel.ChannelCallbackAdapter;
import com.innercosmos.payments.channel.ChannelCallbackIngestService;
import com.innercosmos.payments.channel.ChannelCallbackIngestService.IngestResult;
import com.innercosmos.payments.channel.ChannelCallbackIngestService.Outcome;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * CP-45 channel callback endpoint, POSTed to by the payment channel's servers (sandbox:
 * the deterministic adapter contract; production: the licensed provider per CP-45's
 * 持牌服务商合同). There is no user session here — the ONLY trust is the fail-closed CP-47
 * verification inside the ingest service, which is why this path is CSRF-exempt and
 * permitAll in SecurityConfig: an unverified callback can never produce a ledger row or a
 * success ack, regardless of network reachability.
 *
 * <p>Ack semantics follow async-notify conventions: ACCEPTED returns the provider's literal
 * success body so the channel stops retrying; a quarantined status still returns HTTP 200
 * with the FAILURE body so the channel keeps retrying the (idempotent) notify until ops
 * resolves it via 查单 — the fact is never silently swallowed. Rejections never echo the raw
 * body or signature back.
 */
@RestController
public class ChannelCallbackController {

    private final ChannelCallbackIngestService ingestService;

    public ChannelCallbackController(ChannelCallbackIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping(value = "/api/payments/callbacks/{provider}",
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<String> callback(
            @PathVariable("provider") String provider,
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers) {
        IngestResult result = ingestService.ingest(provider.toLowerCase(Locale.ROOT), rawBody, headers);
        ChannelCallbackAdapter adapter = ingestService.adapter(provider.toLowerCase(Locale.ROOT));
        String ack = adapter == null ? "fail" : adapter.ackBody(result.outcome() == Outcome.ACCEPTED);
        MediaType contentType = adapter != null && "alipay".equals(adapter.provider())
                ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON;
        return switch (result.outcome()) {
            case ACCEPTED -> ResponseEntity.ok().contentType(contentType).body(ack);
            // 200 + failure body: channel retries; recording is idempotent.
            case QUARANTINED_STATUS -> ResponseEntity.ok().contentType(contentType).body(ack);
            case REJECTED_SIGNATURE -> ResponseEntity.status(401).contentType(contentType).body(ack);
            case REJECTED_MERCHANT -> ResponseEntity.status(403).contentType(contentType).body(ack);
            case REJECTED_ORDER -> ResponseEntity.status(404).contentType(contentType).body(ack);
            // Verified signature, contradicted amount: kept as DISPUTED, acked as failure.
            case REJECTED_AMOUNT -> ResponseEntity.status(422).contentType(contentType).body(ack);
            case MALFORMED -> ResponseEntity.badRequest().contentType(contentType).body(ack);
            case UNKNOWN_PROVIDER -> ResponseEntity.status(404).contentType(contentType).body(ack);
        };
    }
}
