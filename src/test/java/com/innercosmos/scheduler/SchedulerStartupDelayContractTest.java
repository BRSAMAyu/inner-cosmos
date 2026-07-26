package com.innercosmos.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerStartupDelayContractTest {

    @Test
    void legacyH2ReadersWaitForApplicationRunnerSchemaUpgrades() throws Exception {
        assertSchedule(LetterDeliveryJob.class, "deliverArrivedLetters", 60_000L, 60_000L);
        assertSchedule(AuroraProactiveJob.class, "run", 90_000L, 90_000L);
    }

    private static void assertSchedule(
            Class<?> jobType,
            String methodName,
            long expectedInterval,
            long expectedInitialDelay
    ) throws NoSuchMethodException {
        Method method = jobType.getDeclaredMethod(methodName);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        long interval = scheduled.fixedRate() >= 0 ? scheduled.fixedRate() : scheduled.fixedDelay();

        assertEquals(expectedInterval, interval);
        assertEquals(expectedInitialDelay, scheduled.initialDelay());
    }
}
