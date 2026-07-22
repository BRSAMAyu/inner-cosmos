package com.innercosmos.service.push;

import com.innercosmos.service.PushGateway;
import org.springframework.stereotype.Component;

/** APNs extension point; signing requires a Mac/Apple Developer supplied p8 credential. */
@Component
public class ApnsPushGateway implements PushGateway {
    @Override public String transport() { return "APNS"; }
    @Override public SendResult send(Message message) {
        return SendResult.failed(false, false, "EXTERNAL_CREDENTIAL_GATE");
    }
}
