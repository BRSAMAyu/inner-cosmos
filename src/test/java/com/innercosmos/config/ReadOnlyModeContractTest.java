package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-62 readonly mode contract (the sunset playbook's 先保只读 capability, exercised for
 * real): with the switch ON, browsing and obligation paths keep working — login, safety
 * resources, data export, entitlement cancellation, and payment channel callbacks (the
 * ledger must keep recording money that moves during a sunset) — while NEW charges are
 * refused with a 503 that names the user's unaffected rights (本地数据、导出与数据权利
 * 不受影响). The filter is @ConditionalOnProperty, so the default (switch off) never
 * registers it — an operator switch, never a silent default.
 */
@SpringBootTest(properties = {
        "inner-cosmos.readonly-mode.enabled=true",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class ReadOnlyModeContractTest {

    @Autowired MockMvc mockMvc;

    private MockHttpSession login() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String token = csrf.getResponse().getContentAsString().contains("token")
                ? extractToken(csrf.getResponse().getContentAsString()) : null;
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-CSRF-TOKEN", token == null ? "" : token)
                        .content("{\"username\":\"demo\",\"password\":\"demo123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private static String extractToken(String body) {
        int i = body.indexOf("\"token\"");
        if (i < 0) {
            return null;
        }
        int start = body.indexOf('"', i + 7) + 1;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    @Test
    void obligationsSurviveAndNewChargesAreRefused() throws Exception {
        MockHttpSession session = login();

        // Safety resources stay available (crisis paths are never sunset).
        mockMvc.perform(get("/api/safety/resources"))
                .andExpect(status().isOk());

        // New charge entry is refused with the user's rights spelled out.
        mockMvc.perform(post("/api/payments/orders").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"pro.monthly\",\"channel\":\"wechatpay\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("READONLY_MODE"))
                .andExpect(jsonPath("$.message").value(containsString("导出")));

        // Data-rights writes are obligations and pass THROUGH the readonly filter
        // (whatever the endpoint itself answers, it must not be 503 READONLY_MODE).
        MvcResult rights = mockMvc.perform(post("/api/me/data-rights/retract-all")
                        .session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        if (rights.getResponse().getStatus() == 503) {
            throw new AssertionError("data-rights writes must never be readonly-blocked: "
                    + rights.getResponse().getContentAsString());
        }

        // Channel callbacks keep flowing into the pipeline: a well-formed sandbox notify
        // passes the readonly filter and lands on the merchant gate's own fail-closed
        // 403 (blank config in the test context) — proving the filter did not eat it.
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String body = "{\"id\":\"ro-callback\",\"event_type\":\"TRANSACTION.SUCCESS\","
                + "\"resource\":{\"mchid\":\"unset\",\"out_trade_no\":\"RO-1\","
                + "\"transaction_id\":\"txn-1\",\"amount\":{\"total\":2500,\"currency\":\"CNY\"},"
                + "\"success_time\":\"2026-09-12T12:00:01+08:00\"}}";
        mockMvc.perform(post("/api/payments/callbacks/wechatpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Wechatpay-Timestamp", timestamp)
                        .header("Wechatpay-Signature", "00")
                        .content(body))
                .andExpect(status().isForbidden());

        // Plain browsing stays open.
        mockMvc.perform(get("/api/plaza/capsules"))
                .andExpect(status().isOk());
    }
}
