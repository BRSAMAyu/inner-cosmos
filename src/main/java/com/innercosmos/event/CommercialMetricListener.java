package com.innercosmos.event;

import com.innercosmos.service.metric.MetricCode;
import com.innercosmos.service.metric.MetricEventService;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * CP-03: emits the private-pathway core metric when a dialog session is finished.
 * DialogServiceImpl only publishes DialogFinishedEvent from the atomic conditional
 * FINISHED update, so this listener sees exactly the server-confirmed completions
 * (never retried or racing double-fires) the K1/K2 contracts require.
 */
@Component
public class CommercialMetricListener {

    private final MetricEventService metricEventService;
    private final Clock clock;

    public CommercialMetricListener(MetricEventService metricEventService, Clock clock) {
        this.metricEventService = metricEventService;
        this.clock = clock;
    }

    @org.springframework.transaction.event.TransactionalEventListener(
            phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onDialogFinished(DialogFinishedEvent event) {
        metricEventService.record(
                MetricCode.PRIVATE_DIALOG_COMPLETED,
                event.userId,
                clock.instant(),
                "DIALOG_SESSION",
                String.valueOf(event.sessionId),
                null,
                null,
                Map.of("sessionId", String.valueOf(event.sessionId)));
    }
}
