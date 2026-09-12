package com.innercosmos.ai.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * CP-40 cost guardrail semantics: the budget check runs before the provider is called,
 * call and token budgets both hard-stop with a dedicated, non-alarming error, the day
 * rolls over on the Asia/Shanghai calendar, and the disabled flag is an honest bypass
 * (not a silent miscount).
 */
class ProviderSpendGuardTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private ProviderSpendGuard guard(long callBudget, long tokenBudget) {
        ProviderSpendGuard guard = new ProviderSpendGuard();
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "enabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "dailyCallBudget", callBudget);
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "dailyTokenBudget", tokenBudget);
        return guard;
    }

    @Test
    void underBudgetCallsPassAndAreCounted() {
        ProviderSpendGuard guard = guard(3, 100_000);
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> guard.tryAcquire(1L, "AURORA_SPEAKER_DAILY_TALK"));
            guard.record(1L, "AURORA_SPEAKER_DAILY_TALK", 900);
        }
        assertEquals(3, guard.snapshot(1L).calls().get());
        assertEquals(2_700, guard.snapshot(1L).tokens().get());
    }

    @Test
    void exhaustedCallBudgetFailsFastWithDedicatedCodeAndUserHonestMessage() {
        ProviderSpendGuard guard = guard(2, 100_000);
        guard.tryAcquire(1L, "m");
        guard.record(1L, "m", 10);
        guard.tryAcquire(1L, "m");
        guard.record(1L, "m", 10);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> guard.tryAcquire(1L, "m"));
        assertEquals(ErrorCode.AI_SPEND_EXCEEDED, denied.code);
        assertTrue(denied.getMessage().contains("每日上限"));
        assertTrue(denied.getMessage().contains("本地功能不受影响"));
        // Other users are not collateral-damaged by one user's budget.
        assertDoesNotThrow(() -> guard.tryAcquire(2L, "m"));
    }

    @Test
    void tokenBudgetIsAnIndependentStop() {
        ProviderSpendGuard guard = guard(1_000, 1_000);
        guard.tryAcquire(1L, "m");
        guard.record(1L, "m", 1_000);
        BusinessException denied = assertThrows(BusinessException.class,
                () -> guard.tryAcquire(1L, "m"));
        assertEquals(ErrorCode.AI_SPEND_EXCEEDED, denied.code);
        assertTrue(denied.getMessage().contains("tokens"));
    }

    @Test
    void dayRolloverResetsOnTheShanghaiCalendar() {
        ProviderSpendGuard guard = guard(1, 100_000);
        Clock dayOne = Clock.fixed(Instant.parse("2026-09-12T20:00:00Z"), SHANGHAI);
        guard.setClock(dayOne);
        guard.tryAcquire(1L, "m");
        guard.record(1L, "m", 10);
        assertThrows(BusinessException.class, () -> guard.tryAcquire(1L, "m"));

        // Next Shanghai day: budget back.
        guard.setClock(Clock.fixed(Instant.parse("2026-09-13T20:00:00Z"), SHANGHAI));
        assertDoesNotThrow(() -> guard.tryAcquire(1L, "m"));
        assertEquals(0, guard.snapshot(1L).calls().get());
    }

    @Test
    void disabledGuardIsAnHonestBypass() {
        ProviderSpendGuard guard = guard(0, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "enabled", false);
        assertDoesNotThrow(() -> guard.tryAcquire(1L, "m"));
        guard.record(1L, "m", 5);
        assertEquals(0, guard.snapshot(1L).calls().get(), "a disabled guard counts nothing");
    }
}
