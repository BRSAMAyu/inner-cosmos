package com.innercosmos.service;

import com.innercosmos.service.impl.GravityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M-014: gravity's time-decay term must actually take effect (the audit's missing contract
 * test — "no test asserts gravity decreases as daysSince grows").
 */
class GravityServiceImplTest {

    private final GravityService gravity = new GravityServiceImpl();

    @Test
    @DisplayName("M-014: gravity decreases as daysSinceLastTouched grows (decay is live)")
    void gravity_decaysOverTime() {
        double fresh = gravity.calculateGravity(5.0, 2, 3.0, 1, 0);
        double week = gravity.calculateGravity(5.0, 2, 3.0, 1, 7);
        double month = gravity.calculateGravity(5.0, 2, 3.0, 1, 30);

        assertTrue(week < fresh, "gravity must decay over a week vs fresh");
        assertTrue(month < week, "gravity must decay over a month vs a week");
        assertTrue(fresh > 0 && month > 0, "gravity stays positive even after long decay");
    }

    @Test
    @DisplayName("gravity grows with intensity / recurrence / importance")
    void gravity_growsWithInputs() {
        double low = gravity.calculateGravity(1.0, 1, 1.0, 1, 0);
        double high = gravity.calculateGravity(9.0, 5, 5.0, 5, 0);
        assertTrue(high > low, "heavier memories must have higher gravity");
    }
}
