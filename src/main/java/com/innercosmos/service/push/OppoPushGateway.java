package com.innercosmos.service.push;

import com.innercosmos.service.PushGateway;
import org.springframework.stereotype.Component;

/**
 * CP-41 OPPO Push ( HeyTap ) mainland vendor transport (大陆厂商推送). Vendor REST credentials come
 * with the per-vendor 渠道合同 (operator gate, config inner-cosmos.push.oppo.* / env INNER_COSMOS_PUSH_OPPO_SECRET); until they
 * exist every send fails closed with EXTERNAL_CREDENTIAL_GATE — never a fabricated
 * provider message id (不伪造发送), and the in-app pull path keeps carrying the message
 * (push 失败仍可站内拉取). The single-vendor kill switch is config-driven: disable the
 * bean and its deliveries fail visibly without touching other channels.
 */
@Component
public class OppoPushGateway implements PushGateway {
    @Override public String transport() { return "OPPO"; }
    @Override public SendResult send(Message message) {
        return SendResult.failed(false, false, "EXTERNAL_CREDENTIAL_GATE");
    }
}
