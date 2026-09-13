package com.innercosmos.payments.entitlement;

import com.innercosmos.ai.observability.ProviderSpendGuard;
import com.innercosmos.common.Constants;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-46 透明配额 contract: the daily AI quota shows used/limit/remaining with a reset
 * instant anchored to the spend guard's own clock zone (same rollover the counters use);
 * subscription capability windows reset at the entitlement period end; and the response
 * carries the never-pay-gated list as a standing promise.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuotaDisplayContractTest {

    @Autowired ProviderSpendGuard spendGuard;
    @Autowired EntitlementStateService entitlements;
    @Autowired MockMvc mockMvc;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void dailyQuotaReflectsUsageAndResetsAtTheGuardDayRollover() {
        long user = 600_000_001L;
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        spendGuard.setClock(Clock.fixed(
                ZonedDateTime.of(2026, 9, 13, 9, 30, 0, 0, shanghai).toInstant(), shanghai));
        spendGuard.record(user, "quota-test", 120);
        ProviderSpendGuard.DailyQuota quota = spendGuard.dailyQuota(user);
        assertEquals(1, quota.usedCalls());
        assertEquals(120, quota.usedTokens());
        assertEquals(Math.max(0, quota.callBudget() - 1), quota.remainingCalls());
        // Reset = next start-of-day in the guard's zone: 2026-09-14 00:00 +08 = 09-13 16:00Z.
        assertEquals(LocalDate.of(2026, 9, 13).atTime(16, 0),
                quota.resetsAtUtc().toLocalTime().atDate(quota.resetsAtUtc().toLocalDate()));
        assertEquals(ZonedDateTime.of(2026, 9, 14, 0, 0, 0, 0, shanghai)
                        .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                quota.resetsAtUtc());
    }

    @Test
    void quotaEndpointShowsDailyWindowSubscriptionWindowAndNeverPayGated() throws Exception {
        // A REAL user row: SessionAuthenticationFilter loads the user from tb_user before
        // the controller runs — synthetic ids would 401 at the security layer.
        long user = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                .queryForObject(
                        "SELECT id FROM tb_user WHERE account_kind<>'SYNTHETIC' ORDER BY id LIMIT 1",
                        Long.class);
        // A live subscription makes the SUBSCRIPTION_PERIOD window visible with its reset.
        entitlements.onPaymentSucceeded(user, "pro.monthly", "wechatpay",
                "O-QUOTA-" + user, "evt-quota-" + user,
                ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime());

        HttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, user);

        String resetsPrefix = ZonedDateTime.now(ZoneOffset.UTC).plusMonths(1)
                .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE).substring(0, 7);
        mockMvc.perform(get("/api/me/quotas").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[0].capability").value("ai.deep_daily_budget"))
                .andExpect(jsonPath("$.data.quotas[0].basis").value("DAILY"))
                .andExpect(jsonPath("$.data.quotas[0].resetsAt").isNotEmpty())
                .andExpect(jsonPath("$.data.subscriptionWindows[0].basis")
                        .value("SUBSCRIPTION_PERIOD"))
                .andExpect(jsonPath("$.data.subscriptionWindows[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.data.subscriptionWindows[0].resetsAt").isNotEmpty())
                .andExpect(jsonPath("$.data.neverPayGated")
                        .isArray())
                .andExpect(jsonPath("$.data.neverPayGated[0]")
                        .value("account.cancellation"));

        // Never-pay-gated stays a promise, not a spendable quota row.
        String[] neverGated = EntitlementGates.NEVER_PAID_GATED.toArray(String[]::new);
        for (String capability : neverGated) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    EntitlementGates.PAID_GATED.contains(capability));
        }
    }
}
