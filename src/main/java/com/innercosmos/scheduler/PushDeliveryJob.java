package com.innercosmos.scheduler;

import com.innercosmos.service.PushGateway;
import com.innercosmos.service.PushTokenProtector;
import com.innercosmos.service.push.PushDeliveryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
@ConditionalOnProperty(prefix = "inner-cosmos.push", name = "worker-enabled", havingValue = "true")
public class PushDeliveryJob {
    private final PushDeliveryRepository deliveries;
    private final PushTokenProtector protector;
    private final Map<String, PushGateway> gateways;

    public PushDeliveryJob(PushDeliveryRepository deliveries, PushTokenProtector protector,
                           java.util.List<PushGateway> gateways) {
        this.deliveries = deliveries; this.protector = protector;
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(PushGateway::transport, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${inner-cosmos.push.poll-delay-ms:5000}")
    public void run() {
        for (PushDeliveryRepository.Claimed row : deliveries.claim(25)) deliver(row);
    }

    void deliver(PushDeliveryRepository.Claimed row) {
        PushGateway gateway = gateways.get(row.transport());
        if (gateway == null) { deliveries.failed(row, false, "UNSUPPORTED_TRANSPORT"); return; }
        String token = "";
        if (!"LOCAL_EVIDENCE".equals(row.transport())) {
            if (row.tokenCiphertext() == null) { deliveries.failed(row, false, "TOKEN_UNAVAILABLE"); return; }
            try { token = protector.reveal(row.tokenCiphertext()); }
            catch (RuntimeException unavailable) { deliveries.failed(row, false, "TOKEN_KEY_UNAVAILABLE"); return; }
        }
        PushGateway.SendResult result = gateway.send(new PushGateway.Message(token, row.title(), row.body(),
            row.deepLink(), row.wakeIntentId()));
        if (result.delivered()) deliveries.delivered(row, result.providerMessageId());
        else {
            if (result.invalidToken()) deliveries.revokeDevice(row.deviceId());
            deliveries.failed(row, result.retryable(), result.errorClass());
        }
    }
}
