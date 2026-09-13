package com.innercosmos.scheduler;

import com.innercosmos.service.PushGateway;
import com.innercosmos.service.PushTokenProtector;
import com.innercosmos.service.push.LocalEvidencePushGateway;
import com.innercosmos.service.push.PushDeliveryRepository;
import com.innercosmos.service.push.XiaomiPushGateway;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

/**
 * CP-41 job-level wiring: a mainland vendor transport whose credentials are not yet
 * contracted fails the delivery VISIBLY (EXTERNAL_CREDENTIAL_GATE, not retryable, no
 * token revocation — the gate says nothing about the token), while the LOCAL_EVIDENCE
 * channel keeps completing — closing one vendor never stops the business message
 * (单厂商故障可关闭对应 SDK 而保住业务消息).
 */
class MainlandVendorDispatchTest {
    @Test void vendorCredentialGateFailsVisiblyAndLocalEvidenceKeepsDelivering() {
        PushDeliveryRepository repository = mock(PushDeliveryRepository.class);
        // A protector WITH a key, so the job can reveal the token and the vendor
        // gateway's own credential gate is what the delivery hits.
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] key = new byte[32];
        random.nextBytes(key);
        String encoded = java.util.Base64.getEncoder().encodeToString(key);
        PushTokenProtector protector = new PushTokenProtector(encoded);
        String cipher = protector.protect("regid-token").orElseThrow();
        var vendorRow = new PushDeliveryRepository.Claimed(1L, 2L, "XIAOMI", cipher,
            42L, "Aurora", "I remembered", "innercosmos://aurora/wake/42", 1);
        var localRow = new PushDeliveryRepository.Claimed(3L, 4L, "LOCAL_EVIDENCE", null,
            43L, "Aurora", "I remembered", "innercosmos://aurora/wake/43", 1);
        var job = new PushDeliveryJob(repository, protector,
                List.of(new XiaomiPushGateway(), new LocalEvidencePushGateway()));
        job.deliver(vendorRow);
        job.deliver(localRow);
        verify(repository).failed(vendorRow, false, "EXTERNAL_CREDENTIAL_GATE");
        verify(repository, never()).revokeDevice(2L);
        verify(repository).delivered(eq(localRow), startsWith("local-"));
    }
}
