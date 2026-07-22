package com.innercosmos.service.push;

import com.innercosmos.service.PushGateway;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Deterministic local transport. The native client turns the durable event into an OS notification. */
@Component
public class LocalEvidencePushGateway implements PushGateway {
    @Override public String transport() { return "LOCAL_EVIDENCE"; }
    @Override public SendResult send(Message message) { return SendResult.delivered("local-" + UUID.randomUUID()); }
}
