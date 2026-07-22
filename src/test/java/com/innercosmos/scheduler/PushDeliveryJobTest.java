package com.innercosmos.scheduler;

import com.innercosmos.service.PushGateway;
import com.innercosmos.service.PushTokenProtector;
import com.innercosmos.service.push.LocalEvidencePushGateway;
import com.innercosmos.service.push.PushDeliveryRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class PushDeliveryJobTest {
    @Test void localEvidenceDeliveryNeedsNoRemoteTokenAndCompletesTheOutboxRow() {
        PushDeliveryRepository repository = mock(PushDeliveryRepository.class);
        var row = new PushDeliveryRepository.Claimed(1L, 2L, "LOCAL_EVIDENCE", null,
            42L, "Aurora", "I remembered", "innercosmos://aurora/wake/42", 1);
        var job = new PushDeliveryJob(repository, new PushTokenProtector(""), List.of(new LocalEvidencePushGateway()));
        job.deliver(row);
        verify(repository).delivered(eq(row), startsWith("local-"));
        verify(repository, never()).failed(any(), anyBoolean(), anyString());
    }

    @Test void missingRemoteTokenFailsClosedWithoutCallingGateway() {
        PushDeliveryRepository repository = mock(PushDeliveryRepository.class);
        PushGateway fcm = mock(PushGateway.class);
        when(fcm.transport()).thenReturn("FCM");
        var row = new PushDeliveryRepository.Claimed(1L, 2L, "FCM", null,
            42L, "Aurora", "I remembered", "innercosmos://aurora/wake/42", 1);
        new PushDeliveryJob(repository, new PushTokenProtector(""), List.of(fcm)).deliver(row);
        verify(repository).failed(row, false, "TOKEN_UNAVAILABLE");
        verify(fcm, never()).send(any());
    }
}
