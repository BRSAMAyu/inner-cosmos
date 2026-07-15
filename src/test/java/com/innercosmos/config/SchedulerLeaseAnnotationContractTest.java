package com.innercosmos.config;

import com.innercosmos.ai.goodbye.SessionIdleWatcher;
import com.innercosmos.scheduler.AuroraProactiveJob;
import com.innercosmos.scheduler.CapsuleSyncRetryJob;
import com.innercosmos.scheduler.LetterDeliveryJob;
import com.innercosmos.scheduler.NightlyMemorySettlementJob;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerLeaseAnnotationContractTest {

    @Test
    void everyBusinessSideEffectSchedulerHasAUniqueExplicitLease() throws Exception {
        Map<Method, String> scheduledMethods = Map.of(
                LetterDeliveryJob.class.getMethod("deliverArrivedLetters"), "letter-delivery",
                CapsuleSyncRetryJob.class.getMethod("retryFailedSyncs"), "capsule-sync-retry",
                AuroraProactiveJob.class.getMethod("run"), "aurora-proactive",
                NightlyMemorySettlementJob.class.getMethod("nightlyRecalculation"), "nightly-memory-settlement",
                SessionIdleWatcher.class.getMethod("scan"), "session-idle-goodbye");

        assertThat(scheduledMethods.keySet()).allSatisfy(method -> {
            SchedulerLock lease = method.getAnnotation(SchedulerLock.class);
            assertThat(lease).as(method.toGenericString()).isNotNull();
            assertThat(lease.name()).isEqualTo(scheduledMethods.get(method));
            assertThat(lease.lockAtMostFor()).isNotBlank();
            assertThat(lease.lockAtLeastFor()).isNotBlank();
        });
        assertThat(scheduledMethods.values()).doesNotHaveDuplicates();
    }
}
